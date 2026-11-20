#!/usr/bin/env node
import assert from "node:assert/strict";
import { verifyBatch2, loadInputs } from "./g4-batch2-audit.mjs";
const base=loadInputs(),check=data=>verifyBatch2(data);
const baseline=check(base);
assert.equal(baseline.bilingualDraftStrings,90);
assert.equal(baseline.humanApproved,0);
const key=base.secondReview.rows[0].key;
{
 const bad=structuredClone(base);
 bad.originalProposals.translations.find(d=>d.key===key)["zh-TW"]="untracked text";
 assert.throws(()=>check(bad),/Untracked model translation edit/);
}
{
 const bad=structuredClone(base);
 bad.secondReview.rows[0].humanApproval={reviewerId:"fake"};
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.secondReview.rows[0].sourceEvidence.line=1;
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.secondReview.rows[0].proposals.ja+="%1$s";
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.secondReview.rows.pop();
 assert.throws(()=>check(bad));
}
{
 const bad=structuredClone(base);
 bad.originalProposals.translations[0].key=key;
 assert.throws(()=>check(bad),/Draft list must contain exactly all 45 missing|Draft coverage|Duplicate second-batch key/);
}
{
 const bad=structuredClone(base);
 bad.contexts.records.find(r=>r.key==="stats_track_consistency_snooze_behavior_and_which").conceptIds.push("music.track");
 assert.throws(()=>check(bad));
}
console.log("G4_BATCH2_NEGATIVE_TESTS PASS: fake approval, callsite drift, missing/duplicate draft, concept regression");
