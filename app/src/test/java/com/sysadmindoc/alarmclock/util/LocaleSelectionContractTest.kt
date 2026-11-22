package com.sysadmindoc.alarmclock.util

import org.junit.Assert.*
import org.junit.Test

class LocaleSelectionContractTest {
    private val builtinEnvironment = LocaleSelectionEnvironment(
        deviceLocaleTags = listOf("zh-TW"), androidAppLocaleApiAvailable = true
    )

    @Test fun followingSystemTracksDeviceChangesWithoutEditingPreference() {
        val state = LocaleSelectionState()
        val zh = LocaleSelectionContract.decide(state,builtinEnvironment)
        val ja = LocaleSelectionContract.decide(state,builtinEnvironment.copy(deviceLocaleTags=listOf("ja-JP")))
        assertEquals("zh-TW",zh.primaryLocale)
        assertEquals("ja",ja.primaryLocale)
        assertEquals("SYSTEM",ja.primarySource)
        assertEquals("",ja.recommendedPlatformAppLocales)
        assertEquals(LocaleSelectionMode.FOLLOW_SYSTEM,state.mode)
    }

    @Test fun unsupportedSystemLanguageUsesNextSupportedBeforeEnglish() {
        val state=LocaleSelectionState()
        val withJa=LocaleSelectionContract.decide(state,builtinEnvironment.copy(
            deviceLocaleTags=listOf("fr-FR","ja-JP")
        ))
        assertEquals("ja",withJa.primaryLocale)
        assertFalse(withJa.fallbackUsed)
        val noMatch=LocaleSelectionContract.decide(state,builtinEnvironment.copy(
            deviceLocaleTags=listOf("fr-FR","zh-Hans-CN")
        ))
        assertEquals("en",noMatch.primaryLocale)
        assertTrue(noMatch.fallbackUsed)
    }

    @Test fun explicitBuiltinIgnoresLaterDeviceLocaleChanges() {
        val state=LocaleSelectionContract.transition(LocaleSelectionState(),
            LocaleSelectionAction.SelectBuiltin("en-US")).state
        val resolved=LocaleSelectionContract.decide(state,builtinEnvironment.copy(deviceLocaleTags=listOf("ja")))
        assertEquals("en",resolved.primaryLocale)
        assertEquals("APP_BUILTIN",resolved.primarySource)
        assertEquals("en",resolved.recommendedPlatformAppLocales)
    }

    @Test fun unsupportedBuiltinAndUninstalledCustomNeverChangeSelection() {
        val start=LocaleSelectionState()
        val bad=LocaleSelectionContract.transition(start,LocaleSelectionAction.SelectBuiltin("zh-Hans-CN"))
        assertFalse(bad.accepted)
        assertEquals(start,bad.state)
        val absent=LocaleSelectionContract.transition(start,
            LocaleSelectionAction.SelectCustom("ko-KR"),setOf("fr-FR"))
        assertFalse(absent.accepted)
        assertEquals(start,absent.state)
        assertNull(LocaleSelectionContract.canonicalTag("../../../bad"))
        assertEquals("zh-TW",LocaleSelectionContract.builtinTag("zh-Hant-HK"))
    }

    @Test fun customPackIsKeptSeparateFromNativeFallbackAndDual() {
        val packs=setOf("ko-KR")
        var state=LocaleSelectionContract.transition(LocaleSelectionState(),
            LocaleSelectionAction.SelectCustom("ko-KR"),packs).state
        state=LocaleSelectionContract.transition(state,
            LocaleSelectionAction.SelectSecondary("zh-TW"),packs).state
        state=LocaleSelectionContract.transition(state,LocaleSelectionAction.SetDual(true)).state
        val decision=LocaleSelectionContract.decide(state,builtinEnvironment.copy(
            installedCustomPacks=packs,deviceLocaleTags=listOf("en-US")))
        assertEquals("ko-KR",decision.primaryLocale)
        assertEquals("CUSTOM_PACK",decision.primarySource)
        assertEquals("zh-TW",decision.secondaryLocale)
        assertEquals("SECONDARY_BUILTIN",decision.secondarySource)
        assertEquals("en",decision.nativeResourceLocale)
        assertEquals("en",decision.recommendedPlatformAppLocales)
        assertTrue(decision.platformOverrideSupported)
    }

    @Test fun samePrimarySecondaryIsNotShownTwiceAndPreferencesRemainIntact() {
        var state=LocaleSelectionContract.transition(LocaleSelectionState(),
            LocaleSelectionAction.SelectBuiltin("ja")).state
        state=LocaleSelectionContract.transition(state,LocaleSelectionAction.SelectSecondary("ja")).state
        state=LocaleSelectionContract.transition(state,LocaleSelectionAction.SetDual(true)).state
        assertNull(LocaleSelectionContract.decide(state,builtinEnvironment).secondaryLocale)
        assertEquals("ja",state.secondaryTag)
        assertTrue(state.dualEnabled)
    }

