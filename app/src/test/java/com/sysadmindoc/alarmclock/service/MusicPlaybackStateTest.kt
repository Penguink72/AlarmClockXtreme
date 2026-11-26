package com.sysadmindoc.alarmclock.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPlaybackStateTest {
    @After fun clear() {
        MusicPlaybackState.updateTitle(null)
    }

    @Test fun nextTrackReplacesTitleSourceAndFolderTogether() {
        MusicPlaybackState.updateNowPlaying(
            NowPlayingInfo("Song A", NowPlayingSource.MUSIC_LIBRARY, "Download")
        )
        MusicPlaybackState.updateNowPlaying(
            NowPlayingInfo("Song B", NowPlayingSource.PLAYLIST, "RJ01396934")
        )
        assertEquals("Song B", MusicPlaybackState.currentTitle.value)
        assertEquals("Song B", MusicPlaybackState.nowPlaying.value?.title)
        assertEquals(NowPlayingSource.PLAYLIST, MusicPlaybackState.nowPlaying.value?.source)
        assertEquals("RJ01396934", MusicPlaybackState.nowPlaying.value?.folderName)
    }

    @Test fun clearingTheAlarmCannotLeaveAStaleTrack() {
        MusicPlaybackState.updateNowPlaying(
            NowPlayingInfo("Track", NowPlayingSource.PLAYLIST)
        )
        MusicPlaybackState.updateTitle(null)
        assertNull(MusicPlaybackState.currentTitle.value)
        assertNull(MusicPlaybackState.nowPlaying.value)
    }

    @Test fun catalogFolderNameUsesRealFolderIdsNotContentUriIds() {
        assertEquals(
            "RJ01396934",
            NowPlayingInfo.folderNameFrom(setOf("Download/RJ01396934", "RJ01396934"))
        )
        assertEquals("Music", NowPlayingInfo.folderNameFrom(setOf("Music")))
        assertNull(NowPlayingInfo.folderNameFrom(emptySet()))
    }
}
