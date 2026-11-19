#!/usr/bin/env node
import assert from "node:assert/strict";
import { loadJson } from "./lp0-audit.mjs";
const g=loadJson("localization/governance/g1/glossary-contract.json");
const source=loadJson("localization/governance/lp0/source-matrix.json");
const original=loadJson("localization/governance/lp0/terminology-candidates.json");
const known=new Set(source.records.map(x=>x.key));
assert.equal(g.schema,"acx-glossary-contract/1");
assert.equal(g.catalogRevision,source.baselineRevision);
assert.equal(g.approval,"UNAPPROVED_FOR_RELEASE");
assert.equal(g.concepts.length,18);
assert.equal(g.concepts.length,original.concepts.length);
const terms=new Set();
for(const t of g.concepts){
 assert(!terms.has(t.id));terms.add(t.id);
 assert.equal(t.labelState,"CANDIDATE_NOT_APPROVED");
 for(const locale of ["en","zh-TW","ja"])assert(typeof t.labels[locale]==="string" && t.labels[locale].trim());
 for(const key of t.matchedKeys)assert(known.has(key));
 for(const c of t.confusableWith)assert(g.concepts.some(x=>x.id===c));
}
for(const [a,b]of g.policy.neverEquate)assert(terms.has(a)&&terms.has(b));
console.log("G1_TESTS PASS: 18 provisional concepts, labels, source mapping and strict non-equivalence");
