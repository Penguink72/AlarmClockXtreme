package com.sysadmindoc.alarmclock.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSkipPolicyTest {

    @Test
    fun maxConsecutiveFailuresIsFive() {
        assertEquals(5, MusicSkipPolicy.MAX_CONSECUTIVE_FAILURES)
    }

    @Test
    fun shouldFallBackOnlyWhenFailureCountReachesOrExceedsMax() {
        assertFalse(MusicSkipPolicy.shouldFallBack(0))
        assertFalse(MusicSkipPolicy.shouldFallBack(1))
        assertFalse(MusicSkipPolicy.shouldFallBack(4))

        assertTrue(MusicSkipPolicy.shouldFallBack(5))
        assertTrue(MusicSkipPolicy.shouldFallBack(6))
        assertTrue(MusicSkipPolicy.shouldFallBack(100))
    }
}
