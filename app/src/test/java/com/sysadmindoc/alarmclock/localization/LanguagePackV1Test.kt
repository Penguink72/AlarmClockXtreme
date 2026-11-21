package com.sysadmindoc.alarmclock.localization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class LanguagePackV1Test {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var catalog: LanguageCatalogV1
    private lateinit var validator: LanguagePackV1Validator

    @Before fun setup() {
        catalog = LanguageCatalogV1.load(ApplicationProvider.getApplicationContext<Context>())
        validator = LanguagePackV1Validator(catalog)
    }

    private fun translation(key: String, value: String): JSONObject =
        JSONObject().put("sourceHash",catalog.sources.getValue(key).sourceHash)
            .put("type","string").put("text",value)

    private fun makePack(
        entries: JSONObject = JSONObject().put("string:settings_title",
            translation("string:settings_title","설정")),
        revision: String = catalog.revision,
        locale: String = "ko-KR",
        status: String = "USER_SUPPLIED_UNREVIEWED",
        packRevision: String = "v1"
    ): String = JSONObject()
        .put("format","acx-language-pack/1")
        .put("locale",locale)
        .put("catalogRevision",revision)
        .put("packRevision",packRevision)
        .put("status",status)
        .put("entries",entries).toString()

    private fun repository(): LanguagePackV1Repository =
        LanguagePackV1Repository(temp.newFolder("packs"), validator)

    @Test fun completeGeneratedCatalogCarriesSourceHashesAndFullTypeContract() {
        assertEquals(2471,catalog.sources.size)
        assertEquals(2462,catalog.sources.values.count { it.translatable })
        assertEquals(43,catalog.sources.values.count { it.type == "plurals" })
        assertEquals(1,catalog.sources.values.count { it.type == "array" })
        assertEquals("sha256:c608e163bb47aeefa169e3ca72b8e86ebd8deb37d89c59e47796d953fe95c70a",catalog.revision)
        assertTrue(catalog.sources.values.all { it.sourceHash.startsWith("sha256:") })
    }

    @Test fun customKoreanPackValidatesOnlyOneKeyWithoutPretendingFullTranslation() {
        val inspected=validator.validate(makePack())
        assertTrue(inspected.errors.toString(),inspected.canInstall)
        assertEquals("ko-KR",inspected.pack?.locale)
        assertEquals(setOf("string:settings_title"),inspected.usableKeys)
        assertEquals(2461,inspected.fallbackKeys.size)
        assertEquals("설정",inspected.pack?.entries?.get("string:settings_title")?.text)
    }

    @Test fun unknownKeyAndNonTranslatableKeyAreRejected() {
        val extra=JSONObject().put("string:__unknown",JSONObject()
            .put("type","string").put("sourceHash","sha256:"+"0".repeat(64)).put("text","xx"))
        assertEquals(listOf("UNKNOWN_RESOURCE_KEY"),validator.validate(makePack(extra)).errors)
        val blocked=catalog.sources.values.first { !it.translatable }
        val forbidden=JSONObject().put(blocked.key,JSONObject()
            .put("type",blocked.type).put("sourceHash",blocked.sourceHash).put("text","malicious"))
        assertEquals(listOf("NON_TRANSLATABLE_KEY"),validator.validate(makePack(forbidden)).errors)
    }

    @Test fun duplicateJsonObjectKeysAreRejectedAtEveryDepth() {
        val good=makePack()
        val duplicated=good.replace("\"locale\":\"ko-KR\"",
            "\"locale\":\"ko-KR\",\"locale\":\"ja\"")
        assertEquals(listOf("DUPLICATE_JSON_KEY"),validator.validate(duplicated).errors)
        val nested=good.replace("\"text\":\"설정\"",
            "\"text\":\"설정\",\"text\":\"overwrite\"")
        assertEquals(listOf("DUPLICATE_JSON_KEY"),validator.validate(nested).errors)
    }

    @Test fun unreviewedPackCannotForgeOfficialTranslationApproval() {
        val attempt=validator.validate(makePack(status="OFFICIALLY_REVIEWED"))
        assertFalse(attempt.canInstall)
        assertEquals(listOf("UNAUTHORIZED_APPROVAL_CLAIM"),attempt.errors)
    }

    @Test fun previousProbeSchemaCannotBeSilentlyAccepted() {
        // JSONObject encodes "/" as "\\/" in some Android implementations, so
        // substring replacement is not a reliable way to build a negative fixture.
        // Mutate the parsed field explicitly to verify the actual version gate.
        val old=JSONObject(makePack()).put("format","acx-language-pack-probe/1").toString()
        assertEquals(listOf("UNSUPPORTED_PACK_FORMAT"),validator.validate(old).errors)
    }

    @Test fun perKeyStaleHashDoesNotDiscardUnchangedTranslation() {
        val entries=JSONObject().put("string:settings_title",
            translation("string:settings_title","설정"))
            .put("string:music_library_title",
                translation("string:music_library_title","음악").put(
                    "sourceHash","sha256:"+"0".repeat(64)))
        val result=validator.validate(makePack(entries,revision="sha256:"+"f".repeat(64)))
        assertTrue(result.canInstall)
        assertTrue("CATALOG_REVISION_DIFFERENT" in result.warnings)
        assertTrue("STALE_TRANSLATIONS_SKIPPED" in result.warnings)
        assertEquals(setOf("string:music_library_title"),result.staleKeys)
        assertEquals(setOf("string:settings_title"),result.usableKeys)
        assertTrue("string:music_library_title" in result.fallbackKeys)
    }

    @Test fun mismatchedFormatTypeIsRejectedBeforeImport() {
        val source=catalog.sources.values.first {
            it.type=="string" && !it.hasRichMarkup && it.formatTokens == setOf("%1\$d")
        }
        val entry=JSONObject().put(source.key,translation(source.key,"%1\$s"))
        val result=validator.validate(makePack(entry))
        assertEquals(listOf("FORMAT_TOKEN_MISMATCH"),result.errors)
        val malformed=JSONObject().put(source.key,translation(source.key,"%"))
        assertEquals(listOf("MALFORMED_PRINTF"),validator.validate(makePack(malformed)).errors)
    }

    @Test fun pluralOtherIsRequiredAndPositionalArgumentsChecked() {
        val source=catalog.sources.values.first {
            it.type=="plurals" && it.sourceXml.contains("quantity=\"other\"")
        }
        val missing=JSONObject().put(source.key,JSONObject()
            .put("type","plurals").put("sourceHash",source.sourceHash)
            .put("forms",JSONObject().put("one","한 개")))
        assertEquals(listOf("PLURAL_OTHER_REQUIRED"),validator.validate(makePack(missing)).errors)
        val actualOther=Regex("<item\\s+quantity\\s*=\\s*['\"]other['\"]\\s*>([\\s\\S]*?)</item>")
            .find(source.sourceXml)?.groupValues?.get(1)
        assertNotNull(actualOther)
        val good=JSONObject().put(source.key,JSONObject().put("type","plurals")
            .put("sourceHash",source.sourceHash)
            .put("forms",JSONObject().put("other",actualOther)))
        assertTrue(validator.validate(makePack(good)).canInstall)
    }

    @Test fun arraysMustPreserveCanonicalElementCount() {
        val base=catalog.sources.values.single { it.type=="array" }
        val broken=JSONObject().put(base.key,JSONObject()
            .put("type","array").put("sourceHash",base.sourceHash)
            .put("items",org.json.JSONArray().put("one")))
        assertEquals(listOf("ARRAY_SIZE_MISMATCH"),validator.validate(makePack(broken)).errors)
    }

    @Test fun bidiOverridesAndUnexpectedFieldsAreRejected() {
        assertEquals(listOf("INVALID_TRANSLATION_TEXT"),validator.validate(makePack(
            JSONObject().put("string:settings_title",
                translation("string:settings_title","safe\u202Ehidden")))).errors)
        val extras=JSONObject(makePack()).put("isOfficial",true).toString()
        assertEquals(listOf("UNKNOWN_JSON_FIELD"),validator.validate(extras).errors)
    }

    @Test fun invalidLocaleAndDirectoryTraversalCannotEscapePrivateStorage() {
        assertEquals(listOf("INVALID_LOCALE_TAG"),validator.validate(
            makePack(locale="../ar")).errors)
        assertEquals(listOf("INVALID_LOCALE_TAG"),validator.validate(
            makePack(locale="zh_Hant_TW")).errors)
        val r=repository()
        assertFalse(r.importRaw(makePack(locale="../ar")).stored)
        assertNull(r.load("../ar"))
    }

    @Test fun malformedUtf8AndOversizedStreamsFailBeforeWriting() {
        val folder=temp.newFolder("large")
        val repo=LanguagePackV1Repository(folder,validator)
        val invalid=repo.importFromStream(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)))
        assertEquals("INVALID_UTF8",invalid.errorCode)
        assertFalse(invalid.stored)
        val huge=ByteArray(LanguagePackV1Validator.MAX_PACK_BYTES+1) { 65 }
        val large=repo.importFromStream(ByteArrayInputStream(huge))
        assertEquals("PACK_TOO_LARGE",large.errorCode)
        assertFalse(folder.listFiles().orEmpty().any { it.isFile })
    }

    @Test fun atomicImportExportAndRollbackPreservePreviousValidVersion() {
        val repo=repository()
        val one=makePack(packRevision="v1")
        val two=makePack(
            entries=JSONObject().put("string:settings_title",
                translation("string:settings_title","설정 두 번째")),
            packRevision="v2")
        assertTrue(repo.importRaw(one).stored)
        assertTrue(repo.importRaw(two).stored)
        assertEquals("v2",repo.load("ko-KR")?.validation?.pack?.packRevision)
        val exported=ByteArrayOutputStream()
        assertTrue(repo.exportToStream("ko-KR",exported))
        assertEquals(two,exported.toString(StandardCharsets.UTF_8.name()))
        assertTrue(repo.rollback("ko-KR"))
        assertEquals("v1",repo.load("ko-KR")?.validation?.pack?.packRevision)
        assertTrue("ko-KR" in repo.installedLocales())
    }

    @Test fun invalidImportDoesNotOverwritePreviouslyWorkingPack() {
        val repo=repository()
        assertTrue(repo.importRaw(makePack()).stored)
        val failure=repo.importRaw(makePack(status="APPROVED"))
        assertFalse(failure.stored)
        assertEquals("v1",repo.load("ko-KR")?.validation?.pack?.packRevision)
    }

    @Test fun damagedCurrentPackCanReadPreviousWithoutMisreportingCurrent() {
        val root=temp.newFolder("recovery")
        val repo=LanguagePackV1Repository(root,validator)
        assertTrue(repo.importRaw(makePack(packRevision="v1")).stored)
        assertTrue(repo.importRaw(makePack(packRevision="v2")).stored)
        File(root,"ko-KR.json").writeText("{broken",StandardCharsets.UTF_8)
        val recovered=repo.load("ko-KR")
        assertTrue(recovered?.recoveredFromPrevious==true)
        assertEquals("v1",recovered?.validation?.pack?.packRevision)
    }
}
