package com.sysadmindoc.alarmclock.ui.ringtone

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrackTitleResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MusicScanResult(
    val tracks: List<MusicTrack>,
    val warnings: List<MusicScanWarning>,
    val sources: List<MusicLibrarySource>,
    val mediaStoreScanned: Boolean,
    val rejectedAudio: Int,
    val rejectedVideos: Int,
    val evaluatedUris: Set<String> = emptySet(),
    val automaticallyEligibleUris: Set<String> = emptySet(),
    val metadataByUri: Map<String, MusicCatalogTrackMetadata> = emptyMap(),
    val videoSourceStatus: MediaCatalogSourceStatus? = null
)

data class MusicFolderCandidate(
    val documentId: String,
    val name: String,
    val path: String
)

data class MusicScanWarning(
    val kind: Kind,
    val detail: String = ""
) {
    enum class Kind {
        MEDIA_STORE_FAILED,
        AUDIO_PERMISSION_DENIED,
        VIDEO_PERMISSION_DENIED,
        FOLDER_FAILED
    }
}

@Singleton
class MusicLibraryIndexer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioTrackProbe = AndroidPlayableAudioTrackProbe(context)
    private val treeScanner = MusicLibraryTreeScanner(
        SafMusicLibraryTreeAccess(
            context,
            audioTrackProbe
        )
    )

    fun hasAudioPermission(): Boolean {
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, mediaPermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasVideoPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            return false
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(sourceIds: Set<String>? = null): MusicScanResult = withContext(Dispatchers.IO) {
        val tracks = LinkedHashMap<String, MusicTrack>()
        val metadataByUri = LinkedHashMap<String, MusicCatalogTrackMetadata>()
        val warnings = mutableListOf<MusicScanWarning>()
        val allSources = MusicLibrarySourceStore.getSources(context)
        val updatedSources = allSources.toMutableList()
        val selectedSources = if (sourceIds == null) {
            allSources
        } else {
            allSources.filter { it.id in sourceIds }
        }
        var mediaStoreScanned = false
        var rejectedAudio = 0
        var rejectedVideos = 0
        val evaluatedUris = LinkedHashSet<String>()
        val automaticallyEligibleUris = LinkedHashSet<String>()
        val minimumSeconds = MusicAutoPoolPreferences.minimumDurationSeconds(context)
        var videoSourceStatus: MediaCatalogSourceStatus? = null

        if (sourceIds == null) {
            if (hasAudioPermission()) {
                try {
                    scanMediaStoreAudio(
                        output = tracks,
                        metadataOutput = metadataByUri,
                        evaluated = evaluatedUris,
                        eligible = automaticallyEligibleUris,
                        minimumSeconds = minimumSeconds
                    )
                    mediaStoreScanned = true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    warnings += MusicScanWarning(
                        MusicScanWarning.Kind.MEDIA_STORE_FAILED,
                        error.message ?: error.javaClass.simpleName
                    )
                }
            } else {
                warnings += MusicScanWarning(MusicScanWarning.Kind.AUDIO_PERMISSION_DENIED)
            }

            if (hasVideoPermission()) {
                try {
                    val videoResult = scanMediaStoreVideo(
                        output = tracks,
                        metadataOutput = metadataByUri,
                        evaluated = evaluatedUris,
                        eligible = automaticallyEligibleUris,
                        minimumSeconds = minimumSeconds
                    )
                    rejectedVideos += videoResult.rejectedNoAudioCount +
                        videoResult.rejectedUnsupportedCodecCount +
                        videoResult.unreadableCount
                    videoSourceStatus = videoResult
                    mediaStoreScanned = true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    videoSourceStatus = MediaCatalogSourceStatus(
                        sourceId = "media_store_video",
                        displayName = "MediaStore Video",
                        providerKind = MediaProviderKind.MEDIA_STORE_VIDEO,
                        permissionGranted = true,
                        errorMessage = error.message ?: error.javaClass.simpleName
                    )
                }
            } else {
                videoSourceStatus = MediaCatalogSourceStatus(
                    sourceId = "media_store_video",
                    displayName = "MediaStore Video",
                    providerKind = MediaProviderKind.MEDIA_STORE_VIDEO,
                    permissionGranted = false
                )
            }
        }

        for (source in selectedSources) {
            currentCoroutineContext().ensureActive()
            try {
                val result = scanTree(source, minimumSeconds)
                result.tracks.forEach { tracks.putIfAbsent(it.uri, it) }
                result.metadataByUri.forEach { (uri, meta) ->
                    metadataByUri.putIfAbsent(uri, meta)
                }
                evaluatedUris.addAll(result.evaluatedUris)
                automaticallyEligibleUris.addAll(result.automaticallyEligibleUris)
                rejectedAudio += result.rejectedAudio
                rejectedVideos += result.rejectedVideos
                val updated = source.copy(
                    status = MusicSourceStatus.READY,
                    indexed = true,
                    audioCount = result.audioCount,
                    videoCount = result.videoCount,
                    trackUris = result.tracks.mapTo(LinkedHashSet(), MusicTrack::uri),
                    error = ""
                )
                updatedSources[updatedSources.indexOfFirst { it.id == source.id }] = updated
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val updated = source.copy(
                    status = MusicSourceStatus.ERROR,
                    error = error.message ?: error.javaClass.simpleName
                )
                updatedSources[updatedSources.indexOfFirst { it.id == source.id }] = updated
                warnings += MusicScanWarning(
                    MusicScanWarning.Kind.FOLDER_FAILED,
                    "${source.name}: ${updated.error}"
                )
            }
        }

        MusicScanResult(
            tracks = tracks.values.toList(),
            warnings = warnings,
            sources = updatedSources,
            mediaStoreScanned = mediaStoreScanned,
            rejectedAudio = rejectedAudio,
            rejectedVideos = rejectedVideos,
            evaluatedUris = evaluatedUris,
            automaticallyEligibleUris = automaticallyEligibleUris,
            metadataByUri = metadataByUri,
            videoSourceStatus = videoSourceStatus
        )
    }

    suspend fun listSubfolders(sourceId: String): List<MusicFolderCandidate> =
        withContext(Dispatchers.IO) {
            val source = MusicLibrarySourceStore.getSources(context)
                .firstOrNull { it.id == sourceId }
                ?: error("Music folder source no longer exists")
            verifyPersistedGrant(source)
            treeScanner.listSubfolders(source)
        }

    suspend fun addFolder(treeUri: Uri): MusicLibrarySource = withContext(Dispatchers.IO) {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val name = queryDisplayName(treeUri, documentId)
            ?: context.getString(com.sysadmindoc.alarmclock.R.string.music_library_source_default_name)
        MusicLibrarySourceStore.addRoot(context, treeUri, name)
    }

    suspend fun addSubfolders(sourceId: String, folders: List<MusicFolderCandidate>) =
        withContext(Dispatchers.IO) {
            val source = MusicLibrarySourceStore.getSources(context)
                .firstOrNull { it.id == sourceId }
                ?: error("Music folder source no longer exists")
            folders.forEach { folder ->
                MusicLibrarySourceStore.addSource(
                    context,
                    Uri.parse(source.treeUri),
                    folder.documentId,
                    folder.name
                )
            }
        }

    private suspend fun scanMediaStoreAudio(
        output: MutableMap<String, MusicTrack>,
        metadataOutput: MutableMap<String, MusicCatalogTrackMetadata>,
        evaluated: MutableSet<String>,
        eligible: MutableSet<String>,
        minimumSeconds: Int
    ) {
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
        }
        val stagedTracks = LinkedHashMap<String, MusicTrack>()
        val stagedMetadata = LinkedHashMap<String, MusicCatalogTrackMetadata>()
        val stagedEvaluated = LinkedHashSet<String>()
        val stagedEligible = LinkedHashSet<String>()

        context.contentResolver.query(contentUri, projection.toTypedArray(), null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            fun flag(column: String): Boolean {
                val index = cursor.getColumnIndex(column)
                return index >= 0 && !cursor.isNull(index) && cursor.getInt(index) != 0
            }
            fun longOrNull(column: String): Long? {
                val index = cursor.getColumnIndex(column)
                return if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
            fun stringOrEmpty(column: String): String {
                val index = cursor.getColumnIndex(column)
                return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
            }
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri = ContentUris.withAppendedId(
                    contentUri,
                    cursor.getLong(idColumn)
                ).toString()
                val fileName = cursor.getString(nameColumn).orEmpty().ifBlank {
                    Uri.parse(uri).lastPathSegment.orEmpty()
                }
                val metadataTitle = cursor.getString(titleColumn).orEmpty()
                val artist = normalizeArtist(stringOrEmpty(MediaStore.Audio.Media.ARTIST))
                val album = stringOrEmpty(MediaStore.Audio.Media.ALBUM).trim()
                val mimeType = stringOrEmpty(MediaStore.Audio.Media.MIME_TYPE)
                val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stringOrEmpty(MediaStore.Audio.Media.RELATIVE_PATH).trimEnd('/', '\\')
                } else ""
                val duration = longOrNull(MediaStore.Audio.Media.DURATION)?.takeIf { it > 0L }
                val ringtone = flag(MediaStore.Audio.Media.IS_RINGTONE)
                val notification = flag(MediaStore.Audio.Media.IS_NOTIFICATION)
                val alarm = flag(MediaStore.Audio.Media.IS_ALARM)
                val isSystemTone = MusicAutoPoolPolicy.isSystemSound(
                    relativePath = relativePath,
                    isRingtone = ringtone,
                    isNotification = notification,
                    isAlarm = alarm
                )
                val container = SupportedMediaFile.containerFor(fileName, mimeType)

                stagedTracks[uri] = MusicTrack(
                    uri = uri,
                    title = MusicTrackTitleResolver.resolve(fileName, metadataTitle),
                    source = SOURCE_MEDIA_STORE,
                    fileName = fileName,
                    metadataTitle = metadataTitle
                )
                stagedMetadata[uri] = MusicCatalogTrackMetadata(
                    uri = uri,
                    artist = artist,
                    album = album,
                    folderPath = relativePath,
                    durationMillis = duration,
                    mediaType = CatalogMediaType.AUDIO,
                    availability = MediaAvailability.PLAYABLE,
                    container = container,
                    seekSupported = container != "flv",
                    isSystemTone = isSystemTone
                )
                if (MusicAutoPoolPolicy.hasDecisiveMetadata(
                        duration, relativePath, ringtone, notification, alarm
                    )
                ) stagedEvaluated += uri
                if (MusicAutoPoolPolicy.isEligible(
                        durationMs = duration,
                        relativePath = relativePath,
                        isRingtone = ringtone,
                        isNotification = notification,
                        isAlarm = alarm,
                        minimumSeconds = minimumSeconds
                    )
                ) stagedEligible += uri
            }
        } ?: error("MediaStore returned no query cursor")

        output.putAll(stagedTracks)
        metadataOutput.putAll(stagedMetadata)
        evaluated.addAll(stagedEvaluated)
        eligible.addAll(stagedEligible)
    }

    private suspend fun scanMediaStoreVideo(
        output: MutableMap<String, MusicTrack>,
        metadataOutput: MutableMap<String, MusicCatalogTrackMetadata>,
        evaluated: MutableSet<String>,
        eligible: MutableSet<String>,
        minimumSeconds: Int
    ): MediaCatalogSourceStatus {
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Video.Media.RELATIVE_PATH
        }

        data class VideoRow(
            val uri: Uri,
            val fileName: String,
            val metadataTitle: String,
            val artist: String,
            val album: String,
            val mimeType: String,
            val relativePath: String,
            val duration: Long?
        )

        val rows = mutableListOf<VideoRow>()
        context.contentResolver.query(contentUri, projection.toTypedArray(), null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            fun longOrNull(column: String): Long? {
                val index = cursor.getColumnIndex(column)
                return if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
            fun stringOrEmpty(column: String): String {
                val index = cursor.getColumnIndex(column)
                return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
            }
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri = ContentUris.withAppendedId(contentUri, cursor.getLong(idColumn))
                val fileName = cursor.getString(nameColumn).orEmpty().ifBlank {
                    uri.lastPathSegment.orEmpty()
                }
                val mimeType = stringOrEmpty(MediaStore.Video.Media.MIME_TYPE)
                if (SupportedMediaFile.classify(fileName, mimeType) != MusicMediaKind.VIDEO) {
                    continue
                }
                rows += VideoRow(
                    uri = uri,
                    fileName = fileName,
                    metadataTitle = cursor.getString(titleColumn).orEmpty(),
                    artist = normalizeArtist(stringOrEmpty(MediaStore.Video.Media.ARTIST)),
                    album = stringOrEmpty(MediaStore.Video.Media.ALBUM).trim(),
                    mimeType = mimeType,
                    relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stringOrEmpty(MediaStore.Video.Media.RELATIVE_PATH).trimEnd('/', '\\')
                    } else "",
                    duration = longOrNull(MediaStore.Video.Media.DURATION)?.takeIf { it > 0L }
                )
            }
        } ?: error("MediaStore.Video returned no query cursor")

        var playableCount = 0
        var rejectedNoAudio = 0
        var rejectedUnsupportedCodec = 0
        var unreadableCount = 0

        for (row in rows) {
            currentCoroutineContext().ensureActive()
            val probe = audioTrackProbe.probe(row.uri, row.fileName, row.mimeType)
            if (!probe.isPlayable) {
                when (probe.availability) {
                    MediaAvailability.NO_AUDIO_TRACK -> rejectedNoAudio++
                    MediaAvailability.CODEC_UNSUPPORTED -> rejectedUnsupportedCodec++
                    else -> unreadableCount++
                }
                continue
            }
            playableCount++
            val uriString = row.uri.toString()
            val resolvedDuration = row.duration ?: probe.durationMs
            val container = probe.container.ifBlank {
                SupportedMediaFile.containerFor(row.fileName, row.mimeType)
            }
            val isSystemTone = MusicAutoPoolPolicy.isSystemSound(row.relativePath)

            output[uriString] = MusicTrack(
                uri = uriString,
                title = MusicTrackTitleResolver.resolve(row.fileName, row.metadataTitle),
                source = SOURCE_MEDIA_STORE_VIDEO,
                fileName = row.fileName,
                metadataTitle = row.metadataTitle
            )
            metadataOutput[uriString] = MusicCatalogTrackMetadata(
                uri = uriString,
                artist = row.artist,
                album = row.album,
                folderPath = row.relativePath,
                durationMillis = resolvedDuration,
                mediaType = CatalogMediaType.VIDEO_AUDIO,
                availability = MediaAvailability.PLAYABLE,
                codecWarning = if (container == "flv") "FLV" else "",
                container = container,
                seekSupported = probe.seekSupported && container != "flv",
                isSystemTone = isSystemTone
            )
            if (MusicAutoPoolPolicy.hasDecisiveMetadata(resolvedDuration, row.relativePath)) {
                evaluated += uriString
            }
            if (MusicAutoPoolPolicy.isEligible(
                    durationMs = resolvedDuration,
                    relativePath = row.relativePath,
                    minimumSeconds = minimumSeconds
                )
            ) {
                eligible += uriString
            }
        }

        return MediaCatalogSourceStatus(
            sourceId = "media_store_video",
            displayName = "MediaStore Video",
            providerKind = MediaProviderKind.MEDIA_STORE_VIDEO,
            playableCount = playableCount,
            rejectedNoAudioCount = rejectedNoAudio,
            rejectedUnsupportedCodecCount = rejectedUnsupportedCodec,
            unreadableCount = unreadableCount,
            permissionGranted = true
        )
    }

    private suspend fun scanTree(
        source: MusicLibrarySource,
        minimumSeconds: Int
    ): MusicFolderScanResult {
        verifyPersistedGrant(source)
        return treeScanner.scan(source, minimumSeconds)
    }

    private fun verifyPersistedGrant(source: MusicLibrarySource) {
        val treeUri = Uri.parse(source.treeUri)
        val hasGrant = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri &&
                it.isReadPermission &&
                it.uri.authority == treeUri.authority
        }
        if (!hasGrant) throw SecurityException("Folder access permission was revoked")
    }

    private fun queryDisplayName(treeUri: Uri, documentId: String): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotBlank) else null
        }
    }

    private fun normalizeArtist(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.equals("<unknown>", ignoreCase = true)) "" else trimmed
    }

    companion object {
        const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
        const val SOURCE_MEDIA_STORE_VIDEO = "MEDIA_STORE_VIDEO"
        const val SOURCE_SAF = "SAF"
        const val SOURCE_SAF_VIDEO = "SAF_VIDEO"

        fun isMediaStoreSource(source: String): Boolean =
            source == SOURCE_MEDIA_STORE || source == SOURCE_MEDIA_STORE_VIDEO

        fun isVideoSource(source: String): Boolean =
            source == SOURCE_SAF_VIDEO || source == SOURCE_MEDIA_STORE_VIDEO
    }
}
