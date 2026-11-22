#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL10} from "./l10-source-binding-graph.mjs";
import {buildL11,verifyDraft,draftTsv,summaryMd} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue,dispositionTsv} from "./l11-b3-source-dispositions.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=path=>JSON.parse(readFileSync(join(ROOT,path),"utf8"));
let cached;
function inputs(){
  return cached??={
    graph:buildL10(ROOT,"L11-B5-TEST-FIXTURE-SHA"),
    catalog:read("app/src/debug/assets/lp2/catalog.json"),
    packs:{
      "zh-TW":read("app/src/debug/assets/lp2/packs/zh-TW.json"),
      ja:read("app/src/debug/assets/lp2/packs/ja.json")
    },
    curated:read("localization/candidates/l11-priority-unreviewed-drafts-20261122.json"),
    batch2:[
      read("localization/candidates/l11-b2-settings-drafts-20261122.json"),
      read("localization/candidates/l11-b2-alarm-notification-drafts-20261122.json")
    ],
    batch3:[
      read("localization/candidates/l11-b3-music-alarm-drafts-20261122.json"),
      read("localization/candidates/l11-b3-firing-permissions-drafts-20261122.json")
    ],
    batch4:[
      read("localization/candidates/l11-b4-alarm-editor-drafts-20261122.json"),
      read("localization/candidates/l11-b4-dashboard-drafts-20261122.json"),
      read("localization/candidates/l11-b4-templates-readiness-drafts-20261122.json"),
      read("localization/candidates/l11-b4-alarmfiring-errors-drafts-20261122.json")
    ],
    batch5:[
      read("localization/candidates/l11-b5-backup-restore-drafts-20261122.json"),
      read("localization/candidates/l11-b5-integrations-health-drafts-20261122.json"),
      read("localization/candidates/l11-b5-personalization-accessibility-drafts-20261122.json")
    ]
  };
}

test("L11-B5 conserves 3100 locale gap rows, 201 new keys and prior candidate counts",()=>{
  const data=buildL11(inputs()),s=data.summary;
  assert.equal(s.baseKeys,2471);
  assert.equal(s.translatableKeys,2462);
  assert.equal(s.missingByLocale["zh-TW"],1550);
  assert.equal(s.missingByLocale.ja,1550);
  assert.deepEqual([s.curatedLocaleRows,s.batch2LocaleRows,s.batch3LocaleRows,s.batch4LocaleRows,s.batch5LocaleRows,s.reusedG6LocaleRows],
    [146,398,314,434,402,84]);
  assert.equal(s.batch5Keys,201);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.officialApproved,0);
  assert.equal(s.androidXmlModified,false);
  assert.equal(s.sourceDriftHolds,0);
  assert.equal(data.queue.length,3100);
  assert.equal(data.review.length,1778);
  assert.equal(s.untranslatedLocaleRows,1322);
  assert(data.review.every(x=>x.humanApproval===false&&x.independentApproval===false));
  assert(data.review.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  assert(data.queue.every(x=>x.targetExists===false));
  assert.match(summaryMd(data),/L11-B5/);
  assert.match(draftTsv(data),/candidate_text/);
});

test("L11-B5 only accepts non-overlapping source-pinned locale gaps and valid format tokens",()=>{
  const x=inputs(),sources=new Map(x.graph.sources.map(y=>[y.key,y]));
  const existing=new Set([
    ...x.curated.records.map(y=>y.key),
    ...x.batch2.flatMap(b=>b.records.map(y=>y.key)),
    ...x.batch3.flatMap(b=>b.records.map(y=>y.key)),
    ...x.batch4.flatMap(b=>b.records.map(y=>y.key)),
    ...x.catalog.entries.map(y=>y.key)
  ]);
  assert.deepEqual(x.batch5.map(b=>b.records.length),[76,78,47]);
  let n=0;
  for(const batch of x.batch5){
    assert.equal(batch.schema,"acx-l11-batch5-drafts/1");
    assert.equal(batch.independentApproval,false);
    assert.equal(batch.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    for(const draft of batch.records){
      assert(!existing.has(draft.key),"Duplicate candidate: "+draft.key);
      existing.add(draft.key);
      const source=sources.get("string:"+draft.key);
      assert(source,"No source "+draft.key);
      assert(verifyDraft(source,draft),"Invalid draft source/placeholder "+draft.key);
      assert(draft.reviewRisk?.endsWith("REVIEW"));
      assert(draft.translations["zh-TW"].length>0&&draft.translations.ja.length>0);
      n++;
    }
  }
  assert.equal(n,201);
});

test("L11-B5 critical privacy and data-loss words remain distinct and cannot be promoted",()=>{
  const d=buildL11(inputs());
  for(const key of [
    "settings_append_alarms","settings_replace_alarms",
    "settings_keep_restored_disabled","settings_keep_integrations_hint",
    "settings_encrypted_backup_warning","settings_plain_backup_warning",
    "settings_health_read_sleep_description","settings_hue_forget_message",
    "settings_challenge_bypass_description","settings_repeat_missed_description"
  ]){
    const rows=d.review.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B5_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>x.candidateState==="DRAFT_NEEDS_SEMANTIC_REVIEW"));
    assert(rows.every(x=>x.independentApproval===false&&x.humanApproval===false));
    assert(rows.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  }
  const find=key=>inputs().batch5.flatMap(b=>b.records).find(x=>x.key===key);
  assert.notEqual(find("settings_append_alarms").translations.ja,
    find("settings_replace_alarms").translations.ja);
  assert.match(find("settings_keep_integrations_hint").translations["zh-TW"],/簡訊|撥打電話/);
  assert.match(find("settings_health_read_sleep_description").translations.ja,/睡眠/);
  assert.match(find("settings_plain_backup_description").translations["zh-TW"],/可讀取.*JSON/);
});

