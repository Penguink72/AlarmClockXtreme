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
        WHERE music_pool_members.state IN ('${MusicPoolEntry.INCLUDED}', '${MusicPoolEntry.AUTO_INCLUDED}')
        ORDER BY RANDOM() LIMIT 1
        """
    )
    suspend fun pickRandom(): MusicTrack?

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state IN ('${MusicPoolEntry.INCLUDED}', '${MusicPoolEntry.AUTO_INCLUDED}')
          AND music_tracks.uri NOT IN (:excludedUris)
        ORDER BY RANDOM() LIMIT 1
        """
    )
    suspend fun pickRandomExcluding(excludedUris: List<String>): MusicTrack?

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_pool_members ON music_tracks.uri = music_pool_members.uri
        WHERE music_pool_members.state IN ('${MusicPoolEntry.INCLUDED}', '${MusicPoolEntry.AUTO_INCLUDED}')
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
        WHERE music_pool_members.state IN ('${MusicPoolEntry.INCLUDED}', '${MusicPoolEntry.AUTO_INCLUDED}')
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
            .filter { it.track.uri in sourceSet &&
                (it.membershipState == null || it.membershipState == MusicPoolEntry.AUTO_SKIPPED) }
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

    /**
     * Apply the latest successful scan to previously unchosen/automatically
     * chosen items. Never overwrite the user's INCLUDED or EXCLUDED decision.
     * Read current indexed rows in this transaction; no oversized SQL IN list.
     * Returns the number of tracks newly made playable by automation.
     */
    @Transaction
    suspend fun reconcileAutomaticallyEvaluated(
        evaluatedUris: Collection<String>,
        eligibleUris: Collection<String>
    ): Int {
        val evaluated = evaluatedUris.toHashSet()
        if (evaluated.isEmpty()) return 0
        val eligible = eligibleUris.toHashSet()
        val now = System.currentTimeMillis()
        val updates = ArrayList<MusicPoolEntry>()
        var newlyAdded = 0
        for (candidate in getPoolCandidates()) {
            if (candidate.track.uri !in evaluated) continue
            val previous = candidate.membershipState
            if (previous == MusicPoolEntry.EXCLUDED || previous == MusicPoolEntry.INCLUDED) continue
            val next = if (candidate.track.uri in eligible) {
                MusicPoolEntry.AUTO_INCLUDED
            } else {
                MusicPoolEntry.AUTO_SKIPPED
            }
            if (previous != next) {
                updates += MusicPoolEntry(candidate.track.uri, next, now)
                if (next == MusicPoolEntry.AUTO_INCLUDED) newlyAdded++
            }
        }
        if (updates.isNotEmpty()) setPoolEntries(updates)
        return newlyAdded
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
