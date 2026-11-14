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
 * Read-only adapter surface for catalog metadata that is not stored in the
 * Room v27 `MusicTrack` row.
 */
data class MusicLibrarySupplementalCatalog(
    val artists: List<MusicArtistFacet> = emptyList(),
    val folders: List<MusicFolderFacet> = emptyList(),
    val trackMetadataByUri: Map<String, MusicCatalogTrackMetadata> = emptyMap(),
    val videoSourceStatus: MediaCatalogSourceStatus? = null,
    val flvSupportNoteVisible: Boolean = false
)

data class MusicArtistFacet(
    val id: String,
    val displayName: String,
    val playableTrackCount: Int,
    val trackUris: Set<String> = emptySet()
)

data class MusicFolderFacet(
    val id: String,
    val displayName: String,
    val path: String = "",
    val playableTrackCount: Int = 0,
    val audioTrackCount: Int = 0,
    val videoAudioTrackCount: Int = 0,
    val rejectedNoAudioCount: Int = 0,
    val rejectedUnsupportedCodecCount: Int = 0,
    val unreadableCount: Int = 0,
    val trackUris: Set<String> = emptySet()
)

data class MusicCatalogTrackMetadata(
    val uri: String,
    val artist: String = "",
    val album: String = "",
    val folderPath: String = "",
    val durationMillis: Long? = null,
    val mediaType: CatalogMediaType? = null,
    val availability: MediaAvailability = MediaAvailability.PLAYABLE,
    val codecWarning: String = "",
    val container: String = "",
    val seekSupported: Boolean = true,
    val isSystemTone: Boolean = false
)

/**
 * UI-facing contract for the single-instance catalog preview player.
 *
 * Implementations must never write to `playback_history`, must stop when the
 * Music Library closes, and must not play over an active firing alarm.
 */
interface MusicCatalogPreviewController {
    val state: StateFlow<MusicCatalogPreviewState>

    suspend fun togglePreview(uri: String)

    fun togglePreview(item: MediaCatalogItem) {}

    fun stop()
}

typealias PreviewAudioController = MusicCatalogPreviewController

data class MusicCatalogPreviewState(
    val playingUri: String? = null,
    val blockedByAlarm: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = playingUri != null,
    val isBuffering: Boolean = false
) {
    val activeUri: String?
        get() = playingUri

    val playing: Boolean
        get() = isPlaying

    val blockedByActiveAlarm: Boolean
        get() = blockedByAlarm

    val errorMessage: String
        get() = error.orEmpty()
}
