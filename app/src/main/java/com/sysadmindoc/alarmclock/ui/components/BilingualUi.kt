package com.sysadmindoc.alarmclock.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.util.AppLanguageManager
import com.sysadmindoc.alarmclock.util.AppLanguageOption
import java.util.Locale

/** User-facing S3 preferences only; never changes alarms, Media3, or global Locale. */
data class BilingualUiOptions(
    val enabled: Boolean = false,
    val secondaryTag: String = "ja"
) {
    fun normalized(): BilingualUiOptions = copy(
        secondaryTag = secondaryTag.takeIf { it in setOf("en", "zh-TW", "ja") } ?: "ja"
    )
}

object BilingualUiPreferences {
    private const val FILE = "acx_s3_bilingual_ui"
    private const val ENABLED = "dual_language_enabled"
    private const val SECONDARY = "secondary_language_tag"
    internal fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): BilingualUiOptions {
        val p = prefs(context)
        return BilingualUiOptions(
            enabled = p.getBoolean(ENABLED, false),
            secondaryTag = p.getString(SECONDARY, "ja").orEmpty()
        ).normalized()
    }

    fun save(context: Context, options: BilingualUiOptions) {
        val value = options.normalized()
        prefs(context).edit()
            .putBoolean(ENABLED, value.enabled)
            .putString(SECONDARY, value.secondaryTag)
            .apply()
    }
}

val LocalBilingualUiOptions = compositionLocalOf { BilingualUiOptions() }

/** SharedPreferences listener is scoped to the composition; changes update every visible screen. */
@Composable
fun BilingualLocalizationHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var state by remember(context) { mutableStateOf(BilingualUiPreferences.read(context)) }
    DisposableEffect(context) {
        val prefs = BilingualUiPreferences.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state = BilingualUiPreferences.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalBilingualUiOptions provides state.normalized(), content = content)
}

/**
 * Use only for visible UI text; raw values, persistence, file names, a11y control
 * identities, network payloads and notification protocol strings remain unchanged.
 */
@Composable
fun bilingualStringResource(@StringRes resId: Int, vararg args: Any): String {
    val primary = stringResource(resId, *args)
    val options = LocalBilingualUiOptions.current.normalized()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    if (!options.enabled) return primary
    val appPrimary = AppLanguageManager.currentOption(context)
    val effectivePrimary = appPrimary.languageTag
        ?: configuration.locales.get(0)?.toLanguageTag().orEmpty()
    val normalizedPrimary = AppLanguageManager.optionForLanguageTags(effectivePrimary).languageTag
        ?: "en"
    if (normalizedPrimary == options.secondaryTag) return primary
    val translated = runCatching {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(options.secondaryTag))
        val secondaryContext = context.createConfigurationContext(config)
        secondaryContext.getString(resId, *args)
    }.getOrNull() ?: return primary
    return if (translated.isBlank() || translated == primary) primary
        else primary + "\n" + translated
}

/** Two distinct text levels; primary is always action/screen identity. */
@Composable
fun BilingualLabel(
    @StringRes resId: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = 2
) {
    val text = bilingualStringResource(resId)
    val parts = text.split("\n", limit = 2)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = parts[0], style = style, color = color,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        if (parts.size > 1) {
            Text(text = parts[1], style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}
