package com.sysadmindoc.alarmclock.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi

internal enum class AppLanguageOption(val languageTag: String?) {
    SYSTEM_DEFAULT(null),
    ENGLISH("en"),
    TRADITIONAL_CHINESE("zh-TW"),
    JAPANESE("ja")
}

internal object AppLanguageManager {
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun currentOption(context: Context): AppLanguageOption {
        if (!isSupported()) return AppLanguageOption.SYSTEM_DEFAULT
        return currentOptionApi33(context)
    }

    fun setOption(context: Context, option: AppLanguageOption) {
        if (!isSupported()) return
        setOptionApi33(context, option)
    }

    internal fun optionForLanguageTags(languageTags: String): AppLanguageOption {
        val firstLanguage = languageTags
            .substringBefore(',')
            .trim()
            .substringBefore('-')
            .lowercase()
        return when {
            firstLanguage == "en" -> AppLanguageOption.ENGLISH
            firstLanguage == "ja" -> AppLanguageOption.JAPANESE
            firstLanguage == "zh" &&
                languageTags.substringBefore(',').contains("TW", ignoreCase = true) ->
                AppLanguageOption.TRADITIONAL_CHINESE
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
