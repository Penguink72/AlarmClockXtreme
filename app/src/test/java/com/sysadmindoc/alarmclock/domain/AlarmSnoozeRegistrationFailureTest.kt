package com.sysadmindoc.alarmclock.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.model.Alarm
import com.sysadmindoc.alarmclock.data.preferences.AppSettings
import com.sysadmindoc.alarmclock.data.preferences.PreferencesManager
import com.sysadmindoc.alarmclock.data.repository.AlarmIncidentRepository
import com.sysadmindoc.alarmclock.data.repository.AlarmRepository
import com.sysadmindoc.alarmclock.data.repository.HolidayRepository
import com.sysadmindoc.alarmclock.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A successful startForegroundService(snooze) is NOT proof the next alarm was
 * registered. Inject failing AlarmManager calls to cover the actual API
 * contract, rather than only testing a pure boolean helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class AlarmSnoozeRegistrationFailureTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun failedExactAndFailedInexactNeverClaimSnoozeSuccessOrPersistTarget() = runTest {
        val alarmManager = mockk<AlarmManager>()
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { alarmManager.setAlarmClock(any(), any()) } throws SecurityException("injected exact denial")
        every {
            alarmManager.setAndAllowWhileIdle(any(), any(), any())
        } throws IllegalStateException("injected inexact denial")

        val repository = mockk<AlarmRepository>(relaxed = true)
        val incidentRepository = mockk<AlarmIncidentRepository>(relaxed = true)
        val preferencesManager = mockk<PreferencesManager>()
        coEvery { preferencesManager.getCurrentSettings() } returns AppSettings()
        every { preferencesManager.getCachedSettings() } returns AppSettings()

        val realContext: Context = ApplicationProvider.getApplicationContext()
        val context = object : ContextWrapper(realContext) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) alarmManager
                else super.getSystemService(name)
        }
        val scheduler = AlarmScheduler(
            context = context,
            repository = repository,
            calculator = mockk(relaxed = true),
            preferencesManager = preferencesManager,
            holidayRepository = mockk(relaxed = true),
            alarmIncidentRepository = incidentRepository,
            weatherRepository = mockk(relaxed = true)
        )
        val id = 875L
        val target = System.currentTimeMillis() + 10 * 60_000L
        val alarm = Alarm(
            id = id, hour = 7, minute = 0, label = "Snooze failure probe",
            isEnabled = true
        )

        val registered = scheduler.scheduleSnoozeAt(alarm, target)

        assertFalse("Both OS registration attempts failed: must reject snooze", registered)
        coVerify(exactly = 0) { repository.updateNextTrigger(id, any()) }
        verify(exactly = 1) { alarmManager.setAlarmClock(any(), any()) }
        verify(exactly = 1) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun inexactRegistrationIsRolledBackWhenDatabasePersistenceFails() = runTest {
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        every { alarmManager.canScheduleExactAlarms() } returns false

        val repository = mockk<AlarmRepository>(relaxed = true)
        coEvery { repository.updateNextTrigger(876L, any()) } throws
            IllegalStateException("injected Room write failure")
        val preferencesManager = mockk<PreferencesManager>()
        coEvery { preferencesManager.getCurrentSettings() } returns AppSettings()
        every { preferencesManager.getCachedSettings() } returns AppSettings()

        val realContext: Context = ApplicationProvider.getApplicationContext()
        val context = object : ContextWrapper(realContext) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) alarmManager
                else super.getSystemService(name)
        }
        val scheduler = AlarmScheduler(
            context = context,
            repository = repository,
            calculator = mockk(relaxed = true),
            preferencesManager = preferencesManager,
            holidayRepository = mockk(relaxed = true),
            alarmIncidentRepository = mockk(relaxed = true),
            weatherRepository = mockk(relaxed = true)
        )
        val alarm = Alarm(
            id = 876L, hour = 7, minute = 0,
            label = "Persistence failure probe", isEnabled = true
        )
        val target = System.currentTimeMillis() + 15 * 60_000L

        assertFalse(scheduler.scheduleSnooze(alarm, customMinutes = 8))
        assertFalse(scheduler.scheduleSnoozeAt(alarm, target))

        // Both registration APIs accepted the PendingIntent; neither may
        // leave a live OS alarm after the subsequent database failure.
        verify(exactly = 2) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any())
        }
        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
        coVerify(exactly = 2) { repository.updateNextTrigger(876L, any()) }
    }

    @Test
    fun incidentLoggerFailureDoesNotCancelAnAlreadyPersistedSnooze() = runTest {
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        every { alarmManager.canScheduleExactAlarms() } returns false

        val repository = mockk<AlarmRepository>(relaxed = true)
        val incidentRepository = mockk<AlarmIncidentRepository>()
        every {
            incidentRepository.recordAsync(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } throws IllegalStateException("injected incident logging failure")
        val preferencesManager = mockk<PreferencesManager>()
        coEvery { preferencesManager.getCurrentSettings() } returns AppSettings()
        every { preferencesManager.getCachedSettings() } returns AppSettings()

        val realContext: Context = ApplicationProvider.getApplicationContext()
        val context = object : ContextWrapper(realContext) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) alarmManager
                else super.getSystemService(name)
        }
        val scheduler = AlarmScheduler(
            context = context,
            repository = repository,
            calculator = mockk(relaxed = true),
            preferencesManager = preferencesManager,
            holidayRepository = mockk(relaxed = true),
            alarmIncidentRepository = incidentRepository,
            weatherRepository = mockk(relaxed = true)
        )
        val alarm = Alarm(
            id = 877L, hour = 7, minute = 0,
            label = "Logger failure probe", isEnabled = true
        )
        val target = System.currentTimeMillis() + 18 * 60_000L

        assertTrue(scheduler.scheduleSnooze(alarm, customMinutes = 9))
        assertTrue(scheduler.scheduleSnoozeAt(alarm, target))

        coVerify(exactly = 2) { repository.updateNextTrigger(877L, any()) }
        verify(exactly = 2) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any())
        }
        verify(exactly = 0) { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun registrationStatusNeverTreatsFailureAsAccepted() {
        assertFalse(AlarmRegistrationResult.FAILED.registered)
        assertTrue(AlarmRegistrationResult.EXACT.registered)
        assertTrue(AlarmRegistrationResult.INEXACT.registered)
        assertEquals(3, AlarmRegistrationResult.entries.size)
    }
}
