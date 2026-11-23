#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {mkdtempSync,symlinkSync,rmSync,readFileSync,existsSync} from "node:fs";
import {tmpdir} from "node:os";
import {join,resolve,dirname} from "node:path";
import {fileURLToPath} from "node:url";
import {
  createOperationalHandoff,buildHandoffSnapshot,writeOperationalHandoff
} from "./l12-b9b-authority-readiness.mjs";
import {readExternalProtectedKeys} from "./l12-b9-protected-policy-intake.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let cached;
function fixture(){
  if(cached)return cached;
  const {bundle,stage}=buildHandoffSnapshot("a".repeat(40));
  return cached={bundle,stage,prepared:createOperationalHandoff({
    packet:bundle.data.packet,riskPlan:bundle.riskPlan,policy:bundle.policy,
    glossary:bundle.data.glossary,g4Ledger:bundle.data.g4Ledger,
    matrix:bundle.data.matrix,stage,batchPlan:bundle.batchPlan
  })};
}
test("L12-9B handoff contains exact 3100 candidate scope, 1414 review, 1686 residual",()=>{
  const x=fixture(),m=x.prepared.report;
  assert.equal(m.schema,"acx-l12-b9b-authority-readiness/1");
  assert.equal(m.status,"BLOCKED_AWAITING_EXTERNAL_APPROVAL");
  assert.equal(m.fullLocaleRows,3100);
  assert.equal(m.mustReviewPerLocaleRows,1414);
  assert.equal(m.highRiskAndRecheckRows,1208);
  assert.equal(m.p3SampleRows,206);
  assert.equal(m.residualRowsRequiringSeparateRiskAcceptance,1686);
  assert.equal(m.stratumCount,12);
  assert.equal(m.mustReviewPerLocaleRows+m.residualRowsRequiringSeparateRiskAcceptance,3100);
  assert.match(m.reviewSetDigest,/^sha256:[0-9a-f]{64}$/);
  assert.match(m.policyDigest,/^sha256:[0-9a-f]{64}$/);
  assert.match(m.policySubjectDigest,/^sha256:[0-9a-f]{64}$/);
});
test("L12-9B worklist is source-bound per locale with no automatic approvals or common reviewer",()=>{
  const {prepared,bundle}=fixture(),keys=new Set();
  for(const row of prepared.reviewQueue){
    assert(row.key && ["zh-TW","ja"].includes(row.locale),row.key);
    assert(row.sourceHash && row.translationHash && row.reviewRevision);
    assert(["string","plurals","array"].includes(row.type));
    const id=row.key+"\0"+row.locale;
    assert(!keys.has(id),"Duplicate "+id);
    keys.add(id);
    assert.equal(row.status,"PENDING_REAL_INDEPENDENT_HUMAN_REVIEW");
    assert.equal(row.meaningChecked,false);
    assert.equal(row.callsiteContextChecked,false);
    assert.equal(row.androidFormatChecked,false);
    assert.equal(row.operationConsequencesChecked,false);
    assert.equal(row.reviewerId,null);
    assert.equal(row.reviewedAt,null);
    assert.equal(row.rationale,null);
    assert.equal(row.independentApproved,false);
    assert.equal(row.mayRelease,false);
    assert.equal(row.notSigned,true);
  }
  assert.equal(keys.size,1414);
  assert.equal(bundle.data.glossary.approval,"UNAPPROVED_FOR_RELEASE");
  assert.equal(bundle.data.g4Ledger.approvals.length,0);
});
test("L12-9B 12 unsigned feature-family dispositions never auto accept residual risk",()=>{
  const x=fixture(),s=x.prepared.strata;
  assert.equal(s.length,12);
  assert.equal(s.reduce((n,a)=>n+a.selectedRowCount,0),206);
  assert.equal(s.reduce((n,a)=>n+a.residualRowCount,0),1686);
  assert.equal(new Set(s.map(a=>a.stratumId)).size,12);
  for(const row of s){
    assert.equal(row.decision,null);
    assert.equal(row.unreviewedResidualExplicitlyAccepted,false);
    assert.equal(row.independentApproved,false);
    assert.equal(row.mayRelease,false);
    assert.equal(row.reviewerId,null);
    assert.match(row.selectedRowDigest,/^sha256:[a-f0-9]{64}$/);
    assert.match(row.residualRowDigest,/^sha256:[a-f0-9]{64}$/);
  }
});
test("L12-9B compiler handoff pins candidate and official XML digests but never asserts build",()=>{
  const x=fixture(),c=x.prepared.unsignedCompilerRequirements;
  assert.equal(c.status,"NO_COMPILE_ATTESTATION_NOT_PROTECTED");
  assert.equal(c.sourceSha,"a".repeat(40));
  assert.equal(c.reviewSetDigest,x.bundle.data.packet.reviewSetDigest);
  assert.equal(c.policyDigest,x.bundle.riskPlan.summary.policyDigest);
  assert.equal(Object.keys(c.expectedOfficialFilesSha256).length,2);
  assert.equal(c.independentApproved,false);
  assert.equal(c.mayRelease,false);
});
test("L12-9B preliminary invalid notes refuse all claimed review progress",()=>{
  const x=fixture();
  const prepared=createOperationalHandoff({
    packet:x.bundle.data.packet,riskPlan:x.bundle.riskPlan,
    policy:x.bundle.policy,glossary:x.bundle.data.glossary,
    g4Ledger:x.bundle.data.g4Ledger,matrix:x.bundle.data.matrix,stage:x.stage,
    preliminaryNotes:{schema:"forged-approved-packet/1",approved:true},
    batchPlan:x.bundle.batchPlan
  });
  assert.equal(prepared.report.unsignedNotesImported,true);
  assert.equal(prepared.report.unsignedNotesValid,false);
  assert.equal(prepared.report.verifiedIndependentApprovals,0);
  assert.equal(prepared.report.releasePermitted,false);
  assert(prepared.strata.every(s=>s.observedUnsignedNoteStatus==="INVALID_NOTES_REFUSED"));
});
test("L12-9B blocks output outside build and only writes unapproved handoff files",()=>{
  const x=fixture(),temp=mkdtempSync(join(ROOT,"build","l12b9b-handoff-"));
  try{
    assert.throws(()=>writeOperationalHandoff("/tmp/l12-b9b-untrusted-release",x.prepared),
      /confined to build/);
    writeOperationalHandoff(temp,x.prepared);
    for(const file of [
      "authorization-readiness.json","unsigned-policy-subject.json",
      "unsigned-row-review-queue.json","unsigned-stratum-risk-receipts.json",
      "unsigned-compiler-requirements.json","READ_ME_UNAPPROVED.txt"
    ])assert(existsSync(join(temp,file)),"missing "+file);
    const exported=JSON.parse(readFileSync(join(temp,"authorization-readiness.json"),"utf8"));
    assert.equal(exported.releasePermitted,false);
    assert.equal(exported.officialXmlWritten,0);
    assert.equal(exported.verifiedPolicyRiskAcceptances,0);
    assert.equal(exported.verifiedIndependentApprovals,0);
    assert(!existsSync(join(temp,"signed-policy.json")));
    assert(!existsSync(join(temp,"approved-locale.xml")));
  }finally{rmSync(temp,{recursive:true,force:true})}
});
test("L12-9B resolves external symlinks to block protected key aliasing back into repo",()=>{
  const outer=mkdtempSync(join(tmpdir(),"acx-l12b9b-key-link-"));
  try{
    const alias=join(outer,"policy.pem");
    symlinkSync(join(ROOT,"localization/governance/g4/review-ledger.json"),alias);
    const env={ACX_L12_B9_POLICY_PUBLIC_KEY_FILE:alias};
    assert.throws(()=>readExternalProtectedKeys(env,ROOT),
      /PROTECTED_POLICY_KEY_PATH_INSIDE_REPOSITORY/);
    assert.throws(()=>readExternalProtectedKeys({},ROOT),
      /PROTECTED_POLICY_KEY_PATH_REQUIRED/);
  }finally{rmSync(outer,{recursive:true,force:true})}
});
test("L12-9B has no release permissions, full Android resources or signing code",()=>{
  const p=fixture().prepared;
  assert.equal(p.report.releasePermitted,false);
  assert.equal(p.report.verifiedIndependentApprovals,0);
  assert.equal(p.report.verifiedPolicyRiskAcceptances,0);
  assert.equal(p.report.officialXmlWritten,0);
  assert.equal(p.report.productionApkReleased,0);
  const source=readFileSync(join(ROOT,
    "scripts/localization/l12-b9b-authority-readiness.mjs"),"utf8");
  assert(!/\bsign\s*\(\s*null\s*,/.test(source));
  assert(!source.includes("writeAuthorizedXml("));
  assert(!source.includes("privateKey"));
});
