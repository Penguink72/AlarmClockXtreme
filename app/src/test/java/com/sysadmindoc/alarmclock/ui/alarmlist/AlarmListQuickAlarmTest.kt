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
import com.sysadmindoc.alarmclock.domain.AlarmSchedulingOutcome
import com.sysadmindoc.alarmclock.domain.AlarmSnoozeMusicModeStore
import com.sysadmindoc.alarmclock.domain.SnoozeMusicResumeMode
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsDraft
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import com.sysadmindoc.alarmclock.domain.NextAlarmCalculator
import com.sysadmindoc.alarmclock.service.HistoryReplayActions
import com.sysadmindoc.alarmclock.service.ReplayUiState
import io.mockk.coEvery
import io.mockk.firstArg
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

    @Test fun duplicatedAlarmCopiesMusicSourceAndSnoozeModeExactlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sourceId = 510_013L
        val copyId = 510_014L
        val settings = AlarmMusicSettingsDraft(
            playlistId = "commute", playbackStrategy = AlarmMusicPlaybackStrategy.PLAYLIST_ORDER
        )
        try {
            assertTrue(AlarmSnoozeMusicModeStore.save(
                context, sourceId, SnoozeMusicResumeMode.RESUME_INTERRUPTED
            ))
            coEvery { music.getSettings(sourceId) } returns settings
            coEvery { alarmRepository.save(any()) } returns copyId
            coEvery { scheduler.scheduleWithResult(any(), any(), any()) } returns
                AlarmSchedulingOutcome.EXACT

            val vm = create()
            val original = Alarm(
                id = sourceId, hour = 7, minute = 10, randomMusicEnabled = true
            )
            vm.duplicateAlarm(original)
            vm.duplicateAlarm(original)
            runCurrent()

            coVerify(exactly = 1) { alarmRepository.save(any()) }
            coVerify(exactly = 1) {
                music.saveSettings(copyId, match {
                    it.playlistId == "commute" &&
                    it.playbackStrategy == AlarmMusicPlaybackStrategy.PLAYLIST_ORDER
                })
            }
            coVerify(exactly = 1) {
                scheduler.scheduleWithResult(
                    match { it.id == copyId && it.randomMusicEnabled },
                    any(), any()
                )
            }
            assertEquals(
                SnoozeMusicResumeMode.RESUME_INTERRUPTED,
                AlarmSnoozeMusicModeStore.get(context, copyId)
            )
        } finally {
            AlarmSnoozeMusicModeStore.delete(context, sourceId)
            AlarmSnoozeMusicModeStore.delete(context, copyId)
        }
    }

    @Test fun rejectedDuplicateRegistrationRemovesNewRowAndMusicSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val copyId = 510_016L
        coEvery { music.getSettings(any()) } returns AlarmMusicSettingsDraft()
        coEvery { alarmRepository.save(any()) } returns copyId
        coEvery { scheduler.scheduleWithResult(any(), any(), any()) } returns
            AlarmSchedulingOutcome.FAILED
        val vm = create()
        vm.duplicateAlarm(Alarm(id = 510_015L, hour = 7, minute = 10))
        runCurrent()
        coVerify(exactly = 1) { scheduler.cancel(copyId) }
        coVerify(exactly = 1) { music.deleteSettings(copyId) }
        coVerify(exactly = 1) { alarmRepository.deleteById(copyId) }
    }

    @Test fun listToggleFailureDisablesPhantomAlarmInsteadOfClaimingSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = 510_018L
        coEvery { scheduler.scheduleWithResult(any(), any(), any()) } returns
            AlarmSchedulingOutcome.FAILED
        val vm = create()
        vm.toggleAlarm(Alarm(id = id, hour = 8, minute = 20, isEnabled = false))
        vm.toggleAlarm(Alarm(id = id, hour = 8, minute = 20, isEnabled = false))
        runCurrent()
        coVerify(exactly = 1) {
            scheduler.scheduleWithResult(match { it.id == id && it.isEnabled }, any(), any())
        }
        coVerify(exactly = 1) { alarmRepository.setEnabled(id, true, 0) }
        coVerify(exactly = 1) { alarmRepository.setEnabled(id, false, 0) }
    }

    @Test fun listToggleAcknowledgedAlarmIsNeverDisabledAsAFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = 510_019L
        coEvery { scheduler.scheduleWithResult(any(), any(), any()) } returns
            AlarmSchedulingOutcome.EXACT
        val vm = create()
        vm.toggleAlarm(Alarm(id = id, hour = 8, minute = 30, isEnabled = false))
        runCurrent()
        coVerify(exactly = 1) { alarmRepository.setEnabled(id, true, 0) }
        coVerify(exactly = 0) { alarmRepository.setEnabled(id, false, 0) }
    }

    @Test fun batchEnableCountsOnlyActuallyRegisteredAlarmsAsScheduled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { alarmRepository.getById(601L) } returns
            Alarm(id = 601L, hour = 8, minute = 0, isEnabled = false)
        coEvery { alarmRepository.getById(602L) } returns
            Alarm(id = 602L, hour = 8, minute = 5, isEnabled = false)
        coEvery { scheduler.scheduleWithResult(any(), any(), any()) } coAnswers {
            when (firstArg<Alarm>().id) {
                601L -> AlarmSchedulingOutcome.EXACT
                else -> AlarmSchedulingOutcome.FAILED
            }
        }
        val vm = create()
        vm.selectMany(setOf(601L, 602L))
        vm.enableSelected()
        vm.enableSelected()
        runCurrent()
        coVerify(exactly = 1) { alarmRepository.setEnabled(601L, true, 0) }
        coVerify(exactly = 1) { alarmRepository.setEnabled(602L, true, 0) }
        coVerify(exactly = 1) { alarmRepository.setEnabled(602L, false, 0) }
    }
}
