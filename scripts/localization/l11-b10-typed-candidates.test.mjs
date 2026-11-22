#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildResidualClosure} from "./l11-b10-residual-closure.mjs";
import {validateB10,stageB10} from "./l11-b10-typed-candidates.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cache;
function setup(){if(cache)return cache;
  const inputs=loadL11Inputs(ROOT,"L11-B10-TEST-SOURCE"),
    l11=buildL11(inputs),residual=buildResidualClosure(l11,inputs.graph),
    matrix=generateMatrix(ROOT),packs=["strings","plural","array"].map(k=>
      JSON.parse(readFileSync(join(ROOT,"localization/candidates/l11-b10-"+k+"-drafts-20261122.json"),"utf8")));
  return cache={l11,residual,matrix,packs};}
test("L11-10 drafts close 114 missing locale rows without producing authorized resources",()=>{
  const x=setup(),d=validateB10(x);
  assert.equal(d.stats.residualLocaleRows,114);
  assert.deepEqual(d.stats.byType,{string:50,plurals:62,array:2});
  assert.equal(d.stats.remainingUnfilled,0);
  assert.equal(x.l11.review.length+d.records.length,3100);
  assert.equal(d.stats.independentApproved,0);
  assert(d.records.every(x=>!x.independentApproval&&!x.mayWriteOfficialXml));
});
test("L11-10 isolates real Android string, plural and array XML types",()=>{
  const d=validateB10(setup()),files=stageB10(d);
  for(const v of Object.values(files)){
    assert(v.includes("<string-array name=\"motivational_quotes\">"));
    assert(v.includes("<plurals name=\"alarm_edit_minutes_before\">"));
    assert(v.includes("<string name=\"app_name\">"));
    assert(!v.includes("<string name=\"alarm_edit_minutes_before\">"));
    assert(v.includes("MODEL-UNREVIEWED"));
  }
});
test("L11-10 rejects source mutation, missing format tokens, bad plurality, unauthorized authority",()=>{
  const x=setup(),clone=()=>structuredClone(x.packs);
  let packs=clone();packs[0].records[0].expectedSourceXml+="source drift";
  assert.throws(()=>validateB10({...x,packs}),/source revision drift/);
  packs=clone();packs[1].records.find(r=>r.key==="plurals:alarm_edit_minutes_before").translations.ja.one="壊れた";
  assert.throws(()=>validateB10({...x,packs}),/plural branch format mismatch/);
  packs=clone();delete packs[1].records[0].translations.ja.other;
  assert.throws(()=>validateB10({...x,packs}),/plural quantity mismatch/);
  packs=clone();packs[2].records[0].translations["zh-TW"].pop();
  assert.throws(()=>validateB10({...x,packs}),/array length mismatch/);
  packs=clone();packs[0].independentApproval=true;
  assert.throws(()=>validateB10({...x,packs}),/self-approved/);
});
