#!/usr/bin/env node
/**
 * L12-4: isolated Android aapt2 compile staging of MODEL-UNREVIEWED candidate XML.
 * This does not mutate app/src/main/res or attest semantic correctness.
 */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {mkdirSync,writeFileSync,readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const sha=x=>"sha256:"+createHash("sha256").update(x,"utf8").digest("hex");
const QU={"zh-TW":"values-b+zh+Hant+TW",ja:"values-ja"};
const concerns=[
  {code:"LITERAL_PERCENT_REVIEW",check:r=>r.structuralAlerts.includes("PERCENT_LITERAL_ANDROID_REVIEW"),
    reason:"Percent token requires reviewer and runtime formatting contract check; aapt2 compile is not enough"},
  {code:"UNCHANGED_ENGLISH_REVIEW",check:r=>r.structuralAlerts.includes("UNCHANGED_ENGLISH_REVIEW"),
    reason:"Possibly a name or deliberate source identifier, not assumed approved fallback"},
  {code:"XML_ESCAPING_REVIEW",check:r=>/[<>&'"\\]/.test(r.candidateText),
    reason:"Entity/quote/backslash staging transformation needs rendered UI confirmation"}
];
export function triageWarnings(preflight){
  assert.equal(preflight.schema,"acx-l12-b3-resource-preflight/1");
  const rows=[];
  for(const row of preflight.review){
    const flags=concerns.filter(c=>c.check(row));
    if(flags.length)rows.push({
      key:row.key,locale:row.locale,sourceHash:row.sourceHash,
      translationHash:row.translationHash,reviewRevision:row.reviewRevision,
      sourcePreview:row.sourcePreview,candidateText:row.candidateText,
      flags:flags.map(f=>f.code),reasons:flags.map(f=>f.reason),
      actionSensitive:row.riskPriority!=="GENERAL_UI",
      suggestedDisposition:"HUMAN_REVIEW_REQUIRED_NOT_APPROVED",
      reviewedBy:null,approved:false,
      compilerPassWillNotDismissWarning:true
    });
  }
  const countOf=tag=>rows.filter(r=>r.flags.includes(tag)).length;
  return {schema:"acx-l12-b4-warning-triage/1",sourceSha:preflight.sourceSha,
    stats:{rows:rows.length,percent:countOf("LITERAL_PERCENT_REVIEW"),
      unchangedEnglish:countOf("UNCHANGED_ENGLISH_REVIEW"),
      xmlEscaping:countOf("XML_ESCAPING_REVIEW"),
      authorizedDecisions:0},
    authority:"NONE_MODEL_RULES_CANNOT_DISMISS_REVIEW_WARNINGS",rows};
}
export function buildStagingResources(preflight){
  assert.equal(preflight.schema,"acx-l12-b3-resource-preflight/1");
  if(preflight.stats.structuralHardHolds!==0)
    throw Error("L12-B4 refuses AAPT2 staging with hard structural holds");
  if(preflight.stats.sourceVersionMismatches!==0 ||
     preflight.stats.androidArgumentMismatches!==0)
    throw Error("L12-B4 source or positional format drift");
  if(preflight.review.length!==preflight.stats.candidateLocaleRows)
    throw Error("L12-B4 inconsistent review row count");
  const rowsByLocale={ja:[],"zh-TW":[]};
  const keys=new Set();
  for(const r of preflight.review){
    if(!rowsByLocale[r.locale])throw Error("Unsupported stage locale");
    const id=r.key+"\0"+r.locale;
    if(keys.has(id))throw Error("Duplicate resource "+id);
    keys.add(id);
    if(r.independentApproval!==false||r.mayWriteOfficialXml!==false)
      throw Error("L12-B4 cannot stage authoritatively approved resources");
    if(!/^<string name="[a-z][a-z0-9_]*">[\s\S]*<\/string>$/.test(r.escapedXmlReviewSnippet))
      throw Error("Invalid staged string fragment "+id);
    rowsByLocale[r.locale].push(r);
  }
  const files={};
  for(const locale of ["zh-TW","ja"]){
    const rows=rowsByLocale[locale].slice().sort((a,b)=>a.key.localeCompare(b.key,"en"));
    const content='<?xml version="1.0" encoding="utf-8"?>\n'+
      '<!-- REVIEW-ONLY. GENERATED MODEL DRAFTS. NEVER COPY TO PRODUCTION. -->\n'+
      "<resources>\n"+rows.map(x=>"  "+x.escapedXmlReviewSnippet).join("\n")+
      "\n</resources>\n";
    files[QU[locale]+"/strings.xml"]=content;
  }
  const manifest={schema:"acx-l12-b4-aapt2-stage-manifest/1",
    sourceSha:preflight.sourceSha,catalogueRevision:preflight.catalogueRevision,
    status:"UNAPPROVED_ISOLATED_COMPILER_STAGING_ONLY",
    fullAndroidResourcesCompiled:false,actualAapt2Result:"RUN_CI_TOOL_SEPARATELY",
    sourceStringsXmlMutated:false,independentSemanticApproval:false,
    fileSha256:Object.fromEntries(Object.entries(files).map(([p,x])=>[p,sha(x)])),
    candidatesByLocale:Object.fromEntries(Object.entries(rowsByLocale).map(([l,a])=>[l,a.length])),
    warning:"Even aapt2 success on isolated staging does NOT compile all existing App resources, verify use at runtime or approve any translation"
  };
  return {files,manifest};
}
export function makeWarningTsv(result){
  return ["key","locale","source_hash","translation_hash","review_revision","flags","source_text","translation",
    "disposition"].join("\t")+"\n"+result.rows.map(x=>[
    x.key,x.locale,x.sourceHash,x.translationHash,x.reviewRevision,x.flags.join(";"),
    x.sourcePreview,x.candidateText,x.suggestedDisposition
  ].map(y=>JSON.stringify(y)).join("\t")).join("\n")+"\n";
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let commit="unavailable";
  try{commit=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const arg=process.argv.indexOf("--out-dir"),
    dest=arg>=0?resolve(process.argv[arg+1]??""):join(ROOT,"build/localization/L12_B4_AAPT2_ISOLATED_UNAPPROVED");
  if(!dest.startsWith(ROOT+"/")&&!dest.startsWith(ROOT+"\\"))throw Error("Stage must live under repository build");
  const x=loadL11Inputs(ROOT,commit),l11=buildL11(x),matrix=generateMatrix(ROOT),
    parse=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8")),
    glossary=parse("localization/governance/g1/glossary-contract.json"),
    reviewDecisions=parse("localization/governance/l12/independent-review-ledger.json"),
    g4Ledger=parse("localization/governance/g4/review-ledger.json"),
    l12=buildL12({l11,graph:x.graph,matrix,glossary});
  const preflight=stageSourceRows({l11,l12,matrix,reviewDecisions,glossary,g4Ledger});
  const {files,manifest}=buildStagingResources(preflight);
  const triage=triageWarnings(preflight);
  mkdirSync(join(dest,"res"),{recursive:true});
  for(const [path,content] of Object.entries(files)){
    const full=join(dest,"res",path);
    mkdirSync(dirname(full),{recursive:true});
    writeFileSync(full,content);
  }
  writeFileSync(join(dest,"aapt2-isolated-staging-manifest.json"),JSON.stringify(manifest,null,2)+"\n");
  writeFileSync(join(dest,"warning-triage-review.json"),JSON.stringify(triage,null,2)+"\n");
  writeFileSync(join(dest,"warning-triage-review.tsv"),makeWarningTsv(triage));
  console.log("L12_B4_AAPT2_STAGE "+JSON.stringify({sourceSha:commit,...manifest.candidatesByLocale,
    total:preflight.stats.candidateLocaleRows,warnings:triage.stats}));
}
