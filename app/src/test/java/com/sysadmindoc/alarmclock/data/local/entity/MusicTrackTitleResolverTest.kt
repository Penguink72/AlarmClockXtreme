package com.sysadmindoc.alarmclock.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class MusicTrackTitleResolverTest {
    @Test
    fun preservesMixedLanguageFilenames() {
        val names = listOf(
            "周杰倫 - 晴天.mp3",
            "宇多田ヒカル - 光.mp3",
            "Queen - Bohemian Rhapsody.mp3",
            "夏の雨 - 回家 Home.mp3",
            "テスト_測試（Live）#01.mp3"
        )

        names.forEach { fileName ->
            assertEquals(
                fileName.removeSuffix(".mp3"),
                MusicTrackTitleResolver.resolve(fileName, "")
            )
        }
    }

    @Test
    fun removesSupportedVideoContainerExtensionFromDisplayTitle() {
        assertEquals(
            "夏の雨 - 回家 Home",
            MusicTrackTitleResolver.resolve("夏の雨 - 回家 Home.mkv", "")
        )
    }

    @Test
    fun keepsCorrectUtf8AndUtf16MetadataWithoutRewritingSourceValues() {
        val expected = "夏の雨 - 回家 Home"
        val utf8Tag = String(expected.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        val utf16Tag = String(expected.toByteArray(StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE)

        assertEquals(expected, MusicTrackTitleResolver.resolve("fallback.mp3", utf8Tag))
        assertEquals(expected, MusicTrackTitleResolver.resolve("fallback.mp3", utf16Tag))
    }

    @Test
    fun rejectsRecognizableMojibakeMetadataInFavorOfTheOriginalFilename() {
        val fileName = "宇多田ヒカル - 光.mp3"
        val mojibake = "å¤\u008Fã\u0081®é\u009B¨"

        assertEquals(
            "宇多田ヒカル - 光",
            MusicTrackTitleResolver.resolve(fileName, mojibake)
        )
    }

    @Test
    fun missingOrCorruptMetadataNeverReplacesTheFilenameFallback() {
        val fileName = "テスト_測試（Live）#01.mp3"

        assertEquals(
            "テスト_測試（Live）#01",
            MusicTrackTitleResolver.resolve(fileName, "\uFFFD bad tag")
        )
        assertEquals(
            "テスト_測試（Live）#01",
            MusicTrackTitleResolver.resolve(fileName, "   ")
        )
    }

    @Test
    fun metadataAndHistoryDisplaySnapshotStayIndependentOfLaterIndexValues() {
        val track = MusicTrack(
            uri = "content://music/1",
            title = "晴天",
            source = "MEDIA_STORE",
            fileName = "周杰倫 - 晴天.mp3",
            metadataTitle = "晴天"
        )

        assertEquals("晴天", track.displayTitle)
        val historyTitle = track.displayTitle
        val refreshedTrack = track.copy(title = "Different", metadataTitle = "Different")
        assertEquals("晴天", historyTitle)
        assertEquals("Different", refreshedTrack.displayTitle)
        assertEquals("周杰倫 - 晴天.mp3", track.fileName)
    }
}
