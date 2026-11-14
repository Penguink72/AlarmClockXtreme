package com.sysadmindoc.alarmclock.ui.ringtone

import kotlinx.coroutines.flow.Flow

/**
 * Gemini Preview 3 Room / MediaStore / SAF integration contracts consumed by
 * the Music Library and Playlists workspace.
 */
interface MediaCatalogRepository {
    fun observeCatalog(
        scope: MediaCatalogScope,
        search: String = "",
        pageSize: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0
    ): Flow<MediaCatalogPage>

    fun observeSources(): Flow<List<MediaCatalogSourceStatus>>

    suspend fun refreshMediaStore(force: Boolean = false)

    suspend fun rescanSafSource(sourceId: String)

    companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}

data class MediaCatalogPage(
    val items: List<MediaCatalogItem> = emptyList(),
    val totalCount: Int = 0,
    val offset: Int = 0,
    val pageSize: Int = MediaCatalogRepository.DEFAULT_PAGE_SIZE
)

data class MediaCatalogItem(
    val uri: String,
    val displayTitle: String,
    val fileName: String = "",
    val metadataTitle: String = "",
    val artist: String = "",
    val album: String = "",
    val folderPath: String = "",
    val durationMillis: Long? = null,
    val providerKind: MediaProviderKind = MediaProviderKind.MEDIA_STORE_AUDIO,
    val mediaType: CatalogMediaType = CatalogMediaType.AUDIO,
    val poolMembershipState: String? = null,
    val availability: MediaAvailability = MediaAvailability.PLAYABLE,
    val mimeType: String = "",
    val container: String = "",
    val audioCodec: String = "",
    val seekSupported: Boolean = true,
    val isSystemTone: Boolean = false,
    val failureReason: String = ""
) {
    val playable: Boolean
        get() = availability == MediaAvailability.PLAYABLE

    val unplayableReason: String?
        get() = failureReason.ifBlank { null }
}

enum class MediaProviderKind {
    MEDIA_STORE_AUDIO,
    MEDIA_STORE_VIDEO,
    SAF_TREE,
    SAF_VIDEO_TREE
}

enum class CatalogMediaType {
    AUDIO,
    VIDEO_AUDIO
}

enum class MediaAvailability {
    PLAYABLE,
    PENDING_PROBE,
    NO_AUDIO_TRACK,
    CODEC_UNSUPPORTED,
    PERMISSION_MISSING,
    UNREADABLE,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

sealed interface MediaCatalogScope {
    data object All : MediaCatalogScope
    data object AudioOnly : MediaCatalogScope
    data object VideoAudioOnly : MediaCatalogScope
    data class Folder(val folderId: String) : MediaCatalogScope
    data class Artist(val artistId: String) : MediaCatalogScope
    data class Playlist(val playlistId: String) : MediaCatalogScope
}

data class MediaCatalogSourceStatus(
    val sourceId: String,
    val displayName: String,
    val providerKind: MediaProviderKind,
    val playableCount: Int = 0,
    val rejectedNoAudioCount: Int = 0,
    val rejectedUnsupportedCodecCount: Int = 0,
    val unreadableCount: Int = 0,
    val permissionGranted: Boolean = true,
    val scanning: Boolean = false,
    val errorMessage: String = ""
)

interface MusicPlaylistRepository {
    fun observePlaylists(): Flow<List<MusicPlaylist>>

    fun observeItems(playlistId: String): Flow<List<MusicPlaylistItem>>

    suspend fun create(name: String): MusicPlaylist

    suspend fun rename(playlistId: String, name: String): MusicPlaylist

    suspend fun duplicate(playlistId: String, ownerAlarmId: Long? = null): MusicPlaylist

    /**
     * Must execute in a single Room transaction:
     * - Candidate (`membershipState == null`) tracks are automatically set to
     *   `INCLUDED` and added to [playlistId].
     * - `EXCLUDED` tracks are never silently restored unless the user chooses
     *   [ExcludedTrackResolution.RESTORE_EXCLUDED].
     * - Unavailable tracks are skipped and reported in [AddTracksResult].
     */
    suspend fun addTracks(
        playlistId: String,
        selectedUris: List<String>,
        excludedResolution: ExcludedTrackResolution = ExcludedTrackResolution.KEEP_EXCLUDED
    ): AddTracksResult

    suspend fun removeItem(playlistItemId: String)

    suspend fun removeItems(playlistId: String, playlistItemIds: List<String>)

    suspend fun reorder(playlistId: String, orderedPlaylistItemIds: List<String>)

    suspend fun deleteWithReferencesCheck(playlistId: String): DeletePlaylistResult

    companion object {
        const val SMART_ALL_PLAYLIST_ID = "smart_all"
        const val FAVORITES_PLAYLIST_ID = "favorites"
    }
}

data class MusicPlaylist(
    val playlistId: String,
    val name: String,
    val kind: MusicPlaylistKind = MusicPlaylistKind.NAMED,
    val ownerAlarmId: Long? = null,
    val playableTrackCount: Int = 0,
    val unavailableTrackCount: Int = 0,
    val referencingAlarmCount: Int = 0,
    val referencedAlarmCount: Int = referencingAlarmCount
)

enum class MusicPlaylistKind {
    SMART_ALL,
    FAVORITES,
    NAMED,
    ALARM_COPY
}

data class MusicPlaylistItem(
    val playlistId: String,
    val playlistItemId: String,
    val trackUri: String,
    val position: Int,
    val displayTitle: String,
    val artist: String = "",
    val folderPath: String = "",
    val durationMillis: Long? = null,
    val mediaType: CatalogMediaType = CatalogMediaType.AUDIO,
    val availability: MediaAvailability = MediaAvailability.PLAYABLE,
    val poolMembershipState: String? = null
)

enum class ExcludedTrackResolution {
    KEEP_EXCLUDED,
    RESTORE_EXCLUDED
}

data class AddTracksResult(
    val addedItems: Int = 0,
    val autoIncludedCandidates: Int = 0,
    val excludedItemsSkipped: Int = 0,
    val excludedItemsRestored: Int = 0,
    val unavailableItemsSkipped: Int = 0,
    val excludedTrackUris: List<String> = emptyList()
)

sealed interface DeletePlaylistResult {
    data object Deleted : DeletePlaylistResult
    data class BlockedByAlarmReferences(
        val referencingAlarmCount: Int
    ) : DeletePlaylistResult {
        val referencedAlarmCount: Int
            get() = referencingAlarmCount
    }
}
