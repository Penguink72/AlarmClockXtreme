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
    fun automaticallyDetectedDeviceMusicAppearsInDefaultScopeButRingtonesDoNot() {
        val autoSong = poolItemMediaStore.copy(
            membershipState = MusicPoolEntry.AUTO_INCLUDED,
            track = mediaStoreTrack.copy(title = "Full song")
        )
        val manuallyExcludedTone = poolItemMediaStore.copy(
            membershipState = MusicPoolEntry.EXCLUDED
        )
        val visible = filterPoolTracks(
            tracks = listOf(autoSong, manuallyExcludedTone, poolItemSaf1),
            filter = MusicPoolFilter.INCLUDED,
            scope = MusicPoolSourceScope.Folders,
            search = ""
        )
        assertEquals(listOf(autoSong), visible)
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
}
