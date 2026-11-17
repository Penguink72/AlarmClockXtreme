package com.sysadmindoc.alarmclock.domain

import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeNextDifferentPolicyTest {
    private fun snapshot(count: Int, strategy: AlarmMusicPlaybackStrategy) =
        PlaylistSnapshot(
            playlistId = "morning",
            playbackStrategy = strategy,
            entries = (0 until count).map { n ->
                PlaybackTrackCandidate(
                    track = MusicTrack("content://music/$n", "Track $n", "MEDIA_STORE"),
                    position = n
                )
            }
        )

    @Test fun firstResumedTrackDiffersUnderAllThreeExistingPolicies() {
        for (strategy in AlarmMusicPlaybackStrategy.entries) {
            val source = snapshot(3, strategy)
            val session = AlarmMusicSession(
                AlarmMusicSessionId(42, "second-fire", 1),
                source, AlarmMusicSession.createPolicy(strategy, source.playlistId, Random(7))
            )
            val first = session.selectFirstTrackAvoiding("content://music/0")
            assertTrue(first is NextAlarmTrackDecision.PlayTrack)
            assertNotEquals("content://music/0", (first as NextAlarmTrackDecision.PlayTrack).candidate.uri)
            session.onTrackStarted(first.candidate)
            assertTrue(session.selectNextTrack() is NextAlarmTrackDecision.PlayTrack)
        }
    }

    @Test fun oneSongPoolRingsInsteadOfSilence() {
        for (strategy in AlarmMusicPlaybackStrategy.entries) {
            val source = snapshot(1, strategy)
            val session = AlarmMusicSession(
                AlarmMusicSessionId(42, "second-fire", 1),
                source, AlarmMusicSession.createPolicy(strategy, source.playlistId, Random(9))
            )
            val next = session.selectFirstTrackAvoiding("content://music/0")
            assertEquals("content://music/0", (next as NextAlarmTrackDecision.PlayTrack).candidate.uri)
        }
    }

    @Test fun playlistOrderContinuesAfterInterruptedSongInsteadOfRepeatingResumedSong() {
        val source = snapshot(3, AlarmMusicPlaybackStrategy.PLAYLIST_ORDER)
        val session = AlarmMusicSession(
            AlarmMusicSessionId(42, "snoozed-fire", 1),
            source, PlaylistOrderSelectionPolicy()
        )
        val resumed = (session.selectFirstTrackAvoiding("content://music/1")
            as NextAlarmTrackDecision.PlayTrack).candidate
        assertEquals("content://music/2", resumed.uri)
        session.onTrackStarted(resumed)

        val afterResumed = (session.selectNextTrack()
            as NextAlarmTrackDecision.PlayTrack).candidate
        assertEquals("content://music/0", afterResumed.uri)
        session.onTrackStarted(afterResumed)
        assertEquals("content://music/1", (session.selectNextTrack()
            as NextAlarmTrackDecision.PlayTrack).candidate.uri)
    }

    @Test fun duplicateUriSlotsAreSkippedOnlyForFirstSnoozeChoice() {
        val source = PlaylistSnapshot(
            playlistId = "duplicate-order",
            playbackStrategy = AlarmMusicPlaybackStrategy.PLAYLIST_ORDER,
            entries = listOf("a", "a", "b", "c").mapIndexed { index, name ->
                PlaybackTrackCandidate(
                    track = MusicTrack("content://music/$name", name, "MEDIA_STORE"),
                    playlistItemId = "slot-$index",
                    position = index
                )
            }
        )
        val session = AlarmMusicSession(
            AlarmMusicSessionId(42, "duplicate-snooze-fire", 1),
            source, PlaylistOrderSelectionPolicy()
        )
        val resumed = (session.selectFirstTrackAvoiding("content://music/a")
            as NextAlarmTrackDecision.PlayTrack).candidate
        assertEquals("slot-2", resumed.playlistItemId)
        session.onTrackStarted(resumed)
        val next = (session.selectNextTrack() as NextAlarmTrackDecision.PlayTrack).candidate
        assertEquals("slot-3", next.playlistItemId)
        session.onTrackStarted(next)
        assertEquals("slot-0", (session.selectNextTrack()
            as NextAlarmTrackDecision.PlayTrack).candidate.playlistItemId)
    }

    @Test fun duplicateOnlyUriPoolNeverBecomesSilent() {
        val source = PlaylistSnapshot(
            playlistId = "duplicate-only",
            playbackStrategy = AlarmMusicPlaybackStrategy.PLAYLIST_ORDER,
            entries = (0..1).map { index ->
                PlaybackTrackCandidate(
                    track = MusicTrack("content://music/a", "A", "MEDIA_STORE"),
                    playlistItemId = "duplicate-$index",
                    position = index
                )
            }
        )
        val session = AlarmMusicSession(
            AlarmMusicSessionId(42, "duplicate-only-fire", 1),
            source, PlaylistOrderSelectionPolicy()
        )
        val decision = session.selectFirstTrackAvoiding("content://music/a")
        assertTrue(decision is NextAlarmTrackDecision.PlayTrack)
        assertEquals("content://music/a",
            (decision as NextAlarmTrackDecision.PlayTrack).candidate.uri)
    }

    @Test fun noEligibleTrackSignalsFallback() {
        val source = snapshot(0, AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
        val session = AlarmMusicSession(
            AlarmMusicSessionId(42, "second-fire", 1),
            source, RandomWithRepeatsSelectionPolicy(Random(3))
        )
        assertTrue(session.selectFirstTrackAvoiding("content://music/0")
            is NextAlarmTrackDecision.FallbackToDefaultTone)
    }
}
