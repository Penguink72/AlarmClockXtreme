package com.sysadmindoc.alarmclock.ui.alarmedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.model.Alarm
import com.sysadmindoc.alarmclock.data.preferences.AppSettings
import com.sysadmindoc.alarmclock.data.preferences.PreferencesManager
import com.sysadmindoc.alarmclock.data.repository.AlarmRepository
import com.sysadmindoc.alarmclock.data.repository.EvaluatedAlarmMusicSettings
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import com.sysadmindoc.alarmclock.domain.AlarmScheduler
import com.sysadmindoc.alarmclock.domain.NextAlarmCalculator
import com.sysadmindoc.alarmclock.domain.SnoozeMusicResumeMode
import io.mockk.coAnswers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** U2: Save is a single-flight operation, and does not certify a newer draft. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AlarmEditMusicSaveConcurrencyTest {
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun quickDoubleSaveDoesNotWriteTwiceAndNewerModeRemainsUnsaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alarms = mockk<AlarmRepository>(relaxed = true)
        val scheduler = mockk<AlarmScheduler>(relaxed = true)
        val calculator = mockk<NextAlarmCalculator>(relaxed = true)
        val preferences = mockk<PreferencesManager>(relaxed = true)
        val music = mockk<MusicLibraryRepository>(relaxed = true)
        coEvery { preferences.getCurrentSettings() } returns AppSettings()
        coEvery { alarms.getById(91L) } returns Alarm(
            id = 91L, hour = 7, minute = 30, label = "Music save probe"
        )
        coEvery { music.getSettings(91L) } returns AlarmMusicSettingsDraft()
        coEvery { music.evaluateAlarmMusicDraft(any()) } returns
            EvaluatedAlarmMusicSettings(
                playlists = emptyList(),
                artists = emptyList(),
                folders = emptyList(),
                matchingPlayableTrackCount = 2,
                unavailableTrackCount = 0
            )
        val firstWriteCanFinish = CompletableDeferred<Unit>()
        var writes = 0
        coEvery { music.saveSettings(91L, any()) } coAnswers {
            writes++
            if (writes == 1) firstWriteCanFinish.await()
            Unit
        }

        val viewModel = AlarmEditViewModel(
            SavedStateHandle(mapOf("alarmId" to 91L)),
            ApplicationProvider.getApplicationContext<Application>(),
            alarms, scheduler, calculator, preferences, music
        )
        runCurrent()
        assertTrue(viewModel.alarmMusicSettingsState.value.integrationAvailable)

        viewModel.onAlarmMusicSettingsAction(AlarmMusicSettingsAction.Save)
        viewModel.onAlarmMusicSettingsAction(AlarmMusicSettingsAction.Save)
        runCurrent()
        coVerify(exactly = 1) { music.saveSettings(91L, any()) }
        assertTrue(viewModel.alarmMusicSettingsState.value.saving)

        // The in-flight write captured NEXT_DIFFERENT. A newer
        // RESUME_INTERRUPTED choice cannot inherit that old Save ACK.
        viewModel.onAlarmMusicSettingsAction(
            AlarmMusicSettingsAction.SetSnoozeMusicMode(
                SnoozeMusicResumeMode.RESUME_INTERRUPTED
            )
        )
        firstWriteCanFinish.complete(Unit)
        runCurrent()
        assertFalse(viewModel.alarmMusicSettingsState.value.saving)
        assertFalse(viewModel.alarmMusicSettingsState.value.saved)
        assertTrue(viewModel.alarmMusicSettingsState.value.error == null)

        viewModel.onAlarmMusicSettingsAction(AlarmMusicSettingsAction.Save)
        runCurrent()
        coVerify(exactly = 2) { music.saveSettings(91L, any()) }
        assertTrue(viewModel.alarmMusicSettingsState.value.saved)
    }
}
