package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
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
    val loadingSubfolders: Boolean = false
)

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val indexer: MusicLibraryIndexer,
    private val repository: MusicLibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicLibraryUiState())
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()

    init {
        refreshCount()
        refreshSources()
        loadHistory(LocalDate.now())
    }

    fun refreshCount() {
        viewModelScope.launch {
            try {
                updateTrackCounts(repository.allTracks())
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
                val remainingSources = withContext(Dispatchers.IO) {
                    if (MusicLibrarySourceStore.remove(context, sourceId) == null) {
                        null
                    } else {
                        MusicLibrarySourceStore.getSources(context)
                    }
                }
                if (remainingSources != null) {
                    val retainedTracks = MusicTrackIndexReconciler.afterSourceRemoval(
                        existing = repository.allTracks(),
                        remainingSources = remainingSources
                    )
                    repository.replaceTracks(retainedTracks)
                    updateTrackCounts(retainedTracks)
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
                repository.replaceTracks(combined)
                updateTrackCounts(combined)
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

    private fun updateTrackCounts(tracks: List<MusicTrack>) {
        _uiState.value = _uiState.value.copy(
            count = tracks.size,
            audioCount = tracks.count { it.source != MusicLibraryIndexer.SOURCE_SAF_VIDEO },
            videoCount = tracks.count { it.source == MusicLibraryIndexer.SOURCE_SAF_VIDEO }
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
