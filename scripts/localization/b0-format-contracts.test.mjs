#!/usr/bin/env node
import assert from "node:assert/strict";
import test from "node:test";
import { argumentsOnly, signatureDisposition, generateFormatAudit }
  from "./b0-format-contracts.mjs";

test("literal percent is not an argument placeholder",()=>{
  assert.deepEqual(argumentsOnly(["%%","%1$d","%2$s"]),["%1$d","%2$s"]);
  assert.equal(signatureDisposition(["%%"],["%%"]),"LEXICAL_SIGNATURE_MATCH_ONLY");
});

test("printf type and index mismatches remain review candidates",()=>{
  assert.equal(signatureDisposition(["%1$d"],["%1$s"]),
    "POSSIBLE_ARGUMENT_SIGNATURE_MISMATCH");
  assert.equal(signatureDisposition(["%1$d","%2$s"],["%1$d"]),
    "POSSIBLE_ARGUMENT_SIGNATURE_MISMATCH");
  assert.equal(signatureDisposition(["%1$d"],null),"NO_LOCALE_DEFINITION");
});

test("current Android app-wide formatted and plural sources are indexed",()=>{
  const audit=generateFormatAudit();
  assert(audit.summary.authoritativeFormattedKeys>20);
  assert(audit.summary.authoritativePluralKeys>10);
  assert.equal(audit.summary.examinedLocaleRows,audit.summary.unionReviewKeys*2);
  assert(audit.rows.every(r=>r.status==="MECHANICAL_SURFACE_ONLY_NOT_SEMANTIC_APPROVAL"));
  assert(audit.rows.some(r=>r.key==="string:firing_snooze_for_minutes"&&r.locale==="ja"));
  assert(audit.rows.some(r=>r.type==="plurals"&&r.pluralCategoriesRequireReview));
});
