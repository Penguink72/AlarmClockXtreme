package com.sysadmindoc.alarmclock.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_pool_members",
    indices = [Index(value = ["state"])]
)
data class MusicPoolEntry(
    @PrimaryKey val uri: String,
    val state: String,
    val updatedAt: Long
) {
    companion object {
        const val INCLUDED = "INCLUDED"
        const val EXCLUDED = "EXCLUDED"
    }
}
