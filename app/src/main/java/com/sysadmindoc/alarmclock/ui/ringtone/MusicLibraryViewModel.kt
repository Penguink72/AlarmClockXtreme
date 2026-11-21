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
import com.sysadmindoc.alarmclock.service.HistoryReplayActions
import com.sysadmindoc.alarmclock.service.ReplayUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class HistoryFilterMode {
    RECENT,
    BY_DATE
}

data class MusicLibraryUiState(
    val count: Int = 0,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val sources: List<MusicLibrarySource> = emptyList(),
    val folderPoolRules: Map<String, String> = emptyMap(),
    val scanning: Boolean = false,
    val scanningSourceIds: Set<String> = emptySet(),
    val status: String = "",
    val historyFilterMode: HistoryFilterMode = HistoryFilterMode.RECENT,
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
    val catalogFilter: MusicPoolFilter = MusicPoolFilter.ALL_CANDIDATES,
    val catalogSearch: String = "",
    val selectedCatalogUris: Set<String> = emptySet(),
    val catalogSourceScope: MusicPoolSourceScope? = null,
    val supplementalCatalog: MusicLibrarySupplementalCatalog = MusicLibrarySupplementalCatalog(),
    val loadingPool: Boolean = false,
    val systemToneUndoCount: Int = 0,
    val poolSourceScope: MusicPoolSourceScope = MusicPoolSourceScope.Folders
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
    data class DeviceFolder(val relativePath: String) : MusicPoolSourceScope
    data class Folder(val sourceId: String) : MusicPoolSourceScope
}

data class MusicPoolUiTrack(
    val track: MusicTrack,
    val membershipState: String?,
    val sourceName: String,
    val sourceIds: Set<String> = emptySet()
)

internal fun MusicTrack.isVideoAudio(): Boolean = MusicLibraryIndexer.isVideoSource(source)

internal fun MusicPoolUiTrack.matchesScope(scope: MusicPoolSourceScope): Boolean = when (scope) {
    MusicPoolSourceScope.Folders -> !MusicLibraryIndexer.isMediaStoreSource(track.source)
    MusicPoolSourceScope.DeviceMedia -> MusicLibraryIndexer.isMediaStoreSource(track.source)
    is MusicPoolSourceScope.DeviceFolder -> MusicLibraryIndexer.isMediaStoreSource(track.source)
    is MusicPoolSourceScope.Folder ->
        !MusicLibraryIndexer.isMediaStoreSource(track.source) && scope.sourceId in sourceIds
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
            MusicPoolFilter.INCLUDED -> item.membershipState == MusicPoolEntry.INCLUDED
            MusicPoolFilter.EXCLUDED -> item.membershipState == MusicPoolEntry.EXCLUDED
        }
        if (!matchesFilter || !item.matchesScope(scope)) return@filter false
        if (query.isBlank()) return@filter true
        val searchable = listOf(
            item.track.displayTitle,
            item.track.title,
            item.track.fileName,
            item.track.metadataTitle,
            item.sourceName
        ).joinToString(" ").lowercase(Locale.ROOT)
        query in searchable
    }
}

internal fun filterLibraryCatalog(
    tracks: List<MusicPoolUiTrack>,
    filter: MusicPoolFilter,
    search: String,
    sourceScope: MusicPoolSourceScope? = null,
    videoOnly: Boolean = false,
    metadataByUri: Map<String, MusicCatalogTrackMetadata> = emptyMap()
): List<MusicPoolUiTrack> {
    val query = search.trim().lowercase(Locale.ROOT)
    return tracks.filter { item ->
        val matchesFilter = when (filter) {
            MusicPoolFilter.ALL_CANDIDATES -> true
            MusicPoolFilter.INCLUDED -> item.membershipState == MusicPoolEntry.INCLUDED
            MusicPoolFilter.EXCLUDED -> item.membershipState == MusicPoolEntry.EXCLUDED
        }
        val matchingFolder = if (sourceScope is MusicPoolSourceScope.DeviceFolder) {
            MusicLibraryIndexer.isMediaStoreSource(item.track.source) &&
                normalizeMusicFolderPath(metadataByUri[item.track.uri]?.folderPath.orEmpty()) ==
                    sourceScope.relativePath
        } else sourceScope == null || item.matchesScope(sourceScope)
        if (!matchesFilter || !matchingFolder ||
            (videoOnly && !item.track.isVideoAudio())
        ) return@filter false
        if (query.isBlank()) return@filter true
        // MusicTrack intentionally only stores the five legacy fields. Artist,
        // album, and folder path live in the separate v28 metadata catalog.
        // Include them without a Room schema migration, so typing an artist
        // such as "Jia Peng Fang" finds indexed tracks by artist tags.
        val metadata = metadataByUri[item.track.uri]
        val searchable = listOf(
            item.track.displayTitle,
            item.track.title,
            item.track.fileName,
            item.track.metadataTitle,
            item.sourceName,
            metadata?.artist.orEmpty(),
            metadata?.album.orEmpty(),
            metadata?.folderPath.orEmpty()
        ).joinToString(" ").lowercase(Locale.ROOT)
        query in searchable
    }
}

