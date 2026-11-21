package com.sysadmindoc.alarmclock.util

import java.util.Locale

/**
 * B0-L1: pure, side-effect-free locale decision contract.
 *
 * This is deliberately NOT connected to LocaleManager, DataStore, Compose,
 * notifications, or AlarmService. B1/B2 may integrate it after verifying
 * persistence and Android-version migration. Nothing here changes alarm time.
 */
internal enum class LocaleSelectionMode { FOLLOW_SYSTEM, BUILTIN, CUSTOM }

internal data class LocaleSelectionState(
    val mode: LocaleSelectionMode = LocaleSelectionMode.FOLLOW_SYSTEM,
    val primaryTag: String? = null,
    val secondaryTag: String? = null,
    val dualEnabled: Boolean = false,
    // Null = first platform observation has not yet been reconciled.
    // Distinguishes an external change from the result of our own write.
    val lastObservedAndroidAppLocales: String? = null
)

internal data class LocaleSelectionEnvironment(
    val deviceLocaleTags: List<String>,
    val installedCustomPacks: Set<String> = emptySet(),
    val androidAppLocaleApiAvailable: Boolean
)

internal sealed interface LocaleSelectionAction {
    data object FollowSystem : LocaleSelectionAction
    data class SelectBuiltin(val languageTag: String) : LocaleSelectionAction
    data class SelectCustom(val languageTag: String) : LocaleSelectionAction
    data class SelectSecondary(val languageTag: String?) : LocaleSelectionAction
    data class SetDual(val enabled: Boolean) : LocaleSelectionAction

    /** Platform app-locales read, NOT a broadcast of device locale changes. */
    data class ObserveAndroidAppLocales(val tags: String) : LocaleSelectionAction
    /** Record the observed result of a write originating from our own UI. */
    data class AcknowledgeAndroidAppLocalesWrite(val tags: String) : LocaleSelectionAction
}

internal data class LocaleSelectionTransition(
    val state: LocaleSelectionState,
    val accepted: Boolean,
    val reason: String
)

internal data class LocaleSelectionDecision(
    val primaryLocale: String,
    val secondaryLocale: String?,
    val primarySource: String,
    val secondarySource: String?,
    val fallbackUsed: Boolean,
    val secondaryFallbackUsed: Boolean,
    // Safe platform fallback for built-in resources when a custom overlay is used.
    // It is information only: applying it to Android settings belongs to B1/B2.
    val nativeResourceLocale: String,
    val recommendedPlatformAppLocales: String,
    val platformOverrideSupported: Boolean
)

internal object LocaleSelectionContract {
    private const val ENGLISH = "en"
    private const val TRADITIONAL_CHINESE = "zh-TW"
    private const val JAPANESE = "ja"
    private val tagPattern = Regex(
        "^[A-Za-z]{2,3}(?:-[A-Za-z]{4})?(?:-(?:[A-Za-z]{2}|[0-9]{3}))?(?:-[A-Za-z0-9]{5,8}){0,2}$"
    )

    /** Reject malformed/overlong tags, but do not require Android installed assets. */
    fun canonicalTag(raw: String): String? {
        val text = raw.trim()
        if (text.length !in 2..35 || !tagPattern.matches(text)) return null
        val locale = Locale.forLanguageTag(text)
        return locale.toLanguageTag().takeIf { it != "und" && locale.language.isNotBlank() }
    }

    fun builtinTag(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val tag = canonicalTag(raw) ?: return null
        val parsed = Locale.forLanguageTag(tag)
        return when (parsed.language.lowercase(Locale.ROOT)) {
            "en" -> ENGLISH
            "ja" -> JAPANESE
            "zh" -> if (parsed.script.equals("Hant", ignoreCase = true) ||
                parsed.country in setOf("TW", "HK", "MO")) {
                TRADITIONAL_CHINESE
            } else null
            else -> null
        }
    }

    /** Keep order, canonicalize case, ignore invalid empty members. */
    fun normalizedAndroidAppLocales(tags: String): String =
        tags.split(',').mapNotNull(::canonicalTag).distinct().joinToString(",")

