package com.sysadmindoc.alarmclock.service

/**
 * Bounded-skip rule for the random music-library alarm. Unplayable tracks
 * (missing SD card, revoked SAF grant, unsupported codec, video without a
 * decodable audio track) are skipped, but only [MAX_CONSECUTIVE_FAILURES]
 * consecutive failures are tolerated before the alarm falls back to the
 * default tone. The counter is reset whenever a track actually starts
 * playing (Player.isPlaying == true), not merely when it becomes READY.
 */
internal object MusicSkipPolicy {
    const val MAX_CONSECUTIVE_FAILURES = AlarmService.MAX_MUSIC_TRACK_SKIPS

    fun shouldFallBack(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
}
