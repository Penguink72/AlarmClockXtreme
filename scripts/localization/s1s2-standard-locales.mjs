#!/usr/bin/env node
/**
 * S1+S2: ordinary EN / zh-TW / ja Android text-only update.
 *
 * This is a *technical* resource writer, not L12-6/9 protected human approval.
 * Translation candidates remain MACHINE_TRANSLATED_NOT_SEMANTICALLY_VERIFIED.
 * Existing hand-maintained locale XML and English source are never overwritten.
 */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {existsSync,readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {resolve,dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {loadUnifiedInputs} from "./l12-b6-independent-review.mjs";
import {buildTypedResourceStage} from "./l13-b-protected-xml-writer.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const SOURCE_STAGING_LABEL="<!-- MODEL UNREVIEWED. ISOLATED L13-B STAGING ONLY, NEVER COPY INTO APP. -->";
const STANDARD_LABEL="<!-- S2 machine-translated supplemental locale strings; TECHNICALLY CHECKED, NOT INDEPENDENTLY SEMANTICALLY REVIEWED. -->";
const LOCALES=["zh-TW","ja"];
const FOLDER={"zh-TW":"values-b+zh+Hant+TW",ja:"values-ja"};
const NAME="strings_s2_machine_translated.xml";
const HASH=data=>"sha256:"+createHash("sha256").update(data,"utf8").digest("hex");

const placeholderTokens=s=>{
  const matches=String(s).match(/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g)??[];
  return matches.filter(v=>!v.endsWith("%")).sort();
};
const eq=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
export function createStandardLocaleResources(packet,matrix){
  // Existing L13-B staging validates every candidate against canonical English,
  // its typed resource kind and the absence of existing locale entries.
  const stage=buildTypedResourceStage(packet,matrix);
  assert.equal(stage.manifest.rowsPerLocale,1550);
  const formatMismatches=[];
  for(const row of packet.rows){
    if(row.type==="string" && !eq(placeholderTokens(row.english),placeholderTokens(row.candidate)))
      formatMismatches.push(row.locale+" "+row.key);
  }
  // Refuse an unsafe build instead of inventing auto-approvals or truncating text.
  if(formatMismatches.length){
    throw Error("S2_ANDROID_FORMAT_ARGUMENT_DRIFT: "+formatMismatches.slice(0,15).join(", ")+
      " ("+formatMismatches.length+" total)");
  }
  const files={};
  for(const locale of LOCALES){
    const old=FOLDER[locale]+"/strings_l13_reviewed.xml";
    const next=FOLDER[locale]+"/"+NAME;
    const input=stage.files[old];
    if(typeof input!=="string"||!input.includes(SOURCE_STAGING_LABEL))
      throw Error("S2 stage provenance drift: "+old);
    const result=input.replace(SOURCE_STAGING_LABEL,STANDARD_LABEL);
    assert(result.includes(STANDARD_LABEL));
    assert(!result.includes("Reviewed through protected"));
    files[next]=result;
  }
  const manifest={
    schema:"acx-s2-standard-native-locale-assets/1",
    status:"TECHNICAL_TRANSLATION_CANDIDATE_NOT_SEMANTICALLY_VERIFIED",
    mode:"STANDARD_USER_OPT_IN_NO_G1_G4_OR_PROTECTED_SIGNERS",
    sourceSha:packet.sourceSha,
    catalogueRevision:packet.catalogueRevision,
    reviewSetDigest:packet.reviewSetDigest,
    originalEnglishChanged:false,
    existingLocalizedDefinitionsOverwritten:0,
    localeRowsPerLanguage:1550,
    translatedTotalRows:3100,
    byTypePerLocale:{string:1518,plurals:31,array:1},
    formatArgumentMismatches:0,
    independentlyApprovedLocaleRows:0,
    filesSha256:Object.fromEntries(Object.entries(files).map(([k,v])=>[k,HASH(v)])),
    caveat:"Machine translation is testable and usable; native Android resource/link and UI testing still required. No claim of human semantic approval."
  };
  return {files,manifest};
}

export function writeStandardLocaleResources({files,manifest},out,mode){
  if(!["stage","verify","materialize"].includes(mode))throw Error("S2_UNKNOWN_MODE");
  const resolved=resolve(out);
  const stageRoot=join(ROOT,"build")+"/";
  const mainRoot=join(ROOT,"app/src/main/res");
  if(mode==="stage"&&!resolved.startsWith(stageRoot))
    throw Error("S2 staging must be under build");
  if(mode!=="stage"&&resolved!==mainRoot)
    throw Error("S2 materialization/verification must target app/src/main/res");
  if(Object.keys(files).length!==2)throw Error("S2 requires two localized XML assets");
  for(const [rel,xml] of Object.entries(files)){
    if(!Object.values(FOLDER).some(folder=>rel===folder+"/"+NAME))
      throw Error("S2 disallowed resource target "+rel);
    const dest=join(resolved,rel);
    if(mode==="verify"){
      if(!existsSync(dest)||readFileSync(dest,"utf8")!==xml)
        throw Error("S2 committed translation resource drift: "+rel);
    }else if(mode==="materialize"&&existsSync(dest)){
      if(readFileSync(dest,"utf8")!==xml)
        throw Error("S2 refuses to silently overwrite a changed locale resource: "+rel);
    }else{
      mkdirSync(dirname(dest),{recursive:true});
      writeFileSync(dest,xml,"utf8");
    }
  }
  if(mode==="stage"){
    writeFileSync(join(resolved,"s2-resource-manifest.json"),
      JSON.stringify(manifest,null,2)+"\n","utf8");
  }
  return {status:mode==="verify"?"S2_COMMITTED_LOCALE_RESOURCES_MATCH":
    mode==="stage"?"S2_STAGED_FOR_TECHNICAL_VALIDATION":
    "S2_STANDARD_LOCALE_XML_MATERIALIZED_NOT_REVIEWED",
    localeFiles:Object.keys(files),manifest};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const arg=key=>{
    const i=process.argv.indexOf(key);
    return i<0?null:process.argv[i+1]??null;
  };
  const mode=arg("--mode")??"stage";
  if(mode==="materialize"&&!process.argv.includes("--user-approved-machine-translation"))
    throw Error("S1/S2 user opt-in required for materializing machine translation into app");
  let sha="UNAVAILABLE";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const {packet,matrix}=loadUnifiedInputs(ROOT,sha);
  const result=createStandardLocaleResources(packet,matrix);
  const output=arg("--out-dir")??(mode==="stage"?
    "build/localization/S2_STANDARD_MACHINE_TRANSLATED":"app/src/main/res");
  const status=writeStandardLocaleResources(result,resolve(ROOT,output),mode);
  console.log("S1_S2_STANDARD_LOCALES "+JSON.stringify({
    status:status.status,localeFiles:status.localeFiles,
    machineTranslatedRows:result.manifest.translatedTotalRows,
    humanApprovalRequired:false,
    humanSemanticReviewClaimed:false,
    sha256:result.manifest.filesSha256
  }));
}
