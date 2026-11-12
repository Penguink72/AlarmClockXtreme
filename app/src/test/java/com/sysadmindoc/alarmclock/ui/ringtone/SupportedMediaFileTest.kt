package com.sysadmindoc.alarmclock.ui.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedMediaFileTest {
    @Test
    fun acceptsCommonAudioByMimeOrExtension() {
        listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus").forEach { extension ->
            assertEquals(
                MusicMediaKind.AUDIO,
                SupportedMediaFile.classify("song.$extension", "application/octet-stream")
            )
        }
        assertEquals(MusicMediaKind.AUDIO, SupportedMediaFile.classify("track.bin", "audio/mpeg"))
    }

    @Test
    fun acceptsSupportedVideoContainersForAudioTrackInspection() {
        listOf("mp4", "mkv", "webm", "3gp", "m4v", "mov").forEach { extension ->
            assertEquals(
                MusicMediaKind.VIDEO,
                SupportedMediaFile.classify("clip.$extension", "video/mp4")
            )
        }
        assertEquals(
            MusicMediaKind.VIDEO,
            SupportedMediaFile.classify("clip.mp4", "application/octet-stream")
        )
    }

    @Test
    fun ignoresUnrelatedAndConflictingMediaTypes() {
        listOf("jpg", "pdf", "txt", "zip", "srt", "vtt").forEach { extension ->
            assertNull(
                extension,
                SupportedMediaFile.classify(
                    "file.$extension",
                    when (extension) {
                        "jpg" -> "image/jpeg"
                        "pdf" -> "application/pdf"
                        "zip" -> "application/zip"
                        "srt", "vtt", "txt" -> "text/plain"
                        else -> "application/octet-stream"
                    }
                )
            )
        }
        assertNull(SupportedMediaFile.classify("cover.mp3", "image/jpeg"))
        assertNull(SupportedMediaFile.classify("clip.avi", "video/x-msvideo"))
    }

    @Test
    fun preservesUnicodeNamesWhenClassifyingMedia() {
        assertEquals(
            MusicMediaKind.AUDIO,
            SupportedMediaFile.classify("宇多田ヒカル - 光.mp3", "audio/mpeg")
        )
        assertEquals(
            MusicMediaKind.VIDEO,
            SupportedMediaFile.classify("夏の雨 - 回家 Home.mp4", "video/mp4")
        )
    }

    @Test
    fun requiresAConfirmedPlayableAudioTrackForAnyFile() {
        assertEquals(
            false,
            MusicMediaTrackPolicy.isIndexable(false)
        )
        assertEquals(
            false,
            MusicMediaTrackPolicy.isIndexable(null)
        )
        assertEquals(
            true,
            MusicMediaTrackPolicy.isIndexable(true)
        )
    }
}
