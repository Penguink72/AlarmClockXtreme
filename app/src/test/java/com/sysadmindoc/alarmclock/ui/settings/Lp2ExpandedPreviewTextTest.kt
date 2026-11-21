package com.sysadmindoc.alarmclock.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Lp2ExpandedPreviewTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun newG6EditableScreenKeysArePresentWithoutFormatArguments() {
        val catalog = Lp2PreviewText.catalog(context)
        assertEquals("g6-200-current-20261120",catalog.revision)
        assertEquals(36,Lp2PreviewText.extendedKeys.size)
        assertEquals(122,Lp2PreviewText.previewKeys.size)
        assertEquals(87,(Lp2PreviewText.pilotKeys + Lp2PreviewText.crossScreenKeys + Lp2PreviewText.extendedKeys).size)
        for(key in Lp2PreviewText.extendedKeys) {
            assertTrue("Missing current source: "+key,catalog.entries.containsKey(key))
            assertTrue("Formatted B2 key requires separate resolver: "+key,
                catalog.entries.getValue(key).placeholders.isEmpty())
        }
    }

    @Test fun unreviewedG6CandidateIsAvailableOnMusicEditorAndStatisticsSurfaces() {
        for(locale in listOf("en","zh-TW","ja")) {
            for(key in listOf("alarm_edit_add_ringtone","music_library_add_folder",
                "stats_snoozed","alarm_edit_progressive_snooze","ringtone_preview_unavailable")) {
                val text=Lp2PreviewText.resolveForDebug(key,locale,context)
                assertTrue(text.value.isNotBlank())
                assertFalse(text.fallback)
            }
        }
    }

    @Test fun legacyPreviewRetainsDisabledReleaseFallbackState() {
        assertEquals(21,Lp2PreviewText.pilotKeys.size)
        assertEquals(31,Lp2PreviewText.crossScreenKeys.size)
        assertEquals(51,(Lp2PreviewText.pilotKeys+Lp2PreviewText.crossScreenKeys).size)
        assertTrue(Lp2PreviewText.previewKeys.containsAll(Lp2PreviewText.extendedKeys))
    }
}
