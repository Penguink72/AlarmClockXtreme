package com.sysadmindoc.alarmclock.ui.alarmedit

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmMusicSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playlistShowsLongMixedLanguageNameAndUnavailableWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = "宇多田ヒカル - 光 / 夏の雨 Home (Live Edition)"
        val state = settingsState(
            draft = AlarmMusicSettingsDraft(
                sourceMode = AlarmMusicSourceMode.INDEPENDENT_PLAYLIST,
                playlist = listOf(
                    AlarmMusicPlaylistTrack(
                        uri = "content://music/1",
                        title = title,
                        accessible = false
                    )
                )
            ),
            matchingCount = 0,
            unavailableCount = 1
        )
        composeRule.setContent {
            MaterialTheme {
                AlarmMusicSettingsScreen(
                    state = state,
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("alarm-music-settings-list")
            .performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.alarm_music_settings_track_unavailable)
        ).assertIsDisplayed()
    }

    @Test
    fun playbackStrategySelectionEmitsRepositoryAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actions = mutableListOf<AlarmMusicSettingsAction>()
        composeRule.setContent {
            MaterialTheme {
                AlarmMusicSettingsScreen(
                    state = settingsState(),
                    onAction = { actions.add(it) },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.alarm_music_settings_strategy_shuffle)
        ).performClick()
        composeRule.runOnIdle {
            assertTrue(
                actions.contains(
                    AlarmMusicSettingsAction.SetPlaybackStrategy(
                        AlarmMusicPlaybackStrategy.SHUFFLE_WITHOUT_REPEATS
                    )
                )
            )
        }
    }

    @Test
    fun noMatchingTracksShowsAnExplicitWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MaterialTheme {
                AlarmMusicSettingsScreen(
                    state = settingsState(matchingCount = 0),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.alarm_music_settings_empty_warning)
        )
            .assertIsDisplayed()
    }

    private fun settingsState(
        draft: AlarmMusicSettingsDraft = AlarmMusicSettingsDraft(),
        matchingCount: Int = 4,
        unavailableCount: Int = 0
    ) = AlarmMusicSettingsUiState(
        draft = draft,
        artists = emptyList(),
        folders = emptyList(),
        matchingPlayableTrackCount = matchingCount,
        unavailableTrackCount = unavailableCount
    )
}
