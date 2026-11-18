package com.sysadmindoc.alarmclock.ui.languagelab

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/** Debug-only LP1 translation experiment; never accessed by alarm execution. */
internal data class Lp1Entry(val key: String, val section: String, val source: String, val placeholders: List<String>)
internal data class Lp1Catalog(val revision: String, val entries: List<Lp1Entry>) {
    val byKey: Map<String, Lp1Entry> = entries.associateBy { it.key }
}
internal data class Lp1Pack(val locale: String, val translations: Map<String, String>, val version: String, val custom: Boolean)
internal data class Lp1Resolved(val value: String, val fallback: Boolean)

internal object Lp1Repository {
    private const val PACK_FORMAT = "acx-language-pack-probe/1"
    private const val CATALOG_FORMAT = "acx-language-catalog-probe/1"
    private const val MAX_BYTES = 256 * 1024
    private const val STORAGE = "lp1-experimental-packs"
    private val builtIns = listOf("en", "zh-TW", "ja")
    private val localeRegex = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$")
    private val printf = Regex("%(?:\\d+\\$)?[-+# 0,(]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtT%]")

    fun placeholders(text: String): List<String> =
        printf.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    fun readBounded(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val bytes = ByteArray(8192)
        while (true) {
            val size = stream.read(bytes)
            if (size < 0) break
            require(output.size() + size <= MAX_BYTES) { "File too large" }
            output.write(bytes, 0, size)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun loadCatalog(context: Context): Lp1Catalog {
        val json = JSONObject(context.assets.open("lp1/catalog.json").use(::readBounded))
        require(json.getString("format") == CATALOG_FORMAT) { "Unsupported catalog" }
        val array = json.getJSONArray("entries")
        require(array.length() == 200) { "Expected 200 keys" }
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val tokens = item.getJSONArray("placeholders")
            val entry = Lp1Entry(
                item.getString("key"),
                item.getString("section"),
                item.getString("source"),
                (0 until tokens.length()).map { tokens.getString(it) }
            )
            require(placeholders(entry.source) == entry.placeholders) { "Bad catalog parameters: " + entry.key }
            entry
        }
        require(entries.map { it.key }.distinct().size == 200) { "Duplicate catalog keys" }
        return Lp1Catalog(json.getString("revision"), entries)
    }

    fun parsePack(raw: String, catalog: Lp1Catalog): Lp1Pack {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "File too large" }
        val root = JSONObject(raw)
        require(root.getString("format") == PACK_FORMAT) { "Unsupported language pack format" }
        require(root.getString("catalogRevision") == catalog.revision) { "Wrong catalog revision" }
        val locale = root.getString("locale")
        require(locale.length <= 30 && localeRegex.matches(locale) &&
            Locale.forLanguageTag(locale).language.isNotBlank()) { "Invalid locale tag" }
        val translations = root.getJSONObject("translations")
        require(translations.length() <= 200) { "Too many translations" }
        val accepted = linkedMapOf<String, String>()
        val keys = translations.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val base = catalog.byKey[key] ?: error("Unknown key: " + key)
            val value = translations.get(key)
            require(value is String && value.length <= 4096) { "Not a valid translation: " + key }
            if (value.isNotBlank()) {
                require(placeholders(value) == base.placeholders) { "Format mismatch: " + key }
                accepted[key] = value
            }
        }
        return Lp1Pack(locale, accepted, root.optString("packVersion", "manual"), custom = false)
    }

    fun loadInstalled(context: Context, catalog: Lp1Catalog): Map<String, Lp1Pack> {
        val result = linkedMapOf<String, Lp1Pack>()
        for (name in builtIns) {
            val value = context.assets.open("lp1/packs/" + name + ".json").use(::readBounded)
            val pack = parsePack(value, catalog)
            require(pack.locale == name) { "Wrong built-in locale" }
            result[name] = pack
        }
        File(context.filesDir, STORAGE).listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach { file ->
            runCatching {
                val value = AtomicFile(file).openRead().use(::readBounded)
                val pack = parsePack(value, catalog)
                require(file.name == pack.locale + ".json") { "Wrong stored filename" }
                result[pack.locale] = pack.copy(custom = true)
            }
        }
        return result
    }

    fun install(context: Context, raw: String, catalog: Lp1Catalog): Lp1Pack {
        val pack = parsePack(raw, catalog)
        val folder = File(context.filesDir, STORAGE)
        check(folder.isDirectory || folder.mkdirs()) { "Cannot create pack storage" }
        val file = AtomicFile(File(folder, pack.locale + ".json"))
        val output = file.startWrite()
        try {
            output.write(raw.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        return pack.copy(custom = true)
    }

    fun resolve(catalog: Lp1Catalog, pack: Lp1Pack?, key: String): Lp1Resolved {
        val source = requireNotNull(catalog.byKey[key]) { "Unknown catalog key: " + key }
        val translated = pack?.translations?.get(key)
        return if (translated.isNullOrBlank()) Lp1Resolved(source.source, pack?.locale != "en")
        else Lp1Resolved(translated, fallback = false)
    }
}
