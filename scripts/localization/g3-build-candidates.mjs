#!/usr/bin/env node
/* G3: build 200-key review queue, preserve key identity, never alter original packs. */
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";
import { loadJson } from "./lp0-audit.mjs";
import { placeholders } from "../../experiments/lp0-200/web/probe-core.js";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const source=loadJson("localization/governance/lp0/source-matrix.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const seeds=loadJson("localization/governance/g3/draft-proposals.json");
const contexts=loadJson("localization/governance/g2/context-evidence.json");

export function buildCandidates(source,glossary,seeds,contexts){
 assert.equal(source.schema,"acx-lp0-source-matrix/1");
 assert.equal(glossary.schema,"acx-glossary-contract/1");
 assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
 assert.equal(seeds.schema,"acx-g3-proposals/1");
 assert.equal(glossary.catalogRevision,source.baselineRevision);
 assert.equal(seeds.catalogRevision,source.baselineRevision);
 assert.equal(seeds.glossaryRevision,glossary.glossaryRevision);
 assert.equal(contexts.catalogRevision,source.baselineRevision);
 const byKey=new Map(source.records.map(r=>[r.key,r]));
 assert.equal(byKey.size,200);
 const contextsByKey=new Map(contexts.records.map(r=>[r.key,r]));
 assert.equal(contextsByKey.size,200);
 const prepared=new Map();
 for(const entry of seeds.translations){
  assert(byKey.has(entry.key),"LLM invented key "+entry.key);
  assert(!prepared.has(entry.key),"Duplicate proposed key "+entry.key);
  assert.equal(entry.reviewState,"DRAFT_LLM_UNREVIEWED");
  assert.equal(entry.provenance,"model-generated-candidate");
  prepared.set(entry.key,entry);
  const sourceEntry=byKey.get(entry.key);
  for(const locale of ["zh-TW","ja"]){
   assert.equal(sourceEntry.translations[locale].state,"MISSING",
    "Do not silently overwrite existing or reviewed translations: "+locale+" "+entry.key);
   assert.equal(typeof entry[locale],"string");
   assert(entry[locale].trim(),"Empty model proposal "+entry.key);
   assert.deepEqual(placeholders(entry[locale]),sourceEntry.placeholders,
    "Model proposal broke placeholder "+locale+" "+entry.key);
  }
 }
 const result={"zh-TW":[],"ja":[]};
 for(const r of source.records){
  const context=contextsByKey.get(r.key);
  assert(context,"Missing G2 evidence "+r.key);
  assert.equal(context.sourceHash,r.sourceHash);
  for(const locale of ["zh-TW","ja"]){
   const legacy=r.translations[locale];
   const seed=prepared.get(r.key);
   const text=legacy.text ?? seed?.[locale] ?? null;
   const origin=legacy.text!==null?"LEGACY_UNREVIEWED":(seed?"LLM_DRAFT_UNREVIEWED":"MISSING");
   const status=text===null?"MISSING":"REVIEW_REQUIRED";
   result[locale].push({key:r.key,locale,source:r.source,sourceHash:r.sourceHash,
    text,origin,status,sourceSection:r.section,
    glossaryRevision:glossary.glossaryRevision,contextEvidenceStatus:context.callsiteStatus,
    contextSemanticStatus:context.semanticReview,
    conceptIds:r.candidateConceptIds,
    placeholders:r.placeholders,
    humanApproval:null});
  }
 }
 const stats=Object.fromEntries(Object.entries(result).map(([locale,rows])=>[locale,{
  total:rows.length,legacy:rows.filter(x=>x.origin==="LEGACY_UNREVIEWED").length,
  llmDrafts:rows.filter(x=>x.origin==="LLM_DRAFT_UNREVIEWED").length,
  missing:rows.filter(x=>x.origin==="MISSING").length,approved:0
 }]));
 return {schema:"acx-g3-review-queue/1",catalogRevision:source.baselineRevision,
  glossaryRevision:glossary.glossaryRevision,producer:seeds.producer,
  approval:"NONE",stats,locales:result};
}
const queue=buildCandidates(source,glossary,seeds,contexts);
const outArg=process.argv.indexOf("--out");
if(outArg>=0){
 const file=resolve(process.argv[outArg+1]||"");
 assert(file.startsWith(root+(process.platform==="win32"?"\\":"/")),"Output must stay in the repository");
 mkdirSync(dirname(file),{recursive:true});
 writeFileSync(file,JSON.stringify(queue,null,2)+"\n");
}
console.log("G3_CANDIDATE_QUEUE PASS "+JSON.stringify(queue.stats));
