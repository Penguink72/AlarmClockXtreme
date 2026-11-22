#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {loadL11Inputs,buildL11,verifyDraft} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue} from "./l11-b3-source-dispositions.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
const inputs=()=>cached??=({...loadL11Inputs(ROOT,"L11-B8-SOURCE-FIXTURE"),batch9:[]});
test("L11-B8 reconciles 187 new source-locked keys and 374 locale drafts, preserving all old rows",()=>{
  const x=buildL11(inputs()),s=x.summary;
  assert.deepEqual(inputs().batch8.map(b=>b.records.length),[79,32,76]);
  assert.equal(s.batch8Keys,187);
  assert.equal(s.batch8LocaleRows,374);
  assert.deepEqual([s.curatedLocaleRows,s.batch2LocaleRows,s.batch3LocaleRows,s.batch4LocaleRows,
    s.batch5LocaleRows,s.batch6LocaleRows,s.batch7LocaleRows,s.batch8LocaleRows,
    s.reusedG6LocaleRows],[146,398,314,434,402,296,232,374,84]);
  assert.equal(x.queue.length,3100);
  assert.equal(x.review.length,2680);
  assert.equal(s.untranslatedLocaleRows,420);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.sourceDriftHolds,0);
  assert.equal(s.officialApproved,0);
  assert.equal(s.androidXmlModified,false);
});
test("L11-B8 every draft is bound to source, has correct format and doesn't overlap old locale XML or candidate",()=>{
  const x=inputs(),byKey=new Map(x.graph.sources.map(r=>[r.key,r]));
  const prev=new Set([
    ...x.curated.records.map(r=>r.key),
    ...[...x.batch2,...x.batch3,...x.batch4,...x.batch5,...x.batch6,...x.batch7]
      .flatMap(b=>b.records.map(r=>r.key)),
    ...x.catalog.entries.map(r=>r.key)
  ]);
  for(const b of x.batch8){
    assert.equal(b.schema,"acx-l11-batch8-drafts/1");
    assert.equal(b.independentApproval,false);
    assert.equal(b.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    for(const r of b.records){
      assert(!prev.has(r.key),"Duplicate "+r.key);
      prev.add(r.key);
      assert(verifyDraft(byKey.get("string:"+r.key),r));
    }
  }
});
test("L11-B8 high-impact fallback, shared alarm import, and alert translations remain unapproved",()=>{
  const r=buildL11(inputs()).review;
  for(const key of ["challenge_no_tag_registered_yet_any",
    "challenge_no_code_registered_yet_continue","challenge_no_reference_photo_registered_yet",
    "share_import_strip_private_references","share_import_save_without_enabling",
    "share_import_sanitizing_keeps_wake_time_repeat","health_exact_alarms_revoked",
    "world_remove_city_message"]){
    const rows=r.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B8_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>x.independentApproval===false&&x.humanApproval===false));
  }
});
test("L11-B8 residual disposition stays candidate only, with no false approved English fallback",()=>{
  const x=inputs(),d=buildL11(x),triage=buildDispositionQueue(d,x.graph);
  assert.equal(triage.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(triage.stats.candidateLocaleRows,2680);
  assert.equal(triage.stats.undecidedLocaleRows,420);
  assert.equal(triage.suggestions.length,420);
  assert(triage.suggestions.every(x=>x.candidateOnly&&x.humanReviewRequired));
  assert(triage.suggestions.every(x=>!x.officialTranslationApproved&&!x.officialFallbackApproved));
});
test("L11-B8 refuses source drift, wrong format, duplicate key or forged human approval",()=>{
  const x=inputs(),draft=x.batch8[0].records.find(r=>r.key==="challenge_typing_speed_instructions"),
    source=x.graph.sources.find(r=>r.key==="string:"+draft.key);
  assert(verifyDraft(source,draft));
  assert.throws(()=>verifyDraft({...source,sourcePreview:"changed"},draft),/source drift/);
  assert.throws(()=>verifyDraft(source,{...draft,translations:{...draft.translations,ja:"高速で入力"}}),/format tokens disagree/);
  assert.throws(()=>buildL11({...x,batch8:[{...x.batch8[0],independentApproval:true},...x.batch8.slice(1)]}),/Untrusted L11-B8/);
  assert.throws(()=>buildL11({...x,batch8:[x.batch8[0],
    {...x.batch8[1],records:[...x.batch8[1].records,x.batch8[0].records[0]]},x.batch8[2]]}),/Duplicate curated draft/);
});
test("L11-B8 L12 review consumes updated 2680 candidates and remains prohibited from release",()=>{
  const x=inputs(),l11=buildL11(x);
  const l12=buildL12({l11,graph:x.graph,matrix:generateMatrix(ROOT),
    glossary:JSON.parse(readFileSync(join(ROOT,"localization/governance/g1/glossary-contract.json"),"utf8"))});
  assert.equal(l12.stats.totalCandidates,2680);
  assert.equal(l12.stats.byLocale["zh-TW"],1340);
  assert.equal(l12.stats.byLocale.ja,1340);
  assert.equal(l12.stats.officialReleaseEligible,0);
  assert.equal(l12.stats.independentSemanticApproved,0);
});
