#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {PROBES,inspectBehavior,evidenceTsv,evidenceSummary}
  from "./l12-b2-code-behavior-probes.mjs";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cache;
function fixtures(){
  if(cache)return cache;
  const inp=loadL11Inputs(root,"l12-b2-test-same-sha"),
    l11=buildL11(inp),matrix=generateMatrix(root),
    glossary=JSON.parse(readFileSync(join(root,"localization/governance/g1/glossary-contract.json"),"utf8"));
  return cache={matrix,l12:buildL12({l11,graph:inp.graph,matrix,glossary})};
}
function report(){const f=fixtures();return inspectBehavior(root,f.l12,f.matrix);}

test("L12-B2 checks all concrete operation source anchors and preserves release blockade",()=>{
  const r=report();
  assert.equal(r.totals.probes,14);
  assert.equal(r.totals.officialReleaseEligible,0);
  assert.equal(r.totals.independentSemanticApprovals,0);
  assert.equal(r.totals.runtimeConfirmed,0);
  assert.equal(r.totals.unresolvedBehaviorProof,r.totals.probes);
  assert(r.cases.every(x=>x.anchorEvidence.length>=1));
  assert(r.cases.every(x=>x.anchorEvidence.every(e=>e.line>0)));
  assert(r.cases.every(x=>x.fileRevision.startsWith("sha256:")));
  assert(r.localeEvidence.every(x=>x.independentApproval===false&&x.runtimeVerified===false));
  assert(r.localeEvidence.every(x=>x.sourceHash.startsWith("sha256:")));
  assert(r.localeEvidence.every(x=>x.reviewRevision.startsWith("sha256:")));
  assert.match(evidenceTsv(r),/translation_hash/);
  assert.match(evidenceSummary(r),/L12-2/);
});

test("L12-B2 verifies key distinctions from actual code, not lexical English guesses",()=>{
  const r=report();
  const get=id=>r.cases.find(x=>x.id===id);
  for(const id of ["BACKUP_APPEND_VS_REPLACE_UI","BACKUP_REPLACE_MUTATIONS",
    "BACKUP_UNTRUSTED_INTEGRATIONS","ALARM_SNOOZE_BEFORE_AUDIO_STOP",
    "ALARM_DISMISS_EVENT","MEDIA_SOURCE_REMOVE_STORE",
    "MEDIA_SOURCE_REMOVE_VIEWMODEL","PLAYLIST_REMOVE_ITEMS",
    "WEBHOOK_EVENTS_AND_HTTPS","HUE_FIRST_USE_PINNING",
    "STATISTICS_EVENT_TABLE"]){
    assert(get(id),id);
    assert.equal(get(id).evidenceStatus,"STATIC_CODE_PATH_EVIDENCE_ONLY");
    assert.notEqual(get(id).claim,get(id).limitation);
    assert(get(id).sourceKeys.every(s=>s.sourceHash.startsWith("sha256:")));
  }
  const media=get("MEDIA_SOURCE_REMOVE_STORE");
  assert.match(media.limitation,/not file deletion/);
  assert(get("ALARM_SNOOZE_BEFORE_AUDIO_STOP").anchorEvidence
    .map(x=>x.line).every((n,i,a)=>i===0||n>=a[i-1]));
  assert(get("STATISTICS_EVENT_TABLE").anchorEvidence.some(x=>x.token.includes("alarm_events")));
  assert(!r.localeEvidence.some(x=>x.semanticCorrectnessApproved));
});

test("L12-B2 aborts if a real semantic operation anchor disappears or gets reordered",()=>{
  const f=fixtures();
  const base=PROBES.find(x=>x.id==="MEDIA_SOURCE_REMOVE_STORE");
  const src=readFileSync(join(root,base.path),"utf8");
  const mutated=src.replace("releasePersistableUriPermission(","releasePersistableUriPermissionBROKEN(");
  assert.notEqual(src,mutated);
  assert.throws(()=>inspectBehavior(root,f.l12,f.matrix,[base],()=>mutated),
    /Missing or out-of-order L12-2 source anchor/);
  const reordered={...base,anchors:[...base.anchors].reverse()};
  assert.throws(()=>inspectBehavior(root,f.l12,f.matrix,[reordered],()=>src),
    /Missing or out-of-order L12-2 source anchor/);
});

test("L12-B2 refuses unrelated or unversioned source/candidate and out-of-repo paths",()=>{
  const f=fixtures();
  assert.throws(()=>inspectBehavior(root,{...f.l12,catalogueRevision:"invalid"},f.matrix),
    /source versions disagree/);
  const bad={...PROBES[0],path:"../../secrets/file.kt"};
  assert.throws(()=>inspectBehavior(root,f.l12,f.matrix,[bad]),/Invalid probe contract/);
  const missing={...PROBES[0],keys:["unknown_bad_resource_key"]};
  assert.throws(()=>inspectBehavior(root,f.l12,f.matrix,[missing]),/unknown source key/);
});

test("L12-B2 source-only existing-XML probes are not misreported as new L11 translations",()=>{
  const r=report();
  const sources=r.cases.filter(c=>/MEDIA_SOURCE_REMOVE|PLAYLIST_REMOVE/.test(c.id));
  assert(sources.length>=3);
  for(const item of sources){
    for(const key of item.sourceKeys){
      assert(key.sourceHash.startsWith("sha256:"));
      for(const locale of key.availableLocaleDrafts)
        assert(r.localeEvidence.some(x=>x.probeId===item.id&&x.locale===locale));
    }
  }
  assert(r.warnings.some(s=>s.includes("distinct objects")));
});
