package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack

internal object MusicTrackIndexReconciler {
    fun afterScan(
        existing: List<MusicTrack>,
        discovered: List<MusicTrack>,
        sources: List<MusicLibrarySource>,
        mediaStoreScanned: Boolean
    ): List<MusicTrack> {
        val sourceUris = sources.flatMapTo(HashSet()) { it.trackUris }
        val sourceIndexIsIncomplete = sources.any { !it.indexed }
        val retained = existing.filter { track ->
            (isMediaStoreTrack(track) && !mediaStoreScanned) ||
                track.uri in sourceUris ||
                (sourceIndexIsIncomplete && !isMediaStoreTrack(track))
        }
        return (retained + discovered).distinctBy(MusicTrack::uri)
    }

    fun afterSourceRemoval(
        existing: List<MusicTrack>,
        remainingSources: List<MusicLibrarySource>
    ): List<MusicTrack> {
        val sourceUris = remainingSources.flatMapTo(HashSet()) { it.trackUris }
        val sourceIndexIsIncomplete = remainingSources.any { !it.indexed }
        return existing.filter { track ->
            isMediaStoreTrack(track) ||
                track.uri in sourceUris ||
                (sourceIndexIsIncomplete && !isMediaStoreTrack(track))
        }
    }

    private fun isMediaStoreTrack(track: MusicTrack): Boolean =
        track.source == MusicLibraryIndexer.SOURCE_MEDIA_STORE
}
