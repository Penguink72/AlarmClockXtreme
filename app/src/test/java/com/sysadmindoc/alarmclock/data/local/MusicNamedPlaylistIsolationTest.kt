package com.sysadmindoc.alarmclock.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import com.sysadmindoc.alarmclock.ui.alarmedit.AlarmMusicSettingsDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class MusicNamedPlaylistIsolationTest {
    @Test fun namedListHistoryFavoritesAndSmartAllAreIndependentlyEditable() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val db=Room.inMemoryDatabaseBuilder(context,AlarmDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo=MusicLibraryRepository(db.musicLibraryDao(),db.playbackHistoryDao())
            val a=MusicTrack("content://test/a","A","MEDIA_STORE")
            val b=MusicTrack("content://test/b","B","MEDIA_STORE")
            val c=MusicTrack("content://test/c","C","MEDIA_STORE")
            repo.replaceTracks(listOf(a,b,c))
            val named=repo.create("Morning")
            val result=repo.addTracks(named.playlistId,listOf(a.uri,b.uri))
            assertEquals(2,result.addedItems)
            assertEquals(0,result.autoIncludedCandidates)
            assertEquals(0,repo.includedPoolCount())
            assertEquals(2,repo.getPlaylistsSnapshot().first {
                it.playlistId == named.playlistId }.playableTrackCount)
            repo.setPoolMembership(listOf(c.uri),true)
            repo.saveSettings(42,AlarmMusicSettingsDraft(playlistId=named.playlistId))
            assertEquals(setOf(a.uri,b.uri),
                repo.loadPlaylistSnapshotForAlarm(42).eligibleEntries().map{it.uri}.toSet())
            assertEquals(setOf(c.uri),
                repo.loadPlaylistSnapshotForAlarm(43).eligibleEntries().map{it.uri}.toSet())

            repo.recordStarted("fire",1234,a)
            val favorites=repo.ensureFavorites()
            assertEquals("favorites",favorites.playlistId)
            assertEquals(1,repo.addHistoryTrack(favorites.playlistId,a.uri).addedItems)
            assertEquals(0,repo.addHistoryTrack(favorites.playlistId,a.uri).addedItems)
            assertEquals(1,repo.recentHistory().size)
            assertEquals(1,repo.getPlaylistsSnapshot().first{
                it.playlistId == favorites.playlistId }.playableTrackCount)
            assertEquals(1,repo.includedPoolCount())

            repo.setPoolMembership(listOf(a.uri),false)
            assertEquals(setOf(b.uri),
                repo.loadPlaylistSnapshotForAlarm(42).eligibleEntries().map{it.uri}.toSet())
            assertEquals(0,repo.getPlaylistsSnapshot().first{
                it.playlistId == favorites.playlistId }.playableTrackCount)
            assertEquals(setOf(c.uri),
                repo.loadPlaylistSnapshotForAlarm(43).eligibleEntries().map{it.uri}.toSet())
            assertEquals(1,repo.recentHistory().size)
            assertEquals(MusicPoolEntry.EXCLUDED,repo.poolCandidates().first{
                it.track.uri == a.uri }.membershipState)
        } finally {db.close()}
    }
}
