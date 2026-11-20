#!/usr/bin/env node
/**
 * Batch 2: auditable 24-key bilingual model review + full draft-key coverage.
 * A model's contextual check is NOT independent semantic approval.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import { loadJson } from "./lp0-audit.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const {placeholders}=core;
export const loadInputs=()=>({
 matrix:loadJson("localization/governance/lp0/source-matrix.json"),
 originalProposals:loadJson("localization/governance/g3/draft-proposals.json"),
 firstReview:loadJson("localization/governance/g4/content-review-batch1.json"),
 secondReview:loadJson("localization/governance/g4/content-review-batch2.json"),
 contexts:loadJson("localization/governance/g2/context-evidence.json"),
 glossary:loadJson("localization/governance/g1/glossary-contract.json"),
 approvalLedger:loadJson("localization/governance/g4/review-ledger.json")
});
export function verifyBatch2({matrix,originalProposals,firstReview,secondReview,contexts,glossary,approvalLedger}){
 assert.equal(secondReview.schema,"acx-g4-model-assisted-content-review-batch2/1");
 assert.equal(secondReview.catalogRevision,matrix.baselineRevision);
 assert.equal(secondReview.glossaryRevision,glossary.glossaryRevision);
 assert.equal(originalProposals.glossaryRevision,glossary.glossaryRevision);
 assert.equal(secondReview.humanApproval,false);
 assert.equal(secondReview.modelReview,true);
 assert.equal(secondReview.scope.keys,24);
 assert.equal(secondReview.scope.modelReviewedDrafts,48);
 assert.equal(secondReview.scope.independentApprovals,0);
 assert.equal(secondReview.scope.remainingMissingAfterDrafts,0);
 assert.equal(secondReview.rows.length,24);
 assert.equal(approvalLedger.approvals.length,0);
 assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
 assert.equal(contexts.records.length,200);
 const byKey=new Map(matrix.records.map(r=>[r.key,r]));
 const byContext=new Map(contexts.records.map(r=>[r.key,r]));
 const byDraft=new Map(originalProposals.translations.map(r=>[r.key,r]));
 const firstKeys=new Set(firstReview.newCandidateReview.map(r=>r.key));
 const secondKeys=new Set();
 assert.equal(firstKeys.size,21);
 assert.equal(byDraft.size,45,"Draft list must contain exactly all 45 missing LP0 keys");
 for(const row of secondReview.rows){
   assert(!secondKeys.has(row.key),"Duplicate second-batch key");
   secondKeys.add(row.key);
   assert(!firstKeys.has(row.key),"Second-batch key overlaps reviewed first batch");
   const source=byKey.get(row.key),candidate=byDraft.get(row.key);
   assert(source && candidate,"Unknown key in batch2 "+row.key);
   assert.equal(source.source,row.source);
   assert.equal(source.sourceHash,row.sourceHash);
   assert.equal(source.translations["zh-TW"].text,null,"Overwrite of legacy zh-TW forbidden");
   assert.equal(source.translations.ja.text,null,"Overwrite of legacy ja forbidden");
   assert.equal(candidate.draftBatch,"B2_20261120");
   for(const locale of ["zh-TW","ja"]){
     assert.equal(candidate[locale],row.proposals[locale],"Untracked model translation edit "+locale+"/"+row.key);
     assert.deepEqual(placeholders(candidate[locale]),source.placeholders,
       "Changed printf placeholder "+locale+"/"+row.key);
   }
   assert.deepEqual(row.formatPlaceholders,source.placeholders);
   assert.equal(row.reviewStatus,"MODEL_CONTEXT_CHECK_DONE_HUMAN_PENDING");
   assert.equal(row.humanApproval,null);
   assert(row.contextRationale.length>=15);
   assert.equal(row.sourceEvidence.kind,"EXACT_STATIC_SOURCE_CALLSITE");
   const path=row.sourceEvidence.path,line=row.sourceEvidence.line;
   assert(path.startsWith("app/src/main/")&&Number.isInteger(line)&&line>0);
   const ref=byContext.get(row.key)?.sourceCallsites||[];
   assert(ref.some(r=>r.path===path&&r.line===line),"Unverifiable G2 source reference "+row.key);
   const raw=readFileSync(resolve(root,path),"utf8").split(/\r?\n/);
   assert(raw[line-1]?.includes("R.string."+row.key),"Source line drift "+row.key);
 }
 assert.equal(secondKeys.size,24);
 const unreviewedKeys=new Set([...firstKeys,...secondKeys]);
 assert.equal(unreviewedKeys.size,45);
 const missingKeys=matrix.records.filter(r=>r.translations["zh-TW"].text===null && r.translations.ja.text===null).map(r=>r.key);
 assert.equal(missingKeys.length,45);
 assert.deepEqual([...unreviewedKeys].sort(),missingKeys.sort(),"Draft coverage does not equal original missing keys");
 for(const key of unreviewedKeys)assert(byDraft.has(key));
 const track=byContext.get("stats_track_consistency_snooze_behavior_and_which");
 assert(track.rawHeuristicConceptIds.includes("music.track"));
 assert(!track.conceptIds.includes("music.track"));
 const noReference=contexts.records.filter(r=>!r.sourceCallsites.length).map(r=>r.key);
 assert.equal(noReference.length,5);
 return {
  sourceKeys:matrix.records.length,legacyKeysPerLocale:155,
  bilingualDraftKeys:unreviewedKeys.size,bilingualDraftStrings:90,
  newlyReviewedDraftKeys:secondKeys.size,modelReviewedThisBatch:48,
  previewCoveragePerLocale:200,unresolvedDirectReferences:noReference,
  humanApproved:0,releaseAllowed:false
 };
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 console.log("G4_BATCH2_CONTENT_AUDIT PASS "+JSON.stringify(verifyBatch2(loadInputs())));
