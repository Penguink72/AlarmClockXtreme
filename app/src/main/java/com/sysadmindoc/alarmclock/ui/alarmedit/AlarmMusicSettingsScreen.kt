package com.sysadmindoc.alarmclock.ui.alarmedit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import com.sysadmindoc.alarmclock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmMusicSettingsScreen(
    state: AlarmMusicSettingsUiState,
    onAction: (AlarmMusicSettingsAction) -> Unit,
    onDismiss: () -> Unit
) {
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
                            enabled = !state.loading && !state.saving,
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
                    item {
                        SettingsGroupTitle(R.string.alarm_music_settings_source)
                        SourceModeRow(state.draft.sourceMode, onAction)
                    }
                    item {
                        SettingsGroupTitle(R.string.alarm_music_settings_strategy)
                        PlaybackStrategyRow(state, onAction)
                    }
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
                        }
                        Text(
                            stringResource(R.string.alarm_music_settings_repeat_lifecycle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.draft.sourceMode == AlarmMusicSourceMode.FILTERED_POOL) {
                        item { SettingsGroupTitle(R.string.alarm_music_settings_artists) }
                        if (state.artists.isEmpty()) {
                            item { Text(stringResource(R.string.alarm_music_settings_no_artists)) }
                        } else {
                            items(state.artists, key = { "artist:${it.id}" }) { artist ->
                                FacetRow(
                                    name = artist.name,
                                    count = artist.playableTrackCount,
                                    selected = artist.id in state.draft.selectedArtistIds,
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
                                    selected = folder.id in state.draft.selectedFolderIds,
                                    onClick = {
                                        onAction(AlarmMusicSettingsAction.ToggleFolder(folder.id))
                                    }
                                )
                            }
                        }
                    }
                    if (state.draft.sourceMode == AlarmMusicSourceMode.INDEPENDENT_PLAYLIST) {
                        item {
                            SettingsGroupTitle(R.string.alarm_music_settings_playlist)
                            OutlinedButton(
                                enabled = !state.loading && !state.saving,
                                onClick = {
                                    onAction(AlarmMusicSettingsAction.RequestTrackPicker)
                                }
                            ) {
                                Text(stringResource(R.string.alarm_music_settings_add_tracks))
                            }
                        }
                        itemsIndexed(
                            state.draft.playlist,
                            key = { _, track -> track.uri }
                        ) { index, track ->
                            PlaylistTrackRow(
                                index = index,
                                track = track,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.draft.playlist.lastIndex,
                                enabled = !state.loading && !state.saving,
                                onMove = { toIndex ->
                                    onAction(
                                        AlarmMusicSettingsAction.MovePlaylistTrack(track.uri, toIndex)
                                    )
                                },
                                onRemove = {
                                    onAction(AlarmMusicSettingsAction.RemovePlaylistTrack(track.uri))
                                }
                            )
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
                    if (state.saved) {
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
private fun SettingsGroupTitle(@StringRes resourceId: Int) {
    Text(
        stringResource(resourceId),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun SourceModeRow(
    selected: AlarmMusicSourceMode,
    onAction: (AlarmMusicSettingsAction) -> Unit
) {
    Column {
        AlarmMusicSourceMode.values().forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == mode,
                    onClick = { onAction(AlarmMusicSettingsAction.SetSourceMode(mode)) }
                )
                Text(
                    stringResource(
                        when (mode) {
                            AlarmMusicSourceMode.SHARED_POOL ->
                                R.string.alarm_music_settings_mode_shared
                            AlarmMusicSourceMode.FILTERED_POOL ->
                                R.string.alarm_music_settings_mode_filtered
                            AlarmMusicSourceMode.INDEPENDENT_PLAYLIST ->
                                R.string.alarm_music_settings_mode_playlist
                        }
                    ),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PlaybackStrategyRow(
    state: AlarmMusicSettingsUiState,
    onAction: (AlarmMusicSettingsAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AlarmMusicPlaybackStrategy.values().forEach { strategy ->
            val enabled = strategy != AlarmMusicPlaybackStrategy.PLAYLIST_ORDER ||
                state.draft.sourceMode == AlarmMusicSourceMode.INDEPENDENT_PLAYLIST
            FilterChip(
                selected = state.draft.playbackStrategy == strategy,
                enabled = enabled,
                onClick = { onAction(AlarmMusicSettingsAction.SetPlaybackStrategy(strategy)) },
                label = {
                    Text(
                        stringResource(
                            when (strategy) {
                                AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS ->
                                    R.string.alarm_music_settings_strategy_random
                                AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS ->
                                    R.string.alarm_music_settings_strategy_shuffle
                                AlarmMusicPlaybackStrategy.PLAYLIST_ORDER ->
                                    R.string.alarm_music_settings_strategy_order
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun FacetRow(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
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

@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: AlarmMusicPlaylistTrack,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}.", modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!track.accessible) {
                Text(
                    stringResource(R.string.alarm_music_settings_track_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        IconButton(enabled = enabled && canMoveUp, onClick = { onMove(index - 1) }) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = stringResource(R.string.alarm_music_settings_move_up)
            )
        }
        IconButton(enabled = enabled && canMoveDown, onClick = { onMove(index + 1) }) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = stringResource(R.string.alarm_music_settings_move_down)
            )
        }
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.alarm_music_settings_remove_track)
            )
        }
    }
}
