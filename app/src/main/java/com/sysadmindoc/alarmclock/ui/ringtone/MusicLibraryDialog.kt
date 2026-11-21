package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
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
import com.sysadmindoc.alarmclock.service.ReplayUiState
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import com.sysadmindoc.alarmclock.ui.components.HistoryTrackRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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

private enum class MusicLibraryWorkspace {
    MEDIA_LIBRARY,
    PLAYLISTS
}

private enum class MusicLibraryCategory {
    ALL,
    MUSIC,
    VIDEOS
}

private enum class MusicLibraryBrowseMode {
    MEDIA,
    FOLDERS,
    ARTISTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryDialog(
    onDismiss: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
    previewController: MusicCatalogPreviewController? = viewModel.previewController,
    playlistRepository: MusicPlaylistRepository? = viewModel.playlistRepository,
    initialShowHistory: Boolean = false,
    initialShowPlaylists: Boolean = false,
    /** Editor-only callback; other Music Center entry points remain read/write standalone. */
    onPlaylistChosenForAlarm: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(initialShowHistory) }
    var showSources by remember { mutableStateOf(false) }
    var workspaceName by rememberSaveable {
        mutableStateOf(
            if (initialShowPlaylists) MusicLibraryWorkspace.PLAYLISTS.name
            else MusicLibraryWorkspace.MEDIA_LIBRARY.name
        )
    }
    var targetPlaylistForAdd by rememberSaveable { mutableStateOf<String?>(null) }
    val workspace = MusicLibraryWorkspace.valueOf(workspaceName)
    var categoryName by rememberSaveable { mutableStateOf(MusicLibraryCategory.ALL.name) }
    val category = MusicLibraryCategory.valueOf(categoryName)
    var browseModeName by rememberSaveable { mutableStateOf(MusicLibraryBrowseMode.FOLDERS.name) }
    val browseMode = MusicLibraryBrowseMode.valueOf(browseModeName)
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            permissions.any {
                ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasAudioPermission = grants.values.any { it }
        viewModel.scan()
    }

    LaunchedEffect(viewModel) {
        // Read saved index immediately. Rescan only if no indexed tracks exist.
        // Previously every opening forced a multi-thousand-file SAF scan,
        // disabling selection and hiding fast searches until it completed.
        viewModel.loadPoolCandidates(autoScanIfEmpty = true)
    }
    DisposableEffect(previewController) {
        onDispose { previewController?.stop() }
    }
    LaunchedEffect(workspace, browseMode, showHistory, previewController) {
        if (workspace != MusicLibraryWorkspace.MEDIA_LIBRARY ||
            browseMode != MusicLibraryBrowseMode.MEDIA || showHistory
        ) {
            previewController?.stop()
        }
    }

