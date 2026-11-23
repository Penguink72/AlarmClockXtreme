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
}
