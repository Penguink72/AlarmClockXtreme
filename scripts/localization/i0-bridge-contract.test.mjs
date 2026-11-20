#!/usr/bin/env node
/* I0: inspect actual G5 generated JSON against LP1's Debug-only importer contract. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = path => JSON.parse(readFileSync(path, "utf8"));
const source = read(join(root, "experiments/lp0-200/catalog.json"));
const debug = read(join(root, "app/src/debug/assets/lp1/catalog.json"));
assert.deepEqual(debug, source, "LP1 Debug catalog diverges from LP-G source catalog");
assert.equal(debug.entries.length, 200);
const previewDir = resolve(root, process.argv[2] || "build/localization/UNREVIEWED_PREVIEW_NOT_RELEASE");
const manifest = read(join(previewDir, "manifest.json"));
assert.equal(manifest.schema, "acx-g5-pack-manifest/1");
assert.equal(manifest.releaseChannel, "UNREVIEWED_PREVIEW_NOT_RELEASE");
assert.equal(manifest.mayDistributeAsOfficial, false);
assert.equal(manifest.reviewStatus, "no-human-approval");
assert.equal(manifest.catalogRevision, debug.revision);
assert.equal(manifest.frozenBaselinePreview,true);
assert.deepEqual(manifest.staleCanonicalSourceKeys,["music_playlist_integration_pending"]);
const kotlin = readFileSync(join(root,
  "app/src/debug/java/com/sysadmindoc/alarmclock/ui/languagelab/Lp1Repository.kt"), "utf8");
assert.match(kotlin, /PACK_FORMAT = "acx-language-pack-probe\/1"/);
assert.match(kotlin, /CATALOG_FORMAT = "acx-language-catalog-probe\/1"/);
assert.match(kotlin, /MAX_BYTES = 256 \* 1024/);
assert.match(kotlin, /AtomicFile\(/);
const defects = read(join(root, "localization/governance/g4/source-defects.json"));
assert(defects.items.some(x => x.releaseBlocking && x.status !== "RESOLVED"),
  "I0 must not silently close source defects or unblock official release");
for (const locale of ["zh-TW", "ja"]) {
  const raw = readFileSync(join(previewDir, locale + ".json"), "utf8");
  assert(Buffer.byteLength(raw, "utf8") <= 256 * 1024, "LP1 import size exceeded: " + locale);
  const pack = JSON.parse(raw);
  const validated = core.validatePack(pack, debug);
  assert.equal(validated.locale, locale);
  assert.equal(Object.keys(validated.translations).length, 200, locale + ": expected 200 preview keys");
  assert.equal(manifest.totals[locale].present, 200);
  assert.equal(manifest.totals[locale].approved, 0);
  assert.equal(manifest.totals[locale].unreviewedModelDrafts, 45);
  assert.equal(manifest.totals[locale].unreviewedLegacy, 155);
  assert.throws(() => core.validatePack({...pack, catalogRevision: "stale"}, debug),
    /不相容/, locale + ": stale catalog must be rejected");
  assert.throws(() => core.validatePack({...pack, translations: {...pack.translations, unknown_i0_key: "x"}}, debug),
    /未知 Key|超過/, locale + ": unknown key must be rejected");
  const withArgs = debug.entries.find(x => x.placeholders.length);
  assert(withArgs);
  assert.throws(() => core.validatePack({
    ...pack, translations: {...pack.translations, [withArgs.key]: "Missing parameter"}
  }, debug), /格式參數不符/, locale + ": bad parameters must be rejected");
}
console.log("I0_BRIDGE_CONTRACT PASS: G5 preview JSON matches LP1 Debug catalog and portable parser; malformed packs rejected; official release remains blocked");
