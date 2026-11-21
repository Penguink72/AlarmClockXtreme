#!/usr/bin/env node
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {join,resolve} from "node:path";
import {auditG6Q1,loadQ1Inputs,toTsv} from "./g6q1-review-worklist.mjs";

const input=loadQ1Inputs();
const data=auditG6Q1(input);
const report=data.summary,rows=data.worklist;
assert.equal(report.totalLocaleRows,400);
assert.equal(report.preliminarilyTriagedLocaleRows,74);
assert.equal(report.notYetModelTriagedLocaleRows,326);
assert.equal(report.proposedLocaleRevisions,18);
assert.equal(report.sourceChangedLocaleRows,2);
assert.equal(report.independentApprovals,0);
assert.equal(report.glossaryReleaseApproved,false);
assert.equal(report.mayDistributeAsOfficial,false);
assert.equal(report.strictReleaseGate,"DENY");
assert.equal(rows.length,400);
const ids=rows.map(x=>x.locale+"\u0000"+x.key);
assert.equal(new Set(ids).size,400);
const proposed=rows.filter(x=>x.proposal);
assert.equal(proposed.filter(x=>x.proposalOrigin==="G4_PRIOR_MODEL_PROPOSAL").length,16);
assert.equal(proposed.filter(x=>x.proposalOrigin==="G6Q1_MODEL_PROPOSAL").length,2);
assert.equal(rows.filter(x=>x.g2EvidenceState==="HISTORICAL_SOURCE_HASH_REBASE_REQUIRED").length,2);
assert(rows.every(x=>x.independentApproved===false && x.humanApproval===null));
const tsv=toTsv(rows);
assert.equal(tsv.split("\n").length,402);
const out=resolve("build/localization/G6Q1_PRELIMINARY_NOT_APPROVED");
assert.deepEqual(JSON.parse(readFileSync(join(out,"manifest.json"),"utf8")),report);
const artifact=JSON.parse(readFileSync(join(out,"human-review-worklist.json"),"utf8"));
assert.equal(artifact.items.length,400);
assert.deepEqual(artifact.items,rows);
assert.equal(readFileSync(join(out,"human-review-worklist.tsv"),"utf8"),tsv);
assert(readFileSync(join(out,"README.txt"),"utf8").includes("release remains BLOCKED"));
const invalid=[
 x=>{x.triage.records[0].sourceHash="sha256:forged";},
 x=>{x.triage.records[0].humanApproved=true;},
 x=>{x.triage.records[0].reviewRationale="";},
 x=>{x.triage.independentApprovals=1;},
 x=>{x.triage.records[0].candidateOrigins.ja="APPROVED";},
 x=>{x.triage.records.find(v=>v.key==="music_playlist_remove").proposedReplacements.ja.text="任意改動";},
 x=>{x.triage.records.find(v=>v.key==="notif_snooze_countdown_title").proposedReplacements["zh-TW"].origin="HUMAN_APPROVED";},
 x=>{x.triage.records.find(v=>v.key==="firing_snooze_schedule_failed").candidates["zh-TW"]="tampered";},
 x=>{x.triage.records[0].priority="APPROVED";},
 x=>{x.triage.records.push(structuredClone(x.triage.records[0]));},
 x=>{x.packs.ja.translations.music_playlist_remove="altered";},
 x=>{x.queue.items[0].text="changed";},
 x=>{x.queue.items[0].semanticApproved=true;},
 x=>{x.queue.items.pop();},
 x=>{x.g4.approvals.push({decision:"APPROVED"});},
 x=>{x.glossary.approval="APPROVED_FOR_RELEASE";},
 x=>{x.s0.items[0].currentSourceHash="sha256:pretend";},
 x=>{x.g2.records.find(v=>v.key==="music_playlist_integration_pending").sourceHash=x.triage.records.find(v=>v.key==="music_playlist_integration_pending").sourceHash;}
];
for(let i=0;i<invalid.length;i++){
 const changed=structuredClone(input);
 invalid[i](changed);
 assert.throws(()=>auditG6Q1(changed),"G6Q1 must reject invalid review mutation "+i);
}
console.log("G6Q1_NEGATIVE_TESTS_PASS "+JSON.stringify({
 sourceRows:200,candidateRows:400,modelReviewedLocaleRows:74,stillUntriaged:326,
 priorG4Proposals:16,newModelProposals:2,invalidMutationsRejected:invalid.length,
 independentApprovals:0,releaseAllowed:false
}));
