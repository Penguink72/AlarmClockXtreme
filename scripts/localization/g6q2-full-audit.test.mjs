#!/usr/bin/env node
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {join,resolve} from "node:path";
import {auditG6Q2,loadQ2Inputs,toTsv} from "./g6q2-full-audit.mjs";

const inputs=loadQ2Inputs();
const output=auditG6Q2(inputs);
const {manifest,worklist}=output;
assert.equal(output.noStaticCallsiteKeysInQ2,5);
assert.deepEqual(output.noStaticKeyNames,[
 "alarm_music_settings_mode_shared","alarm_music_settings_remove_track",
 "alarm_music_settings_save","music_library_history_row","music_library_tab_videos"
]);
assert.equal(manifest.sourceKeys,200);
assert.equal(manifest.candidateLocaleRows,400);
assert.equal(manifest.q1LocaleRows,74);
assert.equal(manifest.q2LocaleRows,326);
assert.equal(manifest.modelSurfaceScreenedLocaleRows,400);
assert.equal(manifest.q2FormattedKeys,21);
assert.equal(manifest.q2ContextFlaggedKeys,43);
assert.equal(manifest.q2ProposedLocaleRevisions,9);
assert.equal(manifest.totalProposedLocaleRevisions,27);
assert.equal(manifest.independentHumanApprovals,0);
assert.equal(manifest.officialRelease,"DENY");
assert.equal(manifest.glossaryApprovedForRelease,false);
assert.equal(worklist.length,400);
assert.equal(new Set(worklist.map(x=>x.locale+"\u0000"+x.key)).size,400);
assert.equal(worklist.filter(x=>x.sourceChanged).length,2);
assert.equal(worklist.filter(x=>x.proposal).length,27);
assert(worklist.every(x=>x.independentApproved===false&&x.humanApproval===null));
const releaseDir=resolve("build/localization/G6Q2_PRELIMINARY_NOT_APPROVED");
assert.deepEqual(JSON.parse(readFileSync(join(releaseDir,"manifest.json"),"utf8")),manifest);
const data=JSON.parse(readFileSync(join(releaseDir,"human-review-worklist.json"),"utf8"));
assert.deepEqual(data.items,worklist);
assert.equal(readFileSync(join(releaseDir,"human-review-worklist.tsv"),"utf8"),toTsv(worklist));
assert.equal(toTsv(worklist).split("\n").length,402);
assert(readFileSync(join(releaseDir,"README.txt"),"utf8").includes("release remains BLOCKED"));
const example=worklist.find(x=>x.key==="stats_snoozed"&&x.locale==="zh-TW");
assert.equal(example.candidate,"貪睡率");
assert.equal(example.proposal,null);
assert(example.contextNote.includes("snoozeRate%"));
const invalidMutations=[
 x=>{x.q2.records[0].sourceHash="sha256:forged";},
 x=>{x.q2.records[0].source="tampered EN";},
 x=>{x.q2.records[0].humanApproved=true;},
 x=>{x.q2.independentHumanApprovals=1;},
 x=>{x.q2.records.push(structuredClone(x.q2.records[0]));},
 x=>{x.q2.records[0].key=x.q1.triage.records[0].key;},
 x=>{x.q2.records[0].localeReview.ja.currentCandidate="outdated";},
 x=>{x.q2.records[0].localeReview["zh-TW"].humanApproved=true;},
 x=>{x.q2.records[0].localeReview.ja.independentReview={reviewerId:"fake"};},
 x=>{x.q2.records[0].priority="APPROVED";},
 x=>{x.q2.records[0].sourceHash=x.q1.triage.records[0].sourceHash;},
 x=>{x.q2.records.find(r=>r.key==="stats_snoozed").localeReview.ja.currentCandidate="スヌーズ中";},
 x=>{x.q2.records.find(r=>r.key==="ringtone_folder").localeReview.ja.proposalAuthority="INDEPENDENT_APPROVED";},
 x=>{x.q2.records.find(r=>r.key==="ringtone_folder").localeReview.ja.proposedReplacement="";},
 x=>{x.q2.records.find(r=>r.key==="ringtone_folder").localeReview.ja.proposedReplacement="%1$d";},
 x=>{x.q2.records.find(r=>r.key==="settings_title").localeReview.ja.proposedReplacement="変更";},
 x=>{x.q2.records.find(r=>r.key==="settings_dashboard_description").localeReview.ja.observation="no context";},
 x=>{x.q2.records.find(r=>r.key==="music_playlist_partial_add").placeholders=[];},
 x=>{x.q2.records.find(r=>r.key==="b2_test_scheduled").localeReview["zh-TW"].preliminaryAssessment="APPROVED";},
 x=>{x.q2.stats.proposedLocaleRevisions=10;},
 x=>{x.q2.catalogRevision="wrong";},
 x=>{x.q1.g4.approvals.push({decision:"APPROVED"});},
 x=>{x.q1.glossary.approval="APPROVED_FOR_RELEASE";},
 x=>{x.q1.packs.ja.translations.nav_stopwatch="source drift";},
 x=>{x.q1.g2.records.find(r=>r.key==="music_playlist_integration_pending").sourceHash=x.q1.triage.records.find(r=>r.key==="music_playlist_integration_pending").sourceHash;},
 x=>{x.q1.g2.records.find(r=>r.key==="alarm_music_settings_remove_track").sourceCallsites=[{path:"unverified",line:1}];}
];
for(let i=0;i<invalidMutations.length;i++){
 const input=structuredClone(inputs);
 invalidMutations[i](input);
 assert.throws(()=>auditG6Q2(input),"Mutation "+i+" must be blocked");
}
console.log("G6Q2_NEGATIVE_TESTS_PASS "+JSON.stringify({
 keys:200,priorQ1LocaleRows:74,newQ2LocaleRows:326,combined:400,
 formattedKeys:21,q2FlaggedKeys:43,q1Proposals:18,q2Proposals:9,
 rejectedMutations:invalidMutations.length,humanApprovals:0,officialRelease:"DENY"
}));
