package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTrackIndexReconcilerTest {
    @Test
    fun mergesOneThreeAndTenSourcesWithoutDuplicateUris() {
        listOf(1, 3, 10).forEach { sourceCount ->
            val tracks = tracks(3_001)
            val sources = (0 until sourceCount).map { sourceIndex ->
                val assigned = tracks.filterIndexed { index, _ ->
                    index % sourceCount == sourceIndex
                }.mapTo(LinkedHashSet(), MusicTrack::uri)
                source(sourceIndex, assigned)
            }
            val duplicateTracks = tracks + tracks.take(100)

            val merged = MusicTrackIndexReconciler.afterScan(
                existing = emptyList(),
                discovered = duplicateTracks,
                sources = sources,
                mediaStoreScanned = true
            )

            assertEquals("source count $sourceCount", tracks.size, merged.size)
        }
    }

    @Test
    fun overlappingAndNestedSourcesKeepSharedTracksUntilLastSourceIsRemoved() {
        val shared = track("content://tree/shared", "Shared")
        val onlyParent = track("content://tree/parent", "Parent")
        val onlyChild = track("content://tree/child", "Child")
        val parent = source(1, setOf(shared.uri, onlyParent.uri))
        val child = source(2, setOf(shared.uri, onlyChild.uri))

        val afterRemovingParent = MusicTrackIndexReconciler.afterSourceRemoval(
            listOf(shared, onlyParent, onlyChild),
            listOf(child)
        )

        assertEquals(setOf(shared.uri, onlyChild.uri), afterRemovingParent.map { it.uri }.toSet())
    }

    @Test
    fun preservesTracksWhenAnyRemainingSourceHasNotBeenIndexedYet() {
        val oldTrack = track("content://tree/old", "Old source track")
        val unindexed = source(2, emptySet()).copy(indexed = false)

        val retained = MusicTrackIndexReconciler.afterSourceRemoval(
            listOf(oldTrack),
            listOf(unindexed)
        )

        assertEquals(listOf(oldTrack), retained)
    }

    @Test
    fun failedSourceRetainsItsIndexedTracksWhileSuccessfulSourcesRefresh() {
        val retained = track("content://folder/retained", "周杰倫 - 晴天")
        val removedOnRescan = track("content://folder/gone", "Gone")
        val newTrack = track("content://folder/new", "宇多田ヒカル - 光")
        val failedSource = source(1, setOf(retained.uri)).copy(
            indexed = true,
            status = MusicSourceStatus.ERROR,
            error = "SD card unavailable"
        )
        val refreshedSource = source(2, setOf(newTrack.uri)).copy(indexed = true)

        val result = MusicTrackIndexReconciler.afterScan(
            existing = listOf(retained, removedOnRescan),
            discovered = listOf(newTrack),
            sources = listOf(failedSource, refreshedSource),
            mediaStoreScanned = true
        )

        assertEquals(setOf(retained.uri, newTrack.uri), result.map { it.uri }.toSet())
        assertTrue(result.none { it.uri == removedOnRescan.uri })
    }

    @Test
    fun keepsMediaStoreRowsWhenStoragePermissionIsUnavailable() {
        val mediaTrack = track("content://media/1", "Queen - Bohemian Rhapsody")
            .copy(source = MusicLibraryIndexer.SOURCE_MEDIA_STORE)

        val result = MusicTrackIndexReconciler.afterScan(
            existing = listOf(mediaTrack),
            discovered = emptyList(),
            sources = emptyList(),
            mediaStoreScanned = false
        )

        assertEquals(listOf(mediaTrack), result)
    }

    private fun tracks(count: Int): List<MusicTrack> =
        (0 until count).map { track("content://folder/$it", "Track $it") }

    private fun track(uri: String, title: String): MusicTrack = MusicTrack(
        uri = uri,
        title = title,
        source = MusicLibraryIndexer.SOURCE_SAF,
        fileName = "$title.mp3"
    )

    private fun source(index: Int, trackUris: Set<String>) = MusicLibrarySource(
        id = "tree-$index",
        treeUri = "content://provider/tree/$index",
        documentId = "folder-$index",
        name = "Folder $index",
        status = MusicSourceStatus.READY,
        indexed = true,
        audioCount = trackUris.size,
        trackUris = trackUris
    )
}
