#!/usr/bin/env node
/**
 * L12-8B: reproducible risk-tiered human review queue proposal.
 * NOT a semantic-approval mechanism. Never modifies G1, G4, L12-6 or L13-B.
 * Every existing release decision remains fail-closed in the current gates.
 */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {buildReviewBatchPlan,validateUntrustedReviewNotes,renderDesk} from "./l12-b7-review-batches.mjs";
import {stageSourceRows} from "./l12-b3-android-resource-preflight.mjs";
import {triageWarnings} from "./l12-b4-aapt2-staging.mjs";
import {buildWarningDispositions} from "./l12-b5-warning-disposition.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const POLICY_PATH="localization/policies/l12-b8b-risk-tiered-review-policy.proposal.json";
const hash=x=>"sha256:"+createHash("sha256").update(JSON.stringify(x),"utf8").digest("hex");
const id=r=>r.key+"\0"+r.locale;
const expectedTiers=["P0_OPERATION_SECURITY_DATA","P1_MEDIA_PLAYBACK","P2_FORMAT_AND_WARNING"];
const sorting=(a,b)=>a.localeCompare(b,"en");
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);

export function validatePolicy(policy){
  assert.equal(policy?.schema,"acx-l12-b8-risk-tiered-policy-proposal/1");
  assert.equal(policy.status,"PROPOSED_NOT_AUTHORIZED");
  assert.equal(policy.authorization,"NONE_NO_POLICY_RELEASE_AUTHORITY");
  assert.equal(policy.scope,"B0_T1_FULL_APP_3100_MISSING_LOCALE_ROWS");
  assert.equal(policy.sourceReviewContract,"acx-l12-b6-independent-review-packet/1");
  const rules=policy.processingRules;
  assert(equal(rules.alwaysManualRiskTiers,expectedTiers));
  assert.equal(rules.diagnosticSampleTier,"P3_GENERAL_UI");
  assert.equal(rules.samplingUnit,"BILINGUAL_KEY_CARD_BOTH_LOCALES_ALWAYS_IN_SAMPLE");
  assert(equal(rules.strata,["featureFamily"]));
  assert(Number.isFinite(rules.initialFraction)&&
    rules.initialFraction>=0.05&&rules.initialFraction<=0.25);
  assert(Number.isInteger(rules.minCardsPerNonemptyStratum)&&
    rules.minCardsPerNonemptyStratum>=2&&rules.minCardsPerNonemptyStratum<=10);
  assert.equal(rules.sampler,"SHA256_SORTED_REVIEW_SET_DIGEST_BOUND_DETERMINISTIC_QUEUE");
  assert.equal(rules.onSampleFailure,"FULL_REVIEW_OF_FEATURE_FAMILY_STRATUM");
  assert.equal(rules.onUndeterminedSample,"REMAIN_PENDING_NO_RELEASE");
  assert.equal(rules.onSourceCandidateOrPolicyRevisionChange,"INVALIDATE_AND_REGENERATE");
  assert.equal(rules.onCrossFeatureSafetyMisclassification,"ESCALATE_ALL_AFFECTED_ROWS_TO_FULL_REVIEW");
  assert.equal(rules.noteSemantics,"UNTRUSTED_TRIAGE_ONLY_NEVER_INDEPENDENT_APPROVAL");
  assert(typeof rules.seed==="string" && rules.seed.length>=16);
  // An ordinary regex is only a conservative screening hint, never authority to reduce risk.
  assert(typeof rules.additionalEscalationEnglishRegex==="string" &&
    rules.additionalEscalationEnglishRegex.length>30);
  const gate=policy.releaseGate;
  for(const name of ["existingL12B6FullPerRowHumanReviewRequired",
    "existingL13BProtectedWriterUnchanged","requiresNewExplicitPolicyAuthorization",
    "requiresProtectedReviewerProvenance",
    "requiresRevisedL12B6AndL13BContractsBeforeRelease",
    "requiresG1AndG4IndependentGovernance","requiresAndroidRuntimeValidation"])
    assert.equal(gate[name],true,name+" must remain fail-closed");
  assert.equal(gate.policyProposalMayApproveRows,false);
  assert.equal(gate.policyProposalMayWriteOfficialXml,false);
  return hash([policy.policyId,policy.policyRevision,policy.status,
    policy.processingRules,policy.releaseGate]);
}
function cardsFromPlan(plan){
  assert.equal(plan?.summary?.schema,"acx-l12-b7-risk-batch-plan/1");
  assert.equal(plan.summary.reviewRequiredLocaleRows,3100);
  assert.equal(plan.summary.reviewKeyCards,1550);
  const all=plan.batches.flatMap(b=>b.cards);
  assert.equal(all.length,1550);
  assert.equal(new Set(all.map(c=>c.key)).size,1550);
  for(const c of all){
    assert.equal(c.localeRows.length,2);
    assert.deepEqual(c.localeRows.map(r=>r.locale),["zh-TW","ja"]);
    assert(c.localeRows.every(r=>r.independentApproved===false && r.mayRelease===false));
  }
  return all;
}
const safeCheck=(c,re)=>re.test(c.key+" "+c.english);
/**
 * Returns two disjoint sets: full manual queue and deterministic diagnostic
 * P3 sample. Selected P3 rows NEVER become approved by sampling alone.
 */
