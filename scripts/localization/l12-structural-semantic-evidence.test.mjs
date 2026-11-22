#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildL12,checkCandidate,l12Tsv,l12Markdown,risksFor}
  from "./l12-structural-semantic-evidence.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
function fixtures(){
  if(!cached){
    const inputs=loadL11Inputs(ROOT,"L12-SAME-SHA-TEST-FIXTURE"),
      l11=buildL11(inputs),matrix=generateMatrix(ROOT),
      glossary=JSON.parse(readFileSync(join(ROOT,"localization/governance/g1/glossary-contract.json"),"utf8"));
    cached={inputs,l11,matrix,glossary};
  }
  return cached;
}
function result(){
  const {inputs,l11,matrix,glossary}=fixtures();
  return buildL12({l11,graph:inputs.graph,matrix,glossary});
}

test("L12 consumes the exact current L11 review queue without publishing or approving resources",()=>{
  const d=result();
  assert.equal(d.stats.totalCandidates,2680);
  assert.equal(d.records.length,2680);
  assert.equal(d.stats.byLocale["zh-TW"],1340);
  assert.equal(d.stats.byLocale.ja,1340);
  assert.equal(d.stats.officialReleaseEligible,0);
  assert.equal(d.stats.independentSemanticApproved,0);
  assert(d.records.every(r=>r.independentSemanticApproval===false));
  assert(d.records.every(r=>r.officialReleaseEligible===false));
  assert(d.records.every(r=>r.contentDecision==="NOT_REVIEWED"));
  assert(d.records.every(r=>r.reviewerIdentity===null));
  assert.match(l12Tsv(d),/translation_hash/);
  assert.match(l12Markdown(d),/L12/);
});

test("L12 source, translation and context revisions are stable and traceable",()=>{
  const d=result();
  const source=new Map(fixtures().matrix.rows.map(x=>[x.key,x]));
  for(const r of d.records){
    assert.equal(r.sourceHash,source.get(r.key).sourceHash);
    assert.match(r.translationHash,/^sha256:[a-f0-9]{64}$/);
    assert.match(r.reviewRevision,/^sha256:[a-f0-9]{64}$/);
    assert(r.glossaryRevision.length>0);
    assert.equal(r.bindingCount,r.bindingEvidence.length+(r.bindingsTruncated?
      r.bindingCount-r.bindingEvidence.length:0));
  }
  assert.deepEqual(d.records.map(r=>[r.key,r.locale,r.reviewRevision]),
    result().records.map(r=>[r.key,r.locale,r.reviewRevision]));
});

test("L12 marks real high-risk operations and glossary as hints, without presuming onClick semantics",()=>{
  const d=result(),get=key=>d.records.filter(r=>r.key==="string:"+key);
  for(const key of ["settings_replace_alarms","settings_keep_integrations_hint",
    "settings_hue_forget_message","alarm_edit_remove_ringtone",
    "bedtime_do_not_disturb_at_bedtime","stats_action_dismissed"]){
    const rows=get(key);
    assert.equal(rows.length,2,key);
    assert(rows.every(x=>x.contentDecision==="NOT_REVIEWED"));
    assert(rows.every(x=>x.behaviorProof==="NOT_VERIFIED"));
    assert(rows.every(x=>x.risks.length>=1));
  }
  const x=get("alarm_edit_remove_ringtone")[0];
  assert(x.risks.includes("DATA_LOSS_OR_RESTORE"));
  assert(d.stats.actionSensitiveRows>0);
  assert(d.stats.glossaryHintRows>0);
  assert(d.records.every(x=>x.bindingEvidence.every(y=>
    y.behaviorProof==="NOT_VERIFIED_BY_STATIC_SCAN")));
});

test("L12 catches repeated printf argument disappearance, even when L11 set-based checks would miss it",()=>{
  const source={sourceHash:"sha256:s",sourcePreview:"%1$s vs %1$s",
    translatable:true,hasRichMarkup:false};
  const candidate={sourceHash:"sha256:s",sourcePreview:source.sourcePreview,
    targetExists:false,translation:"%1$s 比較"};
  assert(checkCandidate(candidate,source).issues.includes("FORMAT_MULTIPLICITY_HOLD"));
  assert.equal(checkCandidate(candidate,source).mechanicalPreviewOnly,false);
});

test("L12 detects invalid Unicode, Android escapes, unchanged English and rich XML review",()=>{
  const source={sourceHash:"sha256:s",sourcePreview:"Hello %1$d",
    translatable:true,hasRichMarkup:false};
  const candidate={sourceHash:"sha256:s",sourcePreview:source.sourcePreview,
    targetExists:false,translation:"嗨 %1$d"};
  assert(checkCandidate(candidate,source).mechanicalPreviewOnly);
  assert(checkCandidate({...candidate,translation:"hi \\q %1$d"},source)
    .issues.includes("ANDROID_BACKSLASH_HOLD"));
  assert(checkCandidate({...candidate,translation:"hi \uD800 %1$d"},source)
    .issues.includes("ILLEGAL_UNICODE_HOLD"));
  assert(checkCandidate({...candidate,translation:source.sourcePreview},source)
    .issues.includes("UNCHANGED_ENGLISH_REVIEW"));
  assert(checkCandidate(candidate,{...source,hasRichMarkup:true})
    .issues.includes("RICH_XML_STRUCTURE_REVIEW"));
});

test("L12 rejects stale English hashes, existing locale definitions, self-issued approvals and catalogue drift",()=>{
  const f=fixtures(),normal=result().records[0];
  assert(normal);
  const source=f.matrix.rows.find(x=>x.key===normal.key),
    candidate={sourceHash:normal.sourceHash,sourcePreview:normal.sourcePreview,
      translation:normal.translation,targetExists:false};
  assert(checkCandidate({...candidate,sourceHash:"old"},source).issues.includes("SOURCE_STALE_HOLD"));
  assert(checkCandidate({...candidate,targetExists:true},source).issues.includes("EXISTING_XML_HOLD"));
  const forged={...f.l11,review:[{...f.l11.review[0],independentApproval:true},...f.l11.review.slice(1)]};
  assert.throws(()=>buildL12({l11:forged,graph:f.inputs.graph,
    matrix:f.matrix,glossary:f.glossary}),/refuses model-issued approval/);
  assert.throws(()=>buildL12({l11:{...f.l11,catalogueRevision:"changed"},
    graph:f.inputs.graph,matrix:f.matrix,glossary:f.glossary}),/catalogue revision conflict/);
});

test("L12 output never conflates remove, Snooze, Dismiss, Delete or private network access",()=>{
  assert(risksFor("settings_replace_alarms","Replace alarms").includes("DATA_LOSS_OR_RESTORE"));
  assert(risksFor("alarm_edit_remove_ringtone","Remove ringtone").includes("MEDIA_SOURCE_PLAYBACK"));
  assert(risksFor("stats_action_snoozed","Snoozed").includes("ALARM_FIRING_CONTROL"));
  assert(risksFor("settings_health_read_sleep","Read sleep sessions").includes("SECURITY_PRIVACY_PERMISSION"));
  const d=result(),ids=new Set(d.records.map(x=>x.key+"\0"+x.locale));
  assert.equal(ids.size,d.records.length);
});
