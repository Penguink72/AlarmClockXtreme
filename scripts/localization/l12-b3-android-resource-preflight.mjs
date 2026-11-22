#!/usr/bin/env node
/**
 * L12-3: full-app Android resource staging QA and evidence-locked release gate.
 * Candidate XML is escaped as REVIEW PREVIEW TEXT ONLY; no app/res mutation.
 * Model-authored candidates and source probes can NEVER grant release authority.
 */
import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const allowedLocales=["zh-TW","ja"];
const idOf=x=>x.key+"\u0000"+x.locale;
const arg=/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g;
const tokenize=x=>(x.match(arg)||[]).sort();
const eq=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const shaRe=/^sha256:[a-f0-9]{64}$/;
/**
 * XML text preview. Convert raw text into entity-escaped, Android-quoted text.
 * Not evidence that aapt2 accepts every format or markup combination.
 */
export function androidXmlPreviewText(raw){
  if(typeof raw!=="string")throw Error("XML preview requires string");
  let result="";
  for(let i=0;i<raw.length;i++){
    const c=raw[i],previous=raw[i-1];
    if(c==="&")result+="&amp;";
    else if(c==="<")result+="&lt;";
    else if(c===">")result+="&gt;";
    else if((c==="'"||c==='"')&&previous!=="\\")result+="\\"+c;
    else result+=c;
  }
  if(result.startsWith("@")||result.startsWith("?"))result="\\"+result;
  return result;
}
export function stageSourceRows({l11,l12,matrix,reviewDecisions,glossary,g4Ledger}){
  assert.equal(l11.schema,"acx-l11-full-locale-candidate-queue/1");
  assert.equal(l12.schema,"acx-l12-evidence-worklist/1");
  assert.equal(matrix.schema,"acx-b0-trilingual-canonical-matrix/1");
  assert.equal(reviewDecisions.schema,"acx-l12-b3-independent-review/1");
  assert.equal(glossary.schema,"acx-glossary-contract/1");
  assert.equal(g4Ledger.schema,"acx-g4-review-ledger/1");
  assert.equal(l11.sourceSha,l12.sourceSha);
  assert.equal(l11.catalogueRevision,l12.catalogueRevision);
  assert.equal(matrix.catalogueRevision,l11.catalogueRevision);
  assert.equal(glossary.glossaryRevision,l12.glossaryRevision);
  assert.equal(g4Ledger.catalogRevision,glossary.catalogRevision);
  assert.equal(g4Ledger.glossaryRevision,glossary.glossaryRevision);
  if(reviewDecisions.approvals.length||reviewDecisions.rejections.length)
    throw Error("Independent review entries require external authenticated review intake, not an L12 model/CI-generated file");
  const sources=new Map(matrix.rows.map(x=>[x.key,x]));
  const required=new Set(l11.queue.map(idOf));
  const seen=new Set(),byLanguage={"zh-TW":0,ja:0},samples=[],review=[];
  let hardHolds=0,needsReview=0,sourceVersionMismatch=0,formatMismatch=0,xmlAttention=0;
  const reasons={};
  for(const row of l12.records){
    const id=idOf(row);
    if(seen.has(id))throw Error("Duplicate L12 review row "+id);
    seen.add(id);
    if(!required.has(id))throw Error("L12 review contains unexpected locale/key "+id);
    if(!allowedLocales.includes(row.locale))throw Error("Unexpected L12 locale "+row.locale);
    const source=sources.get(row.key);
    if(!source)throw Error("No canonical source for "+id);
    const alerts=[...row.issues];
    if(row.sourceHash!==source.sourceHash){
      alerts.push("SOURCE_REVISION_MISMATCH_HOLD");sourceVersionMismatch++;
    }
    if(!eq(tokenize(row.sourcePreview),tokenize(row.translation))){
      alerts.push("ANDROID_FORMAT_ARGUMENTS_HOLD");formatMismatch++;
    }
    if(!/^[a-z][a-z0-9_]*$/.test(row.key.replace(/^string:/,"")))
      alerts.push("ANDROID_RESOURCE_NAME_HOLD");
    if(row.sourceContainsRichMarkup)alerts.push("RICH_RESOURCE_MANUAL_XML_REVIEW");
    if(/\\u(?![a-fA-F0-9]{4})/.test(row.translation))
      alerts.push("BAD_ANDROID_UNICODE_ESCAPE_HOLD");
    if(/(?<!\\)@[a-zA-Z]/.test(row.translation))
      alerts.push("POSSIBLE_ANDROID_RESOURCE_REFERENCE_REVIEW");
    if(/[<>&'"\\]/.test(row.translation))xmlAttention++;
    const holds=alerts.some(x=>x.endsWith("_HOLD"));
    if(holds)hardHolds++;
    if(alerts.length)needsReview++;
    for(const issue of alerts)reasons[issue]=(reasons[issue]||0)+1;
    const escaped=androidXmlPreviewText(row.translation);
    const snippet="<string name=\""+row.key.replace(/^string:/,"")+"\">"+
      escaped+"</string>";
    const evidence={
      key:row.key,locale:row.locale,sourceHash:row.sourceHash,
      translationHash:row.translationHash,reviewRevision:row.reviewRevision,
      candidateText:row.translation,escapedXmlReviewSnippet:snippet,
      sourcePreview:row.sourcePreview,sourcePath:row.sourcePath,sourceLine:row.sourceLine,
      callsiteCount:row.bindingCount,sourceContainsRichMarkup:row.sourceContainsRichMarkup,
      riskPriority:row.reviewPriority,glossaryHints:row.glossaryHints,
      structuralAlerts:[...new Set(alerts)],
      mechanicalPreflight:holds?"HOLD":"CANDIDATE_ONLY_AAPT2_NOT_RUN",
      semanticDecision:"INDEPENDENT_REVIEW_REQUIRED",
      independentApproval:false,compilerVerification:false,
      runtimeValidation:false,mayWriteOfficialXml:false
    };
    byLanguage[row.locale]++;
    review.push(evidence);
    if(samples.length<120)samples.push({
      key:evidence.key,locale:evidence.locale,preview:snippet,
      warning:"EXAMPLE_ONLY_NOT_APPROVED_NOT_AAPT2_VALIDATED"
    });
  }
  if(seen.size!==l12.records.length)throw Error("L12 stage denominator mismatch");
  if(!l11.queue.every(x=>allowedLocales.includes(x.locale)))throw Error("L11 unsupported locale");
  const missingRows=l11.queue.filter(x=>!seen.has(idOf(x))).length;
  const stats={
    totalMissingLocaleDefinitions:l11.queue.length,
    candidateLocaleRows:review.length,
    stillMissingCandidateRows:missingRows,
    byLocale:byLanguage,structuralHardHolds:hardHolds,
    rowsWithReviewAlerts:needsReview,sourceVersionMismatches:sourceVersionMismatch,
    androidArgumentMismatches:formatMismatch,
    textNeedingXmlQuoteOrEntityAttention:xmlAttention,
    independentSemanticApproved:0,officialXmlWritten:0,
    releasedLanguagePacks:0,resourceCompilerPassed:0,
    androidRuntimeVerified:0,issues:reasons
  };
  return {
    schema:"acx-l12-b3-resource-preflight/1",
    sourceSha:l12.sourceSha,catalogueRevision:l11.catalogueRevision,
    glossaryRevision:glossary.glossaryRevision,
    status:"REVIEW_ONLY_NO_RELEASE",
    authority:"NO_INDEPENDENT_REVIEW_NO_AAPT2_ATTESTATION",
    inputDecisionLedgerStatus:reviewDecisions.status,
    G4OriginalReviewApproved:g4Ledger.approvals.length,
    warnings:[
      "XML snippets are escaped staging text for review ONLY, not compiled Android resources.",
      "Approval cannot be inferred from an existing static code probe or a matching glossary label.",
      "No independent reviewer or approved glossary currently exists.",
      "AAPT2 and Android UI/runtime tests are separate required gates.",
      "No generated candidate snippet is copied into app/src/main/res or official packs."
    ],stats,review,previewSamples:samples
  };
}
export function releasePreconditionViolations(report,decisions,glossary,g4Ledger,compilerAttestation=null){
  const failures=[];
  if(report.schema!=="acx-l12-b3-resource-preflight/1")failures.push("BAD_REPORT_SCHEMA");
  if(decisions.schema!=="acx-l12-b3-independent-review/1")failures.push("BAD_REVIEW_SCHEMA");
  if(glossary.approval!=="APPROVED_FOR_RELEASE")failures.push("GLOSSARY_NOT_INDEPENDENTLY_APPROVED");
  if(g4Ledger.approvals.length===0)failures.push("G4_INDEPENDENT_LEDGER_EMPTY");
  if(report.stats.stillMissingCandidateRows!==0)failures.push("TRANSLATIONS_MISSING");
  if(report.stats.structuralHardHolds!==0)failures.push("STRUCTURAL_HOLDS");
  if(!compilerAttestation || compilerAttestation.status!=="PASSED" ||
     compilerAttestation.sourceSha!==report.sourceSha ||
     !shaRe.test(compilerAttestation.compiledResourcesDigest??""))
    failures.push("AAPT2_COMPILATION_EVIDENCE_MISSING_OR_STALE");
  const byId=new Map(report.review.map(x=>[idOf(x),x]));
  const approvals=decisions.approvals||[];
  if(approvals.length!==report.stats.totalMissingLocaleDefinitions)
    failures.push("NOT_ALL_LOCALE_ROWS_INDEPENDENTLY_REVIEWED");
  const seen=new Set();
  for(const approval of approvals){
    const id=idOf(approval),row=byId.get(id);
    if(seen.has(id)){failures.push("DUPLICATE_INDEPENDENT_APPROVAL");continue;}
    seen.add(id);
    if(!row){failures.push("APPROVED_UNKNOWN_LOCALE_ROW");continue;}
    if(approval.sourceHash!==row.sourceHash ||
       approval.translationHash!==row.translationHash ||
       approval.reviewRevision!==row.reviewRevision)
      failures.push("STALE_OR_MISMATCHED_REVIEW_REVISION");
    if(approval.decision!=="APPROVED" ||
       approval.reviewMode!=="human-independent-semantic-review" ||
       !approval.meaningVerified || !approval.operationConsequencesVerified ||
       !approval.formatVerified || !approval.contextVerified ||
       typeof approval.reviewerId!=="string" || approval.reviewerId.trim().length<3 ||
       typeof approval.reviewNotes!=="string" || approval.reviewNotes.trim().length<16 ||
       !Number.isFinite(Date.parse(approval.reviewedAt)))
      failures.push("INDEPENDENT_REVIEW_EVIDENCE_INCOMPLETE");
  }
  return [...new Set(failures)];
}
export function assertNoUnauthorizedRelease(report,decisions,glossary,g4Ledger,compilerAttestation=null){
  const issues=releasePreconditionViolations(report,decisions,glossary,g4Ledger,compilerAttestation);
  if(issues.length)throw Error("L12-B3 official release blocked: "+issues.join(", "));
  // Even after satisfiable evidence, no production writer is implemented here.
  // L13 needs a separate protected release command, human authorization and review.
  throw Error("L12-B3 official release blocked: PROTECTED_L13_WRITER_NOT_IMPLEMENTED");
}
export function reviewTsv(report){
  const header=["priority","key","locale","source_hash","translation_hash","review_revision","source_path",
    "source_line","binding_count","issues","xml_review_snippet","candidate_text","reviewer","semantic_approval"];
  return header.join("\t")+"\n"+report.review.map(row=>[
    row.riskPriority,row.key,row.locale,row.sourceHash,row.translationHash,row.reviewRevision,
    row.sourcePath,row.sourceLine,row.callsiteCount,row.structuralAlerts.join(";"),
    row.escapedXmlReviewSnippet,row.candidateText,"",false
  ].map(x=>JSON.stringify(x??"")).join("\t")).join("\n")+"\n";
}
export function markdownSummary(report){
  const s=report.stats;
  return ["# L12-3｜Android XML/來源/正式核准閘門",
    "","**此產物不能發布為正式 XML 或語言包；無獨立批准，未執行 aapt2。**","",
    "- Source SHA: "+report.sourceSha,
    "- Review candidate locale rows: "+s.candidateLocaleRows+" / "+s.totalMissingLocaleDefinitions,
    "- Missing candidate locale rows: "+s.stillMissingCandidateRows,
    "- Structural hard holds: "+s.structuralHardHolds,
    "- Rows with format or content-review alerts: "+s.rowsWithReviewAlerts,
    "- Rows requiring XML escape/quotation attention: "+s.textNeedingXmlQuoteOrEntityAttention,
    "- Human independent approvals: 0; Android compile attestations: 0; official XML: 0",
    "","## Specific flags",...Object.entries(s.issues)
      .sort((a,b)=>a[0].localeCompare(b[0],"en")).map(([key,count])=>"- "+key+": "+count),
    "","## Gates to advance", "",
    "1. Obtain an independent human reviewer for each exact sourceHash, translationHash and reviewRevision.",
    "2. Approve/freeze G1 glossary and reconcile G4 review/known source defects.",
    "3. Resolve holds and ambiguity, including % placeholders, XML escapes, quotes, plurals and arrays.",
    "4. Run Android aapt2 on a release-intended resource staging set and bind a signed digest to the same version.",
    "5. Only a separately authorized L13 release writer may modify app/src/main/res or official language packs.",
    ""].join("\n");
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const read=path=>JSON.parse(readFileSync(join(ROOT,path),"utf8"));
  const x=loadL11Inputs(ROOT,sha),l11=buildL11(x),matrix=generateMatrix(ROOT),
    glossary=read("localization/governance/g1/glossary-contract.json"),
    g4=read("localization/governance/g4/review-ledger.json"),
    decisions=read("localization/governance/l12/independent-review-ledger.json"),
    l12=buildL12({l11,graph:x.graph,matrix,glossary});
  const out=stageSourceRows({l11,l12,matrix,reviewDecisions:decisions,glossary,g4Ledger:g4});
  if(process.argv.includes("--mode") && process.argv[process.argv.indexOf("--mode")+1]==="release"){
    assertNoUnauthorizedRelease(out,decisions,glossary,g4);
  }
  const i=process.argv.indexOf("--out-dir");
  const dir=i>=0?resolve(process.argv[i+1]??""):
    join(ROOT,"build/localization/L12_B3_REVIEW_ONLY_NOT_RELEASE");
  if(!dir.startsWith(ROOT+"/")&&!dir.startsWith(ROOT+"\\"))throw Error("Review output outside repo");
  mkdirSync(dir,{recursive:true});
  writeFileSync(join(dir,"l12-b3-resource-preflight.json"),JSON.stringify({
    ...out,previewSamples:undefined},null,2)+"\n");
  writeFileSync(join(dir,"l12-b3-review-queue.tsv"),reviewTsv(out));
  writeFileSync(join(dir,"l12-b3-xml-review-examples.json"),
    JSON.stringify(out.previewSamples,null,2)+"\n");
  writeFileSync(join(dir,"l12-b3-gate-summary.md"),markdownSummary(out));
  console.log("L12_B3_FORMAT_AND_RELEASE_GATES "+JSON.stringify(out.stats));
}
