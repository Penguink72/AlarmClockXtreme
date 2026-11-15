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
import com.sysadmindoc.alarmclock.service.ManualAudioOwner
import com.sysadmindoc.alarmclock.service.ManualPlaybackArbiter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

internal interface PreviewAudioPlayer {
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onBufferingChanged(isBuffering: Boolean)
        fun onEnded()
        fun onError(reason: String)
    }

    fun play(uri: String)
    fun stopAndRelease()
}

internal fun interface PreviewAudioPlayerFactory {
    fun create(listener: PreviewAudioPlayer.Listener): PreviewAudioPlayer
}

/**
 * Single-instance Media3 audio preview controller for the XPlayer-style Music Library browser.
 *
 * Contract guarantees:
 * - At most one preview track can play at a time; starting a new track stops the previous one.
 * - Shares [ManualPlaybackArbiter] with history replay so preview and replay never overlap.
 * - Never starts or continues preview while an alarm is actively firing ([AlarmService.activeAlarm] != null).
 * - Disables video track decoding so video containers (.mp4/.mkv/.webm/.flv) preview audio-only.
 * - Never writes to `playback_history`.
 * - [stop] immediately stops and releases the preview player without disturbing history replay.
 */
@Singleton
class Media3PreviewAudioController internal constructor(
    private val arbiter: ManualPlaybackArbiter,
    private val playerFactory: PreviewAudioPlayerFactory
) : MusicCatalogPreviewController {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        arbiter: ManualPlaybackArbiter
    ) : this(arbiter, defaultPlayerFactory(context))

    constructor(context: Context) : this(ManualPlaybackArbiter(), defaultPlayerFactory(context))

    private companion object {
        const val ALARM_CHECK_INTERVAL_MS = 200L

        @OptIn(UnstableApi::class)
        fun defaultPlayerFactory(context: Context): PreviewAudioPlayerFactory =
            PreviewAudioPlayerFactory { listener ->
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
                        listener.onIsPlayingChanged(isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> listener.onBufferingChanged(true)
                            Player.STATE_READY -> listener.onBufferingChanged(false)
                            Player.STATE_ENDED -> listener.onEnded()
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        listener.onError(error.errorCodeName)
                    }
                })

                object : PreviewAudioPlayer {
                    override fun play(uri: String) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }

                    override fun stopAndRelease() {
                        exoPlayer.playWhenReady = false
                        exoPlayer.stop()
                        exoPlayer.release()
                    }
                }
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestGeneration = AtomicLong(0)
    private var player: PreviewAudioPlayer? = null
    private var activeUri: String? = null

    private val _state = MutableStateFlow(MusicCatalogPreviewState())
    override val state: StateFlow<MusicCatalogPreviewState> = _state.asStateFlow()

    init {
        arbiter.registerStopHandler(ManualAudioOwner.CATALOG_PREVIEW) {
            preemptFromArbiter()
        }
    }

    private val alarmMonitor = object : Runnable {
        override fun run() {
            if (player == null && activeUri == null) return
            if (AlarmService.activeAlarm.get() != null) {
                requestGeneration.incrementAndGet()
                arbiter.release(ManualAudioOwner.CATALOG_PREVIEW)
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState(blockedByAlarm = true)
                return
            }
            mainHandler.postDelayed(this, ALARM_CHECK_INTERVAL_MS)
        }
    }

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
        val generation = requestGeneration.incrementAndGet()
        val token = arbiter.claim(ManualAudioOwner.CATALOG_PREVIEW)
        runOnMain {
            if (generation != requestGeneration.get() ||
                !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
            ) {
                return@runOnMain
            }

            if (AlarmService.activeAlarm.get() != null) {
                arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
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
                arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState()
                return@runOnMain
            }

            if (!playable) {
                arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
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

            startPreviewInternal(uri, generation, token)
        }
    }

    override fun stop() {
        val generation = requestGeneration.incrementAndGet()
        arbiter.release(ManualAudioOwner.CATALOG_PREVIEW)
        runOnMain {
            if (generation == requestGeneration.get()) {
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState()
            }
        }
    }

    private fun preemptFromArbiter() {
        val generation = requestGeneration.incrementAndGet()
        runOnMain {
            if (generation == requestGeneration.get()) {
                stopAndReleaseInternal()
                _state.value = MusicCatalogPreviewState()
            }
        }
    }

    private fun startPreviewInternal(uri: String, generation: Long, token: Long) {
        stopAndReleaseInternal()
        if (generation != requestGeneration.get() ||
            !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
        ) {
            return
        }
        activeUri = uri
        _state.value = MusicCatalogPreviewState(
            playingUri = uri,
            blockedByAlarm = false,
            error = null,
            isPlaying = false,
            isBuffering = true
        )

        runCatching {
            val previewPlayer = playerFactory.create(object : PreviewAudioPlayer.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activeUri != uri ||
                        generation != requestGeneration.get() ||
                        !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
                    ) {
                        return
                    }
                    if (AlarmService.activeAlarm.get() != null) {
                        requestGeneration.incrementAndGet()
                        arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
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

                override fun onBufferingChanged(isBuffering: Boolean) {
                    if (activeUri != uri ||
                        generation != requestGeneration.get() ||
                        !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
                    ) {
                        return
                    }
                    _state.value = if (isBuffering) {
                        _state.value.copy(playingUri = uri, isBuffering = true)
                    } else {
                        _state.value.copy(playingUri = uri, isBuffering = false)
                    }
                }

                override fun onEnded() {
                    if (activeUri != uri ||
                        generation != requestGeneration.get() ||
                        !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
                    ) {
                        return
                    }
                    arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
                    stopAndReleaseInternal()
                    _state.value = MusicCatalogPreviewState()
                }

                override fun onError(reason: String) {
                    if (activeUri != uri ||
                        generation != requestGeneration.get() ||
                        !arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
                    ) {
                        return
                    }
                    arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
                    stopAndReleaseInternal()
                    _state.value = MusicCatalogPreviewState(
                        playingUri = null,
                        blockedByAlarm = false,
                        error = reason,
                        isPlaying = false,
                        isBuffering = false
                    )
                }
            })

            player = previewPlayer
            mainHandler.removeCallbacks(alarmMonitor)
            mainHandler.postDelayed(alarmMonitor, ALARM_CHECK_INTERVAL_MS)
            previewPlayer.play(uri)
        }.onFailure { error ->
            if (generation == requestGeneration.get() &&
                arbiter.isCurrent(ManualAudioOwner.CATALOG_PREVIEW, token)
            ) {
                arbiter.release(ManualAudioOwner.CATALOG_PREVIEW, token)
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
    }

    private fun stopAndReleaseInternal() {
        mainHandler.removeCallbacks(alarmMonitor)
        activeUri = null
        val existing = player ?: return
        player = null
        runCatching {
            existing.stopAndRelease()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
