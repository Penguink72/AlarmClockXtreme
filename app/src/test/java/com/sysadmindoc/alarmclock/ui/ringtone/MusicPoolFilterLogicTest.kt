package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPoolFilterLogicTest {

    private val safTrack1 = MusicTrack(
        uri = "content://saf/song1.mp3",
        title = "Song One",
        source = MusicLibraryIndexer.SOURCE_SAF,
        fileName = "song1.mp3"
    )
    private val safVideoTrack = MusicTrack(
        uri = "content://saf/video.mp4",
        title = "Music Video",
        source = MusicLibraryIndexer.SOURCE_SAF_VIDEO,
        fileName = "video.mp4"
    )
    private val mediaStoreTrack = MusicTrack(
        uri = "content://media/internal/audio/ringtone.ogg",
        title = "Default Tone",
        source = MusicLibraryIndexer.SOURCE_MEDIA_STORE,
        fileName = "ringtone.ogg"
    )

    private val poolItemSaf1 = MusicPoolUiTrack(
        track = safTrack1,
        membershipState = null,
        sourceName = "Music Folder",
        sourceIds = setOf("source-folder-1")
    )
    private val poolItemVideo = MusicPoolUiTrack(
        track = safVideoTrack,
        membershipState = MusicPoolEntry.INCLUDED,
        sourceName = "Music Folder",
        sourceIds = setOf("source-folder-1")
    )
    private val poolItemMediaStore = MusicPoolUiTrack(
        track = mediaStoreTrack,
        membershipState = null,
        sourceName = "Device Media",
        sourceIds = emptySet()
    )

    @Test
    fun isVideoAudioCorrectlyIdentifiesSafVideoTracks() {
        assertFalse(safTrack1.isVideoAudio())
        assertTrue(safVideoTrack.isVideoAudio())
        assertFalse(mediaStoreTrack.isVideoAudio())
    }

    @Test
    fun defaultFoldersScopeExcludesMediaStoreSystemRingtones() {
        val tracks = listOf(poolItemSaf1, poolItemVideo, poolItemMediaStore)

        val visibleInFoldersScope = filterPoolTracks(
            tracks = tracks,
            filter = MusicPoolFilter.ALL_CANDIDATES,
            scope = MusicPoolSourceScope.Folders,
            search = ""
        )
        assertEquals(listOf(poolItemSaf1, poolItemVideo), visibleInFoldersScope)
        assertFalse(visibleInFoldersScope.any { it.track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE })

        val visibleInDeviceScope = filterPoolTracks(
            tracks = tracks,
            filter = MusicPoolFilter.ALL_CANDIDATES,
            scope = MusicPoolSourceScope.DeviceMedia,
            search = ""
        )
        assertEquals(listOf(poolItemMediaStore), visibleInDeviceScope)

        val visibleInFolder1Scope = filterPoolTracks(
            tracks = tracks,
            filter = MusicPoolFilter.ALL_CANDIDATES,
            scope = MusicPoolSourceScope.Folder("source-folder-1"),
            search = ""
        )
        assertEquals(listOf(poolItemSaf1, poolItemVideo), visibleInFolder1Scope)
    }

    @Test
    fun batchAddFolderIncludesOnlyUnexcludedCandidatesOfThatFolder() {
        val excludedItem = poolItemSaf1.copy(membershipState = MusicPoolEntry.EXCLUDED)
        val alreadyIncludedItem = poolItemVideo.copy(membershipState = MusicPoolEntry.INCLUDED)
        val candidateItem = MusicPoolUiTrack(
            track = MusicTrack(
                uri = "content://saf/song2.flac",
                title = "Song Two",
                source = MusicLibraryIndexer.SOURCE_SAF,
                fileName = "song2.flac"
            ),
            membershipState = null,
            sourceName = "Music Folder",
            sourceIds = setOf("source-folder-1")
        )
        val otherFolderCandidate = MusicPoolUiTrack(
            track = MusicTrack(
                uri = "content://saf/other.mp3",
                title = "Other",
                source = MusicLibraryIndexer.SOURCE_SAF
            ),
            membershipState = null,
            sourceName = "Other Folder",
            sourceIds = setOf("source-folder-2")
        )

        val allTracks = listOf(excludedItem, alreadyIncludedItem, candidateItem, otherFolderCandidate, poolItemMediaStore)
        val urisToInclude = folderCandidatesToInclude(allTracks, "source-folder-1")

        assertEquals(listOf("content://saf/song2.flac"), urisToInclude)
        assertFalse(urisToInclude.contains(excludedItem.track.uri))
        assertFalse(urisToInclude.contains(poolItemMediaStore.track.uri))
        assertFalse(urisToInclude.contains(otherFolderCandidate.track.uri))
    }

    @Test
    fun catalogSearchMatchesMixedLanguageTitleFileMetadataAndSource() {
        val chinese = poolItemSaf1.copy(
            track = safTrack1.copy(
                title = "周杰倫 - 晴天",
                fileName = "周杰倫 - 晴天.mp3",
                metadataTitle = "晴天"
            ),
            sourceName = "手機音樂"
        )
        val japanese = poolItemVideo.copy(
            track = safVideoTrack.copy(
                title = "夏の雨 - 回家 Home",
                fileName = "夏の雨 - 回家 Home.mp4",
                metadataTitle = "夏の雨"
            )
        )
        val english = poolItemMediaStore.copy(
            track = mediaStoreTrack.copy(
                title = "Bohemian Rhapsody",
                fileName = "Queen - Bohemian Rhapsody.mp3",
                metadataTitle = "Bohemian Rhapsody"
            )
        )
        val tracks = listOf(chinese, japanese, english)

        assertEquals(
            listOf(chinese),
            filterLibraryCatalog(tracks, MusicPoolFilter.ALL_CANDIDATES, "晴天")
        )
        assertEquals(
            listOf(japanese),
            filterLibraryCatalog(tracks, MusicPoolFilter.ALL_CANDIDATES, "夏の雨")
        )
        assertEquals(
            listOf(english),
            filterLibraryCatalog(tracks, MusicPoolFilter.ALL_CANDIDATES, "Queen")
        )
        assertEquals(
            listOf(chinese),
            filterLibraryCatalog(tracks, MusicPoolFilter.ALL_CANDIDATES, "手機音樂")
        )
    }

    @Test
    fun catalogMembershipFiltersDoNotRestoreExcludedTracks() {
        val excluded = poolItemSaf1.copy(membershipState = MusicPoolEntry.EXCLUDED)
        val included = poolItemVideo.copy(membershipState = MusicPoolEntry.INCLUDED)

        assertEquals(
            listOf(excluded),
            filterLibraryCatalog(listOf(excluded, included), MusicPoolFilter.EXCLUDED, "")
        )
        assertEquals(
            listOf(included),
            filterLibraryCatalog(listOf(excluded, included), MusicPoolFilter.INCLUDED, "")
        )
    }

    @Test
    fun selectAllThenAddLeavesExcludedTracksExcluded() {
        val candidate = poolItemSaf1
        val excluded = poolItemVideo.copy(membershipState = MusicPoolEntry.EXCLUDED)
        val included = poolItemMediaStore.copy(membershipState = MusicPoolEntry.INCLUDED)
        val visible = listOf(candidate, excluded, included)
        val selected = visible.mapTo(linkedSetOf()) { it.track.uri }

        assertEquals(
            setOf(candidate.track.uri),
            catalogUrisForMembershipUpdate(
                visible,
                selected,
                MusicPoolFilter.ALL_CANDIDATES,
                included = true
            )
        )
        assertEquals(
            setOf(excluded.track.uri),
            catalogUrisForMembershipUpdate(
                listOf(excluded),
                setOf(excluded.track.uri),
                MusicPoolFilter.EXCLUDED,
                included = true
            )
        )
    }

    @Test
    fun catalogCanFilterVideoCandidatesAndFolderScopeWithoutCrossingTabs() {
        val tracks = listOf(poolItemSaf1, poolItemVideo, poolItemMediaStore)

        assertEquals(
            listOf(poolItemVideo),
            filterLibraryCatalog(
                tracks = tracks,
                filter = MusicPoolFilter.ALL_CANDIDATES,
                search = "",
                sourceScope = MusicPoolSourceScope.Folder("source-folder-1"),
                videoOnly = true
            )
        )
        assertEquals(
            listOf(poolItemMediaStore),
            filterLibraryCatalog(
                tracks = tracks,
                filter = MusicPoolFilter.ALL_CANDIDATES,
                search = "",
                sourceScope = MusicPoolSourceScope.DeviceMedia
            )
        )
    }

    @Test
    fun catalogSearchHandlesMoreThanThreeThousandTracks() {
        val tracks = (0 until 3_001).map { index ->
            MusicPoolUiTrack(
                track = MusicTrack(
                    uri = "content://media/audio/$index",
                    title = if (index == 3_000) "夏の雨 - Home" else "Track $index",
                    source = MusicLibraryIndexer.SOURCE_MEDIA_STORE,
                    fileName = "track-$index.mp3"
                ),
                membershipState = null,
                sourceName = "Device Media"
            )
        }

        assertEquals(
            listOf("content://media/audio/3000"),
            filterLibraryCatalog(
                tracks = tracks,
                filter = MusicPoolFilter.ALL_CANDIDATES,
                search = "夏の雨"
            ).map { it.track.uri }
        )
    }
}
