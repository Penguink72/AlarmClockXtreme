package com.sysadmindoc.alarmclock.ui.ringtone

import kotlinx.coroutines.flow.StateFlow

data class MusicLibrarySection<T>(
    val items: List<T>,
    val availability: Availability,
    val error: String? = null
) {
    enum class Availability {
        READY,
        NOT_CONNECTED,
        PERMISSION_REQUIRED,
        FAILED
    }
}

data class SupplementalVideoCandidate(
    val uri: String,
    val title: String,
    val fileName: String,
    val sourceName: String,
    val audioTrackProbe: AudioTrackProbe,
    val decoderSupport: DecoderSupport
) {
    enum class AudioTrackProbe {
        NOT_CHECKED,
        AUDIO_TRACK_FOUND,
        NO_AUDIO_TRACK,
        FAILED
    }

    enum class DecoderSupport {
        NOT_CHECKED,
        SUPPORTED,
        UNSUPPORTED,
        FAILED
    }
}

data class MusicArtistEntry(
    val id: String,
    val name: String,
    val trackUris: Set<String>,
    val unknownArtist: Boolean = false
)

/**
 * UI-facing seam for MediaStore Video and artist metadata not present in the
 * current Room v27 catalog. No implementation is registered in this branch.
 */
interface MusicLibrarySupplementalCatalog {
    suspend fun searchVideos(query: String): MusicLibrarySection<SupplementalVideoCandidate>

    suspend fun searchArtists(query: String): MusicLibrarySection<MusicArtistEntry>
}

data class MusicCatalogPreviewState(
    val playingUri: String? = null,
    val blockedByAlarm: Boolean = false,
    val error: String? = null
)

/**
 * Preview integration must refuse to take audio focus from an active alarm,
 * must not write playback history, and must stop when the library screen closes.
 */
interface MusicCatalogPreviewController {
    val state: StateFlow<MusicCatalogPreviewState>

    suspend fun togglePreview(uri: String)

    fun stop()
}
