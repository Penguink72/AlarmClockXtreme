package com.sysadmindoc.alarmclock.util

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList

/**
 * Android locale access is isolated for deterministic version/OS-change tests.
 * Custom pack language is NOT applied to ordinary UI until B2 LocaleResolver.
 */
internal interface LocalePlatformPort {
    val perAppLocaleSupported: Boolean
    fun appLanguageTags(): String
    fun updateAppLanguageTags(tags: String): Boolean
    fun deviceLanguageTags(): List<String>
}

internal class AndroidLocalePlatformPort(private val context: Context) : LocalePlatformPort {
    override val perAppLocaleSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun appLanguageTags(): String =
        if (perAppLocaleSupported) {
            runCatching {
                context.getSystemService(LocaleManager::class.java)
                    .applicationLocales.toLanguageTags()
            }.getOrDefault("")
        } else ""

    override fun updateAppLanguageTags(tags: String): Boolean {
        if (!perAppLocaleSupported) return false
        return runCatching {
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales = if (tags.isBlank()) {
                    LocaleList.getEmptyLocaleList()
                } else LocaleList.forLanguageTags(tags)
        }.isSuccess
    }

    override fun deviceLanguageTags(): List<String> =
        Resources.getSystem().configuration.locales.toLanguageTags()
            .split(',').filter { it.isNotBlank() }
}

/**
 * Persistent, single-writer in-process coordinator. The previous B0-L1
 * LocaleSelectionContract remains the only reducer and decision source.
 * B1-U1 may store custom+secondary preferences; B2 performs rendering.
 */
internal class LocaleCoordinator(
    private val prefs: SharedPreferences,
    private val platform: LocalePlatformPort,
    private val installedCustomPacks: () -> Set<String> = { emptySet() }
) {
    companion object {
        private const val PREFS = "acx-locale-selection-v1"
        private const val VERSION = 1
        private val processLock = Any()

        fun forApp(
            context: Context,
            installedCustomPacks: () -> Set<String> = { emptySet() }
        ): LocaleCoordinator = LocaleCoordinator(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            AndroidLocalePlatformPort(context),
            installedCustomPacks
        )
    }

    private val platformTags: String
        get() = LocaleSelectionContract.normalizedAndroidAppLocales(
            platform.appLanguageTags()
        )

    private fun persist(value: LocaleSelectionState): Boolean =
        prefs.edit().putInt("version",VERSION)
            .putString("mode",value.mode.name)
            .putString("primaryTag",value.primaryTag)
            .putString("secondaryTag",value.secondaryTag)
            .putBoolean("dualEnabled",value.dualEnabled)
            .putString("lastObservedAndroidAppLocales",value.lastObservedAndroidAppLocales)
            .commit()

    /** Initial migration preserves existing Android 13+ app-locales preference. */
    private fun fromPrefsOrMigrate(): LocaleSelectionState {
        if (prefs.getInt("version",0)!=VERSION) {
            val current=platformTags
            val builtin=LocaleSelectionContract.builtinTag(current.substringBefore(','))
            val state=if (builtin!=null) LocaleSelectionState(
                mode=LocaleSelectionMode.BUILTIN,
                primaryTag=builtin,
                lastObservedAndroidAppLocales=current
            ) else LocaleSelectionState(
                mode=LocaleSelectionMode.FOLLOW_SYSTEM,
                lastObservedAndroidAppLocales=current
            )
            check(persist(state)) { "LOCALE_PREFERENCE_WRITE_FAILED" }
            return state
        }
        val mode=runCatching {
            LocaleSelectionMode.valueOf(prefs.getString("mode","FOLLOW_SYSTEM")!!)
        }.getOrDefault(LocaleSelectionMode.FOLLOW_SYSTEM)
        return LocaleSelectionState(
            mode=mode,
            primaryTag=prefs.getString("primaryTag",null),
            secondaryTag=prefs.getString("secondaryTag",null),
            dualEnabled=prefs.getBoolean("dualEnabled",false),
            lastObservedAndroidAppLocales=prefs.getString("lastObservedAndroidAppLocales",null)
        )
    }

    private fun observed(): LocaleSelectionState {
        val old=fromPrefsOrMigrate()
        if (!platform.perAppLocaleSupported) return old
        val result=LocaleSelectionContract.transition(old,
            LocaleSelectionAction.ObserveAndroidAppLocales(platformTags))
        if (result.state!=old)
            check(persist(result.state)) { "LOCALE_PREFERENCE_WRITE_FAILED" }
        return result.state
    }

    fun snapshot(): LocaleSelectionState = synchronized(processLock) { observed() }

    fun decision(): LocaleSelectionDecision = synchronized(processLock) {
        val state=observed()
        LocaleSelectionContract.decide(state,LocaleSelectionEnvironment(
            deviceLocaleTags=platform.deviceLanguageTags(),
            installedCustomPacks=installedCustomPacks(),
            androidAppLocaleApiAvailable=platform.perAppLocaleSupported
        ))
    }

    /**
     * External OS picker wins when it changes AppLocales. Our own builtin
     * picker acknowledges its write; do not classify the callback as external.
     *
     * For API 26-32 builtin choice is saved as preview-only, not secretly
     * mutating Resources/Locale.setDefault or promising whole-app switching.
     */
    fun dispatch(action: LocaleSelectionAction): LocaleSelectionTransition =
        synchronized(processLock) {
            val previous=observed()
            val next=LocaleSelectionContract.transition(
                previous,action,installedCustomPacks()
            )
            if(!next.accepted) return@synchronized next

            val nativeTarget=when(action) {
                LocaleSelectionAction.FollowSystem -> ""
                is LocaleSelectionAction.SelectBuiltin ->
                    LocaleSelectionContract.builtinTag(action.languageTag)
                else -> null
            }
            if (nativeTarget!=null && platform.perAppLocaleSupported) {
                // Write state+expected snapshot BEFORE changing Android locales,
                // because the platform may recreate the Activity synchronously.
                val optimistic=LocaleSelectionContract.transition(
                    next.state,LocaleSelectionAction.AcknowledgeAndroidAppLocalesWrite(
                        nativeTarget
                    )).state
                if(!persist(optimistic))
                    return@synchronized LocaleSelectionTransition(
                        previous,false,"LOCALE_PREFERENCE_WRITE_FAILED"
                    )
                if(!platform.updateAppLanguageTags(nativeTarget)) {
                    persist(previous)
                    return@synchronized LocaleSelectionTransition(
                        previous,false,"PLATFORM_LOCALE_WRITE_FAILED"
                    )
                }
                val acknowledged=LocaleSelectionContract.transition(
                    optimistic,LocaleSelectionAction.AcknowledgeAndroidAppLocalesWrite(
                        platformTags
                    )).state
                persist(acknowledged)
                return@synchronized LocaleSelectionTransition(
                    acknowledged,true,"PLATFORM_LANGUAGE_APPLIED"
                )
            }
            if(!persist(next.state)) return@synchronized LocaleSelectionTransition(
                previous,false,"LOCALE_PREFERENCE_WRITE_FAILED"
            )
            next
        }
}
