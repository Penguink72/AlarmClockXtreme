#!/usr/bin/env node
/*
 * B0 incremental audit: compare printf-like resource signatures and plural
 * categories in the actual Android base XML vs localized XML. This is a
 * candidate-only mechanical index, not an Android formatting simulation or
 * independent translation approval.
 */
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { extractResourceDefinitions } from "./b0-resource-inventory.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const resource=(root,qualifier)=>readFileSync(
  join(root,"app/src/main/res",qualifier,"strings.xml"),"utf8");

export function argumentsOnly(tokens) {
  return tokens.filter(t=>t!=="%%" && !t.endsWith("%"));
}

/**
 * Compare sorted distinct lexical tokens, NOT parameter counts or actual
 * argument values (the upstream extractor de-duplicates tokens). A mismatch
 * demands review; a match is only compatible at the lexical surface.
 */
export function signatureDisposition(source,translation) {
  const a=argumentsOnly(source||[]).slice().sort();
  if(!translation) return "NO_LOCALE_DEFINITION";
  const b=argumentsOnly(translation).slice().sort();
  if(a.length===b.length && a.every((v,i)=>v===b[i]))
    return "LEXICAL_SIGNATURE_MATCH_ONLY";
  if(a.length===0 && b.length===0) return "NO_FORMAT_ARGUMENTS";
  return "POSSIBLE_ARGUMENT_SIGNATURE_MISMATCH";
}

export function generateFormatAudit(root=ROOT) {
  const en=new Map(extractResourceDefinitions(resource(root,"values")).map(x=>[x.key,x]));
  const localeInputs=[
    ["zh-TW","values-b+zh+Hant+TW"],
    ["ja","values-ja"]
  ].map(([locale,qualifier])=>({
    locale,qualifier,
    defs:new Map(extractResourceDefinitions(resource(root,qualifier)).map(x=>[x.key,x]))
  }));
  const reviewKeys=[...en.values()].filter(r=>r.formatTokens.length>0 || r.type==="plurals");
  const rows=[];
  for(const source of reviewKeys) {
    for(const {locale,qualifier,defs} of localeInputs) {
      const target=defs.get(source.key);
      rows.push({
        key:source.key,locale,qualifier,type:source.type,
        sourceLine:source.line,localeLine:target?.line??null,
        sourceTokens:source.formatTokens,
        localizedTokens:target?.formatTokens??null,
        lexicalSignature:signatureDisposition(source.formatTokens,target?.formatTokens??null),
        sourcePluralQuantities:source.pluralQuantities,
        localePluralQuantities:target?.pluralQuantities??null,
        pluralCategoriesRequireReview:source.type==="plurals",
        status:"MECHANICAL_SURFACE_ONLY_NOT_SEMANTIC_APPROVAL"
      });
    }
  }
  const summary={
    authoritativeFormattedKeys:[...en.values()].filter(x=>x.formatTokens.length>0).length,
    authoritativePluralKeys:[...en.values()].filter(x=>x.type==="plurals").length,
    unionReviewKeys:reviewKeys.length,
    examinedLocaleRows:rows.length,
    absentLocaleDefinitions:rows.filter(x=>x.localeLine===null).length,
    lexicalMismatches:rows.filter(x=>x.lexicalSignature==="POSSIBLE_ARGUMENT_SIGNATURE_MISMATCH").length,
    lexicalMatches:rows.filter(x=>x.lexicalSignature==="LEXICAL_SIGNATURE_MATCH_ONLY").length,
    reviewOnlyNoApprovals:true
  };
  return {schema:"acx-b0-format-and-plurals-audit/1",
    scope:"Two locale definitions vs current base XML; static only, no actual UI formatting",
    summary,rows};
}

if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  const at=process.argv.indexOf("--out-dir");
  const dir=at<0?join(ROOT,"build/localization/B0_FULL_APP_INVENTORY"):
    resolve(process.argv[at+1]??"");
  if(!dir.startsWith(ROOT+(process.platform==="win32"?"\\":"/")))
    throw Error("Output directory must stay inside repo");
  const result=generateFormatAudit(ROOT);
  mkdirSync(dir,{recursive:true});
  writeFileSync(join(dir,"format-contract-review.json"),JSON.stringify(result,null,2)+"\n");
  console.log("B0_FORMAT_CONTRACT_REVIEW "+JSON.stringify(result.summary));
}
