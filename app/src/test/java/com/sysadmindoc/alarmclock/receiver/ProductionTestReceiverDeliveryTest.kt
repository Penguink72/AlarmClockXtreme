package com.sysadmindoc.alarmclock.receiver

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestAlarmScheduler
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestSession
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestSessionStore
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestStage
import com.sysadmindoc.alarmclock.service.AlarmService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * This exercises the actual production AlarmReceiver route without starting
 * the audio Service. Invalid/duplicate broadcasts cannot forge the proof or
 * dispatch a second foreground-service startup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ProductionTestReceiverDeliveryTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var session: ProductionTestSession

    @Before fun prepareRegisteredSession() {
        ProductionTestSessionStore.clear(context)
        val now = System.currentTimeMillis()
        session = ProductionTestSession.new(4059, now - 1_000L, firstDelayMs = 1_000L)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        assertTrue(ProductionTestSessionStore.advance(
            context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, now
        ))
        // Keep the assertions independent of any previous Robolectric
        // service startup, if one was left in the shadow application.
        while (shadowOf(context).nextStartedService != null) { }
    }

    @After fun cleanup() { ProductionTestSessionStore.clear(context) }

    private fun diagnosticIntent(
        fireId: String = session.fireId(),
        triggerAt: Long = session.scheduledAtMs
    ): Intent = Intent(context, AlarmReceiver::class.java).apply {
        action = ProductionTestAlarmScheduler.ACTION_TEST_FIRE
        putExtra(ProductionTestAlarmScheduler.EXTRA_SESSION_ID, session.sessionId)
        putExtra(ProductionTestAlarmScheduler.EXTRA_TEST_FIRE_ID, fireId)
        putExtra(ProductionTestAlarmScheduler.EXTRA_SOURCE_ALARM_ID, session.sourceAlarmId)
        putExtra(ProductionTestAlarmScheduler.EXTRA_TRIGGER_AT, triggerAt)
    }

    @Test fun validDeliveryStartsRealServiceOnceWithTestNamespace() {
        AlarmReceiver().onReceive(context, diagnosticIntent())
        val proof = ProductionTestSessionStore.read(context, System.currentTimeMillis())
        assertEquals(ProductionTestStage.RECEIVER_DELIVERED, proof?.stage)
        val service = shadowOf(context).nextStartedService
        assertEquals(AlarmService::class.java.name, service?.component?.className)
        assertEquals(AlarmService.ACTION_START_ALARM, service?.action)
        assertEquals(session.sessionId, service?.getStringExtra(
            AlarmService.EXTRA_PRODUCTION_TEST_SESSION_ID
        ))

        AlarmReceiver().onReceive(context, diagnosticIntent())
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(ProductionTestStage.RECEIVER_DELIVERED,
            ProductionTestSessionStore.read(context, System.currentTimeMillis())?.stage)
    }

    @Test fun wrongTimeAndOldFireAreIgnoredBeforeServiceHandoff() {
        AlarmReceiver().onReceive(context, diagnosticIntent(
            triggerAt = session.scheduledAtMs + 13_000L
        ))
        AlarmReceiver().onReceive(context, diagnosticIntent(fireId = "old-fire"))
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(ProductionTestStage.REGISTERED,
            ProductionTestSessionStore.read(context, System.currentTimeMillis())?.stage)
    }
}
