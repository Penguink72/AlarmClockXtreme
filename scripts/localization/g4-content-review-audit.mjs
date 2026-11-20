#!/usr/bin/env node
/**
 * G4 model-assisted content review integrity checks.
 * This checks that source evidence and revised candidate values really match
 * the selected 200-key snapshot. It DOES NOT certify semantic quality.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { loadJson } from "./lp0-audit.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)), "../..");
export const inputs=()=>({
  review:loadJson("localization/governance/g4/content-review-batch1.json"),
  glossary:loadJson("localization/governance/g1/glossary-contract.json"),
  matrix:loadJson("localization/governance/lp0/source-matrix.json"),
  drafts:loadJson("localization/governance/g3/draft-proposals.json"),
  contexts:loadJson("localization/governance/g2/context-evidence.json"),
  approvals:loadJson("localization/governance/g4/review-ledger.json")
});
export function verifyContentReview({review,glossary,matrix,drafts,contexts,approvals}){
 assert.equal(review.schema,"acx-g4-model-assisted-content-review/1");
 assert.equal(review.authority,"MODEL_ASSISTED_REVIEW_ONLY");
 assert.equal(review.humanApproved,false);
 assert.equal(review.scope.semanticHumanApprovals,0);
 assert.equal(review.scope.preExistingTranslationsFullyReviewed,false);
 assert.equal(approvals.approvals.length,0);
 assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
 assert.equal(review.glossaryRevision,glossary.glossaryRevision);
 assert.equal(drafts.glossaryRevision,glossary.glossaryRevision);
 assert.equal(approvals.glossaryRevision,glossary.glossaryRevision);
 assert.equal(matrix.baselineRevision,review.catalogRevision);
 assert.equal(contexts.catalogRevision,review.catalogRevision);
 assert.equal(review.termFindings.length,18);
 assert.equal(review.newCandidateReview.length,21);
 assert.equal(review.scope.localeDraftsReviewed,42);
 const terms=new Map(glossary.concepts.map(t=>[t.id,t]));
 const sources=new Map(matrix.records.map(r=>[r.key,r]));
 const contextsByKey=new Map(contexts.records.map(r=>[r.key,r]));
 const draftsByKey=new Map(drafts.translations.map(r=>[r.key,r]));
 assert.equal(terms.size,18);
 assert(draftsByKey.size>=21,"Batch 1 remains a subset of extended drafts");
 const seenConcept=new Set();
 for(const entry of review.termFindings){
   const t=terms.get(entry.conceptId);
   assert(t,"Unknown concept in review "+entry.conceptId);
   assert(!seenConcept.has(entry.conceptId),"Duplicated concept review");
   seenConcept.add(entry.conceptId);
   assert.deepEqual(entry.preferredCandidates,t.labels,"Glossary candidate drift "+entry.conceptId);
   assert.equal(entry.approved,false);
   assert(t.labelState==="CANDIDATE_NOT_APPROVED");
   assert(entry.assessment && entry.assessment.trim().length>=10,"Missing content rationale");
 }
 assert.equal(seenConcept.size,18);
 const seen=new Set();let revisions=0;
 for(const row of review.newCandidateReview){
   const source=sources.get(row.key),draft=draftsByKey.get(row.key);
   assert(source && draft,"Invalid source or missing LLM draft "+row.key);
   assert(!seen.has(row.key),"Duplicate review key "+row.key);
   seen.add(row.key);
   assert.equal(row.source,source.source);
   assert.equal(row.sourceHash,source.sourceHash);
   assert.deepEqual(row.locales,["zh-TW","ja"]);
   assert.equal(row.currentDraft["zh-TW"],draft["zh-TW"]);
   assert.equal(row.currentDraft.ja,draft.ja);
   assert.equal(row.reviewStatus,"MODEL_CONTEXT_CHECK_DONE_HUMAN_PENDING");
   assert.equal(row.reviewedByType,"model");
   assert.equal(row.humanApproval,null);
   assert(row.rationale.length>=10,"Missing semantic context rationale");
   const loc=row.sourceEvidence;
   assert.equal(loc.kind,"STATIC_CODE_CALLSITE_PLUS_READ_CODE_CONTEXT");
   assert(loc.path.startsWith("app/src/main/java/"),"No Android source path");
   assert(Number.isInteger(loc.line) && loc.line>0);
   const references=contextsByKey.get(row.key)?.sourceCallsites||[];
   assert(references.some(e=>e.path===loc.path&&e.line===loc.line),
     "Referenced callsite absent from generated G2 evidence "+row.key);
   const lines=readFileSync(resolve(root,loc.path),"utf8").split(/\r?\n/);
   assert(lines[loc.line-1]?.includes("R.string."+row.key),
     "Code at reference line has changed "+row.key+" "+loc.path+":"+loc.line);
   const changed=["zh-TW","ja"].some(locale=>row.originalDraft[locale]!==row.currentDraft[locale]);
   assert.equal(row.decision,changed?"REVISED_UNREVIEWED_DRAFT":"RETAIN_UNREVIEWED_DRAFT");
   if(changed)revisions++;
 }
 assert.equal(seen.size,21);
 assert.equal(revisions,9);
 const refuted=new Set();
 for(const r of review.refutedHeuristicLinks){
   assert(r.decision==="REFUTE_AUTOMATIC_CONCEPT_LINK");
   assert.equal(r.manualSemanticApproval,false);
   const t=terms.get(r.conceptId);
   assert(t && t.matchedKeys.includes(r.key),
    "Refuted link did not actually exist in source glossary "+r.conceptId+"/"+r.key);
   assert(sources.get(r.key)?.candidateConceptIds.includes(r.conceptId),
    "Review evidence must refer to actual LP0 heuristic");
   assert(r.reason.length>=12);
   assert(!contextsByKey.get(r.key)?.conceptIds.includes(r.conceptId),
     "Refuted LP0 concept must not leak into effective G2 binding");
   const id=r.conceptId+"/"+r.key;
   assert(!refuted.has(id),"Duplicate link rejection "+id);
   refuted.add(id);
 }
 assert.equal(refuted.size,9);
 assert.equal(glossary.concepts.find(c=>c.id==="music.object.ringtone").labels.ja,"アラーム音");
 assert.equal(review.additionalRisks.length,3);
 for(const item of review.additionalRisks){
   assert(sources.has(item.key));
   assert.equal(item.humanApproved,false);
 }
 return {concepts:seenConcept.size,modelReviewedKeys:seen.size,modelReviewedLocaleDrafts:42,
   draftKeysRevised:revisions,heuristicLinksRefuted:refuted.size,humanApprovals:0,
   staticEvidenceKeysVerified:seen.size};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 console.log("CONTENT_REVIEW_CHECK PASS "+JSON.stringify(verifyContentReview(inputs())));
