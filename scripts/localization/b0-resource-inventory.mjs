#!/usr/bin/env node
/*
 * B0: full Android resource and direct-callsite inventory.
 * Node.js-only, read-only. This is an XML lexical index, not proof of runtime
 * screen visibility or semantic translation approval.
 */
import { readdirSync, readFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, relative, join, dirname, basename, extname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { execFileSync } from "node:child_process";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const lexicalReference = /\bR\s*\.\s*(string|plurals|array)\s*\.\s*([a-zA-Z_][a-zA-Z_0-9]*)\b|@(string|plurals|array)\/([a-zA-Z_][a-zA-Z_0-9]*)\b/g;
const formatter = /%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g;

function walk(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true })
    .sort((a, b) => a.name.localeCompare(b.name, "en"))
    .flatMap(entry => entry.isDirectory()
      ? walk(join(dir, entry.name))
      : entry.isFile() ? [join(dir, entry.name)] : []);
}
const rel = (root, file) => relative(root, file).replaceAll("\\", "/");
const blank = s => s.replace(/[^\n]/g, " ");

export function extractResourceDefinitions(xml) {
  // Strings can contain rich markup, comments and CDATA. Ignore markup-like
  // pseudo-definitions inside comments/CDATA; preserve offsets for line numbers.
  const masked = xml.replace(/<!--[\s\S]*?-->/g, blank)
    .replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, blank);
  const pattern = /<(string|plurals|string-array|integer-array|array)\b([^>]*)>/g;
  const result = [];
  const seen = new Set();
  for (const m of masked.matchAll(pattern)) {
    const kind = m[1] === "string-array" || m[1] === "integer-array" ? "array" : m[1];
    const name = m[2].match(/\bname\s*=\s*(['"])([^'"]+)\1/);
    if (!name) continue; // e.g. embedded formatting tags, not named resources.
    const key = kind + ":" + name[2];
    if (seen.has(key)) throw new Error("Duplicate resource definition in XML: " + key);
    seen.add(key);
    const translatable = !/\btranslatable\s*=\s*(['"])false\1/.test(m[2]);
    const after = (m.index ?? 0) + m[0].length;
    const close = xml.indexOf("</" + m[1] + ">", after);
    const rawValue = close < 0 || m[0].endsWith("/>") ? "" : xml.slice(after, close);
    const formats = [...new Set(rawValue.match(formatter) ?? [])].sort();
    const isPlural = kind === "plurals";
    const hasRichMarkup = /<(?:b|i|u|font|annotation|xliff:|a\b)/.test(rawValue);
    result.push({
      key, type:kind, name:name[2], line:xml.slice(0, m.index).split("\n").length,
      translatable, formatTokens:formats, hasRichMarkup,
      pluralQuantities:isPlural ? [...new Set([...rawValue.matchAll(/<item\s+quantity\s*=\s*(['"])(\w+)\1/g)].map(x => x[2]))].sort() : []
    });
  }
  return result;
}

function sourceVariant(path) {
  const m = path.match(/^(app|wear)\/src\/(main|debug)\/res\/(values[^/]*)\/[^/]+\.xml$/);
  return m ? {module:m[1], sourceSet:m[2], qualifier:m[3]} : null;
}

export function buildInventory(root = ROOT) {
  const resources = [];
  const sourceDirs = [
    "app/src/main/res", "app/src/debug/res", "wear/src/main/res"
  ];
  for (const dir of sourceDirs) {
    for (const file of walk(join(root, dir))) {
      if (extname(file) !== ".xml") continue;
      const path = rel(root, file), variant = sourceVariant(path);
      if (!variant) continue;
      const xml = readFileSync(file, "utf8");
      for (const entry of extractResourceDefinitions(xml)) {
        resources.push({...entry, ...variant, path});
      }
    }
  }

  const definitions = resources.filter(r =>
    r.module === "app" && r.sourceSet === "main" && r.qualifier === "values");
  const byKey = new Map();
  for (const record of definitions) {
    if (byKey.has(record.key))
      throw new Error("Duplicate authoritative base resource key: " + record.key);
    byKey.set(record.key, record);
  }
  if (!byKey.size) throw new Error("No authoritative app/src/main/res/values resources");

  const sourcePaths = [
    ...walk(join(root, "app/src/main")),
    ...walk(join(root, "wear/src/main"))
  ].filter(p => [".kt", ".java", ".xml"].includes(extname(p))
    && !/\/res\/values[^/]*\//.test(p.replaceAll("\\", "/")));

  const uses = new Map([...byKey.keys()].map(key => [key, []]));
  const externalOrNotInAppBase = new Map();
  const dynamicHints = [];
  for (const file of sourcePaths) {
    const path = rel(root, file);
    const lines = readFileSync(file, "utf8").split(/\r?\n/);
    for (const [index, line] of lines.entries()) {
      const withoutLineComment = line.replace(/\/\/.*$/, "");
      for (const match of withoutLineComment.matchAll(lexicalReference)) {
        const type = match[1] || match[3], name = match[2] || match[4];
        const key = type + ":" + name;
        const hit = {path, line:index + 1, kind:"STATIC_REFERENCE_ONLY"};
        if (uses.has(key)) uses.get(key).push(hit);
        else {
          if (!externalOrNotInAppBase.has(key)) externalOrNotInAppBase.set(key, []);
          externalOrNotInAppBase.get(key).push(hit);
        }
      }
      if (/\bgetIdentifier\s*\(|\bgetQuantityText\s*\(|\bResources\.getSystem\s*\(|\bgetTextArray\s*\(/.test(withoutLineComment)) {
        dynamicHints.push({path,line:index + 1,kind:"DYNAMIC_OR_INDIRECT_RESOURCE_HINT"});
      }
    }
  }

  const localeFiles = resources.filter(r => r.module === "app"
    && r.sourceSet === "main" && r.qualifier !== "values");
  const qualifiers = [...new Set(localeFiles.map(x => x.qualifier))].sort();
  const translatableBase = [...byKey.values()].filter(x => x.translatable);
  const localeCoverage = qualifiers.map(qualifier => {
    const entries = localeFiles.filter(r => r.qualifier === qualifier);
    const keys = new Set(entries.map(x => x.key));
    const missing = translatableBase.filter(x => !keys.has(x.key)).map(x => x.key);
    return {
      qualifier, definitions:entries.length,
      presentTranslatable:translatableBase.length-missing.length,
      authoritativeTranslatable:translatableBase.length,
      missingCount:missing.length,
      missingKeys:missing
    };
  });
  const strings = [...byKey.entries()].map(([key, source]) => ({
    ...source,
    callsiteCount:uses.get(key).length,
    callsiteStatus:uses.get(key).length
      ? "STATIC_REFERENCE_FOUND" : "NO_STATIC_REFERENCE_FOUND",
    callsites:uses.get(key).slice(0, 20),
    truncatedCallsites:uses.get(key).length > 20,
    translatedVariants:qualifiers.filter(q =>
      localeFiles.some(row => row.qualifier === q && row.key === key))
  }));

  let g6SampleSize = null, g6SampleOverlap = null;
  const g6Path = join(root, "app/src/debug/assets/lp2/catalog.json");
  if (existsSync(g6Path)) {
    const g6 = JSON.parse(readFileSync(g6Path, "utf8"));
    const entries = Array.isArray(g6.entries) ? g6.entries : Object.values(g6.entries ?? {});
    const sample = entries.map(x => x.key).filter(Boolean);
    g6SampleSize = sample.length;
    g6SampleOverlap = sample.filter(key => byKey.has("string:" + key)).length;
  }

  const summary = {
    authoritativeBaseDefinitions:strings.length,
    sourceTypes:Object.fromEntries([...new Set(strings.map(x=>x.type))].sort()
      .map(type=>[type,strings.filter(x=>x.type===type).length])),
    translatableBaseKeys:translatableBase.length,
    formattedBaseDefinitions:strings.filter(x=>x.formatTokens.length).length,
    richMarkupBaseDefinitions:strings.filter(x=>x.hasRichMarkup).length,
    staticReferencedKeys:strings.filter(x=>x.callsiteCount).length,
    noDirectStaticReferenceKeys:strings.filter(x=>!x.callsiteCount).length,
    scannedCallsiteFiles:sourcePaths.length,
    dynamicHints:dynamicHints.length,
    nonBaseReferences:externalOrNotInAppBase.size,
    g6SampleSize, g6SampleOverlap
  };
  return {
    schema:"acx-b0-full-locale-resource-inventory/1",
    authority:"app/src/main/res/values/*.xml only; callsites lexical, not runtime proof",
    sourceSets:sourceDirs,
    summary, localeCoverage,
    sourceKeys:strings,
    nonBaseReferences:[...externalOrNotInAppBase.entries()]
      .sort(([a],[b])=>a.localeCompare(b,"en"))
      .map(([key,locations])=>({key,count:locations.length,locations:locations.slice(0,5)})),
    dynamicHints
  };
}

export function markdownSummary(inventory) {
  const s=inventory.summary;
  const locales=inventory.localeCoverage.map(r =>
    `| \`${r.qualifier}\` | ${r.presentTranslatable} / ${r.authoritativeTranslatable} | ${r.missingCount} |`).join("\n");
  return `# B0｜Android 全資源盤點（自動產出，非產品驗收）

> 方法：只以目前 checkout 的 \`app/src/main/res/values\` 定義為權威分母；其他限定 locale 記覆蓋率。Android Kotlin/XML 使用位置為靜態詞法索引，不能證明真實 runtime 畫面或操作後果。自訂語言包可用程度另由 B1/B2 驗收。

| 指標 | 數量 |
|---|---:|
| 權威基底資源 Key | ${s.authoritativeBaseDefinitions} |
| 可翻譯 Key | ${s.translatableBaseKeys} |
| 帶格式符號定義 | ${s.formattedBaseDefinitions} |
| 含 rich markup | ${s.richMarkupBaseDefinitions} |
| 找到直接靜態引用的基底 Key | ${s.staticReferencedKeys} |
| 未找到直接靜態引用的基底 Key | ${s.noDirectStaticReferenceKeys} |
| Kotlin/Java/XML 使用點掃描檔 | ${s.scannedCallsiteFiles} |
| 動態/間接資源提示 | ${s.dynamicHints} |
| G6 抽樣 Key | ${s.g6SampleSize ?? "無"} |
| G6 抽樣與全 App 基底交集 | ${s.g6SampleOverlap ?? "無"} |

### 現有 Android 資源限定語系覆蓋率

| 限定語系 | 有譯／可翻譯基底 | 缺漏 |
|---|---:|---:|
${locales || "| 無 | — | — |"}

### 未封閉的限制

- 沒有直接靜態引用不代表 Key 未使用，也可能通過動態 ID 或其他間接路徑。
- 位於 Compose、通知、前景服務、Wear 和系統資源的可見性需在 B0 後續審核，不能推導成 P 已達標。
- 缺漏會透過 Android 或動態解析器 fallback；實際語義品質與格式型別須另由 C1/B2 驗證。
- G6 200-Key 是 Debug 預覽試驗，不能把 122/200 當整個 App 的翻譯或動態切換率。
- 逐 Key 狀態、使用點、現有 locale 缺漏、格式與疑似動態引用參見同目錄 JSON。
`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const at = process.argv.indexOf("--out-dir");
  const dir = at < 0 ? resolve(ROOT, "build/localization/B0_FULL_APP_INVENTORY") :
    resolve(process.argv[at + 1] ?? "");
  if (!dir.startsWith(ROOT + (process.platform === "win32" ? "\\" : "/")))
    throw new Error("Output directory must remain within repository");
  let sha = "unavailable";
  try { sha = execFileSync("git", ["rev-parse", "HEAD"], {cwd: ROOT, encoding:"utf8"}).trim(); }
  catch { /* a local snapshot may not be a Git checkout */ }
  const data = {...buildInventory(ROOT), gitSha:sha};
  mkdirSync(dir, {recursive:true});
  writeFileSync(join(dir, "full-resource-inventory.json"), JSON.stringify(data,null,2) + "\n");
  writeFileSync(join(dir, "summary.md"), markdownSummary(data));
  console.log("B0_FULL_RESOURCE_INVENTORY " + JSON.stringify(data.summary));
}