    fun transition(
        old: LocaleSelectionState,
        action: LocaleSelectionAction,
        installedCustomPacks: Set<String> = emptySet()
    ): LocaleSelectionTransition {
        val packs = installedCustomPacks.mapNotNull(::canonicalTag).toSet()
        fun ok(value: LocaleSelectionState, reason: String) =
            LocaleSelectionTransition(value, true, reason)
        fun reject(reason: String) = LocaleSelectionTransition(old, false, reason)
        return when (action) {
            LocaleSelectionAction.FollowSystem ->
                ok(old.copy(mode = LocaleSelectionMode.FOLLOW_SYSTEM, primaryTag = null), "FOLLOW_SYSTEM")
            is LocaleSelectionAction.SelectBuiltin -> {
                val tag = builtinTag(action.languageTag)
                if (tag == null) reject("UNSUPPORTED_BUILTIN_LOCALE")
                else ok(old.copy(mode = LocaleSelectionMode.BUILTIN, primaryTag = tag), "BUILTIN_SELECTED")
            }
            is LocaleSelectionAction.SelectCustom -> {
                val tag = canonicalTag(action.languageTag)
                if (tag == null || tag !in packs) reject("CUSTOM_PACK_NOT_INSTALLED")
                else ok(old.copy(mode = LocaleSelectionMode.CUSTOM, primaryTag = tag), "CUSTOM_SELECTED")
            }
            is LocaleSelectionAction.SelectSecondary -> {
                if (action.languageTag == null) {
                    ok(old.copy(secondaryTag = null), "SECONDARY_CLEARED")
                } else {
                    val tag = canonicalTag(action.languageTag)
                    if (tag == null || (builtinTag(tag) == null && tag !in packs))
                        reject("SECONDARY_NOT_AVAILABLE")
                    else ok(old.copy(secondaryTag = builtinTag(tag) ?: tag), "SECONDARY_SELECTED")
                }
            }
            is LocaleSelectionAction.SetDual ->
                ok(old.copy(dualEnabled = action.enabled), "DUAL_UPDATED")
            is LocaleSelectionAction.AcknowledgeAndroidAppLocalesWrite -> {
                ok(old.copy(lastObservedAndroidAppLocales =
                    normalizedAndroidAppLocales(action.tags)), "OWN_WRITE_ACK")
            }
            is LocaleSelectionAction.ObserveAndroidAppLocales -> {
                val observed = normalizedAndroidAppLocales(action.tags)
                val previous = old.lastObservedAndroidAppLocales
                when {
                    previous == null -> ok(
                        old.copy(lastObservedAndroidAppLocales = observed),
                        "FIRST_OBSERVATION_ONLY"
                    )
                    previous == observed -> ok(old, "NO_EXTERNAL_CHANGE")
                    observed.isBlank() -> ok(
                        old.copy(mode = LocaleSelectionMode.FOLLOW_SYSTEM,
                            primaryTag = null, lastObservedAndroidAppLocales = observed),
                        "EXTERNAL_RESET_TO_SYSTEM"
                    )
                    else -> {
                        val built = builtinTag(observed.substringBefore(','))
                        if (built != null) ok(
                            old.copy(mode = LocaleSelectionMode.BUILTIN,
                                primaryTag = built,lastObservedAndroidAppLocales = observed),
                            "EXTERNAL_BUILTIN_LANGUAGE"
                        ) else ok(
                            old.copy(lastObservedAndroidAppLocales = observed),
                            "EXTERNAL_UNSUPPORTED_LOCALE_PRESERVED"
                        )
                    }
                }
            }
        }
    }

    fun decide(
        state: LocaleSelectionState,
        environment: LocaleSelectionEnvironment
    ): LocaleSelectionDecision {
        val packs = environment.installedCustomPacks.mapNotNull(::canonicalTag).toSet()
        val (primary, source, primaryFallback) = when (state.mode) {
            LocaleSelectionMode.FOLLOW_SYSTEM -> {
                val firstSupported = environment.deviceLocaleTags.firstNotNullOfOrNull(::builtinTag)
                Triple(firstSupported ?: ENGLISH, "SYSTEM", firstSupported == null)
            }
            LocaleSelectionMode.BUILTIN -> {
                val native = builtinTag(state.primaryTag)
                Triple(native ?: ENGLISH, "APP_BUILTIN", native == null)
            }
            LocaleSelectionMode.CUSTOM -> {
                val tag = state.primaryTag?.let(::canonicalTag)
                if (tag != null && tag in packs)
                    Triple(tag, "CUSTOM_PACK", false)
                else Triple(ENGLISH, "MISSING_CUSTOM_PACK", true)
            }
        }

        val selectedSecondary = state.secondaryTag?.let(::canonicalTag)
        val resolvedSecondary = when {
            !state.dualEnabled || selectedSecondary == null -> null
            builtinTag(selectedSecondary) != null -> builtinTag(selectedSecondary)
            selectedSecondary in packs -> selectedSecondary
            else -> ENGLISH // deliberate language-specific fallback
        }?.takeUnless { it == primary }
        val secondaryMissing = selectedSecondary != null &&
            state.dualEnabled && resolvedSecondary == ENGLISH &&
            builtinTag(selectedSecondary) == null && selectedSecondary !in packs
        val secondarySource = when {
            resolvedSecondary == null -> null
            secondaryMissing -> "SECONDARY_MISSING_PACK"
            resolvedSecondary in setOf(ENGLISH, TRADITIONAL_CHINESE, JAPANESE) -> "SECONDARY_BUILTIN"
            else -> "SECONDARY_CUSTOM_PACK"
        }
        val native = builtinTag(primary) ?: ENGLISH
        return LocaleSelectionDecision(
            primaryLocale = primary,
            secondaryLocale = resolvedSecondary,
            primarySource = source,
            secondarySource = secondarySource,
            fallbackUsed = primaryFallback,
            secondaryFallbackUsed = secondaryMissing,
            nativeResourceLocale = native,
            recommendedPlatformAppLocales = when (state.mode) {
                LocaleSelectionMode.FOLLOW_SYSTEM -> ""
                LocaleSelectionMode.BUILTIN -> native
                LocaleSelectionMode.CUSTOM -> ENGLISH
            },
            platformOverrideSupported = environment.androidAppLocaleApiAvailable
        )
    }
}
