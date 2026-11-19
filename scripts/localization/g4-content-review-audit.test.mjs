#!/usr/bin/env node
import assert from "node:assert/strict";
import { verifyContentReview,inputs } from "./g4-content-review-audit.mjs";
const base=inputs();
const validate=x=>verifyContentReview(x);
const baseline=validate(base);
assert.equal(baseline.concepts,18);
assert.equal(baseline.modelReviewedLocaleDrafts,42);
assert.equal(baseline.draftKeysRevised,9);
assert.equal(baseline.heuristicLinksRefuted,9);
assert.equal(baseline.humanApprovals,0);
{
 const x=structuredClone(base);
 x.review.newCandidateReview[0].currentDraft["zh-TW"]="silently modified";
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.review.newCandidateReview[0].sourceEvidence.line=1;
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.review.humanApproved=true;
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.approvals.approvals=[{key:"faker",locale:"ja",decision:"APPROVED"}];
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.review.refutedHeuristicLinks[0].key="nonexistent";
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.review.termFindings[0].preferredCandidates.ja="fake";
 assert.throws(()=>validate(x));
}
{
 const x=structuredClone(base);
 x.drafts.translations[1]["ja"]="untracked candidate edit";
 assert.throws(()=>validate(x));
}
console.log("CONTENT_REVIEW_NEGATIVE_TESTS PASS: drift, false evidence, fake approvals and concept-link corruption rejected");
