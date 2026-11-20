#!/usr/bin/env node
import assert from "node:assert/strict";
import { buildPacks } from "./g5-build-language-packs.mjs";
import { loadJson } from "./lp0-audit.mjs";
import core from "../../experiments/lp0-200/web/probe-core.js";
const sourceDefects=loadJson("localization/governance/g4/source-defects.json");
const catalog=loadJson("experiments/lp0-200/catalog.json");
const glossary=loadJson("localization/governance/g1/glossary-contract.json");
const queue=loadJson("localization/governance/g3/review-queue.generated.json");
const qa=loadJson("localization/governance/g4/qa-report.generated.json");
const preview=buildPacks("preview",catalog,glossary,queue,qa,sourceDefects);
assert.deepEqual(preview.manifest.staleCanonicalSourceKeys,["music_playlist_integration_pending"]);
assert.equal(preview.manifest.frozenBaselinePreview,true);
assert.equal(preview.manifest.releaseChannel,"UNREVIEWED_PREVIEW_NOT_RELEASE");
assert.equal(preview.manifest.mayDistributeAsOfficial,false);
for(const locale of ["zh-TW","ja"]){
 assert.equal(Object.keys(preview.packs[locale].translations).length,200);
 // Contract fidelity: a G5 preview JSON must be accepted by the existing
 // LP0/LP1 runtime data-only importer without upgrading its format.
 const runtimeValidated = core.validatePack(preview.packs[locale],catalog);
 assert.equal(Object.keys(runtimeValidated.translations).length,200);
 assert(Buffer.byteLength(JSON.stringify(preview.packs[locale]),"utf8") < 256*1024);

 assert.equal(preview.manifest.totals[locale].unreviewedLegacy,155);
 assert.equal(preview.manifest.totals[locale].unreviewedModelDrafts,45);
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
console.log("G5_TESTS PASS 200 preview strings per locale, release denied without approvals, provenance enforced");

{
 // In-memory synthetic one-key approval: even a 'green' generic G4 flag
 // must NOT allow an incomplete locale to be published.
 const approvedGlossary=structuredClone(glossary);
 approvedGlossary.approval="APPROVED_FOR_RELEASE";
 approvedGlossary.concepts.forEach(c=>{c.labelState="APPROVED";});
 const partial=structuredClone(qa);
 partial.releaseAllowed=true;
 const sample=queue.locales["zh-TW"][0];
 partial.approvals=[{...sample,review:{sourceHash:sample.sourceHash,
   glossaryRevision:glossary.glossaryRevision,
   translationDigest:"sha256:fixture-only"}}];
 partial.stats["zh-TW"].approved=1;
 assert.throws(()=>buildPacks("release",catalog,approvedGlossary,queue,partial),
   /every 200-key sample translation/);
 // Fully synthetic fixture tests the successful release serialization path;
 // no such approval is stored in the real review-ledger.
 const fully=structuredClone(qa);
 fully.releaseAllowed=true;
 fully.approvals=[];
 for(const locale of ["zh-TW","ja"]){
   fully.stats[locale].approved=200;
   fully.stats[locale].reviewRequired=0;
   for(const candidate of queue.locales[locale]){
     fully.approvals.push({...candidate,review:{
       sourceHash:candidate.sourceHash,glossaryRevision:glossary.glossaryRevision,
       translationDigest:"sha256:"+((await import("node:crypto")).createHash("sha256")
         .update(candidate.text,"utf8").digest("hex"))
     }});
   }
 }
 for(const row of fully.rows)row.approved=true;
 assert.throws(()=>buildPacks("release",catalog,approvedGlossary,queue,fully,sourceDefects),
   /unresolved canonical English source defects/);
 // In-memory only: prove the serialization code would work after the source
 // is actually corrected, reviewed and the defect properly resolved.
 const resolved=structuredClone(sourceDefects);
 resolved.items[0].status="RESOLVED";
 resolved.items[0].releaseBlocking=false;
 assert.throws(()=>buildPacks("release",catalog,approvedGlossary,queue,fully,resolved),
   /source amendments have stale locale translations/);
 // Synthetic approval is a test-only artifact, never written to the repo.
 resolved.items[0].correction.translationReviewStatus="APPROVED";
 resolved.items[0].correction.localeReviews={"zh-TW":"APPROVED","ja":"APPROVED"};
 const syntheticRelease=buildPacks("release",catalog,approvedGlossary,queue,fully,resolved);
 assert.equal(syntheticRelease.manifest.mayDistributeAsOfficial,true);
 assert.equal(syntheticRelease.manifest.totals["zh-TW"].approved,200);
 assert.equal(syntheticRelease.manifest.totals.ja.approved,200);
 assert.equal(Object.keys(syntheticRelease.packs["zh-TW"].translations).length,200);
}
console.log("G5_STRICT_COVERAGE_TESTS PASS: partial approval and open stale English source blocked; synthetic fully approved/resolved fixture serializes");
