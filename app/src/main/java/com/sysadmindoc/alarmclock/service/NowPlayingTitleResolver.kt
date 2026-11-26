package com.sysadmindoc.alarmclock.service

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * UI-only track title lookup. Run on Dispatchers.IO *after* playback starts.
 * ContentResolver and metadata providers must never be on the alarm's audible
 * startup path. Failure is ordinary: SAF URIs may not expose a display name.
 */
internal object NowPlayingTitleResolver {
    fun resolve(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            for (column in listOf(MediaStore.Audio.Media.TITLE, OpenableColumns.DISPLAY_NAME)) {
                val raw = runCatching {
                    context.contentResolver.query(uri, arrayOf(column), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                }.getOrNull()
                normalized(raw)?.let { return it }
            }
        }
        return normalized(uri.lastPathSegment)
    }

    internal fun normalized(raw: String?): String? {
        val name = raw?.trim()?.substringAfterLast('/')?.substringBefore('?')?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (name.all(Char::isDigit)) return null // MediaStore ID is not a song title.
        val withoutExtension = name.replace(Regex("(?i)\\.(mp3|m4a|wav|ogg|flac|aac|opus|wma)$"), "")
        return withoutExtension.trim().takeIf { it.isNotEmpty() }
    }

    fun radioHost(url: String): String? =
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
}
