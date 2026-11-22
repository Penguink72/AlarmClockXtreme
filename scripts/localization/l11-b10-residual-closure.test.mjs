#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import {dirname,resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildResidualClosure} from "./l11-b10-residual-closure.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
test("L11-10 creates exact remaining gap ledger, with no implicit approval",()=>{
  const input=loadL11Inputs(ROOT,"L11-B10-SOURCE");
  const data=buildResidualClosure(buildL11(input),input.graph);
  assert.equal(data.stats.remainingLocaleRows,114);
  assert.equal(data.stats.remainingKeys,57);
  assert.equal(data.stats.modelDraftRows,2986);
  assert.equal(data.stats.totalMissingDedicatedDefinitions,3100);
  assert.equal(data.stats.authorizedFallbacks,0);
  assert.equal(data.stats.independentApprovals,0);
  assert(data.keys.every(x=>x.locales.length===2 && x.locales.includes("zh-TW") && x.locales.includes("ja")));
  assert(data.rows.every(x=>!x.officialFallbackApproved&&!x.officialTranslationApproved));
  assert.equal(new Set(data.keys.map(x=>x.key)).size,data.keys.length);
});
