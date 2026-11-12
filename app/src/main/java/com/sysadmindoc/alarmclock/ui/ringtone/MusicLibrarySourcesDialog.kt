package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.alarmclock.R

@Composable
internal fun MusicLibrarySourcesDialog(
    viewModel: MusicLibraryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onSuccess {
                viewModel.addFolder(uri)
            }.onFailure { error ->
                viewModel.reportStatus(
                    context.getString(
                        R.string.music_library_folder_access_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_library_sources_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.music_library_source_management_hint))
                OutlinedButton(
                    enabled = !state.scanning,
                    onClick = { folderLauncher.launch(null) }
                ) {
                    Text(stringResource(R.string.music_library_add_folder))
                }
                if (state.sources.isEmpty()) {
                    Text(stringResource(R.string.music_library_no_sources))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.sources, key = MusicLibrarySource::id) { source ->
                            MusicLibrarySourceRow(
                                source = source,
                                scanning = state.scanning &&
                                    (state.scanningSourceIds.isEmpty() ||
                                        source.id in state.scanningSourceIds),
                                onScan = { viewModel.scan(setOf(source.id)) },
                                onRemove = { viewModel.removeSource(source.id) },
                                onSelectSubfolders = { viewModel.loadSubfolders(source.id) }
                            )
                        }
                    }
                }
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.music_library_duplicate_location_note))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.music_library_close))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.scanning,
                onClick = { viewModel.scan() }
            ) {
                Text(stringResource(R.string.music_library_scan_all_sources))
            }
        }
    )

    if (state.folderPickerSourceId != null) {
        SubfolderSelectionDialog(viewModel)
    }
}

@Composable
private fun MusicLibrarySourceRow(
    source: MusicLibrarySource,
    scanning: Boolean,
    onScan: () -> Unit,
    onRemove: () -> Unit,
    onSelectSubfolders: () -> Unit
) {
    val statusText = when (source.status) {
        MusicSourceStatus.NEEDS_SCAN -> stringResource(R.string.music_library_source_needs_scan)
        MusicSourceStatus.READY -> stringResource(R.string.music_library_source_ready)
        MusicSourceStatus.ERROR -> stringResource(R.string.music_library_source_error)
    }
    val storageRoot = source.documentId.substringBefore(':')
    val origin = when {
        storageRoot.equals("primary", ignoreCase = true) ->
            stringResource(R.string.music_library_source_phone)
        storageRoot.isNotBlank() && storageRoot != source.documentId ->
            stringResource(R.string.music_library_source_removable)
        else -> source.treeUri.substringAfter("://")
            .substringBefore('/')
            .ifBlank { stringResource(R.string.music_library_source_unknown_origin) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(source.name, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.music_library_source_origin, origin, statusText),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            stringResource(
                R.string.music_library_source_counts,
                source.trackCount,
                source.audioCount,
                source.videoCount
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (source.error.isNotBlank()) {
            Text(source.error, style = MaterialTheme.typography.bodySmall)
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !scanning, onClick = onScan) {
                    Text(
                        stringResource(
                            if (scanning) R.string.music_library_scanning
                            else R.string.music_library_rescan
                        )
                    )
                }
                TextButton(enabled = !scanning, onClick = onRemove) {
                    Text(stringResource(R.string.music_library_remove_source))
                }
            }
            TextButton(enabled = !scanning, onClick = onSelectSubfolders) {
                Text(stringResource(R.string.music_library_choose_subfolders))
            }
        }
    }
}

@Composable
private fun SubfolderSelectionDialog(viewModel: MusicLibraryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = viewModel::closeSubfolderPicker,
        title = { Text(stringResource(R.string.music_library_choose_subfolders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loadingSubfolders) {
                    Text(stringResource(R.string.music_library_scanning_status))
                } else if (state.folderCandidates.isEmpty()) {
                    Text(stringResource(R.string.music_library_no_subfolders))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(state.folderCandidates, key = MusicFolderCandidate::documentId) { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = folder.documentId in state.selectedFolderIds,
                                    onCheckedChange = { viewModel.toggleFolderSelection(folder.documentId) }
                                )
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(folder.path, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.loadingSubfolders && state.selectedFolderIds.isNotEmpty(),
                onClick = viewModel::addSelectedSubfolders
            ) {
                Text(stringResource(R.string.music_library_add_selected_folders))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeSubfolderPicker) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