export function buildRiskTieredPlan(batchPlan,policy){
  const policyDigest=validatePolicy(policy),all=cardsFromPlan(batchPlan);
  const re=new RegExp(policy.processingRules.additionalEscalationEnglishRegex,"iu");
  const full=[],screened=[],overrides=[];
  for(const c of all){
    if(expectedTiers.includes(c.priority)){
      full.push({...c,policyDisposition:"MANDATORY_PER_ROW_INDEPENDENT_REVIEW",
        policyEscalatedFromP3:false});
    }else if(c.priority==="P3_GENERAL_UI"){
      if(c.warningCount>0||c.type!=="string"||c.risks.some(x=>x!=="GENERAL_UI")||
         c.localeRows.some(r=>r.issues.length>0)||safeCheck(c,re)){
        full.push({...c,policyDisposition:"P3_CONSERVATIVE_RISK_RECHECK_FULL_REVIEW",
          policyEscalatedFromP3:true});
        overrides.push({key:c.key,featureFamily:c.featureFamily,
          trigger:"P3_RECHECK_NONTRIVIAL_SOURCE_OR_OPERATION",
          status:"FULL_PER_ROW_REVIEW_REQUIRED"});
      }else screened.push(c);
    }else throw Error("Unexpected B7 tier "+c.priority);
  }
  const strata=new Map();
  for(const c of screened){
    const s=c.featureFamily;
    if(!strata.has(s))strata.set(s,[]);
    strata.get(s).push(c);
  }
  const stratumWork=[],sampled=[],sampleKeys=new Set(),unselected=[];
  for(const [family,cards] of [...strata].sort((a,b)=>sorting(a[0],b[0]))){
    const ranked=cards.map(c=>({
      card:c,
      rank:hash([policy.processingRules.seed,policyDigest,
        batchPlan.summary.reviewSetDigest,c.key,c.sourceHash,
        c.localeRows.map(r=>[r.locale,r.translationHash,r.reviewRevision])])
    })).sort((a,b)=>sorting(a.rank,b.rank)||sorting(a.card.key,b.card.key));
    const n=Math.min(cards.length,Math.max(policy.processingRules.minCardsPerNonemptyStratum,
      Math.ceil(cards.length*policy.processingRules.initialFraction)));
    const chosen=ranked.slice(0,n).map(x=>x.card),
      omitted=ranked.slice(n).map(x=>x.card);
    for(const c of chosen){
      if(sampleKeys.has(c.key))throw Error("Duplicate sample "+c.key);
      sampleKeys.add(c.key);sampled.push(c);
    }
    unselected.push(...omitted);
    stratumWork.push({
      stratumId:"L12B8B-"+family,featureFamily:family,
      eligibleKeyCards:cards.length,eligibleLocaleRows:cards.length*2,
      sampledKeyCards:n,sampledLocaleRows:n*2,
      unselectedKeyCards:omitted.length,
      status:"DIAGNOSTIC_SAMPLE_NOT_INDEPENDENTLY_REVIEWED",
      sampledKeys:chosen.map(c=>c.key),
      unselectedKeys:omitted.map(c=>c.key),
      independentApproved:0,
      onAnySampleIssue:"ESCALATE_ALL_"+family+"_TO_FULL_REVIEW"
    });
  }
  const unique=new Set([...full,...sampled,...unselected].map(x=>x.key));
  if(unique.size!==1550||full.length+sampled.length+unselected.length!==1550)
    throw Error("L12-8B partition is not a bijection");
  const selected=new Set(sampled.map(x=>x.key));
  const sampleBatches=batchPlan.batches.map(b=>({
    ...b,cards:b.cards.filter(c=>selected.has(c.key))
  })).filter(b=>b.cards.length).map(b=>({
    ...b,status:"L12_8B_P3_DIAGNOSTIC_SAMPLE_ONLY",
    keyCards:b.cards.length,localeRows:b.cards.length*2,
    semanticApprovalCount:0
  }));
  const distribution={};
  for(const c of full){
    const bucket=c.policyEscalatedFromP3?"P3_CONSERVATIVE_OVERRIDE":c.priority;
    distribution[bucket]=(distribution[bucket]??0)+2;
  }
  if(Object.values(distribution).reduce((sum,n)=>sum+n,0)!==full.length*2)
    throw Error("L12-8B manual review distribution double count");
  const summary={
    schema:"acx-l12-b8b-risk-tiered-review-plan/1",
    policyId:policy.policyId,policyRevision:policy.policyRevision,
    policyDigest,policyStatus:policy.status,
    status:"PROPOSED_DIAGNOSTIC_WORKFLOW_NO_RELEASE_AUTHORITY",
    reviewSetDigest:batchPlan.summary.reviewSetDigest,
    batchPlanDigest:batchPlan.summary.batchPlanDigest,
    sourceSha:batchPlan.summary.sourceSha,
    catalogueRevision:batchPlan.summary.catalogueRevision,
    fullSourceLocaleRows:3100,totalKeyCards:1550,
    baselineRiskSensitiveRows:batchPlan.summary.sourceRiskSensitiveLocaleRows,
    mandatoryFullKeyCards:full.length,
    mandatoryFullLocaleRows:full.length*2,
    p3ConservativeOverridesKeyCards:overrides.length,
    diagnosticEligibleKeyCards:screened.length,
    diagnosticEligibleLocaleRows:screened.length*2,
    sampleKeyCards:sampled.length,
    sampleLocaleRows:sampled.length*2,
    deferredKeyCards:unselected.length,
    deferredLocaleRows:unselected.length*2,
    initialHumanFocusLocaleRows:full.length*2+sampled.length*2,
    diagnosticStrata:stratumWork.length,
    sampleBatches:sampleBatches.length,
    sampleObservedDecisions:0,
    actualApprovedLocaleRows:0,
    policyAuthorized:false,
    releasePermitted:false,
    byMandatoryReviewTier:distribution,
    disclaimers:[
      "Initial human focus counts are workload estimates, not independent approvals.",
      "All 3100 translation rows remain unapproved; the current L12-6 release gate still requires individual human approval.",
      "A sample result does not certify any uninspected candidate. Individual locale text remains distinct.",
      "L12-8B policy requires separately authenticated authorization and explicit revised L12-6/L13-B validators before release."
    ]
  };
  const sampleDeskPlan={
    summary:{...batchPlan.summary,
      status:"L12_8B_DIAGNOSTIC_SAMPLE_ONLY_NOT_COMPLETE",
      reviewKeyCards:sampled.length,reviewRequiredLocaleRows:sampled.length*2,
      batches:sampleBatches.length,
      sourceRiskSensitiveLocaleRows:0,
      sampleOnlyPreview:true,
      allSourceReviewRows:3100
    },
    batches:sampleBatches
  };
  return {summary,mandatoryFullReview:full.map(c=>({
      key:c.key,priority:c.priority,featureFamily:c.featureFamily,
      english:c.english,sourcePath:c.sourcePath,sourceLine:c.sourceLine,
      policyDisposition:c.policyDisposition,localeRows:c.localeRows.map(r=>({
        key:r.key,locale:r.locale,type:r.type,sourceHash:r.sourceHash,
        translationHash:r.translationHash,reviewRevision:r.reviewRevision,
        candidate:r.candidate,risks:r.risks,issues:r.issues
      }))
    })),
    p3Overrides:overrides,
    strata:stratumWork,
    diagnosticSampleCards:sampled,
    sampleDeskPlan
  };
}
/** Progress using ONLY unsigned L12-7 notes. It may escalate; it never approves. */
export function evaluateProvisionalNotes(riskPlan,batchPlan,notes){
  const checked=validateUntrustedReviewNotes(batchPlan,notes);
  if(checked.status!=="VALID_UNTRUSTED_NOTES_ONLY")
    return {status:"INVALID_UNTRUSTED_NOTES",errors:checked.errors,
      actualApprovedLocaleRows:0,releasePermitted:false};
  const cardMap=new Map(cardsFromPlan(batchPlan).map(c=>[c.key,c]));
  const stratumByFamily=new Map(riskPlan.strata.map(s=>[s.featureFamily,s]));
  const byId=new Map(notes.entries.map(n=>[id(n),n]));
  const escalations=new Map();
  const positive=new Set(),remaining=new Set();
  for(const s of riskPlan.strata){
    for(const key of s.sampledKeys){
      const c=cardMap.get(key);
      for(const r of c.localeRows){
        const note=byId.get(id(r));
        if(!note||note.disposition==="SKIP_FOR_NOW"){
          remaining.add(id(r));
        }else if(note.disposition==="LOOKS_ACCEPTABLE_UNVERIFIED"){
          positive.add(id(r));
        }else{
          escalations.set(s.featureFamily,notes.entries.some(n=>
            n.key===key&&n.disposition==="NEEDS_REVISION")?
            "NEEDS_REVISION":"NEEDS_MORE_CONTEXT");
        }
      }
    }
  }
  // A reported problem in any unsampled row of a P3 stratum also triggers
  // escalation; do not hide negative evidence just because it wasn't selected.
  for(const n of notes.entries){
    const card=cardMap.get(n.key),family=card?.featureFamily;
    if(!stratumByFamily.has(family))continue;
    if(n.disposition==="NEEDS_REVISION"||n.disposition==="NEEDS_MORE_CONTEXT"){
      const previous=escalations.get(family);
      if(previous!=="NEEDS_REVISION")escalations.set(family,n.disposition);
    }
  }
  const results=riskPlan.strata.map(s=>{
    const hasEscalation=escalations.has(s.featureFamily);
    const fullSampleAnnotations=s.sampledKeys.every(key=>{
      const c=cardMap.get(key);
      return c.localeRows.every(r=>
        byId.get(id(r))?.disposition==="LOOKS_ACCEPTABLE_UNVERIFIED");
    });
    return {
      stratumId:s.stratumId,featureFamily:s.featureFamily,
      status:hasEscalation?"ESCALATE_FULL_STRATUM":
        fullSampleAnnotations?"PROVISIONAL_ANNOTATED_NOT_AUTHORIZED":"AWAITING_SAMPLE_NOTES",
      trigger:escalations.get(s.featureFamily)??null,
      pendingFullReviewLocaleRows:hasEscalation?s.eligibleLocaleRows:0,
      independentApproved:0,releasePermitted:false
    };
  });
  return {
    schema:"acx-l12-b8b-provisional-progress/1",
    status:results.some(x=>x.status==="ESCALATE_FULL_STRATUM")?
      "PROVISIONAL_ISSUES_ESCALATE_NOT_RELEASE":
      "PROVISIONAL_REVIEW_PROGRESS_NO_RELEASE",
    reviewSetDigest:riskPlan.summary.reviewSetDigest,
    policyDigest:riskPlan.summary.policyDigest,
    progressNotes:notes.entries.length,
    annotatedSampleLocaleRows:positive.size,
    unannotatedSampleLocaleRows:remaining.size,
    escalatedStrata:results.filter(x=>x.status==="ESCALATE_FULL_STRATUM").length,
    escalatedLocaleRows:results.reduce((n,x)=>n+x.pendingFullReviewLocaleRows,0),
    strata:results,
    actualApprovedLocaleRows:0,releasePermitted:false,
    warnings:[
      "The imported notes are untrusted. A positive annotation NEVER becomes an official semantic approval.",
      "Any failure or unknown meaning escalates the whole feature stratum to human review.",
      "Formal release still requires the original L12-6 independent per-row reviewer evidence."
    ]
  };
}
function tsv(plan){
  const cols=["review_mode","tier","family","stratum","key","locale",
    "source_hash","translation_hash","review_revision","english","candidate",
    "independent_approval"];
  const rows=[
    ...plan.mandatoryFullReview.flatMap(c=>c.localeRows.map(r=>({
      mode:"MANDATORY_FULL",tier:c.priority,family:c.featureFamily,stratum:"",
      r,english:c.english,candidate:
        typeof r.candidate==="string"?r.candidate:JSON.stringify(r.candidate)
    }))),
    ...plan.diagnosticSampleCards.flatMap(c=>c.localeRows.map(r=>({
      mode:"DIAGNOSTIC_SAMPLE",tier:c.priority,family:c.featureFamily,
      stratum:"L12B8B-"+c.featureFamily,r,english:c.english,candidate:
        typeof r.candidate==="string"?r.candidate:JSON.stringify(r.candidate)
    })))
  ];
  return cols.join("\t")+"\n"+rows.map(x=>[
    x.mode,x.tier,x.family,x.stratum,x.r.key,x.r.locale,
    x.r.sourceHash,x.r.translationHash,x.r.reviewRevision,x.english,
    x.candidate,"NOT_APPROVED"
  ].map(y=>JSON.stringify(y)).join("\t")).join("\n")+"\n";
}
export function renderSamplingDesk(riskPlan){
  const html=renderDesk(riskPlan.sampleDeskPlan);
  return html.replace("L12-7 翻譯分批審閱工作台（未批准）",
    "L12-8B 診斷抽樣工作台（未批准）")
    .replace("AlarmClockXtreme｜L12-7 離線翻譯審閱工作台",
      "AlarmClockXtreme｜L12-8B P3 診斷抽樣工作台")
    .replace("所有勾選與備註只是審閱工作筆記",
      "此頁只有經版本固定的 P3 診斷樣本，不包含 P0/P1/P2 和全部 P3 文字。所有勾選與備註只是審閱工作筆記");
}
export function loadPolicy(root=ROOT){
  return JSON.parse(readFileSync(join(root,POLICY_PATH),"utf8"));
}
export function loadRiskPlan(root=ROOT,sourceSha="unavailable"){
  const data=loadUnifiedInputs(root,sourceSha),
    ledger=JSON.parse(readFileSync(join(root,"localization/governance/l12/independent-review-ledger.json"),"utf8"));
  const preflight=stageSourceRows({l11:data.l11,l12:data.l12,matrix:data.matrix,
    glossary:data.glossary,g4Ledger:data.g4Ledger,reviewDecisions:ledger}),
    warnings=buildWarningDispositions(triageWarnings(preflight),preflight),
    batchPlan=buildReviewBatchPlan(data.packet,warnings),
    policy=loadPolicy(root),
    riskPlan=buildRiskTieredPlan(batchPlan,policy);
  return {data,policy,batchPlan,riskPlan};
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const x=loadRiskPlan(ROOT,sha);
  const flag=process.argv.indexOf("--notes");
  if(flag>=0){
    const p=resolve(process.argv[flag+1]??"");
    const notes=JSON.parse(readFileSync(p,"utf8"));
    const progress=evaluateProvisionalNotes(x.riskPlan,x.batchPlan,notes);
    console.log("L12_B8B_UNTRUSTED_PROGRESS "+JSON.stringify(progress));
    if(progress.status==="INVALID_UNTRUSTED_NOTES")process.exitCode=1;
  }
  const at=process.argv.indexOf("--out-dir");
  if(at>=0){
    const dest=resolve(process.argv[at+1]??"");
    if(!dest.startsWith(join(ROOT,"build")+"/"))throw Error("L12-8B output only allowed under build/");
    mkdirSync(dest,{recursive:true});
    const risk=x.riskPlan;
    const exports={
      "policy-proposal.json":x.policy,
      "risk-plan-summary.json":risk.summary,
      "mandatory-full-review-queue.json":risk.mandatoryFullReview,
      "p3-risk-rechecks.json":risk.p3Overrides,
      "diagnostic-strata.json":risk.strata,
    };
    for(const [name,content] of Object.entries(exports))
      writeFileSync(join(dest,name),JSON.stringify(content,null,2)+"\n");
    writeFileSync(join(dest,"human-focus-worklist.tsv"),tsv(risk));
    writeFileSync(join(dest,"p3-diagnostic-sample-desk.html"),renderSamplingDesk(risk));
  }
  console.log("L12_B8B_RISK_TIERED_PLAN "+JSON.stringify(x.riskPlan.summary));
}
