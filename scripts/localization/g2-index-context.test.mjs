#!/usr/bin/env node
import assert from "node:assert/strict";
import { out, compileConceptExclusions } from "./g2-index-context.mjs";
import { loadJson } from "./lp0-audit.mjs";
assert.equal(out.records.length,200);
assert(out.stats.filesScanned>20,"expected an Android codebase, not a small mock source");
assert(out.stats.withStaticReference>0,"no concrete callsite evidence");
assert.equal(out.stats.noStaticReference + out.stats.withStaticReference,200);
for(const r of out.records){
 assert.equal(r.semanticReview,"NOT_REVIEWED");
 assert.equal(r.guessedSurfaceVerified,false);
 for(const c of r.sourceCallsites){
  assert(c.path.startsWith("app/src/main/")||c.path.startsWith("wear/src/main/"));
  assert.equal(c.evidence,"STATIC_SOURCE_REFERENCE_ONLY");
  assert(c.match.includes(r.key),"evidence must include exact key");
  assert(c.line>0);
 }
}
console.log("G2_TESTS PASS: all 200 keys carry honest callsite and unknown-context statuses");

const contract=loadJson("localization/governance/g2/concept-exclusions.json");
const matrix=loadJson("localization/governance/lp0/source-matrix.json");
const terms=loadJson("localization/governance/g1/glossary-contract.json");
assert.equal(out.conceptBindingRevision,contract.revision);
assert.equal(out.stats.modelRefutedConceptLinks,10);
for(const rule of contract.exclusions){
 const row=out.records.find(x=>x.key===rule.key);
 assert(row.rawHeuristicConceptIds.includes(rule.conceptId));
 assert(!row.conceptIds.includes(rule.conceptId));
 assert(row.excludedConceptIds.includes(rule.conceptId));
}
assert.equal(compileConceptExclusions(matrix,terms,contract).size,10);
{
 const bad=structuredClone(contract);
 bad.exclusions.push({...bad.exclusions[0]});
 assert.throws(()=>compileConceptExclusions(matrix,terms,bad),/Duplicate exclusion/);
}
{
 const bad=structuredClone(contract);
 bad.exclusions[0].key="unknown-key";
 assert.throws(()=>compileConceptExclusions(matrix,terms,bad),/missing key/);
}
console.log("G2_CONCEPT_BINDING_TESTS PASS: historical heuristic links preserved, 10 false links excluded");
