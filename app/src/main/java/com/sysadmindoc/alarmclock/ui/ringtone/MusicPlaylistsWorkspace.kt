package com.sysadmindoc.alarmclock.ui.ringtone

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MusicPlaylistsWorkspace(
    repository: MusicPlaylistRepository?,
    selectedTrackUris: List<String>,
    initialTargetPlaylistId: String?,
    onSelectionHandled: () -> Unit,
    onAddTracksToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<MusicPlaylist>?>(null) }
    var items by remember { mutableStateOf<List<MusicPlaylistItem>?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMode by remember { mutableStateOf<PlaylistDialogMode?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var pendingExcludedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExcludedTrackUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingDeletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var previousOrder by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(initialTargetPlaylistId) {
        if (initialTargetPlaylistId != null) {
            selectedPlaylistId = initialTargetPlaylistId
        }
    }

    LaunchedEffect(repository) {
        playlists = null
        if (repository != null) {
            try {
                repository.observePlaylists().collect { playlists = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: error.javaClass.simpleName
            }
        }
    }
    LaunchedEffect(repository, selectedPlaylistId) {
        items = null
        selectedPlaylistItemIds = emptySet()
        if (repository != null && selectedPlaylistId != null) {
            try {
                repository.observeItems(selectedPlaylistId!!).collect {
                    items = it.sortedBy(MusicPlaylistItem::position)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: error.javaClass.simpleName
            }
        }
    }

    val addSelectionToPlaylist: (String, ExcludedTrackResolution, List<String>) -> Unit =
        { playlistId, resolution, trackUris ->
            val repo = repository
            if (repo == null || busy) {
                status = null
            } else {
                scope.launch {
                    busy = true
                    try {
                        val result = repo.addTracks(
                            playlistId = playlistId,
                            selectedUris = trackUris,
                            excludedResolution = resolution
                        )
                        if (result.excludedItemsSkipped > 0 &&
                            resolution == ExcludedTrackResolution.KEEP_EXCLUDED
                        ) {
                            if (result.excludedTrackUris.isEmpty()) {
                                status = "excluded-uri-missing"
                                onSelectionHandled()
                            } else {
                                pendingExcludedPlaylistId = playlistId
                                pendingExcludedTrackUris = result.excludedTrackUris
                                status = when {
                                    result.addedItems > 0 &&
                                        result.unavailableItemsSkipped > 0 ->
                                        "partial:${result.addedItems}:${result.unavailableItemsSkipped}"
                                    result.addedItems > 0 -> "added:${result.addedItems}"
                                    result.unavailableItemsSkipped > 0 ->
                                        "unavailable:${result.unavailableItemsSkipped}"
                                    else -> null
                                }
                            }
                        } else {
                            onSelectionHandled()
                            status = when {
                                result.addedItems > 0 &&
                                    result.unavailableItemsSkipped > 0 ->
                                    "partial:${result.addedItems}:${result.unavailableItemsSkipped}"
                                result.addedItems > 0 -> "added:${result.addedItems}"
                                result.unavailableItemsSkipped > 0 ->
                                    "unavailable:${result.unavailableItemsSkipped}"
                                else -> "added:0"
                            }
                            if (result.addedItems > 0) {
                                selectedPlaylistId = playlistId
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        status = error.message ?: error.javaClass.simpleName
                    } finally {
                        busy = false
                    }
                }
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("music-playlists-workspace"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (repository == null) {
            Text(
                stringResource(R.string.music_playlist_integration_pending),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(enabled = false, onClick = {}) {
                Text(stringResource(R.string.music_playlist_create))
            }
        } else if (selectedPlaylistId != null) {
            val playlist = playlists?.firstOrNull { it.playlistId == selectedPlaylistId }
            val playlistIsEditable = playlist?.kind == MusicPlaylistKind.NAMED ||
                playlist?.kind == MusicPlaylistKind.ALARM_COPY ||
                playlist?.kind == MusicPlaylistKind.FAVORITES
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    selectedPlaylistId = null
                    previousOrder = null
                }) {
                    Text(stringResource(R.string.music_playlist_back))
                }
                Text(
                    playlist?.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedTrackUris.isNotEmpty() && playlistIsEditable) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            addSelectionToPlaylist(
                                selectedPlaylistId!!,
                                ExcludedTrackResolution.KEEP_EXCLUDED,
                                selectedTrackUris
                            )
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.music_playlist_add_selected,
                                selectedTrackUris.size
                            )
                        )
                    }
                } else {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onAddTracksToPlaylist(selectedPlaylistId!!) }
                    ) {
                        Text(stringResource(R.string.music_playlist_add_tracks))
                    }
                }
            }
            playlist?.let {
                Text(
                    stringResource(
                        R.string.music_playlist_track_count,
                        it.playableTrackCount,
                        it.unavailableTrackCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (selectedPlaylistItemIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.music_playlist_selected_items,
                            selectedPlaylistItemIds.size
                        )
                    )
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val playlistId = selectedPlaylistId ?: return@TextButton
                            scope.launch {
                                busy = true
                                try {
                                    val repo = repository ?: return@launch
                                    repo.removeItems(
                                        playlistId,
                                        selectedPlaylistItemIds.toList()
                                    )
                                    status = "removed:${selectedPlaylistItemIds.size}"
                                    selectedPlaylistItemIds = emptySet()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    status = error.message ?: error.javaClass.simpleName
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.music_playlist_remove_selected))
                    }
                }
            }
            if (playlistIsEditable) previousOrder?.let { oldOrder ->
                TextButton(
                    onClick = {
                        val repo = repository
                        val playlistId = selectedPlaylistId
                        if (repo != null && playlistId != null) {
                            scope.launch {
                                try {
                                    repo.reorder(playlistId, oldOrder)
                                    previousOrder = null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    status = error.message ?: error.javaClass.simpleName
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.music_playlist_undo_order))
                }
            }
            if (items == null) {
                CircularProgressIndicator()
            } else if (items.orEmpty().isEmpty()) {
                Text(stringResource(R.string.music_playlist_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items.orEmpty(), key = MusicPlaylistItem::playlistItemId) { item ->
                        PlaylistItemRow(
                            item = item,
                            editable = playlistIsEditable,
                            modifier = Modifier.pointerInput(
                                item.playlistItemId,
                                items,
                                playlistIsEditable
                            ) {
                                if (!playlistIsEditable) return@pointerInput
                                var totalDrag = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        val stepCount = (kotlin.math.abs(totalDrag) / 64f)
                                            .toInt()
                                            .coerceAtLeast(if (totalDrag == 0f) 0 else 1)
                                        if (stepCount > 0) {
                                            val offset = if (totalDrag < 0f) -stepCount else stepCount
                                            movePlaylistItem(
                                                item = item,
                                                offset = offset,
                                                currentItems = items.orEmpty(),
                                                repository = repository,
                                                playlistId = selectedPlaylistId,
                                                onPreviousOrder = { previousOrder = it },
                                                onStatus = { status = it },
                                                scope = scope
                                            )
                                        }
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDrag += dragAmount.y
                                    }
                                )
                            },
                            selected = item.playlistItemId in selectedPlaylistItemIds,
                            onSelectionChange = {
                                selectedPlaylistItemIds = if (
                                    item.playlistItemId in selectedPlaylistItemIds
                                ) {
                                    selectedPlaylistItemIds - item.playlistItemId
                                } else {
                                    selectedPlaylistItemIds + item.playlistItemId
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    try {
                                        val repo = repository ?: return@launch
                                        repo.removeItem(item.playlistItemId)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        status = error.message ?: error.javaClass.simpleName
                                    }
                                }
                            },
                            onMove = { offset ->
                                movePlaylistItem(
                                    item = item,
                                    offset = offset,
                                    currentItems = items.orEmpty(),
                                    repository = repository,
                                    playlistId = selectedPlaylistId,
                                    onPreviousOrder = { previousOrder = it },
                                    onStatus = { status = it },
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.music_playlist_title),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        name = ""
                        dialogMode = PlaylistDialogMode.CREATE
                    }
                ) {
                    Text(stringResource(R.string.music_playlist_create))
                }
            }
            if (selectedTrackUris.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.music_playlist_selected_tracks,
                        selectedTrackUris.size
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (playlists == null) {
                CircularProgressIndicator()
            } else if (playlists.orEmpty().isEmpty()) {
                Text(stringResource(R.string.music_playlist_none))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists.orEmpty(), key = MusicPlaylist::playlistId) { playlist ->
                        PlaylistSummaryRow(
                            playlist = playlist,
                            hasTracksToAdd = selectedTrackUris.isNotEmpty() &&
                                (playlist.kind == MusicPlaylistKind.NAMED ||
                                    playlist.kind == MusicPlaylistKind.ALARM_COPY ||
                                    playlist.kind == MusicPlaylistKind.FAVORITES),
                            busy = busy,
                            onOpen = { selectedPlaylistId = playlist.playlistId },
                            onAddSelected = {
                                addSelectionToPlaylist(
                                    playlist.playlistId,
                                    ExcludedTrackResolution.KEEP_EXCLUDED,
                                    selectedTrackUris
                                )
                            },
                            onRename = {
                                name = playlist.name
                                dialogMode = PlaylistDialogMode.RENAME(playlist.playlistId)
                            },
                            onCopy = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val repo = repository ?: return@launch
                                        val copy = repo.duplicate(playlist.playlistId)
                                        status = "copied:${copy.name}"
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        status = error.message ?: error.javaClass.simpleName
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            onDelete = {
                                pendingDeletePlaylistId = playlist.playlistId
                            }
                        )
                    }
                }
            }
        }

        if (busy) CircularProgressIndicator()
        playlistStatusText(status)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    val dialog = dialogMode
    if (dialog != null) {
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = {
                Text(
                    stringResource(
                        if (dialog == PlaylistDialogMode.CREATE) {
                            R.string.music_playlist_create
                        } else {
                            R.string.music_playlist_rename
                        }
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.music_playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val repo = repository ?: return@launch
                                when (dialog) {
                                    PlaylistDialogMode.CREATE -> {
                                        val created = repo.create(name.trim())
                                        if (selectedTrackUris.isNotEmpty()) {
                                            val result = repo.addTracks(
                                                created.playlistId,
                                                selectedTrackUris,
                                                ExcludedTrackResolution.KEEP_EXCLUDED
                                            )
                                            if (result.excludedItemsSkipped > 0) {
                                                if (result.excludedTrackUris.isEmpty()) {
                                                    status = "excluded-uri-missing"
                                                    onSelectionHandled()
                                                } else {
                                                    pendingExcludedPlaylistId = created.playlistId
                                                    pendingExcludedTrackUris =
                                                        result.excludedTrackUris
                                                }
                                                if (result.addedItems > 0 &&
                                                    result.excludedTrackUris.isNotEmpty()
                                                ) {
                                                    status = if (result.unavailableItemsSkipped > 0) {
                                                        "partial:${result.addedItems}:${result.unavailableItemsSkipped}"
                                                    } else {
                                                        "added:${result.addedItems}"
                                                    }
                                                }
                                            } else {
                                                onSelectionHandled()
                                                status = when {
                                                    result.addedItems > 0 &&
                                                        result.unavailableItemsSkipped > 0 ->
                                                        "partial:${result.addedItems}:${result.unavailableItemsSkipped}"
                                                    result.addedItems > 0 ->
                                                        "added:${result.addedItems}"
                                                    result.unavailableItemsSkipped > 0 ->
                                                        "unavailable:${result.unavailableItemsSkipped}"
                                                    else -> "added:0"
                                                }
                                            }
                                        }
                                        selectedPlaylistId = created.playlistId
                                    }
                                    is PlaylistDialogMode.RENAME -> {
                                        repo.rename(dialog.playlistId, name.trim())
                                    }
                                }
                                dialogMode = null
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                status = error.message ?: error.javaClass.simpleName
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.music_playlist_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text(stringResource(R.string.music_playlist_cancel))
                }
            }
        )
    }

    pendingExcludedPlaylistId?.let { playlistId ->
        AlertDialog(
            onDismissRequest = {
                pendingExcludedPlaylistId = null
                pendingExcludedTrackUris = emptyList()
            },
            title = { Text(stringResource(R.string.music_playlist_restore_excluded_title)) },
            text = { Text(stringResource(R.string.music_playlist_restore_excluded_message)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingExcludedPlaylistId = null
                        val excludedUris = pendingExcludedTrackUris
                        pendingExcludedTrackUris = emptyList()
                        addSelectionToPlaylist(
                            playlistId,
                            ExcludedTrackResolution.RESTORE_EXCLUDED,
                            excludedUris
                        )
                    }
                ) {
                    Text(stringResource(R.string.music_playlist_restore_excluded_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingExcludedPlaylistId = null
                    pendingExcludedTrackUris = emptyList()
                    onSelectionHandled()
                }) {
                    Text(stringResource(R.string.music_playlist_cancel))
                }
            }
        )
    }

    pendingDeletePlaylistId?.let { playlistId ->
        val playlistName = playlists.orEmpty()
            .firstOrNull { it.playlistId == playlistId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeletePlaylistId = null },
            title = { Text(stringResource(R.string.music_playlist_delete_confirm)) },
            text = {
                Text(stringResource(R.string.music_playlist_delete_confirm_message, playlistName))
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDeletePlaylistId = null
                        scope.launch {
                            busy = true
                            try {
                                val repo = repository ?: return@launch
                                when (
                                    val result = repo.deleteWithReferencesCheck(playlistId)
                                ) {
                                    DeletePlaylistResult.Deleted -> status = "deleted"
                                    is DeletePlaylistResult.BlockedByAlarmReferences ->
                                        status = "delete-blocked:${result.referencingAlarmCount}"
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                status = error.message ?: error.javaClass.simpleName
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.music_playlist_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlaylistId = null }) {
                    Text(stringResource(R.string.music_playlist_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistSummaryRow(
    playlist: MusicPlaylist,
    hasTracksToAdd: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onAddSelected: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(playlist.playlistId) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("playlist-${playlist.playlistId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextButton(onClick = onOpen, enabled = !busy) {
                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stringResource(
                    R.string.music_playlist_track_count,
                    playlist.playableTrackCount,
                    playlist.unavailableTrackCount
                ),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (hasTracksToAdd) {
            OutlinedButton(onClick = onAddSelected, enabled = !busy) {
                Text(stringResource(R.string.music_playlist_add))
            }
        }
        if (playlist.kind == MusicPlaylistKind.NAMED ||
            playlist.kind == MusicPlaylistKind.ALARM_COPY
        ) {
            IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.music_playlist_actions)
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_playlist_rename)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_playlist_copy)) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_playlist_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: MusicPlaylistItem,
    editable: Boolean,
    modifier: Modifier,
    selected: Boolean,
    onSelectionChange: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("playlist-item-${item.playlistItemId}"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                enabled = editable,
                onCheckedChange = { onSelectionChange() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.availability != MediaAvailability.PLAYABLE) {
                    Text(
                        stringResource(R.string.music_playlist_item_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.music_playlist_item_drag_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(enabled = editable, onClick = { onMove(-1) }) {
                Text(stringResource(R.string.music_playlist_move_up))
            }
            TextButton(enabled = editable, onClick = { onMove(1) }) {
                Text(stringResource(R.string.music_playlist_move_down))
            }
            TextButton(
                enabled = editable,
                modifier = Modifier.testTag("playlist-remove-${item.playlistItemId}"),
                onClick = onRemove
            ) {
                Text(stringResource(R.string.music_playlist_remove))
            }
        }
    }
}

private fun movePlaylistItem(
    item: MusicPlaylistItem,
    offset: Int,
    currentItems: List<MusicPlaylistItem>,
    repository: MusicPlaylistRepository?,
    playlistId: String?,
    onPreviousOrder: (List<String>) -> Unit,
    onStatus: (String?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val from = currentItems.indexOfFirst { it.playlistItemId == item.playlistItemId }
    val to = (from + offset).coerceIn(0, currentItems.lastIndex)
    if (from < 0 || from == to || repository == null || playlistId == null) return
    val oldOrder = currentItems.map(MusicPlaylistItem::playlistItemId)
    val newOrder = oldOrder.toMutableList().apply {
        add(to, removeAt(from))
    }
    scope.launch {
        try {
            repository.reorder(playlistId, newOrder)
            onPreviousOrder(oldOrder)
            onStatus(null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onStatus(error.message ?: error.javaClass.simpleName)
        }
    }
}

private sealed interface PlaylistDialogMode {
    data object CREATE : PlaylistDialogMode
    data class RENAME(val playlistId: String) : PlaylistDialogMode
}

@Composable
private fun playlistStatusText(status: String?): String? {
    return when {
        status == null -> null
        status == "excluded-uri-missing" ->
            stringResource(R.string.music_playlist_excluded_resolution_error)
        status == "deleted" -> stringResource(R.string.music_playlist_deleted)
        status == "added:0" -> stringResource(R.string.music_playlist_add_zero)
        status.startsWith("removed:") -> {
            val count = status.substringAfter(':').toIntOrNull() ?: return null
            stringResource(R.string.music_playlist_removed_count, count)
        }
        status.startsWith("added:") -> {
            val count = status.substringAfter(':').toIntOrNull() ?: return null
            stringResource(R.string.music_playlist_added_count, count)
        }
        status.startsWith("unavailable:") -> {
            val count = status.substringAfter(':').toIntOrNull() ?: return null
            stringResource(R.string.music_playlist_unavailable_count, count)
        }
        status.startsWith("partial:") -> {
            val counts = status.substringAfter(':').split(':')
            val added = counts.getOrNull(0)?.toIntOrNull() ?: return null
            val unavailable = counts.getOrNull(1)?.toIntOrNull() ?: return null
            stringResource(R.string.music_playlist_partial_add, added, unavailable)
        }
        status.startsWith("copied:") -> stringResource(
            R.string.music_playlist_copied,
            status.substringAfter(':')
        )
        status.startsWith("delete-blocked:") -> {
            val count = status.substringAfter(':').toIntOrNull() ?: return null
            stringResource(R.string.music_playlist_delete_blocked, count)
        }
        else -> status
    }
}