/** An existing index is browseable immediately; permission/source changes are
 * scanned on explicit Refresh. Only the empty first-install catalog auto-scans.
 */
/**
 * Only metadata with a positive system-tone signal and MediaStore origin can
 * be offered as a possible stock ringtone. SAF music in an Alarms/ folder
 * may be user-created and is deliberately left alone. This still cannot
 * distinguish an OEM tone from a custom tone marked IS_RINGTONE.
 */
internal fun recognizedSystemToneCandidates(
    tracks: List<MusicPoolUiTrack>,
    metadataByUri: Map<String, MusicCatalogTrackMetadata>
): List<MusicPoolUiTrack> = tracks.filter { item ->
    item.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE &&
        item.membershipState != MusicPoolEntry.EXCLUDED &&
        metadataByUri[item.track.uri]?.isSystemTone == true
}

/**
 * U5: the exact candidate URIs shown in the user's confirmation dialog are
 * the upper bound of this operation. Never expand the set while the catalog
 * changes or after a delayed coroutine starts.
 */
internal fun confirmedSystemToneUris(
    tracks: List<MusicPoolUiTrack>,
    metadataByUri: Map<String, MusicCatalogTrackMetadata>,
    confirmedUris: Collection<String>
): Set<String> {
    val confirmed = confirmedUris.toHashSet()
    if (confirmed.isEmpty()) return emptySet()
    return recognizedSystemToneCandidates(tracks, metadataByUri)
        .asSequence().map { it.track.uri }
        .filter { it in confirmed }
        .toSet()
}

internal fun shouldAutoScanMusicCatalog(
    autoScanIfEmpty: Boolean,
    indexedTrackCount: Int,
    hasAudioPermission: Boolean,
    hasFolderSources: Boolean
): Boolean = autoScanIfEmpty && indexedTrackCount == 0 &&
    (hasAudioPermission || hasFolderSources)

