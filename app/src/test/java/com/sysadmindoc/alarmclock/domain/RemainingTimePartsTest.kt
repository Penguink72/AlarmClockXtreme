package com.sysadmindoc.alarmclock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Does not read the system clock, default locale or time zone.
 * A localized rendering layer receives only these numeric parts.
 */
class RemainingTimePartsTest {
    private val now = 1_800_000_000_000L

    @Test fun exactHoursAndMinutes() {
        assertEquals(
            RemainingTimeParts(0, 18, 56, due = false, underMinute = false),
            RemainingTimeParts.between(now + (18 * 60 + 56) * 60_000L, now)
        )
    }

    @Test fun crossingMidnightIsElapsedDurationNotWallClockDate() {
        assertEquals(
            RemainingTimeParts(1, 2, 3, due = false, underMinute = false),
            RemainingTimeParts.between(now + (26 * 60 + 3) * 60_000L, now)
        )
    }

    @Test fun underOneMinuteNeverClaimsZeroMinutes() {
        val projection = RemainingTimeParts.between(now + 59_999L, now)
        assertTrue(projection.underMinute)
        assertFalse(projection.due)
        assertEquals(0L, projection.minutes)
    }

    @Test fun atAndPastTriggerAreDue() {
        assertTrue(RemainingTimeParts.between(now, now).due)
        assertTrue(RemainingTimeParts.between(now - 1L, now).due)
    }

    @Test fun exactDayOrMinuteBoundariesUseNumbersWithoutImplicitClock() {
        assertEquals(
            RemainingTimeParts(1, 0, 0, false, false),
            RemainingTimeParts.between(now + 86_400_000L, now)
        )
        assertEquals(
            RemainingTimeParts(0, 0, 1, false, false),
            RemainingTimeParts.between(now + 60_000L, now)
        )
    }
}
