package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class MusicSourceStatus {
    NEEDS_SCAN,
    READY,
    ERROR
}

data class MusicLibrarySource(
    val id: String,
    val treeUri: String,
    val documentId: String,
    val name: String,
    val status: MusicSourceStatus = MusicSourceStatus.NEEDS_SCAN,
    val indexed: Boolean = false,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val trackUris: Set<String> = emptySet(),
    val error: String = ""
) {
    val trackCount: Int
        get() = audioCount + videoCount
}

internal object MusicLibrarySourceStore {
    private const val FILE_NAME = "music-library-sources.json"
    private const val LEGACY_PREFERENCES = "ringtone_sources"
    private const val LEGACY_FOLDER_URIS = "folder_uris"
    private const val MIGRATION_PREFERENCES = "music_library_sources"
    private const val LEGACY_MIGRATION_DONE = "legacy_folder_sources_migrated"

    @Synchronized
    fun getSources(context: Context): List<MusicLibrarySource> {
        val sources = read(context)
        val migrationPreferences = context.getSharedPreferences(
            MIGRATION_PREFERENCES,
            Context.MODE_PRIVATE
        )
        if (migrationPreferences.getBoolean(LEGACY_MIGRATION_DONE, false)) return sources
        val legacyUris = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(LEGACY_FOLDER_URIS, emptySet())
            .orEmpty()
        val knownIds = sources.mapTo(HashSet(), MusicLibrarySource::id)
        val migrated = legacyUris.mapNotNull { rawUri ->
            runCatching { rootSource(context, Uri.parse(rawUri)) }.getOrNull()
        }.filter { it.id !in knownIds }
        if (migrated.isNotEmpty()) {
            write(context, sources + migrated)
        }
        check(migrationPreferences.edit().putBoolean(LEGACY_MIGRATION_DONE, true).commit()) {
            "Could not record the music source migration"
        }
        return sources + migrated
    }

    @Synchronized
    fun addRoot(context: Context, treeUri: Uri, name: String): MusicLibrarySource =
        addSource(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), name)

    @Synchronized
    fun addSource(
        context: Context,
        treeUri: Uri,
        documentId: String,
        name: String
    ): MusicLibrarySource {
        val sources = getSources(context)
        val source = MusicLibrarySource(
            id = sourceId(treeUri.toString(), documentId),
            treeUri = treeUri.toString(),
            documentId = documentId,
            name = name.ifBlank { documentId.substringAfterLast('/') }
        )
        val existing = sources.firstOrNull { it.id == source.id }
        if (existing != null) return existing
        write(context, sources + source)
        return source
    }

    @Synchronized
    fun update(context: Context, source: MusicLibrarySource) {
        val sources = getSources(context)
        if (sources.none { it.id == source.id }) return
        write(context, sources.map { if (it.id == source.id) source else it })
    }

    @Synchronized
    fun remove(context: Context, sourceId: String): MusicLibrarySource? {
        val sources = getSources(context)
        val removed = sources.firstOrNull { it.id == sourceId } ?: return null
        write(context, sources.filterNot { it.id == sourceId })
        if (sources.none { it.id != sourceId && it.treeUri == removed.treeUri }) {
            val uri = Uri.parse(removed.treeUri)
            if (context.contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        return removed
    }

    internal fun sourceId(treeUri: String, documentId: String): String =
        "$treeUri|$documentId"

    private fun rootSource(context: Context, treeUri: Uri): MusicLibrarySource {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return MusicLibrarySource(
            id = sourceId(treeUri.toString(), documentId),
            treeUri = treeUri.toString(),
            documentId = documentId,
            name = runCatching { queryDisplayName(context, treeUri) }
                .getOrNull()
                ?: context.getString(
                    com.sysadmindoc.alarmclock.R.string.music_library_source_default_name
                )
        )
    }

    private fun queryDisplayName(context: Context, treeUri: Uri): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        return context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.takeIf(String::isNotBlank)
            } else {
                null
            }
        }
    }

    private fun read(context: Context): List<MusicLibrarySource> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val entries = root.optJSONArray("sources") ?: return emptyList()
        return buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                val json = entries.optJSONObject(index) ?: continue
                val treeUri = json.optString("treeUri")
                val documentId = json.optString("documentId")
                if (treeUri.isBlank() || documentId.isBlank()) continue
                val trackUrisJson = json.optJSONArray("trackUris") ?: JSONArray()
                val trackUris = buildSet(trackUrisJson.length()) {
                    for (uriIndex in 0 until trackUrisJson.length()) {
                        val uri = trackUrisJson.optString(uriIndex)
                        if (uri.isNotBlank()) add(uri)
                    }
                }
                add(
                    MusicLibrarySource(
                        id = sourceId(treeUri, documentId),
                        treeUri = treeUri,
                        documentId = documentId,
                        name = json.optString("name").ifBlank { documentId },
                        status = runCatching {
                            MusicSourceStatus.valueOf(json.optString("status"))
                        }.getOrDefault(MusicSourceStatus.NEEDS_SCAN),
                        indexed = json.optBoolean("indexed"),
                        audioCount = json.optInt("audioCount").coerceAtLeast(0),
                        videoCount = json.optInt("videoCount").coerceAtLeast(0),
                        trackUris = trackUris,
                        error = json.optString("error")
                    )
                )
            }
        }
    }

    private fun write(context: Context, sources: List<MusicLibrarySource>) {
        val root = JSONObject().put("version", 1)
        val entries = JSONArray()
        sources.forEach { source ->
            val trackUris = JSONArray()
            source.trackUris.sorted().forEach { trackUris.put(it) }
            entries.put(
                JSONObject()
                    .put("treeUri", source.treeUri)
                    .put("documentId", source.documentId)
                    .put("name", source.name)
                    .put("status", source.status.name)
                    .put("indexed", source.indexed)
                    .put("audioCount", source.audioCount)
                    .put("videoCount", source.videoCount)
                    .put("trackUris", trackUris)
                    .put("error", source.error)
            )
        }
        root.put("sources", entries)

        val destination = File(context.filesDir, FILE_NAME)
        val temporary = File(context.filesDir, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (error: Exception) {
                temporary.delete()
                throw IllegalStateException("Could not save music library sources", error)
            }
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not save music library sources", error)
        }
    }
}
