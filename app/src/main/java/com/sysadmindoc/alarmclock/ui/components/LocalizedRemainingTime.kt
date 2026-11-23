package com.sysadmindoc.alarmclock.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.util.AppLanguageManager
import java.util.Locale

/**
 * Keep the scheduler's compact d/h/m contract untouched; localize it only
 * at the display boundary so no English units leak into an otherwise
 * translated alarm hero. Never modifies alarm timestamps or persistence.
 */
internal object LocalizedRemainingTime {
    private val unitToken = Regex("""(\d+)([dhm])""")
    private val whitespace = Regex("""\s+""")

    fun format(context: Context, compact: String): String {
        val raw = compact.trim()
        if (raw == "<1m") return context.getString(R.string.s4_remaining_less_than_minute)
        val parts = unitToken.findAll(raw).toList()
        if (parts.isEmpty() || parts.joinToString(" ") { it.value } != raw.replace(whitespace, " ")) {
            return compact
        }
        return parts.joinToString(" ") { match ->
            val quantity = match.groupValues[1].toIntOrNull() ?: return compact
            val stringId = when (match.groupValues[2]) {
                "d" -> R.string.s4_remaining_days
                "h" -> R.string.s4_remaining_hours
                else -> R.string.s4_remaining_minutes
            }
            context.getString(stringId, quantity)
        }
    }
}

@Composable
fun bilingualNextAlarmWithDuration(compactDuration: String): String {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val primaryDuration = LocalizedRemainingTime.format(context, compactDuration)
    val primary = stringResource(R.string.alarmlist_next_alarm, primaryDuration)
    val options = LocalBilingualUiOptions.current.normalized()
    if (!options.enabled) return primary

    val currentTag = AppLanguageManager.currentOption(context).languageTag
        ?: currentConfiguration.locales.get(0)?.toLanguageTag().orEmpty()
    val normalizedPrimary = AppLanguageManager.optionForLanguageTags(currentTag).languageTag ?: "en"
    if (normalizedPrimary == options.secondaryTag) return primary
    val secondary = runCatching {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(options.secondaryTag))
        val secondaryContext = context.createConfigurationContext(config)
        secondaryContext.getString(
            R.string.alarmlist_next_alarm,
            LocalizedRemainingTime.format(secondaryContext, compactDuration)
        )
    }.getOrNull() ?: return primary
    return BilingualUiTextPolicy.combine(primary, secondary, normalizedPrimary, options)
}