    @Test fun removedCustomPackFallsBackToEnglishWithoutErasingChoice() {
        val current=LocaleSelectionState(mode=LocaleSelectionMode.CUSTOM,primaryTag="ko-KR")
        val decision=LocaleSelectionContract.decide(current,builtinEnvironment)
        assertEquals("en",decision.primaryLocale)
        assertEquals("MISSING_CUSTOM_PACK",decision.primarySource)
        assertTrue(decision.fallbackUsed)
        assertEquals("ko-KR",current.primaryTag)
    }

    @Test fun externalAndroidAppLanguageChangeBeatsPreviousAppSelection() {
        val initial=LocaleSelectionState(mode=LocaleSelectionMode.CUSTOM,primaryTag="ko-KR",
            lastObservedAndroidAppLocales="en")
        val received=LocaleSelectionContract.transition(initial,
            LocaleSelectionAction.ObserveAndroidAppLocales("ja-JP"))
        assertEquals("EXTERNAL_BUILTIN_LANGUAGE",received.reason)
        assertEquals(LocaleSelectionMode.BUILTIN,received.state.mode)
        assertEquals("ja",received.state.primaryTag)
        assertEquals("ja-JP",received.state.lastObservedAndroidAppLocales)
    }

    @Test fun firstObservationAndOwnWriteAcknowledgementPreventFalseExternalChanges() {
        val initial=LocaleSelectionState(mode=LocaleSelectionMode.CUSTOM,primaryTag="ko-KR")
        val first=LocaleSelectionContract.transition(initial,
            LocaleSelectionAction.ObserveAndroidAppLocales("en"))
        assertEquals("FIRST_OBSERVATION_ONLY",first.reason)
        assertEquals(initial.mode,first.state.mode)
        val acknowledged=LocaleSelectionContract.transition(first.state,
            LocaleSelectionAction.AcknowledgeAndroidAppLocalesWrite("en"))
        val same=LocaleSelectionContract.transition(acknowledged.state,
            LocaleSelectionAction.ObserveAndroidAppLocales("en"))
        assertEquals("NO_EXTERNAL_CHANGE",same.reason)
        assertEquals(LocaleSelectionMode.CUSTOM,same.state.mode)
    }

    @Test fun externalResetToSystemDoesNotDeleteInstalledCustomPacks() {
        val old=LocaleSelectionState(mode=LocaleSelectionMode.BUILTIN,primaryTag="ja",
            lastObservedAndroidAppLocales="ja")
        val reset=LocaleSelectionContract.transition(old,
            LocaleSelectionAction.ObserveAndroidAppLocales(""))
        assertEquals("EXTERNAL_RESET_TO_SYSTEM",reset.reason)
        assertEquals(LocaleSelectionMode.FOLLOW_SYSTEM,reset.state.mode)
        assertEquals("zh-TW",LocaleSelectionContract.decide(reset.state,builtinEnvironment).primaryLocale)
    }

    @Test fun unsupportedExternalAppLocaleIsRememberedButNotMislabelledBuiltin() {
        val old=LocaleSelectionState(mode=LocaleSelectionMode.CUSTOM,primaryTag="ko-KR",
            lastObservedAndroidAppLocales="en")
        val observed=LocaleSelectionContract.transition(old,
            LocaleSelectionAction.ObserveAndroidAppLocales("fr-FR"))
        assertEquals("EXTERNAL_UNSUPPORTED_LOCALE_PRESERVED",observed.reason)
        assertEquals(old.mode,observed.state.mode)
        assertEquals("fr-FR",observed.state.lastObservedAndroidAppLocales)
    }

    @Test fun android12HasNoNativePickerEvenThoughPureDecisionCanRepresentPreference() {
        val state=LocaleSelectionContract.transition(LocaleSelectionState(),
            LocaleSelectionAction.SelectBuiltin("ja")).state
        val decided=LocaleSelectionContract.decide(state,builtinEnvironment.copy(
            androidAppLocaleApiAvailable=false))
        assertFalse(decided.platformOverrideSupported)
        assertEquals("ja",decided.primaryLocale)
        assertEquals("ja",decided.recommendedPlatformAppLocales)
    }

    @Test fun secondaryUnknownPackFallsBackButDoesNotOverridePrimary() {
        val old=LocaleSelectionState(mode=LocaleSelectionMode.BUILTIN,primaryTag="ja",
            secondaryTag="ko-KR",dualEnabled=true)
        val decided=LocaleSelectionContract.decide(old,builtinEnvironment)
        assertEquals("ja",decided.primaryLocale)
        assertEquals("en",decided.secondaryLocale)
        assertTrue(decided.secondaryFallbackUsed)
        assertEquals("SECONDARY_MISSING_PACK",decided.secondarySource)
    }
}
