package com.sysadmindoc.alarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sysadmindoc.alarmclock.data.local.entity.AlarmMusicFacetFilterEntity
import com.sysadmindoc.alarmclock.data.local.entity.AlarmMusicSettingsEntity
import com.sysadmindoc.alarmclock.data.local.entity.MediaCatalogMetadataEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPlaylistEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPlaylistItemEntity
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.ui.ringtone.AddTracksResult
import com.sysadmindoc.alarmclock.ui.ringtone.DeletePlaylistResult
import com.sysadmindoc.alarmclock.ui.ringtone.ExcludedTrackResolution
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MusicLibraryDao {
    @Query("SELECT COUNT(*) FROM music_tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM music_tracks ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<MusicTrack>

    @Query("SELECT * FROM music_tracks ORDER BY title COLLATE NOCASE")
    fun observeAllTracks(): Flow<List<MusicTrack>>

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state = '${MusicPoolEntry.INCLUDED}'
        ORDER BY RANDOM() LIMIT 1
        """
    )
    suspend fun pickRandom(): MusicTrack?

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state = '${MusicPoolEntry.INCLUDED}'
          AND music_tracks.uri NOT IN (:excludedUris)
        ORDER BY RANDOM() LIMIT 1
        """
    )
    suspend fun pickRandomExcluding(excludedUris: List<String>): MusicTrack?

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state = '${MusicPoolEntry.INCLUDED}'
        """
    )
    suspend fun getIncludedTracks(): List<MusicTrack>

    @Query(
        """
        SELECT music_tracks.*, music_pool_members.state AS membershipState
        FROM music_tracks
        LEFT JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        ORDER BY music_tracks.title COLLATE NOCASE
        """
    )
    suspend fun getPoolCandidates(): List<MusicPoolTrack>

    @Query(
        """
        SELECT music_tracks.*, music_pool_members.state AS membershipState
        FROM music_tracks
        LEFT JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        ORDER BY music_tracks.title COLLATE NOCASE
        """
    )
    fun observePoolCandidates(): Flow<List<MusicPoolTrack>>

    @Query(
        """
        SELECT COUNT(*) FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state = '${MusicPoolEntry.INCLUDED}'
        """
    )
    suspend fun includedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPoolEntries(entries: List<MusicPoolEntry>)

    @Query("SELECT * FROM music_pool_members")
    suspend fun getAllPoolEntries(): List<MusicPoolEntry>

    @Query("SELECT * FROM music_pool_members")
    fun observeAllPoolEntries(): Flow<List<MusicPoolEntry>>

    /**
     * Add newly indexed candidates from the *current* catalog for one selected
     * SAF source. This intentionally does not rely on the pool dialog's cached
     * row list. In one Room transaction, existing INCLUDED and explicitly
     * EXCLUDED entries are left untouched.
     */
    @Transaction
    suspend fun includeUnselectedSourceTracks(sourceUris: Collection<String>): Int {
        val sourceSet = sourceUris.toHashSet()
        if (sourceSet.isEmpty()) return 0
        val toInclude = getPoolCandidates()
            .asSequence()
            .filter { it.track.uri in sourceSet && it.membershipState == null }
            .map { it.track.uri }
            .distinct()
            .toList()
        if (toInclude.isNotEmpty()) {
            val now = System.currentTimeMillis()
            setPoolEntries(
                toInclude.map { uri ->
                    MusicPoolEntry(uri, MusicPoolEntry.INCLUDED, now)
                }
            )
        }
        return toInclude.size
    }

    @Query("DELETE FROM music_tracks")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrack>)

    @Transaction
    suspend fun replaceAll(tracks: List<MusicTrack>) {
        clear()
        insertAll(tracks.distinctBy { it.uri })
    }

    // =========================================================================
    // Room v28: Media Catalog Metadata
    // =========================================================================

    @Query("SELECT * FROM media_catalog_metadata")
    suspend fun getAllCatalogMetadata(): List<MediaCatalogMetadataEntity>

    @Query("SELECT * FROM media_catalog_metadata")
    fun observeAllCatalogMetadata(): Flow<List<MediaCatalogMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogMetadata(items: List<MediaCatalogMetadataEntity>)

    @Query("DELETE FROM media_catalog_metadata WHERE uri NOT IN (SELECT uri FROM music_tracks)")
    suspend fun pruneOrphanedCatalogMetadata()

    @Transaction
    suspend fun replaceTracksAndMetadata(
        tracks: List<MusicTrack>,
        metadata: List<MediaCatalogMetadataEntity>
    ) {
        replaceAll(tracks)
        if (metadata.isNotEmpty()) {
            upsertCatalogMetadata(metadata.distinctBy { it.uri })
        }
        pruneOrphanedCatalogMetadata()
    }

    // =========================================================================
    // Room v28: Named Playlists & Playlist Items
    // =========================================================================

    @Query("SELECT * FROM music_playlists ORDER BY CASE WHEN kind = 'FAVORITES' THEN 0 ELSE 1 END, name COLLATE NOCASE")
    suspend fun getAllPlaylists(): List<MusicPlaylistEntity>

    @Query("SELECT * FROM music_playlists ORDER BY CASE WHEN kind = 'FAVORITES' THEN 0 ELSE 1 END, name COLLATE NOCASE")
    fun observePlaylistEntities(): Flow<List<MusicPlaylistEntity>>

    @Query("SELECT * FROM music_playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): MusicPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: MusicPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<MusicPlaylistEntity>)

    @Query("DELETE FROM music_playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistRow(playlistId: String)

    @Query("SELECT * FROM music_playlist_items ORDER BY playlistId, position ASC")
    suspend fun getAllPlaylistItems(): List<MusicPlaylistItemEntity>

    @Query("SELECT * FROM music_playlist_items ORDER BY playlistId, position ASC")
    fun observeAllPlaylistItems(): Flow<List<MusicPlaylistItemEntity>>

    @Query("SELECT * FROM music_playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistItems(playlistId: String): List<MusicPlaylistItemEntity>

    @Query("SELECT * FROM music_playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistItems(playlistId: String): Flow<List<MusicPlaylistItemEntity>>

    @Query("SELECT * FROM music_playlist_items WHERE playlistItemId = :playlistItemId LIMIT 1")
    suspend fun getPlaylistItemById(playlistItemId: String): MusicPlaylistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<MusicPlaylistItemEntity>)

    @Query("DELETE FROM music_playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: String)

    @Query("DELETE FROM music_playlist_items WHERE playlistItemId = :playlistItemId")
    suspend fun deletePlaylistItemById(playlistItemId: String)

    @Query("DELETE FROM music_playlist_items WHERE playlistId = :playlistId AND playlistItemId IN (:playlistItemIds)")
    suspend fun deletePlaylistItemsByIds(playlistId: String, playlistItemIds: List<String>)

    @Transaction
    suspend fun addTracksToPlaylistTransaction(
        playlistId: String,
        selectedUris: List<String>,
        excludedResolution: ExcludedTrackResolution,
        nowMillis: Long = System.currentTimeMillis()
    ): AddTracksResult {
        if (selectedUris.isEmpty()) return AddTracksResult()
        val tracksByUri = getAll().associateBy { it.uri }
        val metadataByUri = getAllCatalogMetadata().associateBy { it.uri }
        val poolByUri = getAllPoolEntries().associateBy({ it.uri }, { it.state }).toMutableMap()
        val existingItems = getPlaylistItems(playlistId)
        var nextPosition = (existingItems.maxOfOrNull { it.position } ?: -1) + 1

        val poolUpdates = LinkedHashMap<String, MusicPoolEntry>()
        val itemsToInsert = mutableListOf<MusicPlaylistItemEntity>()
        val excludedSkippedUris = mutableListOf<String>()
        var autoIncludedCandidates = 0
        var excludedItemsSkipped = 0
        var excludedItemsRestored = 0
        var unavailableItemsSkipped = 0

        for (rawUri in selectedUris) {
            val uri = rawUri.trim()
            if (uri.isEmpty()) continue
            val track = tracksByUri[uri]
            val meta = metadataByUri[uri]
            val isPlayable = track != null && (meta == null || meta.audioProbeStatus == "PLAYABLE")
            if (!isPlayable) {
                unavailableItemsSkipped++
                continue
            }

            when (poolByUri[uri]) {
                MusicPoolEntry.EXCLUDED -> {
                    if (excludedResolution == ExcludedTrackResolution.RESTORE_EXCLUDED) {
                        poolByUri[uri] = MusicPoolEntry.INCLUDED
                        poolUpdates[uri] = MusicPoolEntry(uri, MusicPoolEntry.INCLUDED, nowMillis)
                        excludedItemsRestored++
                    } else {
                        excludedItemsSkipped++
                        excludedSkippedUris += uri
                        continue
                    }
                }
                null -> {
                    poolByUri[uri] = MusicPoolEntry.INCLUDED
                    poolUpdates[uri] = MusicPoolEntry(uri, MusicPoolEntry.INCLUDED, nowMillis)
                    autoIncludedCandidates++
                }
                else -> Unit
            }

            itemsToInsert += MusicPlaylistItemEntity(
                playlistItemId = UUID.randomUUID().toString(),
                playlistId = playlistId,
                trackUri = uri,
                position = nextPosition++,
                addedAt = nowMillis
            )
        }

        if (poolUpdates.isNotEmpty()) {
            setPoolEntries(poolUpdates.values.toList())
        }
        if (itemsToInsert.isNotEmpty()) {
            insertPlaylistItems(itemsToInsert)
            getPlaylistById(playlistId)?.let { playlist ->
                upsertPlaylist(playlist.copy(updatedAt = nowMillis))
            }
        }

        return AddTracksResult(
            addedItems = itemsToInsert.size,
            autoIncludedCandidates = autoIncludedCandidates,
            excludedItemsSkipped = excludedItemsSkipped,
            excludedItemsRestored = excludedItemsRestored,
            unavailableItemsSkipped = unavailableItemsSkipped,
            excludedTrackUris = excludedSkippedUris.distinct()
        )
    }

    @Transaction
    suspend fun removePlaylistItemTransaction(playlistItemId: String) {
        val target = getPlaylistItemById(playlistItemId) ?: return
        deletePlaylistItemById(playlistItemId)
        compactPlaylistPositions(target.playlistId)
    }

    @Transaction
    suspend fun removePlaylistItemsTransaction(playlistId: String, playlistItemIds: List<String>) {
        if (playlistItemIds.isEmpty()) return
        deletePlaylistItemsByIds(playlistId, playlistItemIds)
        compactPlaylistPositions(playlistId)
    }

    @Transaction
    suspend fun reorderPlaylistItemsTransaction(
        playlistId: String,
        orderedPlaylistItemIds: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val current = getPlaylistItems(playlistId)
        if (current.isEmpty()) return
        val byId = current.associateBy { it.playlistItemId }
        val seen = HashSet<String>()
        val reordered = mutableListOf<MusicPlaylistItemEntity>()
        var nextPos = 0

        for (id in orderedPlaylistItemIds) {
            val item = byId[id] ?: continue
            if (seen.add(id)) {
                reordered += item.copy(position = nextPos++)
            }
        }
        for (item in current) {
            if (seen.add(item.playlistItemId)) {
                reordered += item.copy(position = nextPos++)
            }
        }
        insertPlaylistItems(reordered)
        getPlaylistById(playlistId)?.let { playlist ->
            upsertPlaylist(playlist.copy(updatedAt = nowMillis))
        }
    }

    @Transaction
    suspend fun compactPlaylistPositions(playlistId: String) {
        val remaining = getPlaylistItems(playlistId)
        val reindexed = remaining.mapIndexed { index, item ->
            if (item.position == index) item else item.copy(position = index)
        }
        if (reindexed != remaining) {
            insertPlaylistItems(reindexed)
        }
    }

    @Query("SELECT COUNT(*) FROM alarm_music_settings WHERE playlistId = :playlistId")
    suspend fun countAlarmsReferencingPlaylist(playlistId: String): Int

    @Transaction
    suspend fun deletePlaylistWithReferenceCheckTransaction(playlistId: String): DeletePlaylistResult {
        val refCount = countAlarmsReferencingPlaylist(playlistId)
        if (refCount > 0) {
            return DeletePlaylistResult.BlockedByAlarmReferences(refCount)
        }
        clearPlaylistItems(playlistId)
        deletePlaylistRow(playlistId)
        return DeletePlaylistResult.Deleted
    }

    // =========================================================================
    // Room v28: Per-Alarm Music Settings & Facet Filters
    // =========================================================================

    @Query("SELECT * FROM alarm_music_settings WHERE alarmId = :alarmId LIMIT 1")
    suspend fun getAlarmMusicSettings(alarmId: Long): AlarmMusicSettingsEntity?

    @Query("SELECT * FROM alarm_music_settings WHERE alarmId = :alarmId LIMIT 1")
    fun observeAlarmMusicSettings(alarmId: Long): Flow<AlarmMusicSettingsEntity?>

    @Query("SELECT * FROM alarm_music_settings")
    suspend fun getAllAlarmMusicSettings(): List<AlarmMusicSettingsEntity>

    @Query("SELECT * FROM alarm_music_settings")
    fun observeAllAlarmMusicSettings(): Flow<List<AlarmMusicSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarmMusicSettings(settings: AlarmMusicSettingsEntity)

    @Query("SELECT * FROM alarm_music_facet_filters WHERE alarmId = :alarmId")
    suspend fun getAlarmMusicFacetFilters(alarmId: Long): List<AlarmMusicFacetFilterEntity>

    @Query("SELECT * FROM alarm_music_facet_filters WHERE alarmId = :alarmId")
    fun observeAlarmMusicFacetFilters(alarmId: Long): Flow<List<AlarmMusicFacetFilterEntity>>

    @Query("SELECT * FROM alarm_music_facet_filters")
    suspend fun getAllAlarmMusicFacetFilters(): List<AlarmMusicFacetFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmMusicFacetFilters(filters: List<AlarmMusicFacetFilterEntity>)

    @Query("DELETE FROM alarm_music_facet_filters WHERE alarmId = :alarmId")
    suspend fun clearAlarmMusicFacetFilters(alarmId: Long)

    @Query("DELETE FROM alarm_music_settings WHERE alarmId = :alarmId")
    suspend fun deleteAlarmMusicSettings(alarmId: Long)

    @Transaction
    suspend fun saveAlarmMusicSettingsTransaction(
        settings: AlarmMusicSettingsEntity,
        filters: List<AlarmMusicFacetFilterEntity>
    ) {
        upsertAlarmMusicSettings(settings)
        clearAlarmMusicFacetFilters(settings.alarmId)
        if (filters.isNotEmpty()) {
            insertAlarmMusicFacetFilters(filters)
        }
    }
}
