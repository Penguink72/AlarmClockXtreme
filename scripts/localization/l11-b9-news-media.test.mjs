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
let saved;
function inputs(){return saved??=loadL11Inputs(ROOT,"L11-B9-TEST-SOURCE");}
test("L11-B9 adds 153 source-locked keys and 306 locale rows without changing the 3100 gap",()=>{
  const a=inputs(),x=buildL11(a),s=x.summary;
  assert.deepEqual(a.batch9.map(b=>b.records.length),[37,33,53,30]);
  assert.equal(s.batch9Keys,153);assert.equal(s.batch9LocaleRows,306);
  assert.equal(x.queue.length,3100);assert.equal(x.review.length,2986);
  assert.equal(s.untranslatedLocaleRows,114);
  assert.equal(s.existingLocaleProtected,1824);
  assert.equal(s.sourceDriftHolds,0);
  assert.equal(s.officialApproved,0);assert.equal(s.androidXmlModified,false);
});
test("L11-B9 all news/media/daily UI drafts preserve canonical source placeholders and avoid duplicates",()=>{
  const a=inputs(),sources=new Map(a.graph.sources.map(x=>[x.key,x]));
  const old=new Set([...a.curated.records.map(r=>r.key),
    ...[...a.batch2,...a.batch3,...a.batch4,...a.batch5,...a.batch6,...a.batch7,...a.batch8]
      .flatMap(x=>x.records.map(r=>r.key)),...a.catalog.entries.map(r=>r.key)]);
  let n=0;
  for(const b of a.batch9){
    assert.equal(b.schema,"acx-l11-batch9-drafts/1");
    assert.equal(b.status,"MODEL_PRELIMINARY_NOT_APPROVED");
    assert.equal(b.independentApproval,false);
    for(const r of b.records){
      assert(!old.has(r.key),"Repeated key "+r.key);
      old.add(r.key);
      assert(verifyDraft(sources.get("string:"+r.key),r));n++;
    }
  }
  assert.equal(n,153);
});
test("L11-B9 unreviewed network and scheduling strings never receive human approval",()=>{
  const rev=buildL11(inputs()).review;
  for(const key of ["news_error_tls","news_stale_headlines","youtube_error_blocked",
    "youtube_error_engine_update","youtube_downloading","briefing_spoken_time_and_date",
    "oem_samsung_title","shift_four_title","chess_puzzle_corner_squeeze_explanation"]){
    const rows=rev.filter(x=>x.key==="string:"+key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.translationOrigin==="L11_B9_MODEL_DRAFT_UNREVIEWED"));
    assert(rows.every(x=>x.reviewStatus==="UNREVIEWED"));
    assert(rows.every(x=>!x.independentApproval&&!x.humanApproval));
  }
});
test("L11-B9 remaining 114 locale gaps stay conditional suggestions, not approved English fallbacks",()=>{
  const a=inputs(),x=buildL11(a),q=buildDispositionQueue(x,a.graph);
  assert.equal(q.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(q.stats.candidateLocaleRows,2986);
  assert.equal(q.stats.undecidedLocaleRows,114);
  assert.equal(q.suggestions.length,114);
  assert(q.suggestions.every(r=>r.candidateOnly&&r.humanReviewRequired));
  assert(q.suggestions.every(r=>!r.officialFallbackApproved&&!r.officialTranslationApproved));
});
test("L11-B9 rejects English drift, dropped placeholder, duplicate and fabricated approval",()=>{
  const a=inputs(),r=a.batch9[1].records.find(x=>x.key==="youtube_downloading"),
    src=a.graph.sources.find(x=>x.key==="string:"+r.key);
  assert(verifyDraft(src,r));
  assert.throws(()=>verifyDraft({...src,sourcePreview:"new"},r),/source drift/);
  assert.throws(()=>verifyDraft(src,{...r,translations:{...r.translations,ja:"ダウンロード中"}}),/format tokens disagree/);
  assert.throws(()=>buildL11({...a,batch9:[{...a.batch9[0],independentApproval:true},...a.batch9.slice(1)]}),/Untrusted L11-B9/);
  assert.throws(()=>buildL11({...a,batch9:[
    {...a.batch9[0],records:[...a.batch9[0].records,a.batch9[1].records[0]]},
    ...a.batch9.slice(1)
  ]}),/Duplicate curated draft/);
});
test("L11-B9 new full L12 mechanically stages 2986 drafts while preserving official release gate",()=>{
  const a=inputs(),l11=buildL11(a),matrix=generateMatrix(ROOT),
    glossary=JSON.parse(readFileSync(join(ROOT,"localization/governance/g1/glossary-contract.json"),"utf8"));
  const r=buildL12({l11,graph:a.graph,matrix,glossary});
  assert.equal(r.stats.totalCandidates,2986);
  assert.equal(r.stats.byLocale["zh-TW"],1493);
  assert.equal(r.stats.byLocale.ja,1493);
  assert.equal(r.stats.independentSemanticApproved,0);
  assert.equal(r.stats.officialReleaseEligible,0);
});
