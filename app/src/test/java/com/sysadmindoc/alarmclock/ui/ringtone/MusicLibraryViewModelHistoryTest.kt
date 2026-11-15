package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MusicLibraryViewModelHistoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val indexer = mockk<MusicLibraryIndexer>(relaxed = true)
    private val repository = mockk<MusicLibraryRepository>(relaxed = true)
    private val previewController = mockk<MusicCatalogPreviewController>(relaxed = true)
    private val replayActions = mockk<HistoryReplayActions>(relaxed = true)
    private val replayStateFlow = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)

    @Before
    fun setUp() {
        every { replayActions.state } returns replayStateFlow
        coEvery { repository.poolCandidates() } returns emptyList()
        coEvery { repository.recentHistory(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initLoadsRecentHistoryByDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sample = listOf(
            PlaybackHistoryEntry(id = 10L, fireId = "f1", startedAt = 5000L, playOrder = 1, title = "Recent Track", uri = "uri")
        )
        coEvery { repository.recentHistory(100) } returns sample

        val viewModel = MusicLibraryViewModel(
            indexer = indexer,
            repository = repository,
            previewController = previewController,
            replayActions = replayActions,
            context = context
        )
        runCurrent()

        assertEquals(HistoryFilterMode.RECENT, viewModel.uiState.value.historyFilterMode)
        assertEquals(sample, viewModel.uiState.value.history)
        coVerify(atLeast = 1) { repository.recentHistory(100) }
    }

    @Test
    fun loadHistoryByDateSwitchesModeAndQueriesDateRange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val targetDate = LocalDate.of(2026, 11, 15)
        val sample = listOf(
            PlaybackHistoryEntry(id = 20L, fireId = "f2", startedAt = 6000L, playOrder = 1, title = "Date Track", uri = "uri2")
        )
        coEvery { repository.historyForDate(any(), any()) } returns sample

        val viewModel = MusicLibraryViewModel(
            indexer = indexer,
            repository = repository,
            previewController = previewController,
            replayActions = replayActions,
            context = context
        )
        runCurrent()

        viewModel.loadHistory(targetDate)
        runCurrent()

        assertEquals(HistoryFilterMode.BY_DATE, viewModel.uiState.value.historyFilterMode)
        assertEquals(targetDate, viewModel.uiState.value.selectedDate)
        assertEquals(sample, viewModel.uiState.value.history)
        coVerify(exactly = 1) { repository.historyForDate(any(), any()) }
    }

    @Test
    fun playHistoryStopsPreviewAndDelegatesToReplayActions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MusicLibraryViewModel(
            indexer = indexer,
            repository = repository,
            previewController = previewController,
            replayActions = replayActions,
            context = context
        )
        runCurrent()

        viewModel.playHistory(99L)

        verify(exactly = 1) { previewController.stop() }
        verify(exactly = 1) { replayActions.playHistory(99L) }

        viewModel.pauseReplay()
        verify(exactly = 1) { replayActions.pause() }

        viewModel.resumeReplay()
        verify(exactly = 1) { replayActions.resume() }

        viewModel.stopReplay()
        verify(exactly = 1) { replayActions.stop() }
    }
}
