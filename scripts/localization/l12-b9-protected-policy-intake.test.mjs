#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {createHash,generateKeyPairSync,sign} from "node:crypto";
import {readFileSync} from "node:fs";
import {resolve,dirname,join} from "node:path";
import {fileURLToPath} from "node:url";
import {loadRiskPlan} from "./l12-b8b-risk-tiered-policy.mjs";
import {buildTypedResourceStage,releaseViolations} from "./l13-b-protected-xml-writer.mjs";
import {validateProtectedTieredRelease,policyApprovalSubject,compilerProofSubject,
  makeUnsignedReviewRequirements,readExternalProtectedKeys}
  from "./l12-b9-protected-policy-intake.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const sha=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x),"utf8").digest("hex");
const at="2026-11-23T00:00:00.000Z";
const roles=()=>["policy","reviewer","compiler"].map(()=>{
  const pair=generateKeyPairSync("ed25519");
  return {privateKey:pair.privateKey,pem:pair.publicKey.export({type:"spki",format:"pem"})};
});
const signed=(schema,payload,role)=>({
  schema,payload,signatureBase64:sign(null,Buffer.from(JSON.stringify(payload),"utf8"),
    role.privateKey).toString("base64")
});
let fixture;
function f(){
  if(fixture)return fixture;
  const raw=loadRiskPlan(ROOT,"a".repeat(40));
  const stage=buildTypedResourceStage(raw.data.packet,raw.data.matrix);
  const glossary={...raw.data.glossary,approval:"APPROVED_FOR_RELEASE"};
  const g4Ledger={...raw.data.g4Ledger,approvals:[{
    testOnly:true,note:"Synthetic G4 fixture is NOT an external human approval"
  }]};
  const authority=roles(),[policyRole,reviewerRole,compilerRole]=authority;
  const context={packet:raw.data.packet,riskPlan:raw.riskPlan,policy:raw.policy,
    glossary,g4Ledger,stage,protectedRef:true,
    trustedPolicyKeyPem:policyRole.pem,trustedReviewerKeyPem:reviewerRole.pem,
    trustedCompilerKeyPem:compilerRole.pem};
  const subject=policyApprovalSubject(context);
  const policyEnvelope=signed("acx-l12-b9-signed-policy-envelope/1",subject,policyRole);
  const requirements=makeUnsignedReviewRequirements(context);
  const approvals=requirements.rowReviewRequirements.map(r=>({
    key:r.key,locale:r.locale,type:r.type,
    sourceHash:r.sourceHash,translationHash:r.translationHash,
    reviewRevision:r.reviewRevision,decision:"APPROVED",
    reviewMode:"human-independent-semantic-review",
    meaningVerified:true,contextVerified:true,formatVerified:true,
    operationConsequencesVerified:true,reviewerId:"SYNTHETIC_TEST_ONLY",
    reviewNotes:"Synthetic test fixture, not real independent semantic review.",
    reviewedAt:at
  }));
  const stratumReceipts=requirements.stratumReviewRequirements.map(s=>({
    stratumId:s.stratumId,
    reviewSetDigest:context.packet.reviewSetDigest,
    policyDigest:subject.policyDigest,
    sampledKeysDigest:s.sampledKeysDigest,
    residualKeysDigest:s.residualKeysDigest,
    selectedRowDigest:s.selectedRowDigest,
    residualRowDigest:s.residualRowDigest,
    selectedRowCount:s.selectedRowCount,
    residualRowCount:s.residualRowCount,
    decision:s.residualRowCount?"ACCEPT_RESIDUAL_UNDER_PROTECTED_POLICY":
      "ALL_KEYS_INDEPENDENTLY_REVIEWED",
    unreviewedResidualExplicitlyAccepted:s.residualRowCount>0,
    reviewerId:"SYNTHETIC_TEST_ONLY",
    reviewedAt:at,
    acceptanceReason:"Synthetic protected reviewer explicitly accepts diagnostic-only residual risk for the purpose of test coverage; not actual language approval."
  }));
  const reviewPayload={
    policyEnvelopeDigest:sha(policyEnvelope),
    reviewSetDigest:context.packet.reviewSetDigest,
    policyDigest:subject.policyDigest,
    catalogueRevision:context.packet.catalogueRevision,
    glossaryRevision:context.packet.glossaryRevision,
    g1GlossaryDigest:subject.g1GlossaryDigest,
    g4LedgerDigest:subject.g4LedgerDigest,
    reviewPolicy:"INDEPENDENT_TIERED_SEMANTIC_AND_RESIDUAL_RISK_REVIEW",
    approvals,stratumReceipts
  };
  const tieredReviewEnvelope=signed(
    "acx-l12-b9-signed-tiered-review-envelope/1",reviewPayload,reviewerRole);
  const compilation=compilerProofSubject({stage,packet:context.packet,
    policySubject:subject,policyEnvelope,reviewEnvelope:tieredReviewEnvelope});
  const compilerEvidence={
    schema:"acx-l12-b9-protected-compiler-evidence/1",
    status:"PASSED",repository:"Penguink72/AlarmClockXtreme",
    protectedRef:true,gitCommitSha:context.packet.sourceSha,
    issuedAt:at,compiledResourceZipSha256:"sha256:"+"f".repeat(64),
    workflowRunUrl:"https://github.com/Penguink72/AlarmClockXtreme/actions/runs/123",
    gradleTasks:[":app:processFdroidDebugResources",":app:compileFdroidDebugKotlin"],
    ...compilation
  };
  const compilerEnvelope=signed(
    "acx-l12-b9-signed-compiler-envelope/1",compilerEvidence,compilerRole);
  return fixture={context,requirements,authority,
    envelopes:{policyEnvelope,tieredReviewEnvelope,compilerEnvelope},
    payloads:{subject,reviewPayload,compilerEvidence}};
}
const verify=(changes={})=>{
  const x=f();
  return validateProtectedTieredRelease({...x.context,...x.envelopes,...changes});
};
test("L12-9 unsigned requirements preserve risk distribution and never approve",()=>{
  const x=f(),r=x.requirements,s=x.context.riskPlan.summary;
  assert.equal(r.status,"REVIEW_REQUIRED_NOT_AN_AUTHORIZATION");
  assert.equal(r.independentApproved,0);
  assert.equal(r.requiredIndividualLocaleRows,s.initialHumanFocusLocaleRows);
  assert.equal(r.nonSampledResidualLocaleRows,s.deferredLocaleRows);
  assert.equal(r.requiredIndividualLocaleRows,1414);
  assert.equal(r.nonSampledResidualLocaleRows,1686);
  assert.equal(r.rowReviewRequirements.length,1414);
  assert.equal(r.stratumReviewRequirements.length,12);
  assert(r.rowReviewRequirements.every(z=>z.independentApproved===false));
  assert.equal(x.context.policy.status,"PROPOSED_NOT_AUTHORIZED");
});
test("L12-9 synthetic three-separate-key positive proves verification mechanics only",()=>{
  const x=f(),out=verify();
  assert.equal(out.status,"PROTECTED_TIERED_POLICY_EVIDENCE_VALID");
  assert.deepEqual(out.errors,[]);
  assert.equal(out.officialRows,3100);
  assert.equal(out.directlyApprovedRows,1414);
  assert.equal(out.policyRiskAcceptedRows,1686);
  // Synthetic keys and G1/G4 approvals exist only in memory of this test;
  // the repository remains at 0 actual independent reviews and no release.
  const reasons=releaseViolations({...x.context,...x.envelopes,
    reviewMode:"risk-tiered-policy"});
  assert.deepEqual(reasons,[]);
});
test("L12-9 ordinary repository remains blocked without keys or G1/G4 decisions",()=>{
  const x=f(),raw=loadRiskPlan(ROOT,"a".repeat(40));
  const r=verify({glossary:raw.data.glossary,g4Ledger:raw.data.g4Ledger,
    trustedPolicyKeyPem:"",trustedReviewerKeyPem:"",trustedCompilerKeyPem:""});
  assert.equal(r.status,"BLOCKED");
  assert(r.errors.includes("G1_GLOSSARY_NOT_INDEPENDENTLY_APPROVED"));
  assert(r.errors.includes("G4_APPROVAL_LEDGER_EMPTY"));
  assert(r.errors.includes("POLICY_PROTECTED_KEY_MISSING"));
  assert(r.errors.includes("REVIEWER_PROTECTED_KEY_MISSING"));
  assert(r.errors.includes("COMPILER_PROTECTED_KEY_MISSING"));
  assert.equal(r.officialRows,0);
  assert.throws(()=>readExternalProtectedKeys({},ROOT),/PROTECTED_POLICY_KEY_PATH_REQUIRED/);
});
test("L12-9 never accepts unprotected refs or signature/key substitution",()=>{
  const x=f();
  assert(verify({protectedRef:false}).errors.includes("GITHUB_PROTECTED_REF_REQUIRED"));
  assert(verify({trustedPolicyKeyPem:x.authority[1].pem}).errors.some(e=>
    /POLICY_SIGNATURE/.test(e)));
  assert(verify({trustedReviewerKeyPem:x.authority[0].pem}).errors.some(e=>
    /REVIEWER_SIGNATURE/.test(e)));
  assert(verify({trustedCompilerKeyPem:x.authority[0].pem}).errors.some(e=>
    /COMPILER_SIGNATURE/.test(e)));
  assert(verify({trustedPolicyKeyPem:x.authority[1].pem,
    trustedReviewerKeyPem:x.authority[1].pem}).errors.includes(
      "PROTECTED_AUTHORITY_KEYS_MUST_BE_DISTINCT"));
});
test("L12-9 policy drift or forged subject fails even with signed review packets",()=>{
  const x=f();
  const moved=structuredClone(x.context.policy);
  moved.policyRevision="l12-b9-proposal-modified";
  assert(verify({policy:moved}).errors.includes(
    "POLICY_AUTHORIZATION_NOT_BOUND_TO_CURRENT_SOURCE_OR_GOVERNANCE"));
  const forged=structuredClone(x.envelopes.policyEnvelope);
  forged.payload.policyRevision="SELF_APPROVED";
  assert(verify({policyEnvelope:forged}).errors.some(e=>/POLICY_SIGNATURE/.test(e)));
  const stale=structuredClone(x.envelopes.tieredReviewEnvelope);
  stale.payload.reviewSetDigest="sha256:"+"0".repeat(64);
  assert(verify({tieredReviewEnvelope:stale}).errors.includes(
    "REVIEW_ENVELOPE_NOT_BOUND_TO_POLICY_OR_GOVERNANCE"));
});
test("L12-9 signed semantic review requires exact per-key and per-locale evidence",()=>{
  const x=f(),p=structuredClone(x.payloads.reviewPayload);
  p.approvals=p.approvals.slice(1);
  const env=signed("acx-l12-b9-signed-tiered-review-envelope/1",p,x.authority[1]);
  const r=verify({tieredReviewEnvelope:env});
  assert.equal(r.status,"BLOCKED");
  assert(r.errors.some(e=>/MANDATORY_RISK_ROW_NOT_INDEPENDENTLY_REVIEWED|SAMPLE_LOCALE_ROW_NOT_INDEPENDENTLY_REVIEWED/.test(e)));
  assert(r.errors.includes("PROTECTED_COMPILER_ATTESTATION_MISSING_INVALID_OR_STALE"));
  const bad=structuredClone(x.payloads.reviewPayload);
  bad.approvals.push({...bad.approvals[0]});
  const dup=signed("acx-l12-b9-signed-tiered-review-envelope/1",bad,x.authority[1]);
  assert(verify({tieredReviewEnvelope:dup}).errors.includes("DUPLICATE_REVIEW_APPROVAL"));
  const wrong=structuredClone(x.payloads.reviewPayload);
  wrong.approvals[0].meaningVerified=false;
  const invalid=signed("acx-l12-b9-signed-tiered-review-envelope/1",wrong,x.authority[1]);
  assert(verify({tieredReviewEnvelope:invalid}).errors.includes("UNVERIFIED_ROW_REVIEW_OR_VERSION_DRIFT"));
});
test("L12-9 residual acceptances must bind selected rows and explicitly acknowledge unreviewed population",()=>{
  const x=f(),bad=structuredClone(x.payloads.reviewPayload);
  bad.stratumReceipts[0].residualKeysDigest="sha256:"+"f".repeat(64);
  const env=signed("acx-l12-b9-signed-tiered-review-envelope/1",bad,x.authority[1]);
  assert(verify({tieredReviewEnvelope:env}).errors.includes(
    "STRATUM_PROVENANCE_OR_ACCOUNTABILITY_MISMATCH"));
  const no=structuredClone(x.payloads.reviewPayload);
  no.stratumReceipts[0].unreviewedResidualExplicitlyAccepted=false;
  const deny=signed("acx-l12-b9-signed-tiered-review-envelope/1",no,x.authority[1]);
  assert(verify({tieredReviewEnvelope:deny}).errors.includes("RESIDUAL_RISK_NOT_EXPLICITLY_ACKNOWLEDGED"));
  const missing=structuredClone(x.payloads.reviewPayload);
  missing.stratumReceipts.pop();
  const miss=signed("acx-l12-b9-signed-tiered-review-envelope/1",missing,x.authority[1]);
  assert(verify({tieredReviewEnvelope:miss}).errors.includes("INVALID_APPROVAL_OR_STRATUM_COUNT"));
});
test("L12-9 an escalated stratum cannot use sampling to bypass full per-locale review",()=>{
  const x=f(),bad=structuredClone(x.payloads.reviewPayload);
  const s=bad.stratumReceipts.find(r=>r.residualRowCount>0);
  assert(s);
  s.decision="ESCALATED_FULL_STRATUM_REVIEW";
  s.unreviewedResidualExplicitlyAccepted=false;
  const invalid=signed("acx-l12-b9-signed-tiered-review-envelope/1",bad,x.authority[1]);
  assert(verify({tieredReviewEnvelope:invalid}).errors.includes(
    "ESCALATED_STRATUM_REQUIRES_FULL_ROW_REVIEW"));
});
test("L12-9 compiler evidence is independently signed and binds exact official bytes",()=>{
  const x=f();
  const altered=structuredClone(x.envelopes.compilerEnvelope);
  altered.payload.officialFilesSha256={
    ...altered.payload.officialFilesSha256,
    [Object.keys(altered.payload.officialFilesSha256)[0]]:"sha256:"+"1".repeat(64)
  };
  assert(verify({compilerEnvelope:altered}).errors.some(e=>/COMPILER_SIGNATURE/.test(e)));
  const forged=structuredClone(x.payloads.compilerEvidence);
  forged.gradleTasks=[":app:processFdroidDebugResources"];
  const signedWrong=signed("acx-l12-b9-signed-compiler-envelope/1",forged,x.authority[2]);
  assert(verify({compilerEnvelope:signedWrong}).errors.includes(
    "PROTECTED_COMPILER_ATTESTATION_MISSING_INVALID_OR_STALE"));
  const missing=verify({compilerEnvelope:null});
  assert(missing.errors.includes("COMPILER_ENVELOPE_INVALID"));
  assert(missing.errors.includes("PROTECTED_COMPILER_ATTESTATION_MISSING_INVALID_OR_STALE"));
});
test("L12-9 original L13-B full-per-row gate remains unchanged and strict",()=>{
  const x=f();
  const r=releaseViolations({...x.context,...x.envelopes,reviewMode:"full-per-row",
    envelope:null,trustedKeyPem:null,compilerAttestation:null});
  assert(r.includes("NO_SIGNED_EXTERNAL_REVIEW_ENVELOPE"));
  assert(r.includes("L13_B_AAPT2_EVIDENCE_MISSING_OR_STALE"));
  assert(releaseViolations({...x.context,...x.envelopes,
    reviewMode:"unknown"})[0]==="UNKNOWN_L13_B_REVIEW_MODE");
});
