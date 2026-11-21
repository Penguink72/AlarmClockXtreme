package com.sysadmindoc.alarmclock.ui.settings

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.alarmclock.BuildConfig
import com.sysadmindoc.alarmclock.R
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * LP2-A/B1/B2/B3: an opt-in Debug-only preview for a governed G6 catalog.
 * B3 adds 13 direct and 22 resource-ID-indirected UI keys (122 total).
 * Non-allowlisted UI and every release build use Android stringResource().
 * Alarm-firing screens, notifications, scheduling and services are excluded.
 * LP1 experimental assets are untouched; LP2 requires the current G6 revision.
 */
internal object Lp2PreviewText {
    private const val ASSETS = "lp2"
    private const val STORAGE = "lp2-experimental-packs"
    private const val PREFS = "lp2-settings-debug-preview"
    private const val LIMIT = 256 * 1024
    private val builtIns = listOf("en", "zh-TW", "ja")
    private val tagPattern = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$")
    private val printf = Regex("%(?:\\d+\\$)?[-+# 0,(]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtT%]")

    val pilotKeys: Set<String> = setOf(
        "nav_stopwatch",
        "settings_title",
        "settings_share_support_bundle",
        "settings_choose_group_hint",
        "settings_not_selected",
        "settings_alarm_defaults_description",
        "settings_flip_snooze",
        "settings_default_snooze_description",
        "settings_dashboard_description",
        "settings_post_dismiss_summary_description",
        "settings_commute_history_description",
        "settings_show_today_tab",
        "settings_utilities_description",
        "settings_alarm_statistics",
        "settings_stopwatch_description",
        "settings_language_dialog_title",
        "settings_language_english",
        "settings_language_japanese",
        "settings_license",
        "settings_readiness_exact_alarms",
        "settings_return_to_app_after_dismiss_description"
    )

    val crossScreenKeys: Set<String> = setOf(
        "settings_close",
        "alarm_list_clear_filters",
        "alarm_edit_disabled",
        "alarm_list_delete",
        "settings_ready",
        "alarm_list_pause",
        "settings_not_selected",
        "alarm_edit_background_selected",
        "alarm_edit_background_permission_warning",
        "alarm_edit_saving",
        "music_library_title",
        "music_library_history_title",
        "music_library_system_tones_cancel",
        "music_library_category_music",
        "music_library_source_scope_all",
        "music_library_pool_all_candidates",
        "music_library_pool_status_included",
        "music_library_history_filter_recent",
        "music_library_no_history",
        "recent_playback_title",
        "recent_playback_empty",
        "replay_status_playing",
        "replay_action_pause",
        "replay_open_in_external",
        "alarm_music_settings_playlist_smart_all",
        "u4_snooze_mode_note",
        "music_playlist_integration_pending",
        "music_playlist_apply_to_alarm",
        "music_playlist_empty",
        "music_playlist_rename",
        "music_playlist_remove"
    )
    val extendedKeys: Set<String> = setOf(
        "alarm_edit_add_ringtone",
        "alarm_edit_add_ringtone_accessibility",
        "alarm_edit_add_ringtone_title",
        "alarm_edit_capture_photo_hint",
        "alarm_edit_challenge_chain",
        "alarm_edit_delivery_test_limitation",
        "alarm_edit_heartbeat",
        "alarm_edit_immediately_default",
        "alarm_edit_progressive_snooze",
        "alarm_edit_progressive_snooze_hint",
        "alarm_edit_ringtone_duplicate",
        "alarm_edit_ringtone_uri_hint",
        "alarm_edit_shift_pattern",
        "alarm_edit_snooze_limit_unlimited",
        "alarm_edit_squats_required",
        "b2_test_cancel",
        "b2_test_saved_only",
        "music_library_add_folder",
        "music_library_close",
        "music_library_pool_source_label",
        "music_library_remove_source",
        "music_library_source_unknown_origin",
        "replay_action_stop",
        "ringtone_folder",
        "ringtone_preview_unavailable",
        "settings_cover_to_snooze_description",
        "settings_export_incident_diagnostics",
        "settings_long_press_threshold_description",
        "settings_on_call_mode_description",
        "snooze_duration",
        "stats_alarm_history_optional_health_connect",
        "stats_compares_recent_sleep_duration_with_dismiss",
        "stats_recent_health_connect_sessions_stay_local",
        "stats_recent_history",
        "stats_snoozed",
        "stats_track_consistency_snooze_behavior_and_which"
    )
    val b3DirectKeys: Set<String> = setOf(
        "alarm_edit_default_ringtone_placeholder",
        "alarm_edit_emergency_phone",
        "alarm_edit_group_custom_name",
        "alarm_edit_holidays_hint",
        "settings_backup_locked_description",
        "settings_webhook_description",
        "settings_recent_deliveries",
        "settings_hue_api_placeholder",
        "settings_health_no_session",
        "settings_connection_sunrise_skipped",
        "bedtime_update_health_connect_before_recent_sleep",
        "alarm_edit_delete_digit",
        "permissions_context_permissions"
    )
    val b3IndirectKeys: Set<String> = setOf(
        "settings_pane_readiness",
        "settings_pane_defaults_description",
        "settings_pane_integrations_description",
        "settings_pane_personalization_description",
        "settings_pane_backup_description",
        "settings_pane_utilities_description",
        "settings_language_description",
        "settings_battery_protected",
        "settings_battery_setup_needed",
        "settings_setup_needed",
        "alarm_edit_page_integrations",
        "alarm_edit_section_group_description",
        "alarm_edit_anti_snooze",
        "alarm_edit_section_anti_snooze_description",
        "alarm_edit_smart_alarm",
        "alarm_edit_section_hue_description",
        "alarm_edit_24_hour",
        "onboarding_see_weather_calendar_next_alarm",
        "music_library_pool_clear_selection",
        "music_library_catalog_empty",
        "music_library_preview",
        "settings_show_incident_events"
    )
    val previewKeys: Set<String> = pilotKeys + crossScreenKeys + extendedKeys + b3DirectKeys + b3IndirectKeys

