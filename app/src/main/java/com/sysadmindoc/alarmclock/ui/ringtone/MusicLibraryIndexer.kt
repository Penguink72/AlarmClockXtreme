package com.sysadmindoc.alarmclock.ui.ringtone

import android.Manifest
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
    val rejectedVideos: Int
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
        FOLDER_FAILED
    }
}

@Singleton
class MusicLibraryIndexer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val treeScanner = MusicLibraryTreeScanner(
        SafMusicLibraryTreeAccess(
            context,
            AndroidPlayableAudioTrackProbe(context)
        )
    )

    suspend fun scan(sourceIds: Set<String>? = null): MusicScanResult = withContext(Dispatchers.IO) {
        val tracks = LinkedHashMap<String, MusicTrack>()
        val warnings = mutableListOf<MusicScanWarning>()
        val allSources = MusicLibrarySourceStore.getSources(context)
        val selectedSources = if (sourceIds == null) {
            allSources
        } else {
            allSources.filter { it.id in sourceIds }
        }
        var mediaStoreScanned = false
        var rejectedAudio = 0
        var rejectedVideos = 0

        if (sourceIds == null) {
            val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, mediaPermission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    scanMediaStore(tracks)
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
        }

        for (source in selectedSources) {
            currentCoroutineContext().ensureActive()
            try {
                val result = scanTree(source)
                result.tracks.forEach { tracks.putIfAbsent(it.uri, it) }
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
                MusicLibrarySourceStore.update(context, updated)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val updated = source.copy(
                    status = MusicSourceStatus.ERROR,
                    error = error.message ?: error.javaClass.simpleName
                )
                MusicLibrarySourceStore.update(context, updated)
                warnings += MusicScanWarning(
                    MusicScanWarning.Kind.FOLDER_FAILED,
                    "${source.name}: ${updated.error}"
                )
            }
        }

        MusicScanResult(
            tracks = tracks.values.toList(),
            warnings = warnings,
            sources = MusicLibrarySourceStore.getSources(context),
            mediaStoreScanned = mediaStoreScanned,
            rejectedAudio = rejectedAudio,
            rejectedVideos = rejectedVideos
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

    private suspend fun scanMediaStore(output: MutableMap<String, MusicTrack>) {
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE
        )
        context.contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri = android.content.ContentUris.withAppendedId(
                    contentUri,
                    cursor.getLong(idColumn)
                )
                val fileName = cursor.getString(nameColumn).orEmpty().ifBlank {
                    uri.lastPathSegment.orEmpty()
                }
                val metadataTitle = cursor.getString(titleColumn).orEmpty()
                output[uri.toString()] = MusicTrack(
                    uri = uri.toString(),
                    title = MusicTrackTitleResolver.resolve(fileName, metadataTitle),
                    source = SOURCE_MEDIA_STORE,
                    fileName = fileName,
                    metadataTitle = metadataTitle
                )
            }
        } ?: error("MediaStore returned no query cursor")
    }

    private suspend fun scanTree(source: MusicLibrarySource): MusicFolderScanResult {
        verifyPersistedGrant(source)
        return treeScanner.scan(source)
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

    companion object {
        const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
        const val SOURCE_SAF = "SAF"
        const val SOURCE_SAF_VIDEO = "SAF_VIDEO"
    }
}
