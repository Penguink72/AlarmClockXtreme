package com.sysadmindoc.alarmclock.ui.alarmlist

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.AlarmDatabase
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
@Config(sdk = [30], application = Application::class)
class AlarmListViewModelHistoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val alarmRepository = mockk<AlarmRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val calculator = mockk<NextAlarmCalculator>(relaxed = true)
    private val preferencesManager = mockk<PreferencesManager>(relaxed = true)
    private val eventRepository = mockk<AlarmEventRepository>(relaxed = true)
    private val musicLibraryRepository = mockk<MusicLibraryRepository>(relaxed = true)
    private val replayActions = mockk<HistoryReplayActions>(relaxed = true)
    private val replayStateFlow = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)

    @Before
    fun setUp() {
        every { alarmRepository.observeAll() } returns flowOf(emptyList())
        every { alarmRepository.observeNextAlarm() } returns flowOf(null)
        coEvery { alarmRepository.getAll() } returns emptyList()
        every { preferencesManager.settings } returns flowOf(AppSettings())
        every { replayActions.state } returns replayStateFlow
        every { musicLibraryRepository.observeRecentHistory(any()) } returns flowOf(emptyList())
        coEvery { musicLibraryRepository.recentHistory(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observeRecentHistoryLoadsTopEntriesFromRepository() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sampleHistory = listOf(
            PlaybackHistoryEntry(id = 2L, fireId = "f1", startedAt = 2000L, playOrder = 2, title = "Song 2", uri = "uri2"),
            PlaybackHistoryEntry(id = 1L, fireId = "f1", startedAt = 1000L, playOrder = 1, title = "Song 1", uri = "uri1")
        )
        every { musicLibraryRepository.observeRecentHistory(5) } returns flowOf(sampleHistory)

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
        verify(atLeast = 1) { musicLibraryRepository.observeRecentHistory(5) }
    }

    @Test
    fun recentHistoryUpdatesReactivelyWithoutRecreatingViewModelAndReplaysByHistoryId() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        try {
            val realRepository = MusicLibraryRepository(
                database.musicLibraryDao(),
                database.playbackHistoryDao()
            )
            database.playbackHistoryDao().appendStarted("fire-a", 1_000L, "Song A", "content://music/a")
            database.playbackHistoryDao().appendStarted("fire-b", 2_000L, "Song B", "content://music/b")

            val viewModel = AlarmListViewModel(
                context = context,
                repository = alarmRepository,
                scheduler = scheduler,
                calculator = calculator,
                preferencesManager = preferencesManager,
                eventRepository = eventRepository,
                musicLibraryRepository = realRepository,
                replayActions = replayActions
            )

            val initialDeadline = System.currentTimeMillis() + 3_000L
            while (viewModel.recentHistory.value.map { it.title } != listOf("Song B", "Song A") &&
                System.currentTimeMillis() < initialDeadline
            ) {
                runCurrent()
                Thread.sleep(10)
            }
            assertEquals(listOf("Song B", "Song A"), viewModel.recentHistory.value.map { it.title })

            // Insert C into the DB after ViewModel creation; Room Flow should push [C, B, A] without recreating ViewModel.
            database.playbackHistoryDao().appendStarted("fire-c", 3_000L, "Song C", "content://music/c")

            val updateDeadline = System.currentTimeMillis() + 3_000L
            while (viewModel.recentHistory.value.map { it.title } != listOf("Song C", "Song B", "Song A") &&
                System.currentTimeMillis() < updateDeadline
            ) {
                runCurrent()
                Thread.sleep(10)
            }
            val updated = viewModel.recentHistory.value
            assertEquals(listOf("Song C", "Song B", "Song A"), updated.map { it.title })

            val latestEntry = updated.first()
            viewModel.playHistory(latestEntry.id)
            verify(exactly = 1) { replayActions.playHistory(latestEntry.id) }
        } finally {
            database.close()
        }
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
