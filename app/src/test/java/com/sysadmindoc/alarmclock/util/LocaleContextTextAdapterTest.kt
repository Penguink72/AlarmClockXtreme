package com.sysadmindoc.alarmclock.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocaleContextTextAdapterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun select(tag: String): LocaleSelectionDecision {
        val state = LocaleSelectionState(mode = LocaleSelectionMode.BUILTIN, primaryTag = tag)
        return LocaleSelectionContract.decide(state,
            LocaleSelectionEnvironment(deviceLocaleTags = listOf("en"), androidAppLocaleApiAvailable = true))
    }

    @Test fun nativeLookupDoesNotChangeGlobalOrBaseContextLocale() {
        val before = context.resources.configuration.locales.toLanguageTags()
        val text = LocaleContextTextAdapter.text(context, select("ja"), R.string.settings_language)
        assertEquals("ja", text.requestedLocale)
        assertTrue(text.value.isNotBlank())
        assertEquals("ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED", text.origin)
        assertEquals(before, context.resources.configuration.locales.toLanguageTags())
    }

    @Test fun traditionalChineseLookupAndEnglishFallbackCanCoexistWithoutStateWrite() {
        val zh = LocaleContextTextAdapter.text(context, select("zh-TW"), R.string.settings_language)
        val en = LocaleContextTextAdapter.text(context, select("en"), R.string.settings_language)
        assertEquals("zh-TW", zh.requestedLocale)
        assertEquals("en", en.requestedLocale)
        assertTrue(zh.value.isNotBlank())
        assertTrue(en.value.isNotBlank())
    }

    @Test fun formattedAndPluralResourcesUseSelectedNativeContext() {
        val d = select("ja")
        val formatted = LocaleContextTextAdapter.formatted(
            context,d,R.string.settings_incident_timing,"08:00","08:30")
        assertTrue(formatted.value.contains("08:00"))
        assertTrue(formatted.value.contains("08:30"))
        val count = LocaleContextTextAdapter.quantity(context,d,R.plurals.settings_minutes,3,3)
        assertTrue(count.value.contains("3"))
        assertEquals("ja", count.requestedLocale)
    }

    @Test fun customPackRequiresSeparateOverlayAndUsesEnglishNativeBase() {
        val state = LocaleSelectionState(mode = LocaleSelectionMode.CUSTOM, primaryTag = "ko-KR")
        val d = LocaleSelectionContract.decide(state,
            LocaleSelectionEnvironment(deviceLocaleTags = listOf("zh-TW"),
                installedCustomPacks = setOf("ko-KR"), androidAppLocaleApiAvailable = true))
        assertEquals("ko-KR",d.primaryLocale)
        assertEquals("en",d.nativeResourceLocale)
        val result = LocaleContextTextAdapter.text(context,d,R.string.settings_language)
        assertEquals("en",result.requestedLocale)
        assertEquals("ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED", result.origin)
    }
}
