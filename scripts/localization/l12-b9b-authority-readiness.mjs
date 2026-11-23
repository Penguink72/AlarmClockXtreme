#!/usr/bin/env node
/**
 * L12-9 continuation: operational handoff to independent human authorities.
 *
 * This is a fully UNTRUSTED, UNSIGNED staging tool. It NEVER signs data,
 * produces an APPROVED decision, or touches Android production resources.
 * It prepares exact per-locale review obligations and non-signing examples.
 */
import assert from "node:assert/strict";
import {readFileSync,writeFileSync,mkdirSync} from "node:fs";
import {createHash} from "node:crypto";
import {resolve,join,dirname} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {loadRiskPlan,evaluateProvisionalNotes} from "./l12-b8b-risk-tiered-policy.mjs";
import {makeUnsignedReviewRequirements,compilerProofSubject}
  from "./l12-b9-protected-policy-intake.mjs";
import {buildTypedResourceStage} from "./l13-b-protected-xml-writer.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const digest=value=>"sha256:"+createHash("sha256").update(JSON.stringify(value),"utf8").digest("hex");
const PREPARATION_SCHEMA="acx-l12-b9b-authority-readiness/1";
const PENDING="PENDING_REAL_INDEPENDENT_HUMAN_REVIEW";
const NO_DECISION="NO_REAL_AUTHORITY_GRANTED";

