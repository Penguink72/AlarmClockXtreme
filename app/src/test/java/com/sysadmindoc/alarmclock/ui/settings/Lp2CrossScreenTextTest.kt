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
@Config(sdk=[34])
class Lp2CrossScreenTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun debugCrossScreenKeysHaveValidatedCurrentCatalogSources() {
        val catalog = Lp2PreviewText.catalog(context)
        assertEquals(200, catalog.entries.size)
        assertEquals(21, Lp2PreviewText.pilotKeys.size)
        assertEquals(31, Lp2PreviewText.crossScreenKeys.size)
        assertEquals(51, Lp2PreviewText.previewKeys.size)
        assertTrue(Lp2PreviewText.previewKeys.containsAll(Lp2PreviewText.pilotKeys))
        assertTrue(Lp2PreviewText.previewKeys.containsAll(Lp2PreviewText.crossScreenKeys))
        for(key in Lp2PreviewText.previewKeys) {
            assertTrue("Missing G6 source $key", catalog.entries.containsKey(key))
            assertTrue("Dynamic printf not yet supported for $key",
                catalog.entries.getValue(key).placeholders.isEmpty())
        }
    }

    @Test
    fun currentG6TranslatedMusicHistoryAndAlarmListTextIsAvailable() {
        val locales = listOf("en","zh-TW","ja")
        val keys = listOf(
            "alarm_list_delete",
            "music_library_title",
            "music_playlist_integration_pending",
            "recent_playback_title",
            "replay_action_pause",
            "u4_snooze_mode_note"
        )
        for(locale in locales)for(key in keys) {
            val resolved = Lp2PreviewText.resolveForDebug(key, locale, context)
            assertTrue("Missing G6 candidate $locale/$key", resolved.value.isNotBlank())
            assertFalse("Included G6 preview key fell back $locale/$key", resolved.fallback)
        }
    }

    @Test
    fun persistedModeCanRestoreOnResumeAndBeDisabledWithoutAlarmService() {
        val before = Lp2PreviewText.options(context)
        try {
            assertTrue(Lp2PreviewText.configure(context,
                Lp2PreviewText.Options(enabled=true,primary="ja",secondary="zh-TW",dual=true)))
            assertTrue(Lp2PreviewText.refresh(context))
            assertTrue(Lp2PreviewText.isEnabled())
            val saved=Lp2PreviewText.options(context)
            assertEquals("ja",saved.primary)
            assertEquals("zh-TW",saved.secondary)
            assertTrue(saved.dual)
            assertTrue(Lp2PreviewText.configure(context,saved.copy(enabled=false)))
            assertFalse(Lp2PreviewText.isEnabled())
        }finally{
            Lp2PreviewText.configure(context,before.copy(enabled=false))
        }
    }
}
