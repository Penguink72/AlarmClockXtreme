package com.sysadmindoc.alarmclock.ui.alarmedit

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                playlistId = "playlist-1"
            ),
            playlists = listOf(
                AlarmMusicPlaylistOption(
                    playlistId = "playlist-1",
                    name = title,
                    kind = com.sysadmindoc.alarmclock.ui.ringtone.MusicPlaylistKind.NAMED,
                    playableTrackCount = 0,
                    unavailableTrackCount = 1
                )
            ),
            matchingCount = 0,
            unavailableCount = 1,
            integrationAvailable = true
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

        composeRule.onNodeWithTag("alarm-music-playlist-list").assertIsDisplayed()
        composeRule.onNodeWithText(title).assertExists()
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
                    state = settingsState(integrationAvailable = true),
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
                    state = settingsState(matchingCount = 0, integrationAvailable = true),
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

    @Test
    fun missingRoomIntegrationShowsPendingStateAndDisablesSave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MaterialTheme {
                AlarmMusicSettingsScreen(
                    state = settingsState(),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.alarm_music_settings_pending)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.alarm_music_settings_save)
        ).assertIsNotEnabled()
    }

    private fun settingsState(
        draft: AlarmMusicSettingsDraft = AlarmMusicSettingsDraft(),
        playlists: List<AlarmMusicPlaylistOption> = emptyList(),
        matchingCount: Int = 4,
        unavailableCount: Int = 0,
        integrationAvailable: Boolean = false
    ) = AlarmMusicSettingsUiState(
        draft = draft,
        playlists = playlists,
        artists = emptyList(),
        folders = emptyList(),
        matchingPlayableTrackCount = matchingCount,
        unavailableTrackCount = unavailableCount,
        integrationAvailable = integrationAvailable
    )
}
