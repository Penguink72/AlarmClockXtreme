#!/usr/bin/env node
/**
 * L12-9 protected policy/reviewer/compiler attestations, fail-closed.
 * Real keys must come from three separately protected external authorities.
 * This file never signs approvals or changes source/official resources.
 */
import assert from "node:assert/strict";
import {createHash,createPublicKey,verify} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync,realpathSync,statSync} from "node:fs";
import {resolve,dirname,join,isAbsolute} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {loadRiskPlan,buildRiskTieredPlan,validatePolicy} from "./l12-b8b-risk-tiered-policy.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const sha=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x===undefined?null:x),"utf8").digest("hex");
const rawFileDigest=x=>"sha256:"+createHash("sha256").update(x,"utf8").digest("hex");
const rowId=r=>r.key+"\0"+r.locale;
const policySchema="acx-l12-b9-signed-policy-envelope/1";
const reviewSchema="acx-l12-b9-signed-tiered-review-envelope/1";
const compilerSchema="acx-l12-b9-signed-compiler-envelope/1";
const ISO=x=>typeof x==="string"&&
  /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(x)&&
  Number.isFinite(Date.parse(x));
const HASH=/^sha256:[0-9a-f]{64}$/;
const authorizeStatement="AUTHORIZED_RISK_TIERED_POLICY_WITH_RESIDUAL_ACCOUNTABILITY";
const approvedRow=(a,r)=>a&&a.key===r.key&&a.locale===r.locale&&a.type===r.type&&
  a.sourceHash===r.sourceHash&&a.translationHash===r.translationHash&&
  a.reviewRevision===r.reviewRevision&&a.decision==="APPROVED"&&
  a.reviewMode==="human-independent-semantic-review"&&
  a.meaningVerified===true&&a.contextVerified===true&&a.formatVerified===true&&
  a.operationConsequencesVerified===true&&
  typeof a.reviewerId==="string"&&a.reviewerId.trim().length>=3&&
  typeof a.reviewNotes==="string"&&a.reviewNotes.trim().length>=16&&
  ISO(a.reviewedAt);
const uniq=items=>[...new Set(items)];

function verifyEnvelope(envelope,schema,pem,role,errors){
  if(envelope?.schema!==schema||!envelope.payload||
     typeof envelope.signatureBase64!=="string"||
     !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(envelope.signatureBase64)){
    errors.push(role+"_ENVELOPE_INVALID");
    return null;
  }
  if(typeof pem!=="string"||!pem.trim()){
    errors.push(role+"_PROTECTED_KEY_MISSING");
    return null;
  }
  try{
    const key=createPublicKey(pem);
    if(key.asymmetricKeyType!=="ed25519")throw Error("Non-Ed25519 key");
    const fingerprint=sha(key.export({format:"der",type:"spki"}).toString("base64"));
    const raw=Buffer.from(envelope.signatureBase64,"base64");
    if(raw.length!==64||!verify(null,Buffer.from(JSON.stringify(envelope.payload),"utf8"),key,raw))
      errors.push(role+"_SIGNATURE_INVALID");
    return fingerprint;
  }catch{errors.push(role+"_SIGNATURE_OR_KEY_INVALID");return null;}
}
function lines(rows){
  return rows.map(r=>[r.key,r.locale,r.type,r.sourceHash,r.translationHash,r.reviewRevision]);
}
function receiptDigest(riskPlan,stratum){
  // Neither the sampler nor an L12-7 unsigned note has permission to approve
  // the residual population represented by these fingerprints.
  const cards=new Map(riskPlan.diagnosticSampleCards.map(c=>[c.key,c]));
  const selected=lines(stratum.sampledKeys.flatMap(k=>cards.get(k)?.localeRows??[]));
  return {selected:sha(selected),residual:sha(stratum.unselectedKeys)};
}
/**
 * A protected policy approver signs this source-and-policy-bound approval.
 * It must be separate from the semantic reviewer and compiler authority.
 */
