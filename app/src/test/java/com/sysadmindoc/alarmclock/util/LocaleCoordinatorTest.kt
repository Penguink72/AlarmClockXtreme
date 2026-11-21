package com.sysadmindoc.alarmclock.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocaleCoordinatorTest {
    private class FakePlatform(
        override val perAppLocaleSupported: Boolean,
        var tags: String = "",
        var locales: List<String> = listOf("zh-TW"),
        var failWrites: Boolean = false
    ): LocalePlatformPort {
        var writes = 0
        override fun appLanguageTags(): String = tags
        override fun updateAppLanguageTags(tags: String): Boolean {
            writes++
            if (failWrites) return false
            this.tags = tags
            return true
        }
        override fun deviceLanguageTags(): List<String> = locales
    }

    private fun testPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("acx-locale-coordinator-test",Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }

    @Test fun migrationOfExistingAndroid33PerAppLanguageDoesNotRewritePlatform() {
        val port=FakePlatform(true,tags="ja-JP")
        val coordinator=LocaleCoordinator(testPrefs(),port)
        val state=coordinator.snapshot()
        assertEquals(LocaleSelectionMode.BUILTIN,state.mode)
        assertEquals("ja",state.primaryTag)
        assertEquals("ja-JP",state.lastObservedAndroidAppLocales)
        assertEquals(0,port.writes)
    }

    @Test fun emptyPlatformStateStartsFollowingSystemWithoutWriting() {
        val port=FakePlatform(true)
        val coordinator=LocaleCoordinator(testPrefs(),port)
        assertEquals(LocaleSelectionMode.FOLLOW_SYSTEM,coordinator.snapshot().mode)
        assertEquals("zh-TW",coordinator.decision().primaryLocale)
        assertEquals(0,port.writes)
    }

    @Test fun explicitBuiltinSelectionIsPersistedAndAcknowledgedBeforeObservedCallback() {
        val port=FakePlatform(true)
        val prefs=testPrefs()
        val first=LocaleCoordinator(prefs,port)
        val result=first.dispatch(LocaleSelectionAction.SelectBuiltin("en-US"))
        assertTrue(result.accepted)
        assertEquals("PLATFORM_LANGUAGE_APPLIED",result.reason)
        assertEquals("en",port.tags)
        assertEquals(1,port.writes)
        assertEquals("en",LocaleCoordinator(prefs,port).snapshot().primaryTag)
    }

    @Test fun externalAndroidPerAppLanguageOverrideWinsAfterCustomSelection() {
        val port=FakePlatform(true,tags="en")
        val prefs=testPrefs()
        val first=LocaleCoordinator(prefs,port){setOf("ko-KR")}
        assertTrue(first.dispatch(LocaleSelectionAction.SelectCustom("ko-KR")).accepted)
        assertEquals("ko-KR",first.snapshot().primaryTag)
        assertEquals(0,port.writes)
        port.tags="ja"
        val next=LocaleCoordinator(prefs,port){setOf("ko-KR")}.snapshot()
        assertEquals(LocaleSelectionMode.BUILTIN,next.mode)
        assertEquals("ja",next.primaryTag)
    }

    @Test fun customSelectionDoesNotForceNativeEnglishOrChangeExistingOSAppLocale() {
        val port=FakePlatform(true,tags="ja")
        val c=LocaleCoordinator(testPrefs(),port){setOf("ko-KR")}
        assertTrue(c.dispatch(LocaleSelectionAction.SelectCustom("ko-KR")).accepted)
        assertEquals("ja",port.tags)
        assertEquals(0,port.writes)
        assertEquals("ko-KR",c.decision().primaryLocale)
        assertEquals("en",c.decision().nativeResourceLocale)
    }

    @Test fun explicitSystemResetPropagatesAndPreservesSecondaryPreference() {
        val p=FakePlatform(true,tags="ja")
        val c=LocaleCoordinator(testPrefs(),p)
        assertTrue(c.dispatch(LocaleSelectionAction.SelectSecondary("zh-TW")).accepted)
        assertTrue(c.dispatch(LocaleSelectionAction.SetDual(true)).accepted)
        val result=c.dispatch(LocaleSelectionAction.FollowSystem)
        assertTrue(result.accepted)
        assertEquals("",p.tags)
        assertEquals("zh-TW",c.snapshot().secondaryTag)
        assertTrue(c.snapshot().dualEnabled)
    }

    @Test fun Android12ChoiceIsStoredPreviewOnlyAndNeverCallsNonexistentApi() {
        val port=FakePlatform(false,locales=listOf("en-US"))
        val prefs=testPrefs()
        val coordinator=LocaleCoordinator(prefs,port)
        assertTrue(coordinator.dispatch(LocaleSelectionAction.SelectBuiltin("ja")).accepted)
        assertEquals(0,port.writes)
        assertEquals("ja",LocaleCoordinator(prefs,port).snapshot().primaryTag)
        assertFalse(coordinator.decision().platformOverrideSupported)
    }

    @Test fun failureToApplyNativeLocaleRestoresPreviousSelection() {
        val port=FakePlatform(true,failWrites=true)
        val coordinator=LocaleCoordinator(testPrefs(),port)
        val result=coordinator.dispatch(LocaleSelectionAction.SelectBuiltin("ja"))
        assertFalse(result.accepted)
        assertEquals("PLATFORM_LOCALE_WRITE_FAILED",result.reason)
        assertEquals(LocaleSelectionMode.FOLLOW_SYSTEM,coordinator.snapshot().mode)
    }

    @Test fun removedCustomPackRemainsRecoverableButFallsBackWithoutDroppingTag() {
        val p=FakePlatform(true,tags="")
        val prefs=testPrefs()
        val selected=LocaleCoordinator(prefs,p){setOf("ko-KR")}
        assertTrue(selected.dispatch(LocaleSelectionAction.SelectCustom("ko-KR")).accepted)
        val unavailable=LocaleCoordinator(prefs,p){emptySet()}
        assertEquals("ko-KR",unavailable.snapshot().primaryTag)
        val outcome=unavailable.decision()
        assertTrue(outcome.fallbackUsed)
        assertEquals("en",outcome.primaryLocale)
    }

    @Test fun rapidSecondaryAndDualUpdatesPersistAcrossNewCoordinatorInstance() {
        val p=FakePlatform(true)
        val prefs=testPrefs()
        val first=LocaleCoordinator(prefs,p){setOf("ko-KR")}
        assertTrue(first.dispatch(LocaleSelectionAction.SelectSecondary("ko-KR")).accepted)
        assertTrue(first.dispatch(LocaleSelectionAction.SetDual(true)).accepted)
        val reopened=LocaleCoordinator(prefs,p){setOf("ko-KR")}
        assertEquals("ko-KR",reopened.snapshot().secondaryTag)
        assertTrue(reopened.snapshot().dualEnabled)
        assertEquals("ko-KR",reopened.decision().secondaryLocale)
    }

    @Test fun malformedCustomTagRejectedBeforePersistingOrChangingAndroid() {
        val p=FakePlatform(true)
        val c=LocaleCoordinator(testPrefs(),p){setOf("ko-KR")}
        assertFalse(c.dispatch(LocaleSelectionAction.SelectCustom("../bad")).accepted)
        assertEquals(LocaleSelectionMode.FOLLOW_SYSTEM,c.snapshot().mode)
        assertEquals(0,p.writes)
    }

    @Test fun invalidPrefVersionMigratesFromPlatformRatherThanForgingApproval() {
        val p=FakePlatform(true,tags="zh-TW")
        val prefs=testPrefs()
        prefs.edit().putInt("version",999).putString("mode","CUSTOM")
            .putString("primaryTag","../bad").commit()
        val state=LocaleCoordinator(prefs,p).snapshot()
        assertEquals(LocaleSelectionMode.BUILTIN,state.mode)
        assertEquals("zh-TW",state.primaryTag)
        assertEquals(0,p.writes)
    }
}
