package com.sysadmindoc.alarmclock.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MusicLibraryDatabaseTest {

    @Test
    fun migrationTwentyFourToTwentyFivePreservesAlarmsAndCreatesMusicTables() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `label` TEXT NOT NULL)"
                        )
                        db.execSQL("INSERT INTO `alarms` (`id`, `hour`, `minute`, `label`) VALUES (1, 7, 30, 'Morning')")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            AlarmDatabase.MIGRATION_24_25.migrate(db)

            db.query("SELECT hour, minute, label, randomMusicEnabled FROM alarms WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
                assertEquals(30, cursor.getInt(1))
                assertEquals("Morning", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
            }

            db.execSQL("INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/1', 'Song 1', 'MEDIA_STORE')")
            db.query("SELECT COUNT(*) FROM music_tracks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            db.execSQL("INSERT INTO playback_history (fireId, startedAt, playOrder, title, uri) VALUES ('fire-1', 1000, 1, 'Song 1', 'content://music/1')")
            db.query("SELECT playOrder, title FROM playback_history WHERE fireId = 'fire-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("Song 1", cursor.getString(1))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun musicLibraryRepositoryStoresThreeThousandTracksAndAppendsStartedHistoryInOrder() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = MusicLibraryRepository(
                database.musicLibraryDao(),
                database.playbackHistoryDao()
            )
            val tracks = (0 until 3_001).map { index ->
                MusicTrack(
                    uri = "content://music/$index",
                    title = "Track $index",
                    source = "TEST"
                )
            }
            repository.replaceTracks(tracks)

            assertEquals(3_001, repository.count())
            val picked = repository.pickRandomTrack()
            assertNotNull(picked)
            assertTrue(tracks.any { it.uri == picked!!.uri })

            // Verify A -> A duplicate playback is recorded as distinct playOrder entries
            repository.recordStarted("fire-a", 1_000L, tracks[0])
            repository.recordStarted("fire-a", 2_000L, tracks[0])
            repository.recordStarted("fire-a", 3_000L, tracks[1])

            val history = repository.historyForDate(0L, 5_000L)
            assertEquals(listOf(1, 2, 3), history.map { it.playOrder })
            assertEquals(listOf("Track 0", "Track 0", "Track 1"), history.map { it.title })
        } finally {
            database.close()
        }
    }
}
