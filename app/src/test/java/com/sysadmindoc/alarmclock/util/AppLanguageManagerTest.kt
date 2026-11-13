package com.sysadmindoc.alarmclock.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageManagerTest {
    @Test
    fun emptyLanguageTagsUseSystemDefault() {
        assertEquals(
            AppLanguageOption.SYSTEM_DEFAULT,
            AppLanguageManager.optionForLanguageTags("")
        )
    }

    @Test
    fun englishLanguageTagsSelectEnglish() {
        assertEquals(
            AppLanguageOption.ENGLISH,
            AppLanguageManager.optionForLanguageTags("en-US,en")
        )
    }

    @Test
    fun traditionalChineseAndJapaneseTagsSelectTheirAppLanguages() {
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageManager.optionForLanguageTags("zh-TW")
        )
        assertEquals(
            AppLanguageOption.TRADITIONAL_CHINESE,
            AppLanguageManager.optionForLanguageTags("zh-Hant-TW")
        )
        assertEquals(
            AppLanguageOption.JAPANESE,
            AppLanguageManager.optionForLanguageTags("ja-JP")
        )
    }

    @Test
    fun traditionalChineseScriptAndHongKongMacauLocalesAreRecognized() {
        listOf("zh-Hant", "zh-HK", "zh-MO", "zh-Hant-HK").forEach { tag ->
            assertEquals(
                tag,
                AppLanguageOption.TRADITIONAL_CHINESE,
                AppLanguageManager.optionForLanguageTags(tag)
            )
        }
    }

    @Test
    fun simplifiedChineseScriptDoesNotChooseTraditionalChinese() {
        assertEquals(
            AppLanguageOption.SYSTEM_DEFAULT,
            AppLanguageManager.optionForLanguageTags("zh-Hans-CN")
        )
    }

    @Test
    fun theFirstConfiguredLocaleDeterminesTheSelection() {
        assertEquals(
            AppLanguageOption.ENGLISH,
            AppLanguageManager.optionForLanguageTags("en-US,zh-TW")
        )
    }

    @Test
    fun unsupportedChineseRegionFallsBackToSystemDefault() {
        assertEquals(
            AppLanguageOption.SYSTEM_DEFAULT,
            AppLanguageManager.optionForLanguageTags("zh-CN")
        )
    }

    @Test
    fun unsupportedLanguageFallsBackToSystemDefault() {
        assertEquals(
            AppLanguageOption.SYSTEM_DEFAULT,
            AppLanguageManager.optionForLanguageTags("fr")
        )
    }
}
