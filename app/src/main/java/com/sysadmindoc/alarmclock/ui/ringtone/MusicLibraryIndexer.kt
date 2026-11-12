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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

data class MusicScanResult(
    val tracks: List<MusicTrack>,
    val warnings: List<MusicScanWarning>
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
    suspend fun scan(): MusicScanResult = withContext(Dispatchers.IO) {
        val tracks = LinkedHashMap<String, MusicTrack>()
        val warnings = mutableListOf<String>()
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

        RingtoneFolderStore.folderUris(context).forEach { rawUri ->
            currentCoroutineContext().ensureActive()
            try {
                scanTree(Uri.parse(rawUri), tracks)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                warnings += MusicScanWarning(
                    MusicScanWarning.Kind.FOLDER_FAILED,
                    error.message ?: error.javaClass.simpleName
                )
            }
        }

        MusicScanResult(tracks.values.toList(), warnings)
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
                val displayTitle = MusicTrackTitleResolver.resolve(fileName, metadataTitle)
                output[uri.toString()] = MusicTrack(
                    uri = uri.toString(),
                    title = displayTitle,
                    source = SOURCE_MEDIA_STORE,
                    fileName = fileName,
                    metadataTitle = metadataTitle
                )
            }
        } ?: error("MediaStore returned no query cursor")
    }

    private suspend fun scanTree(
        treeUri: Uri,
        output: MutableMap<String, MusicTrack>
    ) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val pendingDirectories = ArrayDeque<String>().apply { add(rootDocumentId) }
        val visitedDirectories = HashSet<String>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directoryId = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directoryId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val documentId = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn).orEmpty()
                    val mime = cursor.getString(mimeColumn)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories.addLast(documentId)
                    } else if (RingtoneFolderStore.isSupportedAudioDocument(name, mime)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        output[uri.toString()] = MusicTrack(
                            uri = uri.toString(),
                            title = MusicTrackTitleResolver.resolve(name, ""),
                            source = SOURCE_SAF,
                            fileName = name.ifBlank { uri.lastPathSegment.orEmpty() },
                            metadataTitle = ""
                        )
                    }
                }
            } ?: error("A selected folder returned no query cursor")
        }
    }

    companion object {
        const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
        const val SOURCE_SAF = "SAF"
    }
}
