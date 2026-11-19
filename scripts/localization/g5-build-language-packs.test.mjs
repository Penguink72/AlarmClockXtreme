#!/usr/bin/env node
import assert from "node:assert/strict";
import { buildPacks } from "./g5-build-language-packs.mjs";
import { loadJson } from "./lp0-audit.mjs";
const catalog=loadJson("experiments/lp0-200/catalog.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const queue=loadJson("localization/governance/g3/review-queue.generated.json");
const qa=loadJson("localization/governance/g4/qa-report.generated.json");
const preview=buildPacks("preview",catalog,glossary,queue,qa);
assert.equal(preview.manifest.releaseChannel,"UNREVIEWED_PREVIEW_NOT_RELEASE");
assert.equal(preview.manifest.mayDistributeAsOfficial,false);
for(const locale of ["zh-TW","ja"]){
 assert.equal(Object.keys(preview.packs[locale].translations).length,176);
 assert.equal(preview.manifest.totals[locale].unreviewedLegacy,155);
 assert.equal(preview.manifest.totals[locale].unreviewedModelDrafts,21);
 assert.equal(preview.manifest.totals[locale].approved,0);
}
assert.throws(()=>buildPacks("release",catalog,glossary,queue,qa),/Release blocked/);
{
 const q=structuredClone(queue);
 q.locales.ja[0].key="new_unregistered_key";
 assert.throws(()=>buildPacks("preview",catalog,glossary,q,qa));
}
{
 const changed=structuredClone(qa);changed.rows[0].sourceHash="forged";
 assert.throws(()=>buildPacks("preview",catalog,glossary,queue,changed));
}
console.log("G5_TESTS PASS 176 preview strings per locale, release denied without approvals, provenance enforced");
