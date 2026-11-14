package com.sysadmindoc.alarmclock.ui.ringtone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryTreeScannerTest {
    @Test
    fun scansOneThreeAndTenIndependentSources() = runBlocking {
        listOf(1, 3, 10).forEach { sourceCount ->
            val access = FakeTreeAccess()
            val scanner = MusicLibraryTreeScanner(access)
            val results = mutableListOf<MusicFolderScanResult>()
            for (sourceIndex in 0 until sourceCount) {
                val treeUri = "content://provider/tree/$sourceIndex"
                val rootId = "root-$sourceIndex"
                val entries = (0 until 3).map { index ->
                    audio("song-$sourceIndex-$index.mp3", "song-$sourceIndex-$index")
                }
                access.documents[rootId] = entries
                access.playableAudioUris += entries.map {
                    "content://provider/tree/$sourceIndex/document/${it.documentId}"
                }
                results += scanner.scan(source(sourceIndex, treeUri, rootId))
            }

            assertEquals(sourceCount, results.size)
            assertEquals(sourceCount * 3, results.sumOf { it.audioCount })
        }
    }

    @Test
    fun recursivelyIndexesNestedDirectoriesAndListsEachFolderForMultiSelect() = runBlocking {
        val source = source(1, "content://provider/tree/root", "root")
        val access = FakeTreeAccess().apply {
            documents["root"] = listOf(folder("first", "First"))
            documents["first"] = listOf(folder("second", "Second"), audio("a.mp3", "a"))
            documents["second"] = listOf(audio("b.mp3", "b"))
            playableAudioUris += "content://provider/tree/root/document/a"
            playableAudioUris += "content://provider/tree/root/document/b"
        }
        val scanner = MusicLibraryTreeScanner(access)

        val scan = scanner.scan(source)
        val subfolders = scanner.listSubfolders(source)

        assertEquals(setOf("a", "b"), scan.tracks.map { it.fileName.removeSuffix(".mp3") }.toSet())
        assertEquals(listOf("First", "First/Second"), subfolders.map { it.path })
    }

    @Test
    fun recursiveSafScanAutomaticallyQualifiesOnlyLongNonRingtoneAudio() = runBlocking {
        val source = source(1, "content://provider/tree/root", "root")
        val access = FakeTreeAccess().apply {
            documents["root"] = listOf(
                folder("music-folder", "Music"),
                folder("tone-folder", "Ringtones")
            )
            documents["music-folder"] = listOf(
                audio("long.mp3", "primary:Music/long.mp3"),
                audio("short.mp3", "primary:Music/short.mp3"),
                video("video.mp4", "primary:Music/video.mp4")
            )
            documents["tone-folder"] = listOf(
                audio("factory.ogg", "primary:Ringtones/factory.ogg")
            )
            val uris = (documents["music-folder"].orEmpty() +
                documents["tone-folder"].orEmpty()).map { documentUri(source.treeUri, it.documentId) }
            playableAudioUris.addAll(uris)
            durations[documentUri(source.treeUri, "primary:Music/long.mp3")] = 61_000L
            durations[documentUri(source.treeUri, "primary:Music/short.mp3")] = 59_000L
            durations[documentUri(source.treeUri, "primary:Music/video.mp4")] = 180_000L
            durations[documentUri(source.treeUri, "primary:Ringtones/factory.ogg")] = 180_000L
        }
        val result = MusicLibraryTreeScanner(access).scan(source)

        assertEquals(4, result.tracks.size)
        assertEquals(4, result.evaluatedUris.size)
        assertEquals(setOf(
            access.documentUri(source.treeUri, "primary:Music/long.mp3"),
            access.documentUri(source.treeUri, "primary:Music/video.mp4")
        ), result.automaticallyEligibleUris)
    }

    @Test
    fun filtersMixedMediaAndSkipsVideoWithoutAPlayableAudioTrack() = runBlocking {
        val source = source(1, "content://provider/tree/root", "root")
        val access = FakeTreeAccess().apply {
            documents["root"] = listOf(
                audio("周杰倫 - 晴天.mp3", "audio"),
                audio("unsupported-codec.wav", "audio-without-decoder"),
                video("夏の雨 - 回家 Home.mp4", "video-with-audio"),
                video("silent-video.mkv", "video-without-audio"),
                MusicDocumentEntry("cover", "cover.jpg", "image/jpeg", false),
                MusicDocumentEntry("notes", "notes.txt", "text/plain", false),
                MusicDocumentEntry("archive", "songs.zip", "application/zip", false),
                MusicDocumentEntry("subtitle", "clip.srt", "application/x-subrip", false)
            )
            playableAudioUris += "content://provider/tree/root/document/audio"
            playableAudioUris += "content://provider/tree/root/document/video-with-audio"
        }

        val result = MusicLibraryTreeScanner(access).scan(source)

        assertEquals(1, result.audioCount)
        assertEquals(1, result.videoCount)
        assertEquals(1, result.rejectedVideos)
        assertEquals(1, result.rejectedAudio)
        assertEquals(
            setOf("周杰倫 - 晴天", "夏の雨 - 回家 Home"),
            result.tracks.map { it.title }.toSet()
        )
    }

    @Test
    fun propagatesProbeFailureInsteadOfTreatingTheTrackAsUnplayable() {
        val source = source(1, "content://provider/tree/root", "root")
        val access = FakeTreeAccess().apply {
            documents["root"] = listOf(audio("possibly-readable.mp3", "track"))
            probeFailure = java.io.IOException("Temporary provider failure")
        }

        val failure = runCatching {
            runBlocking { MusicLibraryTreeScanner(access).scan(source) }
        }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
    }

    @Test
    fun handlesThreeThousandOneTracksAndDeduplicatesRepeatedDocumentUris() = runBlocking {
        val source = source(1, "content://provider/tree/root", "root")
        val entries = (0 until 3_001).map { index ->
            audio("Track $index.mp3", "track-$index")
        }.toMutableList()
        entries += audio("Duplicate.mp3", "track-0")
        val access = FakeTreeAccess().apply {
            documents["root"] = entries
            playableAudioUris += entries.map { "content://provider/tree/root/document/${it.documentId}" }
        }

        val result = MusicLibraryTreeScanner(access).scan(source)

        assertEquals(3_001, result.tracks.size)
        assertEquals(3_001, result.audioCount)
    }

    @Test
    fun retainsJapaneseAndEnglishMixedTrackNames() = runBlocking {
        val source = source(1, "content://provider/tree/root", "root")
        val names = listOf(
            "周杰倫 - 晴天.mp3",
            "宇多田ヒカル - 光.m4a",
            "Queen - Bohemian Rhapsody.flac",
            "テスト_測試（Live）#01.wav"
        )
        val access = FakeTreeAccess().apply {
            documents["root"] = names.mapIndexed { index, name ->
                audio(name, "track-$index").also {
                    playableAudioUris += "content://provider/tree/root/document/track-$index"
                }
            }
        }

        val result = MusicLibraryTreeScanner(access).scan(source)

        assertEquals(names.map { it.substringBeforeLast('.') }.toSet(), result.tracks.map { it.title }.toSet())
        assertTrue(result.tracks.all { it.uri.startsWith("content://provider/tree/root/") })
    }

    private fun source(index: Int, treeUri: String, documentId: String) = MusicLibrarySource(
        id = "$treeUri|$documentId",
        treeUri = treeUri,
        documentId = documentId,
        name = "Folder $index"
    )

    private fun folder(id: String, name: String) =
        MusicDocumentEntry(id, name, "vnd.android.document/directory", true)

    private fun audio(name: String, id: String) =
        MusicDocumentEntry(id, name, "audio/mpeg", false)

    private fun video(name: String, id: String) =
        MusicDocumentEntry(id, name, "video/mp4", false)

    private class FakeTreeAccess : MusicLibraryTreeAccess {
        val documents = mutableMapOf<String, List<MusicDocumentEntry>>()
        val playableAudioUris = mutableSetOf<String>()
        val durations = mutableMapOf<String, Long>()
        var probeFailure: Exception? = null

        override suspend fun children(
            treeUri: String,
            documentId: String
        ): List<MusicDocumentEntry> = documents[documentId].orEmpty()

        override fun documentUri(treeUri: String, documentId: String): String =
            "$treeUri/document/$documentId"

        override suspend fun hasPlayableAudioTrack(uri: String): Boolean =
            probeFailure?.let { throw it } ?: (uri in playableAudioUris)

        override suspend fun durationMs(uri: String): Long? = durations[uri]
    }
}