internal fun catalogUrisForMembershipUpdate(
    tracks: List<MusicPoolUiTrack>,
    selectedUris: Set<String>,
    filter: MusicPoolFilter,
    included: Boolean
): Set<String> = tracks.asSequence()
    .filter { it.track.uri in selectedUris }
    .filter { item ->
        if (included) {
            if (filter == MusicPoolFilter.EXCLUDED) {
                item.membershipState == MusicPoolEntry.EXCLUDED
            } else {
                item.membershipState == null
            }
        } else {
            item.membershipState != MusicPoolEntry.EXCLUDED
        }
    }
    .map { it.track.uri }
    .toSet()

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
    val previewController: MusicCatalogPreviewController,
    val replayActions: HistoryReplayActions,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val playlistRepository: MusicPlaylistRepository
        get() = repository
    private val _uiState = MutableStateFlow(MusicLibraryUiState())
    private val historyRequestVersion = AtomicLong(0L)
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()
    val replayState: StateFlow<ReplayUiState> = replayActions.state

    init {
        refreshCount()
        refreshSources()
        loadRecentHistory()
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
                _uiState.value = _uiState.value.copy(
                    sources = sources,
                    folderPoolRules = MusicFolderPoolRules.readAll(context)
                )
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
                        // Removing a source must not resurrect its old inclusion
                        // policy when the same SAF tree is selected again.
                        MusicFolderPoolRules.restore(
                            context, MusicFolderPoolRules.safSourceKey(sourceId), null
                        )
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
        if (_uiState.value.scanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                scanning = true,
                scanningSourceIds = sourceIds.orEmpty(),
                status = context.getString(R.string.music_library_scanning_status)
            )
            try {
                val result = indexer.scan(sourceIds)
                val combined = MusicTrackIndexReconciler.afterScan(
                    existing = repository.allTracks(),
                    discovered = result.tracks,
                    sources = result.sources,
                    mediaStoreScanned = result.mediaStoreScanned
                )
                repository.replaceTracksWithMetadata(
                    tracks = combined,
                    metadataByUri = result.metadataByUri,
                    videoSourceStatus = result.videoSourceStatus
                )
                // A folder rule survives a rescan. INCLUDE only adds newly
                // discovered candidates; an explicit per-track exclusion stays.
                val scannedByRule = MusicFolderPoolRules.scannedUrisByRuleKey(result)
                // Broad opt-in runs first; an explicit folder EXCLUDE wins.
                for ((key, mode) in MusicFolderPoolRules.readAll(context).toList()
                    .sortedBy { if (it.first == MusicFolderPoolRules.allDeviceAudioKey()) 0 else 1 }) {
                    val uris = scannedByRule[key].orEmpty()
                    if (uris.isEmpty()) continue
                    if (mode == MusicFolderPoolRules.INCLUDE) {
                        repository.includeUnselectedSourceTracks(uris)
                    } else if (mode == MusicFolderPoolRules.EXCLUDE) {
                        repository.setPoolMembership(uris, included = false)
                    }
                }
                if (sourceIds == null && result.mediaStoreScanned) {
                    MusicAutoPoolPreferences.recordScanCompleted(context)
                }
                withContext(Dispatchers.IO) {
                    MusicLibrarySourceStore.updateAll(context, result.sources)
                }
                loadPoolCandidatesNow()
                _uiState.value = _uiState.value.copy(
                    sources = result.sources,
                    status = buildString {
                        append(
                            context.getString(
                                R.string.music_library_scan_found_count,
                                result.tracks.size,
                                result.tracks.count { MusicLibraryIndexer.isVideoSource(it.source) }
                            )
                        )
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
                                    MusicScanWarning.Kind.AUDIO_PERMISSION_DENIED,
                                    MusicScanWarning.Kind.VIDEO_PERMISSION_DENIED ->
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
        val included = tracks.filter { it.membershipState == MusicPoolEntry.INCLUDED }
        _uiState.value = _uiState.value.copy(
            count = included.size,
            audioCount = included.count { !MusicLibraryIndexer.isVideoSource(it.track.source) },
            videoCount = included.count { MusicLibraryIndexer.isVideoSource(it.track.source) }
        )
    }

    fun loadPoolCandidates(autoScanIfEmpty: Boolean = false) {
        if (_uiState.value.loadingPool) return
        // Claim before coroutine dispatch; two taps/open events in one frame
        // must not enqueue duplicate 4,935-track catalog reads.
        _uiState.value = _uiState.value.copy(loadingPool = true)
        viewModelScope.launch {
            try {
                // Show the persisted index first. A full MediaStore/SAF scan
                // can take minutes for several thousand files and must not
                // block ordinary searches every time this dialog opens.
                loadPoolCandidatesNow()
                if (shouldAutoScanMusicCatalog(
                        autoScanIfEmpty = autoScanIfEmpty,
                        indexedTrackCount = _uiState.value.poolTracks.size,
                        hasAudioPermission = indexer.hasAudioPermission(),
                        hasFolderSources = _uiState.value.sources.isNotEmpty()
                    )
                ) {
                    scan()
                }
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

    /** Folder-level operations do not touch actual files or playlist rows. */
    fun setFolderPoolRule(key: String, visibleUris: Set<String>, include: Boolean) {
        if (_uiState.value.loadingPool || _uiState.value.scanning || visibleUris.isEmpty()) return
        val currentUris = _uiState.value.poolTracks.mapTo(HashSet()) { it.track.uri }
        val approved = visibleUris.intersect(currentUris)
        if (approved.isEmpty()) return
        val old = MusicFolderPoolRules.readAll(context)[key]
        val mode = if (include) MusicFolderPoolRules.INCLUDE else MusicFolderPoolRules.EXCLUDE
        systemToneUndoSnapshot = emptyMap()
        _uiState.value = _uiState.value.copy(loadingPool = true, systemToneUndoCount = 0)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { MusicFolderPoolRules.write(context, key, mode) }
                // This explicitly chosen whole-folder action includes/restores
                // all current folder rows; later rescans do NOT undo manual
                // per-track exclusions.
                val changedCount = if (include && key == MusicFolderPoolRules.allDeviceAudioKey()) {
                    // A broad opt-in must NOT undo folder-specific or manual
                    // EXCLUDED decisions. Those require an explicit folder action.
                    repository.includeUnselectedSourceTracks(approved)
                } else {
                    repository.setPoolMembership(approved, included = include)
                    approved.size
                }
                loadPoolCandidatesNow()
                reportStatus(context.getString(
                    if (include) R.string.music_library_catalog_added
                    else R.string.music_library_catalog_excluded, changedCount
                ))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runCatching { MusicFolderPoolRules.restore(context, key, old) }
                reportStatus(context.getString(R.string.music_library_pool_update_failed,
                    error.message.orEmpty()))
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    fun setCatalogFilter(filter: MusicPoolFilter) {
        _uiState.value = _uiState.value.copy(
            catalogFilter = filter,
            selectedCatalogUris = emptySet()
        )
    }

    fun setCatalogSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            catalogSearch = query,
            selectedCatalogUris = emptySet()
        )
    }

    fun toggleCatalogSelection(uri: String) {
        val selected = _uiState.value.selectedCatalogUris.toMutableSet()
        if (!selected.add(uri)) selected.remove(uri)
        _uiState.value = _uiState.value.copy(selectedCatalogUris = selected)
    }

    fun selectVisibleCatalogTracks(uris: List<String>) {
        _uiState.value = _uiState.value.copy(selectedCatalogUris = uris.toSet())
    }

    fun clearCatalogSelection() {
        _uiState.value = _uiState.value.copy(selectedCatalogUris = emptySet())
    }

    fun setCatalogSourceScope(scope: MusicPoolSourceScope?) {
        _uiState.value = _uiState.value.copy(
            catalogSourceScope = scope,
            selectedCatalogUris = emptySet()
        )
    }

    fun visibleCatalogTracks(videoOnly: Boolean = false): List<MusicPoolUiTrack> =
        visibleCatalogTracks(_uiState.value, videoOnly)

    fun visibleCatalogTracks(
        state: MusicLibraryUiState,
        videoOnly: Boolean = false
    ): List<MusicPoolUiTrack> = filterLibraryCatalog(
        tracks = state.poolTracks,
        filter = state.catalogFilter,
        search = state.catalogSearch,
        metadataByUri = state.supplementalCatalog.trackMetadataByUri,
        sourceScope = state.catalogSourceScope,
        videoOnly = videoOnly
    )

    // Snapshot is only valid for the immediate last automated exclusion.
    // Ordinary membership changes invalidate it so Undo never overwrites a
    // subsequent explicit user decision.
    private var systemToneUndoSnapshot: Map<String, String?> = emptyMap()

    fun excludeRecognizedSystemTones(confirmedUris: Collection<String>) {
        if (_uiState.value.loadingPool || _uiState.value.scanning) return
        val current = _uiState.value
        val candidates = confirmedSystemToneUris(
            current.poolTracks,
            current.supplementalCatalog.trackMetadataByUri,
            confirmedUris
        )
        if (candidates.isEmpty()) return
        // Claim the operation synchronously. Two quick confirmation taps
        // cannot queue overlapping DAO writes with incompatible Undo states.
        _uiState.value = current.copy(loadingPool = true)
        viewModelScope.launch {
            try {
                val snapshot = repository.excludeSystemToneCandidates(candidates)
                systemToneUndoSnapshot = snapshot
                loadPoolCandidatesNow()
                _uiState.value = _uiState.value.copy(systemToneUndoCount = snapshot.size)
                reportStatus(context.getString(R.string.music_library_system_tones_excluded, snapshot.size))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(context.getString(R.string.music_library_system_tones_error))
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    fun undoRecognizedSystemToneExclusion() {
        if (_uiState.value.loadingPool || _uiState.value.scanning) return
        val snapshot = systemToneUndoSnapshot.toMap()
        if (snapshot.isEmpty()) return
        _uiState.value = _uiState.value.copy(loadingPool = true)
        viewModelScope.launch {
            try {
                repository.undoSystemToneCandidateExclusion(snapshot)
                systemToneUndoSnapshot = emptyMap()
                loadPoolCandidatesNow()
                _uiState.value = _uiState.value.copy(systemToneUndoCount = 0)
                reportStatus(context.getString(R.string.music_library_system_tones_restored, snapshot.size))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportStatus(context.getString(R.string.music_library_system_tones_error))
            } finally {
                _uiState.value = _uiState.value.copy(loadingPool = false)
            }
        }
    }

    fun updateSelectedCatalogMembership(included: Boolean, videoOnly: Boolean = false) {
        if (_uiState.value.loadingPool || _uiState.value.scanning) return
        val selected = catalogUrisForMembershipUpdate(
            tracks = visibleCatalogTracks(videoOnly),
            selectedUris = _uiState.value.selectedCatalogUris,
            filter = _uiState.value.catalogFilter,
            included = included
        )
        if (selected.isEmpty()) return
        // A no-op must preserve the existing system-tone Undo. A real manual
        // mutation supersedes Undo and claims the lock before coroutine dispatch.
        systemToneUndoSnapshot = emptyMap()
        _uiState.value = _uiState.value.copy(loadingPool = true, systemToneUndoCount = 0)
        viewModelScope.launch {
            try {
                repository.setPoolMembership(selected, included)
                loadPoolCandidatesNow()
                reportStatus(
                    context.getString(
                        if (included) R.string.music_library_catalog_added
                        else R.string.music_library_catalog_excluded,
                        selected.size
                    )
                )
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
        // Any explicit membership write supersedes the previous automated
        // exclusion. Claim the lock before coroutine dispatch to avoid an
        // overlapping Undo or another bulk write from a second tap.
        systemToneUndoSnapshot = emptyMap()
        _uiState.value = _uiState.value.copy(loadingPool = true, systemToneUndoCount = 0)
        viewModelScope.launch {
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
        systemToneUndoSnapshot = emptyMap()
        _uiState.value = _uiState.value.copy(loadingPool = true, systemToneUndoCount = 0)
        viewModelScope.launch {
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
        val supplemental = repository.buildSupplementalCatalog()
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
            folderPoolRules = MusicFolderPoolRules.readAll(context),
            supplementalCatalog = supplemental,
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
            selectedPoolUris = emptySet(),
            selectedCatalogUris = emptySet()
        )
    }

    fun loadRecentHistory(limit: Int = 100) {
        val requestId = historyRequestVersion.incrementAndGet()
        _uiState.value = _uiState.value.copy(historyFilterMode = HistoryFilterMode.RECENT)
        viewModelScope.launch {
            try {
                val rows = repository.recentHistory(limit)
                _uiState.update { current ->
                    if (historyRequestVersion.get() != requestId ||
                        current.historyFilterMode != HistoryFilterMode.RECENT
                    ) current else current.copy(history = rows)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (historyRequestVersion.get() != requestId ||
                        current.historyFilterMode != HistoryFilterMode.RECENT
                    ) current else current.copy(
                        status = context.getString(
                            R.string.music_library_history_read_failed,
                            error.message.orEmpty()
                        )
                    )
                }
            }
        }
    }

    fun loadHistory(date: LocalDate) {
        val requestId = historyRequestVersion.incrementAndGet()
        _uiState.value = _uiState.value.copy(
            historyFilterMode = HistoryFilterMode.BY_DATE,
            selectedDate = date
        )
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            try {
                val rows = repository.historyForDate(from, to)
                _uiState.update { current ->
                    if (historyRequestVersion.get() != requestId ||
                        current.historyFilterMode != HistoryFilterMode.BY_DATE ||
                        current.selectedDate != date
                    ) current else current.copy(history = rows)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (historyRequestVersion.get() != requestId ||
                        current.selectedDate != date
                    ) current else current.copy(
                        status = context.getString(
                            R.string.music_library_history_read_failed,
                            error.message.orEmpty()
                        )
                    )
                }
            }
        }
    }

    fun playHistory(historyId: Long) {
        previewController.stop()
        replayActions.playHistory(historyId)
    }

    fun pauseReplay() {
        replayActions.pause()
    }

    fun resumeReplay() {
        replayActions.resume()
    }

    fun stopReplay() {
        replayActions.stop()
    }
}
