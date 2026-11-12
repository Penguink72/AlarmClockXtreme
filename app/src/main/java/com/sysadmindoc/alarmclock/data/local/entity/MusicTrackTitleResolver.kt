package com.sysadmindoc.alarmclock.data.local.entity

object MusicTrackTitleResolver {
    private val mojibakePatterns = listOf(
        Regex("Ã[\\u0080-\\u00BF]"),
        Regex("Â[\\u0080-\\u00BF]"),
        Regex("â€[\\u0080-\\u00BF]"),
        Regex("ã[ƒ‚€]"),
        Regex("å[®¤‘]"),
        Regex("æ[ťœž]")
    )

    private val audioExtensions = setOf(
        "3gp", "aac", "amr", "flac", "m4a", "mid", "midi", "mp3", "ogg", "opus", "wav"
    )

    fun resolve(fileName: String, metadataTitle: String, legacyTitle: String = ""): String {
        val metadata = metadataTitle.trim().takeIf(::isUsable)
        if (metadata != null) return metadata

        val filenameTitle = fileName.trim().withoutAudioExtension()
        if (filenameTitle.isNotBlank() && isUsable(filenameTitle)) return filenameTitle

        val legacy = legacyTitle.trim().takeIf(::isUsable)
        if (legacy != null) return legacy

        return filenameTitle.ifBlank { legacyTitle.trim().ifBlank { metadataTitle.trim() } }
    }

    private fun isUsable(value: String): Boolean =
        value.isNotBlank() &&
            value.none {
                it == '\uFFFD' ||
                    it.code in 0x00..0x08 ||
                    it.code in 0x0B..0x1F ||
                    it.code in 0x7F..0x9F
            } &&
            mojibakePatterns.none { it.containsMatchIn(value) }

    private fun String.withoutAudioExtension(): String {
        val dot = lastIndexOf('.')
        if (dot <= 0 || substring(dot + 1).lowercase() !in audioExtensions) return this
        return substring(0, dot)
    }
}
