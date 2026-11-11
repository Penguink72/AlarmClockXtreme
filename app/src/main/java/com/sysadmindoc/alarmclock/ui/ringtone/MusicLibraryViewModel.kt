package com.sysadmindoc.alarmclock.ui.ringtone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MusicLibraryUiState(
    val count: Int = 0,
    val scanning: Boolean = false,
    val status: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val history: List<PlaybackHistoryEntry> = emptyList()
)

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val indexer: MusicLibraryIndexer,
    private val repository: MusicLibraryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicLibraryUiState())
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()

    init {
        refreshCount()
        loadHistory(LocalDate.now())
    }

    fun refreshCount() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(count = repository.count())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = "Could not read the music index: ${error.message.orEmpty()}"
                )
            }
        }
    }

    fun reportStatus(message: String) {
        _uiState.value = _uiState.value.copy(status = message)
    }

    fun scan() {
        if (_uiState.value.scanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scanning = true, status = "Scanning audio sources…")
            try {
                val result = indexer.scan()
                val combined = if (result.warnings.isEmpty()) {
                    result.tracks
                } else {
                    (repository.allTracks() + result.tracks).distinctBy(MusicTrack::uri)
                }
                repository.replaceTracks(combined)
                _uiState.value = _uiState.value.copy(
                    count = repository.count(),
                    status = buildString {
                        append("Found ${result.tracks.size} audio files.")
                        if (result.warnings.isNotEmpty()) {
                            append(' ')
                            append(result.warnings.joinToString(" "))
                        }
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = "Music scan failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
            _uiState.value = _uiState.value.copy(scanning = false)
        }
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
                    status = "Could not read playback history: ${error.message.orEmpty()}"
                )
            }
        }
    }
}
