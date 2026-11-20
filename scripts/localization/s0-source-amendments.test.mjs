#!/usr/bin/env node
import assert from "node:assert/strict";
import { inputs, verifyS0 } from "./s0-source-amendments.mjs";
const source=inputs();
assert(verifyS0(source).releaseBlocked);
const mutations=[
 v=>{v.amend.items[0].currentSourceHash="sha256:forged"},
 v=>{v.amend.items[0].baselineSource="rewritten history"},
 v=>{v.amend.items[0].locales.ja.reviewState="APPROVED"},
 v=>{v.current["zh-TW"]="unexpected resource change"},
 v=>{v.defect.items[0].status="RESOLVED"},
 v=>{v.defect.items[0].releaseBlocking=false},
 v=>{v.ledger.approvals=[{decision:"APPROVED"}]},
 v=>{v.amend.items[0].locales["zh-TW"].newCandidateBasedOnSourceHash="sha256:stale"}
];
for(const change of mutations){const v=structuredClone(source);change(v);assert.throws(()=>verifyS0(v));}
console.log("S0_NEGATIVE_TESTS PASS eight invalid source, review and defect mutations rejected");
