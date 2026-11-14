package com.sysadmindoc.alarmclock.ui.alarmedit

import kotlinx.coroutines.flow.Flow

enum class AlarmMusicSourceMode {
    SHARED_POOL,
    FILTERED_POOL,
    INDEPENDENT_PLAYLIST
}

enum class AlarmMusicPlaybackStrategy {
    RANDOM_WITH_REPEATS,
    SHUFFLE_WITHOUT_REPEATS,
    PLAYLIST_ORDER
}

data class AlarmMusicFacetOption(
    val id: String,
    val name: String,
    val playableTrackCount: Int
)

data class AlarmMusicPlaylistTrack(
    val uri: String,
    val title: String,
    val accessible: Boolean
)

data class AlarmMusicSettingsDraft(
    val sourceMode: AlarmMusicSourceMode = AlarmMusicSourceMode.SHARED_POOL,
    val selectedArtistIds: Set<String> = emptySet(),
    val selectedFolderIds: Set<String> = emptySet(),
    val playlist: List<AlarmMusicPlaylistTrack> = emptyList(),
    val playbackStrategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS
)

data class AlarmMusicSettingsUiState(
    val draft: AlarmMusicSettingsDraft,
    val artists: List<AlarmMusicFacetOption>,
    val folders: List<AlarmMusicFacetOption>,
    val matchingPlayableTrackCount: Int,
    val unavailableTrackCount: Int = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

sealed interface AlarmMusicSettingsAction {
    data class SetSourceMode(val mode: AlarmMusicSourceMode) : AlarmMusicSettingsAction
    data class ToggleArtist(val artistId: String) : AlarmMusicSettingsAction
    data class ToggleFolder(val folderId: String) : AlarmMusicSettingsAction
    data class SetPlaybackStrategy(val strategy: AlarmMusicPlaybackStrategy) : AlarmMusicSettingsAction
    data object RequestTrackPicker : AlarmMusicSettingsAction
    data class AddPlaylistTracks(val uris: List<String>) : AlarmMusicSettingsAction
    data class RemovePlaylistTrack(val uri: String) : AlarmMusicSettingsAction
    data class MovePlaylistTrack(val uri: String, val toIndex: Int) : AlarmMusicSettingsAction
    data object Save : AlarmMusicSettingsAction
}

/**
 * Integration seam for Room v28. The feature UI receives a persisted editor
 * state and emits actions; it does not keep alarm settings in Compose state.
 */
interface AlarmMusicSettingsRepository {
    fun observe(alarmId: Long): Flow<AlarmMusicSettingsUiState>

    suspend fun save(alarmId: Long, draft: AlarmMusicSettingsDraft)
}
