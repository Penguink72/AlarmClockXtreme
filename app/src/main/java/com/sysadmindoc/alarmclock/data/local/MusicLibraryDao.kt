package com.sysadmindoc.alarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack

@Dao
interface MusicLibraryDao {
    @Query("SELECT COUNT(*) FROM music_tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM music_tracks ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<MusicTrack>

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
        SELECT COUNT(*) FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state = '${MusicPoolEntry.INCLUDED}'
        """
    )
    suspend fun includedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPoolEntries(entries: List<MusicPoolEntry>)

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
}
