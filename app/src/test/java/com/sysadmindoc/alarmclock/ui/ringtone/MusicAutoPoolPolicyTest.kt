package com.sysadmindoc.alarmclock.ui.ringtone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAutoPoolPolicyTest {

    @Test
    fun systemRingtonesNotificationsAndAlarmsAreFlaggedAndExcludedFromEligibleSongs() {
        assertTrue(
            MusicAutoPoolPolicy.isSystemSound(
                relativePath = "Ringtones/",
                isRingtone = false,
                isNotification = false,
                isAlarm = false
            )
        )
        assertFalse(
            MusicAutoPoolPolicy.isEligible(
                durationMs = 180_000L,
                relativePath = "Ringtones/",
                minimumSeconds = 60
            )
        )
        assertFalse(
            MusicAutoPoolPolicy.isEligible(
                durationMs = 180_000L,
                relativePath = "Music/",
                isNotification = true,
                minimumSeconds = 60
            )
        )
        assertTrue(
            MusicAutoPoolPolicy.isEligible(
                durationMs = 180_000L,
                relativePath = "Music/Albums/",
                minimumSeconds = 60
            )
        )
    }

    @Test
    fun fiveMinuteAutoRefreshDebouncePreventsRepeatedHeavyScans() {
        val t0 = 1_000_000L
        assertTrue(MusicAutoPoolPolicy.shouldAutoRefresh(-1L, t0))
        assertFalse(MusicAutoPoolPolicy.shouldAutoRefresh(t0, t0 + 60_000L))
        assertFalse(MusicAutoPoolPolicy.shouldAutoRefresh(t0, t0 + 4 * 60_000L + 59_999L))
        assertTrue(MusicAutoPoolPolicy.shouldAutoRefresh(t0, t0 + 5 * 60_000L))
        // Monotonic clock reset safety
        assertTrue(MusicAutoPoolPolicy.shouldAutoRefresh(t0, t0 - 1L))
    }
}
