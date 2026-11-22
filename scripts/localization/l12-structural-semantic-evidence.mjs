#!/usr/bin/env node
/* L12-P1. Mechanical content QA + semantic-review evidence, not approval. */
import {createHash} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const digest=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x),"utf8").digest("hex");
const format=/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g;
const sorted=a=>a.slice().sort((x,y)=>x.localeCompare(y,"en"));
const freq=a=>Object.fromEntries(sorted([...new Set(a)]).map(x=>[x,a.filter(y=>x===y).length]));
const ranks={DATA_LOSS_OR_RESTORE:0,ALARM_FIRING_CONTROL:1,
  SECURITY_PRIVACY_PERMISSION:2,MEDIA_SOURCE_PLAYBACK:3,GENERAL_UI:4};
export function risksFor(key,source){
  const txt=key+" "+source,out=[];
  if(/delete|remove|clear|reset|replace|restore|import|backup|erase/i.test(txt))out.push("DATA_LOSS_OR_RESTORE");
  if(/snooze|dismiss|alarmfiring|alarm_fired|realarm|wake_confirm|cancel|schedule|missed_alarm/i.test(txt))
    out.push("ALARM_FIRING_CONTROL");
  if(/webhook|secret|passphrase|private|privacy|permission|guardian|sms|contact|network|hue|certificate|health|read_sleep|sleep.data/i.test(txt))
    out.push("SECURITY_PRIVACY_PERMISSION");
  if(/playlist|music.library|ringtone|track|music.pool|media.source|playback.history|stream.url/i.test(txt))
    out.push("MEDIA_SOURCE_PLAYBACK");
  return out.length?out:["GENERAL_UI"];
}
function malformedBackslash(text){
  for(let i=0;i<text.length;i++){
    if(text[i]!=="\\")continue;
    const next=text[i+1];
    if(next===undefined)return true;
    if(next==="u"){
      if(!/^[0-9a-fA-F]{4}$/.test(text.slice(i+2,i+6)))return true;
      i+=5;
    }else if("ntr\\'\"@?".includes(next))i++;
    else return true;
  }
  return false;
}
function invalidUnicode(text){
  if(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/u.test(text))return true;
  for(let i=0;i<text.length;i++){
    const c=text.charCodeAt(i);
    if(c>=0xD800&&c<=0xDBFF){
      const d=text.charCodeAt(i+1);
      if(!(d>=0xDC00&&d<=0xDFFF))return true;
      i++;
    }else if(c>=0xDC00&&c<=0xDFFF)return true;
  }
  return false;
}
export function checkCandidate(row,source){
  const issues=[],text=row.translation;
  if(row.sourceHash!==source.sourceHash||row.sourcePreview!==source.sourcePreview)
    issues.push("SOURCE_STALE_HOLD");
  if(!source.translatable||row.targetExists)issues.push("EXISTING_XML_HOLD");
  if(typeof text!=="string"||!text.trim()){
    issues.push("EMPTY_TRANSLATION_HOLD");
    return {issues,mechanicalPreviewOnly:false};
  }
  if(JSON.stringify(freq(source.sourcePreview.match(format)||[]))!==
     JSON.stringify(freq(text.match(format)||[])))issues.push("FORMAT_MULTIPLICITY_HOLD");
  if(invalidUnicode(text))issues.push("ILLEGAL_UNICODE_HOLD");
  if(malformedBackslash(text))issues.push("ANDROID_BACKSLASH_HOLD");
  if((source.sourcePreview.match(/\\n/g)||[]).length!==(text.match(/\\n/g)||[]).length)
    issues.push("LINEBREAK_REVIEW");
  if(source.hasRichMarkup)issues.push("RICH_XML_STRUCTURE_REVIEW");
  if(/%(?![1-9]\d*\$|[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%])/.test(text))
    issues.push("PERCENT_LITERAL_ANDROID_REVIEW");
  if(text.trim()===source.sourcePreview.trim())issues.push("UNCHANGED_ENGLISH_REVIEW");
  return {issues,mechanicalPreviewOnly:!issues.some(x=>x.endsWith("_HOLD"))&&!source.hasRichMarkup};
}
function glossaryHints(row,glossary){
  return glossary.concepts.filter(c=>{
    if((c.matchedKeys||[]).includes(row.key.slice("string:".length)))return true;
    if(["Alarm","Track","Save","Repeat"].includes(c.sourceTerm))return false;
    const term=c.sourceTerm;
    if(!/^[a-z ]+$/i.test(term))return false;
    return row.sourcePreview.toLowerCase().includes(term.toLowerCase());
  }).map(c=>({
    id:c.id,risk:c.risk,expectedCandidate:c.labels[row.locale],
    matchedBy:(c.matchedKeys||[]).includes(row.key.slice("string:".length))?
      "G1_KEY_HEURISTIC":"SOURCE_TEXT_HEURISTIC",
    targetContainsCandidateLabel:row.translation.includes(c.labels[row.locale]),
    labelAuthority:"G1_DRAFT_CANDIDATE_ONLY"
  }));
}
export function buildL12({l11,graph,matrix,glossary}){
  if(l11.schema!=="acx-l11-full-locale-candidate-queue/1"||
     graph.schema!=="acx-l10-source-context-binding-surface/1"||
     matrix.schema!=="acx-b0-trilingual-canonical-matrix/1"||
     glossary.schema!=="acx-glossary-contract/1")
    throw Error("L12 input schema not recognized");
  if(l11.catalogueRevision!==graph.catalogueRevision||
     l11.catalogueRevision!==matrix.catalogueRevision)
    throw Error("L12 catalogue revision conflict");
  const bySource=new Map(matrix.rows.map(x=>[x.key,x]));
  const byBinding=new Map(graph.sources.map(x=>[x.key,x]));
  const records=[];
  const stats={totalCandidates:l11.review.length,byLocale:{"zh-TW":0,ja:0},
    mechanicalPreviewOnly:0,structuralHolds:0,reviewFlagRows:0,unmappedRows:0,
    actionSensitiveRows:0,glossaryHintRows:0,glossaryVariantHints:0,
    independentSemanticApproved:0,officialReleaseEligible:0,byIssue:{},byRisk:{}};
  for(const row of l11.review){
    if(row.reviewStatus!=="UNREVIEWED"||row.humanApproval!==false||
       row.independentApproval!==false)throw Error("L12 refuses model-issued approval "+row.key);
    const source=bySource.get(row.key),node=byBinding.get(row.key);
    if(!source||!node)throw Error("Missing source or binding "+row.key);
    const check=checkCandidate(row,source),risk=risksFor(row.key,row.sourcePreview),
      terms=glossaryHints(row,glossary);
    const bindings=node.bindings.map(x=>({
      path:x.path,line:x.line,symbolHint:x.symbolHint,surfaceHint:x.surfaceHint,
      bindingId:x.bindingId,bindingRevision:x.bindingRevision,
      behaviorProof:"NOT_VERIFIED_BY_STATIC_SCAN"
    }));
    const sourceHash=row.sourceHash,translationHash=digest([row.key,row.locale,row.translation]),
      reviewRevision=digest([sourceHash,translationHash,
        bindings.map(x=>x.bindingRevision),glossary.glossaryRevision]);
    const record={
      key:row.key,locale:row.locale,
      sourcePreview:row.sourcePreview,translation:row.translation,
      sourceHash,translationHash,reviewRevision,
      sourcePath:source.sourcePath,sourceLine:source.sourceLine,
      sourceFormatTokens:source.formatTokens,
      sourceContainsRichMarkup:source.hasRichMarkup,
      issues:check.issues,mechanicalPreviewOnly:check.mechanicalPreviewOnly,
      candidateOrigin:row.translationOrigin,reviewPriority:risk[0],
      risks:risk,glossaryRevision:glossary.glossaryRevision,glossaryHints:terms,
      bindingCount:bindings.length,bindingEvidence:bindings.slice(0,5),
      bindingsTruncated:bindings.length>5,
      contentDecision:"NOT_REVIEWED",behaviorProof:"NOT_VERIFIED",
      reviewerIdentity:null,reviewerTimestamp:null,approvalAuthority:null,
      independentSemanticApproval:false,officialReleaseEligible:false
    };
    records.push(record);
    stats.byLocale[row.locale]++;
    for(const f of check.issues)stats.byIssue[f]=(stats.byIssue[f]||0)+1;
    for(const f of risk)stats.byRisk[f]=(stats.byRisk[f]||0)+1;
    if(check.mechanicalPreviewOnly)stats.mechanicalPreviewOnly++;
    if(check.issues.some(x=>x.endsWith("_HOLD")))stats.structuralHolds++;
    if(check.issues.length)stats.reviewFlagRows++;
    if(!bindings.length)stats.unmappedRows++;
    if(risk[0]!=="GENERAL_UI")stats.actionSensitiveRows++;
    if(terms.length)stats.glossaryHintRows++;
    stats.glossaryVariantHints+=terms.filter(x=>!x.targetContainsCandidateLabel).length;
  }
  records.sort((a,b)=>ranks[a.reviewPriority]-ranks[b.reviewPriority]||
    a.key.localeCompare(b.key,"en")||a.locale.localeCompare(b.locale,"en"));
  if(stats.byLocale["zh-TW"]+stats.byLocale.ja!==stats.totalCandidates)
    throw Error("Incomplete L12 candidate denominator");
  return {
    schema:"acx-l12-evidence-worklist/1",
    sourceSha:l11.sourceSha,catalogueRevision:l11.catalogueRevision,
    glossaryRevision:glossary.glossaryRevision,
    authority:"STATIC_MECHANICAL_QA_WITH_UNAPPROVED_G1_GLOSSARY",
    releaseStatus:"BLOCKED_INDEPENDENT_SEMANTIC_REVIEW_MISSING",
    warnings:[
      "Mechanical preview eligibility is NOT Android aapt2 or device verification.",
      "No semantic approvals are granted by this model or its rules.",
      "A glossary variant is a review hint and not necessarily an incorrect translation.",
      "Static bindings do not establish runtime behavior or operation effects.",
      "Official Android XML, language packs, and APK remain unmodified."
    ],stats,records
  };
}
const cell=x=>JSON.stringify(x??"");
export function l12Tsv(result){
  const columns=["priority","key","locale","source_hash","translation_hash","review_revision",
    "issues","binding_count","binding_paths","glossary_candidates","source_preview",
    "translation","independent_semantic_approval"];
  return columns.join("\t")+"\n"+result.records.map(r=>[
    r.reviewPriority,r.key,r.locale,r.sourceHash,r.translationHash,r.reviewRevision,
    r.issues.join(";"),r.bindingCount,r.bindingEvidence.map(x=>x.path+":"+x.line).join(";"),
    r.glossaryHints.map(x=>x.id+":"+x.targetContainsCandidateLabel).join(";"),
    r.sourcePreview,r.translation,r.independentSemanticApproval
  ].map(cell).join("\t")).join("\n")+"\n";
}
export function l12Markdown(report){
  const s=report.stats;
  return [
    "# L12｜來源、Android 格式與操作語義審核證據",
    "",
    "> Preliminary mechanical/semantic-triage evidence only; no independent approval.",
    "- Source SHA: "+report.sourceSha,
    "- G1 glossary (unapproved): "+report.glossaryRevision,
    "- L11 candidate locale rows: "+s.totalCandidates,
    "- Mechanically preview-eligible (not released): "+s.mechanicalPreviewOnly,
    "- Structural holds: "+s.structuralHolds,
    "- Rows with any review flag: "+s.reviewFlagRows,
    "- High-risk rows: "+s.actionSensitiveRows,
    "- No static use mapping: "+s.unmappedRows,
    "- Glossary hint rows: "+s.glossaryHintRows,
    "- Glossary variant suggestions: "+s.glossaryVariantHints,
    "- Independent approvals: "+s.independentSemanticApproved,
    "",
    "## Automated issue counts",
    ...Object.entries(s.byIssue).sort((a,b)=>a[0].localeCompare(b[0],"en"))
      .map(([k,v])=>"- "+k+": "+v),
    "",
    "## L12 remaining gates",
    "1. Validate high-risk action text against actual Kotlin behavior, ownership, and approved glossary exceptions.",
    "2. Obtain a non-model independent reviewer and record source/target/review revisions and identity.",
    "3. Run Android resource compiler, text length, Compose/notification/service and locale runtime checks before release.",
    "4. L12 records are not official strings.xml, V1 language packs or an APK.",
    ""
  ].join("\n");
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const i=process.argv.indexOf("--out-dir");
  const out=i>=0?resolve(process.argv[i+1]??""):
    join(ROOT,"build/localization/L12_PRELIMINARY_QA_NOT_APPROVED");
  if(!out.startsWith(ROOT+"/")&&!out.startsWith(ROOT+"\\"))throw Error("Out dir outside repository");
  let sourceSha="unavailable";
  try{sourceSha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const inputs=loadL11Inputs(ROOT,sourceSha);
  const l11=buildL11(inputs);
  const matrix=generateMatrix(ROOT);
  const glossary=JSON.parse(readFileSync(join(ROOT,
    "localization/governance/g1/glossary-contract.json"),"utf8"));
  const result=buildL12({l11,graph:inputs.graph,matrix,glossary});
  mkdirSync(out,{recursive:true});
  writeFileSync(join(out,"l12-evidence-review-queue.json"),JSON.stringify(result,null,2)+"\n");
  writeFileSync(join(out,"l12-evidence-review-queue.tsv"),l12Tsv(result));
  writeFileSync(join(out,"l12-mechanical-summary.md"),l12Markdown(result));
  writeFileSync(join(out,"l12-review-authority.json"),JSON.stringify({
    schema:"acx-l12-review-authority/1",
    catalogueRevision:result.catalogueRevision,sourceSha:result.sourceSha,
    glossaryRevision:result.glossaryRevision,
    independentReviewRecords:[],approvedLocaleRows:0,
    authority:"NONE_MODEL_OUTPUT_CANNOT_APPROVE",
    status:"OFFICIAL_RELEASE_BLOCKED"
  },null,2)+"\n");
  console.log("L12_STRUCTURAL_AND_SEMANTIC_QA "+JSON.stringify(result.stats));
}