    data class Entry(val source: String, val placeholders: List<String>)
    data class Catalog(val revision: String, val entries: Map<String, Entry>)
    data class Pack(val locale: String, val values: Map<String, String>, val version: String)
    data class Options(val enabled: Boolean, val primary: String, val secondary: String, val dual: Boolean)
    data class Resolved(val value: String, val fallback: Boolean)
    private data class Session(val catalog: Catalog, val options: Options, val primary: Pack, val secondary: Pack)

    private var session by mutableStateOf<Session?>(null)
    private var lastLoadError by mutableStateOf<String?>(null)

    fun isEnabled(): Boolean = BuildConfig.DEBUG && session != null
    fun lastError(): String? = lastLoadError
    fun showsTwoLanguages(): Boolean {
        val state = session ?: return false
        return BuildConfig.DEBUG && state.options.dual &&
            state.options.primary != state.options.secondary
    }

    private fun placeholders(text: String): List<String> =
        printf.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    private fun readBounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size() + n <= LIMIT) { "LP2 JSON exceeds 256 KiB" }
            output.write(buffer, 0, n)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun catalog(context: Context): Catalog {
        require(BuildConfig.DEBUG) { "LP2 preview is Debug-only" }
        val data = JSONObject(context.assets.open(ASSETS + "/catalog.json").use(::readBounded))
        require(data.getString("format") == "acx-language-catalog-probe/1")
        require(data.getString("revision") == "g6-200-current-20261120") { "Wrong G6 catalog" }
        val array = data.getJSONArray("entries")
        require(array.length() == 200) { "G6 catalog requires 200 keys" }
        val entries = linkedMapOf<String, Entry>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val key = item.getString("key")
            val tokens = item.getJSONArray("placeholders")
            val expected = (0 until tokens.length()).map { tokens.getString(it) }
            val source = item.getString("source")
            require(!entries.containsKey(key)) { "Duplicate key" }
            require(placeholders(source) == expected) { "Invalid source placeholders: " + key }
            entries[key] = Entry(source, expected)
        }
        require(pilotKeys.size == 21 && crossScreenKeys.size == 31 && extendedKeys.size == 36 && b3DirectKeys.size == 13 && b3IndirectKeys.size == 22 && previewKeys.size == 122) {
            "Unexpected LP2-A/B1 preview coverage"
        }
        require(entries.keys.containsAll(previewKeys)) { "Missing LP2 preview keys" }
        require(previewKeys.all { entries.getValue(it).placeholders.isEmpty() }) {
            "Preview cannot format parameterized keys"
        }
        return Catalog(data.getString("revision"), entries)
    }

    fun parsePack(raw: String, catalog: Catalog): Pack {
        require(BuildConfig.DEBUG) { "LP2 preview is Debug-only" }
        require(raw.toByteArray(Charsets.UTF_8).size <= LIMIT) { "File exceeds 256 KiB" }
        val data = JSONObject(raw)
        require(data.getString("format") == "acx-language-pack-probe/1") { "Invalid pack format" }
        require(data.getString("catalogRevision") == catalog.revision) { "G6 catalog revision mismatch" }
        val locale = data.getString("locale")
        require(locale.length <= 30 && tagPattern.matches(locale) &&
            Locale.forLanguageTag(locale).language.isNotBlank()) { "Invalid locale" }
        val translations = data.getJSONObject("translations")
        require(translations.length() <= 200) { "Too many keys" }
        val values = linkedMapOf<String, String>()
        val names = translations.keys()
        while (names.hasNext()) {
            val key = names.next()
            val entry = catalog.entries[key] ?: error("Unknown key: " + key)
            val value = translations.get(key)
            require(value is String && value.length <= 4096) { "Invalid translation: " + key }
            if (value.isNotBlank()) {
                require(placeholders(value) == entry.placeholders) { "Printf mismatch: " + key }
                values[key] = value
            }
        }
        return Pack(locale, values, data.optString("packVersion", "manual"))
    }

    private fun packFile(context: Context, locale: String): File =
        File(File(context.filesDir, STORAGE), locale + ".json")

    private fun pack(context: Context, catalog: Catalog, locale: String): Pack {
        require(locale.length <= 30 && tagPattern.matches(locale)) { "Invalid locale" }
        val custom = packFile(context, locale)
        val raw = if (custom.isFile) {
            AtomicFile(custom).openRead().use(::readBounded)
        } else {
            require(locale in builtIns) { "Missing installed pack: " + locale }
            context.assets.open(ASSETS + "/packs/" + locale + ".json").use(::readBounded)
        }
        return parsePack(raw, catalog).also { require(it.locale == locale) { "Pack locale mismatch" } }
    }

    fun available(context: Context): Map<String, Int> {
        if (!BuildConfig.DEBUG) return emptyMap()
        val catalog = catalog(context)
        val locales = (builtIns + (File(context.filesDir, STORAGE).listFiles()
            ?.mapNotNull { file -> file.name.takeIf { file.isFile && it.endsWith(".json") }
                ?.removeSuffix(".json") } ?: emptyList())).distinct()
        return locales.mapNotNull { locale ->
            runCatching { locale to pack(context, catalog, locale).values.size }.getOrNull()
        }.toMap()
    }

    fun importFromStream(context: Context, stream: InputStream): Pack =
        stream.use { importPack(context, readBounded(it)) }

    fun importPack(context: Context, raw: String): Pack {
        require(BuildConfig.DEBUG) { "LP2 preview is Debug-only" }
        val catalog = catalog(context)
        val parsed = parsePack(raw, catalog)
        val dir = File(context.filesDir, STORAGE)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create LP2 storage" }
        val file = AtomicFile(packFile(context, parsed.locale))
        val stream = file.startWrite()
        try {
            stream.write(raw.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        refresh(context)
        return parsed
    }

    fun options(context: Context): Options {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Options(
            enabled = prefs.getBoolean("enabled", false),
            primary = prefs.getString("primary", "zh-TW") ?: "zh-TW",
            secondary = prefs.getString("secondary", "ja") ?: "ja",
            dual = prefs.getBoolean("dual", true)
        )
    }

    fun configure(context: Context, settings: Options): Boolean {
        require(BuildConfig.DEBUG) { "LP2 preview is Debug-only" }
        require(settings.primary.length <= 30 && tagPattern.matches(settings.primary))
        require(settings.secondary.length <= 30 && tagPattern.matches(settings.secondary))
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putString("primary", settings.primary)
            .putString("secondary", settings.secondary)
            .putBoolean("dual", settings.dual)
            .commit()
        check(saved) { "Cannot persist LP2 mode" }
        return refresh(context)
    }

    /** Called only while displaying Settings; no alarm startup or service work. */
    fun refresh(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            session = null
            return false
        }
        val opts = options(context)
        if (!opts.enabled) {
            session = null
            lastLoadError = null
            return true
        }
        val loaded = runCatching {
            val catalog = catalog(context)
            Session(catalog, opts, pack(context, catalog, opts.primary), pack(context, catalog, opts.secondary))
        }
        session = loaded.getOrNull()
        lastLoadError = loaded.exceptionOrNull()?.message
        return loaded.isSuccess
    }

    fun resolveForDebug(key: String, locale: String, context: Context): Resolved {
        val catalog = catalog(context)
        val entry = requireNotNull(catalog.entries[key]) { "Unrecognized key: " + key }
        val value = pack(context, catalog, locale).values[key]
        return if (value.isNullOrBlank()) Resolved(entry.source, fallback = locale != "en")
        else Resolved(value, fallback = false)
    }

    /** The sole audited resource-ID mapping for B3; non-matches are Android-native. */
    fun b3KeyForResource(@StringRes resId: Int): String? = when (resId) {
            R.string.settings_pane_readiness -> "settings_pane_readiness"
            R.string.settings_pane_defaults_description -> "settings_pane_defaults_description"
            R.string.settings_pane_integrations_description -> "settings_pane_integrations_description"
            R.string.settings_pane_personalization_description -> "settings_pane_personalization_description"
            R.string.settings_pane_backup_description -> "settings_pane_backup_description"
            R.string.settings_pane_utilities_description -> "settings_pane_utilities_description"
            R.string.settings_language_description -> "settings_language_description"
            R.string.settings_battery_protected -> "settings_battery_protected"
            R.string.settings_battery_setup_needed -> "settings_battery_setup_needed"
            R.string.settings_setup_needed -> "settings_setup_needed"
            R.string.alarm_edit_page_integrations -> "alarm_edit_page_integrations"
            R.string.alarm_edit_section_group_description -> "alarm_edit_section_group_description"
            R.string.alarm_edit_anti_snooze -> "alarm_edit_anti_snooze"
            R.string.alarm_edit_section_anti_snooze_description -> "alarm_edit_section_anti_snooze_description"
            R.string.alarm_edit_smart_alarm -> "alarm_edit_smart_alarm"
            R.string.alarm_edit_section_hue_description -> "alarm_edit_section_hue_description"
            R.string.alarm_edit_24_hour -> "alarm_edit_24_hour"
            R.string.onboarding_see_weather_calendar_next_alarm -> "onboarding_see_weather_calendar_next_alarm"
            R.string.music_library_pool_clear_selection -> "music_library_pool_clear_selection"
            R.string.music_library_catalog_empty -> "music_library_catalog_empty"
            R.string.music_library_preview -> "music_library_preview"
            R.string.settings_show_incident_events -> "settings_show_incident_events"
        else -> null
    }

    @Composable
    fun textForResource(@StringRes resId: Int): String {
        val key = b3KeyForResource(resId)
        return if (key == null) stringResource(resId) else text(resId, key)
    }

    @Composable
    fun text(@StringRes resId: Int, key: String): String {
        val native = stringResource(resId)
        val active = session
        if (!BuildConfig.DEBUG || active == null || key !in previewKeys) return native
        val entry = active.catalog.entries[key] ?: return native
        fun render(pack: Pack): String {
            val text = pack.values[key]
            return if (text.isNullOrBlank()) entry.source + " [EN fallback]" else text
        }
        val one = render(active.primary)
        return if (active.options.dual && active.options.primary != active.options.secondary)
            one + "\n" + render(active.secondary)
        else one
    }
}
