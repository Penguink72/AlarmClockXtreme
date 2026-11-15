package com.sysadmindoc.alarmclock.service

import android.app.Application
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import com.sysadmindoc.alarmclock.ui.ringtone.Media3PreviewAudioController
import com.sysadmindoc.alarmclock.ui.ringtone.MusicCatalogPreviewState
import com.sysadmindoc.alarmclock.ui.ringtone.PreviewAudioPlayer
import com.sysadmindoc.alarmclock.ui.ringtone.PreviewAudioPlayerFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ManualPlaybackArbiterAndExclusionTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AlarmService.activeAlarm.set(null)
    }

    @Test
    fun resumeTransitionsToPreparingBeforeSyncOnPlayingCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = historyEntry(10L, "Song Ten", "content://music/10")
        val repository = mockk<MusicLibraryRepository>()
        coEvery { repository.historyEntry(10L) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)

        val arbiter = ManualPlaybackArbiter()
        val replayFactory = FakeArbiterReplayPlayerFactory(syncPlayingOnResume = true)
        val replayController = HistoryReplayController(repository, replayFactory, arbiter)

        replayController.playHistory(10L)
        runCurrent()
        val player = replayFactory.players.single()
        player.listener.onPlaying()
        assertEquals(ReplayUiState.Playing(10L, "Song Ten"), replayController.state.value)

        replayController.pause()
        runCurrent()
        assertEquals(ReplayUiState.Paused(10L, "Song Ten"), replayController.state.value)

        replayController.resume()
        runCurrent()
        // If Preparing overwrote active.resume(), state would be stuck at Preparing instead of Playing.
        assertEquals(ReplayUiState.Playing(10L, "Song Ten"), replayController.state.value)

        replayController.stop()
        runCurrent()
    }

    @Test
    fun startingReplayPreemptsActiveCatalogPreviewSynchronously() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = historyEntry(20L, "Replay Song", "content://music/replay")
        val repository = mockk<MusicLibraryRepository>()
        coEvery { repository.historyEntry(20L) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)

        val arbiter = ManualPlaybackArbiter()
        val previewFactory = FakePreviewAudioPlayerFactory()
        val previewController = Media3PreviewAudioController(arbiter, previewFactory)
        val replayFactory = FakeArbiterReplayPlayerFactory()
        val replayController = HistoryReplayController(repository, replayFactory, arbiter)

        previewController.togglePreview("content://music/preview-1")
        val previewPlayer = previewFactory.players.single()
        previewPlayer.listener.onIsPlayingChanged(true)
        assertTrue(previewController.state.value.isPlaying)
        assertEquals(ManualAudioOwner.CATALOG_PREVIEW, arbiter.activeOwner())

        // Directly calling HistoryReplayController.playHistory (e.g. from AlarmListScreen) must stop preview.
        replayController.playHistory(20L)
        assertEquals(ManualAudioOwner.HISTORY_REPLAY, arbiter.activeOwner())
        assertEquals(1, previewPlayer.stopCalls)
        assertEquals(MusicCatalogPreviewState(), previewController.state.value)

        runCurrent()
        val replayPlayer = replayFactory.players.single()
        replayPlayer.listener.onPlaying()
        assertEquals(ReplayUiState.Playing(20L, "Replay Song"), replayController.state.value)

        // Late callback from preempted preview player must be ignored.
        previewPlayer.listener.onIsPlayingChanged(true)
        assertEquals(MusicCatalogPreviewState(), previewController.state.value)
        assertEquals(ReplayUiState.Playing(20L, "Replay Song"), replayController.state.value)

        replayController.stop()
        runCurrent()
    }

    @Test
    fun startingCatalogPreviewPreemptsActiveHistoryReplaySynchronously() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = historyEntry(30L, "Active Replay", "content://music/active-replay")
        val repository = mockk<MusicLibraryRepository>()
        coEvery { repository.historyEntry(30L) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)

        val arbiter = ManualPlaybackArbiter()
        val previewFactory = FakePreviewAudioPlayerFactory()
        val previewController = Media3PreviewAudioController(arbiter, previewFactory)
        val replayFactory = FakeArbiterReplayPlayerFactory()
        val replayController = HistoryReplayController(repository, replayFactory, arbiter)

        replayController.playHistory(30L)
        runCurrent()
        val replayPlayer = replayFactory.players.single()
        replayPlayer.listener.onPlaying()
        assertEquals(ReplayUiState.Playing(30L, "Active Replay"), replayController.state.value)

        previewController.togglePreview("content://music/preview-2")
        assertEquals(ManualAudioOwner.CATALOG_PREVIEW, arbiter.activeOwner())
        assertEquals(ReplayUiState.Idle, replayController.state.value)
        assertEquals(1, replayPlayer.stopCalls)
        assertEquals(1, replayPlayer.releaseCalls)

        val previewPlayer = previewFactory.players.single()
        previewPlayer.listener.onIsPlayingChanged(true)
        assertTrue(previewController.state.value.isPlaying)

        // Late onPlaying from old replay player must not override idle or hijack preview.
        replayPlayer.listener.onPlaying()
        assertEquals(ReplayUiState.Idle, replayController.state.value)
        assertTrue(previewController.state.value.isPlaying)

        previewController.stop()
    }

    @Test
    fun stoppingPreviewOnDialogCloseDoesNotInterruptActiveReplay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = historyEntry(40L, "Persistent Replay", "content://music/persist")
        val repository = mockk<MusicLibraryRepository>()
        coEvery { repository.historyEntry(40L) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)

        val arbiter = ManualPlaybackArbiter()
        val previewFactory = FakePreviewAudioPlayerFactory()
        val previewController = Media3PreviewAudioController(arbiter, previewFactory)
        val replayFactory = FakeArbiterReplayPlayerFactory()
        val replayController = HistoryReplayController(repository, replayFactory, arbiter)

        replayController.playHistory(40L)
        runCurrent()
        val replayPlayer = replayFactory.players.single()
        replayPlayer.listener.onPlaying()
        assertEquals(ReplayUiState.Playing(40L, "Persistent Replay"), replayController.state.value)

        // MusicLibraryDialog DisposableEffect calls previewController.stop() on close.
        previewController.stop()

        assertEquals(ReplayUiState.Playing(40L, "Persistent Replay"), replayController.state.value)
        assertEquals(ManualAudioOwner.HISTORY_REPLAY, arbiter.activeOwner())
        assertEquals(0, replayPlayer.stopCalls)

        replayController.stop()
        runCurrent()
    }

    @Test
    fun activeAlarmBlocksPreviewStartAndReleasesArbiter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val arbiter = ManualPlaybackArbiter()
        val previewFactory = FakePreviewAudioPlayerFactory()
        val previewController = Media3PreviewAudioController(arbiter, previewFactory)

        AlarmService.activeAlarm.set(
            AlarmService.Companion.ActiveAlarmSnapshot(1L, 1_000L, "fire-1")
        )
        previewController.togglePreview("content://music/blocked")

        assertTrue(previewFactory.players.isEmpty())
        assertTrue(previewController.state.value.blockedByAlarm)
        assertFalse(previewController.state.value.isPlaying)
        assertEquals(ManualAudioOwner.NONE, arbiter.activeOwner())
    }

    private fun historyEntry(id: Long, title: String, uri: String) = PlaybackHistoryEntry(
        id = id,
        fireId = "fire-$id",
        startedAt = id * 1_000L,
        playOrder = 1,
        title = title,
        uri = uri
    )
}

