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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryDialog(
    onDismiss: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (showHistory) showHistory = false else onDismiss()
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.music_library_back)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (showHistory) R.string.music_library_history_title
                                    else R.string.music_library_title
                                ),
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (!showHistory) {
                                Text(
                                    stringResource(
                                        R.string.music_library_track_count,
                                        state.poolTracks.size
                                    ),
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
                }
            ) { padding ->
                if (showHistory) {
                    PlaybackHistoryContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    MusicCatalogContent(
                        viewModel = viewModel,
                        state = state,
                        hasAudioPermission = hasAudioPermission,
                        onRequestPermission = { permissionLauncher.launch(permission) },
                        onManageSources = { showSources = true },
                        modifier = Modifier.padding(padding)
                    )
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
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageSources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = viewModel.visibleCatalogTracks(state)
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
                onClick = { viewModel.updateSelectedCatalogMembership(included = true) }
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
                onClick = { viewModel.updateSelectedCatalogMembership(included = false) }
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
                                "${item.sourceName.ifBlank { fallbackSource }} · $membershipLabel",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
