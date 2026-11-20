package com.sysadmindoc.alarmclock.ui.languagelab

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
class Lp1RepositoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sourceAssetsContainExactly200KeysAndHonestFallbacks() {
        val catalog = Lp1Repository.loadCatalog(context)
        val packs = Lp1Repository.loadInstalled(context, catalog)
        assertEquals(200, catalog.entries.size)
        assertEquals(200, catalog.byKey.size)
        assertEquals(200, packs.getValue("en").translations.size)
        assertEquals(155, packs.getValue("zh-TW").translations.size)
        assertEquals(155, packs.getValue("ja").translations.size)
        val missing = catalog.entries.first { it.key !in packs.getValue("zh-TW").translations }
        val fallback = Lp1Repository.resolve(catalog, packs.getValue("zh-TW"), missing.key)
        assertTrue(fallback.fallback)
        assertEquals(missing.source, fallback.value)
    }

    @Test
    fun importAndReadbackNewLanguageWithoutTouchingAlarmState() {
        val catalog = Lp1Repository.loadCatalog(context)
        val entry = catalog.entries.first { it.placeholders.isEmpty() }
        val translations = JSONObject().put(entry.key, "한국어 예제")
        val pack = JSONObject()
            .put("format", "acx-language-pack-probe/1")
            .put("catalogRevision", catalog.revision)
            .put("locale", "ko-KR")
            .put("packVersion", "0.1.0")
            .put("translations", translations)
        try {
            val imported = Lp1Repository.install(context, pack.toString(), catalog)
            assertTrue(imported.custom)
            val readback = Lp1Repository.loadInstalled(context, catalog).getValue("ko-KR")
            assertEquals("한국어 예제", readback.translations[entry.key])
            assertEquals(1, readback.translations.size)
            assertFalse(Lp1Repository.resolve(catalog, readback, entry.key).fallback)
            val missing = catalog.entries.first { it.key != entry.key }
            assertTrue(Lp1Repository.resolve(catalog, readback, missing.key).fallback)
        } finally {
            File(context.filesDir, "lp1-experimental-packs/ko-KR.json").delete()
        }
    }

    @Test
    fun rejectsUnknownKeyWrongRevisionAndMissingPrintfWithoutInstallation() {
        val catalog = Lp1Repository.loadCatalog(context)
        fun payload(locale: String, revision: String, translation: JSONObject): String =
            JSONObject()
                .put("format", "acx-language-pack-probe/1")
                .put("catalogRevision", revision)
                .put("locale", locale)
                .put("translations", translation)
                .toString()

        val badKey = payload("ko-KR", catalog.revision, JSONObject().put("bad.key", "bad"))
        assertTrue(runCatching { Lp1Repository.parsePack(badKey, catalog) }.isFailure)

        val badVersion = payload("ko-KR", "invalid", JSONObject())
        assertTrue(runCatching { Lp1Repository.parsePack(badVersion, catalog) }.isFailure)

        val withArgs = catalog.entries.first { it.placeholders.isNotEmpty() }
        val missingArgument = payload("ko-KR", catalog.revision, JSONObject().put(withArgs.key, "No format token"))
        assertTrue(runCatching { Lp1Repository.parsePack(missingArgument, catalog) }.isFailure)

        val oversized = ByteArrayInputStream(ByteArray(256 * 1024 + 1))
        assertTrue(runCatching { Lp1Repository.readBounded(oversized) }.isFailure)
    }
}
