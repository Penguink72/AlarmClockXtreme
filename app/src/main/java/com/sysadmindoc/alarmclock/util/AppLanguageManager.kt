package com.sysadmindoc.alarmclock.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

internal enum class AppLanguageOption(val languageTag: String?) {
    SYSTEM_DEFAULT(null),
    ENGLISH("en"),
    TRADITIONAL_CHINESE("zh-TW"),
    JAPANESE("ja")
}

internal object AppLanguageManager {
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // Android 13+ owns per-app locales. Below Android 13, a synchronous
    // device-local preference is needed at Activity.attachBaseContext time.
    private const val LEGACY_PREFS = "acx_s3_locale_settings"
    private const val LEGACY_PRIMARY = "primary_language"

    fun currentOption(context: Context): AppLanguageOption {
        if (isSupported()) return currentOptionApi33(context)
        val saved = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_PRIMARY, null)
        return AppLanguageOption.values().firstOrNull { it.name == saved }
            ?: AppLanguageOption.SYSTEM_DEFAULT
    }

    fun setOption(context: Context, option: AppLanguageOption) {
        if (isSupported()) {
            setOptionApi33(context, option)
        } else {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().putString(LEGACY_PRIMARY, option.name).apply()
        }
    }

    /** Scope the locale to Activity resources; never mutate global Locale. */
    fun localizedActivityBase(base: Context): Context {
        if (isSupported()) return base
        val tag = currentOption(base).languageTag ?: return base
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    internal fun optionForLanguageTags(languageTags: String): AppLanguageOption {
        val firstTag = languageTags.substringBefore(',').trim()
        if (firstTag.isEmpty()) return AppLanguageOption.SYSTEM_DEFAULT
        val locale = Locale.forLanguageTag(firstTag)
        return when (locale.language.lowercase(Locale.ROOT)) {
            "en" -> AppLanguageOption.ENGLISH
            "ja" -> AppLanguageOption.JAPANESE
            "zh" -> if (
                locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.equals("TW", ignoreCase = true) ||
                locale.country.equals("HK", ignoreCase = true) ||
                locale.country.equals("MO", ignoreCase = true)
            ) {
                AppLanguageOption.TRADITIONAL_CHINESE
            } else {
                AppLanguageOption.SYSTEM_DEFAULT
            }
            else -> AppLanguageOption.SYSTEM_DEFAULT
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun currentOptionApi33(context: Context): AppLanguageOption {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) {
            AppLanguageOption.SYSTEM_DEFAULT
        } else {
            optionForLanguageTags(locales.toLanguageTags())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setOptionApi33(context: Context, option: AppLanguageOption) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = when {
            option.languageTag == null -> LocaleList.getEmptyLocaleList()
            else -> LocaleList.forLanguageTags(option.languageTag)
        }
    }
}
