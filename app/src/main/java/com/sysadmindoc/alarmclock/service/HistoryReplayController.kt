package com.sysadmindoc.alarmclock.service

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
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReplayUiState {
    data object Idle : ReplayUiState
    data class Resolving(val historyId: Long) : ReplayUiState
    data class Preparing(val historyId: Long) : ReplayUiState
    data class Playing(val historyId: Long, val title: String) : ReplayUiState
    data class Paused(val historyId: Long, val title: String) : ReplayUiState
    data class Error(val historyId: Long?, val reason: String) : ReplayUiState
}

interface HistoryReplayActions {
    val state: StateFlow<ReplayUiState>
    fun playHistory(historyId: Long)
    fun pause()
    fun resume()
    fun playNext()
    fun playPrevious()
    fun stop()
}

interface HistoryReplayPlayer {
    interface Listener {
        fun onPreparing()
        fun onPlaying()
        fun onPaused()
        fun onEnded()
        fun onError(reason: String)
    }

    fun play(uri: String)
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

fun interface HistoryReplayPlayerFactory {
    fun create(listener: HistoryReplayPlayer.Listener): HistoryReplayPlayer
}

@Singleton
class HistoryReplayController @Inject constructor(
    private val repository: MusicLibraryRepository,
    private val playerFactory: HistoryReplayPlayerFactory
) : HistoryReplayActions {

    private companion object {
        const val RECENT_QUEUE_LIMIT = 500
        const val ALARM_CHECK_INTERVAL_MS = 200L
        const val ERROR_HISTORY_NOT_FOUND = "HISTORY_NOT_FOUND"
        const val ERROR_ALARM_ACTIVE = "ALARM_ACTIVE"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestGeneration = AtomicLong(0)
    private val _state = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)
    override val state: StateFlow<ReplayUiState> = _state.asStateFlow()

    private var player: HistoryReplayPlayer? = null
    private var queue: List<PlaybackHistoryEntry> = emptyList()
    private var queueIndex = -1
    private var currentHistoryId: Long? = null

    private val alarmMonitor = object : Runnable {
        override fun run() {
            if (player == null) return
            if (AlarmService.activeAlarm.get() != null) {
                requestGeneration.incrementAndGet()
                val historyId = currentHistoryId
                val cleanupError = releasePlayer()
                _state.value = ReplayUiState.Error(
                    historyId,
                    appendCleanupError(ERROR_ALARM_ACTIVE, cleanupError)
                )
                return
            }
            mainHandler.postDelayed(this, ALARM_CHECK_INTERVAL_MS)
        }
    }

    override fun playHistory(historyId: Long) {
        val generation = requestGeneration.incrementAndGet()
        scope.launch {
            if (generation != requestGeneration.get()) return@launch
            val cleanupError = releasePlayer()
            if (cleanupError != null) {
                _state.value = ReplayUiState.Error(historyId, cleanupError)
                return@launch
            }
            currentHistoryId = historyId
            _state.value = ReplayUiState.Resolving(historyId)
            if (AlarmService.activeAlarm.get() != null) {
                _state.value = ReplayUiState.Error(historyId, ERROR_ALARM_ACTIVE)
                return@launch
            }

            try {
                val entry = repository.historyEntry(historyId)
                if (generation != requestGeneration.get()) return@launch
                if (entry == null) {
                    currentHistoryId = null
                    _state.value = ReplayUiState.Error(historyId, ERROR_HISTORY_NOT_FOUND)
                    return@launch
                }

                val recent = repository.recentHistory(RECENT_QUEUE_LIMIT)
                if (generation != requestGeneration.get()) return@launch
                queue = if (recent.any { it.id == entry.id }) recent else listOf(entry) + recent
                queueIndex = queue.indexOfFirst { it.id == entry.id }
                startPlayback(entry, generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == requestGeneration.get()) {
                    currentHistoryId = null
                    _state.value = ReplayUiState.Error(
                        historyId,
                        error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    override fun playNext() {
        playAdjacentHistory(1)
    }

    override fun playPrevious() {
        playAdjacentHistory(-1)
    }

    private fun playAdjacentHistory(offset: Int) {
        val scheduledGeneration = requestGeneration.get()
        scope.launch {
            if (scheduledGeneration != requestGeneration.get()) return@launch
            if (_state.value is ReplayUiState.Resolving) return@launch
            val nextIndex = queueIndex + offset
            val entry = queue.getOrNull(nextIndex) ?: return@launch
            val generation = requestGeneration.incrementAndGet()
            val cleanupError = releasePlayer()
            if (cleanupError != null) {
                _state.value = ReplayUiState.Error(entry.id, cleanupError)
                return@launch
            }
            currentHistoryId = entry.id
            queueIndex = nextIndex
            _state.value = ReplayUiState.Resolving(entry.id)
            if (AlarmService.activeAlarm.get() != null) {
                _state.value = ReplayUiState.Error(entry.id, ERROR_ALARM_ACTIVE)
                return@launch
            }
            startPlayback(entry, generation)
        }
    }

    override fun pause() {
        val generation = requestGeneration.get()
        scope.launch {
            if (generation != requestGeneration.get()) return@launch
            val active = player ?: return@launch
            val historyId = currentHistoryId ?: return@launch
            val title = currentTitle() ?: return@launch
            active.pause()
            _state.value = ReplayUiState.Paused(historyId, title)
        }
    }

    override fun resume() {
        val generation = requestGeneration.get()
        scope.launch {
            if (generation != requestGeneration.get()) return@launch
            val active = player ?: return@launch
            if (AlarmService.activeAlarm.get() != null) {
                val historyId = currentHistoryId
                requestGeneration.incrementAndGet()
                val cleanupError = releasePlayer()
                _state.value = ReplayUiState.Error(
                    historyId,
                    appendCleanupError(ERROR_ALARM_ACTIVE, cleanupError)
                )
                return@launch
            }
            val historyId = currentHistoryId ?: return@launch
            active.resume()
            _state.value = ReplayUiState.Preparing(historyId)
        }
    }

    override fun stop() {
        val generation = requestGeneration.incrementAndGet()
        scope.launch {
            if (generation != requestGeneration.get()) return@launch
            val cleanupError = releasePlayer()
            queue = emptyList()
            queueIndex = -1
            currentHistoryId = null
            _state.value = if (cleanupError == null) {
                ReplayUiState.Idle
            } else {
                ReplayUiState.Error(null, cleanupError)
            }
        }
    }

    private fun startPlayback(entry: PlaybackHistoryEntry, generation: Long) {
        if (generation != requestGeneration.get()) return
        if (AlarmService.activeAlarm.get() != null) {
            _state.value = ReplayUiState.Error(entry.id, ERROR_ALARM_ACTIVE)
            return
        }
        currentHistoryId = entry.id
        _state.value = ReplayUiState.Preparing(entry.id)
        try {
            val replayPlayer = playerFactory.create(
                object : HistoryReplayPlayer.Listener {
                    override fun onPreparing() {
                        if (generation == requestGeneration.get()) {
                            _state.value = ReplayUiState.Preparing(entry.id)
                        }
                    }

                    override fun onPlaying() {
                        if (generation != requestGeneration.get()) return
                        if (AlarmService.activeAlarm.get() != null) {
                            requestGeneration.incrementAndGet()
                            val cleanupError = releasePlayer()
                            _state.value = ReplayUiState.Error(
                                entry.id,
                                appendCleanupError(ERROR_ALARM_ACTIVE, cleanupError)
                            )
                        } else {
                            _state.value = ReplayUiState.Playing(entry.id, entry.title)
                        }
                    }

                    override fun onPaused() {
                        if (generation == requestGeneration.get()) {
                            _state.value = ReplayUiState.Paused(entry.id, entry.title)
                        }
                    }

                    override fun onEnded() {
                        if (generation == requestGeneration.get()) {
                            val cleanupError = releasePlayer()
                            _state.value = if (cleanupError == null) {
                                ReplayUiState.Idle
                            } else {
                                ReplayUiState.Error(entry.id, cleanupError)
                            }
                        }
                    }

                    override fun onError(reason: String) {
                        if (generation == requestGeneration.get()) {
                            val cleanupError = releasePlayer()
                            _state.value = ReplayUiState.Error(
                                entry.id,
                                appendCleanupError(reason, cleanupError)
                            )
                        }
                    }
                }
            )
            player = replayPlayer
            mainHandler.removeCallbacks(alarmMonitor)
            mainHandler.postDelayed(alarmMonitor, ALARM_CHECK_INTERVAL_MS)
            replayPlayer.play(entry.uri)
        } catch (error: Exception) {
            val cleanupError = releasePlayer()
            _state.value = ReplayUiState.Error(
                entry.id,
                appendCleanupError(
                    error.message ?: error.javaClass.simpleName,
                    cleanupError
                )
            )
        }
    }

    private fun currentTitle(): String? {
        val id = currentHistoryId ?: return null
        return queue.firstOrNull { it.id == id }?.title
    }

    private fun releasePlayer(): String? {
        mainHandler.removeCallbacks(alarmMonitor)
        val existing = player ?: return null
        player = null
        var failure: Exception? = null
        try {
            existing.stop()
        } catch (error: Exception) {
            failure = error
        }
        try {
            existing.release()
        } catch (error: Exception) {
            val previous = failure
            if (previous == null) failure = error else previous.addSuppressed(error)
        }
        return failure?.let { it.message ?: it.javaClass.simpleName }
    }

    private fun appendCleanupError(reason: String, cleanupError: String?): String =
        if (cleanupError == null) reason else "$reason; cleanup: $cleanupError"
}

@Singleton
class Media3HistoryReplayPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : HistoryReplayPlayerFactory {

    @OptIn(UnstableApi::class)
    override fun create(listener: HistoryReplayPlayer.Listener): HistoryReplayPlayer {
        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .setTrackSelector(selector)
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, true)
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    listener.onPlaying()
                } else if (exoPlayer.playbackState == Player.STATE_READY) {
                    listener.onPaused()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (exoPlayer.playWhenReady) listener.onPreparing()
                        else listener.onPaused()
                    }
                    Player.STATE_ENDED -> listener.onEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                listener.onError(
                    listOfNotNull(error.errorCodeName, error.message)
                        .joinToString(": ")
                )
            }
        })

        return object : HistoryReplayPlayer {
            override fun play(uri: String) {
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                exoPlayer.prepare()
                exoPlayer.play()
            }

            override fun pause() {
                exoPlayer.pause()
            }

            override fun resume() {
                exoPlayer.play()
            }

            override fun stop() {
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
            }

            override fun release() {
                exoPlayer.release()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryReplayBindings {
    @Binds
    @Singleton
    abstract fun bindHistoryReplayActions(
        controller: HistoryReplayController
    ): HistoryReplayActions

    @Binds
    @Singleton
    abstract fun bindHistoryReplayPlayerFactory(
        factory: Media3HistoryReplayPlayerFactory
    ): HistoryReplayPlayerFactory
}
