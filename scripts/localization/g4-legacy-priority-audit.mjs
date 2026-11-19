#!/usr/bin/env node
/* G4 legacy high-priority model triage: evidence fidelity, NOT final approval. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve,dirname } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import { loadJson } from "./lp0-audit.mjs";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
export const inputs=()=>({
 report:loadJson("localization/governance/g4/legacy-high-priority-triage.json"),
 matrix:loadJson("localization/governance/lp0/source-matrix.json"),
 contexts:loadJson("localization/governance/g2/context-evidence.json"),
 zh:loadJson("experiments/lp0-200/packs/zh-TW.json"),
 ja:loadJson("experiments/lp0-200/packs/ja.json"),
 ledger:loadJson("localization/governance/g4/review-ledger.json")
});
export function verifyLegacyReview({report,matrix,contexts,zh,ja,ledger}){
 assert.equal(report.schema,"acx-g4-legacy-high-priority-triage/1");
 assert.equal(report.catalogRevision,matrix.baselineRevision);
 assert.equal(report.baseTranslationPolicy,"DO_NOT_OVERWRITE_LEGACY_PACKS");
 assert.equal(report.humanApproved,false);
 assert.equal(report.legacyTranslationsModified,false);
 assert.equal(report.scope.existingFullyApproved,0);
 assert.equal(ledger.approvals.length,0);
 const expected=matrix.records.filter(r=>r.reviewPriority==="HIGH_REVIEW_PRIORITY");
 const byKey=new Map(expected.map(r=>[r.key,r]));
 const byContext=new Map(contexts.records.map(r=>[r.key,r]));
 assert.equal(expected.length,52);
 assert.equal(report.records.length,52);
 const seen=new Set();
 let suggestions=0,missing=0,warnings=0;
 for(const row of report.records){
  const original=byKey.get(row.key);
  assert(original,"Unexpected or nonpriority key "+row.key);
  assert(!seen.has(row.key),"Duplicate review "+row.key);seen.add(row.key);
  assert.equal(row.source,original.source);
  assert.equal(row.sourceHash,original.sourceHash);
  assert.equal(row.humanApproved,false);
  assert.equal(row.auditType,"MODEL_ONLY_PRELIMINARY_TRIAGE");
  for(const [locale,pack]of [["zh-TW",zh],["ja",ja]]){
   const current=original.translations[locale].text;
   assert.equal(row.existing[locale],current,"Evidence record diverged from LP0 "+row.key);
   assert.equal(current,pack.translations[row.key]??null,
      "Legacy translation changed after review "+locale+"/"+row.key);
  }
  assert(row.note.length>=12,"Review rationale too short "+row.key);
  if(row.suggestedReplacement){
   suggestions++;
   assert.equal(row.outcome,"REVISION_SUGGESTED_LEGACY_UNCHANGED");
   const locale=row.suggestedReplacement.locale;
   assert(["zh-TW","ja"].includes(locale));
   assert(row.existing[locale]!==null);
   assert(row.suggestedReplacement.text.trim() && row.suggestedReplacement.text!==row.existing[locale]);
   assert.deepEqual(core.placeholders(row.suggestedReplacement.text),original.placeholders,
      "Suggested translation changes printf arguments "+row.key);
   const ref=row.evidence;
   assert(ref && ref.kind==="EXACT_STATIC_CODE_CALLSITE");
   assert(ref.path.startsWith("app/src/main/java/") && Number.isInteger(ref.line)&&ref.line>0);
   const ctx=byContext.get(row.key);
   assert(ctx.sourceCallsites.some(s=>s.path===ref.path&&s.line===ref.line),
     "Proposed correction evidence not found in G2 "+row.key);
   const at=readFileSync(resolve(root,ref.path),"utf8").split(/\r?\n/)[ref.line-1];
   assert(at?.includes("R.string."+row.key),"Actual code no longer matches "+row.key);
  }else{
   assert(row.evidence===null,"Unexpected code reference on unsuggested row "+row.key);
  }
  if(row.outcome==="MISSING_NO_CONTENT_TO_REVIEW"){
    missing++; assert.equal(row.existing["zh-TW"],null);assert.equal(row.existing.ja,null);
  }
  if(row.outcome==="CONTEXT_OR_SEMANTIC_RECHECK_REQUIRED")warnings++;
 }
 assert.equal(seen.size,52);
 assert.equal(suggestions,6);
 assert.equal(warnings,9);
 assert.equal(missing,8);
 assert.equal(report.scope.flaggedKeys,52);
 assert.equal(report.scope.proposedRevisions,suggestions);
 assert.equal(report.scope.contextWarnings,warnings);
 assert.equal(report.scope.missingBoth,missing);
 return {flagged:seen.size,suggestions,missing,warnings,approved:0,legacyChanged:false};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 console.log("G4_LEGACY_TRIAGE PASS "+JSON.stringify(verifyLegacyReview(inputs())));
}
