package com.sysadmindoc.alarmclock.localization

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Read-only comparison evidence from B0-T1; not Android text resolution. */
internal data class LanguageComparison(
    val key: String, val english: String,
    val traditionalChinese: String?, val japanese: String?
)

internal class LanguageCenterLabels(private val labels: Map<String, String>) {
    operator fun get(key: String): String = labels[key] ?: key
}

internal data class LanguageCenterSnapshot(
    val catalog: LanguageCatalogV1,
    val compare: List<LanguageComparison>,
    val labels: LanguageCenterLabels
)

internal object LanguageCenterData {
    private const val COMPARE_ASSET = "localization/v1/source-compare.json"
    private const val LABEL_ASSET = "localization/v1/editor-labels.json"

    fun load(context: Context): LanguageCenterSnapshot {
        val catalog = LanguageCatalogV1.load(context)
        val compare = JSONObject(context.assets.open(COMPARE_ASSET).bufferedReader()
            .use { it.readText() })
        check(compare.getString("schema") == "acx-b1-u1-source-compare/1")
        check(compare.getString("catalogRevision") == catalog.revision)
        val rows = compare.getJSONArray("items")
        check(rows.length() == catalog.sources.size)
        val list = (0 until rows.length()).map { index ->
            val value = rows.getJSONObject(index)
            val key = value.getString("key")
            check(key in catalog.sources)
            LanguageComparison(
                key = key,
                english = value.getString("en"),
                traditionalChinese = value.optString("zhTw").takeIf {
                    !value.isNull("zhTw")
                },
                japanese = value.optString("ja").takeIf { !value.isNull("ja") }
            )
        }
        check(list.map { it.key }.toSet().size == list.size)
        val locale = context.resources.configuration.locales[0]
        val preferred = when (locale.language.lowercase(Locale.ROOT)) {
            "ja" -> "ja"
            "zh" -> "zh-TW"
            else -> "en"
        }
        val data = JSONObject(context.assets.open(LABEL_ASSET).bufferedReader()
            .use { it.readText() })
        check(data.getString("schema") == "acx-b1-u1-editor-labels/1")
        val labels = data.getJSONObject("locales").getJSONObject(preferred)
        val keys = labels.keys()
        val values = linkedMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            values[key] = labels.getString(key)
        }
        return LanguageCenterSnapshot(catalog,list,LanguageCenterLabels(values))
    }

    /**
     * Build a new pack; every entry is copied, and only the explicitly edited
     * key gets the current source hash. Old stale entries remain stale until the
     * user individually edits them. Never auto-approve missing translations.
     */
    fun assemblePack(
        catalog: LanguageCatalogV1,
        locale: String,
        previous: LanguagePackV1?,
        updatedKey: String? = null,
        replacement: LanguagePackV1Entry? = null
    ): String {
        val source = updatedKey?.let {
            catalog.sources[it] ?: error("UNKNOWN_RESOURCE_KEY")
        }
        if (source != null && !source.translatable)
            error("NON_TRANSLATABLE_KEY")
        val all = JSONObject()
        previous?.entries?.forEach { (key, value) ->
            if (key != updatedKey) all.put(key, encode(value))
        }
        if (updatedKey != null && replacement != null) {
            check(replacement.type == source?.type)
            val current = replacement.copy(sourceHash = source.sourceHash)
            all.put(updatedKey,encode(current))
        }
        return JSONObject().put("format","acx-language-pack/1")
            .put("locale",locale)
            .put("catalogRevision",catalog.revision)
            .put("packRevision","local-v1")
            .put("status","USER_SUPPLIED_UNREVIEWED")
            .put("entries",all).toString()
    }

    private fun encode(entry: LanguagePackV1Entry): JSONObject {
        val objectValue = JSONObject().put("type",entry.type)
            .put("sourceHash",entry.sourceHash)
        return when (entry.type) {
            "string" -> objectValue.put("text",requireNotNull(entry.text))
            "plurals" -> objectValue.put("forms",JSONObject(requireNotNull(entry.forms)))
            "array" -> objectValue.put("items",JSONArray(requireNotNull(entry.items)))
            else -> error("UNSUPPORTED_RESOURCE_TYPE")
        }
    }
}
