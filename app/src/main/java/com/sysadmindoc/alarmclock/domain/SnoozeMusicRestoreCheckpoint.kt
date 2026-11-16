package com.sysadmindoc.alarmclock.domain

/**
 * U4 contract only: no persistence or AlarmService routing is enabled by this model.
 * The caller must capture a position only after a real onIsPlaying=true callback,
 * before the player is released for snooze.
 */
internal enum class SnoozeMusicResumeMode {
    RESUME_INTERRUPTED,
    NEXT_DIFFERENT
}

/**
 * A snapshot is bound to exactly one future snooze fire.
 *
 * [sourceFireId] is the actually playing occurrence that was snoozed;
 * [nextFireId] is the explicitly registered next occurrence. This prevents
 * stale/duplicate snooze actions or other alarms from consuming the snapshot.
 */
internal data class SnoozeMusicRestoreCheckpoint(
    val alarmId: Long,
    val sourceFireId: String,
    val nextFireId: String,
    val uri: String,
    val positionMs: Long?,
    val playlistId: String?,
    val mode: SnoozeMusicResumeMode,
    val recordedAtMs: Long,
    val expiresAtMs: Long
) {
    fun isEligibleFor(
        alarmId: Long,
        incomingFireId: String,
        nowMs: Long
    ): Boolean {
        if (this.alarmId <= 0L || this.alarmId != alarmId) return false
        if (sourceFireId.isBlank() || nextFireId.isBlank() || sourceFireId == nextFireId) return false
        if (incomingFireId != nextFireId || uri.isBlank()) return false
        if (positionMs != null && positionMs < 0L) return false
        if (mode == SnoozeMusicResumeMode.RESUME_INTERRUPTED && positionMs == null) return false
        if (recordedAtMs <= 0L || expiresAtMs <= recordedAtMs) return false
        if (expiresAtMs - recordedAtMs > MAX_CHECKPOINT_TTL_MS) return false
        return nowMs >= recordedAtMs && nowMs < expiresAtMs
    }

    companion object {
        // Maximum supported snooze is 120 minutes; allow a five-minute margin
        // for dispatch latency without permitting indefinitely stale restores.
        const val MAX_CHECKPOINT_TTL_MS = 125L * 60_000L
    }
}
