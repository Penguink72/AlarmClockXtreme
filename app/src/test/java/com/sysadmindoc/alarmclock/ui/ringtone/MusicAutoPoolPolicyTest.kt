package com.sysadmindoc.alarmclock.ui.ringtone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAutoPoolPolicyTest {
    @Test
    fun durationBoundaryAndUnknownRemainSafe() {
        assertFalse(MusicAutoPoolPolicy.isEligible(null, "Music/"))
        assertFalse(MusicAutoPoolPolicy.isEligible(0L, "Music/"))
        assertFalse(MusicAutoPoolPolicy.isEligible(59_999L, "Music/"))
        assertTrue(MusicAutoPoolPolicy.isEligible(60_000L, "Music/"))
        assertTrue(MusicAutoPoolPolicy.isEligible(120_000L, "Music/"))
        assertTrue(MusicAutoPoolPolicy.isEligible(31_000L, "Music/", minimumSeconds = 30))
        assertFalse(MusicAutoPoolPolicy.isEligible(31_000L, "Music/", minimumSeconds = 60))
        assertTrue(MusicAutoPoolPolicy.isEligible(1_000L, "Music/", minimumSeconds = 0))
    }

    @Test
    fun factoryRingtonesAndNotificationFoldersNeverAutoEnterPool() {
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Ringtones/"))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Audio/Notifications/"))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Alarms/Factory/"))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "audio\\ringtones\\factory"))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Music/",
            isRingtone = true))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Music/",
            isNotification = true))
        assertFalse(MusicAutoPoolPolicy.isEligible(180_000L, "Music/",
            isAlarm = true))
        assertTrue(MusicAutoPoolPolicy.isEligible(180_000L, "Music/Limbus company/"))
    }

    @Test
    fun nearNamedFolderIsNotMistakenForSystemSoundDirectory() {
        assertTrue(MusicAutoPoolPolicy.isEligible(90_000L, "Music/My Ringtones Collection/"))
        assertTrue(MusicAutoPoolPolicy.isEligible(90_000L, "Music/Alarms Album/"))
    }
}
