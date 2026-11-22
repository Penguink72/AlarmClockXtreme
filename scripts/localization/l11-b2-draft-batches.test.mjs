#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL10} from "./l10-source-binding-graph.mjs";
import {buildL11,verifyDraft,draftTsv,summaryMd} from "./l11-translation-worklist.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8"));
let cache;
function inputs(){
  return cache??={
    graph:buildL10(ROOT,"L11-B2-EXACT-TEST-BASE"),
    catalog:read("app/src/debug/assets/lp2/catalog.json"),
    packs:{
      "zh-TW":read("app/src/debug/assets/lp2/packs/zh-TW.json"),
      ja:read("app/src/debug/assets/lp2/packs/ja.json")
    },
    curated:read("localization/candidates/l11-priority-unreviewed-drafts-20261122.json"),
    batch2:[
      read("localization/candidates/l11-b2-settings-drafts-20261122.json"),
      read("localization/candidates/l11-b2-alarm-notification-drafts-20261122.json")
    ]
  };
}

test("L11-B2 full baseline retains complete missing rows, no Android locale writes",()=>{
  const d=buildL11(inputs());
  assert.equal(d.summary.baseKeys,2471);
  assert.equal(d.summary.translatableKeys,2462);
  assert.equal(d.summary.missingByLocale["zh-TW"],1550);
  assert.equal(d.summary.missingByLocale.ja,1550);
  assert.equal(d.queue.length,3100);
  assert.equal(d.summary.curatedKeys,73);
  assert.equal(d.summary.curatedLocaleRows,146);
  assert.equal(d.summary.batch2Keys,199);
  assert.equal(d.summary.batch2LocaleRows,398);
  assert.equal(d.summary.reusedG6LocaleRows,84);
  assert.equal(d.summary.existingLocaleProtected,1824);
  assert.equal(d.summary.untranslatedLocaleRows,2472);
  assert.equal(d.summary.officialApproved,0);
  assert.equal(d.summary.androidXmlModified,false);
  assert.equal(d.review.length,628);
  assert(d.review.every(x=>x.humanApproval===false&&x.independentApproval===false));
  assert(d.queue.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  assert(d.queue.every(x=>x.targetExists===false));
  assert(draftTsv(d).includes("translation"));
  assert(summaryMd(d).includes("L11-B2"));
});

test("L11-B2 every record is unique, source pinned and independent of older batches",()=>{
  const f=inputs(), base=new Map(f.graph.sources.map(x=>[x.key,x]));
  const names=new Set(f.curated.records.map(x=>x.key));
  const g6=new Set(f.catalog.entries.map(x=>x.key));
  for(const batch of f.batch2){
    assert.equal(batch.schema,"acx-l11-batch2-drafts/1");
    assert.equal(batch.independentApproval,false);
    assert.equal(batch.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    for(const item of batch.records){
      assert(!names.has(item.key),"L11-B2 duplicated prior key "+item.key);
      assert(!g6.has(item.key),"L11-B2 should not rewrite G6 candidate "+item.key);
      names.add(item.key);
      assert(verifyDraft(base.get("string:"+item.key),item));
      assert(item.reviewRisk&&item.reviewRisk.endsWith("REVIEW"));
    }
  }
  assert.equal(names.size,272);
});

test("L11-B2 gates forbid source drift, placeholder loss and language XML overwrite",()=>{
  const f=inputs(),sample=f.batch2[1].records.find(x=>x.key==="notif_external_alarm_text_named");
  assert(sample);
  const row=f.graph.sources.find(x=>x.key==="string:"+sample.key);
  assert(verifyDraft(row,sample));
  assert.throws(()=>verifyDraft({...row,sourcePreview:"text updated"},sample),/source drift/);
  assert.throws(()=>verifyDraft(row,{...sample,translations:{
    ...sample.translations,ja:"別のアプリが追加しました。"
  }}),/format tokens disagree/);
  assert.throws(()=>verifyDraft({...row,locales:{
    ...row.locales,ja:{...row.locales.ja,hasDedicatedDefinition:true}
  }},sample),/overwrite existing Android locale/);
});

test("L11-B2 cannot become independently approved by flipping an input flag",()=>{
  const f=inputs();
  assert.throws(()=>buildL11({...f,batch2:[
    {...f.batch2[0],independentApproval:true},f.batch2[1]
  ]}),/Untrusted L11-B2/);
  assert.throws(()=>buildL11({...f,batch2:[
    {...f.batch2[0],status:"APPROVED"},f.batch2[1]
  ]}),/Untrusted L11-B2/);
});

test("L11-B2 refuses duplicate keys across translation batches",()=>{
  const f=inputs();
  assert.throws(()=>buildL11({...f,batch2:[
    f.batch2[0],
    {...f.batch2[1],records:[f.batch2[0].records[0],...f.batch2[1].records]}
  ]}),/Duplicate curated draft/);
});

test("L11-B2 notification and alarm edit action review stays unapproved",()=>{
  const d=buildL11(inputs());
  for(const key of ["notif_database_downgrade_text","notif_missed_auto_silenced",
    "alarm_edit_remove_challenge","settings_pause_alarms","settings_clear_diagnostics"]){
    const rows=d.review.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B2_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  }
  assert(d.queue.some(x=>x.priority==="P2_LONG_TAIL"));
});
