package com.sysadmindoc.alarmclock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A1: corrupted/imported minute values cannot produce past or overflowed OS targets. */
class SnoozeIntervalSafetyTest {
    @Test
    fun normalDurationsPreserveExactMinuteBehavior() {
        assertEquals(60_000L, safeSnoozeDelayMillis(1))
        assertEquals(600_000L, safeSnoozeDelayMillis(10))
        assertEquals(10_800_000L, safeSnoozeDelayMillis(180))
    }

    @Test
    fun zeroAndNegativeDurationsAreAlwaysAtLeastOneMinute() {
        assertEquals(60_000L, safeSnoozeDelayMillis(0))
        assertEquals(60_000L, safeSnoozeDelayMillis(-10))
        assertEquals(60_000L, safeSnoozeDelayMillis(Int.MIN_VALUE))
    }

    @Test
    fun extremeDurationsUseLongArithmeticAfterClamping() {
        assertEquals(10_800_000L, safeSnoozeDelayMillis(181))
        assertEquals(10_800_000L, safeSnoozeDelayMillis(Int.MAX_VALUE))
        assertTrue(safeSnoozeDelayMillis(Int.MAX_VALUE) > 0L)
    }
}
