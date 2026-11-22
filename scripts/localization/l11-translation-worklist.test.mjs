#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { buildL10 } from "./l10-source-binding-graph.mjs";
import { buildL11,verifyDraft,draftTsv,summaryMd } from "./l11-translation-worklist.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8"));
const fixtures=()=>({
  graph:buildL10(ROOT,"l11-test-sha"),
  catalog:read("app/src/debug/assets/lp2/catalog.json"),
  packs:{"zh-TW":read("app/src/debug/assets/lp2/packs/zh-TW.json"),
    ja:read("app/src/debug/assets/lp2/packs/ja.json")},
  curated:read("localization/candidates/l11-priority-unreviewed-drafts-20261122.json")
});
let cached;
function getFixtures(){return cached??=fixtures();}

test("L11 full real catalog keeps all missing locale rows and existing translations untouched",()=>{
  const f=getFixtures(),d=buildL11(f);
  assert.equal(d.summary.baseKeys,2471);
  assert.equal(d.summary.translatableKeys,2462);
  assert.equal(d.summary.missingByLocale["zh-TW"],1550);
  assert.equal(d.summary.missingByLocale.ja,1550);
  assert.equal(d.queue.length,3100);
  assert.equal(d.summary.curatedKeys,f.curated.records.length);
  assert.equal(d.summary.curatedLocaleRows,2*f.curated.records.length);
  assert.equal(d.summary.existingLocaleProtected,1824);
  assert(d.summary.reusedG6LocaleRows>=0);
  assert.equal(d.summary.officialApproved,0);
  assert(d.queue.every(x=>x.reviewStatus==="UNREVIEWED"&&x.humanApproval===false));
  assert(d.queue.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  assert(d.queue.every(x=>x.targetExists===false));
  assert(d.queue.every(x=>!x.translation||x.translation.trim().length>0));
  assert.equal(d.review.length,
    d.summary.curatedLocaleRows+d.summary.reusedG6LocaleRows);
  assert.match(summaryMd(d),/L11/);
  assert.match(draftTsv(d),/source_hash/);
});

test("L11 curations refuse changed source, Android XML overwrite and format drift",()=>{
  const f=getFixtures();
  const first=f.curated.records[0],source=f.graph.sources.find(x=>x.key==="string:"+first.key);
  assert(source&&verifyDraft(source,first));
  assert.throws(()=>verifyDraft({...source,sourcePreview:"Changed English"},first),
    /source drift/);
  assert.throws(()=>verifyDraft({...source,locales:{
    ...source.locales,"zh-TW":{...source.locales["zh-TW"],hasDedicatedDefinition:true}
  }},first),/overwrite existing Android locale/);
  const formatted=f.curated.records.find(x=>x.key==="onboarding_step_of");
  const row=f.graph.sources.find(x=>x.key==="string:"+formatted.key);
  assert(row&&verifyDraft(row,formatted));
  assert.throws(()=>verifyDraft(row,{...formatted,translations:{
    ...formatted.translations,"zh-TW":"第 %1$s 步"
  }}),/format tokens disagree/);
});

test("L11 cannot infer approval from G6 candidates or model-produced translations",()=>{
  const f=getFixtures();
  assert.throws(()=>buildL11({...f,curated:{...f.curated,independentApproval:true}}),
    /Untrusted curated draft schema/);
  const d=buildL11(f);
  assert(d.review.every(x=>x.independentApproval===false));
  assert(d.review.every(x=>x.translationOrigin.includes("UNREVIEWED")));
  assert(d.queue.filter(x=>x.candidateState==="SOURCE_DRIFT_HOLD")
    .every(x=>x.translation===null));
});

test("L11 source-pinned key identity and duplicate protection reject false translation progress",()=>{
  const f=getFixtures();
  const duplicate={...f.curated,records:[...f.curated.records,f.curated.records[0]]};
  assert.throws(()=>buildL11({...f,curated:duplicate}),/Duplicate curated draft/);
  const missing={...f.curated,records:[...f.curated.records,
    {key:"not_a_real_key",expectedEnglishPreview:"Fake",
      translations:{"zh-TW":"假","ja":"偽"}}]};
  assert.throws(()=>buildL11({...f,curated:missing}),/Curated source missing/);
});

test("L11 priority worklist includes onboarding and integration locale draft rows",()=>{
  const d=buildL11(getFixtures());
  for(const key of ["onboarding_welcome","settings_webhook_blocked",
    "settings_wake_readiness","settings_connections_data"]){
    const rows=d.review.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_MODEL_DRAFT_UNREVIEWED"));
  }
  assert(d.queue.some(x=>x.priority==="P2_LONG_TAIL"));
  assert(d.queue.every(x=>x.sourceHash.startsWith("sha256:")));
});
