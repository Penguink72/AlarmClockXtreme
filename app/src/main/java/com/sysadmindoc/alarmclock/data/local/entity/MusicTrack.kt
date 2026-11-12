package com.sysadmindoc.alarmclock.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_tracks")
data class MusicTrack(
    @PrimaryKey val uri: String,
    val title: String,
    val source: String,
    val fileName: String = "",
    val metadataTitle: String = ""
)

val MusicTrack.displayTitle: String
    get() = MusicTrackTitleResolver.resolve(
        fileName = fileName,
        metadataTitle = metadataTitle,
        legacyTitle = title
    )
