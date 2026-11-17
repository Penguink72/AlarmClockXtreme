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

    @Test fun userConfirmedSnapshotIsTheOnlyEligibleBulkExclusionScope() {
        val reviewed = item("content://media/reviewed", MusicLibraryIndexer.SOURCE_MEDIA_STORE)
        val addedLater = item("content://media/late", MusicLibraryIndexer.SOURCE_MEDIA_STORE)
        val userMusic = item("content://saf/own", MusicLibraryIndexer.SOURCE_SAF)
        val metadata = mapOf(
            reviewed.track.uri to MusicCatalogTrackMetadata(reviewed.track.uri, isSystemTone = true),
            addedLater.track.uri to MusicCatalogTrackMetadata(addedLater.track.uri, isSystemTone = true),
            userMusic.track.uri to MusicCatalogTrackMetadata(userMusic.track.uri, isSystemTone = true)
        )
        // The review UI showed only the first row before a later scan added
        // another IS_RINGTONE flag. The late candidate must not be excluded.
        assertEquals(
            setOf(reviewed.track.uri),
            confirmedSystemToneUris(
                listOf(reviewed, addedLater, userMusic),
                metadata,
                listOf(reviewed.track.uri)
            )
        )
        assertTrue(confirmedSystemToneUris(
            listOf(reviewed, addedLater, userMusic), metadata,
            listOf("content://media/unknown", userMusic.track.uri)
        ).isEmpty())
        // A formerly reviewed candidate that is already excluded must not be
        // silently restored or overwritten by a second confirmed operation.
        assertTrue(confirmedSystemToneUris(
            listOf(reviewed.copy(membershipState = MusicPoolEntry.EXCLUDED)),
            metadata, listOf(reviewed.track.uri)
        ).isEmpty())
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
