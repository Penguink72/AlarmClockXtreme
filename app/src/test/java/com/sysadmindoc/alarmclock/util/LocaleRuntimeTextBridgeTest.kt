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
class LocaleRuntimeTextBridgeTest {
    private val base: Context = ApplicationProvider.getApplicationContext()

    @Test fun systemAndPre13DoNotCreateOrPersistAnyAppLocaleOverride() {
        assertEquals(
            LocaleRuntimeRoute(null, "PRE_ANDROID_13_NATIVE_CONTEXT"),
            LocaleRuntimeRouting.route(false, "ja")
        )
        assertEquals(
            LocaleRuntimeRoute(null, "FOLLOW_SYSTEM_NATIVE_CONTEXT"),
            LocaleRuntimeRouting.route(true, "")
        )
        assertEquals(
            LocaleRuntimeRoute(null, "FOLLOW_SYSTEM_NATIVE_CONTEXT"),
            LocaleRuntimeRouting.route(true, null)
        )
    }

    @Test fun firstPlatformAppLocaleWinsAndNormalizedBuiltinsAreSupported() {
        assertEquals("zh-TW", LocaleRuntimeRouting.route(true, "zh-Hant-TW,en").nativeOverrideTag)
        assertEquals("zh-TW", LocaleRuntimeRouting.route(true, "zh-HK").nativeOverrideTag)
        assertEquals("ja", LocaleRuntimeRouting.route(true, "ja-JP,en").nativeOverrideTag)
        assertEquals("en", LocaleRuntimeRouting.route(true, "en-US,ja").nativeOverrideTag)
        assertEquals(
            "PLATFORM_BUILTIN_NATIVE_RESOURCES_UNVERIFIED",
            LocaleRuntimeRouting.route(true, "ja").source
        )
    }

    @Test fun unsupportedFirstPlatformLocaleMustPassThroughNotPickLaterSupportedValue() {
        val unsupported = LocaleRuntimeRouting.route(true, "zh-Hans-CN,ja")
        assertNull(unsupported.nativeOverrideTag)
        assertEquals("UNSUPPORTED_PLATFORM_LOCALE_PASSTHROUGH", unsupported.source)
        assertNull(LocaleRuntimeRouting.route(true, "ko-KR,en").nativeOverrideTag)
        assertNull(LocaleRuntimeRouting.route(true, "../../../bad,ja").nativeOverrideTag)
    }

    @Test fun invalidPreviewLocaleRejectedWithoutInstallingLanguagePacks() {
        assertThrows(IllegalArgumentException::class.java) {
            LocaleRuntimeRouting.builtinDecision("fr-FR")
        }
        assertEquals("ja", LocaleRuntimeRouting.builtinDecision("ja-JP").nativeResourceLocale)
        assertEquals(
            "zh-TW",
            LocaleRuntimeRouting.builtinDecision("zh-Hant-TW").nativeResourceLocale
        )
        assertEquals("en", LocaleRuntimeRouting.builtinDecision("en").nativeResourceLocale)
    }

    @Test
    @Config(sdk = [28])
    fun android12AndBelowUseExistingNativeContextAndFormatsWithoutNewSettings() {
        val before = base.resources.configuration.locales.toLanguageTags()
        val ordinary = LocaleRuntimeTextBridge.text(base, R.string.notif_alarm_title)
        assertEquals(base.getString(R.string.notif_alarm_title), ordinary)
        val snooze = LocaleRuntimeTextBridge.formatted(base, R.string.notif_snooze_action, 10)
        assertEquals(base.getString(R.string.notif_snooze_action, 10), snooze)
        val qty = LocaleRuntimeTextBridge.quantity(base, R.plurals.settings_minutes, 3, 3)
        assertEquals(base.resources.getQuantityString(R.plurals.settings_minutes, 3, 3), qty)
        assertEquals("PRE_ANDROID_13_NATIVE_CONTEXT", LocaleRuntimeRouting.fromContext(base).source)
        assertEquals(before, base.resources.configuration.locales.toLanguageTags())
    }

    @Test
    @Config(sdk = [33])
    fun debugPreviewUsesRealNativeResourcesWithoutMutatingActivityOrSystem() {
        val before = base.resources.configuration.locales.toLanguageTags()
        val en = LocaleRuntimeTextBridge.previewBuiltin(base, "en", R.string.notif_alarm_title)
        val zh = LocaleRuntimeTextBridge.previewBuiltin(base, "zh-TW", R.string.notif_alarm_title)
        val ja = LocaleRuntimeTextBridge.previewBuiltin(base, "ja", R.string.notif_alarm_title)
        assertEquals("en", en.requestedLocale)
        assertEquals("zh-TW", zh.requestedLocale)
        assertEquals("ja", ja.requestedLocale)
        assertTrue(listOf(en, zh, ja).all { it.value.isNotBlank() })
        assertTrue(listOf(en, zh, ja).all {
            it.origin == "ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED"
        })
        assertEquals(before, base.resources.configuration.locales.toLanguageTags())
    }

    @Test
    @Config(sdk = [33])
    fun nativeLocalizedNotificationActionStillRetainsPlaceholderAndOperationIdentity() {
        for (tag in listOf("en", "zh-TW", "ja")) {
            val d = LocaleRuntimeRouting.builtinDecision(tag)
            val snooze = LocaleContextTextAdapter.formatted(
                base, d, R.string.notif_snooze_action, 10
            ).value
            val dismiss = LocaleContextTextAdapter.text(
                base, d, R.string.notif_dismiss_action
            ).value
            val missed = LocaleContextTextAdapter.formatted(
                base, d, R.string.notif_missed_auto_silenced,
                "Wake", "08:30", 5L
            ).value
            assertTrue("Missing formatted minutes in ${tag}", snooze.contains("10"))
            assertTrue("Missing action copy in ${tag}", dismiss.isNotBlank())
            assertTrue("Missing alarm label in ${tag}", missed.contains("Wake"))
            assertTrue("Missing alarm time in ${tag}", missed.contains("08:30"))
        }
    }
}
