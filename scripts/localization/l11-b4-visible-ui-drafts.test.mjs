#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL10} from "./l10-source-binding-graph.mjs";
import {buildL11,verifyDraft} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue} from "./l11-b3-source-dispositions.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=path=>JSON.parse(readFileSync(join(ROOT,path),"utf8"));
let cache;
function inputs(){
  return cache??={
    graph:buildL10(ROOT,"L11-B4-TEST-SOURCE"),
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
    ]
  };
}

test("L11-B4 conserves exact full locale deficit and sources, adding 217 x 2 source-locked draft rows",()=>{
  const d=buildL11(inputs()),s=d.summary;
  assert.equal(s.baseKeys,2471);
  assert.equal(s.translatableKeys,2462);
  assert.equal(s.missingByLocale["zh-TW"],1550);
  assert.equal(s.missingByLocale.ja,1550);
  assert.equal(s.curatedKeys,73);
  assert.equal(s.curatedLocaleRows,146);
  assert.equal(s.batch2Keys,199);
  assert.equal(s.batch2LocaleRows,398);
  assert.equal(s.batch3Keys,157);
  assert.equal(s.batch3LocaleRows,314);
  assert.equal(s.batch4Keys,217);
  assert.equal(s.batch4LocaleRows,434);
  assert.equal(s.reusedG6LocaleRows,84);
  assert.equal(s.untranslatedLocaleRows,1724);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.officialApproved,0);
  assert.equal(s.androidXmlModified,false);
  assert.equal(s.sourceDriftHolds,0);
  assert.equal(d.queue.length,3100);
  assert.equal(d.review.length,1376);
  assert(d.review.every(x=>x.independentApproval===false&&x.humanApproval===false));
  assert(d.review.every(x=>x.reviewStatus==="UNREVIEWED"));
  assert(d.queue.every(x=>x.targetExists===false&&x.actionSemantic==="NOT_VERIFIED_BY_L11"));
});

