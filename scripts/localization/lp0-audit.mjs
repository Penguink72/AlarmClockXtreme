#!/usr/bin/env node
/**
 * LP0 source+translation preflight, Node.js built-ins only.
 * This is an integrity and review-queue gate, NOT a translation quality grader.
 */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
export const root = resolve(here, "../..");
export const loadJson = path => JSON.parse(readFileSync(join(root, path), "utf8"));
export const shaSource = (key, source) =>
  "sha256:" + createHash("sha256").update(key + "\u0000" + source, "utf8").digest("hex");

export function validateBaseline(catalog, en, zh, ja, matrix, glossary) {
  assert.equal(catalog.format, "acx-language-catalog-probe/1");
  assert.equal(catalog.count, 200);
  assert.equal(catalog.entries.length, 200);
  assert.equal(matrix.schema, "acx-lp0-source-matrix/1");
  assert.equal(matrix.baselineRevision, catalog.revision);
  assert.equal(matrix.sourceCommit, catalog.baseCommit);
  assert.equal(matrix.sourcePath, catalog.sourcePath);
  assert.equal(matrix.records.length, 200);
  assert.equal(glossary.schema, "acx-lp0-terminology-candidates/1");
  assert.equal(glossary.catalogRevision, catalog.revision);
  assert.equal(glossary.status, "DRAFT_PROPOSALS_ONLY");

  const byKey = new Map(catalog.entries.map(x => [x.key, x]));
  assert.equal(byKey.size, 200, "source catalogue contains duplicate keys");
  const seen = new Set();
  const validConcepts = new Set();
  for (const concept of glossary.concepts) {
    assert.match(concept.conceptId, /^[a-z]+(?:\.[a-z_]+){1,4}$/);
    assert.equal(concept.reviewState, "PROPOSED_NOT_APPROVED");
    assert.equal(concept.matchingMethod, "SOURCE_OR_KEY_HEURISTIC");
    assert(!validConcepts.has(concept.conceptId), "duplicate concept " + concept.conceptId);
    validConcepts.add(concept.conceptId);
    for (const key of concept.matchedKeys) {
      assert(byKey.has(key), "terminology refers to unknown key " + key);
    }
    assert.equal(concept.matchedKeys.length, new Set(concept.matchedKeys).size, "duplicate linked key");
  }

  const states = {"zh-TW": {existing:0,missing:0,approved:0}, ja:{existing:0,missing:0,approved:0}};
  const sectionCounts = {};
  const surfaceGuesses = {};
  let highRisk=0, termLinked=0;
  for (const [index, record] of matrix.records.entries()) {
    const source = catalog.entries[index];
    assert.deepEqual(
      [record.key,record.section,record.source,record.placeholders],
      [source.key,source.section,source.source,source.placeholders],
      "catalog ordering/data mismatch at index " + index
    );
    assert(!seen.has(record.key), "duplicate review record " + record.key);
    seen.add(record.key);
    assert.equal(en.translations[record.key], record.source, "English source has changed: " + record.key);
    assert.equal(record.sourceHash, shaSource(record.key, record.source), "source hash mismatch " + record.key);
    assert.equal(record.context.evidence, "KEY_PREFIX_HEURISTIC_ONLY");
    assert.equal(record.context.reviewState, "UNVERIFIED");
    assert.equal(record.context.notes, null);
    assert(["HIGH_REVIEW_PRIORITY","NORMAL"].includes(record.reviewPriority));
    if (record.reviewPriority === "HIGH_REVIEW_PRIORITY") highRisk++;
    sectionCounts[record.section] = (sectionCounts[record.section] || 0) + 1;
    surfaceGuesses[record.context.surfaceGuess] = (surfaceGuesses[record.context.surfaceGuess] || 0) + 1;
    const linked = glossary.concepts
      .filter(x => x.matchedKeys.includes(record.key)).map(x => x.conceptId);
    assert.deepEqual(record.candidateConceptIds, linked, "concept link drift " + record.key);
    if (linked.length) termLinked++;
    for (const [locale, pack] of [["zh-TW", zh], ["ja",ja]]) {
      const trans=record.translations[locale];
      assert(trans, "review record is missing locale " + locale);
      const exists=Object.hasOwn(pack.translations,record.key);
      const expected=exists ? pack.translations[record.key] : null;
      assert.equal(trans.text,expected,locale + " source translation drift " + record.key);
      assert.equal(trans.state, exists ? "UNREVIEWED_EXISTING" : "MISSING",
        "existing translation must never be auto-approved " + record.key);
      assert.equal(trans.basedOnSourceHash, exists ? record.sourceHash : null);
      assert.equal(trans.reviewedBy,null,"reviewer identity cannot be inferred");
      assert.equal(trans.reviewedAt,null,"review timestamp cannot be inferred");
      if (exists) states[locale].existing++;
      else states[locale].missing++;
    }
  }
  assert.equal(seen.size, 200);
  assert.deepEqual(Object.keys(sectionCounts).sort(), Object.keys(catalog.sections).sort());
  for (const [category, count] of Object.entries(sectionCounts)) {
    assert.equal(count,catalog.sections[category].total);
  }
  for (const [locale,pack] of [["en",en],["zh-TW",zh],["ja",ja]]) {
    assert.equal(pack.catalogRevision,catalog.revision);
    for (const key of Object.keys(pack.translations))assert(seen.has(key), locale+" untracked key "+key);
  }
  assert.equal(Object.keys(en.translations).length,200);
  assert.equal(states["zh-TW"].existing,155);
  assert.equal(states.ja.existing,155);
  assert.equal(states["zh-TW"].missing,45);
  assert.equal(states.ja.missing,45);

  const snooze = matrix.records
    .filter(x => x.candidateConceptIds.includes("alarm.action.snooze") && x.translations["zh-TW"].text !== null);
  const withSnooze = snooze.filter(x => x.translations["zh-TW"].text.includes("貪睡")).map(x => x.key);
  const withLater = snooze.filter(x => x.translations["zh-TW"].text.includes("稍後提醒")).map(x => x.key);
  const translationEqualsSource = {};
  for (const locale of ["zh-TW","ja"]) {
    translationEqualsSource[locale] = matrix.records
      .filter(x => x.translations[locale].text!==null && x.translations[locale].text===x.source)
      .map(x => x.key);
  }
  return {
    total: 200,
    states,
    priorityReview: highRisk,
    contextUnverified: matrix.records.filter(x=>x.context.reviewState==="UNVERIFIED").length,
    provisionalConcepts: validConcepts.size,
    termLinked,
    termUnlinked: 200-termLinked,
    surfaceGuesses,
    snoozeVariantCandidates: {zhContainsSnooze:withSnooze,zhContainsLaterReminder:withLater},
    sameAsSourceForManualReview:translationEqualsSource,
    approved:0
  };
}

export const loadInputs = () => [
  loadJson("experiments/lp0-200/catalog.json"),
  loadJson("experiments/lp0-200/packs/en.json"),
  loadJson("experiments/lp0-200/packs/zh-TW.json"),
  loadJson("experiments/lp0-200/packs/ja.json"),
  loadJson("localization/governance/lp0/source-matrix.json"),
  loadJson("localization/governance/lp0/terminology-candidates.json")
];

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result=validateBaseline(...loadInputs());
  console.log("LP0-GOVERNANCE-AUDIT PASS: 200 keys, 155 zh-TW, 155 ja, all existing translations UNREVIEWED");
  console.log(JSON.stringify(result,null,2));
}
