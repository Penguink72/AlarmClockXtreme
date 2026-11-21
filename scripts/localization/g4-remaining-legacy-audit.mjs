#!/usr/bin/env node
/* LP-G4 third model triage: covers unflagged existing translations and source defects. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname,resolve } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import { loadJson } from "./lp0-audit.mjs";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
export const inputs=()=>({
 matrix:loadJson("localization/governance/lp0/source-matrix.json"),
 high:loadJson("localization/governance/g4/legacy-high-priority-triage.json"),
 remaining:loadJson("localization/governance/g4/legacy-remaining-triage.json"),
 context:loadJson("localization/governance/g2/context-evidence.json"),
 unresolved:loadJson("localization/governance/g2/unresolved-static-refs.json"),
 sourceDefects:loadJson("localization/governance/g4/source-defects.json"),
 zh:loadJson("experiments/lp0-200/packs/zh-TW.json"),
 ja:loadJson("experiments/lp0-200/packs/ja.json"),
 reviewLedger:loadJson("localization/governance/g4/review-ledger.json")
});
export function verifyRemaining({matrix,high,remaining,context,unresolved,sourceDefects,zh,ja,reviewLedger}){
 assert.equal(remaining.schema,"acx-g4-legacy-remaining-triage/1");
 assert.equal(remaining.catalogRevision,matrix.baselineRevision);
 assert.equal(remaining.reviewAuthority,"MODEL_PRELIMINARY_ONLY");
 assert.equal(remaining.humanApproved,false);
 assert.equal(remaining.legacyResourceChanges,0);
 assert.equal(reviewLedger.approvals.length,0);
 const highKeys=new Set(high.records.map(x=>x.key));
 assert.equal(highKeys.size,52);
 const remainingBaseline=matrix.records.filter(x=>x.translations["zh-TW"].text!==null&&!highKeys.has(x.key));
 assert.equal(remainingBaseline.length,113);
 assert.equal(remaining.records.length,113);
 const sourceMap=new Map(remainingBaseline.map(x=>[x.key,x]));
 const ctxMap=new Map(context.records.map(x=>[x.key,x]));
 const seen=new Set();
 let recommended=0,recommendedLocales=0,noStatic=0,contextWarnings=0,defects=0;
 for(const row of remaining.records){
   const src=sourceMap.get(row.key);
   assert(src,"Missing or out-of-scope legacy Key "+row.key);
   assert(!seen.has(row.key),"Duplicate existing-translation review "+row.key);
   seen.add(row.key);
   assert.equal(row.source,src.source);
   assert.equal(row.sourceHash,src.sourceHash);
   assert.equal(row.humanApproved,false);
   assert.equal(row.reviewType,"MODEL_SURFACE_CHECK_NOT_INDEPENDENT_APPROVAL");
   assert(row.reason.length>15);
   for(const [locale,pack]of [["zh-TW",zh],["ja",ja]]){
     assert.equal(row.existing[locale],src.translations[locale].text);
     assert.equal(row.existing[locale],pack.translations[row.key],"Legacy resource drift "+row.key);
   }
   if(Object.keys(row.suggestions).length){
     recommended++;
     assert.equal(row.status,"REVISION_SUGGESTED_SOURCE_UNCHANGED");
     const loc=row.sourceEvidence;
     assert(loc && loc.kind==="STATIC_ANDROID_CALLSITE");
     const ref=ctxMap.get(row.key)?.sourceCallsites||[];
     const lines=readFileSync(resolve(root,loc.path),"utf8").split(/\r?\n/);
     assert(ref.some(c=>c.path===loc.path&&Math.abs(c.line-loc.line)<=40 &&
       lines[c.line-1]?.includes("R.string."+row.key)),
       "Review evidence absent or displaced from source index "+row.key);
     for(const [locale,proposal]of Object.entries(row.suggestions)){
       assert(["zh-TW","ja"].includes(locale));
       assert(proposal.trim()&&proposal!==row.existing[locale]);
       assert.deepEqual(core.placeholders(proposal),src.placeholders,
         "Proposed translation changed printf tokens "+row.key);
       recommendedLocales++;
     }
   }else assert.equal(row.sourceEvidence,null);
   if(row.status==="NO_DIRECT_STATIC_REFERENCE"){
     noStatic++;
     assert.equal(ctxMap.get(row.key)?.sourceCallsites.length,0);
   }
   if(row.status==="CONTEXT_FOLLOWUP")contextWarnings++;
   if(row.status==="STALE_ENGLISH_SOURCE_BLOCKS_APPROVAL")defects++;
 }
 assert.equal(seen.size,113);
 assert.deepEqual([recommended,recommendedLocales,noStatic,contextWarnings,defects],[6,10,4,3,1]);
 assert.equal(remaining.stats.total,113);
 assert.equal(remaining.stats.suggestedKeys,recommended);
 assert.equal(remaining.stats.suggestedLocaleTranslations,recommendedLocales);
 assert.equal(remaining.stats.staleSources,defects);
 assert.equal(remaining.stats.noStaticInThisBatch,noStatic);
 assert.equal(remaining.stats.contextWarnings,contextWarnings);
 assert.equal(remaining.stats.humanApprovals,0);
 assert.equal(unresolved.schema,"acx-g2-unresolved-static-references/1");
 assert.equal(unresolved.items.length,5);
 const noRef=context.records.filter(x=>!x.sourceCallsites.length).map(x=>x.key).sort();
 assert.deepEqual(unresolved.items.map(x=>x.key).sort(),noRef);
 for(const item of unresolved.items){
   assert(item.nearby.path.startsWith("app/src/main/"));
   const lines=readFileSync(resolve(root,item.nearby.path),"utf8").split(/\r?\n/);
   // Require an exact, unambiguous token within the same recorded file;
   // absolute line drift is expected when folder/history UI is inserted.
   const matching=lines.filter(line=>line.includes(item.nearby.expectedSnippet));
   assert.equal(matching.length,1,
     "Nearby candidate source missing or ambiguous "+item.key);
 }
 assert.equal(sourceDefects.schema,"acx-g4-source-defects/1");
 assert.equal(sourceDefects.items.length,1);
 const defect=sourceDefects.items[0];
 assert.equal(defect.key,"music_playlist_integration_pending");
 assert.equal(defect.status,"OPEN","Release-blocking source defect cannot self-resolve");
 assert.equal(defect.releaseBlocking,true);
 assert.equal(defect.humanApproved,false);
 assert.equal(defect.sourceHash,sourceMap.get(defect.key)?.sourceHash);
 assert.equal(defect.source,sourceMap.get(defect.key)?.source);
 for(const evidence of defect.evidence){
   const lines=readFileSync(resolve(root,evidence.path),"utf8").split(/\r?\n/);
   const start=Math.max(0,evidence.line-41),end=Math.min(lines.length,evidence.line+40);
   assert(lines.slice(start,end).some(line=>line.includes(evidence.expected)),
     "Outdated product-source evidence changed or displaced");
 }
 return {existingModelSurfaceScreened:113,existingHighPriorityFlagged:52,
   sourceDefectsOpen:1,unresolvedDirectReferences:5,legacySuggestedKeys:6,
   legacySuggestedLocaleTranslations:10,humanApprovals:0};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 console.log("G4_REMAINING_LEGACY_AUDIT PASS "+JSON.stringify(verifyRemaining(inputs())));
