package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sysadmindoc.alarmclock.service.AlarmService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-instance Media3 audio preview controller for the XPlayer-style Music Library browser.
 *
 * Contract guarantees:
 * - At most one preview track can play at a time; starting a new track stops the previous one.
 * - Never starts preview while an alarm is actively firing ([AlarmService.activeAlarm] != null).
 * - Disables video track decoding so video containers (.mp4/.mkv/.webm/.flv) preview audio-only.
 * - Never writes to `playback_history`.
 * - [stop] immediately stops and releases the underlying player when the dialog closes or backgrounds.
 */
@Singleton
class Media3PreviewAudioController @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicCatalogPreviewController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var activeUri: String? = null

    private val _state = MutableStateFlow(MusicCatalogPreviewState())
    override val state: StateFlow<MusicCatalogPreviewState> = _state.asStateFlow()

    override suspend fun togglePreview(uri: String) {
        togglePreviewByUri(uri, playable = true, unplayableReason = null)
    }

    override fun togglePreview(item: MediaCatalogItem) {
        togglePreviewByUri(
            uri = item.uri,
            playable = item.playable,
            unplayableReason = item.unplayableReason
        )
    }

    private fun togglePreviewByUri(
        uri: String,
        playable: Boolean,
        unplayableReason: String?
    ) {
        runOnMain {
            if (AlarmService.activeAlarm.get() != null) {
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState(
                    playingUri = null,
                    blockedByAlarm = true,
                    error = null,
                    isPlaying = false,
                    isBuffering = false
                )
                return@runOnMain
            }

            val current = _state.value
            if (current.playingUri == uri && (current.isPlaying || current.isBuffering)) {
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState()
                return@runOnMain
            }

            if (!playable) {
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState(
                    playingUri = null,
                    blockedByAlarm = false,
                    error = unplayableReason ?: "UNPLAYABLE",
                    isPlaying = false,
                    isBuffering = false
                )
                return@runOnMain
            }

            startPreviewInternal(uri)
        }
    }

    override fun stop() {
        runOnMain {
            stopAndReleaseInternal()
            _state.value = MusicCatalogPreviewState()
        }
    }

    @OptIn(UnstableApi::class)
    private fun startPreviewInternal(uri: String) {
        stopAndReleaseInternal()
        activeUri = uri
        _state.value = MusicCatalogPreviewState(
            playingUri = uri,
            blockedByAlarm = false,
            error = null,
            isPlaying = false,
            isBuffering = true
        )

        runCatching {
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            val exoPlayer = ExoPlayer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setTrackSelector(trackSelector)
                .build()

            exoPlayer.setAudioAttributes(audioAttrs, true)
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            exoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activeUri != uri) return
                    if (AlarmService.activeAlarm.get() != null) {
                        stopAndReleaseInternal()
                        _state.value = MusicCatalogPreviewState(blockedByAlarm = true)
                        return
                    }
                    _state.value = _state.value.copy(
                        playingUri = uri,
                        isPlaying = isPlaying,
                        isBuffering = false,
                        error = null
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (activeUri != uri) return
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _state.value = _state.value.copy(
                                playingUri = uri,
                                isBuffering = true
                            )
                        }
                        Player.STATE_READY -> {
                            _state.value = _state.value.copy(
                                playingUri = uri,
                                isBuffering = false,
                                isPlaying = exoPlayer.isPlaying
                            )
                        }
                        Player.STATE_ENDED, Player.STATE_IDLE -> {
                            if (playbackState == Player.STATE_ENDED) {
                                stopAndReleaseInternal()
                                _state.value = MusicCatalogPreviewState()
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (activeUri != uri) return
                    stopAndReleaseInternal()
                    _state.value = MusicCatalogPreviewState(
                        playingUri = null,
                        blockedByAlarm = false,
                        error = error.errorCodeName,
                        isPlaying = false,
                        isBuffering = false
                    )
                }
            })

            player = exoPlayer
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { error ->
            stopAndReleaseInternal()
            _state.value = MusicCatalogPreviewState(
                playingUri = null,
                blockedByAlarm = false,
                error = error.message ?: error.javaClass.simpleName,
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    private fun stopAndReleaseInternal() {
        activeUri = null
        val existing = player ?: return
        player = null
        runCatching {
            existing.playWhenReady = false
            existing.stop()
            existing.release()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
