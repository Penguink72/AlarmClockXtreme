package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream

internal data class AudioProbeResult(
    val availability: MediaAvailability,
    val mimeType: String = "",
    val audioCodec: String = "",
    val durationMs: Long? = null,
    val container: String = "",
    val seekSupported: Boolean = true,
    val failureReason: String = ""
) {
    val isPlayable: Boolean
        get() = availability == MediaAvailability.PLAYABLE
}

internal fun interface PlayableAudioTrackProbe {
    fun hasAudioTrack(uri: Uri): Boolean

    fun probe(
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null
    ): AudioProbeResult {
        val container = SupportedMediaFile.containerFor(displayName, mimeType)
        val playable = try {
            hasAudioTrack(uri)
        } catch (_: SecurityException) {
            return AudioProbeResult(
                availability = MediaAvailability.PERMISSION_MISSING,
                container = container,
                seekSupported = container != "flv",
                failureReason = "PERMISSION_DENIED"
            )
        } catch (_: Exception) {
            return AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = container,
                seekSupported = container != "flv",
                failureReason = "UNREADABLE"
            )
        }
        return if (playable) {
            AudioProbeResult(
                availability = MediaAvailability.PLAYABLE,
                container = container,
                seekSupported = container != "flv"
            )
        } else {
            AudioProbeResult(
                availability = MediaAvailability.NO_AUDIO_TRACK,
                container = container,
                seekSupported = container != "flv",
                failureReason = "NO_AUDIO_TRACK"
            )
        }
    }
}

/**
 * Pure Kotlin binary parser for FLV headers and audio tags aligned with Media3's
 * `FlvExtractor` capabilities.
 *
 * Platform `MediaExtractor` frequently fails to parse `.flv` containers or misreports
 * their tracks, whereas Media3 `ExoPlayer` plays FLV streams containing AAC, MP3,
 * PCM, or G.711 audio via `FlvExtractor`.
 */
internal object FlvAudioHeaderParser {
    const val MAX_FLV_PROBE_BYTES = 256 * 1024
    private const val FLV_HEADER_MIN_SIZE = 9
    private const val TAG_TYPE_AUDIO = 8
    private const val TAG_TYPE_VIDEO = 9
    private const val TAG_TYPE_SCRIPT_DATA = 18

    fun isFlvMagic(bytes: ByteArray, length: Int = bytes.size): Boolean =
        length >= 3 &&
            bytes[0] == 'F'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'V'.code.toByte()

    fun parse(bytes: ByteArray, length: Int = bytes.size): AudioProbeResult {
        if (length < FLV_HEADER_MIN_SIZE || !isFlvMagic(bytes, length)) {
            return AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = "flv",
                seekSupported = false,
                failureReason = "INVALID_FLV_HEADER"
            )
        }
        val typeFlags = bytes[4].toInt() and 0xFF
        val hasAudioFlag = (typeFlags and 0x04) != 0
        val dataOffset = readUInt32BigEndian(bytes, 5)
        if (dataOffset < FLV_HEADER_MIN_SIZE || dataOffset > length) {
            return AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = "flv",
                seekSupported = false,
                failureReason = "TRUNCATED_FLV_HEADER"
            )
        }

        // Pos starts at dataOffset, followed by 4-byte PreviousTagSize0
        var cursor = dataOffset + 4
        var sawAnyValidTag = false
        var unsupportedCodecResult: AudioProbeResult? = null

        while (cursor + 11 <= length) {
            val tagType = bytes[cursor].toInt() and 0xFF
            val dataSize = readUInt24BigEndian(bytes, cursor + 1)
            val tagDataStart = cursor + 11
            if (tagType != TAG_TYPE_AUDIO &&
                tagType != TAG_TYPE_VIDEO &&
                tagType != TAG_TYPE_SCRIPT_DATA
            ) {
                break
            }
            sawAnyValidTag = true
            if (tagType == TAG_TYPE_AUDIO && dataSize >= 1 && tagDataStart < length) {
                val soundHeader = bytes[tagDataStart].toInt() and 0xFF
                val soundFormat = (soundHeader ushr 4) and 0x0F
                val resolved = classifyFlvSoundFormat(soundFormat)
                if (resolved.isPlayable) {
                    return resolved
                } else {
                    unsupportedCodecResult = resolved
                }
            }
            val nextTagOffset = tagDataStart + dataSize + 4
            if (nextTagOffset <= cursor) break
            cursor = nextTagOffset
        }

        if (unsupportedCodecResult != null) {
            return unsupportedCodecResult
        }

        if (!hasAudioFlag || sawAnyValidTag) {
            return AudioProbeResult(
                availability = MediaAvailability.NO_AUDIO_TRACK,
                container = "flv",
                seekSupported = false,
                failureReason = "FLV_NO_AUDIO_TRACK"
            )
        }

        return AudioProbeResult(
            availability = MediaAvailability.UNREADABLE,
            container = "flv",
            seekSupported = false,
            failureReason = "FLV_TRUNCATED_TAGS"
        )
    }

    private fun classifyFlvSoundFormat(soundFormat: Int): AudioProbeResult = when (soundFormat) {
        10 -> AudioProbeResult(
            availability = MediaAvailability.PLAYABLE,
            mimeType = "audio/mp4a-latm",
            audioCodec = "aac",
            container = "flv",
            seekSupported = false
        )
        2, 14 -> AudioProbeResult(
            availability = MediaAvailability.PLAYABLE,
            mimeType = "audio/mpeg",
            audioCodec = "mp3",
            container = "flv",
            seekSupported = false
        )
        0, 3 -> AudioProbeResult(
            availability = MediaAvailability.PLAYABLE,
            mimeType = "audio/raw",
            audioCodec = "pcm",
            container = "flv",
            seekSupported = false
        )
        7 -> AudioProbeResult(
            availability = MediaAvailability.PLAYABLE,
            mimeType = "audio/g711-alaw",
            audioCodec = "g711-alaw",
            container = "flv",
            seekSupported = false
        )
        8 -> AudioProbeResult(
            availability = MediaAvailability.PLAYABLE,
            mimeType = "audio/g711-mlaw",
            audioCodec = "g711-mlaw",
            container = "flv",
            seekSupported = false
        )
        else -> AudioProbeResult(
            availability = MediaAvailability.CODEC_UNSUPPORTED,
            container = "flv",
            audioCodec = flvCodecName(soundFormat),
            seekSupported = false,
            failureReason = "FLV_AUDIO_CODEC_UNSUPPORTED_${flvCodecName(soundFormat)}"
        )
    }

    private fun flvCodecName(soundFormat: Int): String = when (soundFormat) {
        1 -> "adpcm"
        4, 5, 6 -> "nellymoser"
        11 -> "speex"
        15 -> "device_specific"
        else -> "format_$soundFormat"
    }

    private fun readUInt24BigEndian(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)

    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}

