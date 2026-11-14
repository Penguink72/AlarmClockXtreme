package com.sysadmindoc.alarmclock.ui.ringtone

import android.provider.DocumentsContract
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrackTitleResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque

internal data class MusicDocumentEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean
)

internal interface MusicLibraryTreeAccess {
    suspend fun children(treeUri: String, documentId: String): List<MusicDocumentEntry>
    fun documentUri(treeUri: String, documentId: String): String
    suspend fun hasPlayableAudioTrack(uri: String): Boolean

    suspend fun probeAudioTrack(
        uri: String,
        displayName: String = "",
        mimeType: String? = null
    ): AudioProbeResult {
        val container = SupportedMediaFile.containerFor(displayName, mimeType)
        val playable = hasPlayableAudioTrack(uri)
        return if (playable) {
            AudioProbeResult(
                availability = MediaAvailability.PLAYABLE,
                container = container,
                seekSupported = container != "flv"
            )
        } else {
            AudioProbeResult(
                availability = MediaAvailability.NO_AUDIO_TRACK,
                container = container,
                seekSupported = container != "flv",
                failureReason = "NO_AUDIO_TRACK"
            )
        }
    }

    /** Unknown duration returns null and cannot be automatically classified as full song. */
    suspend fun durationMs(uri: String): Long? = null
}

internal data class MusicFolderScanResult(
    val tracks: List<MusicTrack>,
    val audioCount: Int,
    val videoCount: Int,
    val rejectedAudio: Int,
    val rejectedVideos: Int,
    val evaluatedUris: Set<String> = emptySet(),
    val automaticallyEligibleUris: Set<String> = emptySet(),
    val rejectedNoAudioCount: Int = 0,
    val rejectedUnsupportedCodecCount: Int = 0,
    val unreadableCount: Int = 0,
    val metadataByUri: Map<String, MusicCatalogTrackMetadata> = emptyMap()
)

