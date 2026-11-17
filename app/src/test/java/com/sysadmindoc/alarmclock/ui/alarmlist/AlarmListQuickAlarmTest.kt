package com.sysadmindoc.alarmclock.ui.alarmlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.model.Alarm
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AlarmListQuickAlarmTest {
    private val alarmRepository = mockk<AlarmRepository>(relaxed = true)
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val prefs = mockk<PreferencesManager>(relaxed = true)
    private val events = mockk<AlarmEventRepository>(relaxed = true)
    private val music = mockk<MusicLibraryRepository>(relaxed = true)
    private val replay = mockk<HistoryReplayActions>(relaxed = true)

    @Before fun setUp() {
        every { alarmRepository.observeAll() } returns flowOf(emptyList())
        every { alarmRepository.observeNextAlarm() } returns flowOf(null)
        coEvery { alarmRepository.getAll() } returns emptyList()
        every { prefs.settings } returns flowOf(AppSettings())
        every { prefs.getCachedSettings() } returns AppSettings()
        coEvery { prefs.getCurrentSettings() } returns AppSettings()
        every { replay.state } returns MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)
        every { music.observeRecentHistory(any()) } returns flowOf(emptyList())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create() = AlarmListViewModel(
        context = ApplicationProvider.getApplicationContext<Application>(),
        repository = alarmRepository,
        scheduler = scheduler,
        calculator = mockk<NextAlarmCalculator>(relaxed = true),
        preferencesManager = prefs,
        eventRepository = events,
        musicLibraryRepository = music,
        replayActions = replay
    )

    @Test fun duplicateQuickTapSchedulesOnlyOneAlarmAndRecordsAckLatency() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { alarmRepository.save(any()) } returns 803L
        coEvery { scheduler.scheduleAt(any(), any(), any()) } returns true
        val viewModel = create()

        viewModel.createQuickAlarm(10)
        viewModel.createQuickAlarm(10)
        runCurrent()

        coVerify(exactly = 1) { alarmRepository.save(any()) }
        coVerify(exactly = 1) { scheduler.scheduleAt(any(), any(), any()) }
        val metric = viewModel.quickAlarmMetric.value
        assertNotNull(metric)
        assertTrue(metric!!.scheduled)
        assertTrue(metric.elapsedMs >= 0L)
    }

    @Test fun refusedRegistrationIsNeverRecordedAsACompletedQuickAlarm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { alarmRepository.save(any()) } returns 804L
        coEvery { scheduler.scheduleAt(any(), any(), any()) } returns false
        val viewModel = create()

        viewModel.createQuickAlarm(30)
        runCurrent()

        coVerify(exactly = 1) {
            alarmRepository.setEnabled(804L, enabled = false, nextTrigger = 0)
        }
        assertEquals(false, viewModel.quickAlarmMetric.value?.scheduled)
        assertFalse(viewModel.quickAlarmMetric.value?.withinFiveSeconds ?: true)
    }

    @Test fun fiveSecondGoalUsesMeasuredElapsedTimeNotAClaim() {
        assertTrue(QuickAlarmCreationMetric(5_000L, true).withinFiveSeconds)
        assertFalse(QuickAlarmCreationMetric(5_001L, true).withinFiveSeconds)
        assertFalse(QuickAlarmCreationMetric(1L, false).withinFiveSeconds)
    }
}
