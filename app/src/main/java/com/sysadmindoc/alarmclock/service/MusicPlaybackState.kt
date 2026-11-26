package com.sysadmindoc.alarmclock.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Playback provenance for display only. Never used to choose or start audio. */
enum class NowPlayingSource {
    PLAYLIST, MUSIC_LIBRARY, SELECTED_RINGTONE, SYSTEM_RINGTONE, INTERNET_RADIO, UNKNOWN
}

/**
 * Snapshot of the item which reached playback. Folder display is grounded in
 * persisted catalog metadata, not guessed from an opaque content:// URI.
 */
data class NowPlayingInfo(
    val title: String,
    val source: NowPlayingSource,
    val folderName: String? = null
) {
    companion object {
        fun folderNameFrom(folderIds: Set<String>): String? {
            val paths = folderIds.asSequence()
                .map { it.trim() }
                .map { it.trimEnd('/') }
                .filter { it.isNotBlank() }
                .toList()
            val chosen = paths.firstOrNull { '/' in it } ?: paths.firstOrNull()
            return chosen?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * In-process StateFlow is already collected by AlarmFiringViewModel.
 * Each playback transition creates one immutable snapshot, so a playlist
 * advancing while the alarm UI is visible updates its title/source/folder
 * together without polling MediaStore or querying Room in Compose.
 */
object MusicPlaybackState {
    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingInfo?>(null)
    val nowPlaying: StateFlow<NowPlayingInfo?> = _nowPlaying.asStateFlow()

    /** Backwards-compatible entry point for older playback callers. */
    fun updateTitle(title: String?) {
        _nowPlaying.value = title?.let { NowPlayingInfo(it, NowPlayingSource.UNKNOWN) }
        _currentTitle.value = title
    }

    fun updateNowPlaying(info: NowPlayingInfo) {
        _nowPlaying.value = info
        _currentTitle.value = info.title
    }
}
