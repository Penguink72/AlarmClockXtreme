#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import {
  extractResourceDefinitions, buildInventory, markdownSummary
} from "./b0-resource-inventory.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("B0 parser indexes strings, plurals, arrays, formatting and ignores comment/CDATAs", () => {
  const xml = `<resources>
    <!-- <string name="fake">Do not count</string> -->
    <string name="title">Alarm</string>
    <string name="formatted">Due in %1$d minutes, %2$s</string>
    <string-array name="options"><item>First</item><item>Next</item></string-array>
    <plurals name="minutes"><item quantity="one">%d minute</item><item quantity="other">%d minutes</item></plurals>
    <string name="raw"><![CDATA[<string name="not_a_resource">fake</string>]]></string>
    <string name="internal" translatable="false">INTERNAL</string>
  </resources>`;
  const rows = extractResourceDefinitions(xml);
  assert.deepEqual(rows.map(x => x.key), [
    "string:title", "string:formatted", "array:options",
    "plurals:minutes", "string:raw", "string:internal"
  ]);
  assert.deepEqual(rows[1].formatTokens, ["%1$d", "%2$s"]);
  assert.deepEqual(rows[3].pluralQuantities, ["one", "other"]);
  assert.equal(rows[5].translatable, false);
  assert.equal(rows.some(x => x.name === "fake" || x.name === "not_a_resource"), false);
});

test("B0 rejects repeated XML definitions instead of double-counting progress", () => {
  assert.throws(() => extractResourceDefinitions(
    '<resources><string name="twice">x</string><string name="twice">y</string></resources>'
  ), /Duplicate resource definition/);
});

test("B0 scans the actual entire app resources, not only the 200-key sample", () => {
  const inventory = buildInventory(root);
  const base = inventory.summary.authoritativeBaseDefinitions;
  assert(base > 200, "Expected the complete Android catalog to exceed G6's sample");
  assert.equal(inventory.sourceKeys.length, base);
  assert(inventory.summary.scannedCallsiteFiles > 0);
  assert(inventory.localeCoverage.some(x => x.qualifier === "values-ja"));
  assert(inventory.localeCoverage.some(x => x.qualifier === "values-b+zh+Hant+TW"));
  assert.equal(inventory.sourceKeys.some(x => x.key === "string:alarm_music_settings_apply_draft"), true);
  assert.equal(inventory.sourceKeys.some(x => x.key === "string:music_library_category_videos"), true);
  assert.equal(inventory.summary.staticReferencedKeys +
    inventory.summary.noDirectStaticReferenceKeys, base);
  assert.match(markdownSummary(inventory), /B0/);
});

test("B0 explicitly distinguishes absent static refs from verified unused", () => {
  const inventory = buildInventory(root);
  assert(inventory.sourceKeys.every(x =>
    x.callsiteStatus === "STATIC_REFERENCE_FOUND" ||
    x.callsiteStatus === "NO_STATIC_REFERENCE_FOUND"));
  assert.equal(inventory.sourceKeys.some(x => x.callsiteStatus === "LEGACY_UNUSED"), false);
});
