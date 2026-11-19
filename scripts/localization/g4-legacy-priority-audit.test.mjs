#!/usr/bin/env node
import assert from "node:assert/strict";
import {inputs,verifyLegacyReview} from "./g4-legacy-priority-audit.mjs";
const base=inputs(),check=x=>verifyLegacyReview(x);
const ok=check(base);
assert.equal(ok.flagged,52);assert.equal(ok.suggestions,6);assert.equal(ok.approved,0);
const sample=base.report.records.find(x=>x.suggestedReplacement);
assert(sample);
{
 const bad=structuredClone(base);
 bad.report.records.find(r=>r.key===sample.key).humanApproved=true;
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.report.records.find(r=>r.key===sample.key).evidence.line=1;
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.report.records.find(r=>r.key===sample.key).suggestedReplacement.text="Changed %1$s";
 assert.throws(()=>check(bad),/printf arguments/);
}
{
 const bad=structuredClone(base);
 bad.report.records.find(r=>r.key===sample.key).existing["zh-TW"]="an untracked change";
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.report.records.pop();
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.ledger.approvals=[{locale:"ja",key:"bogus"}];
 assert.throws(()=>check(bad));
}
console.log("G4_LEGACY_TRIAGE_NEGATIVE_TESTS PASS: forged approval, context, placeholder, legacy drift and missing record rejected");
