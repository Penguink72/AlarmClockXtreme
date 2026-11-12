package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.net.Uri
import java.io.FileNotFoundException

internal fun interface PlayableAudioTrackProbe {
    fun hasAudioTrack(uri: Uri): Boolean
}

internal class AndroidPlayableAudioTrackProbe(
    private val context: Context
) : PlayableAudioTrackProbe {
    override fun hasAudioTrack(uri: Uri): Boolean {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Media file could not be opened")
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(descriptor.fileDescriptor)
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            (0 until extractor.trackCount).any { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                mime.startsWith("audio/") &&
                    codecs.findDecoderForFormat(format) != null
            }
        } finally {
            try {
                extractor.release()
            } finally {
                descriptor.close()
            }
        }
    }
}
