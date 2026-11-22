#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {Script} from "node:vm";
import {
  loadRiskPlan,buildRiskTieredPlan,validatePolicy,
  evaluateProvisionalNotes,renderSamplingDesk
} from "./l12-b8b-risk-tiered-policy.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
let f;
function fixture(){
  return f??=loadRiskPlan(ROOT,"L12-B8B-TEST-SOURCE");
}
function notesFor(entries,x=fixture()){
  return {schema:"acx-l12-b7-untrusted-review-notes/1",
    status:"PROVISIONAL_UNTRUSTED_NO_APPROVAL",
    reviewSetDigest:x.batchPlan.summary.reviewSetDigest,
    batchPlanDigest:x.batchPlan.summary.batchPlanDigest,
    entries};
}
const note=(r,disposition="LOOKS_ACCEPTABLE_UNVERIFIED")=>({
  key:r.key,locale:r.locale,sourceHash:r.sourceHash,
  translationHash:r.translationHash,reviewRevision:r.reviewRevision,
  disposition,note:"Untrusted quality triage note. Not an approval."
});
test("L12-8B proposal is not an independent approval or policy activation",()=>{
  const x=fixture(),p=x.policy,s=x.riskPlan.summary;
  assert.equal(p.status,"PROPOSED_NOT_AUTHORIZED");
  assert.equal(p.authorization,"NONE_NO_POLICY_RELEASE_AUTHORITY");
  assert.equal(p.releaseGate.existingL12B6FullPerRowHumanReviewRequired,true);
  assert.equal(p.releaseGate.existingL13BProtectedWriterUnchanged,true);
  assert.equal(p.releaseGate.policyProposalMayApproveRows,false);
  assert.equal(p.releaseGate.policyProposalMayWriteOfficialXml,false);
  assert.equal(x.data.glossary.approval,"UNAPPROVED_FOR_RELEASE");
  assert.equal(x.data.g4Ledger.approvals.length,0);
  assert.equal(s.policyAuthorized,false);
  assert.equal(s.releasePermitted,false);
  assert.equal(s.actualApprovedLocaleRows,0);
  assert.match(s.policyDigest,/^sha256:[0-9a-f]{64}$/);
});
test("L12-8B partitions all 1550 bilingual cards with no dropped/duplicated rows",()=>{
  const x=fixture(),r=x.riskPlan,s=r.summary;
  assert.equal(s.fullSourceLocaleRows,3100);
  assert.equal(s.totalKeyCards,1550);
  assert.equal(s.baselineRiskSensitiveRows,986);
  assert.equal(s.mandatoryFullKeyCards*2,s.mandatoryFullLocaleRows);
  assert.equal(s.sampleKeyCards*2,s.sampleLocaleRows);
  assert.equal(s.deferredKeyCards*2,s.deferredLocaleRows);
  assert.equal(s.mandatoryFullLocaleRows+s.sampleLocaleRows+s.deferredLocaleRows,3100);
  assert.equal(s.initialHumanFocusLocaleRows,s.mandatoryFullLocaleRows+s.sampleLocaleRows);
  assert(s.initialHumanFocusLocaleRows<3100);
  assert(s.sampleLocaleRows>0 && s.deferredLocaleRows>0);
  assert(s.p3ConservativeOverridesKeyCards>=0);
  assert.equal(s.diagnosticStrata,r.strata.length);
  assert.equal(r.mandatoryFullReview.length,s.mandatoryFullKeyCards);
  assert.equal(r.diagnosticSampleCards.length,s.sampleKeyCards);
  assert(r.diagnosticSampleCards.every(c=>c.priority==="P3_GENERAL_UI"));
  assert(r.mandatoryFullReview.every(c=>
    c.localeRows.every(y=>y.locale==="zh-TW"||y.locale==="ja")));
  assert.equal(new Set(r.diagnosticSampleCards.map(c=>c.key)).size,s.sampleKeyCards);
});
test("L12-8B diagnostic sampling is deterministic and source-translation bound",()=>{
  const x=fixture(),a=x.riskPlan;
  const b=buildRiskTieredPlan(x.batchPlan,structuredClone(x.policy));
  assert.equal(a.summary.policyDigest,b.summary.policyDigest);
  assert.deepEqual(a.strata.map(z=>z.sampledKeys),b.strata.map(z=>z.sampledKeys));
  assert.deepEqual(a.diagnosticSampleCards.map(c=>c.key),
    b.diagnosticSampleCards.map(c=>c.key));
  const changed=structuredClone(x.batchPlan);
  changed.summary.reviewSetDigest="sha256:"+"0".repeat(64);
  assert.notDeepEqual(buildRiskTieredPlan(changed,x.policy).strata.map(z=>z.sampledKeys),
    a.strata.map(z=>z.sampledKeys));
  const policyRevision=structuredClone(x.policy);
  policyRevision.policyRevision="l12-b8b-v0.2-draft-not-activated";
  assert.notEqual(buildRiskTieredPlan(x.batchPlan,policyRevision).summary.policyDigest,
    a.summary.policyDigest);
});
test("L12-8B checks original P0/P1/P2 full queue and P3 conservative escalation",()=>{
  const x=fixture(),r=x.riskPlan;
  assert(r.mandatoryFullReview.some(c=>c.priority==="P0_OPERATION_SECURITY_DATA"));
  assert(r.mandatoryFullReview.some(c=>c.priority==="P1_MEDIA_PLAYBACK"));
  assert(r.mandatoryFullReview.some(c=>c.priority==="P2_FORMAT_AND_WARNING"));
  assert(r.mandatoryFullReview.every(c=>
    c.priority!=="P3_GENERAL_UI"||c.policyDisposition==="P3_CONSERVATIVE_RISK_RECHECK_FULL_REVIEW"));
  const s=r.summary;
  const initialMandatory=x.batchPlan.summary.byPriority.P0_OPERATION_SECURITY_DATA.localeRows+
    x.batchPlan.summary.byPriority.P1_MEDIA_PLAYBACK.localeRows+
    x.batchPlan.summary.byPriority.P2_FORMAT_AND_WARNING.localeRows;
  assert.equal(initialMandatory,1102);
  assert(s.mandatoryFullLocaleRows>=initialMandatory);
  assert.equal(r.p3Overrides.length*2,
    s.mandatoryFullLocaleRows-initialMandatory);
});
test("L12-8B requires every selected sample's two locales and never calls notes approval",()=>{
  const x=fixture(),first=x.riskPlan.diagnosticSampleCards[0];
  assert.deepEqual(first.localeRows.map(r=>r.locale),["zh-TW","ja"]);
  const one=notesFor([note(first.localeRows[0])],x);
  const progress=evaluateProvisionalNotes(x.riskPlan,x.batchPlan,one);
  assert.equal(progress.status,"PROVISIONAL_REVIEW_PROGRESS_NO_RELEASE");
  assert.equal(progress.annotatedSampleLocaleRows,1);
  assert.equal(progress.actualApprovedLocaleRows,0);
  assert.equal(progress.releasePermitted,false);
  assert(progress.unannotatedSampleLocaleRows>0);
});
test("L12-8B one failing sampled locale escalates its entire feature-family stratum",()=>{
  const x=fixture(),c=x.riskPlan.diagnosticSampleCards[0];
  const progress=evaluateProvisionalNotes(x.riskPlan,x.batchPlan,
    notesFor([note(c.localeRows[0],"NEEDS_REVISION")],x));
  const stratum=progress.strata.find(s=>s.featureFamily===c.featureFamily);
  assert.equal(stratum.status,"ESCALATE_FULL_STRATUM");
  assert.equal(stratum.trigger,"NEEDS_REVISION");
  assert(progress.escalatedLocaleRows>=2);
  assert.equal(progress.status,"PROVISIONAL_ISSUES_ESCALATE_NOT_RELEASE");
  assert.equal(progress.actualApprovedLocaleRows,0);
});
test("L12-8B uncertain sampled locale and negative unsampled P3 observation also escalate",()=>{
  const x=fixture(),c=x.riskPlan.diagnosticSampleCards[0];
  const p=evaluateProvisionalNotes(x.riskPlan,x.batchPlan,
    notesFor([note(c.localeRows[1],"NEEDS_MORE_CONTEXT")],x));
  assert(p.escalatedStrata>=1);
  const s=x.riskPlan.strata.find(s=>s.unselectedKeys.length);
  assert(s);
  const key=s.unselectedKeys[0];
  const unsampled=x.batchPlan.batches.flatMap(b=>b.cards).find(c=>c.key===key);
  const other=evaluateProvisionalNotes(x.riskPlan,x.batchPlan,
    notesFor([note(unsampled.localeRows[0],"NEEDS_REVISION")],x));
  assert(other.strata.some(z=>z.featureFamily===s.featureFamily&&
    z.status==="ESCALATE_FULL_STRATUM"));
});
test("L12-8B refuses forged authorization, weakened thresholds and stale notes",()=>{
  const x=fixture(),c=x.riskPlan.diagnosticSampleCards[0];
  for(const action of [
    p=>{p.status="AUTHORIZED_FOR_RELEASE"},
    p=>{p.releaseGate.policyProposalMayApproveRows=true},
    p=>{p.releaseGate.existingL12B6FullPerRowHumanReviewRequired=false},
    p=>{p.processingRules.alwaysManualRiskTiers=["P0_OPERATION_SECURITY_DATA"]},
    p=>{p.processingRules.initialFraction=0},
    p=>{p.processingRules.minCardsPerNonemptyStratum=0}
  ]){
    const changed=structuredClone(x.policy);action(changed);
    assert.throws(()=>validatePolicy(changed));
  }
  const stale=notesFor([note(c.localeRows[0])],x);
  stale.entries[0].sourceHash="sha256:"+"f".repeat(64);
  assert.equal(evaluateProvisionalNotes(x.riskPlan,x.batchPlan,stale).status,
    "INVALID_UNTRUSTED_NOTES");
  const fake=notesFor([note(c.localeRows[0])],x);
  fake.entries[0].approved=true;
  assert.equal(evaluateProvisionalNotes(x.riskPlan,x.batchPlan,fake).status,
    "INVALID_UNTRUSTED_NOTES");
});
test("L12-8B standalone sample desk renders only keyed sample with parseable JS",()=>{
  const x=fixture(),r=x.riskPlan,html=renderSamplingDesk(r);
  assert(html.includes("L12-8B P3 診斷抽樣工作台"));
  const marker='<script id="review-data" type="application/json">';
  const at=html.indexOf(marker),end=html.indexOf("</script>",at);
  assert(at>0 && end>at);
  const data=JSON.parse(html.slice(at+marker.length,end));
  assert.equal(data.summary.reviewKeyCards,r.summary.sampleKeyCards);
  assert.equal(data.batches.flatMap(b=>b.cards).length,r.summary.sampleKeyCards);
  assert.equal(data.summary.reviewSetDigest,r.summary.reviewSetDigest);
  const js=html.slice(html.lastIndexOf("<script>")+8,html.lastIndexOf("</script>"));
  assert.doesNotThrow(()=>new Script(js));
  assert(!html.includes("<!--L12B7_DATA-->"));
});
