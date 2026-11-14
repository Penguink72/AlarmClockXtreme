package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.MusicPoolTrack
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class MusicLibraryUiState(
    val count: Int = 0,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val sources: List<MusicLibrarySource> = emptyList(),
    val scanning: Boolean = false,
    val scanningSourceIds: Set<String> = emptySet(),
    val status: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val history: List<PlaybackHistoryEntry> = emptyList(),
    val folderPickerSourceId: String? = null,
    val folderCandidates: List<MusicFolderCandidate> = emptyList(),
    val selectedFolderIds: Set<String> = emptySet(),
    val loadingSubfolders: Boolean = false,
    val poolTracks: List<MusicPoolUiTrack> = emptyList(),
    val poolFilter: MusicPoolFilter = MusicPoolFilter.ALL_CANDIDATES,
    val poolSearch: String = "",
    val selectedPoolUris: Set<String> = emptySet(),
    val loadingPool: Boolean = false,
    val poolSourceScope: MusicPoolSourceScope = MusicPoolSourceScope.Folders,
    val minimumAutoDurationSeconds: Int = MusicAutoPoolPolicy.DEFAULT_MIN_SECONDS
)

enum class MusicPoolFilter {
    ALL_CANDIDATES,
    INCLUDED,
    EXCLUDED
}

/**
 * Explicit source scope for the music-pool dialog. The default only shows
 * media found in user-selected folders, so "Select visible" can never sweep
 * device-wide MediaStore ringtones / notification sounds into the pool unless
 * the user deliberately switches to [DeviceMedia].
 */
sealed interface MusicPoolSourceScope {
    data object Folders : MusicPoolSourceScope
    data object DeviceMedia : MusicPoolSourceScope
    data class Folder(val sourceId: String) : MusicPoolSourceScope
}

data class MusicPoolUiTrack(
    val track: MusicTrack,
    val membershipState: String?,
    val sourceName: String,
    val sourceIds: Set<String> = emptySet()
)

internal fun MusicTrack.isVideoAudio(): Boolean = source == MusicLibraryIndexer.SOURCE_SAF_VIDEO

internal fun MusicPoolUiTrack.matchesScope(scope: MusicPoolSourceScope): Boolean = when (scope) {
    MusicPoolSourceScope.Folders ->
        track.source != MusicLibraryIndexer.SOURCE_MEDIA_STORE ||
            membershipState == MusicPoolEntry.AUTO_INCLUDED
    MusicPoolSourceScope.DeviceMedia -> track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE
    is MusicPoolSourceScope.Folder ->
        track.source != MusicLibraryIndexer.SOURCE_MEDIA_STORE && scope.sourceId in sourceIds
}

/** Pure filter used by the pool dialog; selection/batch actions operate only on this result. */
internal fun filterPoolTracks(
    tracks: List<MusicPoolUiTrack>,
    filter: MusicPoolFilter,
    scope: MusicPoolSourceScope,
    search: String
): List<MusicPoolUiTrack> {
    val query = search.trim().lowercase(Locale.ROOT)
    return tracks.filter { item ->
        val matchesFilter = when (filter) {
            MusicPoolFilter.ALL_CANDIDATES -> true
            MusicPoolFilter.INCLUDED ->
                item.membershipState == MusicPoolEntry.INCLUDED ||
                    item.membershipState == MusicPoolEntry.AUTO_INCLUDED
            MusicPoolFilter.EXCLUDED -> item.membershipState == MusicPoolEntry.EXCLUDED
        }
        if (!matchesFilter || !item.matchesScope(scope)) return@filter false
        if (query.isBlank()) return@filter true
        val searchable = listOf(
            item.track.displayTitle,
            item.track.fileName,
            item.track.metadataTitle,
            item.sourceName
        ).joinToString(" ").lowercase(Locale.ROOT)
        query in searchable
    }
}

/**
 * URIs that "Add folder to pool" should include: indexed, not MediaStore,
 * listed by that folder, and not explicitly excluded by the user.
 */
