package com.sysadmindoc.alarmclock.ui.ringtone

import java.util.Locale

internal enum class MusicMediaKind {
    AUDIO,
    VIDEO
}

internal object SupportedMediaFile {
    private val audioExtensions = setOf(
        "3gp", "aac", "amr", "flac", "m4a", "mid", "midi", "mp3", "oga", "ogg", "opus", "wav"
    )
    private val videoExtensions = setOf(
        "3gp", "flv", "m4v", "mkv", "mov", "mp4", "webm"
    )
    private val flvMimeTypes = setOf(
        "video/x-flv",
        "video/flv",
        "application/x-flash-video"
    )
    private val rejectedApplicationMimes = setOf(
        "application/pdf",
        "application/zip"
    )

    fun classify(displayName: String?, mimeType: String?): MusicMediaKind? {
        val extension = extractExtension(displayName)
        val audioExtension = extension in audioExtensions
        val videoExtension = extension in videoExtensions
        val normalizedMime = normalizeMime(mimeType)
        val audioMime = normalizedMime.startsWith("audio/")
        val videoMime = normalizedMime.startsWith("video/") || normalizedMime in flvMimeTypes

        if (normalizedMime.startsWith("image/") ||
            normalizedMime.startsWith("text/") ||
            normalizedMime in rejectedApplicationMimes
        ) return null

        if (normalizedMime in flvMimeTypes) return MusicMediaKind.VIDEO
        if (audioMime) return MusicMediaKind.AUDIO
        if (videoMime) {
            return if (videoExtension || extension.isBlank()) MusicMediaKind.VIDEO else null
        }
        if (extension == "3gp") return MusicMediaKind.VIDEO
        if (audioExtension) return MusicMediaKind.AUDIO
        if (videoExtension) return MusicMediaKind.VIDEO
        return null
    }

    fun isFlv(displayName: String?, mimeType: String?): Boolean {
        val extension = extractExtension(displayName)
        val normalizedMime = normalizeMime(mimeType)
        return extension == "flv" || normalizedMime in flvMimeTypes
    }

    fun containerFor(displayName: String?, mimeType: String?): String {
        val extension = extractExtension(displayName)
        val normalizedMime = normalizeMime(mimeType)
        return when {
            extension == "flv" || normalizedMime in flvMimeTypes -> "flv"
            extension.isNotBlank() -> extension
            normalizedMime.startsWith("audio/") -> normalizedMime.removePrefix("audio/")
            normalizedMime.startsWith("video/") -> normalizedMime.removePrefix("video/")
            else -> ""
        }
    }

    private fun extractExtension(displayName: String?): String =
        displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()

    private fun normalizeMime(mimeType: String?): String =
        mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
}

internal object MusicMediaTrackPolicy {
    fun isIndexable(hasAudioTrack: Boolean?): Boolean = hasAudioTrack == true

    fun isIndexable(availability: MediaAvailability): Boolean =
        availability == MediaAvailability.PLAYABLE
}
