package com.sysadmindoc.alarmclock.domain

import android.app.AlarmManager
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
    fun registrationStatusNeverTreatsFailureAsAccepted() {
        assertFalse(AlarmRegistrationResult.FAILED.registered)
        assertTrue(AlarmRegistrationResult.EXACT.registered)
        assertTrue(AlarmRegistrationResult.INEXACT.registered)
        assertEquals(3, AlarmRegistrationResult.entries.size)
    }
}
