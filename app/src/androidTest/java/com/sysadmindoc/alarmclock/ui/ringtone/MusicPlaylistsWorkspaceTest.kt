package com.sysadmindoc.alarmclock.ui.ringtone

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicPlaylistsWorkspaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createsNamedPlaylistAndAddsTwentySelectedTracks() {
        val repository = InMemoryPlaylistRepository()
        val selectedUris = (1..20).map { "content://media/audio/$it" }
        composeRule.setContent {
            MaterialTheme {
                MusicPlaylistsWorkspace(
                    repository = repository,
                    selectedTrackUris = selectedUris,
                    initialTargetPlaylistId = null,
                    onSelectionHandled = {},
                    onAddTracksToPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNodeWithText("Playlist name").performTextInput("Morning Japanese")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertEquals("Morning Japanese", repository.playlists.value.single().name)
        assertEquals(20, repository.items.value.single().size)
        composeRule.onNodeWithText("Added 20 tracks to the playlist.").assertIsDisplayed()
    }

    @Test
    fun removingDuplicateUriUsesOnlyTheSelectedPlaylistItemId() {
        val duplicateUri = "content://media/audio/shared"
        val repository = InMemoryPlaylistRepository(
            initialPlaylists = listOf(
                MusicPlaylist(
                    playlistId = "playlist-1",
                    name = "Morning Japanese",
                    kind = MusicPlaylistKind.NAMED,
                    playableTrackCount = 2
                )
            ),
            initialItems = listOf(
                MusicPlaylistItem(
                    playlistId = "playlist-1",
                    playlistItemId = "item-1",
                    trackUri = duplicateUri,
                    position = 0,
                    displayTitle = "夏の雨 - Home",
                    availability = MediaAvailability.PLAYABLE
                ),
                MusicPlaylistItem(
                    playlistId = "playlist-1",
                    playlistItemId = "item-2",
                    trackUri = duplicateUri,
                    position = 1,
                    displayTitle = "夏の雨 - Home",
                    availability = MediaAvailability.PLAYABLE
                )
            )
        )
        composeRule.setContent {
            MaterialTheme {
                MusicPlaylistsWorkspace(
                    repository = repository,
                    selectedTrackUris = emptyList(),
                    initialTargetPlaylistId = "playlist-1",
                    onSelectionHandled = {},
                    onAddTracksToPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist-remove-item-1").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("item-2"), repository.items.value.single().map { it.playlistItemId })
        composeRule.onNodeWithTag("playlist-item-item-2").assertIsDisplayed()
    }

    @Test
    fun smartPlaylistItemsAreReadOnly() {
        val repository = InMemoryPlaylistRepository(
            initialPlaylists = listOf(
                MusicPlaylist(
                    playlistId = "all",
                    name = "All available music",
                    kind = MusicPlaylistKind.SMART_ALL,
                    playableTrackCount = 1
                )
            ),
            initialItems = listOf(
                MusicPlaylistItem(
                    playlistId = "all",
                    playlistItemId = "all-item",
                    trackUri = "content://media/audio/1",
                    position = 0,
                    displayTitle = "Morning song",
                    availability = MediaAvailability.PLAYABLE
                )
            )
        )
        composeRule.setContent {
            MaterialTheme {
                MusicPlaylistsWorkspace(
                    repository = repository,
                    selectedTrackUris = listOf("content://media/audio/2"),
                    initialTargetPlaylistId = "all",
                    onSelectionHandled = {},
                    onAddTracksToPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist-remove-all-item").assertIsNotEnabled()
        composeRule.onNodeWithText("Add 1 selected").assertDoesNotExist()
    }
}

private class InMemoryPlaylistRepository(
    initialPlaylists: List<MusicPlaylist> = emptyList(),
    initialItems: List<MusicPlaylistItem> = emptyList()
) : MusicPlaylistRepository {
    val playlists = MutableStateFlow(initialPlaylists)
    val items = MutableStateFlow(initialItems)
    private val nextId = AtomicInteger(1)
    private val itemFlows = mutableMapOf<String, MutableStateFlow<List<MusicPlaylistItem>>>()

    override fun observePlaylists(): Flow<List<MusicPlaylist>> = playlists

    override fun observeItems(playlistId: String): Flow<List<MusicPlaylistItem>> =
        itemFlows.getOrPut(playlistId) {
            MutableStateFlow(items.value.filter { it.playlistId == playlistId })
        }

    override suspend fun create(name: String): MusicPlaylist {
        val playlist = MusicPlaylist(
            playlistId = "playlist-${nextId.getAndIncrement()}",
            name = name,
            kind = MusicPlaylistKind.NAMED,
            playableTrackCount = 0
        )
        playlists.value = playlists.value + playlist
        return playlist
    }

    override suspend fun rename(playlistId: String, name: String): MusicPlaylist {
        val updated = playlists.value.first { it.playlistId == playlistId }.copy(name = name)
        playlists.value = playlists.value.map {
            if (it.playlistId == playlistId) updated else it
        }
        return updated
    }

    override suspend fun duplicate(playlistId: String, ownerAlarmId: Long?): MusicPlaylist {
        val source = playlists.value.first { it.playlistId == playlistId }
        return create("${source.name} copy")
    }

    override suspend fun addTracks(
        playlistId: String,
        selectedUris: List<String>,
        excludedResolution: ExcludedTrackResolution
    ): AddTracksResult {
        val startingPosition = items.value.count { it.playlistId == playlistId }
        val added = selectedUris.mapIndexed { index, uri ->
            MusicPlaylistItem(
                playlistId = playlistId,
                playlistItemId = "item-${nextId.getAndIncrement()}",
                trackUri = uri,
                position = startingPosition + index,
                displayTitle = uri.substringAfterLast('/'),
                availability = MediaAvailability.PLAYABLE
            )
        }
        items.value = items.value + added
        publishItems(playlistId)
        updateCount(playlistId)
        return AddTracksResult(addedItems = added.size)
    }

    override suspend fun removeItem(playlistItemId: String) {
        val playlistId = items.value.first { it.playlistItemId == playlistItemId }.playlistId
        items.value = items.value.filterNot { it.playlistItemId == playlistItemId }
        publishItems(playlistId)
        updateCount(playlistId)
    }

    override suspend fun removeItems(playlistId: String, playlistItemIds: List<String>) {
        items.value = items.value.filterNot {
            it.playlistId == playlistId && it.playlistItemId in playlistItemIds
        }
        publishItems(playlistId)
        updateCount(playlistId)
    }

    override suspend fun reorder(playlistId: String, orderedPlaylistItemIds: List<String>) {
        val order = orderedPlaylistItemIds.withIndex().associate { it.value to it.index }
        items.value = items.value.map { item ->
            if (item.playlistId == playlistId) {
                item.copy(position = order.getValue(item.playlistItemId))
            } else {
                item
            }
        }
        publishItems(playlistId)
    }

    override suspend fun deleteWithReferencesCheck(
        playlistId: String
    ): DeletePlaylistResult {
        playlists.value = playlists.value.filterNot { it.playlistId == playlistId }
        items.value = items.value.filterNot { it.playlistId == playlistId }
        return DeletePlaylistResult.Deleted
    }

    private fun updateCount(playlistId: String) {
        val count = items.value.count { it.playlistId == playlistId }
        playlists.value = playlists.value.map {
            if (it.playlistId == playlistId) it.copy(playableTrackCount = count) else it
        }
    }

    private fun publishItems(playlistId: String) {
        itemFlows[playlistId]?.value = items.value.filter { it.playlistId == playlistId }
    }
}
