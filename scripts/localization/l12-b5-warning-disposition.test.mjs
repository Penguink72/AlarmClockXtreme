#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {buildWarningDispositions} from "./l12-b5-warning-disposition.mjs";
function make(){
  const row={key:"string:test",locale:"ja",sourceHash:"s",translationHash:"t",reviewRevision:"r",
    sourcePreview:"Remove",candidateText:"Remove",flags:["UNCHANGED_ENGLISH_REVIEW"],
    actionSensitive:true,approved:false,reviewedBy:null};
  const r={key:row.key,locale:row.locale,sourceHash:"s",translationHash:"t",reviewRevision:"r"};
  return {triage:{schema:"acx-l12-b4-warning-triage/1",sourceSha:"sha",rows:[row]},
    preflight:{schema:"acx-l12-b3-resource-preflight/1",sourceSha:"sha",catalogueRevision:"rev",review:[r]}};
}
test("L12-5 explains warnings without elevating model suggestions to approval",()=>{
  const input=make(),r=buildWarningDispositions(input.triage,input.preflight);
  assert.equal(r.stats.flaggedRows,1);
  assert.equal(r.stats.approvedRows,0);
  assert.equal(r.items[0].independentApproval,false);
  assert.equal(r.items[0].mayRelease,false);
  assert(r.items[0].reviewCategories.includes("ACTION_SEMANTICS_ESCALATION"));
  assert(r.items[0].reviewCategories.includes("LIKELY_UNTRANSLATED_ENGLISH_NEEDS_REVIEW"));
});
test("L12-5 rejects stale source and fabricated approval",()=>{
  const x=make();x.triage.rows[0].translationHash="outdated";
  assert.throws(()=>buildWarningDispositions(x.triage,x.preflight),/Stale warning/);
  const y=make();y.triage.rows[0].approved=true;
  assert.throws(()=>buildWarningDispositions(y.triage,y.preflight),/cannot claim approval/);
});
test("L12-5 does not double-count multi-flag rows",()=>{
  const x=make();x.triage.rows[0].flags.push("XML_ESCAPING_REVIEW","LITERAL_PERCENT_REVIEW");
  const r=buildWarningDispositions(x.triage,x.preflight);
  assert.equal(r.stats.flaggedRows,1);assert.equal(r.stats.flagOccurrences,3);
  assert(r.items[0].reviewCategories.includes("ANDROID_PERCENT_FORMAT_RUNTIME_USAGE_REVIEW"));
});

test("L12-5 does not misclassify format-only skeletons as missing translated prose",()=>{
  const x=make();x.triage.rows[0].sourcePreview="%1$s • %2$s";
  x.triage.rows[0].candidateText="%1$s • %2$s";
  const r=buildWarningDispositions(x.triage,x.preflight);
  assert(r.items[0].reviewCategories.includes("FORMAT_ONLY_NO_PROSE_REVIEW"));
  assert(!r.items[0].reviewCategories.includes("LIKELY_UNTRANSLATED_ENGLISH_NEEDS_REVIEW"));
});
test("L12-5 preserves brand/proper-name candidates for independent verification",()=>{
  const x=make();x.triage.rows[0].sourcePreview="Wi-Fi";
  x.triage.rows[0].candidateText="Wi-Fi";
  const r=buildWarningDispositions(x.triage,x.preflight);
  assert(r.items[0].reviewCategories.includes("POSSIBLE_IDENTIFIER_RETENTION_NEEDS_EVIDENCE"));
  assert.equal(r.items[0].independentApproval,false);
});

test("L12-5 identifies middle-dot-only positional template as format not prose",()=>{
  const x=make();x.triage.rows[0].sourcePreview="%1$s · %2$s";
  x.triage.rows[0].candidateText="%1$s · %2$s";
  const r=buildWarningDispositions(x.triage,x.preflight);
  assert(r.items[0].reviewCategories.includes("FORMAT_ONLY_NO_PROSE_REVIEW"));
  assert(!r.items[0].reviewCategories.includes("LIKELY_UNTRANSLATED_ENGLISH_NEEDS_REVIEW"));
});
