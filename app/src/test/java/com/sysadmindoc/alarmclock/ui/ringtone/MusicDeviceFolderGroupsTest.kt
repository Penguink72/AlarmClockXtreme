package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import org.junit.Assert.*
import org.junit.Test

class MusicDeviceFolderGroupsTest {
    private fun row(id: String, source: String, member: String? = null) =
        MusicPoolUiTrack(
            MusicTrack(id, "Title", source), member, source
        )

    @Test fun groupsAudioAndVideoByActualRelativePathWithoutShowingIndividualFiles() {
        val rows = listOf(
            row("content://a", MusicLibraryIndexer.SOURCE_MEDIA_STORE, MusicPoolEntry.INCLUDED),
            row("content://v", MusicLibraryIndexer.SOURCE_MEDIA_STORE_VIDEO),
            row("content://other", MusicLibraryIndexer.SOURCE_MEDIA_STORE, MusicPoolEntry.EXCLUDED),
            row("content://saf", MusicLibraryIndexer.SOURCE_SAF)
        )
        val metadata = mapOf(
            "content://a" to MusicCatalogTrackMetadata("content://a", folderPath="Music/Albums/"),
            "content://v" to MusicCatalogTrackMetadata("content://v", folderPath="Music/Albums"),
            "content://other" to MusicCatalogTrackMetadata("content://other", folderPath="Recordings"),
            "content://saf" to MusicCatalogTrackMetadata("content://saf", folderPath="Music/Albums")
        )
        val grouped = groupMediaStoreFolders(rows, metadata)
        assertEquals(2, grouped.size)
        assertEquals("Music/Albums", grouped[0].relativePath)
        assertEquals(setOf("content://a","content://v"), grouped[0].trackUris)
        assertEquals(1, grouped[0].audioCount)
        assertEquals(1, grouped[0].videoCount)
        assertEquals(1, grouped[0].includedCount)
        assertEquals(1, grouped[1].excludedCount)
        assertFalse(grouped.flatMap { it.trackUris }.contains("content://saf"))
    }

    @Test fun exactFolderFilterNeverSweepsSiblingOrSafIntoSelectedFolder() {
        val rows = listOf(
            row("content://a", MusicLibraryIndexer.SOURCE_MEDIA_STORE),
            row("content://b", MusicLibraryIndexer.SOURCE_MEDIA_STORE),
            row("content://c", MusicLibraryIndexer.SOURCE_SAF)
        )
        val metadata = mapOf(
            "content://a" to MusicCatalogTrackMetadata("content://a", folderPath="Music/A"),
            "content://b" to MusicCatalogTrackMetadata("content://b", folderPath="Music/AB"),
            "content://c" to MusicCatalogTrackMetadata("content://c", folderPath="Music/A")
        )
        val result = filterLibraryCatalog(
            tracks=rows,
            filter=MusicPoolFilter.ALL_CANDIDATES,
            search="",
            sourceScope=MusicPoolSourceScope.DeviceFolder("Music/A"),
            metadataByUri=metadata
        )
        assertEquals(listOf("content://a"),result.map { it.track.uri })
    }

    @Test fun emptyRelativePathGetsItsOwnBucket() {
        val rows=listOf(row("content://unknown",MusicLibraryIndexer.SOURCE_MEDIA_STORE))
        val groups=groupMediaStoreFolders(rows,emptyMap())
        assertEquals("",groups.single().relativePath)
        assertEquals(1,groups.single().trackCount)
    }
}
