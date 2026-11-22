#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL11,loadL11Inputs,verifyDraft} from "./l11-translation-worklist.mjs";
import {buildDispositionQueue} from "./l11-b3-source-dispositions.mjs";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
function fixtures(){return cached??=loadL11Inputs(root,"L11-B7-TEST-SHA");}
test("L11-B7 adds 116 source-locked bilingual keys and accounts for complete locale gaps",()=>{
  const d=buildL11(fixtures()),s=d.summary;
  assert.equal(s.batch7Keys,116);
  assert.equal(s.batch7LocaleRows,232);
  assert.deepEqual([s.curatedLocaleRows,s.batch2LocaleRows,s.batch3LocaleRows,s.batch4LocaleRows,
    s.batch5LocaleRows,s.batch6LocaleRows,s.batch7LocaleRows,s.reusedG6LocaleRows],
    [146,398,314,434,402,296,232,84]);
  assert.equal(d.queue.length,3100);
  assert.equal(d.review.length,2306);
  assert.equal(s.untranslatedLocaleRows,794);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.androidXmlModified,false);
  assert.equal(s.officialApproved,0);
});
test("L11-B7 respects B0 source and existing locale definitions and never duplicates old candidates",()=>{
  const input=fixtures(),source=new Map(input.graph.sources.map(r=>[r.key,r]));
  const previous=new Set([
    ...input.curated.records.map(r=>r.key),
    ...[...input.batch2,...input.batch3,...input.batch4,...input.batch5,...input.batch6].flatMap(b=>b.records.map(r=>r.key)),
    ...input.catalog.entries.map(r=>r.key)
  ]);
  assert.deepEqual(input.batch7.map(p=>p.records.length),[44,72]);
  for(const batch of input.batch7){
    assert.equal(batch.schema,"acx-l11-batch7-drafts/1");
    assert.equal(batch.independentApproval,false);
    assert.equal(batch.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    for(const draft of batch.records){
      assert(!previous.has(draft.key),"Duplicate key "+draft.key);
      previous.add(draft.key);
      assert(verifyDraft(source.get("string:"+draft.key),draft));
    }
  }
});
test("L11-B7 retains privacy warning and high-impact alarm history semantics as unapproved",()=>{
  const data=buildL11(fixtures());
  for(const key of ["stats_today_s_wake_is_already_counted",
    "stats_grant_read_sleep_to_add_sleep","stats_sonar_decision_detail",
    "bedtime_grant_dnd_access_so_alarmclockxtreme_can",
    "bedtime_monitoring_movement_and_loud_sleep_sounds",
    "bedtime_sonar_mic_denied","bedtime_sonar_start_failed"]){
    const rows=data.review.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B7_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>x.humanApproval===false&&x.independentApproval===false));
  }
});
test("L11-B7 residual classification retains all 794 undecided locale rows and no fallback approvals",()=>{
  const input=fixtures(),d=buildL11(input),q=buildDispositionQueue(d,input.graph);
  assert.equal(q.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(q.stats.candidateLocaleRows,2306);
  assert.equal(q.stats.undecidedLocaleRows,794);
  assert.equal(q.suggestions.length,794);
  assert(q.suggestions.every(x=>x.candidateOnly&&x.humanReviewRequired));
  assert(q.suggestions.every(x=>!x.officialTranslationApproved&&!x.officialFallbackApproved));
});
test("L11-B7 rejects changed English, dropped format parameter, duplicated keys, forged approval",()=>{
  const input=fixtures(),draft=input.batch7[1].records.find(x=>x.key==="bedtime_sonar_start_failed"),
    src=input.graph.sources.find(x=>x.key==="string:"+draft.key);
  assert(verifyDraft(src,draft));
  assert.throws(()=>verifyDraft({...src,sourcePreview:"changed"},draft),/source drift/);
  assert.throws(()=>verifyDraft(src,{...draft,translations:{...draft.translations,ja:"失敗しました"}}),
    /format tokens disagree/);
  assert.throws(()=>buildL11({...input,batch7:[
    {...input.batch7[0],independentApproval:true},input.batch7[1]
  ]}),/Untrusted L11-B7/);
  assert.throws(()=>buildL11({...input,batch7:[
    {...input.batch7[0],records:[...input.batch7[0].records,input.batch7[1].records[0]]},input.batch7[1]
  ]}),/Duplicate curated draft/);
});
