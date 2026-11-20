#!/usr/bin/env node
/* G5 pack generator: clearly labeled unreviewed preview or strict approved-only release. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { loadJson } from "./lp0-audit.mjs";
import { checkReviewQueue } from "./g4-review-gate.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)), "../..");
export const jsonHash = value => "sha256:"+createHash("sha256").update(JSON.stringify(value),"utf8").digest("hex");

export function buildPacks(mode,catalog,glossary,queue,qa){
 assert(["preview","release"].includes(mode),"Allowed modes are preview and release");
 assert.equal(catalog.revision,queue.catalogRevision);
 assert.equal(queue.glossaryRevision,glossary.glossaryRevision);
 assert.equal(qa.glossaryRevision,glossary.glossaryRevision);
 assert.equal(qa.catalogRevision,catalog.revision);
 const approved=new Map(qa.approvals.map(row=>[row.locale+"\u0000"+row.key,row]));
 const production=mode==="release";
 if(production){
   assert.equal(glossary.approval,"APPROVED_FOR_RELEASE","Release blocked: glossary is not independently approved");
   assert.equal(qa.releaseAllowed,true,"Release blocked: no independently approved translations");
   assert.equal(approved.size,catalog.entries.length*2,
     "Release blocked: every 200-key sample translation must be independently approved in both locales");
   for(const locale of ["zh-TW","ja"]){
     assert.equal(qa.stats[locale]?.approved,catalog.entries.length,
       "Release blocked: locale is not fully reviewed: "+locale);
   }
 }else{
   assert.equal(queue.approval,"NONE","Preview must remain unapproved");
 }
 const packs={};
 const totals={};
 const allowedKeys=new Set(catalog.entries.map(e=>e.key));
 assert.equal(allowedKeys.size,200);
 for(const locale of ["zh-TW","ja"]){
   const values={};
   const group=queue.locales[locale];
   assert(group && group.length===200,"Incomplete candidate queue: "+locale);
   const seen=new Set();
   let copiedLegacy=0,copiedModelDrafts=0,copiedApproved=0;
   for(const candidate of group){
     assert(!seen.has(candidate.key),"Duplicate "+candidate.key);
     seen.add(candidate.key);
     assert(allowedKeys.has(candidate.key),"Invented "+candidate.key);
     assert(candidate.sourceHash===qa.rows.find(r=>r.key===candidate.key&&r.locale===locale)?.sourceHash,
       "Candidate / QA source mismatch: "+candidate.key);
     if(candidate.text===null)continue;
     const gate=approved.get(locale+"\u0000"+candidate.key);
     if(production){
       if(gate){
         assert.equal(gate.text,candidate.text,"Approval does not match payload");
         assert.equal(gate.sourceHash,candidate.sourceHash,"Approval source hash changed");
         assert.equal(gate.review?.sourceHash,candidate.sourceHash,"Reviewer approved a stale source");
         assert.equal(gate.review?.glossaryRevision,glossary.glossaryRevision,"Reviewer approved a stale glossary");
         assert.equal(gate.review?.translationDigest,
           "sha256:"+createHash("sha256").update(candidate.text,"utf8").digest("hex"),
           "Translation changed after review");
         values[candidate.key]=candidate.text;
         copiedApproved++;
       }
     }else{
       values[candidate.key]=candidate.text;
       if(candidate.origin==="LEGACY_UNREVIEWED")copiedLegacy++;
       if(candidate.origin==="LLM_DRAFT_UNREVIEWED")copiedModelDrafts++;
     }
   }
   assert.equal(seen.size,200);
   packs[locale]={
     format:"acx-language-pack-probe/1",
     locale,
     catalogRevision:catalog.revision,
     packVersion:production?"g5-approved-only":"g5-UNREVIEWED-PREVIEW",
     translations:values
   };
   totals[locale]={present:Object.keys(values).length,
     missing:200-Object.keys(values).length,
     approved:copiedApproved,
     unreviewedLegacy:copiedLegacy,
     unreviewedModelDrafts:copiedModelDrafts};
   if(production){
     assert.equal(Object.keys(values).length,catalog.entries.length,
       "Release blocked: incomplete locale pack would require English fallback: "+locale);
     assert.equal(copiedApproved,catalog.entries.length,
       "Release blocked: only fully approved locale packs may be published");
   }
 }
 const manifest={
   schema:"acx-g5-pack-manifest/1",
   releaseChannel:production?"approved-only":"UNREVIEWED_PREVIEW_NOT_RELEASE",
   mayDistributeAsOfficial:production,
   catalogRevision:catalog.revision,glossaryRevision:glossary.glossaryRevision,
   sourceCommit:catalog.baseCommit,
   // Digest pins the reviewed source, draft queue and actual QA report.
   provenance:{catalogDigest:jsonHash(catalog),glossaryDigest:jsonHash(glossary),
     queueDigest:jsonHash(queue),qaDigest:jsonHash(qa)},
   reviewStatus:production?"approved-only":"no-human-approval",
   totals
 };
 return {manifest,packs};
}
const catalog=loadJson("experiments/lp0-200/catalog.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const queue=loadJson("localization/governance/g3/review-queue.generated.json");
const qa=loadJson("localization/governance/g4/qa-report.generated.json");
const modeIndex=process.argv.indexOf("--mode");
const mode=modeIndex>=0?process.argv[modeIndex+1]:"preview";
// Verify the generated QA report against the actual review ledger again at
// the final CLI boundary: a manually edited qa-report.json cannot unlock release.
const reviewEvidence=loadJson("localization/governance/g2/context-evidence.json");
const reviewerLedger=loadJson("localization/governance/g4/review-ledger.json");
const verifiedQa=checkReviewQueue(queue,glossary,reviewEvidence,reviewerLedger);
assert.deepEqual(qa,verifiedQa,"G5 blocked: QA artifact must match the independent review ledger");
const result=buildPacks(mode,catalog,glossary,queue,verifiedQa);
const outIdx=process.argv.indexOf("--out-dir");
if(outIdx>=0){
 const dir=resolve(process.argv[outIdx+1]||"");
 assert(dir.startsWith(root+(process.platform==="win32"?"\\":"/")),"Output outside repository");
 mkdirSync(dir,{recursive:true});
 writeFileSync(join(dir,"manifest.json"),JSON.stringify(result.manifest,null,2)+"\n");
 for(const [locale,pack]of Object.entries(result.packs))
   writeFileSync(join(dir,locale+".json"),JSON.stringify(pack,null,2)+"\n");
 writeFileSync(join(dir,"README.txt"),mode==="preview"?
   "UNREVIEWED PREVIEW - NOT AN OFFICIAL LANGUAGE PACK.\nNo semantic human approvals have been granted.\nUse only in LP0/LP1 debug experiments.\n":
   "APPROVED-ONLY RELEASE - See manifest and reviewer ledger.\n");
}
console.log("G5_PACK_BUILDER "+mode.toUpperCase()+" "+JSON.stringify(result.manifest.totals));
