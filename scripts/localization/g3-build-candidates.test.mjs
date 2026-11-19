#!/usr/bin/env node
import assert from "node:assert/strict";
import { buildCandidates } from "./g3-build-candidates.mjs";
import { loadJson } from "./lp0-audit.mjs";
const source=loadJson("localization/governance/lp0/source-matrix.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const seeds=loadJson("localization/governance/g3/draft-proposals.json");
const contexts=loadJson("localization/governance/g2/context-evidence.json");
const build=(s,g,d,c)=>buildCandidates(s,g,d,c);
const result=build(source,glossary,seeds,contexts);
assert.equal(result.locales["zh-TW"].length,200);
assert.equal(result.stats["zh-TW"].legacy,155);
assert.equal(result.stats.ja.legacy,155);
assert.equal(result.stats["zh-TW"].llmDrafts,seeds.translations.length);
assert.equal(result.stats["zh-TW"].missing,45-seeds.translations.length);
assert.equal(result.stats.ja.approved,0);
{
 const bad=structuredClone(seeds);bad.translations[0].key="invented_key";
 assert.throws(()=>build(source,glossary,bad,contexts),/invented key/);
}
{
 const bad=structuredClone(seeds);bad.translations.push(seeds.translations[0]);
 assert.throws(()=>build(source,glossary,bad,contexts),/Duplicate/);
}
{
 const bad=structuredClone(seeds);bad.translations[0]["zh-TW"]="Missing %1$s";
 assert.throws(()=>build(source,glossary,bad,contexts),/placeholder/);
}
{
 const bad=structuredClone(source);const key=seeds.translations[0].key;
 bad.records.find(r=>r.key===key).translations.ja.text="existing";
 assert.throws(()=>build(bad,glossary,seeds,contexts),/Baseline translation state\/text mismatch|silently overwrite/);
}
{
 const bad=structuredClone(contexts);
 bad.records[0].sourceHash="forged";
 assert.throws(()=>build(source,glossary,seeds,bad));
}
console.log("G3_TESTS PASS missing coverage, no silent replacement, no fabricated keys or placeholders");
