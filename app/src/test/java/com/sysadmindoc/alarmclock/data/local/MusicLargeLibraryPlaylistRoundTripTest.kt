package com.sysadmindoc.alarmclock.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicPlaybackStrategy
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsDraft
import com.sysadmindoc.alarmclock.ui.ringtone.shouldAutoScanMusicCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * U1.2 -> U2 integration contract: an indexed 4,935-track library supports
 * bulk selection, playlist creation, per-alarm binding and persisted readback
 * without a new SAF scan or changing the music library's original rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MusicLargeLibraryPlaylistRoundTripTest {
    @Test fun indexed4935TracksBulkPlaylistAlarmSaveAndReadback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val first = MusicLibraryRepository(
                database.musicLibraryDao(), database.playbackHistoryDao()
            )
            val tracks = (0 until 4_935).map { i ->
                MusicTrack(
                    uri = "content://indexed/music/$i",
                    title = "Track $i",
                    source = "MEDIA_STORE",
                    fileName = "track-$i.mp3"
                )
            }
            first.replaceTracks(tracks)
            assertEquals(4_935, first.count())
            assertFalse(shouldAutoScanMusicCatalog(
                autoScanIfEmpty = true,
                indexedTrackCount = first.count(),
                hasAudioPermission = true,
                hasFolderSources = true
            ))

            // Take more than a page's worth of noncontiguous songs, just as
            // a user does when searching a 4,935-track device library.
            val chosen = tracks.filterIndexed { index, _ -> index % 22 == 0 }
            assertTrue(chosen.size >= 200)
            val playlist = first.create("Morning favorites")
            val added = first.addTracks(playlist.playlistId, chosen.map { it.uri })
            assertEquals(chosen.size, added.addedItems)
            assertEquals(chosen.size, added.autoIncludedCandidates)
            assertEquals(chosen.size, first.includedPoolCount())

            first.saveSettings(
                alarmId = 4_935L,
                draft = AlarmMusicSettingsDraft(
                    playlistId = playlist.playlistId,
                    playbackStrategy = AlarmMusicPlaybackStrategy.PLAYLIST_ORDER
                )
            )

            val reopened = MusicLibraryRepository(
                database.musicLibraryDao(), database.playbackHistoryDao()
            )
            assertEquals(4_935, reopened.count())
            val persisted = reopened.getSettings(4_935L)
            assertEquals(playlist.playlistId, persisted.playlistId)
            assertEquals(AlarmMusicPlaybackStrategy.PLAYLIST_ORDER, persisted.playbackStrategy)
            val playable = reopened.loadPlaylistSnapshotForAlarm(4_935L).eligibleEntries()
            assertEquals(chosen.size, playable.size)
            assertEquals(chosen.map { it.uri }.toSet(), playable.map { it.uri }.toSet())
            assertEquals(4_935 - chosen.size, reopened.poolCandidates().count {
                it.membershipState == null
            })
        } finally {
            database.close()
        }
    }
}
