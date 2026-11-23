package com.sysadmindoc.alarmclock.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S3 options must survive a read/write round trip without exposing app state. */
class BilingualUiOptionsTest {
    @Test
    fun defaultsToSingleLanguageWithoutChangingExistingUsers() {
        val settings = BilingualUiOptions()
        assertFalse(settings.enabled)
        assertEquals("ja", settings.secondaryTag)
    }

    @Test
    fun acceptsAllThreeSecondaryLanguageOptions() {
        for (tag in listOf("zh-TW", "en", "ja")) {
            val result = BilingualUiOptions(enabled = true, secondaryTag = tag).normalized()
            assertTrue(result.enabled)
            assertEquals(tag, result.secondaryTag)
        }
    }

    @Test
    fun invalidAndInjectedLocaleTagsFallBackToARealNativeLocale() {
        for (tag in listOf("de", "../en", "zh-Hans-CN", "", "zh-TW,en")) {
            assertEquals("ja", BilingualUiOptions(true, tag).normalized().secondaryTag)
        }
    }

    @Test
    fun turningOffDualDisplayDoesNotDeleteTheChosenSecondaryLanguage() {
        val enabled = BilingualUiOptions(true, "en")
        assertEquals("en", enabled.copy(enabled = false).normalized().secondaryTag)
        assertFalse(enabled.copy(enabled = false).enabled)
    }
    @Test
    fun allSixOrderedLanguagePairsShowBothVisibleNativeTexts() {
        val samples = mapOf(
            "en" to "Alarm",
            "zh-TW" to "鬧鐘",
            "ja" to "アラーム"
        )
        for ((primaryTag, primary) in samples) {
            for ((secondaryTag, secondary) in samples) {
                if (primaryTag == secondaryTag) continue
                val display = BilingualUiTextPolicy.combine(
                    primary, secondary, primaryTag,
                    BilingualUiOptions(true, secondaryTag)
                )
                assertEquals(primary + "\n" + secondary, display)
            }
        }
    }

    @Test
    fun offSameLanguageAndIdenticalProperNounsDoNotDuplicateVisibleLabels() {
        assertEquals("Alarm", BilingualUiTextPolicy.combine(
            "Alarm", "鬧鐘", "en", BilingualUiOptions(false, "zh-TW")
        ))
        assertEquals("Alarm", BilingualUiTextPolicy.combine(
            "Alarm", "Alarm", "en", BilingualUiOptions(true, "ja")
        ))
        assertEquals("Alarm", BilingualUiTextPolicy.combine(
            "Alarm", "Alarm", "ja", BilingualUiOptions(true, "ja")
        ))
    }

}