private class FakeArbiterReplayPlayerFactory(
    private val syncPlayingOnResume: Boolean = false
) : HistoryReplayPlayerFactory {
    val players = mutableListOf<FakeArbiterReplayPlayer>()

    override fun create(listener: HistoryReplayPlayer.Listener): HistoryReplayPlayer {
        return FakeArbiterReplayPlayer(listener, syncPlayingOnResume).also { players.add(it) }
    }
}

private class FakeArbiterReplayPlayer(
    val listener: HistoryReplayPlayer.Listener,
    private val syncPlayingOnResume: Boolean
) : HistoryReplayPlayer {
    var stopCalls = 0
    var releaseCalls = 0

    override fun play(uri: String) = Unit

    override fun pause() = Unit

    override fun resume() {
        if (syncPlayingOnResume) {
            listener.onPlaying()
        }
    }

    override fun stop() {
        stopCalls++
    }

    override fun release() {
        releaseCalls++
    }
}

private class FakePreviewAudioPlayerFactory : PreviewAudioPlayerFactory {
    val players = mutableListOf<FakePreviewAudioPlayer>()

    override fun create(listener: PreviewAudioPlayer.Listener): PreviewAudioPlayer {
        return FakePreviewAudioPlayer(listener).also { players.add(it) }
    }
}

private class FakePreviewAudioPlayer(
    val listener: PreviewAudioPlayer.Listener
) : PreviewAudioPlayer {
    val playedUris = mutableListOf<String>()
    var stopCalls = 0

    override fun play(uri: String) {
        playedUris.add(uri)
    }

    override fun stopAndRelease() {
        stopCalls++
    }
}
