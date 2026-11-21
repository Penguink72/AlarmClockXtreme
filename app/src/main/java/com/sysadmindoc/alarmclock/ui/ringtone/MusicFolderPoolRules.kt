package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.content.SharedPreferences

/**
 * Durable folder-level intent for newly indexed media. Membership still lives
 * in Room: this projection only propagates explicit include/exclude rules to
 * tracks first discovered by a later rescan.
 */
internal object MusicFolderPoolRules {
    private const val PREFS_NAME = "acx-music-folder-pool-rules-v1"
    const val INCLUDE = "INCLUDE"
    const val EXCLUDE = "EXCLUDE"

    fun mediaStoreKey(relativePath: String): String =
        "device:" + normalizeMusicFolderPath(relativePath)

    fun safSourceKey(sourceId: String): String = "saf:$sourceId"

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAll(context: Context): Map<String, String> =
        store(context).all.mapNotNull { (key, value) ->
            (value as? String)?.takeIf { it == INCLUDE || it == EXCLUDE }
                ?.let { key to it }
        }.toMap()

    fun write(context: Context, key: String, mode: String) {
        require(key.startsWith("device:") || key.startsWith("saf:"))
        require(mode == INCLUDE || mode == EXCLUDE)
        check(store(context).edit().putString(key, mode).commit()) {
            "Could not persist folder pool intent"
        }
    }

    fun restore(context: Context, key: String, previous: String?) {
        val edit = store(context).edit()
        if (previous == null) edit.remove(key) else edit.putString(key, previous)
        check(edit.commit()) { "Could not restore previous folder pool intent" }
    }

    /**
     * Only the media actually indexed in this scan can be affected. No
     * filesystem deletion or undocumented broad storage permission.
     */
    fun scannedUrisByRuleKey(result: MusicScanResult): Map<String, Set<String>> {
        val grouped = linkedMapOf<String, MutableSet<String>>()
        result.tracks.forEach { track ->
            if (MusicLibraryIndexer.isMediaStoreSource(track.source)) {
                val path = result.metadataByUri[track.uri]?.folderPath.orEmpty()
                grouped.getOrPut(mediaStoreKey(path)) { linkedSetOf() }.add(track.uri)
            }
        }
        result.sources.forEach { source ->
            if (source.indexed && source.status == MusicSourceStatus.READY) {
                grouped.getOrPut(safSourceKey(source.id)) { linkedSetOf() }
                    .addAll(source.trackUris)
            }
        }
        return grouped
    }
}
