#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {buildTypedResourceStage,releaseViolations,writeAuthorizedXml}
  from "./l13-b-protected-xml-writer.mjs";
let fixture;
const get=()=>fixture??=loadUnifiedInputs(undefined,"L13-B-TEST-SOURCE");
test("L13-B stages exact 3100 locale rows, preserving all Android resource types",()=>{
  const x=get(),r=buildTypedResourceStage(x.packet,x.matrix);
  assert.equal(r.manifest.rowsPerLocale,1550);
  assert.equal(r.manifest.independentApproved,0);
  assert.equal(r.manifest.existingLocaleDefinitionsOverwritten,0);
  assert.equal(Object.keys(r.files).length,2);
  for(const f of Object.values(r.files)){
    assert.equal((f.match(/<string name=/g)||[]).length,1518);
    assert.equal((f.match(/<plurals name=/g)||[]).length,31);
    assert.equal((f.match(/<string-array name=/g)||[]).length,1);
    assert.equal((f.match(/<item quantity="one">/g)||[]).length,31);
    assert.equal((f.match(/<item quantity="other">/g)||[]).length,31);
    assert.match(f,/MODEL UNREVIEWED/);
  }
});
test("L13-B blocks duplicate existing locale definition and drift",()=>{
  const x=get(),packet=x.packet;
  const r=packet.rows.find(row=>row.locale==="ja");
  const bad={...x.matrix,rows:x.matrix.rows.map(v=>v.key===r.key?
    {...v,ja:{...v.ja,rawXml:"already present"}}:v)};
  assert.throws(()=>buildTypedResourceStage(packet,bad),/duplicate existing Android resource/);
  const edited={...packet,rows:packet.rows.map(v=>v===r?
    {...v,sourceHash:"sha256:tampered"}:v)};
  assert.throws(()=>buildTypedResourceStage(edited,x.matrix),/source or resource kind drift/);
});
test("L13-B fails closed for missing protected reviewer and full compile attestation",()=>{
  const x=get(),stage=buildTypedResourceStage(x.packet,x.matrix);
  const reasons=releaseViolations({stage,packet:x.packet,envelope:null,
    trustedKeyPem:null,glossary:x.glossary,g4Ledger:x.g4Ledger,
    compilerAttestation:null,protectedRef:false});
  assert(reasons.includes("GITHUB_PROTECTED_REF_REQUIRED"));
  assert(reasons.includes("NO_SIGNED_EXTERNAL_REVIEW_ENVELOPE"));
  assert(reasons.includes("L13_B_AAPT2_EVIDENCE_MISSING_OR_STALE"));
});
test("L13-B does not accept a fabricated compiler zip digest",()=>{
  const x=get(),stage=buildTypedResourceStage(x.packet,x.matrix);
  const proof={schema:"acx-l13-b-protected-compiler-evidence/1",status:"PASSED",
    sourceSha:x.packet.sourceSha,reviewSetDigest:x.packet.reviewSetDigest,
    stagedFilesSha256:stage.manifest.stagedFilesSha256,
    compiledResourceZipSha256:"sha256:INVALID"};
  const reasons=releaseViolations({stage,packet:x.packet,envelope:null,
    trustedKeyPem:null,glossary:x.glossary,g4Ledger:x.g4Ledger,
    compilerAttestation:proof,protectedRef:true});
  assert(reasons.includes("L13_B_AAPT2_EVIDENCE_MISSING_OR_STALE"));
});
test("L13-B cannot write official XML in an ordinary unapproved run",()=>{
  const x=get(),stage=buildTypedResourceStage(x.packet,x.matrix);
  assert.throws(()=>writeAuthorizedXml({stage,packet:x.packet,
    envelope:null,trustedKeyPem:null,glossary:x.glossary,
    g4Ledger:x.g4Ledger,compilerAttestation:null,protectedRef:false}),
    /official writer BLOCKED/);
});
