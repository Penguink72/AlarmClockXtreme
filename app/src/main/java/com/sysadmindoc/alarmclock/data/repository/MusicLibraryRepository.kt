package com.sysadmindoc.alarmclock.data.repository

import com.sysadmindoc.alarmclock.data.local.MusicLibraryDao
import com.sysadmindoc.alarmclock.data.local.MusicPoolTrack
import com.sysadmindoc.alarmclock.data.local.PlaybackHistoryDao
import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import com.sysadmindoc.alarmclock.data.local.entity.displayTitle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLibraryRepository @Inject constructor(
    private val musicLibraryDao: MusicLibraryDao,
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    private companion object {
        const val MAX_EXCLUDED_URIS_PER_QUERY = 900
    }

    suspend fun count(): Int = musicLibraryDao.count()

    suspend fun allTracks(): List<MusicTrack> = musicLibraryDao.getAll()

    suspend fun replaceTracks(tracks: List<MusicTrack>) = musicLibraryDao.replaceAll(tracks)

    suspend fun poolCandidates(): List<MusicPoolTrack> = musicLibraryDao.getPoolCandidates()

    suspend fun includedPoolCount(): Int = musicLibraryDao.includedCount()

    /** Read current catalog inside the DAO transaction, not a cached UI snapshot. */
    suspend fun includeUnselectedSourceTracks(sourceUris: Collection<String>): Int =
        musicLibraryDao.includeUnselectedSourceTracks(sourceUris)

    suspend fun setPoolMembership(uris: Collection<String>, included: Boolean) {
        if (uris.isEmpty()) return
        val state = if (included) MusicPoolEntry.INCLUDED else MusicPoolEntry.EXCLUDED
        val updatedAt = System.currentTimeMillis()
        musicLibraryDao.setPoolEntries(
            uris.distinct().map { uri -> MusicPoolEntry(uri, state, updatedAt) }
        )
    }

    suspend fun pickRandomTrack(): MusicTrack? = musicLibraryDao.pickRandom()

    suspend fun pickRandomTrack(excludedUris: List<String>): MusicTrack? =
        when {
            excludedUris.isEmpty() -> musicLibraryDao.pickRandom()
            excludedUris.size <= MAX_EXCLUDED_URIS_PER_QUERY ->
                musicLibraryDao.pickRandomExcluding(excludedUris)
            else -> {
                val excluded = excludedUris.toHashSet()
                musicLibraryDao.getIncludedTracks()
                    .asSequence()
                    .filterNot { it.uri in excluded }
                    .toList()
                    .randomOrNull()
            }
        }

    suspend fun recordStarted(fireId: String, startedAt: Long, track: MusicTrack) {
        playbackHistoryDao.appendStarted(fireId, startedAt, track.displayTitle, track.uri)
    }

    suspend fun historyForDate(fromMillis: Long, toMillis: Long) =
        playbackHistoryDao.getForDate(fromMillis, toMillis)
}
