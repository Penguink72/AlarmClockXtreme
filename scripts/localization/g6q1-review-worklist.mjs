#!/usr/bin/env node
/* G6-Q1: complete 400-row human-review handoff, 37-key model triage.
 * Never approve content, change Android resources, or unlock release. */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve,sep} from "node:path";
import {fileURLToPath} from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import {shaSource} from "./lp0-audit.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=path=>JSON.parse(readFileSync(join(root,path),"utf8"));
const contentHash=text=>"sha256:"+createHash("sha256").update(text,"utf8").digest("hex");
const currentRevision="g6-200-current-20261120";
const locales=["zh-TW","ja"];
const g6Dir="build/localization/G6_UNREVIEWED_CURRENT_PREVIEW";
const validPriority=["CRITICAL","HIGH","MEDIUM"];
const allowedIssue=/^[A-Z][A-Z0-9_]+$/;

export function loadQ1Inputs(){
 return {
  catalog:read("app/src/debug/assets/lp2/catalog.json"),
  packs:Object.fromEntries(locales.map(locale=>[locale,read("app/src/debug/assets/lp2/packs/"+locale+".json")])),
  queue:read(g6Dir+"/current-review-queue.json"),
  manifest:read(g6Dir+"/manifest.json"),
  triage:read("localization/governance/g6q/model-triage-q1.json"),
  matrix:read("localization/governance/lp0/source-matrix.json"),
  s0:read("localization/governance/s0/source-amendments.json"),
  g4:read("localization/governance/g4/review-ledger.json"),
  glossary:read("localization/governance/g1/glossary-contract.json"),
  high:read("localization/governance/g4/legacy-high-priority-triage.json"),
  remaining:read("localization/governance/g4/legacy-remaining-triage.json"),
  g2:read("localization/governance/g2/context-evidence.json")
 };
}
const rowId=(locale,key)=>locale+"\u0000"+key;
const proposalFromG4=(high,remaining)=>{
 const map=new Map();
 for(const r of high.records){
  if(!r.suggestedReplacement)continue;
  const p=r.suggestedReplacement;
  map.set(rowId(p.locale,r.key),p.text);
 }
 for(const r of remaining.records){
  for(const [locale,text] of Object.entries(r.suggestions||{}))
    map.set(rowId(locale,r.key),text);
 }
 return map;
};
export function auditG6Q1(data){
 const {catalog,packs,queue,manifest,triage,matrix,s0,g4,glossary,high,remaining,g2}=data;
 assert.equal(catalog.revision,currentRevision);
 assert.equal(catalog.count,200);
 assert.equal(catalog.entries.length,200);
 assert.equal(manifest.currentCatalogRevision,currentRevision);
 assert.equal(manifest.localeCandidateRows,400);
 assert.equal(manifest.semanticallyApproved,0);
 assert.equal(manifest.mayDistributeAsOfficial,false);
 assert.equal(queue.schema,"acx-g6-current-review-queue/1");
 assert.equal(queue.catalogRevision,currentRevision);
 assert.equal(queue.reviewAuthority,"NONE");
 assert.equal(queue.humanApprovedCount,0);
 assert.equal(queue.items.length,400);
 assert.equal(g4.approvals.length,0);
 assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
 assert.equal(triage.schema,"acx-g6q1-model-preliminary-triage/1");
 assert.equal(triage.catalogRevision,currentRevision);
 assert.equal(triage.sourceCommit,catalog.baseCommit);
 assert.equal(triage.authority,"MODEL_PRELIMINARY_ONLY");
 assert.equal(triage.humanApproval,false);
 assert.equal(triage.independentApprovals,0);
 assert.equal(triage.records.length,37);
 assert.equal(g2.records.length,200);
 assert.equal(matrix.records.length,200);
 assert.equal(s0.items.length,1);
 assert.equal(s0.items[0].key,"music_playlist_integration_pending");
 for(const locale of locales){
  assert.equal(packs[locale].catalogRevision,currentRevision);
  assert.equal(Object.keys(packs[locale].translations).length,200);
  assert.equal(packs[locale].packVersion,"g6-UNREVIEWED-PREVIEW-NOT-RELEASE");
 }
 const byKey=new Map(catalog.entries.map(e=>[e.key,e]));
 const baseline=new Map(matrix.records.map(e=>[e.key,e]));
 const byCtx=new Map(g2.records.map(e=>[e.key,e]));
 const g4Suggestions=proposalFromG4(high,remaining);
 assert.equal(byKey.size,200);
 assert.equal(baseline.size,200);
 assert.equal(byCtx.size,200);
 const priorityTotals={CRITICAL:0,HIGH:0,MEDIUM:0};
 const triageBy=new Map();
 let priorProposals=0,newProposals=0,sourceAmended=0;
 for(const item of triage.records){
  assert(byKey.has(item.key),"Unknown Q1 key "+item.key);
  assert(!triageBy.has(item.key),"Duplicate Q1 entry "+item.key);
  triageBy.set(item.key,item);
  const source=byKey.get(item.key);
  const baselineSource=baseline.get(item.key);
  const change=s0.items.find(a=>a.key===item.key);
  const sourceHash=shaSource(item.key,source.source);
  assert.equal(sourceHash,item.sourceHash,"Stale Q1 source hash "+item.key);
  assert.equal(item.source,source.source,"Q1 source changed "+item.key);
  assert.equal(item.section,source.section);
  assert(validPriority.includes(item.priority));
  priorityTotals[item.priority]++;
  assert.match(item.issueCode,allowedIssue);
  assert(item.reviewRationale.trim().length>=18,"Weak model review rationale "+item.key);
  assert.equal(item.reviewerType,"MODEL_PRELIMINARY_ONLY");
  assert.equal(item.humanApproved,false);
  assert.equal(item.humanApproval,null);
  assert.equal(item.sourceReviewNeeded,!!change);
  if(change){
   sourceAmended++;
   assert.equal(sourceHash,change.currentSourceHash);
   assert.equal(item.status,"SOURCE_AMENDED_HUMAN_REVIEW_REQUIRED");
   assert.notEqual(baselineSource.sourceHash,sourceHash);
  }else{
   assert.equal(sourceHash,baselineSource.sourceHash);
   assert.equal(baselineSource.source,source.source);
  }
  for(const locale of locales){
   const text=packs[locale].translations[item.key];
   assert.equal(item.candidates[locale],text,"Stale Q1 candidate "+locale+"/"+item.key);
   assert.deepEqual(core.placeholders(text),source.placeholders);
   const origin=change?"S0_SOURCE_AMENDED":
       baselineSource.translations[locale].text===null?"LLM_DRAFT_UNREVIEWED":"LEGACY_UNREVIEWED";
   assert.equal(item.candidateOrigins[locale],origin);
  }
  const proposals=Object.entries(item.proposedReplacements);
  assert.equal(item.status,change?"SOURCE_AMENDED_HUMAN_REVIEW_REQUIRED":
    proposals.length?"REVISION_PROPOSED_HUMAN_REVIEW_REQUIRED":"CONTEXT_SEMANTIC_REVIEW_REQUIRED");
  for(const [locale,proposal] of proposals){
   assert(locales.includes(locale),"Unexpected locale "+locale);
   assert(proposal.text.trim() && proposal.text!==item.candidates[locale],"Not a revision");
   assert.deepEqual(core.placeholders(proposal.text),source.placeholders,
     "Proposed printf format mismatch "+locale+"/"+item.key);
   if(proposal.origin==="G4_PRIOR_MODEL_PROPOSAL"){
    assert.equal(proposal.text,g4Suggestions.get(rowId(locale,item.key)),
      "Proposed text is not the original G4 note: "+item.key);
    priorProposals++;
   }else if(proposal.origin==="G6Q1_MODEL_PROPOSAL"){
    assert(["notif_snooze_countdown_title","notif_snooze_countdown_subtext"].includes(item.key),
      "Unauthorized new suggestion outside Q1 subject");
    assert.equal(locale,"zh-TW");
    newProposals++;
   }else assert.fail("Unrecognized proposal provenance "+proposal.origin);
  }
 }
 assert.deepEqual(priorityTotals,{CRITICAL:10,HIGH:19,MEDIUM:8});
 assert.equal(sourceAmended,1);
 assert.equal(priorProposals,16);
 assert.equal(newProposals,2);
 assert.deepEqual(triage.stats,{
  selectedKeys:37,selectedLocaleRows:74,oldLegacySuggestions:16,
  newSuggestions:2,sourceAmendedKeys:1,independentApprovals:0
 });
 const queueBy=new Map();
 for(const item of queue.items){
  const id=rowId(item.locale,item.key);
  assert(!queueBy.has(id),"Duplicate G6 locale row "+id);
  queueBy.set(id,item);
  const source=byKey.get(item.key);
  assert(source);
  assert(locales.includes(item.locale));
  assert.equal(item.currentSourceHash,shaSource(item.key,source.source));
  assert.equal(item.text,packs[item.locale].translations[item.key]);
  assert.deepEqual(core.placeholders(item.text),source.placeholders);
  assert.equal(item.semanticApproved,false);
  assert.equal(item.humanApproval,null);
 }
 assert.equal(queueBy.size,400);
 const list=[];
 for(const item of catalog.entries){
  const ctx=byCtx.get(item.key);
  assert(ctx,"Missing generated G2 context "+item.key);
  const ctxHash=shaSource(item.key,item.source);
  const contextOutdated=ctx.sourceHash!==ctxHash;
  const change=s0.items.find(s=>s.key===item.key);
  assert.equal(contextOutdated,!!change,"Unexpected G2 source revision mismatch "+item.key);
  for(const locale of locales){
   const original=queueBy.get(rowId(locale,item.key));
   const a=triageBy.get(item.key);
   const prop=a?.proposedReplacements?.[locale]||null;
   const candidate=original.text;
   list.push({
    key:item.key,section:item.section,locale,
    currentSource:item.source,currentSourceHash:original.currentSourceHash,
    candidate,translationDigest:contentHash(candidate),origin:original.origin,
    sourceChanged:original.sourceChanged,
    preliminaryModelTriage:a?.status||"NOT_YET_MODEL_TRIAGED",
    severity:a?.priority||"NOT_ASSESSED",
    issueCode:a?.issueCode||null,
    contextNote:a?.reviewRationale||null,
    proposal:prop?.text||null,proposalOrigin:prop?.origin||null,
    g2StaticCallsiteCount:ctx.sourceCallsites.length,
    g2EvidenceState:contextOutdated?"HISTORICAL_SOURCE_HASH_REBASE_REQUIRED":"GENERATED_STATIC_ONLY_NOT_SEMANTIC_REVIEW",
    requiresIndependentContextReview:true,
    requiresIndependentTermReview:true,
    independentApproved:false,humanApproval:null,
    status:"HUMAN_INDEPENDENT_REVIEW_REQUIRED"
   });
  }
 }
 assert.equal(list.length,400);
 assert.equal(list.filter(x=>x.preliminaryModelTriage!=="NOT_YET_MODEL_TRIAGED").length,74);
 assert.equal(list.filter(x=>x.proposal).length,18);
 assert.equal(list.filter(x=>x.sourceChanged).length,2);
 assert.equal(list.filter(x=>x.g2EvidenceState==="HISTORICAL_SOURCE_HASH_REBASE_REQUIRED").length,2);
 assert.equal(list.filter(x=>x.independentApproved).length,0);
 const summary={
  schema:"acx-g6q1-human-handoff-preview/1",
  revision:currentRevision,reviewedBy:"MODEL_PRELIMINARY_ONLY",
  totalLocaleRows:400,preliminarilyTriagedLocaleRows:74,notYetModelTriagedLocaleRows:326,
  sourceChangedLocaleRows:2,proposedLocaleRevisions:18,
  independentApprovals:0,glossaryReleaseApproved:false,
  mayDistributeAsOfficial:false,
  strictReleaseGate:"DENY"
 };
 return {summary,worklist:list};
}
const tsvCell=value=>{
 let t=String(value??"").replace(/[\t\r\n]+/g," ");
 if(/^\s*[=+\-@]/.test(t))t="'"+t;
 return t;
};
export function toTsv(rows){
 const columns=["locale","key","section","severity","currentSource","currentSourceHash",
  "candidate","translationDigest","preliminaryModelTriage","contextNote","proposal","proposalOrigin",
  "g2EvidenceState","Independent reviewer ID (blank)","Reviewer decision (blank)",
  "Reviewed at (blank)","Term and source context checked (blank)","Reviewer rationale (blank)"];
 const vals=x=>[x.locale,x.key,x.section,x.severity,x.currentSource,x.currentSourceHash,
 x.candidate,x.translationDigest,x.preliminaryModelTriage,x.contextNote,x.proposal,x.proposalOrigin,
 x.g2EvidenceState,"","","","",""];
 return [columns.join("\t"),...rows.map(x=>vals(x).map(tsvCell).join("\t"))].join("\n")+"\n";
}
function main(){
 const args=process.argv;
 if(args.includes("--mode") && args[args.indexOf("--mode")+1]!=="preview")
  throw Error("G6-Q1 RELEASE BLOCKED: cannot transform model preliminary review into human approval");
 const output=auditG6Q1(loadQ1Inputs());
 const i=args.indexOf("--out-dir");
 if(i>=0){
  const dir=resolve(root,args[i+1]||"");
  assert(dir.startsWith(root+sep),"Cannot write outside repo");
  mkdirSync(dir,{recursive:true});
  writeFileSync(join(dir,"manifest.json"),JSON.stringify(output.summary,null,2)+"\n");
  writeFileSync(join(dir,"human-review-worklist.json"),JSON.stringify({
   schema:"acx-g6q1-human-review-worklist/1",...output.summary,items:output.worklist
  },null,2)+"\n");
  writeFileSync(join(dir,"human-review-worklist.tsv"),toTsv(output.worklist));
  writeFileSync(join(dir,"README.txt"),
   "G6-Q1 400-row human review handoff; 37 keys/74 locale rows have model-only preliminary findings.\n"+
   "All source and candidate hashes were checked; 18 proposed locale revisions are NOT applied to Android packs.\n"+
   "Human reviewer columns are empty; do not fill automatically. G1 term approval is still missing.\n"+
   "Independent approvals: ZERO; release remains BLOCKED; no notification or alarm-service modifications.\n");
 }
 console.log("G6Q1_REVIEW_WORKLIST_PASS "+JSON.stringify(output.summary));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