test("L11-B5 placeholders, percent literals and non-user technical terms survive in both locales",()=>{
  const drafts=inputs().batch5.flatMap(b=>b.records);
  const find=k=>drafts.find(x=>x.key===k);
  const testKeys=[
    "settings_backup_version","settings_backup_alarm_counts",
    "settings_backup_failure_with_hint",
    "settings_health_last_session","settings_connection_lan_domain",
    "settings_challenge_volume","settings_percent","settings_hold_duration_short",
    "settings_hours_minutes_short","settings_use_accent"
  ];
  for(const key of testKeys){
    const r=find(key);assert(r,key);
    const tokens=[...new Set(r.expectedEnglishPreview.match(/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g)||[])].sort();
    for(const locale of ["zh-TW","ja"]){
      const target=[...new Set(r.translations[locale].match(/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g)||[])].sort();
      assert.deepEqual(target,tokens,key+" "+locale);
    }
  }
  assert(find("settings_challenge_volume").translations["zh-TW"].includes("%%"));
  assert(find("settings_percent").translations.ja.includes("%%"));
  assert(find("settings_backup_version").translations.ja.includes("%2$s"));
  assert(find("settings_health_last_session").translations["zh-TW"].includes("%2$d"));
});

test("L11-B5 residual disposition is a review queue, never automatic English fallback approval",()=>{
  const x=inputs(),l11=buildL11(x);
  const triage=buildDispositionQueue(l11,x.graph);
  assert.equal(triage.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(triage.stats.candidateLocaleRows,1778);
  assert.equal(triage.stats.undecidedLocaleRows,1322);
  assert.equal(triage.suggestions.length,1322);
  assert.equal(triage.stats.excludedNonTranslatableBaseKeys,9);
  assert.equal(Object.values(triage.stats.byDisposition).reduce((a,b)=>a+b,0),1322);
  assert(triage.suggestions.every(x=>x.candidateOnly===true&&x.humanReviewRequired===true));
  assert(triage.suggestions.every(x=>x.officialFallbackApproved===false&&x.officialTranslationApproved===false));
  assert(triage.suggestions.every(x=>x.sourceHash.startsWith("sha256:")));
  assert.match(dispositionTsv(triage),/source_hash/);
});

test("L11-B5 rejects source drift, stale formatting and pre-existing XML rather than filling counts",()=>{
  const x=inputs(),item=x.batch5[0].records.find(x=>x.key==="settings_backup_version");
  const row=x.graph.sources.find(x=>x.key==="string:"+item.key);
  assert(verifyDraft(row,item));
  assert.throws(()=>verifyDraft({...row,sourcePreview:"New English meaning"},item),/source drift/);
  assert.throws(()=>verifyDraft(row,{...item,translations:{
    ...item.translations,"zh-TW":"第 %1$d 版備份"
  }}),/format tokens disagree/);
  assert.throws(()=>verifyDraft({...row,locales:{
    ...row.locales,ja:{...row.locales.ja,hasDedicatedDefinition:true}
  }},item),/overwrite existing Android locale/);
});

test("L11-B5 cannot forge approval or duplicate keys across independently sourced draft batches",()=>{
  const x=inputs();
  assert.throws(()=>buildL11({...x,batch5:[
    {...x.batch5[0],independentApproval:true},...x.batch5.slice(1)
  ]}),/Untrusted L11-B5/);
  assert.throws(()=>buildL11({...x,batch5:[
    {...x.batch5[0],status:"APPROVED"},...x.batch5.slice(1)
  ]}),/Untrusted L11-B5/);
  assert.throws(()=>buildL11({...x,batch5:[
    x.batch5[0],
    {...x.batch5[1],records:[...x.batch5[1].records,x.batch5[0].records[0]]},
    x.batch5[2]
  ]}),/Duplicate curated draft/);
});
