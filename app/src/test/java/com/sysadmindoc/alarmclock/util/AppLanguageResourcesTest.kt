package com.sysadmindoc.alarmclock.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLanguageResourcesTest {

    @Test
    fun settingsMusicAndNavigationLabelsFollowTheSelectedLocale() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        data class Labels(
            val localeTag: String,
            val settings: String,
            val defaults: String,
            val musicLibrary: String,
            val musicPool: String,
            val appLanguage: String,
            val stopwatch: String
        )
        val cases = listOf(
            Labels(
                "en", "Settings", "Defaults",
                "Music library and playback history", "Music pool members",
                "App language", "Stopwatch"
            ),
            Labels(
                "zh-TW", "設定", "預設設定",
                "音樂庫與播放歷史", "音樂池曲目",
                "應用程式語言", "碼錶"
            ),
            Labels(
                "ja", "設定", "初期設定",
                "音楽ライブラリと再生履歴", "音楽プールの曲",
                "アプリの言語", "ストップウォッチ"
            )
        )
        cases.forEach { expected ->
            val configuration = Configuration(appContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(expected.localeTag))
            }
            val localized = appContext.createConfigurationContext(configuration)
            assertEquals(expected.localeTag, expected.settings, localized.getString(R.string.settings_title))
            assertEquals(expected.localeTag, expected.defaults, localized.getString(R.string.settings_pane_defaults))
            assertEquals(expected.localeTag, expected.musicLibrary, localized.getString(R.string.music_library_title))
            assertEquals(expected.localeTag, expected.musicPool, localized.getString(R.string.music_library_pool_title))
            assertEquals(expected.localeTag, expected.appLanguage, localized.getString(R.string.settings_language))
            assertEquals(expected.localeTag, expected.stopwatch, localized.getString(R.string.nav_stopwatch))
        }
    }

    @Test
    fun trilingualResourcesCoverAllMusicAndLanguageSurfacesWithConsistentFormatSpecifiers() {
        val enMap = parseStringResources(resolveResFile("values/strings.xml"))
        val zhMap = parseStringResources(resolveResFile("values-b+zh+Hant+TW/strings.xml"))
        val jaMap = parseStringResources(resolveResFile("values-ja/strings.xml"))

        assertTrue("English strings must be populated", enMap.size > 1000)
        assertTrue("Traditional Chinese strings must have core translations", zhMap.size >= 400)
        assertTrue("Japanese strings must have core translations", jaMap.size >= 400)

        val requiredSurfaceKeys = listOf(
            // Surface 1: Music library main dialog
            "music_library_title", "music_library_button", "music_library_media_counts",
            "music_library_scan", "music_library_scanning", "music_library_sources_button",
            "music_library_pool_manage", "music_library_choose_date", "music_library_no_history",
            "music_library_close",

            // Surface 2: Manage folders & rescan
            "music_library_sources_title", "music_library_add_folder", "music_library_source_management_hint",
            "music_library_no_sources", "music_library_source_default_name", "music_library_source_origin",
            "music_library_source_counts", "music_library_source_ready", "music_library_source_needs_scan",
            "music_library_source_error", "music_library_source_unknown_origin", "music_library_source_phone",
            "music_library_source_removable", "music_library_rescan", "music_library_scan_all_sources",
            "music_library_choose_subfolders", "music_library_remove_source", "music_library_no_subfolders",
            "music_library_add_selected_folders", "music_library_video_without_audio", "music_library_audio_unplayable",
            "music_library_duplicate_location_note", "music_library_sources_read_failed", "music_library_source_remove_failed",
            "music_library_remove_needs_rescan", "music_library_subfolders_failed", "music_library_pool_add_folder",
            "music_library_pool_folder_added",

            // Surface 3: Audio/video candidate list & filter chips
            "music_library_pool_title", "music_library_pool_search", "music_library_pool_all_candidates",
            "music_library_pool_included", "music_library_pool_excluded", "music_library_pool_source_label",
            "music_library_pool_scope_folders", "music_library_pool_device_source", "music_library_pool_type_audio",
            "music_library_pool_type_video_audio",

            // Surface 4: Added / Excluded status labels
            "music_library_pool_status_candidate", "music_library_pool_status_included", "music_library_pool_status_excluded",

            // Surface 5: Search, select visible/scope, batch add, exclude, restore
            "music_library_pool_results", "music_library_pool_scope_summary", "music_library_pool_select_visible",
            "music_library_pool_clear_selection", "music_library_pool_add", "music_library_pool_exclude",
            "music_library_pool_restore",

            // Surface 6: Pool empty states, scope hints, error messages
            "music_library_pool_empty", "music_library_pool_device_warning", "music_library_pool_non_destructive_hint",
            "music_library_pool_read_failed", "music_library_pool_update_failed", "music_library_scanning_status",
            "music_library_scan_found_count", "music_library_permission_denied", "music_library_mediastore_failed",
            "music_library_folder_failed", "music_library_scan_failed", "music_library_index_read_failed",
            "music_library_folder_access_failed",

            // Surface 7: Random music alarm settings
            "alarm_edit_random_music_library", "alarm_edit_random_music_library_hint",

            // Surface 8: Playback history & track display
            "music_library_history_row", "music_library_history_read_failed",

            // Surface 9: Settings language entry
            "settings_language", "settings_language_description", "settings_language_android_required",
            "settings_language_dialog_title", "settings_language_system_default", "settings_language_english",
            "settings_language_traditional_chinese", "settings_language_japanese"
        )

        for (key in requiredSurfaceKeys) {
            val enVal = enMap[key]
            assertNotNull("Missing English key: $key", enVal)
            assertFalse("English key $key must not be blank", enVal!!.isBlank())

            val zhVal = zhMap[key]
            assertNotNull("Missing Traditional Chinese key: $key", zhVal)
            assertFalse("Traditional Chinese key $key must not be blank", zhVal!!.isBlank())

            val jaVal = jaMap[key]
            assertNotNull("Missing Japanese key: $key", jaVal)
            assertFalse("Japanese key $key must not be blank", jaVal!!.isBlank())

            val enSpecifiers = extractFormatSpecifiers(enVal)
            val zhSpecifiers = extractFormatSpecifiers(zhVal)
            val jaSpecifiers = extractFormatSpecifiers(jaVal)

            assertEquals("Format specifiers mismatch in zh-TW for key $key", enSpecifiers, zhSpecifiers)
            assertEquals("Format specifiers mismatch in ja for key $key", enSpecifiers, jaSpecifiers)
        }
    }

    private fun resolveResFile(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/res/$relativePath"),
            File("src/main/res/$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Resource file not found: $relativePath")
    }

    private fun parseStringResources(file: File): Map<String, String> {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = false
        val doc = dbf.newDocumentBuilder().parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>()
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            if (name.isNotBlank()) {
                val value = element.textContent
                val existing = map.put(name, value)
                assertFalse("Duplicate string key '$name' in ${file.path}", existing != null)
            }
        }
        return map
    }

    private fun extractFormatSpecifiers(text: String): List<String> {
        val pattern = Pattern.compile("%(?:[0-9]+\\$)?(?:[0-9]+)?(?:[.\\d]+)?[bBhHsScCdoxXeEfgGaAtT%]")
        val matcher = pattern.matcher(text)
        val specifiers = mutableListOf<String>()
        while (matcher.find()) {
            specifiers.add(matcher.group())
        }
        return specifiers.sorted()
    }
}
