package com.sysadmindoc.alarmclock.data.readiness

import java.util.UUID

/**
 * B2-A: strictly diagnostic session identity. This contract does NOT schedule
 * an OS alarm or claim that the production AlarmService chain was exercised.
 *
 * A source alarm is read-only: the production test must never change its row,
 * history, snooze count, nextTriggerTime, webhooks, or guardian actions.
 */
internal data class ProductionTestSession(
    val sessionId: String,
    val sourceAlarmId: Long,
    val createdAtMs: Long,
    val scheduledAtMs: Long,
    val expiresAtMs: Long,
    val nextOccurrence: Int = 0
) {
    /** Explicit namespace, never the live alarm's regular fireId. */
    fun fireId(occurrence: Int = nextOccurrence): String =
        "b2-test:$sessionId:$occurrence"

    fun canAccept(
        incomingSessionId: String,
        incomingFireId: String,
        nowMs: Long
    ): Boolean {
        if (!valid() || !isActiveAt(nowMs)) return false
        return incomingSessionId == sessionId && incomingFireId == fireId()
    }

    fun isActiveAt(nowMs: Long): Boolean =
        nowMs >= createdAtMs && nowMs < expiresAtMs

    fun valid(): Boolean =
        validId(sessionId) &&
            sourceAlarmId > 0L &&
            scheduledAtMs > createdAtMs &&
            scheduledAtMs < expiresAtMs &&
            expiresAtMs - createdAtMs <= MAX_TEST_SESSION_MS &&
            nextOccurrence in 0..MAX_TEST_OCCURRENCE

    companion object {
        // Two occurrences including one 1-minute snooze, plus bounded recovery.
        const val MAX_TEST_SESSION_MS = 15L * 60_000L
        const val MAX_TEST_OCCURRENCE = 1

        private val canonicalUuid = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )

        fun validId(id: String): Boolean = canonicalUuid.matches(id)

        fun new(
            sourceAlarmId: Long,
            nowMs: Long,
            firstDelayMs: Long = 60_000L
        ): ProductionTestSession {
            require(sourceAlarmId > 0L) { "A test must name a real source alarm read-only." }
            require(firstDelayMs in 1_000L..60_000L) { "Test delay must be 1s to 60s." }
            val session = ProductionTestSession(
                sessionId = UUID.randomUUID().toString(),
                sourceAlarmId = sourceAlarmId,
                createdAtMs = nowMs,
                scheduledAtMs = nowMs + firstDelayMs,
                expiresAtMs = nowMs + MAX_TEST_SESSION_MS
            )
            check(session.valid()) { "Generated test session is invalid." }
            return session
        }
    }
}

/**
 * Test-only transitions. All terminal events are idempotent; neither an alarm
 * service process restart nor an out-of-order broadcast may silently advance
 * an occurrence or invent a successful audio-start event.
 */
internal enum class ProductionTestStage {
    PREPARED, REGISTERED, RECEIVER_DELIVERED, SERVICE_ACKNOWLEDGED,
    AUDIO_STARTED, SNOOZE_REGISTERED, SNOOZE_DELIVERED, AUDIO_RESTARTED,
    DISMISSED, CANCELLED, FAILED
}

internal fun canAdvanceProductionTestStage(
    from: ProductionTestStage,
    to: ProductionTestStage
): Boolean {
    if (from == to) return false
    if (from in setOf(ProductionTestStage.DISMISSED, ProductionTestStage.CANCELLED, ProductionTestStage.FAILED)) {
        return false
    }
    if (to == ProductionTestStage.CANCELLED || to == ProductionTestStage.FAILED) return true
    return when (from) {
        ProductionTestStage.PREPARED -> to == ProductionTestStage.REGISTERED
        ProductionTestStage.REGISTERED -> to == ProductionTestStage.RECEIVER_DELIVERED
        ProductionTestStage.RECEIVER_DELIVERED -> to == ProductionTestStage.SERVICE_ACKNOWLEDGED
        ProductionTestStage.SERVICE_ACKNOWLEDGED -> to == ProductionTestStage.AUDIO_STARTED
        ProductionTestStage.AUDIO_STARTED -> to == ProductionTestStage.SNOOZE_REGISTERED ||
            to == ProductionTestStage.DISMISSED
        ProductionTestStage.SNOOZE_REGISTERED -> to == ProductionTestStage.SNOOZE_DELIVERED
        ProductionTestStage.SNOOZE_DELIVERED -> to == ProductionTestStage.AUDIO_RESTARTED
        ProductionTestStage.AUDIO_RESTARTED -> to == ProductionTestStage.DISMISSED
        else -> false
    }
}
