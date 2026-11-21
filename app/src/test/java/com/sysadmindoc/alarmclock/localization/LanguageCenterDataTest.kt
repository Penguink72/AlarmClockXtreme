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

@RunWith(RobolectricTestRunner::class)
class LanguageCenterDataTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var snapshot: LanguageCenterSnapshot
    private lateinit var validator: LanguagePackV1Validator

    @Before fun prepare() {
        snapshot=LanguageCenterData.load(ApplicationProvider.getApplicationContext<Context>())
        validator=LanguagePackV1Validator(snapshot.catalog)
    }

    @Test fun fullComparisonCoversCanonicalSourceKeysWithoutInventedMissingLocales() {
        assertEquals(2471,snapshot.compare.size)
        assertEquals(snapshot.catalog.sources.keys,snapshot.compare.map { it.key }.toSet())
        val language=snapshot.compare.single { it.key=="string:settings_language" }
        assertTrue(language.english.contains("App language"))
        assertTrue(language.traditionalChinese.orEmpty().contains("App 語言"))
        assertTrue(language.japanese.orEmpty().contains("アプリ"))
        assertTrue(snapshot.compare.any { it.traditionalChinese==null && it.japanese==null })
    }

    @Test fun emptyNewPackIsValidUserSuppliedButNotOfficialTranslation() {
        val raw=LanguageCenterData.assemblePack(snapshot.catalog,"ko-KR",null)
        val result=validator.validate(raw)
        assertTrue(result.errors.toString(),result.canInstall)
        assertTrue("NO_CURRENT_TRANSLATIONS" in result.warnings)
        assertEquals(2462,result.fallbackKeys.size)
        assertEquals("USER_SUPPLIED_UNREVIEWED",JSONObject(raw).getString("status"))
    }

    @Test fun editAndDeleteOnlyNamedEntryAndDoNotChangeSourceType() {
        val key="string:settings_title"
        val source=snapshot.catalog.sources.getValue(key)
        val one=LanguagePackV1Entry(source.sourceHash,"string",text="설정")
        val first=validator.validate(
            LanguageCenterData.assemblePack(snapshot.catalog,"ko-KR",null,key,one))
        assertTrue(first.errors.toString(),first.canInstall)
        assertEquals(setOf(key),first.usableKeys)
        val second=validator.validate(LanguageCenterData.assemblePack(
            snapshot.catalog,"ko-KR",first.pack,key,null))
        assertTrue(second.canInstall)
        assertEquals(0,second.usableKeys.size)
        assertEquals(2462,second.fallbackKeys.size)
    }

    @Test fun editingOneOutdatedEntryDoesNotPromoteOtherStaleKeys() {
        val a=snapshot.catalog.sources.getValue("string:settings_title")
        val b=snapshot.catalog.sources.getValue("string:music_library_title")
        val stale=LanguagePackV1("ko-KR","sha256:"+"f".repeat(64),"old",
            mapOf(
                a.key to LanguagePackV1Entry("sha256:"+"0".repeat(64),"string",text="old"),
                b.key to LanguagePackV1Entry("sha256:"+"0".repeat(64),"string",text="music")
            ),emptySet(),setOf(a.key,b.key))
        val parsed=validator.validate(LanguageCenterData.assemblePack(snapshot.catalog,
            "ko-KR",stale,a.key,LanguagePackV1Entry(a.sourceHash,"string",text="new")))
        assertTrue(parsed.canInstall)
        assertEquals(setOf(a.key),parsed.usableKeys)
        assertEquals(setOf(b.key),parsed.staleKeys)
    }

    @Test fun pluralAndArrayHaveTypedRoundTripAndRejectWrongCount() {
        val plural=snapshot.catalog.sources.values.first {
            it.type=="plurals" && it.sourceXml.contains("quantity=\"other\"")
        }
        val other=Regex("<item\\s+quantity\\s*=\\s*['\"]other['\"]\\s*>([\\s\\S]*?)</item>")
            .find(plural.sourceXml)!!.groupValues[1]
        val goodPlural=LanguagePackV1Entry(plural.sourceHash,"plurals",
            forms=mapOf("other" to other))
        assertTrue(validator.validate(LanguageCenterData.assemblePack(
            snapshot.catalog,"ko-KR",null,plural.key,goodPlural)).canInstall)
        val array=snapshot.catalog.sources.values.single { it.type=="array" }
        val items=List(array.arrayItemCount) { "Text" }
        assertTrue(validator.validate(LanguageCenterData.assemblePack(snapshot.catalog,"ko-KR",
            null,array.key,LanguagePackV1Entry(array.sourceHash,"array",items=items))).canInstall)
        val broken=validator.validate(LanguageCenterData.assemblePack(snapshot.catalog,"ko-KR",
            null,array.key,LanguagePackV1Entry(array.sourceHash,"array",items=listOf("bad"))))
        assertEquals(listOf("ARRAY_SIZE_MISMATCH"),broken.errors)
    }

    @Test fun storedPackIsValidThroughRepositoryAfterEditorSave() {
        val repo=LanguagePackV1Repository(temp.newFolder("packs"),validator)
        val k="string:settings_title"
        val source=snapshot.catalog.sources.getValue(k)
        val raw=LanguageCenterData.assemblePack(snapshot.catalog,"ko-KR",null,
            k,LanguagePackV1Entry(source.sourceHash,"string",text="설정"))
        assertTrue(repo.importRaw(raw).stored)
        assertEquals("설정",repo.load("ko-KR")?.validation?.pack?.entries?.get(k)?.text)
        assertTrue("ko-KR" in repo.installedLocales())
    }

    @Test fun editorLabelsPresentAcrossEnglishChineseAndJapanese() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val json=JSONObject(context.assets.open("localization/v1/editor-labels.json")
            .bufferedReader().use { it.readText() }).getJSONObject("locales")
        val base=json.getJSONObject("en")
        val expected=base.keys().asSequence().toSet()
        listOf("zh-TW","ja").forEach { locale ->
            val labels=json.getJSONObject(locale)
            assertEquals(expected,labels.keys().asSequence().toSet())
            assertTrue(expected.all { labels.getString(it).isNotBlank() })
        }
    }
}
