package com.sysadmindoc.alarmclock.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeColdStartPolicyTest {
    @Test fun rejectedColdSnoozeStopsOnlyItsOwnService() {
        assertTrue(shouldStopRejectedColdSnooze(true, 42, "fire-1", 42, "fire-1", false))
        assertFalse(shouldStopRejectedColdSnooze(false, 42, "fire-1", 42, "fire-1", false))
    }

    @Test fun acceptedColdSnoozeIsCleanedByNormalSnoozeCompletion() {
        assertFalse(shouldStopRejectedColdSnooze(true, 42, "fire-1", 42, "fire-1", true))
    }

    @Test fun newAlarmIsNotInterruptedByLateRejectedSnooze() {
        assertFalse(shouldStopRejectedColdSnooze(true, 42, "fire-1", 43, "fire-2", false))
        assertFalse(shouldStopRejectedColdSnooze(true, 42, "fire-1", 42, "new-fire", false))
    }

    @Test fun invalidRequestNeverClosesAService() {
        assertFalse(shouldStopRejectedColdSnooze(true, 0, "fire-1", 0, "fire-1", false))
        assertFalse(shouldStopRejectedColdSnooze(true, 42, "", 42, "", false))
    }
}