test("L11-B4 checks all batch keys, no overlap, original English identity, locale XML and placeholder equivalence",()=>{
  const x=inputs(),sources=new Map(x.graph.sources.map(r=>[r.key,r]));
  const previous=new Set([
    ...x.curated.records.map(r=>r.key),
    ...x.batch2.flatMap(p=>p.records.map(r=>r.key)),
    ...x.batch3.flatMap(p=>p.records.map(r=>r.key)),
    ...x.catalog.entries.map(r=>r.key)
  ]);
  assert.deepEqual(x.batch4.map(p=>p.records.length),[72,64,47,34]);
  let total=0;
  for(const batch of x.batch4){
    assert.equal(batch.schema,"acx-l11-batch4-drafts/1");
    assert.equal(batch.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    assert.equal(batch.independentApproval,false);
    for(const item of batch.records){
      assert(!previous.has(item.key),"duplicate/legacy key "+item.key);
      previous.add(item.key);
      assert(verifyDraft(sources.get("string:"+item.key),item));
      assert(item.reviewRisk?.endsWith("REVIEW"));
      total++;
    }
  }
  assert.equal(total,217);
});

test("L11-B4 retains percent escape, solar offset sign and explicit line-break escape as source-bound draft data",()=>{
  const x=inputs();
  const get=key=>x.batch4.flatMap(p=>p.records).find(r=>r.key===key);
  const completed=get("alarmfiring_complete_2");
  assert(completed);
  assert(completed.expectedEnglishPreview.includes("%%"));
  assert(completed.translations["zh-TW"].includes("%%"));
  assert(completed.translations.ja.includes("%%"));
  const solar=get("alarm_edit_solar_after");
  assert(solar);
  assert(solar.expectedEnglishPreview.startsWith("+%1$d"));
  assert(solar.translations["zh-TW"].startsWith("+%1$d"));
  assert(solar.translations.ja.startsWith("+%1$d"));
  const multiline=get("alarmfiring_too_slow_ntry_again");
  assert(multiline.expectedEnglishPreview.includes("\\n"));
  assert(multiline.translations["zh-TW"].includes("\\n"));
  assert(multiline.translations.ja.includes("\\n"));
  const timing=get("alarm_edit_fixed_zone_hint");
  assert(timing.translations["zh-TW"].includes("%1$s:%2$s"));
  assert(timing.translations.ja.includes("%1$s:%2$s"));
});

test("L11-B4 continues evidence-bounded residual disposition without auto-approving fallback",()=>{
  const x=inputs(),d=buildL11(x),queue=buildDispositionQueue(d,x.graph);
  assert.equal(queue.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(queue.stats.candidateLocaleRows,1376);
  assert.equal(queue.stats.undecidedLocaleRows,1724);
  assert.equal(queue.suggestions.length,1724);
  assert.equal(queue.stats.excludedNonTranslatableBaseKeys,9);
  assert.equal(Object.values(queue.stats.byDisposition).reduce((a,b)=>a+b,0),1724);
  assert(queue.suggestions.every(r=>r.humanReviewRequired===true&&r.candidateOnly===true));
  assert(queue.suggestions.every(r=>r.officialFallbackApproved===false&&r.officialTranslationApproved===false));
  assert(queue.suggestions.every(r=>r.sourceHash.startsWith("sha256:")));
});

test("L11-B4 never turns drafted media, emergency contact or alarm schedule behavior into approval",()=>{
  const d=buildL11(inputs());
  for(const key of [
    "alarm_edit_guardian_direct_sms",
    "alarm_edit_shift_hint",
    "alarm_edit_solar_hint",
    "alarmlist_release_to_delete",
    "dashboard_calendar_access",
    "readiness_no_nfc",
    "alarmfiring_voice_mic_denied"
  ]){
    const rows=d.review.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B4_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.candidateState==="DRAFT_NEEDS_SEMANTIC_REVIEW"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>x.actionSemantic==="NOT_VERIFIED_BY_L11"));
  }
});

test("L11-B4 rejects duplicate keys, source drift, lost placeholder, locale overwrites and forged review approval",()=>{
  const x=inputs(),record=x.batch4[0].records.find(r=>r.key==="alarm_edit_guardian_direct_sms");
  const row=x.graph.sources.find(r=>r.key==="string:"+record.key);
  assert(row&&verifyDraft(row,record));
  assert.throws(()=>verifyDraft({...row,sourcePreview:"Changed source"},record),/source drift/);
  assert.throws(()=>verifyDraft(row,{...record,translations:{
    ...record.translations,ja:"SMS を送ります。"
  }}),/format tokens disagree/);
  assert.throws(()=>verifyDraft({...row,locales:{
    ...row.locales,"zh-TW":{...row.locales["zh-TW"],hasDedicatedDefinition:true}
  }},record),/overwrite existing Android locale/);
  assert.throws(()=>buildL11({...x,batch4:[{
    ...x.batch4[0],independentApproval:true
  },...x.batch4.slice(1)]}),/Untrusted L11-B4/);
  assert.throws(()=>buildL11({...x,batch4:[
    x.batch4[0],
    {...x.batch4[1],records:[...x.batch4[1].records,x.batch4[0].records[0]]},
    ...x.batch4.slice(2)
  ]}),/Duplicate curated draft/);
});


test("L11-B4 source preview follows B0-T1 entity decoding, never raw XML escaping",()=>{
  const source=inputs().graph.sources.find(x=>x.key==="string:alarm_edit_speak_context");
  const draft=inputs().batch4[0].records.find(x=>x.key==="alarm_edit_speak_context");
  assert(source);
  assert(draft);
  assert.equal(source.sourcePreview,"Speak time, date & weather");
  assert.equal(draft.expectedEnglishPreview,source.sourcePreview);
  assert(verifyDraft(source,draft));
  assert(!draft.expectedEnglishPreview.includes("&amp;"));
});