export function createOperationalHandoff({packet,riskPlan,policy,glossary,g4Ledger,matrix,stage,
  preliminaryNotes=null,batchPlan=null}){
  assert.equal(packet.schema,"acx-l12-b6-independent-review-packet/1");
  assert.equal(packet.rows.length,3100);
  assert.equal(riskPlan.summary.mandatoryFullLocaleRows,1208);
  assert.equal(riskPlan.summary.sampleLocaleRows,206);
  assert.equal(riskPlan.summary.deferredLocaleRows,1686);
  assert.equal(policy.status,"PROPOSED_NOT_AUTHORIZED");
  assert.equal(policy.authorization,"NONE_NO_POLICY_RELEASE_AUTHORITY");
  const work=makeUnsignedReviewRequirements({packet,riskPlan,policy,glossary,g4Ledger});
  assert.equal(work.requiredIndividualLocaleRows,1414);
  assert.equal(work.nonSampledResidualLocaleRows,1686);
  const rows=work.rowReviewRequirements;
  const inputSet=new Set(packet.rows.map(r=>r.key+"\0"+r.locale));
  assert.equal(inputSet.size,3100);
  assert.equal(new Set(rows.map(r=>r.key+"\0"+r.locale)).size,1414);
  assert(rows.every(row=>inputSet.has(row.key+"\0"+row.locale)));
  if(preliminaryNotes!==null && !batchPlan)throw Error("B7_BATCH_PLAN_REQUIRED_FOR_UNTRUSTED_NOTES");
  const triage=preliminaryNotes===null?null:
    evaluateProvisionalNotes(riskPlan,batchPlan,preliminaryNotes);
  const escalated=triage?.strata?.filter(s=>s.status==="ESCALATE_FULL_STRATUM")??[];
  const notesInvalid=triage?.status==="INVALID_UNTRUSTED_NOTES";
  const policySubject=work.policySubjectDraft;

  const policyContext={
    schema:"acx-l12-b9b-unsigned-policy-authorization-subject/1",
    status:"DRAFT_SUBJECT_NOT_AN_AUTHORIZATION",
    subject:policySubject,
    role:"POLICY_SIGNER_ONLY_IN_SEPARATE_PROTECTED_ENVIRONMENT",
    requiredOwnerDecision:[
      "Explicitly authorize risk acceptance for unsampled P3 locale rows",
      "Verify the source/candidate/reviewSet and reviewer independence",
      "Record G1/G4 governance approval from real trusted people",
      "Refuse any draft, test-only, self-signed or automatic approval"
    ],
    independentApproval:false
  };

  const reviewQueue=rows.map((r,i)=>({
    ordinal:i+1,key:r.key,locale:r.locale,type:r.type,
    sourceHash:r.sourceHash,translationHash:r.translationHash,
    reviewRevision:r.reviewRevision,
    english:r.english,candidate:r.candidate,
    sourcePath:r.sourcePath,sourceLine:r.sourceLine,
    risks:r.risks,issues:r.issues,
    status:PENDING,
    meaningChecked:false,callsiteContextChecked:false,
    androidFormatChecked:false,operationConsequencesChecked:false,
    reviewerId:null,reviewedAt:null,rationale:null,
    notSigned:true,independentApproved:false,mayRelease:false
  }));

  const strata=work.stratumReviewRequirements.map(s=>({
    stratumId:s.stratumId,featureFamily:s.featureFamily,
    sampledKeys:s.sampledKeys,unselectedKeys:s.unselectedKeys,
    sampledKeysDigest:s.sampledKeysDigest,
    residualKeysDigest:s.residualKeysDigest,
    selectedRowDigest:s.selectedRowDigest,
    residualRowDigest:s.residualRowDigest,
    selectedRowCount:s.selectedRowCount,
    residualRowCount:s.residualRowCount,
    observedUnsignedNoteStatus:notesInvalid?
      "INVALID_NOTES_REFUSED":triage?.strata?.find(z=>z.stratumId===s.stratumId)?.status??
      "NO_PRELIMINARY_NOTES",
    provisionalEscalationRequested:escalated.some(z=>z.stratumId===s.stratumId),
    status:"AWAITING_INDEPENDENT_HUMAN_STRATUM_DISPOSITION",
    decision:null,
    unreviewedResidualExplicitlyAccepted:false,
    reviewerId:null,reviewedAt:null,acceptanceReason:null,
    independentApproved:false,mayRelease:false
  }));

  const compilerSubject=compilerProofSubject({
    stage,packet,policySubject,
    policyEnvelope:null,reviewEnvelope:null
  });
  const gateChecks=[
    ["G1_INDEPENDENT_GLOSSARY_APPROVAL",glossary?.approval==="APPROVED_FOR_RELEASE"],
    ["G4_INDEPENDENT_GOVERNANCE_DECISIONS",Array.isArray(g4Ledger?.approvals)&&g4Ledger.approvals.length>0],
    ["POLICY_PROTECTED_AUTHORITY_EXTERNAL_SIGNATURE",false],
    ["REVIEWER_ROW_DECISIONS_ALL_1414",false],
    ["REVIEWER_12_STRATUM_RESIDUAL_DECISIONS",false],
    ["COMPILER_PROTECTED_ANDROID_BUILD_ATTESTATION",false],
    ["THREE_DISTINCT_OUTSIDE_REPO_TRUST_ANCHORS",false],
    ["PROTECTED_BRANCH_OR_ENVIRONMENT_IS_VERIFIED",false],
    ["REAL_DEVICE_LOCALE_AND_ALARM_TEST",false]
  ].map(([name,observed])=>({name,observed,
    status:observed?"STATE_ONLY_NOT_PROOF_OF_INDEPENDENT_APPROVAL":"NOT_SATISFIED"}));

  const report={
    schema:PREPARATION_SCHEMA,
    status:"BLOCKED_AWAITING_EXTERNAL_APPROVAL",
    approvalAuthority:NO_DECISION,
    sourceSha:packet.sourceSha,
    reviewSetDigest:packet.reviewSetDigest,
    policyDigest:riskPlan.summary.policyDigest,
    policySubjectDigest:digest(policySubject),
    fullLocaleRows:3100,
    mustReviewPerLocaleRows:1414,
    highRiskAndRecheckRows:1208,
    p3SampleRows:206,
    residualRowsRequiringSeparateRiskAcceptance:1686,
    stratumCount:12,
    unsignedNotesImported:preliminaryNotes!==null,
    unsignedNotesValid:preliminaryNotes===null?null:!notesInvalid,
    provisionalEscalationStrata:escalated.length,
    sampleProblemsMustBeIndependentlyResolved:escalated.length>0,
    gateChecks,
    verifiedIndependentApprovals:0,
    verifiedPolicyRiskAcceptances:0,
    officialXmlWritten:0,
    productionApkReleased:0,
    releasePermitted:false,
    notes:[
      "This artifact is a review handoff, not a signed policy or reviewer decision.",
      "A G1/G4 state flag alone is NOT proof of a real independent human decision.",
      "The only usable compiler evidence is built and attested by a distinct protected authority.",
      "Unreviewed residual rows are never described as individually translated or approved.",
      "Rebuild all handoff data if the signed source / policy / glossary / G4 snapshot changes."
    ]
  };
  return {
    report,policyContext,reviewQueue,strata,
    unsignedCompilerRequirements:{
      schema:"acx-l12-b9b-unsigned-compiler-requirements/1",
      status:"NO_COMPILE_ATTESTATION_NOT_PROTECTED",
      sourceSha:packet.sourceSha,
      reviewSetDigest:packet.reviewSetDigest,
      policyDigest:riskPlan.summary.policyDigest,
      expectedStagedFilesSha256:compilerSubject.stagedFilesSha256,
      expectedOfficialFilesSha256:compilerSubject.officialFilesSha256,
      role:"THIRD_INDEPENDENT_PROTECTED_COMPILER",
      requiredEvidence:[
        "Run on a protected, verified branch/Environment with no draft PR code",
        "Full Android resource merge/link and Kotlin build on the exact sourceSha",
        "Record actual compiled artifact hash, GitHub run URL, and exact generated official XML hash",
        "Sign compiler evidence externally after true Gradle success"
      ],
      independentApproved:false,mayRelease:false
    }
  };
}
export function buildHandoffSnapshot(sourceSha){
  const bundle=loadRiskPlan(ROOT,sourceSha);
  const stage=buildTypedResourceStage(bundle.data.packet,bundle.data.matrix);
  return {bundle,stage};
}
function fileOutput(dest,name,value){
  writeFileSync(join(dest,name),JSON.stringify(value,null,2)+"\n","utf8");
}
export function writeOperationalHandoff(dest,result){
  const directory=resolve(dest);
  if(!directory.startsWith(join(ROOT,"build")+"/"))
    throw Error("L12-9B handoff must be confined to build/");
  mkdirSync(directory,{recursive:true});
  fileOutput(directory,"authorization-readiness.json",result.report);
  fileOutput(directory,"unsigned-policy-subject.json",result.policyContext);
  fileOutput(directory,"unsigned-row-review-queue.json",result.reviewQueue);
  fileOutput(directory,"unsigned-stratum-risk-receipts.json",result.strata);
  fileOutput(directory,"unsigned-compiler-requirements.json",result.unsignedCompilerRequirements);
  const readme=[
    "ACX L12-9B: UNSIGNED INDEPENDENT REVIEW HANDOFF",
    "This folder contains no approval or signature and cannot enable official XML.",
    "Required: "+result.report.mustReviewPerLocaleRows+" independent zh-TW/ja per-row checks.",
    "Additional: "+result.report.stratumCount+" feature-family dispositions, "+
      result.report.residualRowsRequiringSeparateRiskAcceptance+
      " non-sampled locale rows explicitly accepted only if signed.",
    "Missing G1/G4 independent approval, protected POLICY/REVIEWER/COMPILER signers and real Android build evidence.",
    "DO NOT copy these JSON drafts into protected signer payloads without real independent human review.",
    "DO NOT sign unsigned row review queue automatically."
  ].join("\n")+"\n";
  writeFileSync(join(directory,"READ_ME_UNAPPROVED.txt"),readme,"utf8");
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const arg=name=>{
    const i=process.argv.indexOf(name);return i<0?null:process.argv[i+1];
  };
  const out=arg("--out-dir");
  if(!out)throw Error("L12-9B requires --out-dir under build/");
  const sourceSha=execFileSync("git",["rev-parse","HEAD"],{
    cwd:ROOT,encoding:"utf8"}).trim();
  assert.match(sourceSha,/^[0-9a-f]{40}$/);
  const {bundle,stage}=buildHandoffSnapshot(sourceSha);
  const notesPath=arg("--preliminary-notes");
  const preliminaryNotes=notesPath?JSON.parse(readFileSync(resolve(notesPath),"utf8")):null;
  const handoff=createOperationalHandoff({
    packet:bundle.data.packet,riskPlan:bundle.riskPlan,policy:bundle.policy,
    glossary:bundle.data.glossary,g4Ledger:bundle.data.g4Ledger,
    matrix:bundle.data.matrix,stage,
    preliminaryNotes,batchPlan:bundle.batchPlan
  });
  writeOperationalHandoff(out,handoff);
  console.log("L12_9B_UNAUTHORIZED_REVIEW_HANDOFF "+JSON.stringify(handoff.report));
  if(preliminaryNotes&&handoff.report.unsignedNotesValid===false)process.exitCode=1;
}
