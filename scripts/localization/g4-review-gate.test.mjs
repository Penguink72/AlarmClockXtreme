#!/usr/bin/env node
import assert from "node:assert/strict";
import { loadJson } from "./lp0-audit.mjs";
import { checkReviewQueue, digest, evidenceHash } from "./g4-review-gate.mjs";
const queue=loadJson("localization/governance/g3/review-queue.generated.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const contexts=loadJson("localization/governance/g2/context-evidence.json");
const ledger=loadJson("localization/governance/g4/review-ledger.json");
const get=(q=queue,g=glossary,c=contexts,l=ledger)=>checkReviewQueue(q,g,c,l);
const baseline=get();
assert.equal(baseline.releaseAllowed,false);
for(const locale of ["zh-TW","ja"]){
 assert.equal(baseline.stats[locale].total,200);
 assert.equal(baseline.stats[locale].structured,200);
 assert.equal(baseline.stats[locale].approved,0);
}
assert.equal(ledger.approvals.length,0);
{
 const q=structuredClone(queue);
 q.locales["zh-TW"][0].humanApproval={approved:true};
 assert.throws(()=>get(q),/self-approve/);
}
{
 const q=structuredClone(queue);
 q.locales.ja[0].text="Corrupted %1$d";
 assert.throws(()=>get(q),/placeholder/);
}
{
 const q=structuredClone(queue);
 q.locales.ja[0].sourceHash="sha256:forged";
 assert.throws(()=>get(q),/Expected values|strictEqual|forged/);
}
const candidate=queue.locales["zh-TW"].find(x=>x.text!==null &&
  contexts.records.find(e=>e.key===x.key).sourceCallsites.length>0);
assert(candidate,"Need one real code source reference for approval gate regression");
const context=contexts.records.find(x=>x.key===candidate.key);
const approval={
 key:candidate.key,locale:"zh-TW",decision:"APPROVED",reviewMode:"human-independent-semantic-review",
 sourceHash:candidate.sourceHash,glossaryRevision:glossary.glossaryRevision,
 translationDigest:digest(candidate.text),contextEvidenceDigest:evidenceHash(context),
 reviewerId:"fixture-reviewer",reviewNotes:"Test fixture only; no actual translation approval.",
 reviewedAt:"2026-11-19T12:00:00Z",semanticMeaningChecked:true,
 actionDirectionChecked:true,contextVerified:true
};
{
 const l=structuredClone(ledger);l.approvals.push(approval);
 assert.throws(()=>get(queue,glossary,contexts,l),/Glossary not yet approved/);
}
{
 const l=structuredClone(ledger);l.approvals.push({...approval,translationDigest:"sha256:wrong"});
 assert.throws(()=>get(queue,glossary,contexts,l),/Stale translation approval/);
}
// Synthetic green path checks a future explicit review can be represented; not committed as real approval.
{
 const g=structuredClone(glossary);g.approval="APPROVED_FOR_RELEASE";
 for(const t of g.concepts)t.labelState="APPROVED";
 const l=structuredClone(ledger);l.approvals.push(approval);
 const report=get(queue,g,contexts,l);
 assert.equal(report.stats["zh-TW"].approved,1);
 assert.equal(report.releaseAllowed,true);
}
console.log("G4_TESTS PASS: fake self-approval, stale hash, invalid placeholders and unapproved glossary rejected");