internal class MusicLibraryTreeScanner(
    private val treeAccess: MusicLibraryTreeAccess
) {
    suspend fun scan(
        source: MusicLibrarySource,
        minimumSeconds: Int = MusicAutoPoolPolicy.DEFAULT_MIN_SECONDS
    ): MusicFolderScanResult {
        val pendingDirectories = ArrayDeque<String>().apply { add(source.documentId) }
        val visitedDirectories = HashSet<String>()
        val tracks = LinkedHashMap<String, MusicTrack>()
        val metadataByUri = LinkedHashMap<String, MusicCatalogTrackMetadata>()
        var audioCount = 0
        var videoCount = 0
        var rejectedAudio = 0
        var rejectedVideos = 0
        var rejectedNoAudioCount = 0
        var rejectedUnsupportedCodecCount = 0
        var unreadableCount = 0
        val evaluatedUris = LinkedHashSet<String>()
        val automaticallyEligibleUris = LinkedHashSet<String>()

        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directoryId = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directoryId)) continue
            for (entry in treeAccess.children(source.treeUri, directoryId)) {
                currentCoroutineContext().ensureActive()
                if (entry.isDirectory) {
                    pendingDirectories.addLast(entry.documentId)
                    continue
                }
                val kind = SupportedMediaFile.classify(entry.displayName, entry.mimeType)
                    ?: continue
                val uri = treeAccess.documentUri(source.treeUri, entry.documentId)
                val probe = treeAccess.probeAudioTrack(uri, entry.displayName, entry.mimeType)
                if (!MusicMediaTrackPolicy.isIndexable(probe.availability)) {
                    if (kind == MusicMediaKind.VIDEO) rejectedVideos++ else rejectedAudio++
                    when (probe.availability) {
                        MediaAvailability.NO_AUDIO_TRACK -> rejectedNoAudioCount++
                        MediaAvailability.CODEC_UNSUPPORTED -> rejectedUnsupportedCodecCount++
                        MediaAvailability.UNREADABLE,
                        MediaAvailability.PERMISSION_MISSING -> unreadableCount++
                        else -> Unit
                    }
                    continue
                }
                val track = MusicTrack(
                    uri = uri,
                    title = MusicTrackTitleResolver.resolve(entry.displayName, ""),
                    source = if (kind == MusicMediaKind.VIDEO) {
                        MusicLibraryIndexer.SOURCE_SAF_VIDEO
                    } else {
                        MusicLibraryIndexer.SOURCE_SAF
                    },
                    fileName = entry.displayName.ifBlank { entry.documentId.substringAfterLast('/') },
                    metadataTitle = ""
                )
                if (tracks.putIfAbsent(uri, track) == null) {
                    val duration = probe.durationMs ?: try {
                        treeAccess.durationMs(uri)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    val directoryPath = entry.documentId.substringAfter(':', "")
                        .substringBeforeLast('/', "")
                        .ifBlank { source.name }
                    val isSystemTone = MusicAutoPoolPolicy.isSystemSound(directoryPath)
                    if (MusicAutoPoolPolicy.hasDecisiveMetadata(duration, directoryPath)) {
                        evaluatedUris += uri
                    }
                    if (MusicAutoPoolPolicy.isEligible(
                            durationMs = duration,
                            relativePath = directoryPath,
                            minimumSeconds = minimumSeconds
                        )
                    ) {
                        automaticallyEligibleUris += uri
                    }
                    val container = probe.container.ifBlank {
                        SupportedMediaFile.containerFor(entry.displayName, entry.mimeType)
                    }
                    metadataByUri[uri] = MusicCatalogTrackMetadata(
                        uri = uri,
                        artist = "",
                        album = "",
                        folderPath = directoryPath,
                        durationMillis = duration,
                        mediaType = if (kind == MusicMediaKind.VIDEO) {
                            CatalogMediaType.VIDEO_AUDIO
                        } else {
                            CatalogMediaType.AUDIO
                        },
                        availability = MediaAvailability.PLAYABLE,
                        codecWarning = if (container == "flv") "FLV" else "",
                        container = container,
                        seekSupported = probe.seekSupported && container != "flv",
                        isSystemTone = isSystemTone
                    )
                    if (kind == MusicMediaKind.VIDEO) videoCount++ else audioCount++
                }
            }
        }
        return MusicFolderScanResult(
            tracks = tracks.values.toList(),
            audioCount = audioCount,
            videoCount = videoCount,
            rejectedAudio = rejectedAudio,
            rejectedVideos = rejectedVideos,
            evaluatedUris = evaluatedUris,
            automaticallyEligibleUris = automaticallyEligibleUris,
            rejectedNoAudioCount = rejectedNoAudioCount,
            rejectedUnsupportedCodecCount = rejectedUnsupportedCodecCount,
            unreadableCount = unreadableCount,
            metadataByUri = metadataByUri
        )
    }

    suspend fun listSubfolders(source: MusicLibrarySource): List<MusicFolderCandidate> {
        val pendingDirectories = ArrayDeque<Pair<String, String>>().apply {
            add(source.documentId to "")
        }
        val visitedDirectories = HashSet<String>()
        val folders = mutableListOf<MusicFolderCandidate>()
        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directoryId, parentPath) = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directoryId)) continue
            treeAccess.children(source.treeUri, directoryId)
                .filter(MusicDocumentEntry::isDirectory)
                .forEach { entry ->
                    val path = if (parentPath.isBlank()) {
                        entry.displayName
                    } else {
                        "$parentPath/${entry.displayName}"
                    }
                    folders += MusicFolderCandidate(
                        documentId = entry.documentId,
                        name = entry.displayName,
                        path = path
                    )
                    pendingDirectories.addLast(entry.documentId to path)
                }
        }
        return folders
    }
}

internal class SafMusicLibraryTreeAccess(
    private val context: android.content.Context,
    private val audioTrackProbe: PlayableAudioTrackProbe
) : MusicLibraryTreeAccess {
    override suspend fun children(
        treeUri: String,
        documentId: String
    ): List<MusicDocumentEntry> {
        val uri = android.net.Uri.parse(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            buildList {
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeColumn)
                    add(
                        MusicDocumentEntry(
                            documentId = cursor.getString(idColumn),
                            displayName = cursor.getString(nameColumn).orEmpty(),
                            mimeType = mime,
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
            }
        } ?: error("A selected folder returned no query cursor")
    }

    override fun documentUri(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(
            android.net.Uri.parse(treeUri),
            documentId
        ).toString()

    override suspend fun hasPlayableAudioTrack(uri: String): Boolean {
        val contentUri = android.net.Uri.parse(uri)
        return audioTrackProbe.hasAudioTrack(contentUri)
    }

    override suspend fun probeAudioTrack(
        uri: String,
        displayName: String,
        mimeType: String?
    ): AudioProbeResult {
        val contentUri = android.net.Uri.parse(uri)
        return audioTrackProbe.probe(contentUri, displayName, mimeType)
    }

    override suspend fun durationMs(uri: String): Long? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, android.net.Uri.parse(uri))
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } finally {
            runCatching { retriever.release() }
        }
    }
}
