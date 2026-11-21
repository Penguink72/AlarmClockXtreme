package com.sysadmindoc.alarmclock.ui.ringtone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class MusicFolderPoolRulesTest {
    @Test fun explicitRuleSurvivesReadbackAndCanBeClearedWithoutLeakingToOtherFolders() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val key=MusicFolderPoolRules.mediaStoreKey("Music/Camera/")
        MusicFolderPoolRules.restore(context,key,null)
        val other=MusicFolderPoolRules.mediaStoreKey("Recordings")
        MusicFolderPoolRules.write(context,key,MusicFolderPoolRules.INCLUDE)
        assertEquals(MusicFolderPoolRules.INCLUDE,MusicFolderPoolRules.readAll(context)[key])
        assertNull(MusicFolderPoolRules.readAll(context)[other])
        MusicFolderPoolRules.write(context,key,MusicFolderPoolRules.EXCLUDE)
        assertEquals(MusicFolderPoolRules.EXCLUDE,MusicFolderPoolRules.readAll(context)[key])
        MusicFolderPoolRules.restore(context,key,null)
        assertNull(MusicFolderPoolRules.readAll(context)[key])
    }

    @Test fun scannedRuleScopesAreExactAndDoNotClaimOtherSafSources() {
        val uriA="content://a"
        val uriB="content://b"
        val uriSaf="content://saf"
        val source=MusicLibrarySource(
            id="source-1",treeUri="content://tree",
            documentId="primary:Music",name="Imported",
            status=MusicSourceStatus.READY,indexed=true,
            trackUris=setOf(uriSaf)
        )
        val scan=MusicScanResult(
            tracks=listOf(
                MusicTrack(uriA,"Song A",MusicLibraryIndexer.SOURCE_MEDIA_STORE),
                MusicTrack(uriB,"Song B",MusicLibraryIndexer.SOURCE_MEDIA_STORE_VIDEO),
                MusicTrack(uriSaf,"Song C",MusicLibraryIndexer.SOURCE_SAF)
            ),
            warnings=emptyList(),sources=listOf(source),mediaStoreScanned=true,
            rejectedAudio=0,rejectedVideos=0,
            metadataByUri=mapOf(
                uriA to MusicCatalogTrackMetadata(uriA,folderPath="Music"),
                uriB to MusicCatalogTrackMetadata(uriB,folderPath="Videos")
            )
        )
        val grouped=MusicFolderPoolRules.scannedUrisByRuleKey(scan)
        assertEquals(setOf(uriA),grouped[MusicFolderPoolRules.mediaStoreKey("music")])
        assertEquals(setOf(uriB),grouped[MusicFolderPoolRules.mediaStoreKey("videos")])
        assertEquals(setOf(uriSaf),grouped[MusicFolderPoolRules.safSourceKey("source-1")])
        // Device-wide opt-in includes audio only; video audio is a separate
        // folder decision, not swept in by the one-tap music import.
        assertEquals(setOf(uriA),grouped[MusicFolderPoolRules.allDeviceAudioKey()])
    }
}
