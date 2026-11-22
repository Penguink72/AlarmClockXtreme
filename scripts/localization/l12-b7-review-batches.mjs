#!/usr/bin/env node
/**
 * L12-7: deterministic review NAVIGATION, not delegated semantic authority.
 * Pair the 2 locale rows for each English source; all 3100 row-level approvals
 * remain separately required by L12-6. No G1/G4 mutation or release artifacts.
 */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {join,dirname,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
import {triageWarnings} from "./l12-b4-aapt2-staging.mjs";
import {buildWarningDispositions} from "./l12-b5-warning-disposition.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const HASH=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x),"utf8").digest("hex");
const idOf=x=>x.key+"\0"+x.locale;
const localeOrder=["zh-TW","ja"];
const tiers=["P0_OPERATION_SECURITY_DATA","P1_MEDIA_PLAYBACK","P2_FORMAT_AND_WARNING","P3_GENERAL_UI"];
const caps=[10,16,16,28];
const highOps=new Set(["DATA_LOSS_OR_RESTORE","ALARM_FIRING_CONTROL","SECURITY_PRIVACY_PERMISSION"]);
const media="MEDIA_SOURCE_PLAYBACK";
const familyRules=[
  ["backup_import_export",/^(settings_backup_|settings_fossify_|backup_|restore_|import_|export_)/],
  ["alarm_firing",/^(alarmfiring_|firing_|notif_snooze_|wake_confirm_|realarm_)/],
  ["alarm_editor",/^(alarm_edit_|alarmedit_|alarm_list_|alarmlist_|template_)/],
  ["music_and_library",/^(music_|playlist_|playback_|ringtone_|youtube_|spotify_|share_sound_|media_|u4_)/],
  ["settings",/^(settings_|default_settings_)/],
  ["notifications",/^(notif_|notification_|widget_)/],
  ["sleep_and_bedtime",/^(bedtime_|sleep_|sonar_)/],
  ["timer",/^(timer_|stopwatch_)/],
  ["challenges",/^(challenge_|math_|wordle_|chess_)/],
  ["stats",/^(stats_|statistics_|history_)/],
  ["world_clock",/^(worldclock_|world_clock_|clock_)/],
  ["sharing_and_integrations",/^(share_|integration_|webhook_|connection_)/],
  ["navigation_and_layout",/^(navigation_|nav_|swipe_|menu_|dialog_|bottom_|home_)/],
  ["other",/^/]
];
const forbiddenDraftFields=["independentApproval","humanApproval","approved","mayRelease",
  "officialXmlWritten","signatureBase64","reviewMode","reviewerId","reviewedAt",
  "meaningVerified","contextVerified","operationConsequencesVerified","formatVerified"];
function familyFor(key){
  const name=key.substring(key.indexOf(":")+1);
  return familyRules.find(([,regex])=>regex.test(name))[0];
}
function plain(x){return typeof x==="string"?x:JSON.stringify(x);}
function severity(rows,warnings){
  const risks=[...new Set(rows.flatMap(r=>r.risks))];
  if(risks.some(x=>highOps.has(x)) ||
     warnings.some(w=>w.reviewCategories.includes("ACTION_SEMANTICS_ESCALATION")))
    return tiers[0];
  if(risks.includes(media))return tiers[1];
  if(warnings.length||rows.some(r=>r.issues.length>0)||rows.some(r=>r.type!=="string"))
    return tiers[2];
  return tiers[3];
}
function semanticEvidence(r){
  return {
    key:r.key,locale:r.locale,type:r.type,sourceHash:r.sourceHash,
    translationHash:r.translationHash,reviewRevision:r.reviewRevision,
    candidate:r.candidate,issues:r.issues,risks:r.risks,
    glossaryHints:r.glossaryHints,
    disposition:"UNREVIEWED",
    requiredEvidence:["MEANING","SPECIFIC_CALLSITE_CONTEXT","ANDROID_FORMAT",
      "OPERATION_CONSEQUENCE","INDEPENDENT_REVIEWER"],
    independentApproved:false,mayRelease:false
  };
}
/** Every grouped key maps to exactly two individually reviewable locale rows. */
export function buildReviewBatchPlan(packet,warnings,{batchCaps=caps}={}){
  assert.equal(packet.schema,"acx-l12-b6-independent-review-packet/1");
  assert.equal(warnings.schema,"acx-l12-b5-warning-disposition-queue/1");
  if(packet.sourceSha!==warnings.sourceSha ||
     packet.catalogueRevision!==warnings.catalogueRevision)
    throw Error("L12-7 packet/warning revision mismatch");
  if(packet.rows.length!==3100)throw Error("L12-7 expected fixed 3100-row source set");
  if(!Array.isArray(batchCaps)||batchCaps.length!==4||
    batchCaps.some(n=>!Number.isSafeInteger(n)||n<1||n>64))
    throw Error("Invalid review batch size");
  const rowIds=new Set(),byKey=new Map(),warnMap=new Map();
  for(const r of packet.rows){
    const id=idOf(r);
    if(rowIds.has(id))throw Error("Duplicate review row "+id);
    if(r.independentApproved!==false)throw Error("Unapproved source falsely claims approval "+id);
    rowIds.add(id);
    if(!byKey.has(r.key))byKey.set(r.key,[]);
    byKey.get(r.key).push(r);
  }
  if(byKey.size!==1550)throw Error("L12-7 expected paired 1550 keys");
  for(const w of warnings.items){
    const id=idOf(w),match=packet.rows.find(x=>idOf(x)===id);
    if(!match||warnMap.has(id)||w.sourceHash!==match.sourceHash||
       w.translationHash!==match.translationHash||
       w.reviewRevision!==match.reviewRevision)
      throw Error("Warning source/translation drift "+id);
    if(w.independentApproval!==false)throw Error("Warning cannot self-approve");
    warnMap.set(id,w);
  }
  const sameSource=new Map(),cards=[];
  for(const [key,rows] of byKey){
    rows.sort((a,b)=>localeOrder.indexOf(a.locale)-localeOrder.indexOf(b.locale));
    if(rows.length!==2||rows[0].locale!=="zh-TW"||rows[1].locale!=="ja" ||
       rows[0].sourceHash!==rows[1].sourceHash||rows[0].type!==rows[1].type)
      throw Error("Missing locale pair or inconsistent source "+key);
    const warningsFor=rows.map(x=>warnMap.get(idOf(x))).filter(Boolean);
    const type=rows[0].type,english=rows[0].english;
    const sourceGroup=HASH([type,english.replace(/\s+/g," ").trim()]);
    if(!sameSource.has(sourceGroup))sameSource.set(sourceGroup,[]);
    sameSource.get(sourceGroup).push(key);
    cards.push({
      key,type,english,sourceHash:rows[0].sourceHash,
      sourcePath:rows[0].sourcePath,sourceLine:rows[0].sourceLine,
      featureFamily:familyFor(key),priority:severity(rows,warningsFor),
      risks:[...new Set(rows.flatMap(r=>r.risks))],
      sourceSameTextGroup:sourceGroup,
      candidatePairedForNavigationOnly:true,
      sourceContextNotProven:true,
      warningCount:warningsFor.length,
      localeRows:rows.map(r=>{
        const w=warnMap.get(idOf(r));
        return {...semanticEvidence(r),
          warningFlags:w?.observedFlags??[],
          warningCategories:w?.reviewCategories??[]};
      })
    });
  }
  const sourceDupes=new Map();
  for(const [signature,keys] of sameSource)
    if(keys.length>1)sourceDupes.set(signature,keys.slice().sort((a,b)=>a.localeCompare(b,"en")));
  for(const c of cards){
    const similar=sourceDupes.get(c.sourceSameTextGroup)??[];
    c.sameEnglishAsOtherKeys=similar.filter(x=>x!==c.key).slice(0,12);
    c.sameEnglishIsNotSameAction=similar.length>1;
  }
  const buckets=new Map();
  for(const c of cards){
    const bucket=c.priority+"|"+c.featureFamily;
    if(!buckets.has(bucket))buckets.set(bucket,[]);
    buckets.get(bucket).push(c);
  }
  const batches=[];
  for(const [bucket,items] of [...buckets].sort((a,b)=>{
    const [ap,af]=a[0].split("|"),[bp,bf]=b[0].split("|");
    return tiers.indexOf(ap)-tiers.indexOf(bp)||af.localeCompare(bf,"en");
  })){
    items.sort((a,b)=>b.warningCount-a.warningCount||
      (b.risks.length-a.risks.length)||a.key.localeCompare(b.key,"en"));
    const [priority,featureFamily]=bucket.split("|");
    const cap=batchCaps[tiers.indexOf(priority)];
    for(let first=0,index=1;first<items.length;first+=cap,index++){
      const work=items.slice(first,first+cap);
      const batchId="L12B7-"+priority.slice(0,2)+"-"+featureFamily+"-"+String(index).padStart(3,"0");
      batches.push({
        batchId,priority,featureFamily,
        status:"UNREVIEWED_BATCH_NAVIGATION_ONLY",
        keyCards:work.length,localeRows:work.length*2,
        warningLocaleRows:work.reduce((n,c)=>n+c.warningCount,0),
        semanticApprovalCount:0,
        cards:work
      });
    }
  }
  const flattened=batches.flatMap(b=>b.cards.flatMap(c=>c.localeRows));
  const uniqueIds=new Set(flattened.map(idOf));
  if(uniqueIds.size!==3100||flattened.length!==3100||
    [...rowIds].some(id=>!uniqueIds.has(id)))throw Error("Batch coverage not bijective");
  const signatures=batches.map(b=>[b.batchId,b.cards.map(c=>c.key)]);
  const planDigest=HASH([packet.reviewSetDigest,signatures]);
  const sensitiveRows=packet.rows.filter(r=>r.risks.some(x=>x!=="GENERAL_UI")).length;
  const summary={
    schema:"acx-l12-b7-risk-batch-plan/1",status:"REVIEW_NAVIGATION_ONLY_NO_SEMANTIC_APPROVAL",
    sourceSha:packet.sourceSha,catalogueRevision:packet.catalogueRevision,
    glossaryRevision:packet.glossaryRevision,reviewSetDigest:packet.reviewSetDigest,
    batchPlanDigest:planDigest,reviewRequiredLocaleRows:3100,
    reviewKeyCards:1550,batches:batches.length,
    sourceRiskSensitiveLocaleRows:sensitiveRows,
    sourceGeneralLocaleRows:3100-sensitiveRows,
    distinctFlaggedLocaleRows:warnMap.size,
    exactEnglishDuplicateSourceGroups:sourceDupes.size,
    reviewApprovedLocaleRows:0,
    byPriority:Object.fromEntries(tiers.map(t=>[t,{
      keyCards:batches.filter(b=>b.priority===t).reduce((n,b)=>n+b.keyCards,0),
      localeRows:batches.filter(b=>b.priority===t).reduce((n,b)=>n+b.localeRows,0),
      batches:batches.filter(b=>b.priority===t).length
    }])),
    limitations:[
      "Two translations share one navigation card but require TWO independent row-level decisions.",
      "Same English or identical placeholder syntax does NOT prove same operation semantics.",
      "Category and risk are source-heuristic work ordering, not completed meaning review.",
      "Offline notes exported by reviewer are untrusted proposals, not signed L12-6 approvals.",
      "No source and translation approval, protected G1/G4 update, production XML or APK is created."
    ]
  };
  return {summary,batches};
}
const noteChoices=new Set(["LOOKS_ACCEPTABLE_UNVERIFIED","NEEDS_REVISION",
  "NEEDS_MORE_CONTEXT","SKIP_FOR_NOW"]);
/** Validate partial, local reviewer notes. NEVER map directly to L12-6 approvals. */
export function validateUntrustedReviewNotes(plan,notes){
  const errors=[];
  if(notes?.schema!=="acx-l12-b7-untrusted-review-notes/1"||
     notes.reviewSetDigest!==plan.summary.reviewSetDigest||
     notes.batchPlanDigest!==plan.summary.batchPlanDigest)
    return {status:"INVALID",errors:["INVALID_SCHEMA_OR_STALE_PLAN"]};
  if(notes.status!=="PROVISIONAL_UNTRUSTED_NO_APPROVAL"||
     Object.keys(notes).some(k=>forbiddenDraftFields.includes(k)))
    return {status:"INVALID",errors:["REVIEW_NOTES_MUST_REMAIN_UNTRUSTED"]};
  if(!Array.isArray(notes.entries))return {status:"INVALID",errors:["ENTRIES_REQUIRED"]};
  if(notes.entries.length>3100)return {status:"INVALID",errors:["TOO_MANY_NOTES"]};
  const rows=new Map(plan.batches.flatMap(b=>b.cards.flatMap(c=>c.localeRows))
    .map(r=>[idOf(r),r]));
  const seen=new Set();
  for(const e of notes.entries){
    const id=idOf(e),row=rows.get(id);
    if(seen.has(id)){errors.push("DUPLICATE_REVIEW_NOTE");continue;}
    seen.add(id);
    if(!row){errors.push("UNKNOWN_KEY_OR_LOCALE");continue;}
    if(e.sourceHash!==row.sourceHash||e.translationHash!==row.translationHash||
       e.reviewRevision!==row.reviewRevision)
      errors.push("STALE_NOTE_SOURCE_OR_TRANSLATION");
    if(!noteChoices.has(e.disposition))errors.push("UNAUTHORIZED_NOTE_DISPOSITION");
    if(typeof e.note!=="string"||e.note.length>4000)
      errors.push("INVALID_NOTE_CONTENT");
    if(Object.keys(e).some(k=>forbiddenDraftFields.includes(k)))
      errors.push("NOTE_CONTAINS_FAKE_APPROVAL_FIELDS");
  }
  return {status:errors.length?"INVALID":"VALID_UNTRUSTED_NOTES_ONLY",
    errors:[...new Set(errors)],notedLocaleRows:errors.length?0:seen.size,
    officialApprovedLocaleRows:0,mayRelease:false};
}
function batchIndexJson(plan){
  return {...plan.summary,batches:plan.batches.map(b=>({
    batchId:b.batchId,priority:b.priority,featureFamily:b.featureFamily,
    keyCards:b.keyCards,localeRows:b.localeRows,
    warningLocaleRows:b.warningLocaleRows,semanticApprovalCount:0,
    path:"batches/"+b.batchId+".json"
  }))};
}
function batchTsv(plan){
  const cols=["batch_id","priority","feature_family","type","key","locale",
    "source_hash","translation_hash","review_revision","risk","warnings",
    "english","candidate","notes_proposal","independent_approval"];
  return cols.join("\t")+"\n"+plan.batches.flatMap(b=>b.cards.flatMap(c=>
    c.localeRows.map(r=>[b.batchId,b.priority,b.featureFamily,c.type,
      c.key,r.locale,r.sourceHash,r.translationHash,r.reviewRevision,
      r.risks.join(";"),r.warningCategories.join(";"),c.english,
      plain(r.candidate),"","NOT_APPROVED"].map(x=>JSON.stringify(x)).join("\t")))).join("\n")+"\n";
}
function renderDesk(plan,root=ROOT){
  const template=readFileSync(join(root,"scripts/localization/l12-b7-review-desk-template.html"),"utf8");
  if(!template.includes("<!--L12B7_DATA-->"))throw Error("Review desk template data slot missing");
  const safe=JSON.stringify({summary:plan.summary,batches:plan.batches})
    .replace(/</g,"\\u003c").replace(/>/g,"\\u003e").replace(/&/g,"\\u0026");
  return template.replace("<!--L12B7_DATA-->",'<script id="review-data" type="application/json">'+safe+'</script>');
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const inp=loadUnifiedInputs(ROOT,sha);
  const ledger=JSON.parse(readFileSync(join(ROOT,"localization/governance/l12/independent-review-ledger.json"),"utf8"));
  const preflight=stageSourceRows({l11:inp.l11,l12:inp.l12,
    matrix:inp.matrix,glossary:inp.glossary,g4Ledger:inp.g4Ledger,
    reviewDecisions:ledger});
  const warnings=buildWarningDispositions(triageWarnings(preflight),preflight);
  const plan=buildReviewBatchPlan(inp.packet,warnings);
  const noteFlag=process.argv.indexOf("--validate-notes");
  if(noteFlag>=0){
    const raw=JSON.parse(readFileSync(resolve(process.argv[noteFlag+1]??""),"utf8"));
    const result=validateUntrustedReviewNotes(plan,raw);
    console.log("L12_B7_UNTRUSTED_NOTE_VALIDATION "+JSON.stringify(result));
    if(result.status!=="VALID_UNTRUSTED_NOTES_ONLY")process.exitCode=1;
  }
  const outFlag=process.argv.indexOf("--out-dir");
  if(outFlag>=0){
    const dest=resolve(process.argv[outFlag+1]??"");
    if(!dest.startsWith(join(ROOT,"build")+"/"))throw Error("L12-7 outputs only under build/");
    mkdirSync(join(dest,"batches"),{recursive:true});
    writeFileSync(join(dest,"review-plan-index.json"),JSON.stringify(batchIndexJson(plan),null,2)+"\n");
    writeFileSync(join(dest,"review-worklist.tsv"),batchTsv(plan));
    writeFileSync(join(dest,"review-desk.html"),renderDesk(plan));
    for(const b of plan.batches)
      writeFileSync(join(dest,"batches",b.batchId+".json"),
        JSON.stringify({...b,reviewSetDigest:plan.summary.reviewSetDigest,
          batchPlanDigest:plan.summary.batchPlanDigest},null,2)+"\n");
  }
  console.log("L12_B7_BATCH_PLAN "+JSON.stringify(plan.summary));
}
