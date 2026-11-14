package com.sysadmindoc.alarmclock.ui.ringtone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import kotlinx.coroutines.launch

private enum class MusicLibraryTab(val labelRes: Int) {
    SONGS(R.string.music_library_tab_songs),
    VIDEOS(R.string.music_library_tab_videos),
    FOLDERS(R.string.music_library_tab_folders),
    ARTISTS(R.string.music_library_tab_artists),
    POOL(R.string.music_library_tab_pool)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryDialog(
    onDismiss: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
    previewController: MusicCatalogPreviewController? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var selectedTabName by rememberSaveable { mutableStateOf(MusicLibraryTab.SONGS.name) }
    val selectedTab = MusicLibraryTab.valueOf(selectedTabName)
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        viewModel.scan()
    }

    LaunchedEffect(viewModel) {
        viewModel.loadPoolCandidates()
        viewModel.scan()
    }
    DisposableEffect(previewController) {
        onDispose { previewController?.stop() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    when {
                                        showHistory -> showHistory = false
                                        selectedTab != MusicLibraryTab.SONGS ->
                                            selectedTabName = MusicLibraryTab.SONGS.name
                                        else -> onDismiss()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.music_library_back)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.music_library_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!showHistory) {
                                    Text(
                                        stringResource(
                                            R.string.music_library_track_count,
                                            state.poolTracks.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.music_library_history_title),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (!showHistory) {
                                TextButton(onClick = { showHistory = true }) {
                                    Text(stringResource(R.string.music_library_history_button))
                                }
                            }
                        }
                        if (!showHistory) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTab.ordinal,
                                edgePadding = 12.dp
                            ) {
                                MusicLibraryTab.values().forEach { tab ->
                                    Tab(
                                        selected = tab == selectedTab,
                                        onClick = {
                                            selectedTabName = tab.name
                                            viewModel.clearCatalogSelection()
                                        },
                                        text = { Text(stringResource(tab.labelRes)) }
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                if (showHistory) {
                    PlaybackHistoryContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    when (selectedTab) {
                        MusicLibraryTab.SONGS,
                        MusicLibraryTab.VIDEOS,
                        MusicLibraryTab.POOL -> MusicCatalogContent(
                            viewModel = viewModel,
                            state = state,
                            tab = selectedTab,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermission = { permissionLauncher.launch(permission) },
                            onManageSources = { showSources = true },
                            previewController = previewController,
                            modifier = Modifier.padding(padding)
                        )
                        MusicLibraryTab.FOLDERS -> MusicFoldersContent(
                            state = state,
                            onManageSources = { showSources = true },
                            onBrowseSource = { sourceId ->
                                viewModel.setCatalogSourceScope(MusicPoolSourceScope.Folder(sourceId))
                                selectedTabName = MusicLibraryTab.SONGS.name
                            },
                            onBrowseDeviceMedia = {
                                viewModel.setCatalogSourceScope(MusicPoolSourceScope.DeviceMedia)
                                selectedTabName = MusicLibraryTab.SONGS.name
                            },
                            modifier = Modifier.padding(padding)
                        )
                        MusicLibraryTab.ARTISTS -> ArtistCatalogUnavailable(
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
        if (showSources) {
            MusicLibrarySourcesDialog(
                viewModel = viewModel,
                onDismiss = { showSources = false }
            )
        }
    }
}

@Composable
private fun MusicCatalogContent(
    viewModel: MusicLibraryViewModel,
    state: MusicLibraryUiState,
    tab: MusicLibraryTab,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageSources: () -> Unit,
    previewController: MusicCatalogPreviewController?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val previewState by previewController?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(MusicCatalogPreviewState()) }
    val visible = viewModel.visibleCatalogTracks(
        state = state,
        videoOnly = tab == MusicLibraryTab.VIDEOS
    )
    val allVisibleSelected = visible.isNotEmpty() &&
        visible.all { it.track.uri in state.selectedCatalogUris }
    val selectedVisibleCount = visible.count { it.track.uri in state.selectedCatalogUris }
    val selectedUrisToAdd = catalogUrisForMembershipUpdate(
        visible,
        state.selectedCatalogUris,
        state.catalogFilter,
        included = true
    )
    val selectedUrisToExclude = catalogUrisForMembershipUpdate(
        visible,
        state.selectedCatalogUris,
        state.catalogFilter,
        included = false
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.catalogSearch,
            onValueChange = viewModel::setCatalogSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.music_library_catalog_search)) },
            singleLine = true
        )
        if (tab == MusicLibraryTab.SONGS && state.catalogSourceScope != null) {
            val scopeLabel = when (val scope = state.catalogSourceScope) {
                MusicPoolSourceScope.Folders ->
                    stringResource(R.string.music_library_pool_scope_folders)
                MusicPoolSourceScope.DeviceMedia ->
                    stringResource(R.string.music_library_pool_device_source)
                is MusicPoolSourceScope.Folder ->
                    state.sources.firstOrNull { it.id == scope.sourceId }?.name
                        ?: stringResource(R.string.music_library_pool_unknown_source)
                null -> ""
            }
            TextButton(onClick = { viewModel.setCatalogSourceScope(null) }) {
                Text(
                    stringResource(
                        R.string.music_library_source_scope_active,
                        scopeLabel,
                        stringResource(R.string.music_library_source_scope_all)
                    )
                )
            }
        }
        when (tab) {
            MusicLibraryTab.SONGS -> Text(
                stringResource(R.string.music_library_songs_description),
                style = MaterialTheme.typography.bodySmall
            )
            MusicLibraryTab.VIDEOS -> Text(
                stringResource(R.string.music_library_video_catalog_note),
                style = MaterialTheme.typography.bodySmall
            )
            MusicLibraryTab.POOL -> {
                Text(
                    stringResource(R.string.music_library_pool_non_destructive_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                CatalogSourceScopePicker(
                    state = state,
                    onSelect = viewModel::setCatalogSourceScope
                )
            }
            else -> Unit
        }
        if (tab == MusicLibraryTab.SONGS && previewController == null) {
            Text(
                stringResource(R.string.music_library_preview_unavailable),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (previewState.blockedByAlarm) {
            Text(
                stringResource(R.string.music_library_preview_blocked_by_alarm),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        previewState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogFilterChip(
                label = stringResource(R.string.music_library_pool_all_candidates),
                selected = state.catalogFilter == MusicPoolFilter.ALL_CANDIDATES,
                onClick = { viewModel.setCatalogFilter(MusicPoolFilter.ALL_CANDIDATES) }
            )
            CatalogFilterChip(
                label = stringResource(R.string.music_library_pool_included),
                selected = state.catalogFilter == MusicPoolFilter.INCLUDED,
                onClick = { viewModel.setCatalogFilter(MusicPoolFilter.INCLUDED) }
            )
            CatalogFilterChip(
                label = stringResource(R.string.music_library_pool_excluded),
                selected = state.catalogFilter == MusicPoolFilter.EXCLUDED,
                onClick = { viewModel.setCatalogFilter(MusicPoolFilter.EXCLUDED) }
            )
        }
        if (!hasAudioPermission) {
            Text(
                stringResource(R.string.music_library_catalog_permission),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.music_library_catalog_grant_permission))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(
                    R.string.music_library_pool_results,
                    visible.size,
                    selectedVisibleCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                enabled = visible.isNotEmpty() && !state.loadingPool && !state.scanning,
                onClick = {
                    viewModel.selectVisibleCatalogTracks(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = selectedUrisToAdd.isNotEmpty() && !state.loadingPool && !state.scanning,
                onClick = {
                    viewModel.updateSelectedCatalogMembership(
                        included = true,
                        videoOnly = tab == MusicLibraryTab.VIDEOS
                    )
                }
            ) {
                Text(
                    stringResource(
                        if (tab == MusicLibraryTab.POOL &&
                            state.catalogFilter == MusicPoolFilter.EXCLUDED
                        ) {
                            R.string.music_library_pool_restore
                        } else {
                            R.string.music_library_pool_add
                        }
                    )
                )
            }
            OutlinedButton(
                enabled = selectedUrisToExclude.isNotEmpty() && !state.loadingPool && !state.scanning,
                onClick = {
                    viewModel.updateSelectedCatalogMembership(
                        included = false,
                        videoOnly = tab == MusicLibraryTab.VIDEOS
                    )
                }
            ) {
                Text(stringResource(R.string.music_library_pool_exclude))
            }
            OutlinedButton(
                enabled = !state.scanning,
                onClick = {
                    if (hasAudioPermission) viewModel.scan() else onRequestPermission()
                }
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (state.scanning) R.string.music_library_scanning
                        else R.string.music_library_scan
                    )
                )
            }
            OutlinedButton(onClick = onManageSources) {
                Text(stringResource(R.string.music_library_sources_button))
            }
        }
        if (state.poolTracks.any { it.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE }) {
            Text(
                stringResource(R.string.music_library_pool_device_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.bodySmall)
        }
        if (state.loadingPool && state.poolTracks.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (visible.isEmpty()) {
            Text(
                stringResource(
                    if (state.poolTracks.isEmpty()) R.string.music_library_catalog_empty
                    else R.string.music_library_catalog_no_results
                ),
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visible, key = { it.track.uri }) { item ->
                    val membershipLabel = when (item.membershipState) {
                        MusicPoolEntry.INCLUDED ->
                            stringResource(R.string.music_library_pool_status_included)
                        MusicPoolEntry.EXCLUDED ->
                            stringResource(R.string.music_library_pool_status_excluded)
                        else ->
                            stringResource(R.string.music_library_pool_status_candidate)
                    }
                    val fallbackSource = stringResource(
                        if (item.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE) {
                            R.string.music_library_pool_device_source
                        } else {
                            R.string.music_library_pool_unknown_source
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.track.uri in state.selectedCatalogUris,
                            enabled = !state.loadingPool && !state.scanning,
                            onCheckedChange = { viewModel.toggleCatalogSelection(item.track.uri) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp, end = 8.dp)
                        ) {
                            Text(
                                item.track.displayTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.track.fileName.ifBlank { item.track.metadataTitle },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(
                                    if (item.track.isVideoAudio()) {
                                        R.string.music_library_video_audio_track_detected
                                    } else {
                                        R.string.music_library_catalog_source_membership
                                    },
                                    item.sourceName.ifBlank { fallbackSource },
                                    membershipLabel
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (tab == MusicLibraryTab.SONGS) {
                        val previewing = previewState.playingUri == item.track.uri
                        IconButton(
                            enabled = previewController != null,
                            onClick = {
                                val controller = previewController ?: return@IconButton
                                coroutineScope.launch {
                                    controller.togglePreview(item.track.uri)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (previewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (previewing) R.string.components_stop_preview
                                    else R.string.music_library_preview
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun CatalogSourceScopePicker(
    state: MusicLibraryUiState,
    onSelect: (MusicPoolSourceScope?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CatalogFilterChip(
            label = stringResource(R.string.music_library_source_scope_all),
            selected = state.catalogSourceScope == null,
            onClick = { onSelect(null) }
        )
        CatalogFilterChip(
            label = stringResource(R.string.music_library_pool_scope_folders),
            selected = state.catalogSourceScope == MusicPoolSourceScope.Folders,
            onClick = { onSelect(MusicPoolSourceScope.Folders) }
        )
        CatalogFilterChip(
            label = stringResource(R.string.music_library_pool_device_source),
            selected = state.catalogSourceScope == MusicPoolSourceScope.DeviceMedia,
            onClick = { onSelect(MusicPoolSourceScope.DeviceMedia) }
        )
        state.sources.forEach { source ->
            CatalogFilterChip(
                label = source.name,
                selected = state.catalogSourceScope == MusicPoolSourceScope.Folder(source.id),
                onClick = {
                    onSelect(MusicPoolSourceScope.Folder(source.id))
                }
            )
        }
    }
}

@Composable
private fun MusicFoldersContent(
    state: MusicLibraryUiState,
    onManageSources: () -> Unit,
    onBrowseSource: (String) -> Unit,
    onBrowseDeviceMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceTrackCount = state.poolTracks.count {
        it.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.music_library_folder_relative_paths_pending))
        OutlinedButton(onClick = onManageSources, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.music_library_sources_button))
        }
        if (state.sources.isEmpty() && deviceTrackCount == 0) {
            Text(stringResource(R.string.music_library_no_sources))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (deviceTrackCount > 0) {
                item(key = "device-media") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.music_library_folder_device_media),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(
                                R.string.music_library_folder_track_count,
                                deviceTrackCount
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = onBrowseDeviceMedia) {
                            Text(stringResource(R.string.music_library_folder_browse))
                        }
                    }
                }
            }
            items(state.sources, key = MusicLibrarySource::id) { source ->
                val sourceTracks = state.poolTracks.count { source.id in it.sourceIds }
                val status = when (source.status) {
                    MusicSourceStatus.NEEDS_SCAN ->
                        stringResource(R.string.music_library_source_needs_scan)
                    MusicSourceStatus.READY ->
                        stringResource(R.string.music_library_source_ready)
                    MusicSourceStatus.ERROR ->
                        stringResource(R.string.music_library_source_error)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.music_library_folder_track_count, sourceTracks),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (source.error.isNotBlank()) {
                        Text(
                            source.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        enabled = sourceTracks > 0,
                        onClick = { onBrowseSource(source.id) }
                    ) {
                        Text(stringResource(R.string.music_library_folder_browse))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCatalogUnavailable(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.music_library_artist_catalog_unavailable),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.music_library_artist_catalog_contract),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PlaybackHistoryContent(
    viewModel: MusicLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                java.time.format.DateTimeFormatter
                    .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(context.resources.configuration.locales[0])
                    .format(state.selectedDate),
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(
                onClick = {
                    val date = state.selectedDate
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            viewModel.loadHistory(java.time.LocalDate.of(year, month + 1, day))
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).show()
                }
            ) {
                Text(stringResource(R.string.music_library_choose_date))
            }
        }
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.music_library_no_history))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { it.id }) { entry ->
                    val time = android.text.format.DateFormat.getTimeFormat(context).format(
                        java.util.Date(entry.startedAt)
                    )
                    Text(
                        stringResource(
                            R.string.music_library_history_row,
                            entry.playOrder,
                            time,
                            entry.title
                        )
                    )
                }
            }
        }
    }
}