internal fun folderCandidatesToInclude(
    tracks: List<MusicPoolUiTrack>,
    sourceId: String
): List<String> = tracks
    .filter { it.matchesScope(MusicPoolSourceScope.Folder(sourceId)) }
    .filter { it.membershipState != MusicPoolEntry.EXCLUDED && it.membershipState != MusicPoolEntry.INCLUDED }
    .map { it.track.uri }

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val indexer: MusicLibraryIndexer,
    private val repository: MusicLibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MusicLibraryUiState(
            minimumAutoDurationSeconds = MusicAutoPoolPreferences.minimumDurationSeconds(context)
        )
    )
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()
    private var initialAutoScanRequested = false

    init {
        refreshCount()
        refreshSources()
        loadHistory(LocalDate.now())
    }

    /** A music library opens ready to use. Repeat scans remain available manually. */
    fun autoScanIfNeeded() {
        if (initialAutoScanRequested) return
        initialAutoScanRequested = true
        scan()
    }

    /** Permission response must always trigger a scan, even after a prior denial. */
    fun scanAfterPermissionResponse() {
        initialAutoScanRequested = true
        scan()
    }

    fun updateMinimumAutoDuration(seconds: Int) {
        if (seconds !in MusicAutoPoolPolicy.availableMinimumSeconds ||
            _uiState.value.scanning || _uiState.value.loadingPool
        ) return
        try {
            MusicAutoPoolPreferences.setMinimumDurationSeconds(context, seconds)
            _uiState.value = _uiState.value.copy(minimumAutoDurationSeconds = seconds)
            scan()
        } catch (error: Exception) {
            reportStatus(
                context.getString(R.string.music_library_auto_filter_save_failed,
                    error.message.orEmpty())
            )
        }
    }

    fun refreshCount() {
        viewModelScope.launch {
            try {
                updatePoolCounts(repository.poolCandidates())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = context.getString(
                        R.string.music_library_index_read_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    fun refreshSources() {
        viewModelScope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    MusicLibrarySourceStore.getSources(context)
                }
                _uiState.value = _uiState.value.copy(sources = sources)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_sources_read_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    fun reportStatus(message: String) {
        _uiState.value = _uiState.value.copy(status = message)
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                indexer.addFolder(uri)
                scan()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_folder_access_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    fun removeSource(sourceId: String) {
        viewModelScope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    MusicLibrarySourceStore.getSources(context)
                }
                if (sources.any { it.id == sourceId }) {
                    val remainingSources = sources.filterNot { it.id == sourceId }
                    val retainedTracks = MusicTrackIndexReconciler.afterSourceRemoval(
                        existing = repository.allTracks(),
                        remainingSources = remainingSources
                    )
                    repository.replaceTracks(retainedTracks)
                    withContext(Dispatchers.IO) {
                        check(MusicLibrarySourceStore.remove(context, sourceId) != null) {
                            "Music folder source no longer exists"
                        }
                    }
                    updatePoolCounts(repository.poolCandidates())
                    _uiState.value = _uiState.value.copy(sources = remainingSources)
                    scan()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_source_remove_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    fun loadSubfolders(sourceId: String) {
        if (_uiState.value.loadingSubfolders) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                folderPickerSourceId = sourceId,
                folderCandidates = emptyList(),
                selectedFolderIds = emptySet(),
                loadingSubfolders = true
            )
            try {
                val folders = indexer.listSubfolders(sourceId)
                val existingIds = _uiState.value.sources.mapTo(HashSet(), MusicLibrarySource::id)
                val source = _uiState.value.sources.firstOrNull { it.id == sourceId }
                val candidates = folders.filterNot { candidate ->
                    source != null &&
                        MusicLibrarySourceStore.sourceId(source.treeUri, candidate.documentId) in existingIds
                }
                _uiState.value = _uiState.value.copy(
                    folderCandidates = candidates,
                    status = if (candidates.isEmpty()) {
                        context.getString(R.string.music_library_no_subfolders)
                    } else {
                        _uiState.value.status
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_subfolders_failed,
                        error.message.orEmpty()
                    )
                )
                _uiState.value = _uiState.value.copy(folderPickerSourceId = null)
            } finally {
                _uiState.value = _uiState.value.copy(loadingSubfolders = false)
            }
        }
    }

    fun toggleFolderSelection(documentId: String) {
        val selected = _uiState.value.selectedFolderIds.toMutableSet()
        if (!selected.add(documentId)) selected.remove(documentId)
        _uiState.value = _uiState.value.copy(selectedFolderIds = selected)
    }

    fun addSelectedSubfolders() {
        val sourceId = _uiState.value.folderPickerSourceId ?: return
        val selected = _uiState.value.selectedFolderIds
        val folders = _uiState.value.folderCandidates.filter { it.documentId in selected }
        viewModelScope.launch {
            try {
                indexer.addSubfolders(sourceId, folders)
                _uiState.value = _uiState.value.copy(
                    folderPickerSourceId = null,
                    folderCandidates = emptyList(),
                    selectedFolderIds = emptySet()
                )
                scan()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_subfolders_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }

    fun closeSubfolderPicker() {
        _uiState.value = _uiState.value.copy(
            folderPickerSourceId = null,
            folderCandidates = emptyList(),
            selectedFolderIds = emptySet()
        )
    }

    fun scan(sourceIds: Set<String>? = null) {
        if (_uiState.value.scanning || _uiState.value.loadingPool) return
        // Mark scanning before launching so repeated clicks cannot schedule
        // overlapping runs that could overwrite newly indexed source state.
        _uiState.value = _uiState.value.copy(
            scanning = true,
            scanningSourceIds = sourceIds.orEmpty(),
            status = context.getString(R.string.music_library_scanning_status)
        )
        viewModelScope.launch {
            try {
                val result = indexer.scan(sourceIds)
                val combined = MusicTrackIndexReconciler.afterScan(
                    existing = repository.allTracks(),
                    discovered = result.tracks,
                    sources = result.sources,
                    mediaStoreScanned = result.mediaStoreScanned
                )
                repository.replaceTracks(combined)
                withContext(Dispatchers.IO) {
                    MusicLibrarySourceStore.updateAll(context, result.sources)
                }
                // Automatic membership is based on this successful scan only.
                // The DAO transaction preserves explicit user includes/excludes.
                val autoAdded = repository.reconcileAutomaticallyEvaluated(
                    result.evaluatedUris,
                    result.automaticallyEligibleUris
                )
                updatePoolCounts(repository.poolCandidates())
                _uiState.value = _uiState.value.copy(
                    sources = result.sources,
                    status = buildString {
                        append(
                            context.getString(
                                R.string.music_library_scan_found_count,
                                result.tracks.size,
                                result.tracks.count { it.source == MusicLibraryIndexer.SOURCE_SAF_VIDEO }
                            )
                        )
                        append(' ')
                        append(context.getString(
                            R.string.music_library_auto_scan_summary,
                            autoAdded,
                            result.evaluatedUris.size - result.automaticallyEligibleUris.size
                        ))
                        if (result.rejectedAudio > 0) {
                            append(' ')
                            append(
                                context.getString(
                                    R.string.music_library_audio_unplayable,
                                    result.rejectedAudio
                                )
                            )
                        }
                        if (result.rejectedVideos > 0) {
                            append(' ')
                            append(
                                context.getString(
                                    R.string.music_library_video_without_audio,
                                    result.rejectedVideos
                                )
                            )
                        }
                        if (result.warnings.isNotEmpty()) {
                            append(' ')
                            append(result.warnings.joinToString(" ") { warning ->
                                when (warning.kind) {
                                    MusicScanWarning.Kind.MEDIA_STORE_FAILED ->
                                        context.getString(
                                            R.string.music_library_mediastore_failed,
                                            warning.detail
                                        )
                                    MusicScanWarning.Kind.AUDIO_PERMISSION_DENIED ->
                                        context.getString(R.string.music_library_permission_denied)
                                    MusicScanWarning.Kind.FOLDER_FAILED ->
                                        context.getString(
                                            R.string.music_library_folder_failed,
                                            warning.detail
                                        )
                                }
                            })
                        }
                    }
                )
                updatePoolCounts(repository.poolCandidates())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = context.getString(
                        R.string.music_library_scan_failed,
                        error.message ?: error.javaClass.simpleName
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    scanning = false,
                    scanningSourceIds = emptySet()
                )
            }
        }
    }

    private fun updatePoolCounts(tracks: List<MusicPoolTrack>) {
        val included = tracks.filter {
            it.membershipState == MusicPoolEntry.INCLUDED ||
                it.membershipState == MusicPoolEntry.AUTO_INCLUDED
        }
        _uiState.value = _uiState.value.copy(
            count = included.size,
            audioCount = included.count { it.track.source != MusicLibraryIndexer.SOURCE_SAF_VIDEO },
            videoCount = included.count { it.track.source == MusicLibraryIndexer.SOURCE_SAF_VIDEO }
        )
    }

    fun loadPoolCandidates() {
        if (_uiState.value.loadingPool) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingPool = true)
            try {
                loadPoolCandidatesNow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_pool_read_failed,
                        error.message.orEmpty()
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    fun setPoolFilter(filter: MusicPoolFilter) {
        _uiState.value = _uiState.value.copy(poolFilter = filter, selectedPoolUris = emptySet())
    }

    fun setPoolSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            poolSearch = query,
            selectedPoolUris = emptySet()
        )
    }

    fun togglePoolSelection(uri: String) {
        val selected = _uiState.value.selectedPoolUris.toMutableSet()
        if (!selected.add(uri)) selected.remove(uri)
        _uiState.value = _uiState.value.copy(selectedPoolUris = selected)
    }

    fun selectVisiblePoolTracks(uris: List<String>) {
        _uiState.value = _uiState.value.copy(selectedPoolUris = uris.toSet())
    }

    fun updateSelectedPoolMembership(included: Boolean) {
        if (_uiState.value.loadingPool || _uiState.value.scanning) return
        // Only act on selected rows that are still inside the current
        // filter + source scope, so a stale selection can never leak into
        // another folder or into device-wide MediaStore media.
        val visibleUris = visiblePoolTracks().mapTo(HashSet()) { it.track.uri }
        val selected = _uiState.value.selectedPoolUris.filterTo(LinkedHashSet()) { it in visibleUris }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingPool = true)
            try {
                repository.setPoolMembership(selected, included)
                // Refresh in the same coroutine; otherwise repeated taps can
                // schedule overlapping writes while old rows remain selected.
                loadPoolCandidatesNow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_pool_update_failed,
                        error.message.orEmpty()
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    fun visiblePoolTracks(): List<MusicPoolUiTrack> = visiblePoolTracks(_uiState.value)

    fun visiblePoolTracks(state: MusicLibraryUiState): List<MusicPoolUiTrack> =
        filterPoolTracks(state.poolTracks, state.poolFilter, state.poolSourceScope, state.poolSearch)

    fun setPoolSourceScope(scope: MusicPoolSourceScope) {
        _uiState.value = _uiState.value.copy(poolSourceScope = scope, selectedPoolUris = emptySet())
    }

    /**
     * Adds every indexed candidate of exactly one folder source to the pool.
     * Tracks the user explicitly excluded stay excluded; MediaStore items and
     * other folders are never touched.
     */
    fun addSourceToPool(sourceId: String) {
        if (_uiState.value.scanning || _uiState.value.loadingPool) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingPool = true)
            try {
                // The pool dialog may have been opened before a later rescan.
                // Read the selected source and current indexed candidates
                // again rather than trusting the previous poolTracks snapshot.
                val source = withContext(Dispatchers.IO) {
                    MusicLibrarySourceStore.getSources(context)
                        .firstOrNull { it.id == sourceId }
                } ?: error("Music folder source no longer exists")
                val added = repository.includeUnselectedSourceTracks(source.trackUris)
                loadPoolCandidatesNow()
                reportStatus(context.getString(R.string.music_library_pool_folder_added, added, source.name))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(
                    context.getString(
                        R.string.music_library_pool_update_failed,
                        error.message.orEmpty()
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    private suspend fun loadPoolCandidatesNow() {
        val candidates = repository.poolCandidates()
        val sources = withContext(Dispatchers.IO) { MusicLibrarySourceStore.getSources(context) }
        val sourceIdsByUri = HashMap<String, MutableSet<String>>()
        val sourceNames = HashMap<String, String>()
        sources.forEach { source ->
            source.trackUris.forEach { uri ->
                sourceIdsByUri.getOrPut(uri) { LinkedHashSet() }.add(source.id)
                sourceNames.putIfAbsent(uri, source.name)
            }
        }
        updatePoolCounts(candidates)
        val scope = _uiState.value.poolSourceScope
        _uiState.value = _uiState.value.copy(
            sources = sources,
            poolSourceScope = if (
                scope is MusicPoolSourceScope.Folder && sources.none { it.id == scope.sourceId }
            ) MusicPoolSourceScope.Folders else scope,
            poolTracks = candidates.map { candidate ->
                MusicPoolUiTrack(
                    track = candidate.track,
                    membershipState = candidate.membershipState,
                    sourceName = sourceNames[candidate.track.uri].orEmpty(),
                    sourceIds = sourceIdsByUri[candidate.track.uri].orEmpty()
                )
            },
            selectedPoolUris = emptySet()
        )
    }

    fun loadHistory(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            try {
                _uiState.value = _uiState.value.copy(
                    history = repository.historyForDate(from, to)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = context.getString(
                        R.string.music_library_history_read_failed,
                        error.message.orEmpty()
                    )
                )
            }
        }
    }
}
