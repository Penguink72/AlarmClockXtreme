package com.sysadmindoc.alarmclock.ui.ringtone

import com.sysadmindoc.alarmclock.data.local.entity.MusicPoolEntry
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSystemToneExclusionTest {
    private fun item(
        uri: String, source: String, membership: String? = null
    ) = MusicPoolUiTrack(
        track = MusicTrack(uri = uri, title = uri, source = source),
        membershipState = membership,
        sourceName = source
    )

    @Test fun onlyIndexedMediaStoreRecognizedTonesAreCandidates() {
        val mediaTone = item("content://media/tone", MusicLibraryIndexer.SOURCE_MEDIA_STORE)
        val mediaMusic = item("content://media/song", MusicLibraryIndexer.SOURCE_MEDIA_STORE)
        val manualSaf = item("content://saf/custom-alarm", MusicLibraryIndexer.SOURCE_SAF)
        val previouslyExcluded = item(
            "content://media/previous", MusicLibraryIndexer.SOURCE_MEDIA_STORE,
            MusicPoolEntry.EXCLUDED
        )
        val candidates = recognizedSystemToneCandidates(
            tracks = listOf(mediaTone, mediaMusic, manualSaf, previouslyExcluded),
            metadataByUri = mapOf(
                mediaTone.track.uri to MusicCatalogTrackMetadata(mediaTone.track.uri, isSystemTone = true),
                mediaMusic.track.uri to MusicCatalogTrackMetadata(mediaMusic.track.uri, isSystemTone = false),
                manualSaf.track.uri to MusicCatalogTrackMetadata(manualSaf.track.uri, isSystemTone = true),
                previouslyExcluded.track.uri to MusicCatalogTrackMetadata(previouslyExcluded.track.uri, isSystemTone = true)
            )
        )
        assertEquals(listOf(mediaTone), candidates)
    }

    @Test fun missingMetadataAndEmptyLibraryCannotCauseMassExclusion() {
        val unknown = item("content://media/unprobed", MusicLibraryIndexer.SOURCE_MEDIA_STORE)
        assertTrue(recognizedSystemToneCandidates(listOf(unknown), emptyMap()).isEmpty())
        assertTrue(recognizedSystemToneCandidates(emptyList(), emptyMap()).isEmpty())
    }

    @Test fun confirmedPolicyPreservesAbilityToExcludeAlreadyIncludedTone() {
        val included = item(
            "content://media/included", MusicLibraryIndexer.SOURCE_MEDIA_STORE,
            MusicPoolEntry.INCLUDED
        )
        assertEquals(
            listOf(included),
            recognizedSystemToneCandidates(
                listOf(included),
                mapOf(included.track.uri to MusicCatalogTrackMetadata(included.track.uri, isSystemTone = true))
            )
        )
    }
}
