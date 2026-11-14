package com.sysadmindoc.alarmclock.ui.ringtone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle

@Composable
internal fun MusicPoolDialog(
    viewModel: MusicLibraryViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.loadPoolCandidates()
    }
    val visible = viewModel.visiblePoolTracks(state)
    val allVisibleSelected = visible.isNotEmpty() &&
        visible.all { it.track.uri in state.selectedPoolUris }
    val scopeLabel = when (val scope = state.poolSourceScope) {
        MusicPoolSourceScope.Folders -> stringResource(R.string.music_library_pool_scope_folders)
        MusicPoolSourceScope.DeviceMedia -> stringResource(R.string.music_library_pool_device_source)
        is MusicPoolSourceScope.Folder -> state.sources.firstOrNull { it.id == scope.sourceId }?.name
            ?: stringResource(R.string.music_library_pool_unknown_source)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_library_pool_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.poolSearch,
                    onValueChange = viewModel::setPoolSearch,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.music_library_pool_search)) },
                    singleLine = true
                )
                Text(
                    stringResource(R.string.music_library_pool_source_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PoolFilterChip(
                        label = stringResource(R.string.music_library_pool_scope_folders),
                        selected = state.poolSourceScope == MusicPoolSourceScope.Folders,
                        onClick = { viewModel.setPoolSourceScope(MusicPoolSourceScope.Folders) }
                    )
                    state.sources.forEach { source ->
                        PoolFilterChip(
                            label = source.name,
                            selected = state.poolSourceScope == MusicPoolSourceScope.Folder(source.id),
                            onClick = {
                                viewModel.setPoolSourceScope(MusicPoolSourceScope.Folder(source.id))
                            }
                        )
                    }
                    PoolFilterChip(
                        label = stringResource(R.string.music_library_pool_device_source),
                        selected = state.poolSourceScope == MusicPoolSourceScope.DeviceMedia,
                        onClick = { viewModel.setPoolSourceScope(MusicPoolSourceScope.DeviceMedia) }
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PoolFilterChip(
                        label = stringResource(R.string.music_library_pool_all_candidates),
                        selected = state.poolFilter == MusicPoolFilter.ALL_CANDIDATES,
                        onClick = { viewModel.setPoolFilter(MusicPoolFilter.ALL_CANDIDATES) }
                    )
                    PoolFilterChip(
                        label = stringResource(R.string.music_library_pool_included),
                        selected = state.poolFilter == MusicPoolFilter.INCLUDED,
                        onClick = { viewModel.setPoolFilter(MusicPoolFilter.INCLUDED) }
                    )
                    PoolFilterChip(
                        label = stringResource(R.string.music_library_pool_excluded),
                        selected = state.poolFilter == MusicPoolFilter.EXCLUDED,
                        onClick = { viewModel.setPoolFilter(MusicPoolFilter.EXCLUDED) }
                    )
                }
                Text(
                    stringResource(
                        R.string.music_library_pool_results,
                        visible.size,
                        state.selectedPoolUris.size
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.music_library_pool_scope_summary, scopeLabel, visible.size),
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.poolSourceScope == MusicPoolSourceScope.DeviceMedia) {
                    Text(
                        stringResource(R.string.music_library_pool_device_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.music_library_pool_non_destructive_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        enabled = visible.isNotEmpty(),
                        onClick = {
                            viewModel.selectVisiblePoolTracks(
                                if (allVisibleSelected) emptyList() else visible.map { it.track.uri }
                            )
                        }
                    ) {
                        Text(
                            stringResource(
                                if (allVisibleSelected) R.string.music_library_pool_clear_selection
                                else R.string.music_library_pool_select_visible
                            )
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        enabled = state.selectedPoolUris.isNotEmpty() && !state.loadingPool,
                        onClick = { viewModel.updateSelectedPoolMembership(included = true) }
                    ) {
                        Text(
                            stringResource(
                                if (state.poolFilter == MusicPoolFilter.EXCLUDED) {
                                    R.string.music_library_pool_restore
                                } else {
                                    R.string.music_library_pool_add
                                }
                            )
                        )
                    }
                    TextButton(
                        enabled = state.selectedPoolUris.isNotEmpty() && !state.loadingPool,
                        onClick = { viewModel.updateSelectedPoolMembership(included = false) }
                    ) {
                        Text(stringResource(R.string.music_library_pool_exclude))
                    }
                }
                if (state.loadingPool) {
                    Text(stringResource(R.string.music_library_scanning_status))
                } else if (visible.isEmpty()) {
                    Text(stringResource(R.string.music_library_pool_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visible, key = { it.track.uri }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val sourceFallback = stringResource(
                                    if (item.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE) {
                                        R.string.music_library_pool_device_source
                                    } else {
                                        R.string.music_library_pool_unknown_source
                                    }
                                )
                                Checkbox(
                                    checked = item.track.uri in state.selectedPoolUris,
                                    onCheckedChange = { viewModel.togglePoolSelection(item.track.uri) }
                                )
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        item.track.displayTitle,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        item.sourceName.ifBlank { sourceFallback },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        stringResource(
                                            if (item.track.isVideoAudio()) {
                                                R.string.music_library_pool_type_video_audio
                                            } else {
                                                R.string.music_library_pool_type_audio
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        stringResource(
                                            when (item.membershipState) {
                                                MusicPoolEntry.INCLUDED ->
                                                    R.string.music_library_pool_status_included
                                                MusicPoolEntry.AUTO_INCLUDED ->
                                                    R.string.music_library_pool_status_auto_included
                                                MusicPoolEntry.AUTO_SKIPPED ->
                                                    R.string.music_library_pool_status_auto_skipped
                                                MusicPoolEntry.EXCLUDED ->
                                                    R.string.music_library_pool_status_excluded
                                                else -> R.string.music_library_pool_status_candidate
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.music_library_close))
            }
        }
    )
}

@Composable
private fun PoolFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
