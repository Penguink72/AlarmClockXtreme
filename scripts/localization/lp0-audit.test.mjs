#!/usr/bin/env node
/* Node-only LP0 negative regression tests: no automatic trust in existing translations. */
import assert from "node:assert/strict";
import { loadInputs, validateBaseline, shaSource } from "./lp0-audit.mjs";

const original=loadInputs();
const run = arr => validateBaseline(...arr);
const clone=() => structuredClone(original);
assert.equal(shaSource("abc","hello").length,71);
const baseline=run(original);
assert.equal(baseline.total,200);
assert.equal(baseline.approved,0);
assert.equal(baseline.contextUnverified,200);
assert.equal(baseline.provisionalConcepts,18);
assert.equal(baseline.states["zh-TW"].existing,155);
assert.equal(baseline.states.ja.missing,45);
assert(baseline.snoozeVariantCandidates.zhContainsSnooze.length > 0);
assert(baseline.snoozeVariantCandidates.zhContainsLaterReminder.length > 0);

// Simulate a model inventing review status without an explicit reviewer.
{
  const state=clone();state[4].records[0].translations["zh-TW"].state="APPROVED";
  assert.throws(()=>run(state),/must never be auto-approved|existing translation/);
}
{
  const state=clone();state[4].records[0].context.reviewState="VERIFIED";
  assert.throws(()=>run(state));
}
{
  const state=clone();state[4].records[0].source="Unreviewed new source";
  assert.throws(()=>run(state));
}
{
  const state=clone();state[4].records[0].sourceHash="sha256:forged";
  assert.throws(()=>run(state));
}
{
  const state=clone();state[4].records[0].translations["ja"].text="fabricated";
  assert.throws(()=>run(state));
}
{
  const state=clone();state[4].records[0].candidateConceptIds=["nonexistent.concept"];
  assert.throws(()=>run(state));
}
{
  const state=clone();state[5].concepts[0].reviewState="APPROVED";
  assert.throws(()=>run(state));
}
{
  const state=clone();state[4].records.pop();
  assert.throws(()=>run(state));
}
console.log("LP0-GOVERNANCE-NEGATIVE-TESTS PASS: rejects fake approval, context promotion, source/translation drift, forged hash, invented term link and missing record");
