package com.sysadmindoc.alarmclock.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * L13-A: non-mutating native Android text adapter. It prepares a localized
 * Context suitable for Service/Receiver/notification text at resource creation
 * time; it does not install a custom pack, persist preferences, or change the
 * application locale, existing alarms, database or playback state.
 *
 * Android may silently fall back to the English base when a dedicated
 * translation is missing. A successful lookup must NOT be reported as proof
 * that a locale-specific reviewed translation exists.
 */
internal object LocaleContextTextAdapter {
    internal data class NativeText(
        val value: String,
        val requestedLocale: String,
        val origin: String = "ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED"
    )

    private val builtinTags = setOf("en", "zh-TW", "ja")

    fun contextFor(base: Context, decision: LocaleSelectionDecision): Context {
        val tag = decision.nativeResourceLocale
        require(tag in builtinTags) { "Unsupported native locale tag: $tag" }
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        // This does NOT mutate base.resources.configuration, global JVM Locale,
        // LocaleManager.applicationLocales or any setting stored for the app.
        return base.createConfigurationContext(configuration)
    }

    fun text(base: Context, decision: LocaleSelectionDecision, @StringRes resId: Int): NativeText {
        val selected = contextFor(base, decision)
        return NativeText(selected.getString(resId), decision.nativeResourceLocale)
    }

    fun formatted(
        base: Context,
        decision: LocaleSelectionDecision,
        @StringRes resId: Int,
        vararg arguments: Any
    ): NativeText {
        val selected = contextFor(base, decision)
        return NativeText(selected.getString(resId, *arguments), decision.nativeResourceLocale)
    }

    fun quantity(
        base: Context,
        decision: LocaleSelectionDecision,
        @PluralsRes resId: Int,
        count: Int,
        vararg formatArguments: Any
    ): NativeText {
        val selected = contextFor(base, decision)
        return NativeText(
            selected.resources.getQuantityString(resId, count, *formatArguments),
            decision.nativeResourceLocale
        )
    }
}
