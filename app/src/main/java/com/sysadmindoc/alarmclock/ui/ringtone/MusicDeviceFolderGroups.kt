package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import java.util.Locale

/**
 * A browsing projection, never a file-system access claim. Indexing comes from
 * MediaStore and scoped SAF; folder actions change pool membership, not files.
 */
internal data class DeviceMediaFolderGroup(
    val relativePath: String,
    val trackUris: Set<String>,
    val audioCount: Int,
    val videoCount: Int,
    val includedCount: Int,
    val excludedCount: Int
) {
    val trackCount: Int get() = trackUris.size
}

internal fun normalizeMusicFolderPath(path: String): String =
    path.trim().replace('\\', '/').trim('/').lowercase(Locale.ROOT)

/** Do not materialize thousands of individual file rows just to browse folders. */
internal fun groupMediaStoreFolders(
    tracks: List<MusicPoolUiTrack>,
    metadataByUri: Map<String, MusicCatalogTrackMetadata>
): List<DeviceMediaFolderGroup> =
    tracks.asSequence()
        .filter { MusicLibraryIndexer.isMediaStoreSource(it.track.source) }
        .groupBy { item ->
            normalizeMusicFolderPath(metadataByUri[item.track.uri]?.folderPath.orEmpty())
        }
        .map { (path, rows) ->
            DeviceMediaFolderGroup(
                relativePath = path,
                trackUris = rows.mapTo(LinkedHashSet()) { it.track.uri },
                audioCount = rows.count { !it.track.isVideoAudio() },
                videoCount = rows.count { it.track.isVideoAudio() },
                includedCount = rows.count { it.membershipState == MusicPoolEntry.INCLUDED },
                excludedCount = rows.count { it.membershipState == MusicPoolEntry.EXCLUDED }
            )
        }
        .sortedWith(compareBy<DeviceMediaFolderGroup> { it.relativePath.isEmpty() }
            .thenBy { it.relativePath })
