package com.sysadmindoc.alarmclock.localization

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.sysadmindoc.alarmclock.util.LocaleSelectionContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * B1-C1 data contract. The manifest is generated from the B0-T1 2471-key
 * canonical matrix. It is NOT a bundle of translated phrases and not an
 * approval authority. All user packs stay USER_SUPPLIED_UNREVIEWED.
 *
 * Does not alter Android locale, Compose, AlarmManager or alarm storage.
 */
internal data class LanguageSourceKey(
    val key: String,
    val type: String,
    val sourceHash: String,
    val formatTokens: Set<String>,
    val pluralQuantities: Set<String>,
    val translatable: Boolean,
    val hasRichMarkup: Boolean,
    val sourceXml: String,
    val arrayItemCount: Int
)

internal data class LanguageCatalogV1(
    val revision: String,
    val sources: Map<String, LanguageSourceKey>
) {
    companion object {
        const val ASSET = "localization/v1/catalog.json"
        fun load(context: Context): LanguageCatalogV1 {
            val raw = context.assets.open(ASSET).use { LanguagePackV1Validator.readBounded(it) }
            return fromJson(raw)
        }
        fun fromJson(raw: String): LanguageCatalogV1 {
            LanguagePackV1Validator.checkJsonStructure(raw)
            val root = JSONObject(raw)
            check(root.getString("format") == "acx-language-catalog/1")
            val revision = root.getString("catalogRevision")
            check(revision.matches(Regex("sha256:[a-f0-9]{64}")))
            val entries = root.getJSONArray("entries")
            check(entries.length() in 1..3000)
            val map = linkedMapOf<String, LanguageSourceKey>()
            for (i in 0 until entries.length()) {
                val row = entries.getJSONObject(i)
                val key = row.getString("key")
                check(key.matches(Regex("(?:string|plurals|array):[a-zA-Z_][a-zA-Z_0-9]*")))
                check(key !in map)
                val type = row.getString("type")
                check(type in setOf("string", "plurals", "array") && key.startsWith("$type:"))
                val tokens = row.getJSONArray("formatTokens").asStrings().toSet()
                val quantities = row.getJSONArray("pluralQuantities").asStrings().toSet()
                val hash = row.getString("sourceHash")
                check(hash.matches(Regex("sha256:[a-f0-9]{64}")))
                map[key] = LanguageSourceKey(
                    key = key, type = type, sourceHash = hash,
                    formatTokens = tokens, pluralQuantities = quantities,
                    translatable = row.getBoolean("translatable"),
                    hasRichMarkup = row.getBoolean("hasRichMarkup"),
                    sourceXml = row.getString("sourceXml"),
                    arrayItemCount = row.getInt("arrayItemCount")
                )
            }
            check(map.size == root.getInt("sourceKeyCount"))
            return LanguageCatalogV1(revision, map)
        }
    }
}

private fun JSONArray.asStrings(): List<String> =
    (0 until length()).map { index ->
        get(index) as? String ?: throw IllegalArgumentException("Non-string array element")
    }

internal data class LanguagePackV1Entry(
    val sourceHash: String,
    val type: String,
    val text: String? = null,
    val forms: Map<String, String>? = null,
    val items: List<String>? = null
)

internal data class LanguagePackV1(
    val locale: String,
    val catalogRevision: String,
    val packRevision: String,
    val entries: Map<String, LanguagePackV1Entry>,
    val usableKeys: Set<String>,
    val staleKeys: Set<String>
)

internal data class LanguagePackV1Check(
    val canInstall: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val staleKeys: Set<String>,
    val fallbackKeys: Set<String>,
    val usableKeys: Set<String>,
    val pack: LanguagePackV1?
)

internal class LanguagePackProblem(val code: String) : IllegalArgumentException(code)

