package com.sysadmindoc.alarmclock.data.repository

import com.sysadmindoc.alarmclock.data.local.MusicLibraryDao
import com.sysadmindoc.alarmclock.data.local.PlaybackHistoryDao
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLibraryRepository @Inject constructor(
    private val musicLibraryDao: MusicLibraryDao,
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    suspend fun count(): Int = musicLibraryDao.count()

    suspend fun allTracks(): List<MusicTrack> = musicLibraryDao.getAll()

    suspend fun replaceTracks(tracks: List<MusicTrack>) = musicLibraryDao.replaceAll(tracks)

    suspend fun pickRandomTrack(): MusicTrack? = musicLibraryDao.pickRandom()

    suspend fun pickRandomTrack(excludedUris: List<String>): MusicTrack? =
        if (excludedUris.isEmpty()) musicLibraryDao.pickRandom()
        else musicLibraryDao.pickRandomExcluding(excludedUris)

    suspend fun recordStarted(fireId: String, startedAt: Long, track: MusicTrack) {
        playbackHistoryDao.appendStarted(fireId, startedAt, track.displayTitle, track.uri)
    }

    suspend fun historyForDate(fromMillis: Long, toMillis: Long) =
        playbackHistoryDao.getForDate(fromMillis, toMillis)
}
