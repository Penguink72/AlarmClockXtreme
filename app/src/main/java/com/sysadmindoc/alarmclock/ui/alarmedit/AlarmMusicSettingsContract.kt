package com.sysadmindoc.alarmclock.ui.alarmedit

import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistKind
import kotlinx.coroutines.flow.Flow

enum class AlarmMusicPlaybackStrategy {
    RANDOM_WITH_REPEATS,
    SHUFFLE_WITHOUT_REPEATS,
    PLAYLIST_ORDER
}

data class AlarmMusicPlaylistOption(
    val playlistId: String,
    val name: String,
    val kind: MusicPlaylistKind,
    val playableTrackCount: Int,
    val unavailableTrackCount: Int = 0
)

data class AlarmMusicFacetOption(
    val id: String,
    val name: String,
    val playableTrackCount: Int
)

data class AlarmMusicSettingsDraft(
    /** Null represents the SMART_ALL virtual playlist. */
    val playlistId: String? = null,
    val artistFilters: Set<String> = emptySet(),
    val folderFilters: Set<String> = emptySet(),
    val playbackStrategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS
)

data class AlarmMusicSettingsUiState(
    val draft: AlarmMusicSettingsDraft = AlarmMusicSettingsDraft(),
    val playlists: List<AlarmMusicPlaylistOption> = emptyList(),
    val artists: List<AlarmMusicFacetOption> = emptyList(),
    val folders: List<AlarmMusicFacetOption> = emptyList(),
    val matchingPlayableTrackCount: Int = 0,
    val unavailableTrackCount: Int = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val integrationAvailable: Boolean = false
)

sealed interface AlarmMusicSettingsAction {
    data class SelectPlaylist(val playlistId: String?) : AlarmMusicSettingsAction
    data class ToggleArtist(val artistId: String) : AlarmMusicSettingsAction
    data class ToggleFolder(val folderId: String) : AlarmMusicSettingsAction
    data class SetPlaybackStrategy(val strategy: AlarmMusicPlaybackStrategy) :
        AlarmMusicSettingsAction
    data object Save : AlarmMusicSettingsAction
}

/**
 * Persisted setting contract for Room v28. Implementations must update the
 * alarm setting through the same save/backup path as the owning alarm.
 */
interface AlarmMusicSettingsRepository {
    fun observe(alarmId: Long): Flow<AlarmMusicSettingsUiState>

    suspend fun save(
        alarmId: Long,
        playlistId: String?,
        artistIds: Set<String>,
        folderIds: Set<String>,
        strategy: AlarmMusicPlaybackStrategy
    )
}
