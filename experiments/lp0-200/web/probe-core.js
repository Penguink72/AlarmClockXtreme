"use strict";
/* Pure, synchronous, dependency-free LP0 locale pack contract. Browser + Node. */
(function (root, factory) {
  const exported = factory();
  if (typeof module === "object" && module.exports) module.exports = exported;
  else root.ACX_LP0_CORE = exported;
})(typeof globalThis === "object" ? globalThis : this, function () {
  const matcher = /%(?:(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAtT%])/g;
  const placeholders = (text) => (String(text).match(matcher) || []).filter((x) => x !== "%%").sort();
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const supportedLocale = (locale) =>
    typeof locale === "string" && /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}$/.test(locale) && locale.length <= 30;
  function validatePack(candidate, catalog) {
    if (!candidate || typeof candidate !== "object" || Array.isArray(candidate)) throw new Error("語言包根節點必須是 JSON 物件");
    if (candidate.format !== "acx-language-pack-probe/1" || candidate.catalogRevision !== catalog.revision) {
      throw new Error("語言包格式或目錄版本不相容");
    }
    if (!supportedLocale(candidate.locale)) throw new Error("無效 BCP47 語言標籤");
    if (!candidate.translations || typeof candidate.translations !== "object" || Array.isArray(candidate.translations)) {
      throw new Error("translations 必須是物件");
    }
    const known = new Map(catalog.entries.map((entry) => [entry.key, entry]));
    const input = Object.entries(candidate.translations);
    if (input.length > catalog.entries.length) throw new Error("翻譯項目超過語言目錄");
    const clean = Object.create(null);
    for (const [key, value] of input) {
      if (!known.has(key)) throw new Error("未知 Key：" + key);
      if (typeof value !== "string" || value.length > 4096) throw new Error("翻譯值無效：" + key);
      if (!value.trim()) continue;
      if (!same(placeholders(value), known.get(key).placeholders)) throw new Error("格式參數不符：" + key);
      clean[key] = value;
    }
    return {
      format:"acx-language-pack-probe/1",locale:candidate.locale,
      catalogRevision:catalog.revision,packVersion:typeof candidate.packVersion === "string" ? candidate.packVersion : "user-edit",
      builtInSource:false,translations:clean
    };
  }
  function translate(packs, known, locale, key) {
    const entry = known.get(key);
    if (!entry) throw new Error("Unknown catalog key: " + key);
    const pack = packs[locale];
    const own = pack && pack.translations[key];
    if (typeof own === "string" && own.trim()) return {value:own,fallback:false};
    return {value:entry.source,fallback:locale !== "en"};
  }
  function completion(packs, known, locale) {
    const pack = packs[locale];
    return pack ? Object.entries(pack.translations).filter(([key,text]) => known.has(key) && typeof text === "string" && text.trim()).length : 0;
  }
  return {placeholders,same,supportedLocale,validatePack,translate,completion};
});
