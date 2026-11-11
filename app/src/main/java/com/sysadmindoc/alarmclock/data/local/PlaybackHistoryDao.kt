package com.sysadmindoc.alarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT COUNT(*) FROM playback_history WHERE fireId = :fireId")
    suspend fun countForFire(fireId: String): Int

    @Insert
    suspend fun insert(entry: PlaybackHistoryEntry)

    @Transaction
    suspend fun appendStarted(
        fireId: String,
        startedAt: Long,
        title: String,
        uri: String
    ) {
        insert(
            PlaybackHistoryEntry(
                fireId = fireId,
                startedAt = startedAt,
                playOrder = countForFire(fireId) + 1,
                title = title,
                uri = uri
            )
        )
    }

    @Query(
        """
        SELECT * FROM playback_history
        WHERE startedAt >= :fromMillis AND startedAt < :toMillis
        ORDER BY startedAt ASC, playOrder ASC, id ASC
        """
    )
    suspend fun getForDate(fromMillis: Long, toMillis: Long): List<PlaybackHistoryEntry>
}
