package com.sysadmindoc.alarmclock.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["fireId", "playOrder"], unique = true)
    ]
)
data class PlaybackHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fireId: String,
    val startedAt: Long,
    val playOrder: Int,
    val title: String,
    val uri: String
)
