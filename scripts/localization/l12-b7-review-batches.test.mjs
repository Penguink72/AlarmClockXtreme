#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve,dirname,join} from "node:path";
import {fileURLToPath} from "node:url";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
import {triageWarnings} from "./l12-b4-aapt2-staging.mjs";
import {buildWarningDispositions} from "./l12-b5-warning-disposition.mjs";
import {buildReviewBatchPlan,validateUntrustedReviewNotes} from "./l12-b7-review-batches.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
function fixture(){
  if(cached)return cached;
  const source=loadUnifiedInputs(ROOT,"L12-B7-TEST-UNAPPROVED");
  const ledger=JSON.parse(readFileSync(join(ROOT,"localization/governance/l12/independent-review-ledger.json"),"utf8"));
  const preflight=stageSourceRows({l11:source.l11,l12:source.l12,matrix:source.matrix,
    glossary:source.glossary,g4Ledger:source.g4Ledger,reviewDecisions:ledger});
  const warnings=buildWarningDispositions(triageWarnings(preflight),preflight);
  return cached={source,warnings,plan:buildReviewBatchPlan(source.packet,warnings)};
}
function validNote(r,p){
  return {schema:"acx-l12-b7-untrusted-review-notes/1",
    reviewSetDigest:p.summary.reviewSetDigest,
    batchPlanDigest:p.summary.batchPlanDigest,
    entries:[{
      key:r.key,locale:r.locale,sourceHash:r.sourceHash,
      translationHash:r.translationHash,reviewRevision:r.reviewRevision,
      disposition:"NEEDS_MORE_CONTEXT",note:"Inspect the concrete UI operation"
    }]};
}
test("L12-7 covers exactly 3100 rows as 1550 two-locale navigation cards",()=>{
  const x=fixture(),p=x.plan;
  assert.equal(p.summary.reviewRequiredLocaleRows,3100);
  assert.equal(p.summary.reviewKeyCards,1550);
  assert.equal(p.summary.sourceRiskSensitiveLocaleRows,986);
  assert.equal(p.summary.sourceGeneralLocaleRows,2114);
  assert.equal(p.summary.reviewApprovedLocaleRows,0);
  assert.equal(p.summary.distinctFlaggedLocaleRows,43);
  const cards=p.batches.flatMap(b=>b.cards);
  const rows=cards.flatMap(c=>c.localeRows);
  assert.equal(cards.length,1550);assert.equal(rows.length,3100);
  assert.equal(new Set(cards.map(c=>c.key)).size,1550);
  assert.equal(new Set(rows.map(r=>r.key+"\0"+r.locale)).size,3100);
  assert(cards.every(c=>c.localeRows.length===2 &&
    c.localeRows[0].locale==="zh-TW"&&c.localeRows[1].locale==="ja"));
  assert(rows.every(r=>r.independentApproved===false&&r.mayRelease===false));
});
test("L12-7 priority tiers are sorted and batches are small enough to review",()=>{
  const p=fixture().plan,order=["P0_OPERATION_SECURITY_DATA","P1_MEDIA_PLAYBACK",
    "P2_FORMAT_AND_WARNING","P3_GENERAL_UI"],caps=[10,16,16,28];
  let previous=-1;
  for(const b of p.batches){
    const i=order.indexOf(b.priority);
    assert(i>=0 && i>=previous);previous=i;
    assert(b.keyCards>0&&b.keyCards<=caps[i]);
    assert.equal(b.localeRows,b.keyCards*2);
    assert.equal(b.semanticApprovalCount,0);
  }
  assert.equal(p.batches.reduce((n,b)=>n+b.keyCards,0),1550);
  assert.equal(p.batches.reduce((n,b)=>n+b.localeRows,0),3100);
  assert.equal(p.summary.batches,p.batches.length);
});
test("L12-7 source duplicate links are navigation only, never common approvals",()=>{
  const p=fixture().plan;
  for(const c of p.batches.flatMap(b=>b.cards)){
    if(c.sameEnglishIsNotSameAction){
      assert(c.sameEnglishAsOtherKeys.every(key=>key!==c.key));
      assert.equal(c.candidatePairedForNavigationOnly,true);
      assert.equal(c.sourceContextNotProven,true);
    }
  }
});
test("L12-7 detects stale warning source, forgery and incomplete packets",()=>{
  const f=fixture();
  const clone=structuredClone(f.warnings);
  clone.items[0].translationHash="sha256:stale";
  assert.throws(()=>buildReviewBatchPlan(f.source.packet,clone),/drift/);
  const bad=structuredClone(f.source.packet);
  bad.rows[0].independentApproved=true;
  assert.throws(()=>buildReviewBatchPlan(bad,f.warnings),/falsely claims approval/);
  const missing=structuredClone(f.source.packet);missing.rows.pop();
  assert.throws(()=>buildReviewBatchPlan(missing,f.warnings),/expected fixed/);
});
test("L12-7 notes validate exact row revision and remain untrusted",()=>{
  const p=fixture().plan,r=p.batches[0].cards[0].localeRows[0];
  const x=validNote(r,p),ok=validateUntrustedReviewNotes(p,x);
  assert.equal(ok.status,"VALID_UNTRUSTED_NOTES_ONLY");
  assert.equal(ok.notedLocaleRows,1);assert.equal(ok.officialApprovedLocaleRows,0);
  assert.equal(ok.mayRelease,false);
  const changed=structuredClone(x);changed.entries[0].translationHash="sha256:stale";
  assert(validateUntrustedReviewNotes(p,changed).errors.includes("STALE_NOTE_SOURCE_OR_TRANSLATION"));
  const dup=structuredClone(x);dup.entries.push({...dup.entries[0]});
  assert(validateUntrustedReviewNotes(p,dup).errors.includes("DUPLICATE_REVIEW_NOTE"));
  const fake=structuredClone(x);fake.entries[0].independentApproval=true;
  assert(validateUntrustedReviewNotes(p,fake).errors.includes("NOTE_CONTAINS_FAKE_APPROVAL_FIELDS"));
  const forged=structuredClone(x);forged.entries[0].disposition="APPROVED";
  assert(validateUntrustedReviewNotes(p,forged).errors.includes("UNAUTHORIZED_NOTE_DISPOSITION"));
  const stale=structuredClone(x);stale.batchPlanDigest="sha256:stale";
  assert.equal(validateUntrustedReviewNotes(p,stale).status,"INVALID");
});
test("L12-7 offline desk avoids external scripts and never submits official authorization",()=>{
  const t=readFileSync(join(ROOT,"scripts/localization/l12-b7-review-desk-template.html"),"utf8");
  assert(t.includes("<!--L12B7_DATA-->"));
  assert(t.includes('type="file"'));
  assert(t.includes('type="application/json"'));
  assert(!t.includes('src="https://'));
  assert(!t.includes("<button id=\"approve\""));
  assert(t.includes("PROVISIONAL_UNTRUSTED_NO_APPROVAL"));
  assert(t.includes("reviewSetDigest"));
});
