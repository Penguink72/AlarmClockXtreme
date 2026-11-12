package com.sysadmindoc.alarmclock.ui.ringtone

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicLibrarySourceStoreTest {
    @Test
    fun persistsTenDistinctRootAndNestedSourcesWithMoreThanThreeThousandUris() {
        val directory = createTempDirectory()
        try {
            val context = isolatedContext(directory)
            val rootUri = Uri.parse("content://documents/tree/primary%3AMusic")
            val trackUris = (0 until 3_001).mapTo(LinkedHashSet()) {
                "content://documents/tree/primary%3AMusic/document/primary%3AMusic%2F$it"
            }
            repeat(10) { index ->
                MusicLibrarySourceStore.addSource(
                    context,
                    rootUri,
                    "primary:Music/Set$index",
                    "Set $index"
                )
            }
            val source = MusicLibrarySourceStore.getSources(context).first()
            MusicLibrarySourceStore.update(
                context,
                source.copy(
                    indexed = true,
                    status = MusicSourceStatus.READY,
                    audioCount = trackUris.size,
                    trackUris = trackUris
                )
            )

            val restored = MusicLibrarySourceStore.getSources(context)
            assertEquals(10, restored.size)
            assertEquals(3_001, restored.first().trackUris.size)
            assertEquals(3_001, restored.first().trackCount)
            assertTrue(restored.all { it.treeUri == rootUri.toString() })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun treatsTheSameFolderGrantAndDocumentAsOneSource() {
        val directory = createTempDirectory()
        try {
            val context = isolatedContext(directory)
            val uri = Uri.parse("content://documents/tree/primary%3AMusic")
            val first = MusicLibrarySourceStore.addSource(context, uri, "primary:Music/A", "A")
            val duplicate = MusicLibrarySourceStore.addSource(context, uri, "primary:Music/A", "Again")
            val nested = MusicLibrarySourceStore.addSource(context, uri, "primary:Music/A/B", "B")

            assertEquals(first.id, duplicate.id)
            assertEquals(2, MusicLibrarySourceStore.getSources(context).size)
            assertEquals(uri.toString(), nested.treeUri)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun migratesLegacyRingtoneFolderGrantsOnceAndDoesNotReAddRemovedSources() {
        val directory = createTempDirectory()
        try {
            val context = isolatedContext(directory)
            val uri = Uri.parse("content://documents/tree/primary%3AMusic")
            context.getSharedPreferences("ringtone_sources", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("folder_uris", setOf(uri.toString()))
                .commit()

            val imported = MusicLibrarySourceStore.getSources(context)
            assertEquals(1, imported.size)
            assertEquals(uri.toString(), imported.single().treeUri)

            MusicLibrarySourceStore.remove(context, imported.single().id)

            assertTrue(MusicLibrarySourceStore.getSources(context).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File =
        Files.createTempDirectory("music-source-store-test").toFile()

    private fun isolatedContext(directory: File): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val preferenceNamespace = directory.name
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("$preferenceNamespace-$name", mode)
        }
    }
}
