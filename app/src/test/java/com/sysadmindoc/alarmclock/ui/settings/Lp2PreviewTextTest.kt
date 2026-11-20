package com.sysadmindoc.alarmclock.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Lp2PreviewTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun newG6CatalogHas200KeysAnd21NonParameterizedSettingsEntries() {
        val catalog = Lp2PreviewText.catalog(context)
        assertEquals("g6-200-current-20261120", catalog.revision)
        assertEquals(200, catalog.entries.size)
        assertEquals(21, Lp2PreviewText.pilotKeys.size)
        assertTrue(catalog.entries.keys.containsAll(Lp2PreviewText.pilotKeys))
        assertTrue(Lp2PreviewText.pilotKeys.all { catalog.entries.getValue(it).placeholders.isEmpty() })
        assertEquals(200, Lp2PreviewText.available(context).getValue("en"))
        assertEquals(200, Lp2PreviewText.available(context).getValue("zh-TW"))
        assertEquals(200, Lp2PreviewText.available(context).getValue("ja"))
        val updated = Lp2PreviewText.resolveForDebug("music_playlist_integration_pending", "en", context)
        assertTrue(updated.value.contains("unavailable right now"))
        assertFalse(updated.value.contains("Room v28"))
    }

    @Test
    fun importCustomLanguagePersistAndEnablePreviewWithoutChangingAlarmData() {
        val cat = Lp2PreviewText.catalog(context)
        val key = "settings_title"
        val custom = JSONObject()
            .put("format", "acx-language-pack-probe/1")
            .put("catalogRevision", cat.revision)
            .put("locale", "ko-KR")
            .put("packVersion", "lp2-test")
            .put("translations", JSONObject().put(key, "설정"))
        try {
            val installed = Lp2PreviewText.importPack(context, custom.toString())
            assertEquals("ko-KR", installed.locale)
            assertEquals(1, installed.values.size)
            val activated = Lp2PreviewText.configure(context,
                Lp2PreviewText.Options(true, "ko-KR", "ja", true))
            assertTrue(activated)
            assertTrue(Lp2PreviewText.isEnabled())
            assertEquals("ko-KR", Lp2PreviewText.options(context).primary)
            assertEquals("설정", Lp2PreviewText.resolveForDebug(key,"ko-KR",context).value)
            val fallback = Lp2PreviewText.resolveForDebug("settings_license","ko-KR",context)
            assertTrue(fallback.fallback)
            assertEquals("License", fallback.value)
            assertTrue(Lp2PreviewText.refresh(context))
            assertTrue(Lp2PreviewText.isEnabled())
        } finally {
            Lp2PreviewText.configure(context,
                Lp2PreviewText.Options(false,"zh-TW","ja",true))
            File(context.filesDir, "lp2-experimental-packs/ko-KR.json").delete()
        }
    }

    @Test
    fun rejectOldCatalogUnknownKeyBadPrintfAndLargeImportBeforeInstallation() {
        val cat = Lp2PreviewText.catalog(context)
        fun packet(revision: String, values: JSONObject): String = JSONObject()
            .put("format","acx-language-pack-probe/1")
            .put("catalogRevision",revision)
            .put("locale","fr-FR")
            .put("translations",values)
            .toString()
        assertTrue(runCatching { Lp2PreviewText.parsePack(
            packet("lp0-200-20261118", JSONObject()),cat) }.isFailure)
        assertTrue(runCatching { Lp2PreviewText.parsePack(
            packet(cat.revision, JSONObject().put("made_up_key","x")),cat) }.isFailure)
        val formatted = cat.entries.entries.first { it.value.placeholders.isNotEmpty() }.key
        assertTrue(runCatching { Lp2PreviewText.parsePack(
            packet(cat.revision, JSONObject().put(formatted,"Missing placeholder")),cat) }.isFailure)
        assertTrue(runCatching { Lp2PreviewText.importFromStream(
            context, ByteArrayInputStream(ByteArray(256*1024+1))) }.isFailure)
        assertFalse(File(context.filesDir,"lp2-experimental-packs/fr-FR.json").isFile)
    }
}
