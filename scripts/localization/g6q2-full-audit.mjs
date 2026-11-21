#!/usr/bin/env node
/* G6-Q2: consolidate 37 Q1 and 163 Q2 bilingual preliminary key reviews.
 * The human approval ledger, official packs, and release gate stay unchanged. */
import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve,sep} from "node:path";
import {fileURLToPath} from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
import {shaSource} from "./lp0-audit.mjs";
import {loadQ1Inputs,auditG6Q1} from "./g6q1-review-worklist.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=path=>JSON.parse(readFileSync(join(root,path),"utf8"));
const revision="g6-200-current-20261120",locales=["zh-TW","ja"];
const allowedRisks=new Set(["CRITICAL","HIGH","MEDIUM"]);
const allowedFocus=new Set(["FORMAT_AND_DYNAMIC_CONTENT","ALARM_FIRING_OPERATION","WAKE_CHALLENGE","SNOOZE_BEHAVIOR","PERMISSION_READINESS","DIAGNOSTIC_TEST","PRIVACY_BACKUP_HEALTH","MEDIA_SELECTION","HISTORY_STATISTICS","ALARM_CONFIGURATION","GENERAL_UI"]);
const assessments=["SURFACE_NO_CLEAR_CONFLICT_NOT_APPROVED","CONTEXT_RISK_FLAGGED_MODEL_ONLY","REVISION_SUGGESTED_MODEL_ONLY"];
const permitted={
 settings_dashboard_description:["zh-TW","ja"],
 settings_share_support_bundle:["zh-TW","ja"],
 b2_test_scheduled:["zh-TW"],
 recent_playback_empty:["zh-TW"],
 ringtone_folder:["zh-TW","ja"],
 alarm_music_settings_remove_track:["ja"]
};
export const loadQ2Inputs=()=>({q1:loadQ1Inputs(),q2:read("localization/governance/g6q/model-triage-q2.json")});
export function auditG6Q2({q1,q2}){
 const q1result=auditG6Q1(q1),{catalog,packs,g4,glossary,g2,s0}=q1;
 assert.equal(q2.schema,"acx-g6q2-full-remaining-model-triage/1");
 assert.equal(q2.catalogRevision,revision);
 assert.equal(q2.baseCommit,catalog.baseCommit);
 assert.deepEqual([q2.q1SelectedKeys,q2.q2NewKeys,q2.q2LocaleRows,q2.modelPreliminaryRowsCumulative,q2.independentHumanApprovals],[37,163,326,400,0]);
 assert.equal(g4.approvals.length,0);
 assert.equal(glossary.approval,"UNAPPROVED_FOR_RELEASE");
 assert.equal(q1result.summary.independentApprovals,0);
 assert.equal(q2.records.length,163);
 const sources=new Map(catalog.entries.map(e=>[e.key,e]));
 const oldSources=new Map(q1.matrix.records.map(e=>[e.key,e]));
 const staticContexts=new Map(g2.records.map(e=>[e.key,e]));
 const prior=new Set(q1.triage.records.map(x=>x.key));
 const byKey=new Map(),seenProposals=new Set();
 const counts={},risks={CRITICAL:0,HIGH:0,MEDIUM:0};
 let proposed=0,formatted=0,flagged=0,noStatic=0;
 for(const r of q2.records){
  const en=sources.get(r.key),base=oldSources.get(r.key),ctx=staticContexts.get(r.key);
  assert(en&&base&&ctx,"Missing G6/G2/Q2 source "+r.key);
  assert(!prior.has(r.key),"Cannot re-triage Q1 key "+r.key);
  assert(!byKey.has(r.key),"Duplicate Q2 source "+r.key);
  byKey.set(r.key,r);
  assert.equal(r.section,en.section);
  assert.equal(r.source,en.source);
  assert.equal(r.sourceHash,shaSource(r.key,en.source));
  assert.equal(r.sourceHash,base.sourceHash,"Q2 source was amended without S0 authority");
  assert.deepEqual(r.placeholders,en.placeholders);
  if(r.placeholders.length)formatted++;
  assert(allowedRisks.has(r.priority));
  risks[r.priority]++;
  assert(allowedFocus.has(r.reviewFocus));
  assert.match(r.issueCode,/^[A-Z][A-Z0-9_]+$/);
  assert.equal(r.reviewAuthority,"MODEL_PRELIMINARY_ONLY");
  assert(!Object.hasOwn(r,"humanApproved")&&!Object.hasOwn(r,"approval")&&!Object.hasOwn(r,"decision"),
    "Model record cannot smuggle a human approval or independent decision");
  assert.equal(r.contextVerificationRequired,true);
  assert(["MODEL_CONTEXT_RISK_FLAGGED","MODEL_TEXT_SURFACE_ONLY"].includes(r.semanticEvidenceType));
  if(r.semanticEvidenceType==="MODEL_CONTEXT_RISK_FLAGGED")flagged++;
  if(ctx.sourceCallsites.length===0)noStatic++;
  assert.deepEqual(Object.keys(r.localeReview).sort(),locales.slice().sort());
  for(const locale of locales){
   const one=r.localeReview[locale];
   assert.equal(one.currentCandidate,packs[locale].translations[r.key],
     "G6 candidate changed "+locale+"/"+r.key);
   assert.deepEqual(core.placeholders(one.currentCandidate),en.placeholders,
     "Target placeholders changed");
   assert(assessments.includes(one.preliminaryAssessment));
   assert(one.observation.includes(one.currentCandidate)&&one.observation.includes(en.source),
     "Preliminary review must compare source and actual locale");
   assert(one.observation.trim().length>22);
   assert.equal(one.humanApproved,false);
   assert.equal(one.independentReview,null);
   assert(!Object.hasOwn(one,"decision")&&!Object.hasOwn(one,"approvedAt")&&!Object.hasOwn(one,"reviewerId"),
     "Unapproved locale must not carry hidden approval metadata");
   counts[one.preliminaryAssessment]=(counts[one.preliminaryAssessment]||0)+1;
   if(one.proposedReplacement!==null){
    assert.equal(one.preliminaryAssessment,"REVISION_SUGGESTED_MODEL_ONLY");
    assert.equal(one.proposalAuthority,"G6Q2_MODEL_SUGGESTION");
    assert(permitted[r.key]?.includes(locale),"Unknown proposed scope "+locale+"/"+r.key);
    assert(one.proposedReplacement.trim()&&one.proposedReplacement!==one.currentCandidate);
    assert.deepEqual(core.placeholders(one.proposedReplacement),en.placeholders);
    proposed++;seenProposals.add(locale+"\u0000"+r.key);
   }else{
    assert.equal(one.proposalAuthority,null);
    assert.notEqual(one.preliminaryAssessment,"REVISION_SUGGESTED_MODEL_ONLY");
   }
  }
 }
 assert.equal(byKey.size,163);
 assert.equal(formatted,21);
 assert.equal(flagged,43);
 assert.equal(proposed,9);
 assert.equal(seenProposals.size,9);
 assert.deepEqual([...seenProposals].sort(),Object.entries(permitted).flatMap(([key,ls])=>ls.map(loc=>loc+"\u0000"+key)).sort());
 assert.deepEqual(counts,{SURFACE_NO_CLEAR_CONFLICT_NOT_APPROVED:240,CONTEXT_RISK_FLAGGED_MODEL_ONLY:77,REVISION_SUGGESTED_MODEL_ONLY:9});
 assert.equal(q2.stats.keys,163);
 assert.equal(q2.stats.localeRows,326);
 assert.equal(q2.stats.formattedKeys,21);
 assert.equal(q2.stats.flaggedKeys,43);
 assert.equal(q2.stats.proposalKeys,6);
 assert.equal(q2.stats.proposedLocaleRevisions,9);
 assert.deepEqual(q2.stats.assessmentCounts,counts);
 assert.equal(q2.stats.independentApprovals,0);
 assert(!byKey.has(s0.items[0].key),"S0 amended source must remain Q1");
 const example=byKey.get("stats_snoozed");
 assert.equal(example.issueCode,"CONTEXT_EXPLAINS_RATE");
 assert.equal(example.localeReview["zh-TW"].currentCandidate,"貪睡率");
 assert.equal(example.localeReview.ja.currentCandidate,"スヌーズ率");
 const statsrc=readFileSync(join(root,"app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt"),"utf8");
 assert(statsrc.includes("R.string.stats_snoozed") && statsrc.includes("stats.snoozeRate"),
   "Rate context changed; human must recheck the meaning of Snoozed");
 const folder=readFileSync(join(root,"app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/RingtonePickerSheet.kt"),"utf8");
 assert(folder.includes("ActivityResultContracts.OpenDocumentTree()") && folder.includes("R.string.ringtone_folder"),
   "From-folder label context changed");
 const final=q1result.worklist.map(v=>{
  const row={...v};
  if(byKey.has(row.key)){
   const r=byKey.get(row.key),audit=r.localeReview[row.locale];
   assert.equal(row.preliminaryModelTriage,"NOT_YET_MODEL_TRIAGED");
   row.preliminaryModelTriage=audit.preliminaryAssessment;
   row.severity=r.priority;
   row.issueCode=r.issueCode;
   row.contextNote=audit.observation;
   row.proposal=audit.proposedReplacement;
   row.proposalOrigin=audit.proposalAuthority;
   row.modelReviewBatch="G6_Q2_TEXT_SURFACE_PRELIMINARY";
   row.q2Focus=r.reviewFocus;
  }else{
   assert(prior.has(row.key));
   assert.notEqual(row.preliminaryModelTriage,"NOT_YET_MODEL_TRIAGED");
   row.modelReviewBatch="G6_Q1_PRIORITY_PRELIMINARY";
   row.q2Focus=null;
  }
  row.independentApproved=false;
  row.humanApproval=null;
  row.status="INDEPENDENT_HUMAN_REVIEW_REQUIRED";
  return row;
 });
 assert.equal(final.length,400);
 assert.equal(new Set(final.map(x=>x.locale+"\u0000"+x.key)).size,400);
 assert.equal(final.filter(x=>x.modelReviewBatch==="G6_Q2_TEXT_SURFACE_PRELIMINARY").length,326);
 assert.equal(final.filter(x=>x.modelReviewBatch==="G6_Q1_PRIORITY_PRELIMINARY").length,74);
 assert.equal(final.filter(x=>x.preliminaryModelTriage==="NOT_YET_MODEL_TRIAGED").length,0);
 assert.equal(final.filter(x=>x.proposal!==null).length,27);
 assert.equal(final.filter(x=>x.sourceChanged).length,2);
 assert(final.every(x=>x.independentApproved===false && x.humanApproval===null));
 const manifest={schema:"acx-g6q2-complete-model-preliminary-handoff/1",
  catalogRevision:revision,reviewAuthority:"MODEL_PRELIMINARY_ONLY",sourceKeys:200,
  candidateLocaleRows:400,q1Keys:37,q1LocaleRows:74,q2Keys:163,q2LocaleRows:326,
  modelSurfaceScreenedLocaleRows:400,q2FormattedKeys:21,q2ContextFlaggedKeys:43,
  q1ProposedLocaleRevisions:18,q2ProposedLocaleRevisions:9,totalProposedLocaleRevisions:27,
  independentHumanApprovals:0,glossaryApprovedForRelease:false,humanContextEvidenceStillRequired:true,
  officialRelease:"DENY"};
 return {manifest,worklist:final,noStaticCallsiteKeysInQ2:noStatic,priorityCounts:risks};
}
const cell=v=>{
 let s=String(v??"").replace(/[\t\r\n]+/g," ");
 if(/^\s*[=+\-@]/.test(s))s="'"+s;
 return s;
};
export function toTsv(items){
 const cols=["batch","locale","key","section","severity","currentSource","currentSourceHash",
 "candidate","translationDigest","preliminaryModelTriage","issueCode","contextNote",
 "proposal","proposalOrigin","g2StaticCallsiteCount","g2EvidenceState",
 "Human reviewer ID (blank)","Reviewer decision (blank)","Reviewed at (blank)",
 "Term and runtime checked (blank)","Human rationale (blank)"];
 const data=r=>[r.modelReviewBatch,r.locale,r.key,r.section,r.severity,r.currentSource,
 r.currentSourceHash,r.candidate,r.translationDigest,r.preliminaryModelTriage,r.issueCode,
 r.contextNote,r.proposal,r.proposalOrigin,r.g2StaticCallsiteCount,r.g2EvidenceState,"","","","",""];
 return [cols.join("\t"),...items.map(x=>data(x).map(cell).join("\t"))].join("\n")+"\n";
}
function main(){
 const args=process.argv;
 if(args.includes("--mode") && args[args.indexOf("--mode")+1]!=="preview")
  throw Error("G6-Q2 release DENIED: preliminary model screening is not independent human approval");
 const {manifest,worklist,noStaticCallsiteKeysInQ2,priorityCounts}=auditG6Q2(loadQ2Inputs());
 const i=args.indexOf("--out-dir");
 if(i>=0){
  const dir=resolve(root,args[i+1]||"");
  assert(dir.startsWith(root+sep),"Refusing output outside repo");
  mkdirSync(dir,{recursive:true});
  writeFileSync(join(dir,"manifest.json"),JSON.stringify(manifest,null,2)+"\n");
  writeFileSync(join(dir,"human-review-worklist.json"),JSON.stringify({...manifest,items:worklist},null,2)+"\n");
  writeFileSync(join(dir,"human-review-worklist.tsv"),toTsv(worklist));
  writeFileSync(join(dir,"README.txt"),
   "G6-Q2: 200 keys, 400 preliminary bilingual rows; NOT independently approved.\n"+
   "Q1 74 + Q2 326 rows = 400. There are 27 unapproved locale-level candidate revisions.\n"+
   "Reviewer/decision/date/rationale columns are intentionally blank; G1 glossary is unapproved.\n"+
   "Official language pack release remains BLOCKED. Do not overwrite Android/Debug resources.\n");
 }
 console.log("G6Q2_FULL_MODEL_TRIAGE_PASS "+JSON.stringify({...manifest,noStaticCallsiteKeysInQ2,priorityCounts}));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
