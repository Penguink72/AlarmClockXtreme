#!/usr/bin/env node
/*
 * B0 evidence triage for unresolved R.string refs and suspected hardcoded
 * labels. No automatic deletion, translation, or runtime visibility claims.
 */
import { resolve, join, dirname } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { buildInventory } from "./b0-resource-inventory.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

export function classifyLiteral(preview) {
  if (/^\s*@(string|plurals|array|android:)/.test(preview))
    return "RESOURCE_REFERENCE_NOT_HARDCODED";
  if (/^\s*(?:[#$.:/+-]?\$\{[^}]+\}[#$.:/+-]?)+\s*$/.test(preview) ||
      /^[\d#.:/+-]+$/.test(preview))
    return "DYNAMIC_OR_STRUCTURAL_NOT_PROVEN_TEXT";
  if (/^[a-z][A-Za-z0-9_-]+$/.test(preview) && !/\s/.test(preview))
    return "PROBABLE_INTERNAL_IDENTIFIER";
  if (/[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}]/u.test(preview))
    return "HUMAN_LANGUAGE_CANDIDATE";
  if (/[a-zA-Z]{2,}\s+[a-zA-Z]{2,}/.test(preview))
    return "HUMAN_LANGUAGE_CANDIDATE";
  return "CONTEXT_REQUIRED";
}

export function analyzeUnresolved(inventory) {
  const unresolved = inventory.sourceKeys
    .filter(row => row.callsiteStatus === "NO_STATIC_REFERENCE_FOUND")
    .map(row => ({
      key:row.key, sourcePath:row.path,sourceLine:row.line,
      existingLocales:row.translatedVariants,
      status:"NO_STATIC_CALLSITE_YET",
      deletionAllowed:false,
      nextCheck:"Check aliasing, debug-only, indirect generated R IDs and history"
    }));
  const possibleLiterals = inventory.possibleHardcodedUiText.map(hit=>({
    ...hit,classification:classifyLiteral(hit.preview),review:"NOT_RUNTIME_VERIFIED"
  }));
  const counts = possibleLiterals.reduce((acc,r)=>{
    acc[r.classification]=(acc[r.classification]||0)+1;return acc;
  },{});
  return {
    schema:"acx-b0-indirect-and-ui-literal-triage/1",
    rootSource:"B0 whole-app inventory, not Android runtime observations",
    stats:{unresolvedKeys:unresolved.length,literalCandidateLocations:possibleLiterals.length,
      literalCategoryCounts:counts},
    unresolvedKeys:unresolved,
    potentialLiterals:possibleLiterals
  };
}

if(process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href){
  const arg=process.argv.indexOf("--out-dir");
  const dir=arg<0?join(ROOT,"build/localization/B0_FULL_APP_INVENTORY"):
    resolve(process.argv[arg+1]??"");
  if(!dir.startsWith(ROOT+(process.platform==="win32"?"\\":"/")))
    throw Error("Output dir must be inside repo");
  const report=analyzeUnresolved(buildInventory(ROOT));
  mkdirSync(dir,{recursive:true});
  writeFileSync(join(dir,"unresolved-and-ui-literal-triage.json"),JSON.stringify(report,null,2)+"\n");
  console.log("B0_UI_AND_UNRESOLVED_TRIAGE "+JSON.stringify(report.stats));
}
