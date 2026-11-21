package com.sysadmindoc.alarmclock.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Lp2IndirectPreviewTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val indirect = linkedMapOf(
        R.string.settings_pane_readiness to "settings_pane_readiness",
        R.string.settings_pane_defaults_description to "settings_pane_defaults_description",
        R.string.settings_pane_integrations_description to "settings_pane_integrations_description",
        R.string.settings_pane_personalization_description to "settings_pane_personalization_description",
        R.string.settings_pane_backup_description to "settings_pane_backup_description",
        R.string.settings_pane_utilities_description to "settings_pane_utilities_description",
        R.string.settings_language_description to "settings_language_description",
        R.string.settings_battery_protected to "settings_battery_protected",
        R.string.settings_battery_setup_needed to "settings_battery_setup_needed",
        R.string.settings_setup_needed to "settings_setup_needed",
        R.string.alarm_edit_page_integrations to "alarm_edit_page_integrations",
        R.string.alarm_edit_section_group_description to "alarm_edit_section_group_description",
        R.string.alarm_edit_anti_snooze to "alarm_edit_anti_snooze",
        R.string.alarm_edit_section_anti_snooze_description to "alarm_edit_section_anti_snooze_description",
        R.string.alarm_edit_smart_alarm to "alarm_edit_smart_alarm",
        R.string.alarm_edit_section_hue_description to "alarm_edit_section_hue_description",
        R.string.alarm_edit_24_hour to "alarm_edit_24_hour",
        R.string.onboarding_see_weather_calendar_next_alarm to "onboarding_see_weather_calendar_next_alarm",
        R.string.music_library_pool_clear_selection to "music_library_pool_clear_selection",
        R.string.music_library_catalog_empty to "music_library_catalog_empty",
        R.string.music_library_preview to "music_library_preview",
        R.string.settings_show_incident_events to "settings_show_incident_events"
    )

    @Test
    fun all35NewKeysBelongToCurrentG6WithoutUnresolvedPlaceholders() {
        val catalog = Lp2PreviewText.catalog(context)
        assertEquals("g6-200-current-20261120", catalog.revision)
        assertEquals(200, catalog.entries.size)
        assertEquals(13, Lp2PreviewText.b3DirectKeys.size)
        assertEquals(22, Lp2PreviewText.b3IndirectKeys.size)
        assertEquals(122, Lp2PreviewText.previewKeys.size)
        assertEquals(87, (Lp2PreviewText.pilotKeys +
            Lp2PreviewText.crossScreenKeys + Lp2PreviewText.extendedKeys).size)
        val added = Lp2PreviewText.b3DirectKeys + Lp2PreviewText.b3IndirectKeys
        assertEquals(35, added.size)
        for (key in added) {
            assertNotNull("Missing canonical source: $key", catalog.entries[key])
            assertTrue("B3 key needs format substitution: $key",
                catalog.entries.getValue(key).placeholders.isEmpty())
        }
        assertEquals(21, catalog.entries.values.count { it.placeholders.isNotEmpty() })
    }

    @Test
    fun resourceIdsResolveExactSourceKeyAndUnknownResourceIsNotOverridden() {
        assertEquals(22, indirect.size)
        assertEquals(22, indirect.values.toSet().size)
        for ((id, key) in indirect) {
            assertEquals("Incorrect indirect mapping for $key",
                key, Lp2PreviewText.b3KeyForResource(id))
            assertTrue(Lp2PreviewText.b3IndirectKeys.contains(key))
        }
        assertNull(Lp2PreviewText.b3KeyForResource(R.string.settings_language_android_required))
        assertNull(Lp2PreviewText.b3KeyForResource(R.string.firing_controls_title))
        assertNull(Lp2PreviewText.b3KeyForResource(R.string.settings_title))
    }

    @Test
    fun newDirectAndIndirectCandidatesCanBeReadFromUnreviewedDebugPacks() {
        val sample = listOf("settings_pane_readiness", "alarm_edit_section_hue_description",
            "music_library_catalog_empty", "settings_recent_deliveries",
            "alarm_edit_emergency_phone", "onboarding_see_weather_calendar_next_alarm")
        for (locale in listOf("en", "zh-TW", "ja")) {
            for (key in sample) {
                val result = Lp2PreviewText.resolveForDebug(key, locale, context)
                assertTrue("Missing preview candidate $locale/$key", result.value.isNotBlank())
                assertFalse("B3 preview unexpectedly fell back $locale/$key", result.fallback)
            }
        }
    }
}
