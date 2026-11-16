package com.sysadmindoc.alarmclock.ui.alarmedit

import com.sysadmindoc.alarmclock.domain.SnoozeMusicResumeMode
import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistKind
import kotlinx.coroutines.flow.Flow

enum class AlarmMusicPlaybackStrategy {
    RANDOM_WITH_REPEATS,
    SHUFFLE_WITHOUT_REPEATS,
    PLAYLIST_ORDER
}

data class AlarmMusicSettingsDraft(
    /** Null selects the implicit "All available music" smart playlist (`SMART_ALL`). */
    val playlistId: String? = null,
    val artistFilters: Set<String> = emptySet(),
    val folderFilters: Set<String> = emptySet(),
    val playbackStrategy: AlarmMusicPlaybackStrategy =
        AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS,
    val continuousUntilDismiss: Boolean = true,
    val snoozeMusicMode: SnoozeMusicResumeMode = SnoozeMusicResumeMode.NEXT_DIFFERENT
)

data class AlarmMusicPlaylistOption(
    val playlistId: String,
    val name: String,
    val kind: MusicPlaylistKind = MusicPlaylistKind.NAMED,
    val playableTrackCount: Int = 0,
    val unavailableTrackCount: Int = 0
)

data class AlarmMusicFacetOption(
    val id: String,
    val name: String,
    val playableTrackCount: Int = 0
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
    val saved: Boolean = false,
    val integrationAvailable: Boolean = false,
    val error: String? = null
)

sealed interface AlarmMusicSettingsAction {
    data class SelectPlaylist(val playlistId: String?) : AlarmMusicSettingsAction
    data class SetPlaybackStrategy(
        val strategy: AlarmMusicPlaybackStrategy
    ) : AlarmMusicSettingsAction
    data class SetSnoozeMusicMode(
        val mode: SnoozeMusicResumeMode
    ) : AlarmMusicSettingsAction

    data class ToggleArtist(val artistId: String) : AlarmMusicSettingsAction
    data class ToggleFolder(val folderId: String) : AlarmMusicSettingsAction
    data object ClearFilters : AlarmMusicSettingsAction
    data class DuplicateSelectedPlaylistForAlarm(val alarmId: Long) : AlarmMusicSettingsAction
    data object Save : AlarmMusicSettingsAction
}

/**
 * Gemini Room v28 integration contract for per-alarm music source, filter, and
 * playback strategy settings.
 */
interface AlarmMusicSettingsRepository {
    fun observeSettings(alarmId: Long): Flow<AlarmMusicSettingsDraft>

    suspend fun getSettings(alarmId: Long): AlarmMusicSettingsDraft

    suspend fun saveSettings(alarmId: Long, draft: AlarmMusicSettingsDraft)
}
