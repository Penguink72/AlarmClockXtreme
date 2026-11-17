package com.sysadmindoc.alarmclock.service

import android.app.Application
import android.media.MediaPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * U4 prerequisite: a captured seek position must never be an invented
 * success value when the underlying platform media player cannot report one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AlarmPlaybackPositionTest {
    @After fun tearDown() = unmockkAll()

    @Test
    fun reportsCurrentPositionWhenAvailable() {
        val mediaPlayer = mockk<MediaPlayer>()
        every { mediaPlayer.currentPosition } returns 83_142
        assertEquals(83_142L, MediaPlayerAlarmPlaybackPlayer(mediaPlayer).positionMs())
    }

    @Test
    fun invalidOrThrownTimestampIsUnknownRatherThanZero() {
        val mediaPlayer = mockk<MediaPlayer>()
        every { mediaPlayer.currentPosition } returns -1
        assertNull(MediaPlayerAlarmPlaybackPlayer(mediaPlayer).positionMs())
        every { mediaPlayer.currentPosition } throws IllegalStateException("released")
        assertNull(MediaPlayerAlarmPlaybackPlayer(mediaPlayer).positionMs())
    }
}
