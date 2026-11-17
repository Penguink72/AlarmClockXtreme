package com.sysadmindoc.alarmclock.domain

import com.sysadmindoc.alarmclock.service.MusicSkipPolicy
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Strategy 1: Uniform random draw with replacement over eligible unique URIs.
 * Allows immediate repeats (A -> A).
 */
class RandomWithRepeatsSelectionPolicy(
    private val random: Random = Random.Default
) : PlaybackSelectionPolicy {
    override val strategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS

    override fun selectNext(
        snapshot: PlaylistSnapshot,
        failedUris: Set<String>
    ): PlaybackTrackCandidate? {
        val eligible = snapshot.eligibleEntries(failedUris)
            .distinctBy { it.track.uri }
        if (eligible.isEmpty()) return null
        return eligible[random.nextInt(eligible.size)]
    }

    override fun onTrackStarted(candidate: PlaybackTrackCandidate) = Unit

    override fun onTrackFailed(candidate: PlaybackTrackCandidate, reason: String) = Unit
}

/**
 * Strategy 2: Shuffle without repeats within each round.
 *
 * - Builds a shuffled deck of eligible unique URIs per round using [random].
 * - A track is marked played in the current round ONLY when [onTrackStarted]
 *   fires (`isPlaying == true`).
 * - Once all eligible tracks have been played in the current round, starts a
 *   fresh shuffled round so continuous playback never stops.
 */
class ShuffleWithoutRepeatsSelectionPolicy(
    private val random: Random = Random.Default
) : PlaybackSelectionPolicy {
    override val strategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS

    private val roundQueue = ArrayDeque<String>()
    private val playedUrisInCurrentRound = LinkedHashSet<String>()
    private var completedRounds: Int = 0

    val roundsCompleted: Int
        @Synchronized get() = completedRounds

    @Synchronized
    override fun selectNext(
        snapshot: PlaylistSnapshot,
        failedUris: Set<String>
    ): PlaybackTrackCandidate? {
        val eligibleByUri = LinkedHashMap<String, PlaybackTrackCandidate>()
        for (entry in snapshot.eligibleEntries(failedUris)) {
            eligibleByUri.putIfAbsent(entry.track.uri, entry)
        }
        if (eligibleByUri.isEmpty()) {
            roundQueue.clear()
            playedUrisInCurrentRound.clear()
            return null
        }

        // Remove any queued URIs that are no longer eligible or already played in this round
        roundQueue.removeAll { uri ->
            uri !in eligibleByUri || uri in playedUrisInCurrentRound
        }

        val unplayedEligible = eligibleByUri.keys.filterNot { it in playedUrisInCurrentRound }
        if (unplayedEligible.isEmpty()) {
            // Current round exhausted: start a new shuffled round
            playedUrisInCurrentRound.clear()
            roundQueue.clear()
            completedRounds++
            val freshOrder = eligibleByUri.keys.toList().shuffled(random)
            roundQueue.addAll(freshOrder)
        } else if (roundQueue.isEmpty()) {
            val remainingOrder = unplayedEligible.shuffled(random)
            roundQueue.addAll(remainingOrder)
        }

        val nextUri = roundQueue.firstOrNull() ?: return null
        return eligibleByUri[nextUri]
    }

    @Synchronized
    override fun onTrackStarted(candidate: PlaybackTrackCandidate) {
        val uri = candidate.track.uri
        playedUrisInCurrentRound.add(uri)
        if (roundQueue.firstOrNull() == uri) {
            roundQueue.removeFirst()
        } else {
            roundQueue.remove(uri)
        }
    }

    @Synchronized
    override fun onTrackFailed(candidate: PlaybackTrackCandidate, reason: String) {
        val uri = candidate.track.uri
        if (roundQueue.firstOrNull() == uri) {
            roundQueue.removeFirst()
        } else {
            roundQueue.remove(uri)
        }
    }
}

/**
 * Strategy 3: Sequential playlist order (`position` ascending) with wrap-around.
 *
 * Preserves duplicate track URIs at distinct `playlistItemId` / `position` slots.
 */
class PlaylistOrderSelectionPolicy : PlaybackSelectionPolicy {
    override val strategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.PLAYLIST_ORDER

    private var nextCursorIndex: Int = 0
    private var lastSelectedKey: String? = null

    @Synchronized
    override fun selectNext(
        snapshot: PlaylistSnapshot,
        failedUris: Set<String>
    ): PlaybackTrackCandidate? {
        val ordered = snapshot.eligibleEntries(failedUris)
            .sortedWith(compareBy<PlaybackTrackCandidate> { it.position }.thenBy { it.playlistItemId.orEmpty() })
        if (ordered.isEmpty()) {
            nextCursorIndex = 0
            lastSelectedKey = null
            return null
        }
        val index = nextCursorIndex.mod(ordered.size)
        val chosen = ordered[index]
        lastSelectedKey = entryKey(chosen, index)
        nextCursorIndex = (index + 1).mod(ordered.size)
        return chosen
    }

