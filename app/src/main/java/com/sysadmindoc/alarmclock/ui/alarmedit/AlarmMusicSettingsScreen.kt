package com.sysadmindoc.alarmclock.ui.alarmedit

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sysadmindoc.alarmclock.domain.SnoozeMusicResumeMode
import com.sysadmindoc.alarmclock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmMusicSettingsScreen(
    state: AlarmMusicSettingsUiState,
    onAction: (AlarmMusicSettingsAction) -> Unit,
    onDismiss: () -> Unit
) {
    var advancedFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.music_library_back)
                            )
                        }
                        Text(
                            stringResource(R.string.alarm_music_settings_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            enabled = state.integrationAvailable && !state.loading && !state.saving,
                            onClick = { onAction(AlarmMusicSettingsAction.Save) }
                        ) {
                            Text(
                                stringResource(
                                    if (state.saving) R.string.alarm_music_settings_saving
                                    else R.string.alarm_music_settings_save
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .testTag("alarm-music-settings-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!state.integrationAvailable) {
                        item {
                            Text(
                                stringResource(R.string.alarm_music_settings_pending),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    item {
                        SettingsGroupTitle(R.string.alarm_music_settings_playlist_label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("alarm-music-playlist-list"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.draft.playlistId == null,
                                enabled = state.integrationAvailable &&
                                    !state.loading && !state.saving,
                                onClick = {
                                    onAction(AlarmMusicSettingsAction.SelectPlaylist(null))
                                },
                                label = {
                                    Text(stringResource(R.string.alarm_music_settings_playlist_smart_all))
                                }
                            )
                            state.playlists.forEach { playlist ->
                                FilterChip(
                                    selected = state.draft.playlistId == playlist.playlistId,
                                    enabled = state.integrationAvailable &&
                                        !state.loading && !state.saving,
                                    onClick = {
                                        onAction(
                                            AlarmMusicSettingsAction.SelectPlaylist(
                                                playlist.playlistId
                                            )
                                        )
                                    },
                                    label = {
                                        Text(
                                            playlist.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroupTitle(R.string.alarm_music_settings_strategy)
                        PlaybackStrategyRow(state, onAction)
                        if (state.draft.playlistId == null) {
                            Text(
                                stringResource(
                                    R.string.alarm_music_settings_order_requires_named
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item {
                        SettingsGroupTitle(R.string.u4_snooze_mode_label)
                        SnoozeMusicModeRow(state, onAction)
                    }
                    if (state.integrationAvailable) {
                        item {
                            Text(
                                stringResource(
                                    R.string.alarm_music_settings_match_count,
                                    state.matchingPlayableTrackCount
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.matchingPlayableTrackCount == 0) {
                                Text(
                                    stringResource(R.string.alarm_music_settings_empty_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (state.unavailableTrackCount > 0) {
                                Text(
                                    stringResource(
                                        R.string.alarm_music_settings_unavailable_warning,
                                        state.unavailableTrackCount
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    stringResource(R.string.alarm_music_settings_track_unavailable),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.alarm_music_settings_repeat_lifecycle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        TextButton(
                            enabled = !state.loading && !state.saving,
                            onClick = { advancedFiltersExpanded = !advancedFiltersExpanded }
                        ) {
                            Text(
                                stringResource(R.string.alarm_music_settings_advanced_filters)
                            )
                        }
                        Text(
                            stringResource(R.string.alarm_music_settings_filter_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (advancedFiltersExpanded) {
                        item { SettingsGroupTitle(R.string.alarm_music_settings_artists) }
                        if (state.artists.isEmpty()) {
                            item { Text(stringResource(R.string.alarm_music_settings_no_artists)) }
                        } else {
                            items(state.artists, key = { "artist:${it.id}" }) { artist ->
                                FacetRow(
                                    name = artist.name,
                                    count = artist.playableTrackCount,
                                    selected = artist.id in state.draft.artistFilters,
                                    enabled = state.integrationAvailable &&
                                        !state.loading && !state.saving,
                                    onClick = {
                                        onAction(AlarmMusicSettingsAction.ToggleArtist(artist.id))
                                    }
                                )
                            }
                        }
                        item { SettingsGroupTitle(R.string.alarm_music_settings_folders) }
                        if (state.folders.isEmpty()) {
                            item { Text(stringResource(R.string.alarm_music_settings_no_folders)) }
                        } else {
                            items(state.folders, key = { "folder:${it.id}" }) { folder ->
                                FacetRow(
                                    name = folder.name,
                                    count = folder.playableTrackCount,
                                    selected = folder.id in state.draft.folderFilters,
                                    enabled = state.integrationAvailable &&
                                        !state.loading && !state.saving,
                                    onClick = {
                                        onAction(AlarmMusicSettingsAction.ToggleFolder(folder.id))
                                    }
                                )
                            }
                        }
                    }
                    if (state.loading) {
                        item {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    state.error?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                    if (state.saved && state.integrationAvailable) {
                        item {
                            Text(
                                stringResource(R.string.alarm_music_settings_saved),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnoozeMusicModeRow(
    state: AlarmMusicSettingsUiState,
    onAction: (AlarmMusicSettingsAction) -> Unit
) {
    val enabled = state.integrationAvailable && !state.loading && !state.saving
    SnoozeMusicResumeMode.entries.forEach { mode ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.draft.snoozeMusicMode == mode,
                enabled = enabled,
                onClick = { onAction(AlarmMusicSettingsAction.SetSnoozeMusicMode(mode)) }
            )
            Text(
                stringResource(
                    if (mode == SnoozeMusicResumeMode.RESUME_INTERRUPTED)
                        R.string.u4_snooze_resume_position
                    else R.string.u4_snooze_next_different
                ),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    Text(
        stringResource(R.string.u4_snooze_mode_note),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SettingsGroupTitle(@StringRes resourceId: Int) {
    Text(
        stringResource(resourceId),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun PlaybackStrategyRow(
    state: AlarmMusicSettingsUiState,
    onAction: (AlarmMusicSettingsAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AlarmMusicPlaybackStrategy.entries.forEach { strategy ->
            val enabled = state.integrationAvailable && !state.loading && !state.saving &&
                (strategy != AlarmMusicPlaybackStrategy.PLAYLIST_ORDER ||
                    state.draft.playlistId != null)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.draft.playbackStrategy == strategy,
                    enabled = enabled,
                    onClick = {
                        onAction(AlarmMusicSettingsAction.SetPlaybackStrategy(strategy))
                    }
                )
                Text(
                    stringResource(strategy.labelRes),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private val AlarmMusicPlaybackStrategy.labelRes: Int
    get() = when (this) {
        AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS ->
            R.string.alarm_music_settings_strategy_random
        AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS ->
            R.string.alarm_music_settings_strategy_shuffle
        AlarmMusicPlaybackStrategy.PLAYLIST_ORDER ->
            R.string.alarm_music_settings_strategy_order
    }

@Composable
private fun FacetRow(
    name: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                stringResource(R.string.music_library_facet_track_count, name, count),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
