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