    /**
     * U4: a new Service starts with a fresh playlist cursor. For a snoozed
     * playlist, re-anchor the cursor just after the interrupted URI, then
     * advance past any adjacent copies of that same URI. The next ordinary
     * selectNext() continues from the selected slot, rather than repeating
     * the first resumed track because the reduced snapshot had fewer rows.
     *
     * Duplicate URIs cannot identify their exact original playlist slot;
     * choose the first matching slot deterministically without fabricating
     * a position for a song that was not played.
     */
    @Synchronized
    fun selectAfterInterruptedUri(
        snapshot: PlaylistSnapshot,
        failedUris: Set<String>,
        interruptedUri: String
    ): PlaybackTrackCandidate? {
        val ordered = snapshot.eligibleEntries(failedUris)
            .sortedWith(compareBy<PlaybackTrackCandidate> { it.position }
                .thenBy { it.playlistItemId.orEmpty() })
        if (ordered.isEmpty()) return null
        val interruptedIndex = ordered.indexOfFirst { it.uri == interruptedUri }
        if (interruptedIndex < 0) return selectNext(snapshot, failedUris)
        nextCursorIndex = (interruptedIndex + 1).mod(ordered.size)
        if (ordered.all { it.uri == interruptedUri }) return selectNext(snapshot, failedUris)
        repeat(ordered.size) {
            val candidate = selectNext(snapshot, failedUris) ?: return null
            if (candidate.uri != interruptedUri) return candidate
        }
        return null
    }

    @Synchronized
    override fun onTrackStarted(candidate: PlaybackTrackCandidate) = Unit

    @Synchronized
    override fun onTrackFailed(candidate: PlaybackTrackCandidate, reason: String) = Unit

    private fun entryKey(candidate: PlaybackTrackCandidate, index: Int): String =
        candidate.playlistItemId ?: "${candidate.position}:$index:${candidate.track.uri}"
}

sealed interface NextAlarmTrackDecision {
    data class PlayTrack(val candidate: PlaybackTrackCandidate) : NextAlarmTrackDecision
    data class FallbackToDefaultTone(val reasonCode: String) : NextAlarmTrackDecision
}

/**
 * Isolated per-firing alarm music session bound to a single [AlarmMusicSessionId].
 */
class AlarmMusicSession(
    val sessionId: AlarmMusicSessionId,
    val snapshot: PlaylistSnapshot,
    private val policy: PlaybackSelectionPolicy
) {
    private val failedUris = Collections.synchronizedSet( LinkedHashSet<String>() )
    private val consecutiveFailures = AtomicInteger(0)

    fun failedUriSnapshot(): Set<String> = synchronized(failedUris) { failedUris.toSet() }

    fun consecutiveFailureCount(): Int = consecutiveFailures.get()

    fun selectNextTrack(): NextAlarmTrackDecision {
        val currentFailed = failedUriSnapshot()
        val next = policy.selectNext(snapshot, currentFailed)
            ?: return NextAlarmTrackDecision.FallbackToDefaultTone("NO_ELIGIBLE_MUSIC_TRACKS")
        return NextAlarmTrackDecision.PlayTrack(next)
    }

    /**
     * U4 NEXT_DIFFERENT: apply the existing strategy with the interrupted URI
     * excluded for this first choice. A one-song pool remains playable.
     */
    fun selectFirstTrackAvoiding(interruptedUri: String): NextAlarmTrackDecision {
        val failed = failedUriSnapshot()
        if (policy is PlaylistOrderSelectionPolicy) {
            val candidate = policy.selectAfterInterruptedUri(snapshot, failed, interruptedUri)
                ?: return NextAlarmTrackDecision.FallbackToDefaultTone("NO_ELIGIBLE_MUSIC_TRACKS")
            return NextAlarmTrackDecision.PlayTrack(candidate)
        }
        val distinctPlayable = snapshot.eligibleEntries(failed)
            .map { it.uri }.distinct()
        val effective = if (distinctPlayable.size > 1 && interruptedUri in distinctPlayable) {
            snapshot.copy(entries = snapshot.entries.filterNot { it.uri == interruptedUri })
        } else snapshot
        val next = policy.selectNext(effective, failed)
            ?: return NextAlarmTrackDecision.FallbackToDefaultTone("NO_ELIGIBLE_MUSIC_TRACKS")
        return NextAlarmTrackDecision.PlayTrack(next)
    }

    fun onTrackStarted(candidate: PlaybackTrackCandidate) {
        consecutiveFailures.set(0)
        policy.onTrackStarted(candidate)
    }

    fun onTrackFailed(
        candidate: PlaybackTrackCandidate,
        reason: String
    ): NextAlarmTrackDecision {
        failedUris.add(candidate.track.uri)
        policy.onTrackFailed(candidate, reason)
        val failures = consecutiveFailures.incrementAndGet()
        if (MusicSkipPolicy.shouldFallBack(failures)) {
            return NextAlarmTrackDecision.FallbackToDefaultTone(
                "MUSIC_SKIP_LIMIT_${reason}_$failures"
            )
        }
        return selectNextTrack()
    }

    companion object {
        fun createPolicy(
            strategy: AlarmMusicPlaybackStrategy,
            playlistId: String?,
            random: Random = Random.Default
        ): PlaybackSelectionPolicy {
            val effectiveStrategy = if (
                playlistId == null &&
                strategy == AlarmMusicPlaybackStrategy.PLAYLIST_ORDER
            ) {
                AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS
            } else {
                strategy
            }
            return when (effectiveStrategy) {
                AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS ->
                    RandomWithRepeatsSelectionPolicy(random)
                AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS ->
                    ShuffleWithoutRepeatsSelectionPolicy(random)
                AlarmMusicPlaybackStrategy.PLAYLIST_ORDER ->
                    PlaylistOrderSelectionPolicy()
            }
        }
    }
}
