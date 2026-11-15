package com.sysadmindoc.alarmclock.data.repository

import com.sysadmindoc.alarmclock.data.backup.AlarmMusicSettingsBackup
import com.sysadmindoc.alarmclock.data.backup.MusicPlaylistBackup
import com.sysadmindoc.alarmclock.data.backup.MusicPlaylistItemBackup
import com.sysadmindoc.alarmclock.data.local.MusicLibraryDao
import com.sysadmindoc.alarmclock.data.local.MusicPoolTrack
import com.sysadmindoc.alarmclock.data.local.PlaybackHistoryDao
import com.sysadmindoc.alarmclock.data.local.entity.AlarmMusicFacetFilterEntity
import com.sysadmindoc.alarmclock.data.local.entity.AlarmMusicSettingsEntity
import com.sysadmindoc.alarmclock.data.local.entity.MediaCatalogMetadataEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPlaylistEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPlaylistItemEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import com.sysadmindoc.alarmclock.domain.PlaybackTrackCandidate
import com.sysadmindoc.alarmclock.domain.PlaylistSnapshot
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicFacetOption
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaylistOption
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsDraft
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsRepository
import com.sysadmindoc.alarmclock.ui.ringtone.AddTracksResult
import com.sysadmindoc.alarmclock.ui.ringtone.CatalogMediaType
import com.sysadmindoc.alarmclock.ui.ringtone.DeletePlaylistResult
import com.sysadmindoc.alarmclock.ui.ringtone.ExcludedTrackResolution
import com.sysadmindoc.alarmclock.ui.ringtone.MediaAvailability
import com.sysadmindoc.alarmclock.ui.ringtone.MediaCatalogItem
import com.sysadmindoc.alarmclock.ui.ringtone.MediaCatalogPage
import com.sysadmindoc.alarmclock.ui.ringtone.MediaCatalogRepository
import com.sysadmindoc.alarmclock.ui.ringtone.MediaCatalogScope
import com.sysadmindoc.alarmclock.ui.ringtone.MediaCatalogSourceStatus
import com.sysadmindoc.alarmclock.ui.ringtone.MediaProviderKind
import com.sysadmindoc.alarmclock.ui.ringtone.MusicArtistFacet
import com.sysadmindoc.alarmclock.ui.ringtone.MusicCatalogTrackMetadata
import com.sysadmindoc.alarmclock.ui.ringtone.MusicFolderFacet
import com.sysadmindoc.alarmclock.ui.ringtone.MusicLibraryIndexer
import com.sysadmindoc.alarmclock.ui.ringtone.MusicLibrarySupplementalCatalog
import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylist
import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistItem
import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistKind
import com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLibraryRepository @Inject constructor(
    private val musicLibraryDao: MusicLibraryDao,
    private val playbackHistoryDao: PlaybackHistoryDao
) : MediaCatalogRepository, MusicPlaylistRepository, AlarmMusicSettingsRepository {

    private companion object {
        const val MAX_EXCLUDED_URIS_PER_QUERY = 900
        const val DEFAULT_RECENT_HISTORY_LIMIT = 100
        const val SMART_ALL_DEFAULT_NAME = "All available music"
        const val FAVORITES_DEFAULT_NAME = "Favorites"
    }

    private val latestSourceStatuses = MutableStateFlow<List<MediaCatalogSourceStatus>>(emptyList())

    suspend fun count(): Int = musicLibraryDao.count()

    suspend fun allTracks(): List<MusicTrack> = musicLibraryDao.getAll()

    suspend fun replaceTracks(tracks: List<MusicTrack>) = musicLibraryDao.replaceAll(tracks)

    suspend fun replaceTracksWithMetadata(
        tracks: List<MusicTrack>,
        metadataByUri: Map<String, MusicCatalogTrackMetadata>,
        videoSourceStatus: MediaCatalogSourceStatus? = null
    ) {
        val now = System.currentTimeMillis()
        val entities = tracks.distinctBy { it.uri }.map { track ->
            val meta = metadataByUri[track.uri]
            val providerKind = when (track.source) {
                MusicLibraryIndexer.SOURCE_SAF_VIDEO -> MediaProviderKind.SAF_VIDEO_TREE
                MusicLibraryIndexer.SOURCE_SAF -> MediaProviderKind.SAF_TREE
                MusicLibraryIndexer.SOURCE_MEDIA_STORE_VIDEO -> MediaProviderKind.MEDIA_STORE_VIDEO
                else -> MediaProviderKind.MEDIA_STORE_AUDIO
            }
            val mediaType = meta?.mediaType ?: if (MusicLibraryIndexer.isVideoSource(track.source)) {
                CatalogMediaType.VIDEO_AUDIO
            } else {
                CatalogMediaType.AUDIO
            }
            val folderPath = meta?.folderPath.orEmpty().trim()
            val folderName = folderPath.substringAfterLast('/', folderPath).trim()
            MediaCatalogMetadataEntity(
                uri = track.uri,
                providerKind = providerKind.name,
                mediaType = mediaType.name,
                volumeName = "",
                relativePath = folderPath,
                folderName = folderName,
                artist = meta?.artist.orEmpty(),
                album = meta?.album.orEmpty(),
                mimeType = "",
                container = meta?.container.orEmpty(),
                durationMs = meta?.durationMillis,
                audioProbeStatus = (meta?.availability ?: MediaAvailability.PLAYABLE).name,
                decoderStatus = (meta?.availability ?: MediaAvailability.PLAYABLE).name,
                isSystemTone = meta?.isSystemTone ?: false,
                permissionAvailability = "GRANTED",
                failureReason = meta?.codecWarning.orEmpty(),
                lastSeenAt = now
            )
        }
        musicLibraryDao.replaceTracksAndMetadata(tracks, entities)
        if (videoSourceStatus != null) {
            latestSourceStatuses.value = listOf(videoSourceStatus)
        }
    }

    suspend fun poolCandidates(): List<MusicPoolTrack> = musicLibraryDao.getPoolCandidates()

    suspend fun includedPoolCount(): Int = musicLibraryDao.includedCount()

    /** Read current catalog inside the DAO transaction, not a cached UI snapshot. */
    suspend fun includeUnselectedSourceTracks(sourceUris: Collection<String>): Int =
        musicLibraryDao.includeUnselectedSourceTracks(sourceUris)

    suspend fun setPoolMembership(uris: Collection<String>, included: Boolean) {
        if (uris.isEmpty()) return
        val state = if (included) MusicPoolEntry.INCLUDED else MusicPoolEntry.EXCLUDED
        val updatedAt = System.currentTimeMillis()
        musicLibraryDao.setPoolEntries(
            uris.distinct().map { uri -> MusicPoolEntry(uri, state, updatedAt) }
        )
    }

    suspend fun pickRandomTrack(): MusicTrack? = musicLibraryDao.pickRandom()

    suspend fun pickRandomTrack(excludedUris: List<String>): MusicTrack? =
        when {
            excludedUris.isEmpty() -> musicLibraryDao.pickRandom()
            excludedUris.size <= MAX_EXCLUDED_URIS_PER_QUERY ->
                musicLibraryDao.pickRandomExcluding(excludedUris)
            else -> {
                val excluded = excludedUris.toHashSet()
                musicLibraryDao.getIncludedTracks()
                    .asSequence()
                    .filterNot { it.uri in excluded }
                    .toList()
                    .randomOrNull()
            }
        }

    suspend fun recordStarted(fireId: String, startedAt: Long, track: MusicTrack) {
        playbackHistoryDao.appendStarted(fireId, startedAt, track.displayTitle, track.uri)
    }

    suspend fun historyForDate(fromMillis: Long, toMillis: Long) =
        playbackHistoryDao.getForDate(fromMillis, toMillis)

    suspend fun recentHistory(limit: Int = DEFAULT_RECENT_HISTORY_LIMIT) =
        playbackHistoryDao.getRecent(limit.coerceAtLeast(0))

    suspend fun historyEntry(historyId: Long) =
        playbackHistoryDao.getById(historyId)

    // =========================================================================
    // Supplemental Catalog & Facets
    // =========================================================================

    suspend fun buildSupplementalCatalog(
        videoSourceStatusOverride: MediaCatalogSourceStatus? = null
    ): MusicLibrarySupplementalCatalog {
        val candidates = musicLibraryDao.getPoolCandidates()
        val metadataRows = musicLibraryDao.getAllCatalogMetadata().associateBy { it.uri }
        val trackMetadataByUri = LinkedHashMap<String, MusicCatalogTrackMetadata>()
        val artistsById = LinkedHashMap<String, MutableSet<String>>()
        val artistDisplayNames = LinkedHashMap<String, String>()
        val foldersById = LinkedHashMap<String, MutableSet<String>>()
        val folderAudioCounts = HashMap<String, Int>()
        val folderVideoCounts = HashMap<String, Int>()
        var hasFlv = false

        for (candidate in candidates) {
            val uri = candidate.track.uri
            val entity = metadataRows[uri]
            val mediaType = when {
                entity?.mediaType == CatalogMediaType.VIDEO_AUDIO.name -> CatalogMediaType.VIDEO_AUDIO
                MusicLibraryIndexer.isVideoSource(candidate.track.source) -> CatalogMediaType.VIDEO_AUDIO
                else -> CatalogMediaType.AUDIO
            }
            val availability = runCatching {
                entity?.audioProbeStatus?.let(MediaAvailability::valueOf)
            }.getOrNull() ?: MediaAvailability.PLAYABLE
            val container = entity?.container.orEmpty()
            if (container.equals("flv", ignoreCase = true) ||
                candidate.track.fileName.endsWith(".flv", ignoreCase = true)
            ) {
                hasFlv = true
            }
            val artist = entity?.artist.orEmpty().trim()
            val folderPath = entity?.relativePath.orEmpty().trim()
            trackMetadataByUri[uri] = MusicCatalogTrackMetadata(
                uri = uri,
                artist = artist,
                album = entity?.album.orEmpty(),
                folderPath = folderPath,
                durationMillis = entity?.durationMs,
                mediaType = mediaType,
                availability = availability,
                codecWarning = if (container.equals("flv", ignoreCase = true)) "FLV" else "",
                container = container,
                seekSupported = !container.equals("flv", ignoreCase = true),
                isSystemTone = entity?.isSystemTone ?: false
            )
            if (availability == MediaAvailability.PLAYABLE) {
                if (artist.isNotBlank()) {
                    val artistKey = artist.lowercase(Locale.ROOT)
                    artistDisplayNames.putIfAbsent(artistKey, artist)
                    artistsById.getOrPut(artistKey) { LinkedHashSet() }.add(uri)
                }
                if (folderPath.isNotBlank()) {
                    foldersById.getOrPut(folderPath) { LinkedHashSet() }.add(uri)
                    if (mediaType == CatalogMediaType.VIDEO_AUDIO) {
                        folderVideoCounts[folderPath] = (folderVideoCounts[folderPath] ?: 0) + 1
                    } else {
                        folderAudioCounts[folderPath] = (folderAudioCounts[folderPath] ?: 0) + 1
                    }
                }
            }
        }

        val artists = artistsById.entries
            .map { (key, uris) ->
                MusicArtistFacet(
                    id = artistDisplayNames[key] ?: key,
                    displayName = artistDisplayNames[key] ?: key,
                    playableTrackCount = uris.size,
                    trackUris = uris
                )
            }
            .sortedBy { it.displayName.lowercase(Locale.ROOT) }

        val folders = foldersById.entries
            .map { (path, uris) ->
                val displayName = path.trimEnd('/', '\\').substringAfterLast('/', path)
                MusicFolderFacet(
                    id = path,
                    displayName = displayName.ifBlank { path },
                    path = path,
                    playableTrackCount = uris.size,
                    audioTrackCount = folderAudioCounts[path] ?: 0,
                    videoAudioTrackCount = folderVideoCounts[path] ?: 0,
                    trackUris = uris
                )
            }
            .sortedBy { it.displayName.lowercase(Locale.ROOT) }

        return MusicLibrarySupplementalCatalog(
            artists = artists,
            folders = folders,
            trackMetadataByUri = trackMetadataByUri,
            videoSourceStatus = videoSourceStatusOverride ?: latestSourceStatuses.value.firstOrNull(),
            flvSupportNoteVisible = hasFlv
        )
    }

    // =========================================================================
    // MediaCatalogRepository implementation
    // =========================================================================

    override fun observeCatalog(
        scope: MediaCatalogScope,
        search: String,
        pageSize: Int,
        offset: Int
    ): Flow<MediaCatalogPage> {
        return combine(
            musicLibraryDao.observePoolCandidates(),
            musicLibraryDao.observeAllCatalogMetadata(),
            musicLibraryDao.observeAllPlaylistItems()
        ) { candidates, metadataList, playlistItems ->
            val metaByUri = metadataList.associateBy { it.uri }
            val playlistUris = if (scope is MediaCatalogScope.Playlist) {
                playlistItems
                    .filter { it.playlistId == scope.playlistId }
                    .mapTo(HashSet()) { it.trackUri }
            } else {
                emptySet()
            }
            val query = search.trim().lowercase(Locale.ROOT)
            val allMatching = candidates.mapNotNull { candidate ->
                val item = toMediaCatalogItem(candidate, metaByUri[candidate.track.uri])
                val matchesScope = when (scope) {
                    MediaCatalogScope.All -> true
                    MediaCatalogScope.AudioOnly -> item.mediaType == CatalogMediaType.AUDIO
                    MediaCatalogScope.VideoAudioOnly -> item.mediaType == CatalogMediaType.VIDEO_AUDIO
                    is MediaCatalogScope.Folder -> item.folderPath.equals(scope.folderId, ignoreCase = true)
                    is MediaCatalogScope.Artist -> item.artist.equals(scope.artistId, ignoreCase = true)
                    is MediaCatalogScope.Playlist ->
                        if (scope.playlistId == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID) {
                            item.poolMembershipState == MusicPoolEntry.INCLUDED
                        } else {
                            item.uri in playlistUris
                        }
                }
                if (!matchesScope) return@mapNotNull null
                if (query.isNotEmpty()) {
                    val hay = listOf(
                        item.displayTitle,
                        item.fileName,
                        item.metadataTitle,
                        item.artist,
                        item.album,
                        item.folderPath
                    ).joinToString(" ").lowercase(Locale.ROOT)
                    if (query !in hay) return@mapNotNull null
                }
                item
            }
            val safeOffset = offset.coerceAtLeast(0)
            val safePageSize = pageSize.coerceAtLeast(1)
            val pageItems = allMatching.drop(safeOffset).take(safePageSize)
            MediaCatalogPage(
                items = pageItems,
                totalCount = allMatching.size,
                offset = safeOffset,
                pageSize = safePageSize
            )
        }
    }

    override fun observeSources(): Flow<List<MediaCatalogSourceStatus>> = latestSourceStatuses

    override suspend fun refreshMediaStore(force: Boolean) {
        // Coordinated by MusicLibraryViewModel / MusicLibraryIndexer
    }

    override suspend fun rescanSafSource(sourceId: String) {
        // Coordinated by MusicLibraryViewModel / MusicLibraryIndexer
    }

    private fun toMediaCatalogItem(
        candidate: MusicPoolTrack,
        meta: MediaCatalogMetadataEntity?
    ): MediaCatalogItem {
        val track = candidate.track
        val providerKind = runCatching {
            meta?.providerKind?.let(MediaProviderKind::valueOf)
        }.getOrNull() ?: when (track.source) {
            MusicLibraryIndexer.SOURCE_SAF_VIDEO -> MediaProviderKind.SAF_VIDEO_TREE
            MusicLibraryIndexer.SOURCE_SAF -> MediaProviderKind.SAF_TREE
            MusicLibraryIndexer.SOURCE_MEDIA_STORE_VIDEO -> MediaProviderKind.MEDIA_STORE_VIDEO
            else -> MediaProviderKind.MEDIA_STORE_AUDIO
        }
        val mediaType = runCatching {
            meta?.mediaType?.let(CatalogMediaType::valueOf)
        }.getOrNull() ?: if (MusicLibraryIndexer.isVideoSource(track.source)) {
            CatalogMediaType.VIDEO_AUDIO
        } else {
            CatalogMediaType.AUDIO
        }
        val availability = runCatching {
            meta?.audioProbeStatus?.let(MediaAvailability::valueOf)
        }.getOrNull() ?: MediaAvailability.PLAYABLE
        val container = meta?.container.orEmpty()
        return MediaCatalogItem(
            uri = track.uri,
            displayTitle = track.displayTitle,
            fileName = track.fileName,
            metadataTitle = track.metadataTitle,
            artist = meta?.artist.orEmpty(),
            album = meta?.album.orEmpty(),
            folderPath = meta?.relativePath.orEmpty(),
            durationMillis = meta?.durationMs,
            providerKind = providerKind,
            mediaType = mediaType,
            poolMembershipState = candidate.membershipState,
            availability = availability,
            mimeType = meta?.mimeType.orEmpty(),
            container = container,
            audioCodec = "",
            seekSupported = !container.equals("flv", ignoreCase = true),
            isSystemTone = meta?.isSystemTone ?: false,
            failureReason = meta?.failureReason.orEmpty()
        )
    }

    // =========================================================================
    // MusicPlaylistRepository implementation
    // =========================================================================

    override fun observePlaylists(): Flow<List<MusicPlaylist>> {
        return combine(
            musicLibraryDao.observePlaylistEntities(),
            musicLibraryDao.observeAllPlaylistItems(),
            musicLibraryDao.observePoolCandidates(),
            musicLibraryDao.observeAllCatalogMetadata(),
            musicLibraryDao.observeAllAlarmMusicSettings()
        ) { playlistRows, itemRows, candidates, metadataRows, alarmSettings ->
            buildPlaylistsSnapshot(playlistRows, itemRows, candidates, metadataRows, alarmSettings)
        }
    }

    suspend fun getPlaylistsSnapshot(): List<MusicPlaylist> {
        return buildPlaylistsSnapshot(
            playlistRows = musicLibraryDao.getAllPlaylists(),
            itemRows = musicLibraryDao.getAllPlaylistItems(),
            candidates = musicLibraryDao.getPoolCandidates(),
            metadataRows = musicLibraryDao.getAllCatalogMetadata(),
            alarmSettings = musicLibraryDao.getAllAlarmMusicSettings()
        )
    }

    private fun buildPlaylistsSnapshot(
        playlistRows: List<MusicPlaylistEntity>,
        itemRows: List<MusicPlaylistItemEntity>,
        candidates: List<MusicPoolTrack>,
        metadataRows: List<MediaCatalogMetadataEntity>,
        alarmSettings: List<AlarmMusicSettingsEntity>
    ): List<MusicPlaylist> {
        val candidateByUri = candidates.associateBy { it.track.uri }
        val metaByUri = metadataRows.associateBy { it.uri }
        val itemsByPlaylist = itemRows.groupBy { it.playlistId }
        val refsByPlaylist = alarmSettings.groupingBy { it.playlistId }.eachCount()

        var smartAllPlayable = 0
        var smartAllUnavailable = 0
        for (candidate in candidates) {
            if (candidate.membershipState == MusicPoolEntry.INCLUDED) {
                val meta = metaByUri[candidate.track.uri]
                if (meta == null || meta.audioProbeStatus == MediaAvailability.PLAYABLE.name) {
                    smartAllPlayable++
                } else {
                    smartAllUnavailable++
                }
            }
        }

        val smartAll = MusicPlaylist(
            playlistId = MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID,
            name = SMART_ALL_DEFAULT_NAME,
            kind = MusicPlaylistKind.SMART_ALL,
            ownerAlarmId = null,
            playableTrackCount = smartAllPlayable,
            unavailableTrackCount = smartAllUnavailable,
            referencingAlarmCount = refsByPlaylist[null] ?: 0
        )

        val stored = playlistRows.map { row ->
            val kind = runCatching { MusicPlaylistKind.valueOf(row.kind) }
                .getOrDefault(MusicPlaylistKind.NAMED)
            val rowsForPlaylist = itemsByPlaylist[row.playlistId].orEmpty()
            var playable = 0
            var unavailable = 0
            for (item in rowsForPlaylist) {
                val cand = candidateByUri[item.trackUri]
                val meta = metaByUri[item.trackUri]
                val isIncluded = cand?.membershipState == MusicPoolEntry.INCLUDED
                val isPlayable = cand != null && isIncluded &&
                    (meta == null || meta.audioProbeStatus == MediaAvailability.PLAYABLE.name)
                if (isPlayable) playable++ else unavailable++
            }
            MusicPlaylist(
                playlistId = row.playlistId,
                name = row.name,
                kind = kind,
                ownerAlarmId = row.ownerAlarmId,
                playableTrackCount = playable,
                unavailableTrackCount = unavailable,
                referencingAlarmCount = refsByPlaylist[row.playlistId] ?: 0
            )
        }

        return buildList {
            add(smartAll)
            addAll(stored)
        }
    }

    override fun observeItems(playlistId: String): Flow<List<MusicPlaylistItem>> {
        if (playlistId == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID) {
            return combine(
                musicLibraryDao.observePoolCandidates(),
                musicLibraryDao.observeAllCatalogMetadata()
            ) { candidates, metadataRows ->
                val metaByUri = metadataRows.associateBy { it.uri }
                candidates
                    .filter { it.membershipState == MusicPoolEntry.INCLUDED }
                    .mapIndexed { index, candidate ->
                        val meta = metaByUri[candidate.track.uri]
                        toPlaylistItem(
                            playlistId = MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID,
                            playlistItemId = "smart_all:${candidate.track.uri}",
                            trackUri = candidate.track.uri,
                            position = index,
                            candidate = candidate,
                            meta = meta
                        )
                    }
            }
        }
        return combine(
            musicLibraryDao.observePlaylistItems(playlistId),
            musicLibraryDao.observePoolCandidates(),
            musicLibraryDao.observeAllCatalogMetadata()
        ) { items, candidates, metadataRows ->
            val candidateByUri = candidates.associateBy { it.track.uri }
            val metaByUri = metadataRows.associateBy { it.uri }
            items.sortedBy { it.position }.map { item ->
                toPlaylistItem(
                    playlistId = item.playlistId,
                    playlistItemId = item.playlistItemId,
                    trackUri = item.trackUri,
                    position = item.position,
                    candidate = candidateByUri[item.trackUri],
                    meta = metaByUri[item.trackUri]
                )
            }
        }
    }

    private fun toPlaylistItem(
        playlistId: String,
        playlistItemId: String,
        trackUri: String,
        position: Int,
        candidate: MusicPoolTrack?,
        meta: MediaCatalogMetadataEntity?
    ): MusicPlaylistItem {
        val track = candidate?.track
        val membership = candidate?.membershipState
        val rawAvailability = runCatching {
            meta?.audioProbeStatus?.let(MediaAvailability::valueOf)
        }.getOrNull() ?: MediaAvailability.PLAYABLE
        val effectiveAvailability = when {
            track == null -> MediaAvailability.UNREADABLE
            membership == MusicPoolEntry.EXCLUDED -> MediaAvailability.UNREADABLE
            else -> rawAvailability
        }
        val mediaType = runCatching {
            meta?.mediaType?.let(CatalogMediaType::valueOf)
        }.getOrNull() ?: if (track != null && MusicLibraryIndexer.isVideoSource(track.source)) {
            CatalogMediaType.VIDEO_AUDIO
        } else {
            CatalogMediaType.AUDIO
        }
        val fallbackTitle = trackUri.substringAfterLast('/').ifBlank { trackUri }
        return MusicPlaylistItem(
            playlistId = playlistId,
            playlistItemId = playlistItemId,
            trackUri = trackUri,
            position = position,
            displayTitle = track?.displayTitle ?: fallbackTitle,
            artist = meta?.artist.orEmpty(),
            folderPath = meta?.relativePath.orEmpty(),
            durationMillis = meta?.durationMs,
            mediaType = mediaType,
            availability = effectiveAvailability,
            poolMembershipState = membership
        )
    }

    override suspend fun create(name: String): MusicPlaylist {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Playlist name is required" }
        val now = System.currentTimeMillis()
        val entity = MusicPlaylistEntity(
            playlistId = UUID.randomUUID().toString(),
            name = cleanName,
            kind = MusicPlaylistKind.NAMED.name,
            ownerAlarmId = null,
            createdAt = now,
            updatedAt = now
        )
        musicLibraryDao.upsertPlaylist(entity)
        return MusicPlaylist(
            playlistId = entity.playlistId,
            name = entity.name,
            kind = MusicPlaylistKind.NAMED,
            ownerAlarmId = null,
            playableTrackCount = 0,
            unavailableTrackCount = 0
        )
    }

    override suspend fun rename(playlistId: String, name: String): MusicPlaylist {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Playlist name is required" }
        val existing = musicLibraryDao.getPlaylistById(playlistId)
            ?: error("Playlist not found")
        val updated = existing.copy(
            name = cleanName,
            updatedAt = System.currentTimeMillis()
        )
        musicLibraryDao.upsertPlaylist(updated)
        return getPlaylistsSnapshot().first { it.playlistId == playlistId }
    }

    override suspend fun duplicate(playlistId: String, ownerAlarmId: Long?): MusicPlaylist {
        val now = System.currentTimeMillis()
        val isSmartAll = playlistId == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID
        val sourcePlaylist = if (isSmartAll) {
            null
        } else {
            musicLibraryDao.getPlaylistById(playlistId) ?: error("Playlist not found")
        }
        val baseName = sourcePlaylist?.name ?: SMART_ALL_DEFAULT_NAME
        val newName = if (ownerAlarmId != null) {
            "$baseName (Alarm #$ownerAlarmId)"
        } else {
            "$baseName copy"
        }
        val newKind = if (ownerAlarmId != null) {
            MusicPlaylistKind.ALARM_COPY
        } else {
            MusicPlaylistKind.NAMED
        }
        val newPlaylistId = UUID.randomUUID().toString()
        val newEntity = MusicPlaylistEntity(
            playlistId = newPlaylistId,
            name = newName,
            kind = newKind.name,
            ownerAlarmId = ownerAlarmId,
            createdAt = now,
            updatedAt = now
        )
        musicLibraryDao.upsertPlaylist(newEntity)

        val sourceUris = if (isSmartAll) {
            musicLibraryDao.getIncludedTracks().map { it.uri }
        } else {
            musicLibraryDao.getPlaylistItems(playlistId)
                .sortedBy { it.position }
                .map { it.trackUri }
        }
        if (sourceUris.isNotEmpty()) {
            val copiedItems = sourceUris.mapIndexed { index, uri ->
                MusicPlaylistItemEntity(
                    playlistItemId = UUID.randomUUID().toString(),
                    playlistId = newPlaylistId,
                    trackUri = uri,
                    position = index,
                    addedAt = now
                )
            }
            musicLibraryDao.insertPlaylistItems(copiedItems)
        }
        return getPlaylistsSnapshot().first { it.playlistId == newPlaylistId }
    }

    override suspend fun addTracks(
        playlistId: String,
        selectedUris: List<String>,
        excludedResolution: ExcludedTrackResolution
    ): AddTracksResult {
        require(playlistId != MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID) {
            "Cannot directly add tracks to smart_all playlist"
        }
        return musicLibraryDao.addTracksToPlaylistTransaction(
            playlistId = playlistId,
            selectedUris = selectedUris,
            excludedResolution = excludedResolution
        )
    }

    override suspend fun removeItem(playlistItemId: String) {
        musicLibraryDao.removePlaylistItemTransaction(playlistItemId)
    }

    override suspend fun removeItems(playlistId: String, playlistItemIds: List<String>) {
        musicLibraryDao.removePlaylistItemsTransaction(playlistId, playlistItemIds)
    }

    override suspend fun reorder(playlistId: String, orderedPlaylistItemIds: List<String>) {
        musicLibraryDao.reorderPlaylistItemsTransaction(playlistId, orderedPlaylistItemIds)
    }

    override suspend fun deleteWithReferencesCheck(playlistId: String): DeletePlaylistResult {
        require(playlistId != MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID) {
            "Built-in smart playlist cannot be deleted"
        }
        require(playlistId != MusicPlaylistRepository.FAVORITES_PLAYLIST_ID) {
            "Favorites playlist cannot be deleted"
        }
        return musicLibraryDao.deletePlaylistWithReferenceCheckTransaction(playlistId)
    }

    // =========================================================================
    // AlarmMusicSettingsRepository implementation
    // =========================================================================

    override fun observeSettings(alarmId: Long): Flow<AlarmMusicSettingsDraft> {
        if (alarmId <= 0L) {
            return MutableStateFlow(AlarmMusicSettingsDraft())
        }
        return combine(
            musicLibraryDao.observeAlarmMusicSettings(alarmId),
            musicLibraryDao.observeAlarmMusicFacetFilters(alarmId)
        ) { settings, filters ->
            toAlarmMusicSettingsDraft(settings, filters)
        }
    }

    override suspend fun getSettings(alarmId: Long): AlarmMusicSettingsDraft {
        if (alarmId <= 0L) return AlarmMusicSettingsDraft()
        val settings = musicLibraryDao.getAlarmMusicSettings(alarmId)
        val filters = musicLibraryDao.getAlarmMusicFacetFilters(alarmId)
        return toAlarmMusicSettingsDraft(settings, filters)
    }

    override suspend fun saveSettings(alarmId: Long, draft: AlarmMusicSettingsDraft) {
        if (alarmId <= 0L) return
        val normalizedPlaylistId = draft.playlistId
            ?.takeUnless { it == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID }
        val effectiveStrategy = if (
            normalizedPlaylistId == null &&
            draft.playbackStrategy == AlarmMusicPlaybackStrategy.PLAYLIST_ORDER
        ) {
            AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS
        } else {
            draft.playbackStrategy
        }
        val entity = AlarmMusicSettingsEntity(
            alarmId = alarmId,
            playlistId = normalizedPlaylistId,
            playbackStrategy = effectiveStrategy.name,
            continuousUntilDismiss = draft.continuousUntilDismiss,
            updatedAt = System.currentTimeMillis()
        )
        val filterEntities = buildList {
            draft.artistFilters.filter { it.isNotBlank() }.forEach { artist ->
                add(
                    AlarmMusicFacetFilterEntity(
                        alarmId = alarmId,
                        kind = AlarmMusicFacetFilterEntity.KIND_ARTIST,
                        facetId = artist
                    )
                )
            }
            draft.folderFilters.filter { it.isNotBlank() }.forEach { folder ->
                add(
                    AlarmMusicFacetFilterEntity(
                        alarmId = alarmId,
                        kind = AlarmMusicFacetFilterEntity.KIND_FOLDER,
                        facetId = folder
                    )
                )
            }
        }
        musicLibraryDao.saveAlarmMusicSettingsTransaction(entity, filterEntities)
    }

    private fun toAlarmMusicSettingsDraft(
        settings: AlarmMusicSettingsEntity?,
        filters: List<AlarmMusicFacetFilterEntity>
    ): AlarmMusicSettingsDraft {
        if (settings == null) return AlarmMusicSettingsDraft()
        val strategy = runCatching {
            AlarmMusicPlaybackStrategy.valueOf(settings.playbackStrategy)
        }.getOrDefault(AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
        val artists = filters
            .filter { it.kind == AlarmMusicFacetFilterEntity.KIND_ARTIST }
            .mapTo(LinkedHashSet()) { it.facetId }
        val folders = filters
            .filter { it.kind == AlarmMusicFacetFilterEntity.KIND_FOLDER }
            .mapTo(LinkedHashSet()) { it.facetId }
        return AlarmMusicSettingsDraft(
            playlistId = settings.playlistId
                ?.takeUnless { it == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID },
            artistFilters = artists,
            folderFilters = folders,
            playbackStrategy = strategy,
            continuousUntilDismiss = settings.continuousUntilDismiss
        )
    }

    /**
     * Evaluates a [AlarmMusicSettingsDraft] against the current database catalog and
     * returns playlist options, artist facets, folder facets, and matching track counts.
     */
    suspend fun evaluateAlarmMusicDraft(
        draft: AlarmMusicSettingsDraft
    ): EvaluatedAlarmMusicSettings {
        val playlists = getPlaylistsSnapshot()
        val supplemental = buildSupplementalCatalog()
        val snapshot = buildPlaylistSnapshotForDraft(draft)
        val eligibleCount = snapshot.eligibleEntries().size
        val selectedOption = playlists.firstOrNull {
            val normalizedId = it.playlistId
                .takeUnless { id -> id == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID }
            normalizedId == draft.playlistId
        }
        return EvaluatedAlarmMusicSettings(
            playlists = playlists
                .filterNot { it.kind == MusicPlaylistKind.SMART_ALL }
                .map { playlist ->
                    AlarmMusicPlaylistOption(
                        playlistId = playlist.playlistId,
                        name = playlist.name,
                        kind = playlist.kind,
                        playableTrackCount = playlist.playableTrackCount,
                        unavailableTrackCount = playlist.unavailableTrackCount
                    )
                },
            artists = supplemental.artists.map {
                AlarmMusicFacetOption(
                    id = it.id,
                    name = it.displayName,
                    playableTrackCount = it.playableTrackCount
                )
            },
            folders = supplemental.folders.map {
                AlarmMusicFacetOption(
                    id = it.id,
                    name = it.displayName,
                    playableTrackCount = it.playableTrackCount
                )
            },
            matchingPlayableTrackCount = eligibleCount,
            unavailableTrackCount = selectedOption?.unavailableTrackCount ?: 0
        )
    }

    /**
     * Loads the runtime [PlaylistSnapshot] for [alarmId] at alarm fire time.
     *
     * Effective playable set:
     * `GLOBAL INCLUDED` ∩ `Selected Playlist (or SMART_ALL)` ∩ `Artist/Folder Facet Filters` ∩ `Runtime Playable`.
     */
    suspend fun loadPlaylistSnapshotForAlarm(alarmId: Long): PlaylistSnapshot {
        val draft = getSettings(alarmId)
        return buildPlaylistSnapshotForDraft(draft)
    }

    suspend fun buildPlaylistSnapshotForDraft(draft: AlarmMusicSettingsDraft): PlaylistSnapshot {
        val candidates = musicLibraryDao.getPoolCandidates()
        val includedByUri = candidates
            .filter { it.membershipState == MusicPoolEntry.INCLUDED }
            .associate { it.track.uri to it.track }
        val metaByUri = musicLibraryDao.getAllCatalogMetadata().associateBy { it.uri }
        val playlistId = draft.playlistId
            ?.takeUnless { it == MusicPlaylistRepository.SMART_ALL_PLAYLIST_ID }

        val entries = if (playlistId == null) {
            includedByUri.values.mapIndexed { index, track ->
                val meta = metaByUri[track.uri]
                val folderIds = buildSet {
                    meta?.relativePath?.takeIf { it.isNotBlank() }?.let(::add)
                    meta?.folderName?.takeIf { it.isNotBlank() }?.let(::add)
                }
                PlaybackTrackCandidate(
                    track = track,
                    playlistItemId = null,
                    position = index,
                    artistId = meta?.artist.orEmpty(),
                    folderIds = folderIds,
                    isPlayableAtRuntime = meta == null ||
                        meta.audioProbeStatus == MediaAvailability.PLAYABLE.name
                )
            }
        } else {
            val playlistItems = musicLibraryDao.getPlaylistItems(playlistId)
                .sortedBy { it.position }
            playlistItems.mapNotNull { item ->
                // Must also be in GLOBAL INCLUDED (explicitly EXCLUDED tracks never play)
                val track = includedByUri[item.trackUri] ?: return@mapNotNull null
                val meta = metaByUri[track.uri]
                val folderIds = buildSet {
                    meta?.relativePath?.takeIf { it.isNotBlank() }?.let(::add)
                    meta?.folderName?.takeIf { it.isNotBlank() }?.let(::add)
                }
                PlaybackTrackCandidate(
                    track = track,
                    playlistItemId = item.playlistItemId,
                    position = item.position,
                    artistId = meta?.artist.orEmpty(),
                    folderIds = folderIds,
                    isPlayableAtRuntime = meta == null ||
                        meta.audioProbeStatus == MediaAvailability.PLAYABLE.name
                )
            }
        }

        return PlaylistSnapshot(
            playlistId = playlistId,
            playbackStrategy = draft.playbackStrategy,
            artistFilters = draft.artistFilters,
            folderFilters = draft.folderFilters,
            entries = entries
        )
    }

    suspend fun exportPlaylistsForBackup(): List<MusicPlaylistBackup> {
        val playlists = musicLibraryDao.getAllPlaylists()
        val itemsByPlaylist = musicLibraryDao.getAllPlaylistItems()
            .groupBy { it.playlistId }
        return playlists.map { playlist ->
            MusicPlaylistBackup(
                playlistId = playlist.playlistId,
                name = playlist.name,
                kind = playlist.kind,
                ownerAlarmId = playlist.ownerAlarmId,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                items = itemsByPlaylist[playlist.playlistId]
                    .orEmpty()
                    .sortedBy { it.position }
                    .map { item ->
                        MusicPlaylistItemBackup(
                            playlistItemId = item.playlistItemId,
                            trackUri = item.trackUri,
                            position = item.position,
                            addedAt = item.addedAt
                        )
                    }
            )
        }
    }

    suspend fun exportAlarmMusicSettingsForBackup(
        alarmIds: List<Long>
    ): List<AlarmMusicSettingsBackup> {
        return alarmIds.mapNotNull { alarmId ->
            val settings = musicLibraryDao.getAlarmMusicSettings(alarmId) ?: return@mapNotNull null
            val filters = musicLibraryDao.getAlarmMusicFacetFilters(alarmId)
            AlarmMusicSettingsBackup(
                alarmId = alarmId,
                playlistId = settings.playlistId,
                playbackStrategy = settings.playbackStrategy,
                artistFilters = filters
                    .filter { it.kind == AlarmMusicFacetFilterEntity.KIND_ARTIST }
                    .map { it.facetId },
                folderFilters = filters
                    .filter { it.kind == AlarmMusicFacetFilterEntity.KIND_FOLDER }
                    .map { it.facetId },
                continuousUntilDismiss = settings.continuousUntilDismiss
            )
        }
    }

    suspend fun importPlaylistsFromBackup(playlists: List<MusicPlaylistBackup>) {
        if (playlists.isEmpty()) return
        for (playlist in playlists) {
            if (playlist.playlistId.isBlank() || playlist.name.isBlank()) continue
            musicLibraryDao.upsertPlaylist(
                MusicPlaylistEntity(
                    playlistId = playlist.playlistId,
                    name = playlist.name,
                    kind = playlist.kind,
                    ownerAlarmId = playlist.ownerAlarmId,
                    createdAt = playlist.createdAt,
                    updatedAt = playlist.updatedAt
                )
            )
            musicLibraryDao.clearPlaylistItems(playlist.playlistId)
            val items = playlist.items
                .filter { it.trackUri.isNotBlank() }
                .sortedBy { it.position }
                .mapIndexed { index, item ->
                    MusicPlaylistItemEntity(
                        playlistItemId = item.playlistItemId.ifBlank {
                            UUID.randomUUID().toString()
                        },
                        playlistId = playlist.playlistId,
                        trackUri = item.trackUri,
                        position = index,
                        addedAt = item.addedAt
                    )
                }
            if (items.isNotEmpty()) {
                musicLibraryDao.insertPlaylistItems(items)
            }
        }
    }

    suspend fun importAlarmMusicSettingsFromBackup(
        alarmId: Long,
        backup: AlarmMusicSettingsBackup
    ) {
        val strategy = runCatching {
            AlarmMusicPlaybackStrategy.valueOf(backup.playbackStrategy)
        }.getOrDefault(AlarmMusicPlaybackStrategy.RANDOM_WITH_REPEATS)
        saveSettings(
            alarmId = alarmId,
            draft = AlarmMusicSettingsDraft(
                playlistId = backup.playlistId,
                artistFilters = backup.artistFilters.toSet(),
                folderFilters = backup.folderFilters.toSet(),
                playbackStrategy = strategy,
                continuousUntilDismiss = backup.continuousUntilDismiss
            )
        )
    }
}

data class EvaluatedAlarmMusicSettings(
    val playlists: List<AlarmMusicPlaylistOption>,
    val artists: List<AlarmMusicFacetOption>,
    val folders: List<AlarmMusicFacetOption>,
    val matchingPlayableTrackCount: Int,
    val unavailableTrackCount: Int
)
