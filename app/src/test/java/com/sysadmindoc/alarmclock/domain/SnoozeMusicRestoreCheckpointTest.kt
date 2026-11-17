package com.sysadmindoc.alarmclock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeMusicRestoreCheckpointTest {
    private val recordedAt = 1_700_000_000_000L

    private fun checkpoint(
        mode: SnoozeMusicResumeMode = SnoozeMusicResumeMode.RESUME_INTERRUPTED,
        positionMs: Long? = 83_142L,
        expiresAtMs: Long = recordedAt + 10 * 60_000L
    ) = SnoozeMusicRestoreCheckpoint(
        alarmId = 42L,
        sourceFireId = "42:1700000000000",
        nextFireId = "42:1700000600000",
        uri = "content://music/track-1",
        positionMs = positionMs,
        playlistId = "morning",
        mode = mode,
        recordedAtMs = recordedAt,
        expiresAtMs = expiresAtMs
    )

    @Test
    fun matchingSnoozeFireAndValidTimestampCanRestore() {
        assertTrue(checkpoint().isEligibleFor(42L, "42:1700000600000", recordedAt + 60_000L))
        assertTrue(checkpoint(positionMs = 0L).isEligibleFor(42L, "42:1700000600000", recordedAt))
    }

    @Test
    fun wrongAlarmOldFireAndDuplicateSourceFireCannotRestore() {
        val snap = checkpoint()
        assertFalse(snap.isEligibleFor(43L, "42:1700000600000", recordedAt + 60_000L))
        assertFalse(snap.isEligibleFor(42L, "42:1700000000000", recordedAt + 60_000L))
        assertFalse(snap.copy(nextFireId = snap.sourceFireId)
            .isEligibleFor(42L, snap.sourceFireId, recordedAt + 60_000L))
    }

    @Test
    fun missingPositionCannotPretendToResumeButMaySelectAnotherTrack() {
        val resume = checkpoint(positionMs = null)
        assertFalse(resume.isEligibleFor(42L, resume.nextFireId, recordedAt + 60_000L))
        val nextDifferent = checkpoint(
            mode = SnoozeMusicResumeMode.NEXT_DIFFERENT,
            positionMs = null
        )
        assertTrue(nextDifferent.isEligibleFor(42L, nextDifferent.nextFireId, recordedAt + 60_000L))
        assertFalse(checkpoint(positionMs = -1L)
            .isEligibleFor(42L, resume.nextFireId, recordedAt + 60_000L))
    }

    @Test
    fun expiredClockRewindAndUnboundedTtlCannotRestore() {
        val snap = checkpoint()
        assertFalse(snap.isEligibleFor(42L, snap.nextFireId, recordedAt - 1L))
        assertFalse(snap.isEligibleFor(42L, snap.nextFireId, snap.expiresAtMs))
        val tooLong = checkpoint(expiresAtMs = recordedAt + SnoozeMusicRestoreCheckpoint.MAX_CHECKPOINT_TTL_MS + 1L)
        assertFalse(tooLong.isEligibleFor(42L, tooLong.nextFireId, recordedAt + 60_000L))
        val reverse = checkpoint(expiresAtMs = recordedAt)
        assertFalse(reverse.isEligibleFor(42L, reverse.nextFireId, recordedAt))
    }

    @Test
    fun emptyMediaIdentityIsRejected() {
        val snap = checkpoint()
        assertFalse(snap.copy(uri = "").isEligibleFor(42L, snap.nextFireId, recordedAt + 1))
        assertFalse(snap.copy(sourceFireId = "").isEligibleFor(42L, snap.nextFireId, recordedAt + 1))
        assertFalse(snap.copy(alarmId = 0L).isEligibleFor(0L, snap.nextFireId, recordedAt + 1))
    }
}
