package com.sysadmindoc.alarmclock.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * L13-C: read-only bridge between Android's *actual* per-app locale and text
 * created outside Compose (Services, notification builders, receivers).
 *
 * Does not change LocaleManager, DataStore, global Locale, Room, alarm schedule,
 * or media state. Missing zh-TW/ja Android resources fall back natively to the
 * existing English base; successful lookup is never evidence of G1/G4 review.
 */
internal data class LocaleRuntimeRoute(
    val nativeOverrideTag: String?,
    val source: String
)

internal object LocaleRuntimeRouting {
    fun route(platformAppLocaleSupported: Boolean, appLocaleTags: String?): LocaleRuntimeRoute {
        if (!platformAppLocaleSupported) {
            return LocaleRuntimeRoute(null, "PRE_ANDROID_13_NATIVE_CONTEXT")
        }
        if (appLocaleTags.isNullOrBlank()) {
            return LocaleRuntimeRoute(null, "FOLLOW_SYSTEM_NATIVE_CONTEXT")
        }
        // Android uses its first app-specific locale as the primary locale.
        // Never select a later supported language in preference to an unknown
        // first locale; the platform is authoritative in that case.
        val first = appLocaleTags.substringBefore(',').trim()
        val builtin = LocaleSelectionContract.builtinTag(first)
            ?: return LocaleRuntimeRoute(null, "UNSUPPORTED_PLATFORM_LOCALE_PASSTHROUGH")
        return LocaleRuntimeRoute(builtin, "PLATFORM_BUILTIN_NATIVE_RESOURCES_UNVERIFIED")
    }

    fun fromContext(context: Context): LocaleRuntimeRoute {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return route(false, null)
        }
        val tags = runCatching {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        }.getOrElse {
            // If the OS lookup fails, preserve Android's resource configuration
            // rather than silently forcing English or changing a saved choice.
            return LocaleRuntimeRoute(null, "PLATFORM_QUERY_FAILED_PASSTHROUGH")
        }
        return route(true, tags)
    }

    fun builtinDecision(tag: String): LocaleSelectionDecision {
        val native = requireNotNull(LocaleSelectionContract.builtinTag(tag)) {
            "L13-C only permits built-in Android resources"
        }
        return LocaleSelectionContract.decide(
            LocaleSelectionState(mode = LocaleSelectionMode.BUILTIN, primaryTag = native),
            LocaleSelectionEnvironment(
                deviceLocaleTags = listOf(native),
                androidAppLocaleApiAvailable = true
            )
        )
    }
}

internal object LocaleRuntimeTextBridge {
    private fun selectedLocaleTag(context: Context): String? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // S3: foreground service/notification UI text follows the same
            // per-app preference on Android 8-12 as the Activity resources.
            AppLanguageManager.currentOption(context).languageTag
        } else {
            LocaleRuntimeRouting.fromContext(context).nativeOverrideTag
        }

    /** Read the latest platform setting at text-generation time, not at app start. */
    fun text(context: Context, @StringRes resId: Int): String {
        val tag = selectedLocaleTag(context)
            ?: return context.getString(resId)
        return LocaleContextTextAdapter.text(
            context, LocaleRuntimeRouting.builtinDecision(tag), resId
        ).value
    }

    fun formatted(context: Context, @StringRes resId: Int, vararg arguments: Any): String {
        val tag = selectedLocaleTag(context)
            ?: return context.getString(resId, *arguments)
        return LocaleContextTextAdapter.formatted(
            context, LocaleRuntimeRouting.builtinDecision(tag), resId, *arguments
        ).value
    }

    fun quantity(
        context: Context, @PluralsRes resId: Int, count: Int, vararg arguments: Any
    ): String {
        val tag = selectedLocaleTag(context)
            ?: return context.resources.getQuantityString(resId, count, *arguments)
        return LocaleContextTextAdapter.quantity(
            context, LocaleRuntimeRouting.builtinDecision(tag), resId, count, *arguments
        ).value
    }

    /** For debug-only visual probes; NEVER installs or approves model candidates. */
    fun previewBuiltin(
        context: Context, localeTag: String, @StringRes resId: Int
    ): LocaleContextTextAdapter.NativeText =
        LocaleContextTextAdapter.text(
            context, LocaleRuntimeRouting.builtinDecision(localeTag), resId
        )
}
