#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {loadUnifiedInputs,validateSignedReviewIntake,reviewTsv} from "./l12-b6-independent-review.mjs";
let data;
const setup=()=>data??=loadUnifiedInputs(undefined,"L12-B6-TEST-REVISION");
function fakeSigned(rows){
  const {publicKey,privateKey}=generateKeyPairSync("ed25519");
  const p=setup().packet;
  const approvals=rows.map(r=>({
    key:r.key,locale:r.locale,type:r.type,sourceHash:r.sourceHash,
    translationHash:r.translationHash,reviewRevision:r.reviewRevision,
    decision:"APPROVED",reviewMode:"human-independent-semantic-review",
    meaningVerified:true,contextVerified:true,formatVerified:true,
    operationConsequencesVerified:true,reviewerId:"TEST_FIXTURE_ONLY",
    reviewNotes:"Test-only synthetic data; this is not a real translation approval.",
    reviewedAt:"2026-11-22T00:00:00.000Z"
  }));
  const payload={reviewSetDigest:p.reviewSetDigest,catalogueRevision:p.catalogueRevision,
    glossaryRevision:p.glossaryRevision,sourceSha:p.sourceSha,
    reviewPolicy:"INDEPENDENT_HUMAN_SEMANTIC_AND_OPERATION_REVIEW",approvals};
  const envelope={schema:"acx-l12-b6-signed-review-envelope/1",
    payload,signatureBase64:sign(null,Buffer.from(JSON.stringify(payload)),privateKey).toString("base64")};
  return {envelope,trustedPublicKeyPem:publicKey.export({type:"spki",format:"pem"})};
}
test("L12-6 unifies L11 2986 and typed residual 114 without granting approval",()=>{
  const x=setup(),p=x.packet;
  assert.equal(p.statistics.reviewRows,3100);
  assert.deepEqual(p.statistics.byType,{string:3036,plurals:62,array:2});
  assert.deepEqual(p.statistics.byLocale,{"zh-TW":1550,ja:1550});
  assert.equal(p.statistics.independentApproved,0);
  assert.equal(p.statistics.officialXmlWritten,0);
  assert(p.rows.every(x=>!x.independentApproved));
  assert.equal(new Set(p.rows.map(x=>x.key+"\0"+x.locale)).size,3100);
  assert.match(p.reviewSetDigest,/^sha256:[a-f0-9]{64}$/);
});
test("L12-6 preserves array, plural, and ordinary candidate types in evidence packet",()=>{
  const rows=setup().packet.rows;
  const arr=rows.filter(r=>r.type==="array"),pl=rows.filter(r=>r.type==="plurals");
  assert.equal(arr.length,2);assert.equal(pl.length,62);
  assert(arr.every(x=>x.candidate.length===15));
  assert(pl.every(x=>Object.keys(x.candidate).sort().join(",")==="one,other"));
  assert.equal(rows.filter(x=>x.type==="string").length,3036);
  assert(reviewTsv(setup().packet).startsWith("type\tkey\tlocale"));
});
test("L12-6 refuses missing external reviewer trust anchor",()=>{
  const x=setup(),signed=fakeSigned(x.packet.rows.slice(0,1));
  const r=validateSignedReviewIntake({packet:x.packet,
    envelope:signed.envelope,trustedPublicKeyPem:"",glossary:x.glossary,g4Ledger:x.g4Ledger});
  assert.equal(r.status,"BLOCKED");
  assert(r.errors.includes("PROTECTED_REVIEW_TRUST_ANCHOR_MISSING"));
});
test("L12-6 never accepts unapproved glossary or empty G4 ledger",()=>{
  const x=setup(),signed=fakeSigned(x.packet.rows.slice(0,1));
  const r=validateSignedReviewIntake({packet:x.packet,
    envelope:signed.envelope,trustedPublicKeyPem:signed.trustedPublicKeyPem,
    glossary:x.glossary,g4Ledger:x.g4Ledger});
  assert(r.errors.includes("G1_GLOSSARY_NOT_INDEPENDENTLY_APPROVED"));
  assert(r.errors.includes("G4_APPROVAL_LEDGER_EMPTY"));
  assert(r.errors.includes("INCOMPLETE_EXTERNAL_APPROVAL_SET"));
});
test("L12-6 detects revision drift and duplicate approvals even with invalid source bundles",()=>{
  const x=setup(),signed=fakeSigned(x.packet.rows.slice(0,1));
  const payload={...signed.envelope.payload,reviewSetDigest:"sha256:stale",
    approvals:[signed.envelope.payload.approvals[0],signed.envelope.payload.approvals[0]]};
  const r=validateSignedReviewIntake({packet:x.packet,
    envelope:{...signed.envelope,payload},
    trustedPublicKeyPem:signed.trustedPublicKeyPem,
    glossary:x.glossary,g4Ledger:x.g4Ledger});
  assert(r.errors.includes("STALE_SOURCE_OR_REVIEW_SET"));
  assert(r.errors.includes("INVALID_PROTECTED_REVIEW_SIGNATURE"));
  assert(r.errors.includes("DUPLICATE_APPROVAL"));
});
test("L12-6 cryptographic test fixtures validate mechanics only, not real reviewer identity",()=>{
  const x=setup(),r=x.packet.rows[0],synthetic={...x.packet,rows:[r]};
  const signed=fakeSigned([r]);
  // Keep the test fixture's reviewSetDigest; only the row denominator is narrowed.
  const result=validateSignedReviewIntake({
    packet:synthetic,envelope:signed.envelope,
    trustedPublicKeyPem:signed.trustedPublicKeyPem,
    glossary:{...x.glossary,approval:"APPROVED_FOR_RELEASE"},
    g4Ledger:{...x.g4Ledger,approvals:[{testFixtureOnly:true}]}
  });
  assert.equal(result.status,"INDEPENDENT_REVIEW_EVIDENCE_VALID");
  assert.equal(result.verifiedReviewRows,1);
  // The real repository's protected key is NOT this ephemeral test key.
});
