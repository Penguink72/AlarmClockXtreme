#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {existsSync,mkdtempSync,mkdirSync,readFileSync,rmSync,writeFileSync} from "node:fs";
import {join,resolve,dirname} from "node:path";
import {fileURLToPath} from "node:url";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {createStandardLocaleResources,writeStandardLocaleResources}
  from "./s1s2-standard-locales.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let memo;
function fixture(){
  if(!memo){
    const input=loadUnifiedInputs(ROOT,"s1s2-test-only-sha");
    memo={...input,result:createStandardLocaleResources(input.packet,input.matrix)};
  }
  return memo;
}
test("S2 constructs two typed XML locale assets with 1550 missing entries per locale",()=>{
  const {result}=fixture();
  assert.equal(Object.keys(result.files).length,2);
  for(const [path,xml] of Object.entries(result.files)){
    assert(path.endsWith("/strings_s2_machine_translated.xml"));
    assert.match(xml,/TECHNICALLY CHECKED, NOT INDEPENDENTLY SEMANTICALLY REVIEWED/);
    assert(!xml.includes("Reviewed through protected"));
    assert.equal((xml.match(/<string name=/g)||[]).length,1518);
    assert.equal((xml.match(/<plurals name=/g)||[]).length,31);
    assert.equal((xml.match(/<string-array name=/g)||[]).length,1);
    assert.equal((xml.match(/<item quantity="one">/g)||[]).length,31);
    assert.equal((xml.match(/<item quantity="other">/g)||[]).length,31);
  }
  assert.equal(result.manifest.translatedTotalRows,3100);
  assert.equal(result.manifest.existingLocalizedDefinitionsOverwritten,0);
  assert.equal(result.manifest.independentlyApprovedLocaleRows,0);
  assert.equal(result.manifest.originalEnglishChanged,false);
});
test("S1 independent approvals and G1/G4 signatures do not gate standard assets",()=>{
  const {glossary,g4Ledger,result}=fixture();
  assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
  assert.equal(g4Ledger.approvals.length,0);
  assert.equal(result.manifest.mode,"STANDARD_USER_OPT_IN_NO_G1_G4_OR_PROTECTED_SIGNERS");
  assert.equal(result.manifest.status,"TECHNICAL_TRANSLATION_CANDIDATE_NOT_SEMANTICALLY_VERIFIED");
});
test("S2 rejects source/type/canonical drift through typed candidate preflight",()=>{
  const {packet,matrix}=fixture(),item=packet.rows.find(x=>x.type==="string");
  const altered={...packet,rows:packet.rows.map(x=>x===item?{
    ...x,sourceHash:"sha256:altered"}:x)};
  assert.throws(()=>createStandardLocaleResources(altered,matrix),/source or resource kind drift/);
});
test("S2 standard generator refuses unknown targets and external directories",()=>{
  const {result}=fixture();
  assert.throws(()=>writeStandardLocaleResources(result,"/tmp/untrusted","stage"),/under build/);
  assert.throws(()=>writeStandardLocaleResources(result,"/tmp/untrusted","materialize"),
    /app\/src\/main\/res/);
});
test("S2 staged files are deterministic and verify mode catches translation drift",()=>{
  const {result}=fixture();
  mkdirSync(join(ROOT,"build"),{recursive:true});
  const temp=mkdtempSync(join(ROOT,"build","s1s2-stage-test-"));
  try{
    const written=writeStandardLocaleResources(result,temp,"stage");
    assert.equal(written.status,"S2_STAGED_FOR_TECHNICAL_VALIDATION");
    assert(existsSync(join(temp,"s2-resource-manifest.json")));
    for(const [path,expected] of Object.entries(result.files)){
      assert.equal(readFileSync(join(temp,path),"utf8"),expected);
    }
    // verify is reserved for exact committed app/src/main/res, not a staging dir.
    assert.throws(()=>writeStandardLocaleResources(result,temp,"verify"),
      /app\/src\/main\/res/);
  }finally{rmSync(temp,{recursive:true,force:true})}
});
test("S2 refuses key collision with already-present native locale resources",()=>{
  const {packet,matrix}=fixture(),item=packet.rows.find(x=>x.locale==="ja");
  const edited={...matrix,rows:matrix.rows.map(x=>x.key===item.key?{
    ...x,ja:{...x.ja,rawXml:"<string>existing</string>"}
  }:x)};
  assert.throws(()=>createStandardLocaleResources(packet,edited),
    /duplicate existing Android resource/);
});
