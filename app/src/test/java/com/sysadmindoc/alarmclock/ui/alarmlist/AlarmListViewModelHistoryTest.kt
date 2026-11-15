package com.sysadmindoc.alarmclock.ui.alarmlist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.preferences.AppSettings
import com.sysadmindoc.alarmclock.data.preferences.PreferencesManager
import com.sysadmindoc.alarmclock.data.repository.AlarmEventRepository
import com.sysadmindoc.alarmclock.data.repository.AlarmRepository
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import com.sysadmindoc.alarmclock.domain.AlarmScheduler
import com.sysadmindoc.alarmclock.domain.NextAlarmCalculator
import com.sysadmindoc.alarmclock.service.HistoryReplayActions
import com.sysadmindoc.alarmclock.service.ReplayUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlarmListViewModelHistoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val alarmRepository = mockk<AlarmRepository>()
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val calculator = mockk<NextAlarmCalculator>(relaxed = true)
    private val preferencesManager = mockk<PreferencesManager>()
    private val eventRepository = mockk<AlarmEventRepository>(relaxed = true)
    private val musicLibraryRepository = mockk<MusicLibraryRepository>()
    private val replayActions = mockk<HistoryReplayActions>(relaxed = true)
    private val replayStateFlow = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)

    @Before
    fun setUp() {
        every { alarmRepository.observeAll() } returns flowOf(emptyList())
        every { alarmRepository.observeNextAlarm() } returns flowOf(null)
        every { preferencesManager.settings } returns flowOf(AppSettings())
        every { replayActions.state } returns replayStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshRecentHistoryLoadsTopEntriesFromRepository() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sampleHistory = listOf(
            PlaybackHistoryEntry(id = 1L, fireId = "f1", startedAt = 1000L, playOrder = 1, title = "Song 1", uri = "uri1"),
            PlaybackHistoryEntry(id = 2L, fireId = "f1", startedAt = 2000L, playOrder = 2, title = "Song 2", uri = "uri2")
        )
        coEvery { musicLibraryRepository.recentHistory(5) } returns sampleHistory

        val viewModel = AlarmListViewModel(
            context = context,
            repository = alarmRepository,
            scheduler = scheduler,
            calculator = calculator,
            preferencesManager = preferencesManager,
            eventRepository = eventRepository,
            musicLibraryRepository = musicLibraryRepository,
            replayActions = replayActions
        )
        runCurrent()

        assertEquals(sampleHistory, viewModel.recentHistory.value)
        coVerify(atLeast = 1) { musicLibraryRepository.recentHistory(5) }
    }

    @Test
    fun replayMethodsDelegateToHistoryReplayActions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { musicLibraryRepository.recentHistory(any()) } returns emptyList()

        val viewModel = AlarmListViewModel(
            context = context,
            repository = alarmRepository,
            scheduler = scheduler,
            calculator = calculator,
            preferencesManager = preferencesManager,
            eventRepository = eventRepository,
            musicLibraryRepository = musicLibraryRepository,
            replayActions = replayActions
        )
        runCurrent()

        viewModel.playHistory(42L)
        verify(exactly = 1) { replayActions.playHistory(42L) }

        viewModel.pauseReplay()
        verify(exactly = 1) { replayActions.pause() }

        viewModel.resumeReplay()
        verify(exactly = 1) { replayActions.resume() }

        viewModel.stopReplay()
        verify(exactly = 1) { replayActions.stop() }
    }
}
