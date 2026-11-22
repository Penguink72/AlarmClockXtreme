#!/usr/bin/env node
/** L11-10: enumerate remaining locale gaps from the actual L10/L11 source contract.
 * This is a review input only; no semantic approval or production resource output.
 */
import assert from "node:assert/strict";
import {mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue} from "./l11-b3-source-dispositions.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
export function buildResidualClosure(l11,graph) {
  const triage=buildDispositionQueue(l11,graph);
  const records=triage.suggestions;
  const byKey=new Map();
  for(const row of records){
    assert.equal(row.officialFallbackApproved,false);
    assert.equal(row.officialTranslationApproved,false);
    if(!byKey.has(row.key))byKey.set(row.key,{key:row.key,english:row.sourcePreview,sourceHash:row.sourceHash,locales:[],sourceDisposition:row.disposition,formatTokens:[]});
    byKey.get(row.key).locales.push(row.locale);
  }
  const keys=[...byKey.values()].sort((a,b)=>a.key.localeCompare(b.key,"en"));
  const result={
    schema:"acx-l11-b10-residual-source-queue/1",sourceSha:l11.sourceSha,
    catalogueRevision:l11.catalogueRevision,
    status:"UNREVIEWED_SOURCE_PINNED_RESIDUAL_NOT_APPROVED",
    stats:{remainingLocaleRows:records.length,remainingKeys:keys.length,modelDraftRows:l11.review.length,
      totalMissingDedicatedDefinitions:l11.queue.length,authorizedFallbacks:0,independentApprovals:0},
    keys,rows:records,
    controls:["Do not use English unchanged as an official fallback without disposition approval.",
      "Do not overwrite app/src/main/res or the approved G1/G4 ledger.",
      "Source drift, translation format and Android compilation must be checked independently."]
  };
  assert.equal(result.stats.modelDraftRows+result.stats.remainingLocaleRows,l11.queue.length);
  return result;
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const inputs=loadL11Inputs(ROOT,sha),result=buildResidualClosure(buildL11(inputs),inputs.graph);
  const out=process.argv.indexOf("--out-dir");
  if(out>=0){
    const target=resolve(process.argv[out+1]??"");
    if(!target.startsWith(join(ROOT,"build")+"/"))throw Error("Output must remain under build/");
    mkdirSync(target,{recursive:true});
    writeFileSync(join(target,"l11-b10-residual-keys.json"),JSON.stringify(result,null,2)+"\n");
  }
  console.log("L11_B10_RESIDUAL "+JSON.stringify(result.stats));
  if(process.argv.includes("--list"))for(const x of result.keys)
    console.log("L11_B10_KEY "+JSON.stringify({key:x.key,english:x.english,locales:x.locales,disposition:x.sourceDisposition}));
}
