package com.sysadmindoc.alarmclock.domain

import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsDraft

/**
 * Formal playback session & selection contracts for Preview 3 continuous alarm music.
 *
 * Each firing session is strictly isolated by `(alarmId, fireId, generation)`.
 */
data class AlarmMusicSessionId(
    val alarmId: Long,
    val fireId: String,
    val generation: Long
)

data class PlaybackTrackCandidate(
    val track: MusicTrack,
    val playlistItemId: String? = null,
    val position: Int = 0,
    val artistId: String = "",
    val folderIds: Set<String> = emptySet(),
    val isPlayableAtRuntime: Boolean = true
) {
    val uri: String
        get() = track.uri

    val displayTitle: String
        get() = com.sysadmindoc.alarmclock.data.local.entity.MusicTrackTitleResolver.resolve(
            fileName = track.fileName,
            metadataTitle = track.metadataTitle,
            legacyTitle = track.title
        )
}

data class PlaylistSnapshot(
    val playlistId: String?,
    val playbackStrategy: AlarmMusicPlaybackStrategy,
    val artistFilters: Set<String> = emptySet(),
    val folderFilters: Set<String> = emptySet(),
    val entries: List<PlaybackTrackCandidate> = emptyList()
) {
    /**
     * Applies facet filters (`artistFilters` OR within artists, `folderFilters` OR within folders,
     * AND across facets) and excludes runtime-unplayable or session-failed URIs.
     */
    fun eligibleEntries(failedUris: Set<String> = emptySet()): List<PlaybackTrackCandidate> {
        return entries.filter { candidate ->
            if (!candidate.isPlayableAtRuntime) return@filter false
            if (candidate.track.uri in failedUris) return@filter false
            val matchesArtist = artistFilters.isEmpty() ||
                (candidate.artistId.isNotBlank() && candidate.artistId in artistFilters)
            val matchesFolder = folderFilters.isEmpty() ||
                candidate.folderIds.any { it in folderFilters }
            matchesArtist && matchesFolder
        }
    }
}

/**
 * Pure Kotlin selection policy for the three alarm playback strategies:
 * - [AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS]
 * - [AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS]
 * - [AlarmMusicPlaybackStrategy.PLAYLIST_ORDER]
 */
interface PlaybackSelectionPolicy {
    val strategy: AlarmMusicPlaybackStrategy

    /**
     * Selects the next track to attempt for the current session, or null if no
     * eligible track remains.
     */
    fun selectNext(
        snapshot: PlaylistSnapshot,
        failedUris: Set<String>
    ): PlaybackTrackCandidate?

    /**
     * Called only when a track actually starts producing sound (`onIsPlayingChanged(true)`).
     */
    fun onTrackStarted(candidate: PlaybackTrackCandidate)

    /**
     * Called when a candidate fails before or during startup.
     */
    fun onTrackFailed(candidate: PlaybackTrackCandidate, reason: String)
}
