package com.sysadmindoc.alarmclock.domain

import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSelectionPolicyTest {

    @Test
    fun randomWithRepeatsSelectsAcrossOneThreeAndThreeThousandOneTracksAndAllowsRepeats() {
        listOf(1, 3, 3_001).forEach { count ->
            val snapshot = snapshotOf(count, AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
            val policy = RandomWithRepeatsSelectionPolicy(Random(12345L))
            val picks = (1..25).mapNotNull { policy.selectNext(snapshot, emptySet())?.track?.uri }
            assertEquals(25, picks.size)
            assertTrue(picks.all { it.startsWith("content://music/") })
        }

        // With 2 tracks and 20 draws, immediate repeats (A -> A) occur naturally
        val snapshot2 = snapshotOf(2, AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
        val policy2 = RandomWithRepeatsSelectionPolicy(Random(7L))
        val sequence = (1..20).map {
            val pick = policy2.selectNext(snapshot2, emptySet())!!
            policy2.onTrackStarted(pick)
            pick.track.uri
        }
        val hasImmediateRepeat = sequence.zipWithNext().any { (a, b) -> a == b }
        assertTrue(hasImmediateRepeat)
    }

    @Test
    fun shuffleWithoutRepeatsPlaysEveryTrackOncePerRoundBeforeReshufflingNextRound() {
        val snapshot = snapshotOf(5, AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS)
        val policy = ShuffleWithoutRepeatsSelectionPolicy(Random(42L))

        val round1 = (1..5).map {
            val pick = policy.selectNext(snapshot, emptySet())!!
            policy.onTrackStarted(pick)
            pick.track.uri
        }
        assertEquals(5, round1.toSet().size)
        assertEquals(0, policy.roundsCompleted)

        val round2 = (1..5).map {
            val pick = policy.selectNext(snapshot, emptySet())!!
            policy.onTrackStarted(pick)
            pick.track.uri
        }
        assertEquals(5, round2.toSet().size)
        assertEquals(1, policy.roundsCompleted)
        assertEquals(round1.toSet(), round2.toSet())
    }

    @Test
    fun shuffleWithoutRepeatsHandlesSingleTrackThreeThousandOneTracksAndMidRoundFailure() {
        // Single-track pool loops continuously across rounds
        val singleSnapshot = snapshotOf(1, AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS)
        val singlePolicy = ShuffleWithoutRepeatsSelectionPolicy(Random(99L))
        repeat(4) {
            val pick = singlePolicy.selectNext(singleSnapshot, emptySet())
            assertNotNull(pick)
            assertEquals("content://music/0", pick!!.track.uri)
            singlePolicy.onTrackStarted(pick)
        }

        // 3,001-track pool completes a full non-repeating round of 3,001 tracks
        val largeSnapshot = snapshotOf(3_001, AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS)
        val largePolicy = ShuffleWithoutRepeatsSelectionPolicy(Random(2026L))
        val largeRound = HashSet<String>(3_001)
        repeat(3_001) {
            val pick = largePolicy.selectNext(largeSnapshot, emptySet())!!
            largePolicy.onTrackStarted(pick)
            assertTrue(largeRound.add(pick.track.uri))
        }
        assertEquals(3_001, largeRound.size)

        // Failed track is skipped without infinite loop or resetting the round prematurely
        val snapshot3 = snapshotOf(3, AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS)
        val session = AlarmMusicSession(
            sessionId = AlarmMusicSessionId(1L, "fire-1", 1L),
            snapshot = snapshot3,
            policy = ShuffleWithoutRepeatsSelectionPolicy(Random(11L))
        )
        val first = (session.selectNextTrack() as NextAlarmTrackDecision.PlayTrack).candidate
        val afterFail = session.onTrackFailed(first, "ERROR") as NextAlarmTrackDecision.PlayTrack
        assertNotEquals(first.track.uri, afterFail.candidate.track.uri)
        session.onTrackStarted(afterFail.candidate)
        val third = (session.selectNextTrack() as NextAlarmTrackDecision.PlayTrack).candidate
        assertTrue(third.track.uri !in setOf(first.track.uri, afterFail.candidate.track.uri))
    }

    @Test
    fun playlistOrderFollowsPositionPreservesDuplicateUrisAndWrapsAround() {
        val trackA = MusicTrack("content://music/a", "Song A", "MEDIA_STORE")
        val trackB = MusicTrack("content://music/b", "Song B", "MEDIA_STORE")
        val snapshot = PlaylistSnapshot(
            playlistId = "morning-list",
            playbackStrategy = AlarmMusicPlaybackStrategy.PLAYLIST_ORDER,
            entries = listOf(
                PlaybackTrackCandidate(track = trackA, playlistItemId = "item-1", position = 0),
                PlaybackTrackCandidate(track = trackB, playlistItemId = "item-2", position = 1),
                PlaybackTrackCandidate(track = trackA, playlistItemId = "item-3", position = 2)
            )
        )
        val policy = PlaylistOrderSelectionPolicy()
        val pickedItemIds = (1..6).map {
            val pick = policy.selectNext(snapshot, emptySet())!!
            policy.onTrackStarted(pick)
            pick.playlistItemId
        }
        assertEquals(
            listOf("item-1", "item-2", "item-3", "item-1", "item-2", "item-3"),
            pickedItemIds
        )
    }

    @Test
    fun facetFiltersCombineOrWithinCategoryAndAndAcrossCategories() {
        val entries = listOf(
            PlaybackTrackCandidate(
                track = MusicTrack("content://music/1", "A1", "MEDIA_STORE"),
                artistId = "Utada",
                folderIds = setOf("Music/JP")
            ),
            PlaybackTrackCandidate(
                track = MusicTrack("content://music/2", "A2", "MEDIA_STORE"),
                artistId = "Jay",
                folderIds = setOf("Music/TW")
            ),
            PlaybackTrackCandidate(
                track = MusicTrack("content://music/3", "A3", "MEDIA_STORE"),
                artistId = "Queen",
                folderIds = setOf("Music/JP")
            )
        )
        val snapshot = PlaylistSnapshot(
            playlistId = null,
            playbackStrategy = AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS,
            artistFilters = setOf("Utada", "Jay"),
            folderFilters = setOf("Music/JP"),
            entries = entries
        )
        val eligible = snapshot.eligibleEntries()
        assertEquals(listOf("content://music/1"), eligible.map { it.track.uri })
    }

    @Test
    fun alarmMusicSessionFallsBackAfterFiveConsecutiveFailures() {
        val snapshot = snapshotOf(10, AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
        val session = AlarmMusicSession(
            sessionId = AlarmMusicSessionId(1L, "fire-1", 1L),
            snapshot = snapshot,
            policy = RandomWithRepeatsSelectionPolicy(Random(1L))
        )
        var decision: NextAlarmTrackDecision = session.selectNextTrack()
        repeat(4) {
            val current = (decision as NextAlarmTrackDecision.PlayTrack).candidate
            decision = session.onTrackFailed(current, "ERROR")
            assertTrue(decision is NextAlarmTrackDecision.PlayTrack)
        }
        val fifthCandidate = (decision as NextAlarmTrackDecision.PlayTrack).candidate
        val fifthResult = session.onTrackFailed(fifthCandidate, "ERROR")
        assertTrue(fifthResult is NextAlarmTrackDecision.FallbackToDefaultTone)
    }

    private fun snapshotOf(
        count: Int,
        strategy: AlarmMusicPlaybackStrategy
    ): PlaylistSnapshot = PlaylistSnapshot(
        playlistId = "playlist-test",
        playbackStrategy = strategy,
        entries = (0 until count).map { index ->
            PlaybackTrackCandidate(
                track = MusicTrack(
                    uri = "content://music/$index",
                    title = "Track $index",
                    source = "MEDIA_STORE"
                ),
                playlistItemId = "item-$index",
                position = index
            )
        }
    )
}
