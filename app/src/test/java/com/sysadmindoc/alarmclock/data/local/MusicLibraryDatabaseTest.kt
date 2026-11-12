package com.sysadmindoc.alarmclock.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.repository.MusicLibraryRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun exportedSchema25MatchesEntitiesAndRoomRuntimeIdentityHash() {
        val schema25 = readSchemaJson(25).getJSONObject("database")
        assertEquals(25, schema25.getInt("version"))
        val expectedHash = "c93f6e3b9bc5f1f1bd4295c7aff61c29"
        assertEquals(expectedHash, schema25.getString("identityHash"))

        val setupQueries = schema25.getJSONArray("setupQueries").toStringList()
        assertTrue(
            setupQueries.contains(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$expectedHash')"
            )
        )

        val entities = schema25.getJSONArray("entities").toObjectMap("tableName")
        assertEquals(8, entities.size)

        val musicTracks = requireNotNull(entities["music_tracks"])
        assertEquals(
            "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`uri`))",
            musicTracks.getString("createSql")
        )
        val musicTrackFields = musicTracks.getJSONArray("fields")
        assertEquals(3, musicTrackFields.length())
        assertField(musicTrackFields.getJSONObject(0), "uri", "uri", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(1), "title", "title", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(2), "source", "source", "TEXT", true)
        val musicTracksPk = musicTracks.getJSONObject("primaryKey")
        assertFalse(musicTracksPk.getBoolean("autoGenerate"))
        assertEquals(listOf("uri"), musicTracksPk.getJSONArray("columnNames").toStringList())
        assertEquals(0, musicTracks.getJSONArray("indices").length())
        assertEquals(0, musicTracks.getJSONArray("foreignKeys").length())

        val playbackHistory = requireNotNull(entities["playback_history"])
        assertEquals(
            "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fireId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `playOrder` INTEGER NOT NULL, `title` TEXT NOT NULL, `uri` TEXT NOT NULL)",
            playbackHistory.getString("createSql")
        )
        val historyFields = playbackHistory.getJSONArray("fields")
        assertEquals(6, historyFields.length())
        assertField(historyFields.getJSONObject(0), "id", "id", "INTEGER", true)
        assertField(historyFields.getJSONObject(1), "fireId", "fireId", "TEXT", true)
        assertField(historyFields.getJSONObject(2), "startedAt", "startedAt", "INTEGER", true)
        assertField(historyFields.getJSONObject(3), "playOrder", "playOrder", "INTEGER", true)
        assertField(historyFields.getJSONObject(4), "title", "title", "TEXT", true)
        assertField(historyFields.getJSONObject(5), "uri", "uri", "TEXT", true)
        val historyPk = playbackHistory.getJSONObject("primaryKey")
        assertTrue(historyPk.getBoolean("autoGenerate"))
        assertEquals(listOf("id"), historyPk.getJSONArray("columnNames").toStringList())

        val historyIndices = playbackHistory.getJSONArray("indices")
        assertEquals(2, historyIndices.length())
        val startedAtIdx = historyIndices.getJSONObject(0)
        assertEquals("index_playback_history_startedAt", startedAtIdx.getString("name"))
        assertFalse(startedAtIdx.getBoolean("unique"))
        assertEquals(listOf("startedAt"), startedAtIdx.getJSONArray("columnNames").toStringList())
        assertEquals(
            "CREATE INDEX IF NOT EXISTS `index_playback_history_startedAt` ON `\${TABLE_NAME}` (`startedAt`)",
            startedAtIdx.getString("createSql")
        )

        val fireIdOrderIdx = historyIndices.getJSONObject(1)
        assertEquals("index_playback_history_fireId_playOrder", fireIdOrderIdx.getString("name"))
        assertTrue(fireIdOrderIdx.getBoolean("unique"))
        assertEquals(listOf("fireId", "playOrder"), fireIdOrderIdx.getJSONArray("columnNames").toStringList())
        assertEquals(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_history_fireId_playOrder` ON `\${TABLE_NAME}` (`fireId`, `playOrder`)",
            fireIdOrderIdx.getString("createSql")
        )
    }

    @Test
    fun migrationFromExportedSchema24To25PreservesFullAlarmRowAndMatchesSchema25() {
        val schema24 = readSchemaJson(24).getJSONObject("database")
        val schema25 = readSchemaJson(25).getJSONObject("database")
        val context = ApplicationProvider.getApplicationContext<Application>()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities24 = schema24.getJSONArray("entities")
                        for (i in 0 until entities24.length()) {
                            val entity = entities24.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            val indices = entity.getJSONArray("indices")
                            for (j in 0 until indices.length()) {
                                val idx = indices.getJSONObject(j)
                                db.execSQL(idx.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            }
                        }
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        try {
            val alarms24Fields = schema24.getJSONArray("entities")
                .toObjectMap("tableName")
                .getValue("alarms")
                .getJSONArray("fields")
            val colNames = mutableListOf<String>()
            val colValues = mutableListOf<Any>()
            for (i in 0 until alarms24Fields.length()) {
                val field = alarms24Fields.getJSONObject(i)
                val name = field.getString("columnName")
                val affinity = field.getString("affinity")
                colNames += "`$name`"
                colValues += when (name) {
                    "id" -> 42L
                    "hour" -> 6
                    "minute" -> 45
                    "label" -> "Weekday Wake"
                    "isEnabled" -> 1
                    "repeatDays" -> "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
                    "ringtoneUri" -> "content://media/internal/audio/media/99"
                    "ringtonePool" -> "content://media/1,content://media/2"
                    "timezonePolicy" -> "LOCAL"
                    "fixedTimezoneId" -> "Asia/Taipei"
                    else -> when (affinity) {
                        "INTEGER" -> 0
                        "REAL" -> 0.0
                        else -> ""
                    }
                }
            }
            val placeholders = List(colNames.size) { "?" }.joinToString(", ")
            db.execSQL(
                "INSERT INTO `alarms` (${colNames.joinToString(", ")}) VALUES ($placeholders)",
                colValues.toTypedArray()
            )

            AlarmDatabase.MIGRATION_24_25.migrate(db)

            db.query(
                "SELECT id, hour, minute, label, isEnabled, repeatDays, ringtoneUri, ringtonePool, timezonePolicy, fixedTimezoneId, randomMusicEnabled FROM alarms WHERE id = 42"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertEquals(6, cursor.getInt(1))
                assertEquals(45, cursor.getInt(2))
                assertEquals("Weekday Wake", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", cursor.getString(5))
                assertEquals("content://media/internal/audio/media/99", cursor.getString(6))
                assertEquals("content://media/1,content://media/2", cursor.getString(7))
                assertEquals("LOCAL", cursor.getString(8))
                assertEquals("Asia/Taipei", cursor.getString(9))
                assertEquals(0, cursor.getInt(10))
            }

            val entities25 = schema25.getJSONArray("entities")
            for (i in 0 until entities25.length()) {
                val entity = entities25.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val expectedColumns = entity.getJSONArray("fields")
                val expectedPkCols = entity.getJSONObject("primaryKey")
                    .getJSONArray("columnNames")
                    .toStringList()

                val actualColumns = mutableMapOf<String, Triple<String, Boolean, Int>>()
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow("name")
                    val typeIdx = cursor.getColumnIndexOrThrow("type")
                    val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
                    val pkIdx = cursor.getColumnIndexOrThrow("pk")
                    while (cursor.moveToNext()) {
                        actualColumns[cursor.getString(nameIdx)] = Triple(
                            cursor.getString(typeIdx),
                            cursor.getInt(notNullIdx) != 0,
                            cursor.getInt(pkIdx)
                        )
                    }
                }
                assertEquals("Column count mismatch in $tableName", expectedColumns.length(), actualColumns.size)
                for (j in 0 until expectedColumns.length()) {
                    val field = expectedColumns.getJSONObject(j)
                    val colName = field.getString("columnName")
                    val actual = actualColumns[colName]
                    assertNotNull("Missing column $tableName.$colName", actual)
                    assertEquals("Affinity mismatch for $tableName.$colName", field.getString("affinity"), actual!!.first)
                    assertEquals("NotNull mismatch for $tableName.$colName", field.getBoolean("notNull"), actual.second)
                    val expectedPkPos = expectedPkCols.indexOf(colName) + 1
                    assertEquals("PK position mismatch for $tableName.$colName", expectedPkPos, actual.third)
                }

                val expectedIndices = entity.getJSONArray("indices")
                val actualIndices = mutableMapOf<String, Pair<Boolean, List<String>>>()
                db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow("name")
                    val uniqueIdx = cursor.getColumnIndexOrThrow("unique")
                    val originIdx = cursor.getColumnIndexOrThrow("origin")
                    while (cursor.moveToNext()) {
                        val origin = cursor.getString(originIdx)
                        if (origin == "pk") continue
                        val idxName = cursor.getString(nameIdx)
                        val isUnique = cursor.getInt(uniqueIdx) != 0
                        val idxCols = mutableListOf<String>()
                        db.query("PRAGMA index_info(`$idxName`)").use { infoCursor ->
                            val colNameIdx = infoCursor.getColumnIndexOrThrow("name")
                            while (infoCursor.moveToNext()) {
                                idxCols += infoCursor.getString(colNameIdx)
                            }
                        }
                        actualIndices[idxName] = isUnique to idxCols
                    }
                }
                assertEquals("Index count mismatch in $tableName", expectedIndices.length(), actualIndices.size)
                for (j in 0 until expectedIndices.length()) {
                    val idx = expectedIndices.getJSONObject(j)
                    val idxName = idx.getString("name")
                    val actualIdx = actualIndices[idxName]
                    assertNotNull("Missing index $idxName on $tableName", actualIdx)
                    assertEquals("Unique flag mismatch on $idxName", idx.getBoolean("unique"), actualIdx!!.first)
                    assertEquals(
                        "Columns mismatch on $idxName",
                        idx.getJSONArray("columnNames").toStringList(),
                        actualIdx.second
                    )
                }
            }

            // Verify unique constraints on music_tracks(uri) and playback_history(fireId, playOrder)
            db.execSQL("INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/dup', 'Track A', 'SAF')")
            try {
                db.execSQL("INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/dup', 'Track B', 'SAF')")
                fail("Expected SQLiteConstraintException for duplicate music_tracks.uri")
            } catch (_: SQLiteConstraintException) {
                // expected
            }

            db.execSQL("INSERT INTO playback_history (fireId, startedAt, playOrder, title, uri) VALUES ('fire-unique', 1000, 1, 'Track A', 'content://music/dup')")
            try {
                db.execSQL("INSERT INTO playback_history (fireId, startedAt, playOrder, title, uri) VALUES ('fire-unique', 2000, 1, 'Track B', 'content://music/dup')")
                fail("Expected SQLiteConstraintException for duplicate (fireId, playOrder)")
            } catch (_: SQLiteConstraintException) {
                // expected
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun migrationTwentyFiveToTwentySixPreservesIndexedTrackAndHistoryData() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE music_tracks (uri TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, source TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE playback_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fireId TEXT NOT NULL, startedAt INTEGER NOT NULL, playOrder INTEGER NOT NULL, title TEXT NOT NULL, uri TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/1', '晴天', 'MEDIA_STORE')"
                        )
                        db.execSQL(
                            "INSERT INTO playback_history (fireId, startedAt, playOrder, title, uri) VALUES ('fire-1', 1234, 1, '晴天', 'content://music/1')"
                        )
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
            AlarmDatabase.MIGRATION_25_26.migrate(db)

            db.query("SELECT title, source, fileName, metadataTitle FROM music_tracks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("晴天", cursor.getString(0))
                assertEquals("MEDIA_STORE", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
            }
            db.query("SELECT fireId, startedAt, playOrder, title, uri FROM playback_history").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("fire-1", cursor.getString(0))
                assertEquals(1234L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("晴天", cursor.getString(3))
                assertEquals("content://music/1", cursor.getString(4))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun exportedSchema26MatchesEntitiesAndRoomRuntimeIdentityHash() {
        val schema26 = readSchemaJson(26).getJSONObject("database")
        assertEquals(26, schema26.getInt("version"))
        val expectedHash = schema26.getString("identityHash")
        assertTrue(expectedHash.isNotBlank())

        val setupQueries = schema26.getJSONArray("setupQueries").toStringList()
        assertTrue(
            setupQueries.contains(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$expectedHash')"
            )
        )

        val entities = schema26.getJSONArray("entities").toObjectMap("tableName")
        assertEquals(8, entities.size)

        val musicTracks = requireNotNull(entities["music_tracks"])
        val musicTrackFields = musicTracks.getJSONArray("fields")
        assertEquals(5, musicTrackFields.length())
        assertField(musicTrackFields.getJSONObject(0), "uri", "uri", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(1), "title", "title", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(2), "source", "source", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(3), "fileName", "fileName", "TEXT", true)
        assertField(musicTrackFields.getJSONObject(4), "metadataTitle", "metadataTitle", "TEXT", true)
        val musicTracksPk = musicTracks.getJSONObject("primaryKey")
        assertFalse(musicTracksPk.getBoolean("autoGenerate"))
        assertEquals(listOf("uri"), musicTracksPk.getJSONArray("columnNames").toStringList())

        val context: Context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val writableDb = database.openHelper.writableDatabase
            writableDb.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expectedHash, cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromExportedSchema24To25To26PreservesAlarmsTracksAndHistoryAndMatchesSchema26() {
        val schema24 = readSchemaJson(24).getJSONObject("database")
        val schema26 = readSchemaJson(26).getJSONObject("database")
        val context = ApplicationProvider.getApplicationContext<Application>()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities24 = schema24.getJSONArray("entities")
                        for (i in 0 until entities24.length()) {
                            val entity = entities24.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            val indices = entity.getJSONArray("indices")
                            for (j in 0 until indices.length()) {
                                val idx = indices.getJSONObject(j)
                                db.execSQL(idx.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            }
                        }
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        try {
            val alarms24Fields = schema24.getJSONArray("entities")
                .toObjectMap("tableName")
                .getValue("alarms")
                .getJSONArray("fields")
            val colNames = mutableListOf<String>()
            val colValues = mutableListOf<Any>()
            for (i in 0 until alarms24Fields.length()) {
                val field = alarms24Fields.getJSONObject(i)
                val name = field.getString("columnName")
                val affinity = field.getString("affinity")
                colNames += "`$name`"
                colValues += when (name) {
                    "id" -> 42L
                    "hour" -> 7
                    "minute" -> 15
                    "label" -> "Morning Wake"
                    "isEnabled" -> 1
                    else -> when (affinity) {
                        "INTEGER" -> 0
                        "REAL" -> 0.0
                        else -> ""
                    }
                }
            }
            val placeholders = List(colNames.size) { "?" }.joinToString(", ")
            db.execSQL(
                "INSERT INTO `alarms` (${colNames.joinToString(", ")}) VALUES ($placeholders)",
                colValues.toTypedArray()
            )

            // Step 1: 24 -> 25
            AlarmDatabase.MIGRATION_24_25.migrate(db)
            db.execSQL(
                "INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/1', '晴天', 'MEDIA_STORE')"
            )
            db.execSQL(
                "INSERT INTO music_tracks (uri, title, source) VALUES ('content://music/2', '宇多田ヒカル - 光', 'SAF')"
            )
            db.execSQL(
                "INSERT INTO playback_history (fireId, startedAt, playOrder, title, uri) VALUES ('fire-25', 1700000000000, 1, '晴天', 'content://music/1')"
            )

            // Step 2: 25 -> 26
            AlarmDatabase.MIGRATION_25_26.migrate(db)

            db.query("SELECT id, hour, minute, label, isEnabled, randomMusicEnabled FROM alarms WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals(15, cursor.getInt(2))
                assertEquals("Morning Wake", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
            }

            db.query("SELECT uri, title, source, fileName, metadataTitle FROM music_tracks ORDER BY uri").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("content://music/1", cursor.getString(0))
                assertEquals("晴天", cursor.getString(1))
                assertEquals("MEDIA_STORE", cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals("", cursor.getString(4))
                assertTrue(cursor.moveToNext())
                assertEquals("content://music/2", cursor.getString(0))
                assertEquals("宇多田ヒカル - 光", cursor.getString(1))
                assertEquals("SAF", cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals("", cursor.getString(4))
            }

            db.query("SELECT fireId, startedAt, playOrder, title, uri FROM playback_history WHERE fireId = 'fire-25'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("fire-25", cursor.getString(0))
                assertEquals(1700000000000L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("晴天", cursor.getString(3))
                assertEquals("content://music/1", cursor.getString(4))
            }

            val entities26 = schema26.getJSONArray("entities")
            for (i in 0 until entities26.length()) {
                val entity = entities26.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val expectedColumns = entity.getJSONArray("fields")
                val actualColumns = mutableMapOf<String, Triple<String, Boolean, Int>>()
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow("name")
                    val typeIdx = cursor.getColumnIndexOrThrow("type")
                    val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
                    val pkIdx = cursor.getColumnIndexOrThrow("pk")
                    while (cursor.moveToNext()) {
                        actualColumns[cursor.getString(nameIdx)] = Triple(
                            cursor.getString(typeIdx),
                            cursor.getInt(notNullIdx) != 0,
                            cursor.getInt(pkIdx)
                        )
                    }
                }
                assertEquals("Column count mismatch in $tableName", expectedColumns.length(), actualColumns.size)
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun playbackHistoryKeepsTheRecordedDisplayTitleAfterLibraryRefresh() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = MusicLibraryRepository(
                database.musicLibraryDao(),
                database.playbackHistoryDao()
            )
            val original = MusicTrack(
                uri = "content://music/1",
                title = "晴天",
                source = "MEDIA_STORE",
                fileName = "周杰倫 - 晴天.mp3",
                metadataTitle = "晴天"
            )
            repository.replaceTracks(listOf(original))
            repository.recordStarted("fire-1", 1_234L, original)

            repository.replaceTracks(
                listOf(original.copy(title = "Changed", metadataTitle = "Changed"))
            )

            val history = repository.historyForDate(0L, 2_000L)
            assertEquals(1, history.size)
            assertEquals("晴天", history.single().title)
            assertEquals("content://music/1", history.single().uri)
        } finally {
            database.close()
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

    private fun readSchemaJson(version: Int): JSONObject {
        val relative = "schemas/com.sysadmindoc.alarmclock.data.local.AlarmDatabase/$version.json"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("Could not locate Room schema file: $relative")
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun assertField(
        field: JSONObject,
        expectedFieldPath: String,
        expectedColumnName: String,
        expectedAffinity: String,
        expectedNotNull: Boolean
    ) {
        assertEquals(expectedFieldPath, field.getString("fieldPath"))
        assertEquals(expectedColumnName, field.getString("columnName"))
        assertEquals(expectedAffinity, field.getString("affinity"))
        assertEquals(expectedNotNull, field.getBoolean("notNull"))
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    private fun JSONArray.toObjectMap(keyField: String): Map<String, JSONObject> =
        (0 until length()).map { getJSONObject(it) }.associateBy { it.getString(keyField) }
}
