package com.sysadmindoc.alarmclock.ui.ringtone

import kotlinx.coroutines.flow.Flow

enum class MediaCatalogTypeFilter {
    ALL,
    AUDIO,
    VIDEO
}
enum class MediaCatalogMediaType {
    AUDIO,
    VIDEO
}

enum class MediaCatalogGroupBy {
    NONE,
    FOLDER,
    ARTIST,
    ALBUM
}

enum class MediaCatalogSort {
    TITLE,
    RECENTLY_ADDED
}

enum class MediaAvailability {
    PLAYABLE,
    PENDING_PROBE,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

data class MediaCatalogItem(
    val uri: String,
    val displayTitle: String,
    val fileName: String,
    val mediaType: MediaCatalogMediaType,
    val artist: String?,
    val album: String?,
    val folderPath: String?,
    val sourceName: String,
    val availability: MediaAvailability,
    val isSystemTone: Boolean
)

data class MediaCatalogPage(
    val items: List<MediaCatalogItem>,
    val nextPageToken: String?
)

data class MediaCatalogScope(
    val sourceId: String? = null,
    val folderPath: String? = null
)

data class MediaCatalogRefreshResult(
    val indexedCount: Int,
    val warningCount: Int
)

data class MediaCatalogSourceStatus(
    val sourceId: String,
    val name: String,
    val sourceType: String,
    val permissionGranted: Boolean,
    val indexedMediaCount: Int,
    val lastError: String?
)

/**
 * UI-facing seam for the Android integration's MediaStore/SAF catalog.
 * The current P3 app still supplies Room v27 audio rows through
 * MusicLibraryViewModel; this interface has no production implementation yet.
 */
interface MediaCatalogRepository {
    fun observeSourceStatus(): Flow<List<MediaCatalogSourceStatus>>

    fun observeBrowsableMedia(
        query: String,
        typeFilter: MediaCatalogTypeFilter,
        groupBy: MediaCatalogGroupBy,
        pathScope: String?,
        artistIds: Set<String>,
        sort: MediaCatalogSort,
        pageToken: String?,
        pageSize: Int
    ): Flow<MediaCatalogPage>

    suspend fun refresh(scope: MediaCatalogScope): MediaCatalogRefreshResult
}

enum class MusicPlaylistKind {
    SMART_ALL,
    FAVORITES,
    NAMED,
    ALARM_COPY
}

data class MusicPlaylist(
    val playlistId: String,
    val name: String,
    val kind: MusicPlaylistKind,
    val ownerAlarmId: Long? = null,
    val playableTrackCount: Int,
    val unavailableTrackCount: Int = 0,
    val referencingAlarmCount: Int = 0
)

data class MusicPlaylistItem(
    val playlistId: String,
    val playlistItemId: String,
    val trackUri: String,
    val position: Int,
    val displayTitle: String,
    val availability: MediaAvailability
)

enum class ExcludedTrackResolution {
    KEEP_EXCLUDED,
    RESTORE_EXCLUDED
}

data class AddTracksResult(
    val addedItems: Int,
    val excludedItemsSkipped: Int = 0,
    val unavailableItemsSkipped: Int = 0,
    val excludedTrackUris: List<String> = emptyList()
)

sealed interface DeletePlaylistResult {
    data object Deleted : DeletePlaylistResult
    data class BlockedByAlarmReferences(val referencingAlarmCount: Int) : DeletePlaylistResult
}

/**
 * Room v28 integration seam. Playlist entries have stable identities separate
 * from their media URI, so a URI can occur more than once in one playlist.
 */
interface MusicPlaylistRepository {
    /** Emits SMART_ALL and FAVORITES virtual entries alongside user-managed playlists. */
    fun observePlaylists(): Flow<List<MusicPlaylist>>

    fun observeItems(playlistId: String): Flow<List<MusicPlaylistItem>>

    suspend fun create(name: String): MusicPlaylist

    suspend fun rename(playlistId: String, name: String): MusicPlaylist

    suspend fun duplicate(playlistId: String, ownerAlarmId: Long? = null): MusicPlaylist

    suspend fun addTracks(
        playlistId: String,
        selectedUris: List<String>,
        excludedResolution: ExcludedTrackResolution = ExcludedTrackResolution.KEEP_EXCLUDED
    ): AddTracksResult

    suspend fun removeItem(playlistItemId: String)

    suspend fun removeItems(playlistId: String, playlistItemIds: List<String>)

    suspend fun reorder(playlistId: String, orderedPlaylistItemIds: List<String>)

    suspend fun deleteWithReferencesCheck(playlistId: String): DeletePlaylistResult
}
