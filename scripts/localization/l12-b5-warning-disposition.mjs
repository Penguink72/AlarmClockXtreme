#!/usr/bin/env node
/** L12-5: source-bound warning dispositions, separate from official semantic approval.
 * Returns review requests, never decisions or a production release artifact.
 */
import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {loadL11Inputs,buildL11} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
import {triageWarnings} from "./l12-b4-aapt2-staging.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const idOf=x=>x.key+"\0"+x.locale;
function classify(w){
  const categories=[];
  if(w.flags.includes("UNCHANGED_ENGLISH_REVIEW")){
    const normalized=w.candidateText.trim();
    // Keep structure-only format strings distinct from prose that was never translated.
    // These classifications are NOT permission to keep English in production.
    const formatOnly=normalized.replace(/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g,"")
      .replace(/[\s:;,.•+\-—()\\/'"%]+/g,"").length===0;
    const brandOrTechnical=/^(?:Wi-Fi|Philips Hue|Spotify|Health Connect|Webhook|DDNNO|DuPont|Panama|Pitman)$/i.test(normalized) ||
      /^(?:https?:\/\/\S+|[A-Z_][A-Z0-9_.-]{2,})$/.test(normalized);
    categories.push(formatOnly?"FORMAT_ONLY_NO_PROSE_REVIEW":
      brandOrTechnical?"POSSIBLE_IDENTIFIER_RETENTION_NEEDS_EVIDENCE":
      "LIKELY_UNTRANSLATED_ENGLISH_NEEDS_REVIEW");
  }
  if(w.flags.includes("LITERAL_PERCENT_REVIEW"))
    categories.push("ANDROID_PERCENT_FORMAT_RUNTIME_USAGE_REVIEW");
  if(w.flags.includes("XML_ESCAPING_REVIEW"))
    categories.push("ANDROID_XML_ESCAPE_AND_RENDER_REVIEW");
  if(w.actionSensitive)categories.push("ACTION_SEMANTICS_ESCALATION");
  return categories;
}
export function buildWarningDispositions(triage,preflight){
  assert.equal(triage.schema,"acx-l12-b4-warning-triage/1");
  assert.equal(preflight.schema,"acx-l12-b3-resource-preflight/1");
  assert.equal(triage.sourceSha,preflight.sourceSha);
  const live=new Map(preflight.review.map(r=>[idOf(r),r]));
  const seen=new Set(),items=[];
  for(const w of triage.rows){
    const id=idOf(w),source=live.get(id);
    if(seen.has(id)||!source)throw Error("Unknown or duplicate warning row "+id);
    seen.add(id);
    if(w.sourceHash!==source.sourceHash ||
      w.translationHash!==source.translationHash ||
      w.reviewRevision!==source.reviewRevision)throw Error("Stale warning source or translation "+id);
    if(w.approved!==false || w.reviewedBy!==null)
      throw Error("Upstream generated review cannot claim approval "+id);
    const categories=classify(w);
    if(categories.length===0)throw Error("Warning has no review classification "+id);
    items.push({
      key:w.key,locale:w.locale,sourceHash:w.sourceHash,
      translationHash:w.translationHash,reviewRevision:w.reviewRevision,
      sourceText:w.sourcePreview,candidateText:w.candidateText,
      observedFlags:w.flags,reviewCategories:categories,
      priorCompilerPassDoesNotApprove:true,
      requiredEvidence:w.actionSensitive?
        ["REAL_OPERATION_CONTEXT","INDEPENDENT_REVIEWER","ANDROID_RENDER_WHEN_APPLICABLE"]:
        ["SOURCE_CONTEXT","INDEPENDENT_REVIEWER_OR_APPROVED_REVIEW_POLICY","ANDROID_RENDER_WHEN_APPLICABLE"],
      reviewDisposition:"PENDING_INDEPENDENT_REVIEW",
      suggestedChange:null, reviewer:null,reviewedAt:null,
      independentApproval:false,mayRelease:false
    });
  }
  items.sort((a,b)=>a.key.localeCompare(b.key,"en")||a.locale.localeCompare(b.locale,"en"));
  return {
    schema:"acx-l12-b5-warning-disposition-queue/1",
    sourceSha:preflight.sourceSha,catalogueRevision:preflight.catalogueRevision,
    status:"WARNINGS_CLASSIFIED_NOT_RESOLVED",
    stats:{
      flaggedRows:items.length,
      flagOccurrences:items.reduce((a,x)=>a+x.observedFlags.length,0),
      byCategory:Object.fromEntries([...new Set(items.flatMap(x=>x.reviewCategories))].sort().map(c=>
        [c,items.filter(x=>x.reviewCategories.includes(c)).length])),
      approvedRows:0,warningRowsDismissed:0
    },
    items,limitations:[
      "Classification is not an independent human semantic review.",
      "aapt2 isolated compilation does not prove runtime rendering or effect semantics.",
      "Unchanged English may be a brand, product name, technical value or a missed translation.",
      "No generated row can change G1/G4 or a production XML/language pack."
    ]
  };
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const inp=loadL11Inputs(ROOT,sha),l11=buildL11(inp),matrix=generateMatrix(ROOT),
    parse=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8")),
    glossary=parse("localization/governance/g1/glossary-contract.json"),
    reviewDecisions=parse("localization/governance/l12/independent-review-ledger.json"),
    g4Ledger=parse("localization/governance/g4/review-ledger.json"),
    l12=buildL12({l11,graph:inp.graph,matrix,glossary}),
    preflight=stageSourceRows({l11,l12,matrix,reviewDecisions,glossary,g4Ledger}),
    result=buildWarningDispositions(triageWarnings(preflight),preflight);
  const flag=process.argv.indexOf("--out-dir");
  if(flag>=0){
    const target=resolve(process.argv[flag+1]??"");
    if(!target.startsWith(join(ROOT,"build")+"/"))throw Error("Output must remain under build/");
    mkdirSync(target,{recursive:true});
    writeFileSync(join(target,"l12-b5-review-disposition.json"),JSON.stringify(result,null,2)+"\n");
  }
  console.log("L12_B5_WARNING_DISPOSITION "+JSON.stringify({sourceSha:sha,...result.stats}));
  if(process.argv.includes("--list"))for(const r of result.items){
    console.log("L12_B5_WARNING "+JSON.stringify({key:r.key,locale:r.locale,flags:r.observedFlags,
      classifications:r.reviewCategories,source:r.sourceText,candidate:r.candidateText}));
  }
}
