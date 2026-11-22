#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
import {buildStagingResources,triageWarnings,makeWarningTsv}
  from "./l12-b4-aapt2-staging.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
function fixture(){
  if(cached)return cached;
  const i=loadL11Inputs(ROOT,"L12-B4-TEST-SHA"),l11=buildL11(i),matrix=generateMatrix(ROOT),
    read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8")),
    glossary=read("localization/governance/g1/glossary-contract.json"),
    g4Ledger=read("localization/governance/g4/review-ledger.json"),
    reviewDecisions=read("localization/governance/l12/independent-review-ledger.json"),
    l12=buildL12({l11,graph:i.graph,matrix,glossary});
  return cached=stageSourceRows({l11,l12,matrix,reviewDecisions,glossary,g4Ledger});
}
test("L12-B4 stages exactly 2986 review-only locale rows, not approved App resources",()=>{
  const x=buildStagingResources(fixture());
  assert.deepEqual(Object.keys(x.files).sort(),[
    "values-b+zh+Hant+TW/strings.xml","values-ja/strings.xml"]);
  assert.equal(x.manifest.candidatesByLocale["zh-TW"],1493);
  assert.equal(x.manifest.candidatesByLocale.ja,1493);
  assert(x.manifest.fileSha256["values-ja/strings.xml"].startsWith("sha256:"));
  assert.equal(x.manifest.independentSemanticApproval,false);
  assert.equal(x.manifest.sourceStringsXmlMutated,false);
  assert.equal(x.manifest.fullAndroidResourcesCompiled,false);
});
test("L12-B4 emitted XML contains single resources root and structurally delimited rows",()=>{
  const x=buildStagingResources(fixture());
  for(const content of Object.values(x.files)){
    assert.equal((content.match(/<resources>/g)||[]).length,1);
    assert.equal((content.match(/<\/resources>/g)||[]).length,1);
    assert.equal((content.match(/<string name=/g)||[]).length,1493);
    assert(!content.includes("<script>"));
    assert(content.includes("NEVER COPY TO PRODUCTION"));
  }
});
test("L12-B4 flags all unresolved English/percent warnings without machine approval",()=>{
  const x=triageWarnings(fixture());
  assert.equal(x.stats.unchangedEnglish,29);
  assert.equal(x.stats.percent,12);
  assert.equal(x.stats.authorizedDecisions,0);
  assert(x.stats.rows>=32);
  assert(x.rows.every(r=>r.suggestedDisposition==="HUMAN_REVIEW_REQUIRED_NOT_APPROVED"));
  assert(x.rows.every(r=>r.compilerPassWillNotDismissWarning===true));
  assert(x.rows.every(r=>r.reviewRevision.startsWith("sha256:")));
  assert.match(makeWarningTsv(x),/review_revision/);
});
test("L12-B4 staging fails closed on mismatched source version, unexpected locale or duplicate key",()=>{
  const f=fixture();
  assert.throws(()=>buildStagingResources({...f,stats:{...f.stats,structuralHardHolds:1}}),
    /hard structural holds/);
  assert.throws(()=>buildStagingResources({...f,stats:{...f.stats,androidArgumentMismatches:1}}),
    /source or positional format drift/);
  const dup={...f,review:[f.review[0],...f.review],stats:{...f.stats,candidateLocaleRows:f.stats.candidateLocaleRows+1}};
  assert.throws(()=>buildStagingResources(dup),/Duplicate resource/);
  const unknown={...f,review:[{...f.review[0],locale:"es"},...f.review.slice(1)]};
  assert.throws(()=>buildStagingResources(unknown),/Unsupported stage locale/);
});
test("L12-B4 rejects approval forgery and does not stage strings with changed authority fields",()=>{
  const f=fixture();
  assert.throws(()=>buildStagingResources({...f,review:[
    {...f.review[0],independentApproval:true},...f.review.slice(1)
  ]}),/cannot stage authoritatively approved/);
  assert.throws(()=>buildStagingResources({...f,review:[
    {...f.review[0],mayWriteOfficialXml:true},...f.review.slice(1)
  ]}),/cannot stage authoritatively approved/);
});
test("L12-B4 emits deterministic source SHA for candidate review staging",()=>{
  const x=buildStagingResources(fixture()),y=buildStagingResources(fixture());
  assert.deepEqual(x.manifest.fileSha256,y.manifest.fileSha256);
  assert.deepEqual(triageWarnings(fixture()),triageWarnings(fixture()));
});
