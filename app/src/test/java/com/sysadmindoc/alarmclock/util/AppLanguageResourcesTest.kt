package com.sysadmindoc.alarmclock.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLanguageResourcesTest {
    @Test
    fun settingsMusicAndNavigationLabelsFollowTheSelectedLocale() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        data class Labels(
            val localeTag: String,
            val settings: String,
            val defaults: String,
            val musicLibrary: String,
            val stopwatch: String
        )
        val cases = listOf(
            Labels(
                "en", "Settings", "Defaults",
                "Music library and playback history", "Stopwatch"
            ),
            Labels(
                "zh-TW", "設定", "預設設定",
                "音樂庫與播放歷史", "碼錶"
            ),
            Labels(
                "ja", "設定", "初期設定",
                "音楽ライブラリと再生履歴", "ストップウォッチ"
            )
        )
        cases.forEach { expected ->
            val configuration = Configuration(appContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(expected.localeTag))
            }
            val localized = appContext.createConfigurationContext(configuration)
            assertEquals(expected.localeTag, expected.settings, localized.getString(R.string.settings_title))
            assertEquals(expected.localeTag, expected.defaults, localized.getString(R.string.settings_pane_defaults))
            assertEquals(expected.localeTag, expected.musicLibrary, localized.getString(R.string.music_library_title))
            assertEquals(expected.localeTag, expected.stopwatch, localized.getString(R.string.nav_stopwatch))
        }
    }
}
