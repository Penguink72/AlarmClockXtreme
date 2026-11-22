#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildL10 } from "./l10-source-binding-graph.mjs";
import { buildL11, verifyDraft } from "./l11-translation-worklist.mjs";
import { buildDispositionQueue } from "./l11-b3-source-dispositions.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8"));
let saved;
function inputs(){return saved??={
  graph:buildL10(ROOT,"L11-B6-TEST-HEAD"),
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
  ],
  batch6:[
    read("localization/candidates/l11-b6-statistics-drafts-20261122.json"),
    read("localization/candidates/l11-b6-bedtime-drafts-20261122.json")
  ]
};}

test("L11-B6 accounts for every source and locale gap with 148 new keys",()=>{
  const d=buildL11(inputs()),s=d.summary;
  assert.equal(s.baseKeys,2471);
  assert.equal(s.translatableKeys,2462);
  assert.deepEqual(s.missingByLocale,{"zh-TW":1550,ja:1550});
  assert.deepEqual([s.curatedLocaleRows,s.batch2LocaleRows,s.batch3LocaleRows,
    s.batch4LocaleRows,s.batch5LocaleRows,s.batch6LocaleRows,s.reusedG6LocaleRows],
    [146,398,314,434,402,296,84]);
  assert.equal(s.batch6Keys,148);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.androidXmlModified,false);
  assert.equal(s.officialApproved,0);
  assert.equal(d.queue.length,3100);
  assert.equal(d.review.length,2074);
  assert.equal(s.untranslatedLocaleRows,1026);
});

test("L11-B6 all 148 source previews and formatted translations match original source",()=>{
  const x=inputs(),sources=new Map(x.graph.sources.map(r=>[r.key,r]));
  const old=new Set([
    ...x.curated.records.map(r=>r.key),
    ...[...x.batch2,...x.batch3,...x.batch4,...x.batch5].flatMap(p=>p.records.map(r=>r.key)),
    ...x.catalog.entries.map(r=>r.key)
  ]);
  assert.deepEqual(x.batch6.map(b=>b.records.length),[67,81]);
  let count=0;
  for(const pack of x.batch6){
    assert.equal(pack.schema,"acx-l11-batch6-drafts/1");
    assert.equal(pack.independentApproval,false);
    assert.equal(pack.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    for(const r of pack.records){
      assert(!old.has(r.key),"Duplicate "+r.key);
      old.add(r.key);
      assert(verifyDraft(sources.get("string:"+r.key),r),r.key);
      count++;
    }
  }
  assert.equal(count,148);
});

test("L11-B6 does not confer authority on statistics, sleep and alarm result translations",()=>{
  const d=buildL11(inputs());
  for(const key of ["stats_removes_recorded_alarm_outcomes_resets",
     "stats_action_dismissed","stats_action_snoozed","stats_sleep_debt",
     "bedtime_jetlag_shift_description","bedtime_battery_warning",
     "bedtime_do_not_disturb_at_bedtime","bedtime_tracking_no_audio_saved"]){
    const rows=d.review.filter(r=>r.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(r=>r.translationOrigin==="L11_B6_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(r=>r.humanApproval===false&&r.independentApproval===false));
    assert(rows.every(r=>r.actionSemantic==="NOT_VERIFIED_BY_L11"));
  }
});

test("L11-B6 residual queue remains fully accounted for and has zero fallback approvals",()=>{
  const x=inputs(),d=buildL11(x),triage=buildDispositionQueue(d,x.graph);
  assert.equal(triage.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(triage.stats.candidateLocaleRows,2074);
  assert.equal(triage.stats.undecidedLocaleRows,1026);
  assert.equal(triage.stats.excludedNonTranslatableBaseKeys,9);
  assert.equal(Object.values(triage.stats.byDisposition).reduce((a,b)=>a+b,0),1026);
  assert(triage.suggestions.every(r=>r.candidateOnly&&r.humanReviewRequired));
  assert(triage.suggestions.every(r=>r.officialFallbackApproved===false));
});

test("L11-B6 explicitly refuses source drift, duplicate IDs, wrong placeholders and false approval",()=>{
  const x=inputs(),item=x.batch6[1].records.find(r=>r.key==="bedtime_battery_warning"),
    row=x.graph.sources.find(r=>r.key==="string:"+item.key);
  assert(verifyDraft(row,item));
  assert.throws(()=>verifyDraft({...row,sourcePreview:"changed"},item),/source drift/);
  assert.throws(()=>verifyDraft(row,{...item,translations:{
    ...item.translations,ja:"電池残量が不足"
  }}),/format tokens disagree/);
  assert.throws(()=>buildL11({...x,batch6:[
    {...x.batch6[0],independentApproval:true},x.batch6[1]
  ]}),/Untrusted L11-B6/);
  assert.throws(()=>buildL11({...x,batch6:[
    {...x.batch6[0],records:[...x.batch6[0].records,x.batch6[1].records[0]]},x.batch6[1]
  ]}),/Duplicate curated draft/);
});
