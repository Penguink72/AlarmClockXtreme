#!/usr/bin/env node
/* G6: rebase 200 real Android source strings without rewriting LP0 history.
 * This is a preview-only catalog and review queue, NEVER a release approval. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { loadJson, shaSource } from "./lp0-audit.mjs";
import core from "../../experiments/lp0-200/web/probe-core.js";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const NEW_REVISION="g6-200-current-20261120";
const CANONICAL_COMMIT="b8c8b9c9655b03d54452d8947ee851f8588edba1";
const LOCALES=["zh-TW","ja"];
const digest=value=>"sha256:"+createHash("sha256").update(JSON.stringify(value),"utf8").digest("hex");

function unescapeXml(value) {
 return value.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi,(all,entity)=>{
  if(entity[0]==="#")return String.fromCodePoint(entity[1].toLowerCase()==="x"?parseInt(entity.slice(2),16):parseInt(entity.slice(1),10));
  return ({amp:"&",lt:"<",gt:">",quot:'"',apos:"'"})[entity.toLowerCase()];
 });
}
export function parseAndroidStrings(xml) {
 const result={};
 const pattern=/<string\b([^>]*)\bname="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
 for(const match of xml.matchAll(pattern)){
  const attributes=match[1]+match[3],raw=match[4];
  if(/translatable\s*=\s*"false"/.test(attributes)||/<[^>]+>/.test(raw))continue;
  assert(!Object.hasOwn(result,match[2]),"Duplicate Android string resource "+match[2]);
  result[match[2]]=unescapeXml(raw);
 }
 return result;
}
const xmlFor=folder=>readFileSync(join(root,"app/src/main/res",folder,"strings.xml"),"utf8");
export function loadInputs(){
 const previewDir=join(root,"build/localization/UNREVIEWED_PREVIEW_NOT_RELEASE");
 const read=path=>JSON.parse(readFileSync(path,"utf8"));
 return {
  catalog:loadJson("experiments/lp0-200/catalog.json"),
  matrix:loadJson("localization/governance/lp0/source-matrix.json"),
  s0:loadJson("localization/governance/s0/source-amendments.json"),
  defects:loadJson("localization/governance/g4/source-defects.json"),
  ledger:loadJson("localization/governance/g4/review-ledger.json"),
  g5manifest:read(join(previewDir,"manifest.json")),
  baselinePacks:Object.fromEntries(["en",...LOCALES].map(locale=>[locale,loadJson("experiments/lp0-200/packs/"+locale+".json")])),
  g5:Object.fromEntries(LOCALES.map(locale=>[locale,read(join(previewDir,locale+".json"))])),
  resources:{
   en:parseAndroidStrings(xmlFor("values")),
   "zh-TW":parseAndroidStrings(xmlFor("values-b+zh+Hant+TW")),
   ja:parseAndroidStrings(xmlFor("values-ja"))
  }
 };
}
export function buildG6({catalog,matrix,s0,defects,ledger,g5manifest,baselinePacks,g5,resources}){
 assert.equal(catalog.format,"acx-language-catalog-probe/1");
 assert.equal(catalog.revision,"lp0-200-20261118");
 assert.equal(catalog.count,200);
 assert.equal(catalog.entries.length,200);
 assert.equal(matrix.baselineRevision,catalog.revision);
 assert.equal(matrix.records.length,200);
 assert.equal(s0.schema,"acx-s0-canonical-source-amendments/1");
 assert.equal(s0.baselineCatalogRevision,catalog.revision);
 assert.equal(s0.amendmentRevision,"s0-20261120");
 assert.equal(s0.items.length,1,"G6 rebase accepts exactly the single S0 source correction");
 assert.equal(s0.items[0].key,"music_playlist_integration_pending");
 assert.equal(defects.catalogRevision,catalog.revision);
 assert.equal(ledger.approvals.length,0,"G6 cannot silently inherit human approvals");
 assert.equal(g5manifest.catalogRevision,catalog.revision);
 assert.equal(g5manifest.releaseChannel,"UNREVIEWED_PREVIEW_NOT_RELEASE");
 assert.equal(g5manifest.mayDistributeAsOfficial,false);
 assert.equal(g5manifest.reviewStatus,"no-human-approval");
 assert.deepEqual(g5manifest.staleCanonicalSourceKeys,["music_playlist_integration_pending"]);
 const corrections=new Map(s0.items.map(a=>[a.key,a]));
 const changed=[], records=[], entries=[];
 for(const [index,old] of catalog.entries.entries()){
  const matrixRow=matrix.records[index];
  assert.equal(matrixRow.key,old.key);
  assert.equal(matrixRow.source,old.source);
  assert.equal(matrixRow.sourceHash,shaSource(old.key,old.source));
  assert.deepEqual(matrixRow.placeholders,old.placeholders);
  assert.equal(baselinePacks.en.translations[old.key],old.source);
  const current=resources.en[old.key];
  assert.equal(typeof current,"string","Canonical Android English key missing: "+old.key);
  const s0change=corrections.get(old.key);
  if(s0change){
   assert.equal(s0change.baselineSource,old.source);
   assert.equal(s0change.baselineSourceHash,matrixRow.sourceHash);
   assert.equal(s0change.currentSource,current);
   assert.equal(s0change.currentSourceHash,shaSource(old.key,current));
   assert.notEqual(current,old.source);
   assert.equal(s0change.canonicalFixState,"APPLIED_TO_ANDROID_RESOURCES");
   assert.equal(s0change.existingTranslations,"STALE_REVIEW_REQUIRED");
   assert.equal(s0change.humanApproved,false);
   changed.push({key:old.key,previousSourceHash:matrixRow.sourceHash,currentSourceHash:s0change.currentSourceHash});
  }else{
   assert.equal(current,old.source,"Undeclared Android English source drift: "+old.key);
  }
  assert.deepEqual(core.placeholders(current),old.placeholders,"Placeholder change: "+old.key);
  entries.push({...old,source:current});
  records.push({key:old.key,section:old.section,previousSource:old.source,currentSource:current,
   previousSourceHash:matrixRow.sourceHash,currentSourceHash:shaSource(old.key,current),
   sourceChanged:Boolean(s0change),placeholders:old.placeholders,
   contextStatus:"REVIEW_NOT_INHERITED_FROM_LP0"});
 }
 assert.equal(changed.length,1);
 const sections=Object.fromEntries([...new Set(entries.map(e=>e.section))].sort().map(section=>
  [section,{total:entries.filter(e=>e.section===section).length}]));
 const currentCatalog={
  format:"acx-language-catalog-probe/1",revision:NEW_REVISION,
  previousRevision:catalog.revision,baseCommit:CANONICAL_COMMIT,sourceLocale:"en",
  sourcePath:catalog.sourcePath,count:200,sections,entries
 };
 const rows=[],packs={};
 packs.en={format:"acx-language-pack-probe/1",locale:"en",catalogRevision:NEW_REVISION,
  packVersion:"g6-canonical-source-unreviewed",translations:Object.fromEntries(entries.map(e=>[e.key,e.source]))};
 for(const locale of LOCALES){
  const historical=baselinePacks[locale],previous=g5[locale];
  assert.equal(historical.catalogRevision,catalog.revision);
  assert.equal(previous.catalogRevision,catalog.revision);
  assert.equal(previous.format,"acx-language-pack-probe/1");
  assert.equal(previous.locale,locale);
  assert.equal(Object.keys(previous.translations).length,200);
  const translations={};
  for(const [index,entry] of entries.entries()){
   const old=catalog.entries[index];
   const oldText=historical.translations[entry.key];
   const change=corrections.get(entry.key);
   let text=previous.translations[entry.key];
   assert.equal(typeof text,"string","G5 candidate missing: "+locale+"/"+entry.key);
   assert.deepEqual(core.placeholders(text),entry.placeholders,"G5 candidate format changed");
   let origin;
   if(change){
    const amended=change.locales[locale];
    assert(amended,"Missing corrected locale "+locale);
    assert.equal(amended.baseline,oldText);
    assert.equal(amended.originalBasedOnSourceHash,shaSource(entry.key,old.source));
    assert.equal(amended.newCandidateBasedOnSourceHash,shaSource(entry.key,entry.source));
    assert.equal(amended.reviewState,"STALE_REVIEW_REQUIRED");
    assert.equal(amended.humanApproved,false);
    assert.equal(resources[locale][entry.key],amended.currentCandidate,"Canonical locale source differs from S0 amendment");
    assert.equal(text,oldText,"G5 historical pack was silently updated");
    text=amended.currentCandidate;
    origin="S0_UNREVIEWED_TRANSLATION_CANDIDATE";
   }else if(oldText!==undefined){
    assert.equal(text,oldText,"Old existing translation changed: "+entry.key);
    assert.equal(resources[locale][entry.key],oldText,"Undeclared Android locale drift: "+entry.key);
    origin="LEGACY_UNREVIEWED";
   }else{
    assert.equal(resources[locale][entry.key],undefined,"Unexpected translated Android resource in "+locale+"/"+entry.key);
    origin="LLM_DRAFT_UNREVIEWED";
   }
   assert.deepEqual(core.placeholders(text),entry.placeholders,"Current candidate format drift");
   translations[entry.key]=text;
   rows.push({key:entry.key,locale,previousSourceHash:shaSource(entry.key,old.source),
    currentSourceHash:shaSource(entry.key,entry.source),text,origin,
    sourceChanged:Boolean(change),reviewStatus:change?"SOURCE_CHANGED_REVIEW_REQUIRED":"INDEPENDENT_REVIEW_REQUIRED",
    semanticApproved:false,humanApproval:null});
  }
  packs[locale]={format:"acx-language-pack-probe/1",locale,catalogRevision:NEW_REVISION,
   packVersion:"g6-UNREVIEWED-PREVIEW-NOT-RELEASE",translations};
 }
 assert.equal(rows.length,400);
 assert.equal(rows.filter(x=>x.sourceChanged).length,2);
 assert.equal(rows.filter(x=>x.origin==="S0_UNREVIEWED_TRANSLATION_CANDIDATE").length,2);
 assert.equal(rows.filter(x=>x.origin==="LEGACY_UNREVIEWED").length,308);
 assert.equal(rows.filter(x=>x.origin==="LLM_DRAFT_UNREVIEWED").length,90);
 const reviewQueue={schema:"acx-g6-current-review-queue/1",catalogRevision:NEW_REVISION,
  previousCatalogRevision:catalog.revision,sourceCommit:CANONICAL_COMMIT,
  reviewAuthority:"NONE",humanApprovedCount:0,items:rows};
 const manifest={schema:"acx-g6-current-preview-manifest/1",
  previousCatalogRevision:catalog.revision,currentCatalogRevision:NEW_REVISION,
  canonicalSourceCommit:CANONICAL_COMMIT,sourceKeys:200,localeCandidateRows:400,
  changedSourceKeys:changed,sourceChangedLocaleRows:2,semanticallyApproved:0,
  reviewStatus:"UNREVIEWED_PREVIEW_ONLY",mayDistributeAsOfficial:false,
  machinePassIsNotSemanticApproval:true,
  provenance:{baselineCatalogDigest:digest(catalog),s0AmendmentDigest:digest(s0),
   currentCatalogDigest:digest(currentCatalog),reviewQueueDigest:digest(reviewQueue)}};
 return {manifest,catalog:currentCatalog,reviewQueue,packs,sourceRecords:records};
}
function main(){
 const args=process.argv,mode=args.includes("--mode")?args[args.indexOf("--mode")+1]:"preview";
 assert.equal(mode,"preview","G6 RELEASE BLOCKED: current translations are not independently reviewed");
 const result=buildG6(loadInputs());
 const idx=args.indexOf("--out-dir");
 if(idx>=0){
  const dest=resolve(root,args[idx+1]||"");
  assert(dest.startsWith(root+sep),"Refusing output outside repository");
  mkdirSync(join(dest,"packs"),{recursive:true});
  for(const [file,obj] of [["manifest.json",result.manifest],["current-catalog.json",result.catalog],
    ["current-review-queue.json",result.reviewQueue],["current-source-records.json",{records:result.sourceRecords}]]){
   writeFileSync(join(dest,file),JSON.stringify(obj,null,2)+"\n");
  }
  for(const [locale,pack] of Object.entries(result.packs))
   writeFileSync(join(dest,"packs",locale+".json"),JSON.stringify(pack,null,2)+"\n");
  writeFileSync(join(dest,"README.txt"),
   "G6 UNREVIEWED PREVIEW ONLY; NOT AN OFFICIAL LANGUAGE PACK.\n"+
   "Canonical 200-key source catalog rebased from S0. 1 source key and 2 locale candidate texts changed.\n"+
   "Semantic approvals: ZERO. The original LP0/LP1 Debug snapshot remains immutable.\n"+
   "Do not import this revision into the unchanged LP1 Debug lab: its catalogRevision still expects lp0-200-20261118.\n");
 }
 console.log("G6_CURRENT_CATALOG PASS "+JSON.stringify({sources:200,changedSourceKeys:1,
  candidateRows:400,localeRebaseRows:2,semanticApprovals:0,releaseAllowed:false}));
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
