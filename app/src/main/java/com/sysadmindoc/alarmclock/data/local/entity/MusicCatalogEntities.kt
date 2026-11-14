package com.sysadmindoc.alarmclock.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_catalog_metadata",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["artist"]),
        Index(value = ["folderName"]),
        Index(value = ["audioProbeStatus"])
    ]
)
data class MediaCatalogMetadataEntity(
    @PrimaryKey val uri: String,
    val providerKind: String,
    val mediaType: String,
    val volumeName: String = "",
    val relativePath: String = "",
    val folderName: String = "",
    val artist: String = "",
    val album: String = "",
    val mimeType: String = "",
    val container: String = "",
    val durationMs: Long? = null,
    val audioProbeStatus: String = "PLAYABLE",
    val decoderStatus: String = "PLAYABLE",
    val isSystemTone: Boolean = false,
    val permissionAvailability: String = "GRANTED",
    val failureReason: String = "",
    val lastSeenAt: Long = 0L
)

@Entity(
    tableName = "music_playlists",
    indices = [
        Index(value = ["kind"])
    ]
)
data class MusicPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val kind: String,
    val ownerAlarmId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "music_playlist_items",
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["trackUri"])
    ]
)
data class MusicPlaylistItemEntity(
    @PrimaryKey val playlistItemId: String,
    val playlistId: String,
    val trackUri: String,
    val position: Int,
    val addedAt: Long = 0L
)

@Entity(
    tableName = "alarm_music_settings",
    indices = [
        Index(value = ["playlistId"])
    ]
)
data class AlarmMusicSettingsEntity(
    @PrimaryKey val alarmId: Long,
    val playlistId: String? = null,
    val playbackStrategy: String = "RANDOM_WITH_REPEATS",
    val continuousUntilDismiss: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "alarm_music_facet_filters",
    primaryKeys = ["alarmId", "kind", "facetId"],
    indices = [
        Index(value = ["alarmId"])
    ]
)
data class AlarmMusicFacetFilterEntity(
    val alarmId: Long,
    val kind: String,
    val facetId: String
) {
    companion object {
        const val KIND_ARTIST = "ARTIST"
        const val KIND_FOLDER = "FOLDER"
    }
}
