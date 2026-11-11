package com.sysadmindoc.alarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack

@Dao
interface MusicLibraryDao {
    @Query("SELECT COUNT(*) FROM music_tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM music_tracks ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<MusicTrack>

    @Query("SELECT * FROM music_tracks ORDER BY RANDOM() LIMIT 1")
    suspend fun pickRandom(): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE uri NOT IN (:excludedUris) ORDER BY RANDOM() LIMIT 1")
    suspend fun pickRandomExcluding(excludedUris: List<String>): MusicTrack?

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