export function policyApprovalSubject({packet,riskPlan,policy,glossary,g4Ledger}){
  const policyDigest=validatePolicy(policy);
  assert.equal(riskPlan.summary.policyDigest,policyDigest);
  assert.equal(riskPlan.summary.reviewSetDigest,packet.reviewSetDigest);
  return {
    policyId:policy.policyId,policyRevision:policy.policyRevision,
    policyDigest,reviewSetDigest:packet.reviewSetDigest,
    catalogueRevision:packet.catalogueRevision,
    glossaryRevision:packet.glossaryRevision,
    g1GlossaryDigest:sha(glossary),
    g4LedgerDigest:sha(g4Ledger),
    stratificationDigest:sha(riskPlan.strata.map(s=>[
      s.stratumId,s.featureFamily,s.sampledKeys,s.unselectedKeys])),
    mandatoryReviewDigest:sha(riskPlan.mandatoryFullReview.map(c=>[
      c.key,c.localeRows.map(r=>[r.locale,r.sourceHash,r.translationHash,r.reviewRevision])])),
    authorization:authorizeStatement
  };
}
export function compilerProofSubject({stage,packet,policySubject,policyEnvelope,reviewEnvelope}){
  const officialFilesSha256=Object.fromEntries(Object.entries(stage.files).map(([path,data])=>[
    path,rawFileDigest(data.replace(
      "<!-- MODEL UNREVIEWED. ISOLATED L13-B STAGING ONLY, NEVER COPY INTO APP. -->",
      "<!-- Reviewed through protected L12-9 risk-tiered authorizations and L13-B source validation. -->"))
  ]));
  return {
    reviewSetDigest:packet.reviewSetDigest,policyDigest:policySubject.policyDigest,
    policyEnvelopeDigest:sha(policyEnvelope),
    reviewEnvelopeDigest:sha(reviewEnvelope),
    sourceSha:packet.sourceSha,
    stagedFilesSha256:stage.manifest.stagedFilesSha256,
    officialFilesSha256
  };
}
/**
 * The exported worklist is deliberately UNSIGNED. A reviewer must examine
 * the concrete source text and UI effects before generating real decisions.
 * No "APPROVED" flag, release command, private key or signature is produced.
 */
export function makeUnsignedReviewRequirements({packet,riskPlan,policy,glossary,g4Ledger}){
  const subject=policyApprovalSubject({packet,riskPlan,policy,glossary,g4Ledger});
  const byId=new Map(packet.rows.map(r=>[rowId(r),r]));
  const mandatoryIds=riskPlan.mandatoryFullReview.flatMap(c=>c.localeRows.map(rowId));
  const sampledIds=riskPlan.strata.flatMap(s=>s.sampledKeys.flatMap(k=>
    ["zh-TW","ja"].map(locale=>k+"\0"+locale)));
  const reviewIds=[...new Set([...mandatoryIds,...sampledIds])];
  const rows=reviewIds.map(id=>{
    const r=byId.get(id);
    if(!r)throw Error("L12-9 missing required full/sample row "+id);
    return {key:r.key,locale:r.locale,type:r.type,
      sourceHash:r.sourceHash,translationHash:r.translationHash,
      reviewRevision:r.reviewRevision,english:r.english,
      sourcePath:r.sourcePath,sourceLine:r.sourceLine,
      candidate:r.candidate,risks:r.risks,issues:r.issues,
      reviewStatus:"PENDING_INDEPENDENT_HUMAN_SEMANTIC_REVIEW",
      decisionsToCollect:["MEANING","CALLSITE_CONTEXT","ANDROID_FORMAT",
        "OPERATION_CONSEQUENCE","IDENTITY_TIMESTAMP_RATIONALE"],
      independentApproved:false};
  });
  return {
    schema:"acx-l12-b9-unsigned-protected-review-requirements/1",
    status:"REVIEW_REQUIRED_NOT_AN_AUTHORIZATION",
    policySubjectDraft:subject,
    rowReviewRequirements:rows,
    stratumReviewRequirements:riskPlan.strata.map(s=>{
      const digests=receiptDigest(riskPlan,s);
      return {
        stratumId:s.stratumId,featureFamily:s.featureFamily,
        sampledKeys:s.sampledKeys,unselectedKeys:s.unselectedKeys,
        sampledKeysDigest:sha(s.sampledKeys),
        residualKeysDigest:sha(s.unselectedKeys),
        selectedRowDigest:digests.selected,
        residualRowDigest:digests.residual,
        selectedRowCount:s.sampledKeys.length*2,
        residualRowCount:s.unselectedKeys.length*2,
        reviewStatus:"AWAITING_INDEPENDENT_SIGNED_STRATUM_DISPOSITION",
        allowedDecisions:["ACCEPT_RESIDUAL_UNDER_PROTECTED_POLICY",
          "ESCALATED_FULL_STRATUM_REVIEW","ALL_KEYS_INDEPENDENTLY_REVIEWED"],
        mandatoryReviewerEvidence:["REVIEWER_ID","REVIEWED_AT",
          "EXPLICIT_RESIDUAL_ACCOUNTABILITY","ACCEPTANCE_REASON"]
      };
    }),
    requiredIndividualLocaleRows:rows.length,
    nonSampledResidualLocaleRows:packet.rows.length-rows.length,
    independentApproved:0,
    noAutomaticPolicyRelease:true
  };
}

