#!/usr/bin/env node
/* G4: deterministic mechanical QA, enforce review provenance; never model-self-approve. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import { loadJson } from "./lp0-audit.mjs";
const { placeholders } = core;
const root=resolve(dirname(fileURLToPath(import.meta.url)), "../..");
export const digest = text => "sha256:"+createHash("sha256").update(text,"utf8").digest("hex");
export const evidenceHash = evidence => digest(JSON.stringify({
 key:evidence.key,sourceHash:evidence.sourceHash,sourceCallsites:evidence.sourceCallsites
}));

export function checkReviewQueue(queue,glossary,contexts,ledger){
 assert.equal(queue.schema,"acx-g3-review-queue/1");
 assert.equal(queue.approval,"NONE");
 assert.equal(glossary.schema,"acx-glossary-contract/1");
 assert.equal(contexts.schema,"acx-g2-context-evidence/1");
 assert.equal(ledger.schema,"acx-g4-review-ledger/1");
 for(const data of [queue,contexts,ledger]){
   assert.equal(data.catalogRevision,glossary.catalogRevision);
 }
 assert.equal(queue.glossaryRevision,glossary.glossaryRevision);
 assert.equal(ledger.glossaryRevision,glossary.glossaryRevision);
 assert.equal(contexts.records.length,200);
 const contextsByKey=new Map(contexts.records.map(r=>[r.key,r]));
 assert.equal(contextsByKey.size,200);
 const glossaryById=new Map(glossary.concepts.map(t=>[t.id,t]));
 const permitted=new Set(["zh-TW","ja"]);
 const candidates=new Map();
 const candidateList=[];
 for(const [locale,items]of Object.entries(queue.locales)){
   assert(permitted.has(locale),"Unexpected locale "+locale);
   assert.equal(items.length,200);
   const seen=new Set();
   for(const item of items){
     assert.equal(item.locale,locale);
     assert(!seen.has(item.key),"Duplicate localized key "+locale+" "+item.key);
     seen.add(item.key);
     const proof=contextsByKey.get(item.key);
     assert(proof,"Unknown key "+item.key);
     assert.equal(item.source,proof.source);
     assert.equal(item.sourceHash,proof.sourceHash);
     assert.equal(item.glossaryRevision,glossary.glossaryRevision);
     assert.equal(item.contextEvidenceStatus,proof.callsiteStatus);
     assert.deepEqual(item.conceptIds,proof.conceptIds);
     for(const concept of item.conceptIds)assert(glossaryById.has(concept),"Unknown concept "+concept);
     assert.equal(item.humanApproval,null,"Model output cannot self-approve");
     if(item.text!==null){
       assert.equal(typeof item.text,"string");
       assert(item.text.trim(),"Empty candidate must be MISSING");
       assert.deepEqual(placeholders(item.text),item.placeholders,
          "Missing or changed printf placeholder "+locale+"/"+item.key);
       assert.equal(item.status,"REVIEW_REQUIRED");
     }else {
       assert.equal(item.status,"MISSING");
       assert.equal(item.origin,"MISSING");
     }
     const candidateKey=locale+"\u0000"+item.key;
     candidates.set(candidateKey,item);
     candidateList.push(item);
   }
 }
 assert.equal(candidateList.length,400);
 const approved=new Map();
 for(const [i,approval] of ledger.approvals.entries()){
   const id=approval.locale+"\u0000"+approval.key;
   const item=candidates.get(id);
   assert(item && item.text!==null,"Approval references missing candidate "+id);
   assert(!approved.has(id),"Duplicate approval "+id);
   assert.equal(approval.decision,"APPROVED");
   assert.equal(approval.reviewMode,"human-independent-semantic-review");
   assert.equal(approval.sourceHash,item.sourceHash,"Stale approval source "+id);
   assert.equal(approval.glossaryRevision,glossary.glossaryRevision,"Stale term version "+id);
   assert.equal(approval.translationDigest,digest(item.text),"Stale translation approval "+id);
   assert.equal(approval.contextEvidenceDigest,evidenceHash(contextsByKey.get(item.key)),
     "Changed or forged context evidence "+id);
   assert(typeof approval.reviewerId==="string" && approval.reviewerId.trim().length>=3,"Missing human reviewer");
   assert(approval.reviewNotes && approval.reviewNotes.trim().length>=16,"Insufficient review rationale");
   assert(!Number.isNaN(Date.parse(approval.reviewedAt)),"Invalid review date");
   assert(approval.semanticMeaningChecked===true,"Meaning must be reviewed");
   assert(approval.actionDirectionChecked===true,"Action consequences must be reviewed");
   assert(approval.contextVerified===true,"Source callsites alone do not establish context");
   const used=contextsByKey.get(item.key).sourceCallsites;
   assert(used.length>0 || (approval.manualContextEvidence && approval.manualContextEvidence.trim().length>20),
     "No source callsite or additional verified context evidence");
   assert.equal(glossary.approval,"APPROVED_FOR_RELEASE","Glossary not yet approved");
   for(const conceptId of item.conceptIds){
     assert.equal(glossaryById.get(conceptId).labelState,"APPROVED","Term not approved "+conceptId);
   }
   approved.set(id,{...item,review:approval});
 }
 for(const r of ledger.rejections){
   assert(candidates.has(r.locale+"\u0000"+r.key),"Unknown rejected key");
   assert(!approved.has(r.locale+"\u0000"+r.key),"Same key cannot be approved and rejected");
 }
 const rows=candidateList.map(item=>{
   const id=item.locale+"\u0000"+item.key;
   const issues=[];
   if(item.text===null)issues.push("MISSING");
   else {
     if(item.origin==="LEGACY_UNREVIEWED")issues.push("LEGACY_WITHOUT_SEMANTIC_APPROVAL");
     if(item.origin==="LLM_DRAFT_UNREVIEWED")issues.push("MODEL_TRANSLATION_WITHOUT_SEMANTIC_APPROVAL");
     if(!approved.has(id))issues.push("INDEPENDENT_REVIEW_REQUIRED");
   }
   const context=contextsByKey.get(item.key);
   if(context.semanticReview!=="HUMAN_VERIFIED")issues.push("CONTEXT_MEANING_NOT_VERIFIED");
   if(!context.sourceCallsites.length)issues.push("NO_STATIC_SOURCE_CALLSITE");
   if(glossary.approval!=="APPROVED_FOR_RELEASE")issues.push("GLOSSARY_CANDIDATES_NOT_APPROVED");
   return {key:item.key,locale:item.locale,sourceHash:item.sourceHash,
     translationPresent:item.text!==null,structurePass:true,approved:approved.has(id),issues};
 });
 const stats=Object.fromEntries(["zh-TW","ja"].map(locale=>{
   const group=rows.filter(x=>x.locale===locale);
   return [locale,{total:group.length,structured:group.filter(x=>x.structurePass).length,
    present:group.filter(x=>x.translationPresent).length,missing:group.filter(x=>!x.translationPresent).length,
    approved:group.filter(x=>x.approved).length,reviewRequired:group.filter(x=>x.translationPresent&&!x.approved).length}];
 }));
 return {schema:"acx-g4-qa-report/1",catalogRevision:queue.catalogRevision,
   glossaryRevision:glossary.glossaryRevision,releaseAllowed:approved.size>0&&glossary.approval==="APPROVED_FOR_RELEASE",
   stats,approvals:[...approved.values()],rows};
}
const queue=loadJson("localization/governance/g3/review-queue.generated.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const contexts=loadJson("localization/governance/g2/context-evidence.json");
const ledger=loadJson("localization/governance/g4/review-ledger.json");
const report=checkReviewQueue(queue,glossary,contexts,ledger);
const idx=process.argv.indexOf("--out");
if(idx>=0){
 const target=resolve(process.argv[idx+1]||"");
 assert(target.startsWith(root+(process.platform==="win32"?"\\":"/")),"Output outside repository");
 mkdirSync(dirname(target),{recursive:true});
 writeFileSync(target,JSON.stringify(report,null,2)+"\n");
}
console.log("G4_MECHANICAL_QA PASS "+JSON.stringify(report.stats)+" RELEASE_ALLOWED="+report.releaseAllowed);
