#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {
  stageSourceRows,androidXmlPreviewText,
  releasePreconditionViolations,assertNoUnauthorizedRelease,reviewTsv,markdownSummary
} from "./l12-b3-android-resource-preflight.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8"));
let cache;
function fixture(){
  if(cache)return cache;
  const inputs=loadL11Inputs(ROOT,"L12-B3-TEST-SOURCE"),
    l11=buildL11(inputs),matrix=generateMatrix(ROOT),
    glossary=read("localization/governance/g1/glossary-contract.json"),
    g4Ledger=read("localization/governance/g4/review-ledger.json"),
    reviewDecisions=read("localization/governance/l12/independent-review-ledger.json"),
    l12=buildL12({l11,graph:inputs.graph,matrix,glossary});
  return cache={l11,l12,matrix,glossary,g4Ledger,reviewDecisions};
}
function report(){const f=fixture();return stageSourceRows({...f});}

test("L12-B3 supplies a complete per-locale review-only XML preflight with zero official approvals",()=>{
  const x=report();
  assert.equal(x.schema,"acx-l12-b3-resource-preflight/1");
  assert.equal(x.stats.totalMissingLocaleDefinitions,3100);
  assert.equal(x.stats.candidateLocaleRows,2680);
  assert.equal(x.stats.stillMissingCandidateRows,420);
  assert.equal(x.stats.byLocale["zh-TW"],1340);
  assert.equal(x.stats.byLocale.ja,1340);
  assert.equal(x.review.length,2680);
  assert.equal(x.stats.independentSemanticApproved,0);
  assert.equal(x.stats.officialXmlWritten,0);
  assert.equal(x.stats.resourceCompilerPassed,0);
  assert(x.review.every(x=>x.independentApproval===false&&x.mayWriteOfficialXml===false));
  assert(x.review.every(x=>x.sourceHash.startsWith("sha256:")));
  assert(x.review.every(x=>x.reviewRevision.startsWith("sha256:")));
  assert.match(reviewTsv(x),/review_revision/);
  assert.match(markdownSummary(x),/Android/);
});

test("L12-B3 review preview escapes XML entities and Android quotes but never advertises aapt2 proof",()=>{
  assert.equal(androidXmlPreviewText("You're <awake> & \"ready\""),
    "You\\'re &lt;awake&gt; &amp; \\\"ready\\\"");
  assert.equal(androidXmlPreviewText("@android:string/ok"),"\\@android:string/ok");
  assert.equal(androidXmlPreviewText("?attr/color"),"\\?attr/color");
  assert.equal(androidXmlPreviewText("100%% %1$d"),"100%% %1$d");
  assert.equal(androidXmlPreviewText("\\n"),"\\n");
  const r=report();
  assert(r.previewSamples.length>0);
  assert(r.previewSamples.every(x=>x.warning.includes("NOT_APPROVED")));
  assert(r.review.every(x=>x.mechanicalPreflight!=="AAPT2_PASSED"));
});

test("L12-B3 rejects model generated approvals, unknown/cross-version locale and duplicate review rows",()=>{
  const f=fixture();
  assert.throws(()=>stageSourceRows({...f,reviewDecisions:{
    ...f.reviewDecisions,approvals:[{key:"fake",locale:"ja"}]
  }}),/external authenticated review intake/);
  assert.throws(()=>stageSourceRows({...f,l12:{...f.l12,catalogueRevision:"changed"}}),
    /Expected values to be strictly equal/);
  assert.throws(()=>stageSourceRows({...f,l12:{
    ...f.l12,records:[f.l12.records[0],...f.l12.records]
  }}),/Duplicate L12 review row/);
});

test("L12-B3 refuses all official release attempts without a reviewer, approved G1/G4 and actual aapt2 evidence",()=>{
  const f=fixture(),r=report();
  const reasons=releasePreconditionViolations(r,f.reviewDecisions,f.glossary,f.g4Ledger);
  assert(reasons.includes("GLOSSARY_NOT_INDEPENDENTLY_APPROVED"));
  assert(reasons.includes("G4_INDEPENDENT_LEDGER_EMPTY"));
  assert(reasons.includes("TRANSLATIONS_MISSING"));
  assert(reasons.includes("AAPT2_COMPILATION_EVIDENCE_MISSING_OR_STALE"));
  assert(reasons.includes("NOT_ALL_LOCALE_ROWS_INDEPENDENTLY_REVIEWED"));
  assert.throws(()=>assertNoUnauthorizedRelease(r,f.reviewDecisions,f.glossary,f.g4Ledger),
    /official release blocked/);
});

test("L12-B3 a spoofed one-row independent approval cannot unlock official XML",()=>{
  const f=fixture(),r=report(),x=r.review[0];
  const spoofed={...f.reviewDecisions,approvals:[{
    key:x.key,locale:x.locale,sourceHash:x.sourceHash,translationHash:x.translationHash,
    reviewRevision:x.reviewRevision,decision:"APPROVED",
    reviewMode:"human-independent-semantic-review",meaningVerified:true,
    operationConsequencesVerified:true,formatVerified:true,contextVerified:true,
    reviewerId:"human-example",reviewNotes:"Some invented human rationale",
    reviewedAt:"2026-11-22T12:00:00Z"
  }]};
  const reasons=releasePreconditionViolations(r,spoofed,f.glossary,f.g4Ledger,{
    status:"PASSED",sourceSha:"stale",compiledResourcesDigest:"sha256:"+"0".repeat(64)
  });
  assert(reasons.includes("NOT_ALL_LOCALE_ROWS_INDEPENDENTLY_REVIEWED"));
  assert(reasons.includes("AAPT2_COMPILATION_EVIDENCE_MISSING_OR_STALE"));
  assert.throws(()=>assertNoUnauthorizedRelease(r,spoofed,f.glossary,f.g4Ledger),
    /official release blocked/);
});

test("L12-B3 review evidence never silently becomes a production Android resource XML",()=>{
  const r=report(),ids=new Set(r.review.map(x=>x.key+"\0"+x.locale));
  assert.equal(ids.size,r.review.length);
  assert(r.review.every(x=>x.escapedXmlReviewSnippet.startsWith("<string name=")));
  assert(r.review.every(x=>x.semanticDecision==="INDEPENDENT_REVIEW_REQUIRED"));
  assert(r.review.every(x=>x.mechanicalPreflight!=="PRODUCTION_RELEASE"));
  assert(r.warnings.some(x=>x.includes("copied into app/src/main/res")));
});
