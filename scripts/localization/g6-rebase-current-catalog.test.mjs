#!/usr/bin/env node
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { buildG6, loadInputs, parseAndroidStrings } from "./g6-rebase-current-catalog.mjs";
import core from "../../experiments/lp0-200/web/probe-core.js";
const original=loadInputs();
const result=buildG6(original);
assert.equal(result.catalog.entries.length,200);
assert.equal(result.catalog.revision,"g6-200-current-20261120");
assert.equal(result.manifest.changedSourceKeys.length,1);
assert.equal(result.manifest.sourceChangedLocaleRows,2);
assert.equal(result.reviewQueue.items.length,400);
assert.equal(result.reviewQueue.humanApprovedCount,0);
assert.equal(result.manifest.mayDistributeAsOfficial,false);
assert.equal(result.reviewQueue.items.filter(x=>x.sourceChanged).length,2);
for(const locale of ["en","zh-TW","ja"]){
 assert.equal(result.packs[locale].catalogRevision,result.catalog.revision);
 const verified=core.validatePack(result.packs[locale],result.catalog);
 assert.equal(Object.keys(verified.translations).length,200);
 assert(Buffer.byteLength(JSON.stringify(result.packs[locale]),"utf8")<=256*1024);
}
const key="music_playlist_integration_pending";
assert.equal(result.catalog.entries.find(x=>x.key===key).source,original.s0.items[0].currentSource);
for(const locale of ["zh-TW","ja"]){
 assert.equal(result.packs[locale].translations[key],original.s0.items[0].locales[locale].currentCandidate);
 assert.notEqual(result.packs[locale].translations[key],original.baselinePacks[locale].translations[key]);
 assert.throws(()=>core.validatePack(result.packs[locale],original.catalog),/不相容/);
}
const dir=resolve("build/localization/G6_UNREVIEWED_CURRENT_PREVIEW");
for(const path of ["manifest.json","current-catalog.json","current-review-queue.json"]){
 const saved=JSON.parse(readFileSync(join(dir,path),"utf8"));
 const expected=path==="manifest.json"?result.manifest:(path==="current-catalog.json"?result.catalog:result.reviewQueue);
 assert.deepEqual(saved,expected,"Generated artifact differs from tested implementation: "+path);
}
for(const locale of ["en","zh-TW","ja"]){
 assert.deepEqual(JSON.parse(readFileSync(join(dir,"packs",locale+".json"),"utf8")),result.packs[locale]);
}
const mutations=[
 v=>{v.resources.en.nav_stopwatch="unrecorded source drift";},
 v=>{v.s0.items[0].currentSourceHash="sha256:forged";},
 v=>{v.s0.items[0].locales.ja.currentCandidate="untracked current text";},
 v=>{v.ledger.approvals=[{decision:"APPROVED"}];},
 v=>{v.g5["zh-TW"].translations.nav_stopwatch="tampered legacy";},
 v=>{v.catalog.entries.find(x=>x.key===key).source="history rewritten";},
 v=>{v.g5manifest.mayDistributeAsOfficial=true;},
 v=>{v.g5manifest.staleCanonicalSourceKeys=[];},
 v=>{v.g5.ja.catalogRevision="wrong";},
 v=>{v.resources.en[key]="unregistered corrected source";},
 v=>{v.resources.ja[key]="stale Android locale";},
 v=>{v.s0.items[0].locales["zh-TW"].reviewState="APPROVED";}
];
for(const mutate of mutations){
 const changed=structuredClone(original);mutate(changed);
 assert.throws(()=>buildG6(changed),"Must reject mutation that could corrupt G6 candidate sources");
}
assert.deepEqual(parseAndroidStrings('<resources><string name="sample">A &amp; B</string></resources>'),{sample:"A & B"});
console.log("G6_NEGATIVE_TESTS PASS: 12 invalid source/pack/approval mutations rejected, 200 current keys and 400 unreviewed locale rows validated");
