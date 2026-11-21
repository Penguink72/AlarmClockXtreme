#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { buildInventory } from "./b0-resource-inventory.mjs";
import { classifyLiteral, analyzeUnresolved } from "./b0-ui-and-unused-triage.mjs";

test("B0 excludes resources and animation identifiers from claimed hardcoded content",()=>{
  assert.equal(classifyLiteral("@string/app_name"),"RESOURCE_REFERENCE_NOT_HARDCODED");
  assert.equal(classifyLiteral("pulseAlpha"),"PROBABLE_INTERNAL_IDENTIFIER");
  assert.equal(classifyLiteral("#${index + 1}"),"DYNAMIC_OR_STRUCTURAL_NOT_PROVEN_TEXT");
});

test("B0 retains real Chinese and English phrases for context investigation",()=>{
  assert.equal(classifyLiteral("匯入 JSON"),"HUMAN_LANGUAGE_CANDIDATE");
  assert.equal(classifyLiteral("Test Alarm"),"HUMAN_LANGUAGE_CANDIDATE");
});

test("24 undecided source keys are never automatically deleted or approved",()=>{
  const inv=buildInventory();
  const result=analyzeUnresolved(inv);
  assert.equal(result.stats.unresolvedKeys,inv.summary.noDirectStaticReferenceKeys);
  assert(result.unresolvedKeys.length>0);
  assert(result.unresolvedKeys.every(x=>x.deletionAllowed===false));
  assert(result.unresolvedKeys.some(x=>x.key==="string:alarm_music_settings_save"));
  assert.equal(result.potentialLiterals.length,inv.possibleHardcodedUiText.length);
});
