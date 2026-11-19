#!/usr/bin/env node
import assert from "node:assert/strict";
import { out } from "./g2-index-context.mjs";
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