internal class AndroidPlayableAudioTrackProbe(
    private val context: Context
) : PlayableAudioTrackProbe {

    override fun hasAudioTrack(uri: Uri): Boolean =
        probe(uri).isPlayable

    override fun probe(
        uri: Uri,
        displayName: String?,
        mimeType: String?
    ): AudioProbeResult {
        val isKnownFlv = SupportedMediaFile.isFlv(displayName, mimeType) ||
            SupportedMediaFile.isFlv(uri.lastPathSegment, mimeType)
        val container = SupportedMediaFile.containerFor(displayName ?: uri.lastPathSegment, mimeType)

        if (isKnownFlv) {
            return probeFlvStream(uri)
        }

        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Media file could not be opened")
        } catch (_: SecurityException) {
            return AudioProbeResult(
                availability = MediaAvailability.PERMISSION_MISSING,
                container = container,
                failureReason = "PERMISSION_DENIED"
            )
        } catch (error: Exception) {
            return AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = container,
                failureReason = error.javaClass.simpleName
            )
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(descriptor.fileDescriptor)
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var sawAudioTrack = false
            var firstUnsupportedMime = ""
            var maxDurationUs = 0L

            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val dur = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
                    if (dur > maxDurationUs) maxDurationUs = dur
                }
                if (trackMime.startsWith("audio/")) {
                    sawAudioTrack = true
                    val decoderName = runCatching { codecs.findDecoderForFormat(format) }.getOrNull()
                    if (decoderName != null) {
                        val durationMs = (maxDurationUs / 1_000L).takeIf { it > 0L }
                        return AudioProbeResult(
                            availability = MediaAvailability.PLAYABLE,
                            mimeType = trackMime,
                            audioCodec = trackMime.removePrefix("audio/"),
                            durationMs = durationMs,
                            container = container.ifBlank { trackMime.substringAfter('/') },
                            seekSupported = true
                        )
                    } else if (firstUnsupportedMime.isBlank()) {
                        firstUnsupportedMime = trackMime
                    }
                }
            }

            if (sawAudioTrack) {
                AudioProbeResult(
                    availability = MediaAvailability.CODEC_UNSUPPORTED,
                    mimeType = firstUnsupportedMime,
                    audioCodec = firstUnsupportedMime.removePrefix("audio/"),
                    container = container,
                    failureReason = "DECODER_NOT_FOUND_$firstUnsupportedMime"
                )
            } else {
                // Fallback: check if the file has FLV magic bytes despite a non-.flv name
                val flvCheck = tryProbeFlvMagic(uri)
                flvCheck ?: AudioProbeResult(
                    availability = MediaAvailability.NO_AUDIO_TRACK,
                    container = container,
                    failureReason = "NO_AUDIO_TRACK"
                )
            }
        } catch (error: Exception) {
            val flvCheck = tryProbeFlvMagic(uri)
            flvCheck ?: AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = container,
                failureReason = error.javaClass.simpleName
            )
        } finally {
            try {
                extractor.release()
            } finally {
                runCatching { descriptor.close() }
            }
        }
    }

    private fun probeFlvStream(uri: Uri): AudioProbeResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = readPrefix(stream, FlvAudioHeaderParser.MAX_FLV_PROBE_BYTES)
                FlvAudioHeaderParser.parse(buffer)
            } ?: AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = "flv",
                seekSupported = false,
                failureReason = "OPEN_INPUT_STREAM_NULL"
            )
        } catch (_: SecurityException) {
            AudioProbeResult(
                availability = MediaAvailability.PERMISSION_MISSING,
                container = "flv",
                seekSupported = false,
                failureReason = "PERMISSION_DENIED"
            )
        } catch (error: Exception) {
            AudioProbeResult(
                availability = MediaAvailability.UNREADABLE,
                container = "flv",
                seekSupported = false,
                failureReason = error.javaClass.simpleName
            )
        }
    }

    private fun tryProbeFlvMagic(uri: Uri): AudioProbeResult? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = readPrefix(stream, FlvAudioHeaderParser.MAX_FLV_PROBE_BYTES)
                if (FlvAudioHeaderParser.isFlvMagic(buffer)) {
                    FlvAudioHeaderParser.parse(buffer)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun readPrefix(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = stream.read(buffer, offset, maxBytes - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == maxBytes) buffer else buffer.copyOf(offset)
    }
}
