#!/usr/bin/env node
/**
 * L12-6: complete source-bound independent-review packet and fail-closed intake.
 * No model/CI-produced approval, official XML write or G1/G4 ledger mutation.
 * An external protected workflow must supply a trusted review public key.
 */
import assert from "node:assert/strict";
import {createHash,createPublicKey,verify} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve,isAbsolute} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildL12,risksFor} from "./l12-structural-semantic-evidence.mjs";
import {buildResidualClosure} from "./l11-b10-residual-closure.mjs";
import {validateB10} from "./l11-b10-typed-candidates.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const digest=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x),"utf8").digest("hex");
const idOf=x=>x.key+"\0"+x.locale;
const LOCALES=["zh-TW","ja"];

export function buildUnifiedReviewBundle({l11,l12,typed,matrix,glossary}){
  assert.equal(l11.schema,"acx-l11-full-locale-candidate-queue/1");
  assert.equal(l12.schema,"acx-l12-evidence-worklist/1");
  assert.equal(typed.schema,"acx-l11-b10-complete-candidate-evidence/1");
  assert.equal(matrix.schema,"acx-b0-trilingual-canonical-matrix/1");
  assert.equal(glossary.schema,"acx-glossary-contract/1");
  assert.equal(l11.sourceSha,l12.sourceSha);
  assert.equal(l11.sourceSha,typed.sourceSha);
  assert.equal(l11.catalogueRevision,matrix.catalogueRevision);
  assert.equal(l11.catalogueRevision,typed.catalogueRevision);
  assert.equal(l12.glossaryRevision,glossary.glossaryRevision);
  const base=new Map(matrix.rows.map(r=>[r.key,r]));
  const required=new Set(l11.queue.map(idOf));
  const all=[];
  for(const r of l12.records){
    const source=base.get(r.key);
    if(!source || source.type!=="string")throw Error("Unrecognized L12 ordinary string source "+r.key);
    all.push({
      key:r.key,locale:r.locale,type:"string",
      sourceHash:r.sourceHash,translationHash:r.translationHash,
      reviewRevision:r.reviewRevision,english:source.sourcePreview,
      sourceXml:source.sourceXml,candidate:r.translation,
      sourcePath:r.sourcePath,sourceLine:r.sourceLine,
      risks:r.risks,issues:r.issues,glossaryHints:r.glossaryHints,
      origin:r.candidateOrigin,provenance:"L11_L12_SOURCE_BOUND_UNREVIEWED",
      independentApproved:false
    });
  }
  for(const r of typed.records){
    const source=base.get(r.key);
    if(!source || source.type!==r.type||source.sourceHash!==r.sourceHash)
      throw Error("Typed candidate source mismatch "+r.key);
    const translationHash=digest([r.key,r.locale,r.candidate]);
    const reviewRevision=digest([r.sourceHash,translationHash,
      source.sourceXml,glossary.glossaryRevision]);
    all.push({
      key:r.key,locale:r.locale,type:r.type,
      sourceHash:r.sourceHash,translationHash,reviewRevision,
      english:source.sourcePreview,sourceXml:source.sourceXml,
      candidate:r.candidate,sourcePath:source.sourcePath,sourceLine:source.sourceLine,
      risks:risksFor(r.key,r.sourcePreview),issues:["TYPED_RESOURCE_CONTEXT_INDEPENDENT_REVIEW"],
      glossaryHints:[],origin:"L11_B10_TYPED_MODEL_DRAFT",
      provenance:"L11_B10_SOURCE_BOUND_UNREVIEWED",independentApproved:false
    });
  }
  const seen=new Set(),byType={string:0,plurals:0,array:0},byLocale={"zh-TW":0,ja:0};
  for(const r of all){
    const id=idOf(r);
    if(seen.has(id)||!required.has(id)||!LOCALES.includes(r.locale))
      throw Error("Duplicate/unexpected locale review row "+id);
    if(!/^sha256:[0-9a-f]{64}$/.test(r.sourceHash) ||
       !/^sha256:[0-9a-f]{64}$/.test(r.translationHash) ||
       !/^sha256:[0-9a-f]{64}$/.test(r.reviewRevision))
      throw Error("Invalid hash chain "+id);
    seen.add(id);
    byType[r.type]++;
    byLocale[r.locale]++;
  }
  if(seen.size!==required.size)throw Error("Review set incomplete: "+seen.size+"/"+required.size);
  if(all.length!==3100 || byType.string!==3036 || byType.plurals!==62 || byType.array!==2)
    throw Error("B0 source denominator/type drift - rebaseline rather than silently approve");
  if(byLocale["zh-TW"]!==1550||byLocale.ja!==1550)throw Error("Locale denominator mismatch");
  all.sort((a,b)=>a.key.localeCompare(b.key,"en")||a.locale.localeCompare(b.locale,"en"));
  const rowFingerprints=all.map(r=>[r.key,r.locale,r.type,r.sourceHash,r.translationHash,r.reviewRevision]);
  const result={
    schema:"acx-l12-b6-independent-review-packet/1",
    sourceSha:l11.sourceSha,catalogueRevision:l11.catalogueRevision,
    glossaryRevision:glossary.glossaryRevision,
    reviewSetDigest:digest(rowFingerprints),
    status:"ALL_CANDIDATES_REQUIRE_EXTERNAL_INDEPENDENT_REVIEW",
    statistics:{
      missingDedicatedLocaleDefinitions:l11.queue.length,
      reviewRows:all.length,byType,byLocale,
      independentApproved:0,
      officialXmlWritten:0,
      unreviewedHighRiskRows:all.filter(r=>r.risks.some(x=>x!=="GENERAL_UI")).length
    },
    instructions:[
      "The packet does not imply human or independent semantic review.",
      "Official approvals require externally signed per-row verification and source/translation/review revision equality.",
      "Approval cannot be inferred from glossary match, code probe, source fallback, CI success, or aapt2 compilation.",
      "Use a protected external reviewer identity and public key trust anchor outside this repository.",
      "No production resource write is permitted from this packet."
    ],rows:all
  };
  return result;
}
function approvedRow(a,r){
  return a.key===r.key&&a.locale===r.locale&&a.type===r.type&&
    a.sourceHash===r.sourceHash&&a.translationHash===r.translationHash&&
    a.reviewRevision===r.reviewRevision&&a.decision==="APPROVED"&&
    a.reviewMode==="human-independent-semantic-review"&&
    a.meaningVerified===true&&a.contextVerified===true&&
    a.formatVerified===true&&a.operationConsequencesVerified===true&&
    typeof a.reviewerId==="string"&&a.reviewerId.trim().length>=3&&
    typeof a.reviewNotes==="string"&&a.reviewNotes.trim().length>=16&&
    typeof a.reviewedAt==="string"&&Number.isFinite(Date.parse(a.reviewedAt));
}
/** Verify external review signatures; never sign on behalf of a reviewer. */
export function validateSignedReviewIntake({packet,envelope,trustedPublicKeyPem,glossary,g4Ledger}){
  const errors=[];
  if(!packet||packet.schema!=="acx-l12-b6-independent-review-packet/1")
    return {status:"BLOCKED",errors:["BAD_PACKET"]};
  if(!envelope || envelope.schema!=="acx-l12-b6-signed-review-envelope/1" ||
     !envelope.payload || !Array.isArray(envelope.payload.approvals))
    return {status:"BLOCKED",errors:["NO_SIGNED_EXTERNAL_REVIEW_ENVELOPE"]};
  if(typeof trustedPublicKeyPem!=="string"||!trustedPublicKeyPem.trim()){
    errors.push("PROTECTED_REVIEW_TRUST_ANCHOR_MISSING");
  }else{
    try {
      const publicKey=createPublicKey(trustedPublicKeyPem);
      if(publicKey.asymmetricKeyType!=="ed25519")errors.push("REVIEW_KEY_MUST_BE_ED25519");
      else if(typeof envelope.signatureBase64!=="string" ||
        !verify(null,Buffer.from(JSON.stringify(envelope.payload),"utf8"),
          publicKey,Buffer.from(envelope.signatureBase64,"base64")))
        errors.push("INVALID_PROTECTED_REVIEW_SIGNATURE");
    }catch{errors.push("INVALID_REVIEW_PUBLIC_KEY_OR_SIGNATURE")}
  }
  const p=envelope.payload;
  if(p.reviewSetDigest!==packet.reviewSetDigest||
     p.catalogueRevision!==packet.catalogueRevision||
     p.glossaryRevision!==packet.glossaryRevision||
     p.sourceSha!==packet.sourceSha)
    errors.push("STALE_SOURCE_OR_REVIEW_SET");
  if(p.reviewPolicy!=="INDEPENDENT_HUMAN_SEMANTIC_AND_OPERATION_REVIEW")
    errors.push("REVIEW_POLICY_NOT_INDEPENDENT");
  if(!glossary||glossary.approval!=="APPROVED_FOR_RELEASE")
    errors.push("G1_GLOSSARY_NOT_INDEPENDENTLY_APPROVED");
  if(!g4Ledger||g4Ledger.schema!=="acx-g4-review-ledger/1"||
     !Array.isArray(g4Ledger.approvals)||g4Ledger.approvals.length===0)
    errors.push("G4_APPROVAL_LEDGER_EMPTY");
  const expected=new Map(packet.rows.map(r=>[idOf(r),r]));
  if(p.approvals.length!==expected.size)errors.push("INCOMPLETE_EXTERNAL_APPROVAL_SET");
  const seen=new Set();
  for(const approval of p.approvals){
    const id=idOf(approval);
    if(seen.has(id)){errors.push("DUPLICATE_APPROVAL");continue;}
    seen.add(id);
    const row=expected.get(id);
    if(!row){errors.push("UNKNOWN_REVIEW_ROW");continue;}
    if(!approvedRow(approval,row))errors.push("STALE_OR_INSUFFICIENT_ROW_APPROVAL");
  }
  return {status:errors.length?"BLOCKED":"INDEPENDENT_REVIEW_EVIDENCE_VALID",
    errors:[...new Set(errors)],verifiedReviewRows:errors.length?0:expected.size,
    note:"Signature verification is only meaningful when the public key is supplied by a protected external workflow, not from the PR."};
}
export function reviewTsv(packet){
  const columns=["type","key","locale","source_hash","translation_hash","review_revision",
    "risk","issues","english","candidate","reviewer","decision"];
  const cell=x=>JSON.stringify(x??"");
  return columns.join("\t")+"\n"+packet.rows.map(r=>[
    r.type,r.key,r.locale,r.sourceHash,r.translationHash,r.reviewRevision,
    r.risks.join(";"),r.issues.join(";"),r.english,JSON.stringify(r.candidate),"","PENDING"
  ].map(cell).join("\t")).join("\n")+"\n";
}
export function loadUnifiedInputs(root=ROOT,sourceSha="unavailable"){
  const load=p=>JSON.parse(readFileSync(join(root,p),"utf8"));
  const inp=loadL11Inputs(root,sourceSha),l11=buildL11(inp),
    matrix=generateMatrix(root),glossary=load("localization/governance/g1/glossary-contract.json"),
    l12=buildL12({l11,graph:inp.graph,matrix,glossary}),
    residual=buildResidualClosure(l11,inp.graph),
    packs=["strings","plural","array"].map(kind=>
      load("localization/candidates/l11-b10-"+kind+"-drafts-20261122.json")),
    typed=validateB10({residual,matrix,packs});
  const packet=buildUnifiedReviewBundle({l11,l12,typed,matrix,glossary});
  return {packet,l11,l12,typed,matrix,glossary,
    g4Ledger:load("localization/governance/g4/review-ledger.json")};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const data=loadUnifiedInputs(ROOT,sha);
  const flag=process.argv.indexOf("--out-dir");
  if(flag>=0){
    const out=resolve(process.argv[flag+1]??"");
    if(!out.startsWith(join(ROOT,"build")+"/"))throw Error("L12-6 output must stay under build/");
    mkdirSync(out,{recursive:true});
    writeFileSync(join(out,"independent-review-packet.json"),JSON.stringify(data.packet,null,2)+"\n");
    writeFileSync(join(out,"independent-review-worklist.tsv"),reviewTsv(data.packet));
  }
  const signedAt=process.argv.indexOf("--review-envelope");
  if(signedAt>=0){
    const p=resolve(process.argv[signedAt+1]??"");
    const keyPath=process.env.ACX_L12_PROTECTED_REVIEW_PUBLIC_KEY_FILE||"";
    if(!isAbsolute(keyPath)||keyPath.startsWith(ROOT+"/")||keyPath.startsWith(ROOT+"\\"))
      throw Error("PROTECTED_REVIEW_TRUST_ANCHOR_MISSING_OR_INSIDE_REPOSITORY");
    const envelope=JSON.parse(readFileSync(p,"utf8"));
    const result=validateSignedReviewIntake({packet:data.packet,envelope,
      trustedPublicKeyPem:readFileSync(keyPath,"utf8"),glossary:data.glossary,
      g4Ledger:data.g4Ledger});
    if(result.status!=="INDEPENDENT_REVIEW_EVIDENCE_VALID")
      throw Error("L12-6 protected review BLOCKED: "+result.errors.join(", "));
    console.log("L12_B6_EXTERNAL_REVIEW_VALID "+JSON.stringify(result));
  }else{
    console.log("L12_B6_REVIEW_PACKET "+JSON.stringify({
      sourceSha:sha,reviewSetDigest:data.packet.reviewSetDigest,
      ...data.packet.statistics,status:"NO_EXTERNAL_REVIEW_APPROVAL"}));
  }
}
