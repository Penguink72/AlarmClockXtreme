package com.sysadmindoc.alarmclock.data.readiness

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ProductionTestSessionStoreTest {
    private lateinit var context: Context
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ProductionTestSessionStore.clear(context)
    }

    @After fun tearDown() { ProductionTestSessionStore.clear(context) }

    @Test fun roundTripSurvivesReadAndNeverTouchesRealAlarmStore() {
        val session = ProductionTestSession.new(42, now)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        val proof = ProductionTestSessionStore.read(context, now + 100L)
        assertNotNull(proof)
        assertEquals(session, proof!!.session)
        assertEquals(ProductionTestStage.PREPARED, proof.stage)
        assertTrue(context.noBackupFilesDir.listFiles()?.any {
            it.name.startsWith("b2-production-test-proof.")
        } == true)
    }

    @Test fun testOnlyProofRequiresEveryProductionStage() {
        val session = ProductionTestSession.new(42, now)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.AUDIO_STARTED, now + 1L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, now + 1L))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, now + 2L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.RECEIVER_DELIVERED, now + 3L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.SERVICE_ACKNOWLEDGED, now + 4L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.AUDIO_STARTED, now + 5L))
        val snoozeAt = now + 61_000L
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.SNOOZE_REGISTERED, now + 6L,
            nextScheduledAtMs = snoozeAt))
        val registered = ProductionTestSessionStore.read(context, now + 7L)
        assertEquals(snoozeAt, registered?.session?.scheduledAtMs)
        assertFalse(registered!!.session.matchesDelivery(
            session.sourceAlarmId, session.sessionId, session.fireId(1),
            session.scheduledAtMs, now + 7L
        ))
        assertTrue(registered.session.matchesDelivery(
            session.sourceAlarmId, session.sessionId, session.fireId(1),
            snoozeAt, now + 7L
        ))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(0),
            ProductionTestStage.SNOOZE_DELIVERED, now + 7L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(1),
            ProductionTestStage.SNOOZE_DELIVERED, now + 8L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(1),
            ProductionTestStage.AUDIO_RESTARTED, now + 9L))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(1),
            ProductionTestStage.DISMISSED, now + 10L))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(1),
            ProductionTestStage.AUDIO_RESTARTED, now + 11L))
        assertEquals(ProductionTestStage.DISMISSED, ProductionTestSessionStore.read(context, now + 12L)?.stage)
    }

    @Test fun missingOrStaleSnoozeTargetCannotAdvanceProof() {
        val session = ProductionTestSession.new(79, now)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        val steps = listOf(
            ProductionTestStage.REGISTERED,
            ProductionTestStage.RECEIVER_DELIVERED,
            ProductionTestStage.SERVICE_ACKNOWLEDGED,
            ProductionTestStage.AUDIO_STARTED
        )
        steps.forEachIndexed { i, stage ->
            assertTrue(ProductionTestSessionStore.advance(
                context, session.sessionId, session.fireId(), stage, now + i + 1L
            ))
        }
        assertFalse(ProductionTestSessionStore.advance(
            context, session.sessionId, session.fireId(),
            ProductionTestStage.SNOOZE_REGISTERED, now + 5L
        ))
        assertFalse(ProductionTestSessionStore.advance(
            context, session.sessionId, session.fireId(),
            ProductionTestStage.SNOOZE_REGISTERED, now + 6L,
            nextScheduledAtMs = now + 1L
        ))
        assertFalse(ProductionTestSessionStore.advance(
            context, session.sessionId, session.fireId(),
            ProductionTestStage.SNOOZE_REGISTERED, now + 7L,
            nextScheduledAtMs = session.expiresAtMs
        ))
        assertEquals(ProductionTestStage.AUDIO_STARTED,
            ProductionTestSessionStore.read(context, now + 8L)?.stage)
        assertEquals(session.scheduledAtMs,
            ProductionTestSessionStore.read(context, now + 8L)?.session?.scheduledAtMs)
    }

    @Test fun oldOrOtherSessionCannotAdvanceAndExpiryNeverLooksSuccessful() {
        val session = ProductionTestSession.new(42, now)
        val other = ProductionTestSession.new(42, now)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        assertFalse(ProductionTestSessionStore.advance(context, other.sessionId, other.fireId(),
            ProductionTestStage.REGISTERED, now + 1L))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, now - 1L))
        assertNull(ProductionTestSessionStore.read(context, session.expiresAtMs))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, session.expiresAtMs))
    }

    @Test fun secondTestCannotReplaceActiveTestWithoutExplicitTerminalOutcome() {
        val first = ProductionTestSession.new(42, now)
        val second = ProductionTestSession.new(43, now + 5_000L)
        assertTrue(ProductionTestSessionStore.writeNew(context, first))
        assertFalse(ProductionTestSessionStore.writeNew(context, second))
        assertEquals(first.sessionId, ProductionTestSessionStore.read(context, now + 5_000L)?.session?.sessionId)
        assertTrue(ProductionTestSessionStore.advance(context, first.sessionId, first.fireId(),
            ProductionTestStage.CANCELLED, now + 6_000L))
        assertTrue(ProductionTestSessionStore.writeNew(context, second))
        assertEquals(second.sessionId, ProductionTestSessionStore.read(context, now + 6_000L)?.session?.sessionId)
    }

    @Test fun cancellationIsTerminalAndExplicitClearDropsProof() {
        val session = ProductionTestSession.new(42, now)
        assertTrue(ProductionTestSessionStore.writeNew(context, session))
        assertTrue(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.CANCELLED, now + 1L))
        assertFalse(ProductionTestSessionStore.advance(context, session.sessionId, session.fireId(),
            ProductionTestStage.REGISTERED, now + 2L))
        ProductionTestSessionStore.clear(context)
        assertNull(ProductionTestSessionStore.read(context, now + 3L))
    }
}
