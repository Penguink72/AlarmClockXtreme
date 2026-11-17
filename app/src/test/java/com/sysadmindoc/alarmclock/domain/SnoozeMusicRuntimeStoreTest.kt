package com.sysadmindoc.alarmclock.domain

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SnoozeMusicRuntimeStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = 1_700_000_000_000L

    @Test fun perAlarmModeSavesIndependentlyAndDefaultsToNextDifferent() {
        assertEquals(SnoozeMusicResumeMode.NEXT_DIFFERENT, AlarmSnoozeMusicModeStore.get(context, 0L))
        assertTrue(AlarmSnoozeMusicModeStore.save(context, 5_001L, SnoozeMusicResumeMode.RESUME_INTERRUPTED))
        assertTrue(AlarmSnoozeMusicModeStore.save(context, 5_002L, SnoozeMusicResumeMode.NEXT_DIFFERENT))
        assertEquals(SnoozeMusicResumeMode.RESUME_INTERRUPTED, AlarmSnoozeMusicModeStore.get(context, 5_001L))
        assertEquals(SnoozeMusicResumeMode.NEXT_DIFFERENT, AlarmSnoozeMusicModeStore.get(context, 5_002L))
        assertFalse(AlarmSnoozeMusicModeStore.save(context, -1L, SnoozeMusicResumeMode.RESUME_INTERRUPTED))
    }

    @Test fun positionIsDurableOnlyForNextFireAndExpires() {
        val checkpoint = SnoozeMusicRestoreCheckpoint(
            alarmId = 5_003L,
            sourceFireId = "5_003:old",
            nextFireId = "5_003:next",
            uri = "content://music/track",
            positionMs = 82_000L,
            playlistId = "morning",
            mode = SnoozeMusicResumeMode.RESUME_INTERRUPTED,
            recordedAtMs = at,
            expiresAtMs = at + 120_000L
        )
        try {
            assertTrue(SnoozeMusicRestoreStore.save(context, checkpoint))
            val read = SnoozeMusicRestoreStore.readEligible(context, 5_003L, "5_003:next", at + 60_000L)
            assertNotNull(read)
            assertEquals(checkpoint, read)
            assertNull(SnoozeMusicRestoreStore.readEligible(context, 5_003L, "5_003:old", at + 60_000L))
            assertNull(SnoozeMusicRestoreStore.readEligible(context, 5_004L, "5_003:next", at + 60_000L))
            assertNull(SnoozeMusicRestoreStore.readEligible(context, 5_003L, "5_003:next", at + 120_000L))
        } finally {
            SnoozeMusicRestoreStore.clear(context, 5_003L)
        }
    }

    @Test fun unseekableTimestampCannotBePersistedAsResume() {
        val checkpoint = SnoozeMusicRestoreCheckpoint(
            alarmId = 5_005L,
            sourceFireId = "old",
            nextFireId = "next",
            uri = "content://music/nonseekable.flv",
            positionMs = null,
            playlistId = null,
            mode = SnoozeMusicResumeMode.RESUME_INTERRUPTED,
            recordedAtMs = at,
            expiresAtMs = at + 70_000L
        )
        assertFalse(SnoozeMusicRestoreStore.save(context, checkpoint))
        assertNull(SnoozeMusicRestoreStore.readEligible(context, 5_005L, "next", at + 1L))
        try {
            assertTrue(SnoozeMusicRestoreStore.save(
                context, checkpoint.copy(mode = SnoozeMusicResumeMode.NEXT_DIFFERENT)
            ))
        } finally {
            SnoozeMusicRestoreStore.clear(context, 5_005L)
        }
    }
}