internal class LanguagePackV1Validator(private val catalog: LanguageCatalogV1) {
    companion object {
        /** Enough for a full 2471-key pack without unbounded storage/memory. */
        const val MAX_PACK_BYTES: Int = 2 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 4096
        private const val MAX_JSON_DEPTH = 10
        private val PRINTF = Regex("%(?:[1-9]\\d*\\$)?[-+# 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")
        private val VALID_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")
        private val FINGERPRINT = Regex("sha256:[a-f0-9]{64}")
        private val PACK_REVISION = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

        fun readBounded(stream: InputStream): String {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (bytes.size() + n > MAX_PACK_BYTES)
                    throw LanguagePackProblem("PACK_TOO_LARGE")
                bytes.write(buffer, 0, n)
            }
            // String(bytes, UTF_8) otherwise silently replaces malformed bytes.
            return try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            } catch (_: Exception) {
                throw LanguagePackProblem("INVALID_UTF8")
            }
        }

        /**
         * android org.json implementations can differ in duplicate-key policy.
         * A strict streaming syntax walk rejects duplicate object keys at every
         * level BEFORE JSONObject performs any coercion or replacement.
         */
        fun checkJsonStructure(raw: String) {
            try {
                JsonReader(StringReader(raw)).use { reader ->
                    reader.isLenient = false
                    fun visit(depth: Int) {
                        if (depth > MAX_JSON_DEPTH) throw LanguagePackProblem("JSON_TOO_DEEP")
                        when (reader.peek()) {
                            JsonToken.BEGIN_OBJECT -> {
                                reader.beginObject()
                                val seen = HashSet<String>()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    if (!seen.add(name)) throw LanguagePackProblem("DUPLICATE_JSON_KEY")
                                    visit(depth + 1)
                                }
                                reader.endObject()
                            }
                            JsonToken.BEGIN_ARRAY -> {
                                reader.beginArray()
                                var count = 0
                                while (reader.hasNext()) {
                                    if (++count > 3000) throw LanguagePackProblem("JSON_ARRAY_TOO_LARGE")
                                    visit(depth + 1)
                                }
                                reader.endArray()
                            }
                            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                            JsonToken.BOOLEAN -> reader.nextBoolean()
                            JsonToken.NULL -> reader.nextNull()
                            else -> throw LanguagePackProblem("INVALID_JSON_STRUCTURE")
                        }
                    }
                    visit(0)
                    if (reader.peek() != JsonToken.END_DOCUMENT)
                        throw LanguagePackProblem("TRAILING_JSON_DATA")
                }
            } catch (e: LanguagePackProblem) {
                throw e
            } catch (_: Exception) {
                throw LanguagePackProblem("INVALID_JSON_STRUCTURE")
            }
        }

        private fun getRequiredString(json: JSONObject, name: String): String =
            json.opt(name) as? String ?: throw LanguagePackProblem("EXPECTED_STRING")

        private fun allowedFields(json: JSONObject, allowed: Set<String>) {
            val names = json.keys()
            while (names.hasNext()) {
                if (names.next() !in allowed) throw LanguagePackProblem("UNKNOWN_JSON_FIELD")
            }
        }

        private fun checkSafeText(value: String) {
            if (value.isBlank() || value.length > MAX_TEXT_CHARS ||
                value.any { it == '\u0000' || it in '\u202a'..'\u202e' ||
                    it in '\u2066'..'\u2069' }) {
                throw LanguagePackProblem("INVALID_TRANSLATION_TEXT")
            }
        }

        private fun tokens(value: String): Set<String> {
            val result = linkedSetOf<String>()
            var index = 0
            while (index < value.length) {
                if (value[index] != '%') {
                    index++
                    continue
                }
                val match = PRINTF.find(value, index)
                    ?.takeIf { it.range.first == index }
                    ?: throw LanguagePackProblem("MALFORMED_PRINTF")
                if (match.value != "%%") result.add(match.value)
                index = match.range.last + 1
            }
            return result
        }

        private fun safeSignature(value: String, source: LanguageSourceKey) {
            checkSafeText(value)
            if (tokens(value) != source.formatTokens)
                throw LanguagePackProblem("FORMAT_TOKEN_MISMATCH")
        }

        private fun safePlural(
            forms: JSONObject,
            source: LanguageSourceKey
        ): Map<String, String> {
            allowedFields(forms, VALID_QUANTITIES)
            if (!forms.has("other")) throw LanguagePackProblem("PLURAL_OTHER_REQUIRED")
            val result = linkedMapOf<String, String>()
            for (quantity in VALID_QUANTITIES.sorted()) {
                if (!forms.has(quantity)) continue
                val text = getRequiredString(forms, quantity)
                checkSafeText(text)
                // Locale-specific categories can differ from English. No new
                // printf args permitted; English 'other' args stay required.
                if (!source.formatTokens.containsAll(tokens(text)))
                    throw LanguagePackProblem("PLURAL_FORMAT_EXTRA_ARGUMENT")
                result[quantity] = text
            }
            val baseOther = Regex("<item\\s+quantity\\s*=\\s*['\"]other['\"]\\s*>([\\s\\S]*?)</item>")
                .find(source.sourceXml)?.groupValues?.get(1)
            if (baseOther != null && tokens(result.getValue("other")) != tokens(baseOther))
                throw LanguagePackProblem("PLURAL_OTHER_FORMAT_MISMATCH")
            return result
        }
    }

    fun validate(raw: String): LanguagePackV1Check {
        val warnings = mutableListOf<String>()
        val stales = linkedSetOf<String>()
        val missing = linkedSetOf<String>()
        val usable = linkedSetOf<String>()
        try {
            if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_PACK_BYTES)
                throw LanguagePackProblem("PACK_TOO_LARGE")
            checkJsonStructure(raw)
            val root = JSONObject(raw)
            allowedFields(root, setOf("format", "locale", "catalogRevision",
                "packRevision", "status", "entries"))
            if (getRequiredString(root, "format") != "acx-language-pack/1")
                throw LanguagePackProblem("UNSUPPORTED_PACK_FORMAT")
            if (getRequiredString(root, "status") != "USER_SUPPLIED_UNREVIEWED")
                throw LanguagePackProblem("UNAUTHORIZED_APPROVAL_CLAIM")
            val inputLocale = getRequiredString(root, "locale")
            val locale = LocaleSelectionContract.canonicalTag(inputLocale)
                ?: throw LanguagePackProblem("INVALID_LOCALE_TAG")
            if (inputLocale != locale) warnings.add("NORMALIZED_LOCALE_TAG")
            val revision = getRequiredString(root, "catalogRevision")
            if (!FINGERPRINT.matches(revision))
                throw LanguagePackProblem("INVALID_CATALOG_REVISION")
            if (revision != catalog.revision) warnings.add("CATALOG_REVISION_DIFFERENT")
            val packRevision = getRequiredString(root, "packRevision")
            if (!PACK_REVISION.matches(packRevision))
                throw LanguagePackProblem("INVALID_PACK_REVISION")
            val entries = root.opt("entries") as? JSONObject
                ?: throw LanguagePackProblem("INVALID_ENTRIES")
            if (entries.length() > catalog.sources.size)
                throw LanguagePackProblem("TOO_MANY_ENTRIES")
            val parsed = linkedMapOf<String, LanguagePackV1Entry>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val spec = catalog.sources[key]
                    ?: throw LanguagePackProblem("UNKNOWN_RESOURCE_KEY")
                if (!spec.translatable) throw LanguagePackProblem("NON_TRANSLATABLE_KEY")
                val json = entries.opt(key) as? JSONObject
                    ?: throw LanguagePackProblem("INVALID_ENTRY")
                val type = getRequiredString(json, "type")
                val sourceHash = getRequiredString(json, "sourceHash")
                if (type != spec.type) throw LanguagePackProblem("RESOURCE_TYPE_MISMATCH")
                if (!FINGERPRINT.matches(sourceHash))
                    throw LanguagePackProblem("INVALID_SOURCE_HASH")
                val stale = sourceHash != spec.sourceHash
                val entry = when (type) {
                    "string" -> {
                        allowedFields(json, setOf("type", "sourceHash", "text"))
                        val text = getRequiredString(json, "text")
                        checkSafeText(text)
                        if (!stale) {
                            if (spec.hasRichMarkup)
                                throw LanguagePackProblem("STYLED_RESOURCE_NOT_YET_SUPPORTED")
                            safeSignature(text, spec)
                        }
                        LanguagePackV1Entry(sourceHash, type, text = text)
                    }
                    "plurals" -> {
                        allowedFields(json, setOf("type", "sourceHash", "forms"))
                        val forms = json.opt("forms") as? JSONObject
                            ?: throw LanguagePackProblem("INVALID_PLURAL_FORMS")
                        val values = if (stale) {
                            allowedFields(forms, VALID_QUANTITIES)
                            if (!forms.has("other")) throw LanguagePackProblem("PLURAL_OTHER_REQUIRED")
                            buildMap {
                                val names = forms.keys()
                                while (names.hasNext()) {
                                    val q = names.next()
                                    val value = getRequiredString(forms,q)
                                    checkSafeText(value)
                                    put(q,value)
                                }
                            }
                        } else safePlural(forms,spec)
                        LanguagePackV1Entry(sourceHash,type,forms=values)
                    }
                    "array" -> {
                        allowedFields(json, setOf("type", "sourceHash", "items"))
                        val items = json.opt("items") as? JSONArray
                            ?: throw LanguagePackProblem("INVALID_ARRAY_ITEMS")
                        if (items.length() != spec.arrayItemCount)
                            throw LanguagePackProblem("ARRAY_SIZE_MISMATCH")
                        val strings = items.asStrings()
                        for (text in strings) {
                            checkSafeText(text)
                            if (!stale && !spec.formatTokens.containsAll(tokens(text)))
                                throw LanguagePackProblem("ARRAY_FORMAT_EXTRA_ARGUMENT")
                        }
                        LanguagePackV1Entry(sourceHash,type,items=strings)
                    }
                    else -> throw LanguagePackProblem("UNSUPPORTED_RESOURCE_TYPE")
                }
                parsed[key] = entry
                if (stale) stales.add(key) else usable.add(key)
            }
            if (stales.isNotEmpty()) warnings.add("STALE_TRANSLATIONS_SKIPPED")
            for (key in catalog.sources.keys) {
                if (catalog.sources.getValue(key).translatable && key !in usable)
                    missing.add(key)
            }
            if (usable.isEmpty()) warnings.add("NO_CURRENT_TRANSLATIONS")
            val pack = LanguagePackV1(locale,revision,packRevision,parsed,usable,stales)
            return LanguagePackV1Check(true, emptyList(), warnings, stales, missing, usable, pack)
        } catch (e: LanguagePackProblem) {
            return LanguagePackV1Check(false,listOf(e.code),warnings,stales,emptySet(),emptySet(),null)
        } catch (_: Exception) {
            return LanguagePackV1Check(false,listOf("INVALID_PACK_JSON"),warnings,stales,emptySet(),emptySet(),null)
        }
    }
}
