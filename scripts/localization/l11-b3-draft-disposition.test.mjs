#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL10} from "./l10-source-binding-graph.mjs";
import {buildL11,verifyDraft} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue,suggestDisposition,dispositionTsv}
  from "./l11-b3-source-dispositions.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=path=>JSON.parse(readFileSync(join(ROOT,path),"utf8"));
let cached;
function inputs(){
  return cached??={
    graph:buildL10(ROOT,"l11-b3-fixture-sha"),
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
    ]
  };
}

test("L11-3 carries full 3100 denominator and exactly 157 new bilingual keys",()=>{
  const x=inputs(),data=buildL11(x),s=data.summary;
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
  assert.equal(s.reusedG6LocaleRows,84);
  assert.equal(s.untranslatedLocaleRows,2158);
  assert.equal(data.queue.length,3100);
  assert.equal(data.review.length,942);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.officialApproved,0);
  assert.equal(s.androidXmlModified,false);
});

test("L11-3 every source-pinned draft is distinct, has valid locale tokens, does not overwrite XML or G6",()=>{
  const x=inputs(),src=new Map(x.graph.sources.map(v=>[v.key,v]));
  const seen=new Set([
    ...x.curated.records.map(r=>r.key),
    ...x.batch2.flatMap(b=>b.records.map(r=>r.key))
  ]);
  const g6=new Set(x.catalog.entries.map(r=>r.key));
  for(const pack of x.batch3){
    assert.equal(pack.schema,"acx-l11-batch3-drafts/1");
    assert.equal(pack.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    assert.equal(pack.independentApproval,false);
    for(const row of pack.records){
      assert(!seen.has(row.key),"duplicate "+row.key);
      assert(!g6.has(row.key),"G6 candidate already exists "+row.key);
      seen.add(row.key);
      const original=src.get("string:"+row.key);
      assert(original,"source missing "+row.key);
      assert(verifyDraft(original,row),"source invalid "+row.key);
      assert(row.reviewRisk?.endsWith("REVIEW"));
    }
  }
  assert.equal(seen.size,429);
});

test("L11-3 action-critical drafts remain explicitly unreviewed",()=>{
  const data=buildL11(inputs());
  for(const key of ["alarm_edit_remove_ringtone","alarm_edit_dismiss_song_end",
    "alarm_edit_dismiss_action","alarmfiring_waiting_for_a_location_fix_leave",
    "wake_confirm_keep_protection","app_dismiss"]){
    const rows=data.review.filter(r=>r.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(r=>r.translationOrigin==="L11_B3_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(r=>r.reviewStatus==="UNREVIEWED"));
    assert(rows.every(r=>r.actionSemantic==="NOT_VERIFIED_BY_L11"));
    assert(rows.every(r=>r.independentApproval===false && r.humanApproval===false));
    assert(rows.every(r=>r.translation?.length>1));
  }
});

test("L11-3 semantic/disposition triage partitions exactly all undrafted locale rows",()=>{
  const x=inputs(),data=buildL11(x);
  const review=buildDispositionQueue(data,x.graph);
  assert.equal(review.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(review.stats.candidateLocaleRows,942);
  assert.equal(review.stats.undecidedLocaleRows,2158);
  assert.equal(review.suggestions.length,2158);
  assert.equal(review.stats.excludedNonTranslatableBaseKeys,9);
  assert.equal(Object.values(review.stats.byDisposition).reduce((a,b)=>a+b,0),2158);
  assert(review.suggestions.every(r=>r.humanReviewRequired===true&&r.candidateOnly===true));
  assert(review.suggestions.every(r=>r.officialFallbackApproved===false));
  assert(review.suggestions.every(r=>r.officialTranslationApproved===false));
  assert(review.suggestions.every(r=>r.sourceHash.startsWith("sha256:")));
  assert(review.nonTranslatableBySource.every(r=>r.disposition==="NON_TRANSLATABLE_BY_SOURCE"));
  assert(dispositionTsv(review).includes("disposition"));
});

test("L11-3 no-static-use and technical fallback are proposals, never removal/adoption proof",()=>{
  const specimen={
    key:"string:debug_name",locale:"ja",sourceHash:"sha256:fixture",
    sourcePreview:"http://example.com/foo",priority:"P2_LONG_TAIL",
    bindingCandidates:1,surfaceHints:[],translation:null
  };
  let a=suggestDisposition(specimen);
  assert.equal(a.disposition,"INTENTIONAL_FALLBACK_CANDIDATE");
  assert.equal(a.humanReviewRequired,true);
  assert.equal(a.officialFallbackApproved,false);
  a=suggestDisposition({...specimen,bindingCandidates:0});
  assert.equal(a.disposition,"UNKNOWN_MAPPING");
  assert.equal(a.reason,"NO_STATIC_BINDING_CANNOT_PROVE_UNUSED");
  a=suggestDisposition({...specimen,sourcePreview:"Delete all alarms"});
  assert.equal(a.disposition,"SHOULD_TRANSLATE_CANDIDATE");
  assert.equal(a.officialTranslationApproved,false);
  assert.throws(()=>suggestDisposition({...specimen,translation:"草稿"}),
    /Cannot dispose already drafted row/);
});

test("L11-3 prevents duplicate, source drift, placeholder drop, or forged independent approval",()=>{
  const x=inputs(),sample=x.batch3[0].records.find(r=>r.key==="alarm_edit_remove_ringtone");
  const source=x.graph.sources.find(r=>r.key==="string:"+sample.key);
  assert(verifyDraft(source,sample));
  assert.throws(()=>verifyDraft({...source,sourcePreview:"Remove all media"},sample),
    /source drift/);
  assert.throws(()=>verifyDraft(source,{...sample,translations:{
    ...sample.translations,"zh-TW":"從鈴聲池移除"
  }}),/format tokens disagree/);
  assert.throws(()=>buildL11({...x,batch3:[
    {...x.batch3[0],independentApproval:true},x.batch3[1]
  ]}),/Untrusted L11-B3/);
  assert.throws(()=>buildL11({...x,batch3:[
    {...x.batch3[0],records:[...x.batch3[0].records,x.batch3[1].records[0]]},
    x.batch3[1]
  ]}),/Duplicate curated draft/);
});
