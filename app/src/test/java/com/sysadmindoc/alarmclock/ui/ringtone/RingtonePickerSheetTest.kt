package com.sysadmindoc.alarmclock.ui.ringtone

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.sysadmindoc.alarmclock.ui.components.AppSectionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class RingtonePickerSheetTest {

    @Test
    fun enumerationWarningMentionsYouTubeOnlyWhenAvailable() {
        assertTrue(ringtoneEnumerationWarning(youTubeAvailable = true).contains("YouTube"))
        assertFalse(ringtoneEnumerationWarning(youTubeAvailable = false).contains("YouTube"))
    }

    @Test
    fun nestedHorizontalScrollInAppSectionTitleThrowsIllegalStateException() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            MaterialTheme {
                AppSectionTitle(
                    title = "Alarm sound",
                    description = "Preview tones",
                    action = {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text("Music library")
                        }
                    }
                )
            }
        }

        val error = assertThrows(IllegalStateException::class.java) {
            ShadowLooper.idleMainLooper()
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST)
            )
            composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
        }
        assertTrue(
            "Expected Compose nested horizontalScroll constraint failure, got: ${error.message}",
            error.message.orEmpty().contains("Infinity", ignoreCase = true) ||
                error.message.orEmpty().contains("scrollable", ignoreCase = true)
        )
    }

    @Test
    fun ringtonePickerHeaderMeasuresAndLaysOutWithoutCrash() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            MaterialTheme {
                RingtonePickerHeader(
                    youTubeAvailable = true,
                    onOpenMusicLibrary = {},
                    onOpenFolderPicker = {},
                    onOpenYouTubeDialog = {}
                )
            }
        }

        ShadowLooper.idleMainLooper()
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST)
        )
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        assertTrue(composeView.measuredWidth > 0)
        assertTrue(composeView.measuredHeight > 0)
    }

    @Test
    fun deduplicateRingtoneItemsRemovesDuplicateBlankAndNullUrisAndPreventsKeyCollision() {
        val raw = listOf(
            RingtoneItem("Default Alarm", "", isDefault = true),
            RingtoneItem("Silent", "", isSilent = true),
            RingtoneItem("Morning", "content://media/internal/audio/media/10"),
            RingtoneItem("Morning duplicate", "content://media/internal/audio/media/10"),
            RingtoneItem("Broken null", "null"),
            RingtoneItem("Broken blank", "   "),
            RingtoneItem("Named default", "default"),
            RingtoneItem("Named silent", "silent")
        )

        val deduped = deduplicateRingtoneItems(raw)
        assertEquals(5, deduped.size)
        assertEquals("Morning", deduped[2].title)

        val keys = deduped.map(::ringtoneLazyKey)
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(
            listOf(
                "default",
                "silent",
                "uri:content://media/internal/audio/media/10",
                "uri:default",
                "uri:silent"
            ),
            keys
        )
    }
}
