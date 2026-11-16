package com.sysadmindoc.alarmclock.data.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionTestSessionContractTest {
    private val fixedTime = 1_700_000_000_000L

    @Test fun newSessionIsBoundedAndNotARealAlarmFireId() {
        val session = ProductionTestSession.new(42, fixedTime)
        assertTrue(session.valid())
        assertEquals(fixedTime + 60_000L, session.scheduledAtMs)
        assertTrue(session.fireId().startsWith("b2-test:"))
        assertTrue(session.canAccept(session.sessionId, session.fireId(), fixedTime + 60_000L))
        assertFalse(session.canAccept(session.sessionId, "42:${session.scheduledAtMs}", fixedTime + 60_000L))
    }

    @Test fun mismatchedAndExpiredSessionNeverAcceptsBroadcast() {
        val one = ProductionTestSession.new(42, fixedTime)
        val two = ProductionTestSession.new(42, fixedTime)
        assertFalse(one.canAccept(two.sessionId, one.fireId(), fixedTime + 60_000L))
        assertFalse(one.canAccept(one.sessionId, two.fireId(), fixedTime + 60_000L))
        assertFalse(one.canAccept(one.sessionId, one.fireId(), one.expiresAtMs))
        assertFalse(one.canAccept(one.sessionId, one.fireId(), fixedTime - 1L))
        assertFalse(one.copy(sourceAlarmId = -42).canAccept(one.sessionId, one.fireId(), fixedTime + 60_000L))
    }

    @Test fun oneSnoozeRequiresDistinctOccurrenceAndSameSession() {
        val one = ProductionTestSession.new(42, fixedTime)
        val snoozed = one.copy(nextOccurrence = 1)
        assertFalse(snoozed.canAccept(one.sessionId, one.fireId(0), fixedTime + 2 * 60_000L))
        assertTrue(snoozed.canAccept(one.sessionId, one.fireId(1), fixedTime + 2 * 60_000L))
        assertFalse(one.copy(nextOccurrence = 2).valid())
    }

    @Test fun testProofCannotSkipServiceAudioOrPretendSnooze() {
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.PREPARED, ProductionTestStage.REGISTERED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.REGISTERED, ProductionTestStage.AUDIO_STARTED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.SERVICE_ACKNOWLEDGED, ProductionTestStage.SNOOZE_REGISTERED))
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.SERVICE_ACKNOWLEDGED, ProductionTestStage.AUDIO_STARTED))
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.AUDIO_STARTED, ProductionTestStage.SNOOZE_REGISTERED))
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.SNOOZE_DELIVERED, ProductionTestStage.AUDIO_RESTARTED))
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.AUDIO_RESTARTED, ProductionTestStage.DISMISSED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.DISMISSED, ProductionTestStage.AUDIO_STARTED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.FAILED, ProductionTestStage.REGISTERED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.REGISTERED, ProductionTestStage.REGISTERED))
    }

    @Test fun cancellationAndFailureAreTerminalWithoutWritingProductionData() {
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.PREPARED, ProductionTestStage.CANCELLED))
        assertTrue(canAdvanceProductionTestStage(ProductionTestStage.SNOOZE_REGISTERED, ProductionTestStage.FAILED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.CANCELLED, ProductionTestStage.AUDIO_STARTED))
        assertFalse(canAdvanceProductionTestStage(ProductionTestStage.FAILED, ProductionTestStage.CANCELLED))
    }

    @Test fun invalidInputsCannotCreateOrUseSession() {
        assertFalse(ProductionTestSession.validId("../alarm/42"))
        assertFalse(ProductionTestSession.new(1, fixedTime).copy(sessionId = "").valid())
        assertFalse(ProductionTestSession.new(1, fixedTime).copy(expiresAtMs = fixedTime + 20 * 60_000L).valid())
        val noAlarm = runCatching { ProductionTestSession.new(0, fixedTime) }
        val longDelay = runCatching { ProductionTestSession.new(42, fixedTime, 90_000L) }
        assertTrue(noAlarm.isFailure)
        assertTrue(longDelay.isFailure)
    }
}
