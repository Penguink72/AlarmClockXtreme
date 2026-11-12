package com.sysadmindoc.alarmclock.ui.ringtone

internal enum class MusicMediaKind {
    AUDIO,
    VIDEO
}

internal object SupportedMediaFile {
    private val audioExtensions = setOf(
        "3gp", "aac", "amr", "flac", "m4a", "mid", "midi", "mp3", "oga", "ogg", "opus", "wav"
    )
    private val videoExtensions = setOf("3gp", "m4v", "mkv", "mov", "mp4", "webm")

    fun classify(displayName: String?, mimeType: String?): MusicMediaKind? {
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()
        val audioExtension = extension in audioExtensions
        val videoExtension = extension in videoExtensions
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val audioMime = normalizedMime.startsWith("audio/")
        val videoMime = normalizedMime.startsWith("video/")

        if (normalizedMime.startsWith("image/") ||
            normalizedMime.startsWith("text/") ||
            normalizedMime in setOf("application/pdf", "application/zip")
        ) return null

        if (audioMime) return MusicMediaKind.AUDIO
        if (videoMime) return if (videoExtension) MusicMediaKind.VIDEO else null
        if (extension == "3gp") return MusicMediaKind.VIDEO
        if (audioExtension) return MusicMediaKind.AUDIO
        if (videoExtension) return MusicMediaKind.VIDEO
        return null
    }

}

internal object MusicMediaTrackPolicy {
    fun isIndexable(hasAudioTrack: Boolean?): Boolean = hasAudioTrack == true
}
