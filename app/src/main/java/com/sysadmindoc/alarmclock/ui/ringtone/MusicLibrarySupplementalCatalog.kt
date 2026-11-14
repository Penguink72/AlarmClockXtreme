package com.sysadmindoc.alarmclock.ui.ringtone

import kotlinx.coroutines.flow.StateFlow

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

    fun stop()
}

typealias PreviewAudioController = MusicCatalogPreviewController

data class MusicCatalogPreviewState(
    val activeUri: String? = null,
    val playing: Boolean = false,
    val blockedByActiveAlarm: Boolean = false,
    val errorMessage: String = ""
)
