package com.sysadmindoc.alarmclock.service

import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HistoryReplayControllerTest {

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
        AlarmService.activeAlarm.set(null)
    }

    @Test
    fun newestPlayRequestWinsWhenOlderHistoryLookupCompletesLate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<MusicLibraryRepository>()
        val firstLookup = CompletableDeferred<PlaybackHistoryEntry?>()
        val first = historyEntry(1, "First", "content://music/first", 1_000L)
        val second = historyEntry(2, "Second", "content://music/second", 2_000L)
        coEvery { repository.historyEntry(1L) } coAnswers { firstLookup.await() }
        coEvery { repository.historyEntry(2L) } returns second
        coEvery { repository.recentHistory(any()) } returns listOf(second, first)
        val factory = FakeHistoryReplayPlayerFactory()
        val controller = HistoryReplayController(repository, factory)

        controller.playHistory(1L)
        runCurrent()
        controller.playHistory(2L)
        runCurrent()
        firstLookup.complete(first)
        runCurrent()

        assertEquals(listOf(second.uri), factory.players.flatMap { it.playedUris })
        assertEquals(ReplayUiState.Preparing(second.id), controller.state.value)
        coVerify(exactly = 0) { repository.recordStarted(any(), any(), any()) }
        controller.stop()
        runCurrent()
    }

    @Test
    fun replayStateFollowsPlayerAndPlaybackErrorsAreVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<MusicLibraryRepository>()
        val entry = historyEntry(9, "History track", "content://music/history", 9_000L)
        coEvery { repository.historyEntry(entry.id) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)
        val factory = FakeHistoryReplayPlayerFactory()
        val controller = HistoryReplayController(repository, factory)

        controller.playHistory(entry.id)
        runCurrent()
        factory.players.single().listener.onPlaying()
        assertEquals(ReplayUiState.Playing(entry.id, entry.title), controller.state.value)

        controller.pause()
        runCurrent()
        assertEquals(ReplayUiState.Paused(entry.id, entry.title), controller.state.value)

        controller.resume()
        runCurrent()
        assertEquals(ReplayUiState.Preparing(entry.id), controller.state.value)
        factory.players.single().listener.onError("DECODER_INIT_FAILED")
        assertEquals(
            ReplayUiState.Error(entry.id, "DECODER_INIT_FAILED"),
            controller.state.value
        )
        coVerify(exactly = 0) { repository.recordStarted(any(), any(), any()) }
        controller.stop()
        runCurrent()
    }

    @Test
    fun activeAlarmPreemptsReplayAndPreventsStartingAnotherHistoryTrack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<MusicLibraryRepository>()
        val entry = historyEntry(5, "Alarm-safe", "content://music/5", 5_000L)
        coEvery { repository.historyEntry(entry.id) } returns entry
        coEvery { repository.recentHistory(any()) } returns listOf(entry)
        val factory = FakeHistoryReplayPlayerFactory()
        val controller = HistoryReplayController(repository, factory)

        controller.playHistory(entry.id)
        runCurrent()
        val player = factory.players.single()
        player.listener.onPlaying()
        assertEquals(ReplayUiState.Playing(entry.id, entry.title), controller.state.value)

        AlarmService.activeAlarm.set(AlarmService.ActiveAlarmSnapshot(10, 10_000L, "fire-10"))
        player.listener.onPlaying()
        assertEquals(1, player.releaseCount)
        assertEquals(ReplayUiState.Error(entry.id, "ALARM_ACTIVE"), controller.state.value)

        controller.playHistory(entry.id)
        runCurrent()

        assertEquals(ReplayUiState.Error(entry.id, "ALARM_ACTIVE"), controller.state.value)
        assertEquals(1, factory.players.size)
        coVerify(exactly = 0) { repository.recordStarted(any(), any(), any()) }
    }

    @Test
    fun nextAndPreviousSwitchWithinRecentHistoryWithoutRecordingAlarmHistory() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<MusicLibraryRepository>()
        val newest = historyEntry(3, "Newest", "content://music/newest", 3_000L)
        val selected = historyEntry(2, "Selected", "content://music/selected", 2_000L)
        val oldest = historyEntry(1, "Oldest", "content://music/oldest", 1_000L)
        coEvery { repository.historyEntry(selected.id) } returns selected
        coEvery { repository.recentHistory(any()) } returns listOf(newest, selected, oldest)
        val factory = FakeHistoryReplayPlayerFactory()
        val controller = HistoryReplayController(repository, factory)

        controller.playHistory(selected.id)
        runCurrent()
        controller.playNext()
        runCurrent()
        controller.playPrevious()
        runCurrent()

        assertEquals(
            listOf(oldest.uri, selected.uri),
            factory.players.flatMap { it.playedUris }
        )
        coVerify(exactly = 0) { repository.recordStarted(any(), any(), any()) }
        controller.stop()
        runCurrent()
    }

    private fun historyEntry(
        id: Long,
        title: String,
        uri: String,
        startedAt: Long
    ) = PlaybackHistoryEntry(
        id = id,
        fireId = "fire-$id",
        startedAt = startedAt,
        playOrder = 1,
        title = title,
        uri = uri
    )
}

private class FakeHistoryReplayPlayerFactory : HistoryReplayPlayerFactory {
    val players = mutableListOf<FakeHistoryReplayPlayer>()

    override fun create(listener: HistoryReplayPlayer.Listener): HistoryReplayPlayer {
        return FakeHistoryReplayPlayer(listener).also { players.add(it) }
    }
}

private class FakeHistoryReplayPlayer(
    val listener: HistoryReplayPlayer.Listener
) : HistoryReplayPlayer {
    val playedUris = mutableListOf<String>()
    var releaseCount = 0

    override fun play(uri: String) {
        playedUris += uri
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit

    override fun release() {
        releaseCount++
    }
}
