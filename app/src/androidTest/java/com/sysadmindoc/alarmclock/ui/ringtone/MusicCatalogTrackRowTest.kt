package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicCatalogTrackRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixedLanguageTrackCanBeSelectedAndPreviewStaysDisabledWithoutController() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val track = MusicTrack(
            uri = "content://media/audio/1",
            title = "周杰倫 - 晴天 / 夏の雨 Home",
            source = MusicLibraryIndexer.SOURCE_MEDIA_STORE,
            fileName = "周杰倫 - 晴天 / 夏の雨 Home.mp3",
            metadataTitle = "夏の雨"
        )
        val item = MusicPoolUiTrack(
            track = track,
            membershipState = null,
            sourceName = "Device Music"
        )
        var selected = false
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    MusicCatalogTrackRow(
                        item = item,
                        selected = false,
                        selectionEnabled = true,
                        membershipLabel = context.getString(R.string.music_library_pool_status_candidate),
                        sourceFallback = context.getString(R.string.music_library_pool_device_source),
                        showPreview = true,
                        previewEnabled = false,
                        previewing = false,
                        onSelectionChange = { selected = true },
                        onPreview = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(track.displayTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("music-catalog-track-selection").performClick()
        composeRule.runOnIdle { assertTrue(selected) }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.music_library_preview)
        ).assertIsNotEnabled()
    }
}
