package com.sysadmindoc.alarmclock.ui.ringtone

/**
 * Classifies discovered media tracks (system tones vs full-length songs) and
 * provides the 5-minute automatic refresh debounce from PR #5 without
 * automatically including any track into the active alarm music pool.
 *
 * All newly discovered tracks remain candidates (`membershipState == null`)
 * until the user explicitly adds them to the pool or a playlist.
 */
internal object MusicAutoPoolPolicy {
    const val DEFAULT_MIN_SECONDS = 60
    val availableMinimumSeconds: List<Int> = listOf(0, 30, 60, 120)

    private const val AUTO_REFRESH_INTERVAL_MS = 5L * 60L * 1000L

    fun shouldAutoRefresh(lastElapsedMs: Long, nowElapsedMs: Long): Boolean =
        lastElapsedMs < 0L ||
            nowElapsedMs < lastElapsedMs ||
            nowElapsedMs - lastElapsedMs >= AUTO_REFRESH_INTERVAL_MS

    private val systemSoundDirectories = setOf(
        "ringtones", "notifications", "alarms"
    )

    fun isSystemSound(
        relativePath: String,
        isRingtone: Boolean = false,
        isNotification: Boolean = false,
        isAlarm: Boolean = false
    ): Boolean =
        isRingtone || isNotification || isAlarm || isSystemSoundPath(relativePath)

    fun hasDecisiveMetadata(
        durationMs: Long?,
        relativePath: String,
        isRingtone: Boolean = false,
        isNotification: Boolean = false,
        isAlarm: Boolean = false
    ): Boolean =
        (durationMs != null && durationMs > 0L) ||
            isRingtone || isNotification || isAlarm || isSystemSoundPath(relativePath)

    private fun isSystemSoundPath(relativePath: String): Boolean =
        relativePath.split('/', '\\').any {
            it.lowercase() in systemSoundDirectories
        }

    fun isEligible(
        durationMs: Long?,
        relativePath: String,
        isRingtone: Boolean = false,
        isNotification: Boolean = false,
        isAlarm: Boolean = false,
        minimumSeconds: Int = DEFAULT_MIN_SECONDS
    ): Boolean {
        require(minimumSeconds in availableMinimumSeconds) { "Unsupported minimum duration" }
        if (durationMs == null || durationMs <= 0L) return false
        if (durationMs < minimumSeconds.toLong() * 1000L) return false
        if (isRingtone || isNotification || isAlarm) return false
        if (isSystemSoundPath(relativePath)) return false
        return true
    }
}

internal object MusicAutoPoolPreferences {
    private const val PREFS = "music_auto_pool_preferences"
    private const val DURATION_KEY = "minimum_duration_seconds"

    fun minimumDurationSeconds(context: android.content.Context): Int {
        val saved = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(DURATION_KEY, MusicAutoPoolPolicy.DEFAULT_MIN_SECONDS)
        return saved.takeIf { it in MusicAutoPoolPolicy.availableMinimumSeconds }
            ?: MusicAutoPoolPolicy.DEFAULT_MIN_SECONDS
    }

    fun setMinimumDurationSeconds(context: android.content.Context, seconds: Int) {
        require(seconds in MusicAutoPoolPolicy.availableMinimumSeconds)
        check(
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt(DURATION_KEY, seconds)
                .commit()
        ) {
            "Unable to save music duration filter"
        }
    }
}
