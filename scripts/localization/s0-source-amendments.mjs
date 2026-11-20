#!/usr/bin/env node
/* S0: audit corrected canonical Android resources against frozen LP0 metadata. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { shaSource, loadJson } from "./lp0-audit.mjs";
import core from "../../experiments/lp0-200/web/probe-core.js";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const key="music_playlist_integration_pending";
function resource(folder){
 const xml=readFileSync(join(root,"app/src/main/res",folder,"strings.xml"),"utf8");
 const pieces=xml.split('<string name="'+key+'">');
 assert.equal(pieces.length,2,"Exactly one target resource in "+folder);
 const end=pieces[1].indexOf("</string>");
 assert(end>0,"Invalid S0 string resource");
 const value=pieces[1].slice(0,end);
 assert(!value.includes("&"),"Unexpected XML entity in S0 string");
 return value;
}
export const inputs=()=>({
 amend:loadJson("localization/governance/s0/source-amendments.json"),
 catalog:loadJson("experiments/lp0-200/catalog.json"),
 matrix:loadJson("localization/governance/lp0/source-matrix.json"),
 packs:{
  en:loadJson("experiments/lp0-200/packs/en.json"),
  "zh-TW":loadJson("experiments/lp0-200/packs/zh-TW.json"),
  ja:loadJson("experiments/lp0-200/packs/ja.json")
 },
 defect:loadJson("localization/governance/g4/source-defects.json"),
 ledger:loadJson("localization/governance/g4/review-ledger.json"),
 current:{
  en:resource("values"),"zh-TW":resource("values-b+zh+Hant+TW"),ja:resource("values-ja")
 }
});
export function verifyS0({amend,catalog,matrix,packs,defect,ledger,current}){
 assert.equal(amend.schema,"acx-s0-canonical-source-amendments/1");
 assert.equal(amend.baselineCatalogRevision,catalog.revision);
 assert.equal(amend.amendmentRevision,"s0-20261120");
 assert.equal(amend.items.length,1,"S0 only changes one canonical key");
 assert.equal(ledger.approvals.length,0,"S0 must not create human approvals");
 const s=amend.items[0];
 const c=catalog.entries.find(x=>x.key===key),m=matrix.records.find(x=>x.key===key);
 assert(c&&m&&s.key===key);
 assert.equal(s.canonicalFixState,"APPLIED_TO_ANDROID_RESOURCES");
 assert.equal(s.existingTranslations,"STALE_REVIEW_REQUIRED");
 assert.equal(s.humanApproved,false);
 assert.equal(s.baselineSource,c.source);
 assert.equal(s.baselineSource,m.source);
 assert.equal(s.baselineSource,packs.en.translations[key]);
 assert.equal(s.baselineSourceHash,shaSource(key,s.baselineSource));
 assert.equal(m.sourceHash,s.baselineSourceHash);
 assert.equal(s.currentSourceHash,shaSource(key,s.currentSource));
 assert.notEqual(s.currentSourceHash,s.baselineSourceHash,"SourceHash must rotate");
 assert.equal(current.en,s.currentSource);
 assert(!s.currentSource.includes("Room v27")&&!s.currentSource.includes("Room v28"));
 assert.deepEqual(core.placeholders(s.currentSource),c.placeholders);
 for(const locale of ["zh-TW","ja"]){
  const t=s.locales[locale];assert(t,"Missing S0 locale "+locale);
  assert.equal(t.baseline,packs[locale].translations[key]);
  assert.equal(t.baseline,m.translations[locale].text);
  assert.equal(t.originalBasedOnSourceHash,s.baselineSourceHash);
  assert.equal(m.translations[locale].basedOnSourceHash,s.baselineSourceHash);
  assert.equal(t.newCandidateBasedOnSourceHash,s.currentSourceHash);
  assert.equal(t.reviewState,"STALE_REVIEW_REQUIRED");
  assert.equal(t.humanApproved,false);
  assert.equal(current[locale],t.currentCandidate);
  assert.deepEqual(core.placeholders(t.currentCandidate),c.placeholders);
  assert(!t.currentCandidate.includes("Room v28"));
 }
 assert.equal(defect.schema,"acx-g4-source-defects/1");
 assert.equal(defect.catalogRevision,catalog.revision);
 const d=defect.items.find(x=>x.key===key);
 assert(d,"Defect entry missing");
 assert.equal(d.status,"OPEN","Do not self-resolve source review");
 assert.equal(d.releaseBlocking,true);
 assert.equal(d.humanApproved,false);
 assert.equal(d.sourceHash,s.baselineSourceHash);
 assert.equal(d.source,s.baselineSource);
 assert.equal(d.correction.amendmentRevision,amend.amendmentRevision);
 assert.equal(d.correction.canonicalEnglishFixed,true);
 assert.equal(d.correction.currentSource,s.currentSource);
 assert.equal(d.correction.currentSourceHash,s.currentSourceHash);
 assert.equal(d.correction.translationReviewStatus,"STALE_REVIEW_REQUIRED");
 assert.equal(d.correction.humanApproved,false);
 assert.equal(d.correction.baselineCatalogStillFrozen,true);
 for(const locale of ["zh-TW","ja"])
  assert.equal(d.correction.localeReviews[locale],"STALE_REVIEW_REQUIRED");
 return {fixed:true,sourceHashRotated:true,localeReviewsPending:2,humanApproved:0,releaseBlocked:true};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 console.log("S0_SOURCE_AMENDMENTS PASS "+JSON.stringify(verifyS0(inputs())));
