package com.sysadmindoc.alarmclock.domain

/**
 * Pure countdown projection for UI localization.
 *
 * All calculations use epoch duration. The caller supplies [nowMillis] to
 * avoid a hidden wall-clock read and to make tests deterministic across
 * locale, time zone, DST transitions, and midnight.
 */
internal data class RemainingTimeParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val due: Boolean,
    val underMinute: Boolean
) {
    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60L * MINUTE_MS
        private const val DAY_MS = 24L * HOUR_MS

        fun between(triggerAtMillis: Long, nowMillis: Long): RemainingTimeParts {
            val remaining = (triggerAtMillis - nowMillis).coerceAtLeast(0L)
            if (remaining == 0L) {
                return RemainingTimeParts(0, 0, 0, due = true, underMinute = false)
            }
            if (remaining < MINUTE_MS) {
                return RemainingTimeParts(0, 0, 0, due = false, underMinute = true)
            }
            return RemainingTimeParts(
                days = remaining / DAY_MS,
                hours = (remaining % DAY_MS) / HOUR_MS,
                minutes = (remaining % HOUR_MS) / MINUTE_MS,
                due = false,
                underMinute = false
            )
        }
    }
}
