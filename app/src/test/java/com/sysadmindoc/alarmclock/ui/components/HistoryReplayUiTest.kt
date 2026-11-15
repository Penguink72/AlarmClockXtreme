package com.sysadmindoc.alarmclock.ui.components

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.service.ReplayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HistoryReplayUiTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun humanReadableReplayErrorMapsAlarmActive() {
        val error = ReplayUiState.Error(1L, "ALARM_ACTIVE")
        val message = humanReadableReplayError(context, error)
        assertEquals(context.getString(R.string.replay_error_alarm_active), message)
    }

    @Test
    fun humanReadableReplayErrorMapsHistoryNotFound() {
        val error = ReplayUiState.Error(2L, "HISTORY_NOT_FOUND")
        val message = humanReadableReplayError(context, error)
        assertEquals(context.getString(R.string.replay_error_history_not_found), message)
    }

    @Test
    fun humanReadableReplayErrorMapsPlaybackException() {
        val error = ReplayUiState.Error(3L, "ERROR_CODE_IO_FILE_NOT_FOUND: file not found")
        val message = humanReadableReplayError(context, error)
        assertEquals(context.getString(R.string.replay_error_playback_failed), message)
    }

    @Test
    fun humanReadableReplayErrorHandlesCleanupSuffix() {
        val error = ReplayUiState.Error(4L, "ALARM_ACTIVE; cleanup: Player release failed")
        val message = humanReadableReplayError(context, error)
        assertEquals(context.getString(R.string.replay_error_alarm_active), message)
    }

    @Test
    fun resolveMimeTypeIdentifiesAudioAndVideo() {
        val audioUri = Uri.parse("content://media/external/audio/media/123")
        val audioMime = resolveMimeType(context, audioUri, "song.mp3")
        assertTrue(audioMime.startsWith("audio/"))

        val videoUri = Uri.parse("content://media/external/video/media/456")
        val videoMime = resolveMimeType(context, videoUri, "clip.flv")
        assertTrue(videoMime.startsWith("video/"))

        val mp4Mime = resolveMimeType(context, Uri.parse("content://media/video/789"), "sample.mp4")
        assertTrue(mp4Mime.startsWith("video/"))
    }

    @Test
    fun formatPlaybackTimeReturnsFormattedString() {
        val formatted = formatPlaybackTime(context, 1700000000000L)
        assertNotNull(formatted)
        assertTrue(formatted.isNotBlank())
    }

    @Test
    fun formatPlaybackTimeHandlesZeroOrNegative() {
        assertEquals("", formatPlaybackTime(context, 0L))
        assertEquals("", formatPlaybackTime(context, -1L))
    }
}