function checkReviewRows({packet,riskPlan,envelope,errors}){
  const p=envelope?.payload??{};
  const packetMap=new Map(packet.rows.map(r=>[rowId(r),r]));
  const expectedFull=new Set(riskPlan.mandatoryFullReview.flatMap(c=>c.localeRows.map(rowId)));
  const strata=riskPlan.strata.map(s=>({s,sampled:new Set(s.sampledKeys.flatMap(k=>
    ["zh-TW","ja"].map(locale=>k+"\0"+locale))),
    residual:new Set(s.unselectedKeys.flatMap(k=>
    ["zh-TW","ja"].map(locale=>k+"\0"+locale)))}));
  if(!Array.isArray(p.approvals)||!Array.isArray(p.stratumReceipts)){
    errors.push("ROW_APPROVALS_AND_STRATUM_RECEIPTS_REQUIRED");
    return {approved:0,residual:0};
  }
  if(p.approvals.length>3100||p.stratumReceipts.length!==strata.length){
    errors.push("INVALID_APPROVAL_OR_STRATUM_COUNT");
    return {approved:0,residual:0};
  }
  const approved=new Set();
  for(const a of p.approvals){
    const id=rowId(a),source=packetMap.get(id);
    if(approved.has(id)){errors.push("DUPLICATE_REVIEW_APPROVAL");continue;}
    if(!source||!approvedRow(a,source)){
      errors.push("UNVERIFIED_ROW_REVIEW_OR_VERSION_DRIFT");continue;
    }
    approved.add(id);
  }
  for(const id of expectedFull)if(!approved.has(id))
    errors.push("MANDATORY_RISK_ROW_NOT_INDEPENDENTLY_REVIEWED");
  const byId=new Map(strata.map(({s})=>[s.stratumId,s]));
  const receipts=new Map();
  for(const item of p.stratumReceipts){
    if(typeof item?.stratumId!=="string"||!byId.has(item.stratumId)||
       receipts.has(item.stratumId)){
      errors.push("DUPLICATE_OR_UNKNOWN_STRATUM_RECEIPT");continue;
    }
    receipts.set(item.stratumId,item);
  }
  let acceptedResidual=0;
  for(const {s,sampled,residual} of strata){
    const receipt=receipts.get(s.stratumId);
    if(!receipt){
      errors.push("MISSING_STRATUM_DISPOSITION");continue;
    }
    const {selected:expectedSelected,residual:expectedResidual}=receiptDigest(riskPlan,s);
    if(receipt.reviewSetDigest!==packet.reviewSetDigest||
      receipt.policyDigest!==riskPlan.summary.policyDigest||
      receipt.sampledKeysDigest!==sha(s.sampledKeys)||
      receipt.residualKeysDigest!==sha(s.unselectedKeys)||
      receipt.selectedRowDigest!==expectedSelected||
      receipt.selectedRowCount!==sampled.size||
      receipt.residualRowCount!==residual.size||
      !ISO(receipt.reviewedAt)||
      typeof receipt.reviewerId!=="string"||receipt.reviewerId.trim().length<3||
      typeof receipt.acceptanceReason!=="string"||receipt.acceptanceReason.trim().length<30){
      errors.push("STRATUM_PROVENANCE_OR_ACCOUNTABILITY_MISMATCH");
    }
    for(const id of sampled)if(!approved.has(id))
      errors.push("SAMPLE_LOCALE_ROW_NOT_INDEPENDENTLY_REVIEWED");
    if(receipt.decision==="ACCEPT_RESIDUAL_UNDER_PROTECTED_POLICY"){
      // Signed, explicit acknowledgement that the remaining keys were NOT
      // individually reviewed. This is risk acceptance, not semantic proof.
      if(residual.size===0){
        errors.push("EMPTY_STRATUM_CANNOT_CLAIM_RESIDUAL");continue;
      }
      if(receipt.residualRowDigest!==expectedResidual)
        errors.push("UNREVIEWED_RESIDUAL_FINGERPRINT_MISMATCH");
      if(receipt.unreviewedResidualExplicitlyAccepted!==true)
        errors.push("RESIDUAL_RISK_NOT_EXPLICITLY_ACKNOWLEDGED");
      for(const id of residual){
        if(approved.has(id))errors.push("RESIDUAL_ROW_CONFLICTS_WITH_DIRECT_APPROVAL");
      }
      acceptedResidual+=residual.size;
    }else if(receipt.decision==="ESCALATED_FULL_STRATUM_REVIEW"){
      if(receipt.unreviewedResidualExplicitlyAccepted!==false)
        errors.push("ESCALATED_STRATUM_CANNOT_ACCEPT_RESIDUAL_RISK");
      if(receipt.residualRowDigest!==expectedResidual)
        errors.push("ESCALATED_STRATUM_FINGERPRINT_MISMATCH");
      for(const id of residual)if(!approved.has(id))
        errors.push("ESCALATED_STRATUM_REQUIRES_FULL_ROW_REVIEW");
    }else if(receipt.decision==="ALL_KEYS_INDEPENDENTLY_REVIEWED"){
      if(residual.size!==0||receipt.unreviewedResidualExplicitlyAccepted!==false)
        errors.push("BAD_EMPTY_STRATUM_RECEIPT");
    }else errors.push("UNKNOWN_STRATUM_DISPOSITION");
  }
  if(approved.size+acceptedResidual!==packet.rows.length)
    errors.push("INCOMPLETE_AUTHORIZED_3100_ROW_COVERAGE");
  return {approved:approved.size,residual:acceptedResidual,
    expectedMandatory:expectedFull.size};
}
export function validateProtectedTieredRelease({
  packet,riskPlan,policy,glossary,g4Ledger,stage,
  policyEnvelope,reviewEnvelope,compilerEnvelope,
  trustedPolicyKeyPem,trustedReviewerKeyPem,trustedCompilerKeyPem,
  protectedRef
}){
  const errors=[];
  if(protectedRef!==true)errors.push("GITHUB_PROTECTED_REF_REQUIRED");
  if(packet?.schema!=="acx-l12-b6-independent-review-packet/1"||
     packet.rows?.length!==3100||
     stage?.manifest?.reviewSetDigest!==packet.reviewSetDigest)
    return {status:"BLOCKED",errors:["L12_9_SOURCE_OR_STAGE_MISMATCH"],officialRows:0};
  let subject;
  try {subject=policyApprovalSubject({packet,riskPlan,policy,glossary,g4Ledger});}
  catch {return {status:"BLOCKED",errors:["INVALID_OR_UNBOUND_POLICY"],officialRows:0};}
  if(glossary?.approval!=="APPROVED_FOR_RELEASE")
    errors.push("G1_GLOSSARY_NOT_INDEPENDENTLY_APPROVED");
  if(g4Ledger?.schema!=="acx-g4-review-ledger/1"||
    !Array.isArray(g4Ledger.approvals)||g4Ledger.approvals.length===0)
    errors.push("G4_APPROVAL_LEDGER_EMPTY");
  const pKey=verifyEnvelope(policyEnvelope,policySchema,trustedPolicyKeyPem,"POLICY",errors);
  const rKey=verifyEnvelope(reviewEnvelope,reviewSchema,trustedReviewerKeyPem,"REVIEWER",errors);
  const cKey=verifyEnvelope(compilerEnvelope,compilerSchema,trustedCompilerKeyPem,"COMPILER",errors);
  if((pKey&&rKey&&pKey===rKey)||(pKey&&cKey&&pKey===cKey)||(rKey&&cKey&&rKey===cKey))
    errors.push("PROTECTED_AUTHORITY_KEYS_MUST_BE_DISTINCT");
  const actualPolicy=policyEnvelope?.payload??null;
  if(!actualPolicy||!Object.keys(subject).every(k=>equalField(actualPolicy[k],subject[k]))||
     Object.keys(actualPolicy).length!==Object.keys(subject).length)
    errors.push("POLICY_AUTHORIZATION_NOT_BOUND_TO_CURRENT_SOURCE_OR_GOVERNANCE");
  const reviewed=reviewEnvelope?.payload??{};
  if(reviewed.policyEnvelopeDigest!==sha(policyEnvelope)||
    reviewed.reviewSetDigest!==packet.reviewSetDigest||
    reviewed.policyDigest!==subject.policyDigest||
    reviewed.catalogueRevision!==packet.catalogueRevision||
    reviewed.glossaryRevision!==packet.glossaryRevision||
    reviewed.g1GlossaryDigest!==subject.g1GlossaryDigest||
    reviewed.g4LedgerDigest!==subject.g4LedgerDigest||
    reviewed.reviewPolicy!=="INDEPENDENT_TIERED_SEMANTIC_AND_RESIDUAL_RISK_REVIEW")
    errors.push("REVIEW_ENVELOPE_NOT_BOUND_TO_POLICY_OR_GOVERNANCE");
  const coverage=checkReviewRows({packet,riskPlan,envelope:reviewEnvelope,errors});
  const proof=compilerEnvelope?.payload??{};
  const target=compilerProofSubject({stage,packet,policySubject:subject,
    policyEnvelope,reviewEnvelope});
  if(proof.schema!=="acx-l12-b9-protected-compiler-evidence/1"||
    proof.status!=="PASSED"||proof.repository!=="Penguink72/AlarmClockXtreme"||
    proof.protectedRef!==true||
    typeof proof.gitCommitSha!=="string"||!/^[0-9a-f]{40}$/.test(proof.gitCommitSha)||
    proof.gitCommitSha!==packet.sourceSha||
    !ISO(proof.issuedAt)||
    !HASH.test(proof.compiledResourceZipSha256??"")||
    typeof proof.workflowRunUrl!=="string"||
    !/^https:\/\/github\.com\/Penguink72\/AlarmClockXtreme\/actions\/runs\/\d+$/.test(proof.workflowRunUrl)||
    !Array.isArray(proof.gradleTasks)||
    !proof.gradleTasks.includes(":app:processFdroidDebugResources")||
    !proof.gradleTasks.includes(":app:compileFdroidDebugKotlin")||
    !Object.entries(target).every(([k,v])=>equalField(proof[k],v)))
    errors.push("PROTECTED_COMPILER_ATTESTATION_MISSING_INVALID_OR_STALE");
  return {status:errors.length?"BLOCKED":"PROTECTED_TIERED_POLICY_EVIDENCE_VALID",
    errors:uniq(errors),officialRows:errors.length?0:3100,
    directlyApprovedRows:errors.length?0:coverage.approved,
    policyRiskAcceptedRows:errors.length?0:coverage.residual,
    note:"Evidence is meaningful only with separately configured protected external trust anchors and genuine independent review; CI fixtures do not authorize anything."};
}
const equalField=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
/** For the protected CLI, not for ordinary CI or PR-supplied files. */
export function readExternalProtectedKeys(env=process.env,root=ROOT){
  const specs=[
    ["ACX_L12_B9_POLICY_PUBLIC_KEY_FILE","policy"],
    ["ACX_L12_B9_REVIEWER_PUBLIC_KEY_FILE","reviewer"],
    ["ACX_L12_B9_COMPILER_PUBLIC_KEY_FILE","compiler"]
  ];
  const result={},used=new Set();
  for(const [envKey,role] of specs){
    const name=env[envKey]??"";
    if(!isAbsolute(name))throw Error("PROTECTED_"+role.toUpperCase()+"_KEY_PATH_REQUIRED");
    const resolved=realpathSync(resolve(name));
    const realRoot=realpathSync(resolve(root));
    if(resolved===realRoot||resolved.startsWith(realRoot+"/")||resolved.startsWith(realRoot+"\\"))
      throw Error("PROTECTED_"+role.toUpperCase()+"_KEY_PATH_INSIDE_REPOSITORY");
    if(!statSync(resolved).isFile())
      throw Error("PROTECTED_"+role.toUpperCase()+"_KEY_MUST_BE_REGULAR_FILE");
    if(used.has(resolved))throw Error("PROTECTED_AUTHORITY_KEY_PATHS_MUST_BE_DISTINCT");
    used.add(resolved);
    result[role]=readFileSync(resolved,"utf8");
  }
  return result;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const has=flag=>{const i=process.argv.indexOf(flag);return i<0?null:resolve(process.argv[i+1]??"");};
  if(!process.argv.includes("--verify-protected")){
    const out=has("--out-dir");
    if(out){
      if(!out.startsWith(join(ROOT,"build")+"/"))
        throw Error("L12-9 unsigned requirements only under build/");
      const x=loadRiskPlan(ROOT,"PROPOSED_UNSIGNABLE_WORKLIST");
      const content=makeUnsignedReviewRequirements({packet:x.data.packet,
        riskPlan:x.riskPlan,policy:x.policy,glossary:x.data.glossary,
        g4Ledger:x.data.g4Ledger});
      mkdirSync(out,{recursive:true});
      writeFileSync(join(out,"unsigned-review-requirements.json"),JSON.stringify(content,null,2)+"\n");
      writeFileSync(join(out,"protected-gate-status.json"),JSON.stringify({
        schema:"acx-l12-b9-gate-status/1",status:"BLOCKED",
        policyAuthorized:false,independentApproved:0,
        actualOfficialXmlWritten:0,requiredReviewRows:content.requiredIndividualLocaleRows,
        uninspectedResidualRows:content.nonSampledResidualLocaleRows,
        releasePath:"INACTIVE_UNTIL_SEPARATE_REAL_SIGNED_AUTHORITY_AND_G1_G4"
      },null,2)+"\n");
    }
    console.log("L12_9_NO_SELF_APPROVAL "+JSON.stringify({
      status:"BLOCKED_PENDING_PROTECTED_POLICY_AND_REVIEWER_AUTHORITY",
      instructions:"Use --verify-protected and THREE external trust anchors after protected authorization. This command never produces signatures."
    }));
  }else{
    const keys=readExternalProtectedKeys();
    const files=["--policy-envelope","--review-envelope","--compiler-envelope"].map(has);
    if(files.some(x=>x===null))throw Error("THREE_SIGNED_EXTERNAL_ENVELOPES_REQUIRED");
    const [policyEnvelope,reviewEnvelope,compilerEnvelope]=files.map(p=>JSON.parse(readFileSync(p,"utf8")));
    let shaValue="unavailable";
    try{shaValue=(await import("node:child_process")).execFileSync(
      "git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
    const {data,policy,riskPlan}=loadRiskPlan(ROOT,shaValue);
    const {buildTypedResourceStage}=await import("./l13-b-protected-xml-writer.mjs");
    const stage=buildTypedResourceStage(data.packet,data.matrix);
    const result=validateProtectedTieredRelease({packet:data.packet,riskPlan,policy,
      glossary:data.glossary,g4Ledger:data.g4Ledger,stage,
      policyEnvelope,reviewEnvelope,compilerEnvelope,
      trustedPolicyKeyPem:keys.policy,trustedReviewerKeyPem:keys.reviewer,
      trustedCompilerKeyPem:keys.compiler,
      protectedRef:process.env.GITHUB_REF_PROTECTED==="true"});
    console.log("L12_9_PROTECTED_EVIDENCE "+JSON.stringify(result));
    if(result.status!=="PROTECTED_TIERED_POLICY_EVIDENCE_VALID")process.exitCode=1;
  }
}
