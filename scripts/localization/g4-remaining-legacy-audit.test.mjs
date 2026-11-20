#!/usr/bin/env node
import assert from "node:assert/strict";
import { inputs,verifyRemaining } from "./g4-remaining-legacy-audit.mjs";
const original=inputs();const check=arg=>verifyRemaining(arg);
const result=check(original);
assert.equal(result.existingModelSurfaceScreened,113);
assert.equal(result.sourceDefectsOpen,1);
const correction=original.remaining.records.find(x=>Object.keys(x.suggestions).length);
{
 const b=structuredClone(original);b.remaining.humanApproved=true;assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.remaining.records.find(x=>x.key===correction.key).sourceEvidence.line=1;assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.remaining.records.find(x=>x.key===correction.key).suggestions["zh-TW"]="incorrect %1$s";assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.remaining.records.pop();assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.sourceDefects.items[0].status="RESOLVED";assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.unresolved.items[0].nearby.line=1;assert.throws(()=>check(b));
}
{
 const b=structuredClone(original);b.reviewLedger.approvals=[{locale:"ja",key:"fictional"}];assert.throws(()=>check(b));
}
console.log("G4_REMAINING_NEGATIVE_TESTS PASS: source defect cannot self-resolve, context/proposal drift and fake approval rejected");
