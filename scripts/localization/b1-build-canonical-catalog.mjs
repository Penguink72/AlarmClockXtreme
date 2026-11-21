#!/usr/bin/env node
/**
 * B1-C1: build an Android-asset catalog from the exact B0-T1 source matrix.
 * Generates metadata for each of 2,471 authoritative keys, not translated text.
 * Committed asset must be byte-for-byte reproducible and matches source hashes.
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { generateMatrix } from "./b0-t1-canonical-matrix.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const DESTINATION = join(ROOT, "app/src/main/assets/localization/v1/catalog.json");

export function buildCanonicalCatalog(matrix = generateMatrix()) {
  const entries = matrix.rows.map(row => ({
    key: row.key,
    type: row.type,
    translatable: row.translatable,
    sourceHash: row.sourceHash,
    sourceXml: row.sourceXml,
    formatTokens: row.formatTokens,
    pluralQuantities: row.pluralQuantities,
    hasRichMarkup: row.hasRichMarkup,
    arrayItemCount: row.type === "array"
      ? [...row.sourceXml.matchAll(/<item(?:\s[^>]*)?>/g)].length : 0
  }));
  return {
    format: "acx-language-catalog/1",
    catalogRevision: matrix.catalogueRevision,
    authority: "app/src/main/res/values/strings.xml",
    sourceKeyCount: entries.length,
    entries
  };
}

export function canonicalCatalogJson(catalog = buildCanonicalCatalog()) {
  return JSON.stringify(catalog, null, 2) + "\n";
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const arg = process.argv.indexOf("--out");
  const out = arg >= 0 ? resolve(process.argv[arg + 1]) : DESTINATION;
  if (!(out === DESTINATION || out.startsWith(ROOT + (process.platform === "win32" ? "\\" : "/"))))
    throw Error("Output path must remain inside repository");
  const catalog = buildCanonicalCatalog();
  const json = canonicalCatalogJson(catalog);
  if (process.argv.includes("--verify")) {
    const previous = readFileSync(out, "utf8");
    if (previous !== json) throw Error("B1 canonical catalog asset out of sync with B0-T1 source");
  } else {
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, json);
  }
  console.log("B1_CANONICAL_CATALOG " + JSON.stringify({
    count: catalog.sourceKeyCount,
    revision: catalog.catalogRevision,
    verify: process.argv.includes("--verify")
  }));
}