    if (showSources) {
        MusicLibrarySourcesDialog(
            viewModel = viewModel,
            onDismiss = { showSources = false }
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    topBar = {
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
                                            showHistory -> {
                                                if (initialShowHistory) onDismiss() else showHistory = false
                                            }
                                            browseMode != MusicLibraryBrowseMode.MEDIA ->
                                                browseModeName = MusicLibraryBrowseMode.MEDIA.name
                                            workspace != MusicLibraryWorkspace.MEDIA_LIBRARY -> {
                                                workspaceName = MusicLibraryWorkspace.MEDIA_LIBRARY.name
                                                viewModel.clearCatalogSelection()
                                            }
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
                                        Lp2PreviewText.text(R.string.music_library_title, "music_library_title"),
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = if (Lp2PreviewText.showsTwoLanguages()) 2 else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (showHistory) {
                                            Lp2PreviewText.text(R.string.music_library_history_title, "music_library_history_title")
                                        } else {
                                            stringResource(
                                                R.string.music_library_track_count,
                                                state.poolTracks.size
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!showHistory) {
                                    TextButton(onClick = { showHistory = true }) {
                                        Text(stringResource(R.string.music_library_history_button))
                                    }
                                }
                            }
                            if (!showHistory) {
                                androidx.compose.material3.TabRow(
                                    selectedTabIndex = workspace.ordinal
                                ) {
                                    MusicLibraryWorkspace.values().forEach { page ->
                                        Tab(
                                            selected = page == workspace,
                                            onClick = {
                                                workspaceName = page.name
                                                if (page != workspace) {
                                                    viewModel.clearCatalogSelection()
                                                }
                                            },
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (page == MusicLibraryWorkspace.MEDIA_LIBRARY) {
                                                            R.string.music_library_workspace_media
                                                        } else {
                                                            R.string.music_library_workspace_playlists
                                                        }
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    when {
                        showHistory -> PlaybackHistoryContent(
                            viewModel = viewModel,
                            playlistRepository = playlistRepository,
                            modifier = Modifier.padding(padding)
                        )
                        workspace == MusicLibraryWorkspace.PLAYLISTS -> MusicPlaylistsWorkspace(
                            repository = playlistRepository,
                            selectedTrackUris = state.selectedCatalogUris.toList(),
                            initialTargetPlaylistId = targetPlaylistForAdd,
                            onSelectionHandled = {
                                viewModel.clearCatalogSelection()
                                targetPlaylistForAdd = null
                            },
                            onAddTracksToPlaylist = { playlistId ->
                                targetPlaylistForAdd = playlistId
                                viewModel.clearCatalogSelection()
                                workspaceName = MusicLibraryWorkspace.MEDIA_LIBRARY.name
                            },
                            onApplyToAlarm = onPlaylistChosenForAlarm,
                            modifier = Modifier.padding(padding)
                        )
                        browseMode == MusicLibraryBrowseMode.FOLDERS -> MusicFoldersContent(
                            state = state,
                            viewModel = viewModel,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermission = { permissionLauncher.launch(permissions) },
                            onManageSources = { showSources = true },
                            onBrowseSource = { sourceId ->
                                viewModel.setCatalogSourceScope(MusicPoolSourceScope.Folder(sourceId))
                                browseModeName = MusicLibraryBrowseMode.MEDIA.name
                            },
                            onBrowseDeviceFolder = { path ->
                                viewModel.setCatalogSourceScope(MusicPoolSourceScope.DeviceFolder(path))
                                browseModeName = MusicLibraryBrowseMode.MEDIA.name
                            },
                            modifier = Modifier.padding(padding)
                        )
                        browseMode == MusicLibraryBrowseMode.ARTISTS -> ArtistCatalogContent(
                            artists = state.supplementalCatalog.artists,
                            onSelectArtist = { artistName ->
                                viewModel.setCatalogSearch(artistName)
                                browseModeName = MusicLibraryBrowseMode.MEDIA.name
                            },
                            modifier = Modifier.padding(padding)
                        )
                        else -> MusicCatalogContent(
                            viewModel = viewModel,
                            state = state,
                            category = category,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermission = { permissionLauncher.launch(permissions) },
                            onManageSources = { showSources = true },
                            onOpenPlaylists = { selectedUris ->
                                viewModel.selectVisibleCatalogTracks(selectedUris)
                                workspaceName = MusicLibraryWorkspace.PLAYLISTS.name
                            },
                            onOpenFolders = {
                                viewModel.clearCatalogSelection()
                                browseModeName = MusicLibraryBrowseMode.FOLDERS.name
                            },
                            onOpenArtists = {
                                viewModel.clearCatalogSelection()
                                browseModeName = MusicLibraryBrowseMode.ARTISTS.name
                            },
                            onCategoryChange = { next ->
                                viewModel.clearCatalogSelection()
                                categoryName = next.name
                            },
                            previewController = previewController,
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicCatalogContent(
    viewModel: MusicLibraryViewModel,
    state: MusicLibraryUiState,
    category: MusicLibraryCategory,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageSources: () -> Unit,
    onOpenPlaylists: (List<String>) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenArtists: () -> Unit,
    onCategoryChange: (MusicLibraryCategory) -> Unit,
    previewController: MusicCatalogPreviewController?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Freeze the candidate identities and display titles at the moment
    // the user opens review. A background refresh must not silently expand
    // the reviewed bulk-operation scope while this dialog is on screen.
    var reviewedSystemToneCandidates by remember {
        mutableStateOf<List<MusicPoolUiTrack>>(emptyList())
    }
    var unselectedSystemToneUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val systemToneCandidates = remember(
        state.poolTracks, state.supplementalCatalog.trackMetadataByUri
    ) {
        recognizedSystemToneCandidates(
            state.poolTracks, state.supplementalCatalog.trackMetadataByUri
        )
    }
    if (reviewedSystemToneCandidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { reviewedSystemToneCandidates = emptyList() },
            title = { Text(stringResource(R.string.music_library_system_tones_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(
                        R.string.music_library_system_tones_preview_detail,
                        reviewedSystemToneCandidates.size
                    ))
                    // Display all frozen candidates rather than only the
                    // first five. A false positive can be unchecked before
                    // committing a single bulk exclusion.
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(
                            reviewedSystemToneCandidates,
                            key = { it.track.uri }
                        ) { candidate ->
                            val uri = candidate.track.uri
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uri !in unselectedSystemToneUris,
                                    onCheckedChange = { checked ->
                                        unselectedSystemToneUris =
                                            if (checked) unselectedSystemToneUris - uri
                                            else unselectedSystemToneUris + uri
                                    }
                                )
                                Text(
                                    candidate.track.displayTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.music_library_system_tones_false_positive_warning),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reviewedSystemToneCandidates.any {
                        it.track.uri !in unselectedSystemToneUris
                    } && !state.loadingPool && !state.scanning,
                    onClick = {
                        // Send the exact reviewed snapshot; the ViewModel
                        // revalidates it against the current indexed catalog
                        // and can never add an unreviewed candidate.
                        val confirmed = reviewedSystemToneCandidates
                            .map { it.track.uri }
                            .filterNot { it in unselectedSystemToneUris }
                        reviewedSystemToneCandidates = emptyList()
                        unselectedSystemToneUris = emptySet()
                        viewModel.excludeRecognizedSystemTones(confirmed)
                    }
                ) {
                    Text(stringResource(R.string.music_library_system_tones_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewedSystemToneCandidates = emptyList() }) {
                    Text(Lp2PreviewText.text(R.string.music_library_system_tones_cancel, "music_library_system_tones_cancel"))
                }
            }
        )
    }
    val previewState by previewController?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(MusicCatalogPreviewState()) }
    // Cache the 4,000+ track filter across unrelated scan/status/audio-state
    // recompositions. Only recalculate when search, scope or catalog changes.
    val visible = remember(
        state.poolTracks, state.catalogFilter, state.catalogSearch,
        state.catalogSourceScope, state.supplementalCatalog.trackMetadataByUri,
        category
    ) {
        val categoryTracks = viewModel.visibleCatalogTracks(
            state = state,
            videoOnly = category == MusicLibraryCategory.VIDEOS
        )
        when (category) {
            MusicLibraryCategory.ALL, MusicLibraryCategory.VIDEOS -> categoryTracks
            MusicLibraryCategory.MUSIC -> categoryTracks.filterNot { it.track.isVideoAudio() }
        }
    }
    val allVisibleSelected = visible.isNotEmpty() &&
        visible.all { it.track.uri in state.selectedCatalogUris }
    val selectedVisibleCount = visible.count { it.track.uri in state.selectedCatalogUris }
    val selectedForPlaylist = visible.asSequence()
        .filter { it.track.uri in state.selectedCatalogUris }
        .map { it.track.uri }
        .toList()
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogFilterChip(
                label = stringResource(R.string.music_library_category_all),
                selected = category == MusicLibraryCategory.ALL,
                onClick = { onCategoryChange(MusicLibraryCategory.ALL) }
            )
            CatalogFilterChip(
                label = Lp2PreviewText.text(R.string.music_library_category_music, "music_library_category_music"),
                selected = category == MusicLibraryCategory.MUSIC,
                onClick = { onCategoryChange(MusicLibraryCategory.MUSIC) }
            )
            CatalogFilterChip(
                label = stringResource(R.string.music_library_category_videos),
                selected = category == MusicLibraryCategory.VIDEOS,
                onClick = { onCategoryChange(MusicLibraryCategory.VIDEOS) }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onOpenFolders) {
                Text(stringResource(R.string.music_library_browse_folders))
            }
            OutlinedButton(onClick = onOpenArtists) {
                Text(stringResource(R.string.music_library_browse_artists_albums))
            }
        }
        if (state.catalogSourceScope != null) {
            val scopeLabel = when (val scope = state.catalogSourceScope) {
                MusicPoolSourceScope.Folders ->
                    stringResource(R.string.music_library_pool_scope_folders)
                MusicPoolSourceScope.DeviceMedia ->
                    stringResource(R.string.music_library_pool_device_source)
                is MusicPoolSourceScope.DeviceFolder ->
                    scope.relativePath.ifBlank {
                        stringResource(R.string.music_library_pool_device_source)
                    }
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
                        Lp2PreviewText.text(R.string.music_library_source_scope_all, "music_library_source_scope_all")
                    )
                )
            }
        }
        Text(
            stringResource(
                if (category == MusicLibraryCategory.VIDEOS) {
                    R.string.music_library_video_catalog_note
                } else {
                    R.string.music_library_songs_description
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (previewController == null) {
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
                label = Lp2PreviewText.text(R.string.music_library_pool_all_candidates, "music_library_pool_all_candidates"),
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
                    com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText.textForResource(
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
                        videoOnly = category == MusicLibraryCategory.VIDEOS
                    )
                }
            ) {
                Text(
                    stringResource(
                        if (state.catalogFilter == MusicPoolFilter.EXCLUDED) {
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
                        videoOnly = category == MusicLibraryCategory.VIDEOS
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                enabled = systemToneCandidates.isNotEmpty() &&
                    !state.loadingPool && !state.scanning,
                onClick = {
                    reviewedSystemToneCandidates = systemToneCandidates.toList()
                    unselectedSystemToneUris = emptySet()
                }
            ) {
                Text(stringResource(
                    R.string.music_library_system_tones_preview_button,
                    systemToneCandidates.size
                ))
            }
            if (state.systemToneUndoCount > 0) {
                TextButton(
                    enabled = !state.loadingPool && !state.scanning,
                    onClick = viewModel::undoRecognizedSystemToneExclusion
                ) { Text(stringResource(R.string.music_library_system_tones_undo)) }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedForPlaylist.isNotEmpty() &&
                !state.loadingPool && !state.scanning,
            onClick = { onOpenPlaylists(selectedForPlaylist) }
        ) {
            Text(
                stringResource(
                    R.string.music_library_add_to_playlist,
                    selectedForPlaylist.size
                )
            )
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
                com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText.textForResource(
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
                            Lp2PreviewText.text(R.string.music_library_pool_status_included, "music_library_pool_status_included")
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
                    MusicCatalogTrackRow(
                        item = item,
                        selected = item.track.uri in state.selectedCatalogUris,
                        selectionEnabled = !state.loadingPool && !state.scanning,
                        membershipLabel = membershipLabel,
                        sourceFallback = fallbackSource,
                        showPreview = true,
                        previewEnabled = previewController != null,
                        previewing = previewState.playingUri == item.track.uri,
                        onSelectionChange = {
                            viewModel.toggleCatalogSelection(item.track.uri)
                        },
                        onPreview = {
                            val controller = previewController
                            if (controller != null) {
                                coroutineScope.launch {
                                    controller.togglePreview(item.track.uri)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MusicCatalogTrackRow(
    item: MusicPoolUiTrack,
    selected: Boolean,
    selectionEnabled: Boolean,
    membershipLabel: String,
    sourceFallback: String,
    showPreview: Boolean,
    previewEnabled: Boolean,
    previewing: Boolean,
    onSelectionChange: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.track.uri, showPreview, previewEnabled) {
                detectTapGestures(
                    onLongPress = { onSelectionChange() },
                    onTap = {
                        if (showPreview && previewEnabled) onPreview()
                    }
                )
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            enabled = selectionEnabled,
            modifier = Modifier.testTag("music-catalog-track-selection"),
            onCheckedChange = { onSelectionChange() }
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
                    item.sourceName.ifBlank { sourceFallback },
                    membershipLabel
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showPreview) {
            IconButton(
                enabled = previewEnabled,
                onClick = onPreview
            ) {
                Icon(
                    imageVector = if (previewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText.textForResource(
                        if (previewing) R.string.components_stop_preview
                        else R.string.music_library_preview
                    )
                )
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
            label = Lp2PreviewText.text(R.string.music_library_source_scope_all, "music_library_source_scope_all"),
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
    viewModel: MusicLibraryViewModel,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageSources: () -> Unit,
    onBrowseSource: (String) -> Unit,
    onBrowseDeviceFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceFolders = groupMediaStoreFolders(
        state.poolTracks, state.supplementalCatalog.trackMetadataByUri
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.music_library_pool_non_destructive_hint))
        if (!hasAudioPermission) {
            Text(stringResource(R.string.music_library_catalog_permission),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.music_library_catalog_grant_permission))
            }
        }
        OutlinedButton(onClick = onManageSources, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.music_library_sources_button))
        }
        if (state.sources.isEmpty() && deviceFolders.isEmpty()) {
            Text(stringResource(R.string.music_library_no_sources))
        }
        if (deviceFolders.isNotEmpty()) {
            Text(stringResource(R.string.music_library_pool_device_warning),
                style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deviceFolders, key = { "media:${it.relativePath}" }) { folder ->
                val key = MusicFolderPoolRules.mediaStoreKey(folder.relativePath)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(folder.relativePath.ifBlank {
                        stringResource(R.string.music_library_folder_device_media)
                    }, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.music_library_source_counts,
                        folder.trackCount, folder.audioCount, folder.videoCount),
                        style = MaterialTheme.typography.bodySmall)
                    Text("${folder.includedCount}/${folder.trackCount} · " +
                        stringResource(R.string.music_library_pool_included),
                        style = MaterialTheme.typography.bodySmall)
                    state.folderPoolRules[key]?.let { rule ->
                        Text(stringResource(if (rule == MusicFolderPoolRules.INCLUDE)
                            R.string.music_library_pool_status_included
                        else R.string.music_library_pool_status_excluded),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onBrowseDeviceFolder(folder.relativePath) }) {
                            Text(stringResource(R.string.music_library_folder_browse))
                        }
                        TextButton(enabled = !state.loadingPool && !state.scanning,
                            onClick = { viewModel.setFolderPoolRule(key, folder.trackUris, true) }) {
                            Text(stringResource(R.string.music_library_pool_add))
                        }
                        TextButton(enabled = !state.loadingPool && !state.scanning,
                            onClick = { viewModel.setFolderPoolRule(key, folder.trackUris, false) }) {
                            Text(stringResource(R.string.music_library_pool_exclude))
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
                    val key = MusicFolderPoolRules.safSourceKey(source.id)
                    state.folderPoolRules[key]?.let { rule ->
                        Text(stringResource(if (rule == MusicFolderPoolRules.INCLUDE)
                            R.string.music_library_pool_status_included
                        else R.string.music_library_pool_status_excluded),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(enabled = sourceTracks > 0,
                            onClick = { onBrowseSource(source.id) }) {
                            Text(stringResource(R.string.music_library_folder_browse))
                        }
                        TextButton(enabled = sourceTracks > 0 && !state.loadingPool && !state.scanning,
                            onClick = { viewModel.setFolderPoolRule(key, source.trackUris, true) }) {
                            Text(stringResource(R.string.music_library_pool_add))
                        }
                        TextButton(enabled = sourceTracks > 0 && !state.loadingPool && !state.scanning,
                            onClick = { viewModel.setFolderPoolRule(key, source.trackUris, false) }) {
                            Text(stringResource(R.string.music_library_pool_exclude))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCatalogContent(
    artists: List<MusicArtistFacet>,
    onSelectArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        ArtistCatalogUnavailable(modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(artists, key = MusicArtistFacet::id) { artist ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(
                        R.string.music_library_facet_track_count,
                        artist.displayName,
                        artist.playableTrackCount
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(onClick = { onSelectArtist(artist.displayName) }) {
                    Text(stringResource(R.string.music_library_folder_browse))
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
    playlistRepository: MusicPlaylistRepository?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val replayState by viewModel.replayState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var targetHistory by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    var availablePlaylists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(playlistRepository) {
        if (playlistRepository != null) {
            try {
                playlistRepository.ensureFavorites()
                playlistRepository.observePlaylists().collect { rows ->
                    availablePlaylists = rows.filter { it.kind == MusicPlaylistKind.FAVORITES ||
                        it.kind == MusicPlaylistKind.NAMED || it.kind == MusicPlaylistKind.ALARM_COPY }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                availablePlaylists = emptyList()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.historyFilterMode == HistoryFilterMode.RECENT,
                onClick = { viewModel.loadRecentHistory() },
                label = { Text(Lp2PreviewText.text(R.string.music_library_history_filter_recent, "music_library_history_filter_recent")) }
            )
            FilterChip(
                selected = state.historyFilterMode == HistoryFilterMode.BY_DATE,
                onClick = { viewModel.loadHistory(state.selectedDate) },
                label = { Text(stringResource(R.string.music_library_history_filter_date)) }
            )
        }

        if (state.historyFilterMode == HistoryFilterMode.BY_DATE) {
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
        }

        if (state.history.isEmpty()) {
            Text(Lp2PreviewText.text(R.string.music_library_no_history, "music_library_no_history"))
        } else {
            val currentReplayState = replayState
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { it.id }) { entry ->
                    val isCurrent = when (currentReplayState) {
                        is ReplayUiState.Resolving -> currentReplayState.historyId == entry.id
                        is ReplayUiState.Preparing -> currentReplayState.historyId == entry.id
                        is ReplayUiState.Playing -> currentReplayState.historyId == entry.id
                        is ReplayUiState.Paused -> currentReplayState.historyId == entry.id
                        is ReplayUiState.Error -> currentReplayState.historyId == entry.id
                        ReplayUiState.Idle -> false
                    }
                    HistoryTrackRow(
                        entry = entry,
                        isActive = isCurrent,
                        replayState = currentReplayState,
                        onPlay = { viewModel.playHistory(entry.id) },
                        onPause = { viewModel.pauseReplay() },
                        onResume = { viewModel.resumeReplay() },
                        onAddToPlaylist = if (playlistRepository != null) {
                            { targetHistory = entry }
                        } else null
                    )
                }
            }
        }
    }
    val chosenHistory = targetHistory
    if (chosenHistory != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) targetHistory = null },
            title = { Text(stringResource(R.string.music_playlist_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(chosenHistory.title, style = MaterialTheme.typography.bodyMedium)
                    if (availablePlaylists.isEmpty()) {
                        Text(stringResource(R.string.music_playlist_none))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(availablePlaylists, key = MusicPlaylist::playlistId) { playlist ->
                                TextButton(
                                    enabled = !saving,
                                    onClick = {
                                        val repo = playlistRepository ?: return@TextButton
                                        if (saving) return@TextButton
                                        saving = true
                                        scope.launch {
                                            try {
                                                val added = repo.addHistoryTrack(
                                                    playlist.playlistId, chosenHistory.uri
                                                )
                                                val message = when {
                                                    added.addedItems > 0 -> context.getString(
                                                        R.string.music_playlist_added_count, added.addedItems)
                                                    added.excludedItemsSkipped > 0 -> context.getString(
                                                        R.string.music_playlist_item_unavailable)
                                                    else -> context.getString(R.string.music_playlist_add_zero)
                                                }
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                targetHistory = null
                                            } catch (cancel: CancellationException) {
                                                throw cancel
                                            } catch (error: Exception) {
                                                Toast.makeText(context,
                                                    context.getString(R.string.music_library_pool_update_failed,
                                                        error.message.orEmpty()),
                                                    Toast.LENGTH_LONG).show()
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                ) {
                                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = { targetHistory = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}
