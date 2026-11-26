package com.sysadmindoc.alarmclock.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTitleResolverTest {
    @Test fun removesCommonAudioExtensionsWithoutDamagingTrackTitle() {
        assertEquals("Quiet Clock", NowPlayingTitleResolver.normalized("Quiet Clock.mp3"))
        assertEquals("夜行鳥", NowPlayingTitleResolver.normalized("音樂/夜行鳥.FLAC"))
        assertEquals("A.Z 01", NowPlayingTitleResolver.normalized("A.Z 01.m4a"))
    }

    @Test fun avoidsPresentingMediaStoreIdsAsSongTitles() {
        assertNull(NowPlayingTitleResolver.normalized("12345"))
        assertNull(NowPlayingTitleResolver.normalized(" "))
        assertNull(NowPlayingTitleResolver.normalized(null))
    }

    @Test fun leavesPlainTitlesAndNonAudioNamesUntouched() {
        assertEquals("青鳥", NowPlayingTitleResolver.normalized(" 青鳥 "))
        assertEquals("RadioStation", NowPlayingTitleResolver.normalized("RadioStation"))
    }
}
