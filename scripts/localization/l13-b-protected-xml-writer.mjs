#!/usr/bin/env node
/**
 * L13-B typed resource staging + protected-only production writer.
 * The default is unapproved build/ staging. Never treat L11/L12 model content,
 * a source hash, format passing or aapt2 passing as an official approval.
 */
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {existsSync,readFileSync,mkdirSync,writeFileSync,renameSync} from "node:fs";
import {dirname,join,resolve,isAbsolute} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {androidXmlPreviewText} from "./l12-b3-android-resource-preflight.mjs";
import {loadUnifiedInputs,validateSignedReviewIntake} from "./l12-b6-independent-review.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const HASH=s=>"sha256:"+createHash("sha256").update(s,"utf8").digest("hex");
const LOCALES=["zh-TW","ja"];
const QU={"zh-TW":"values-b+zh+Hant+TW",ja:"values-ja"};
const TYPE_COUNTS={string:1518,plurals:31,array:1};
const RESOURCE_FILE="strings_l13_reviewed.xml";

function safeName(key){
  const name=key.slice(key.indexOf(":")+1);
  if(!/^[a-z][a-z0-9_]*$/.test(name))throw Error("ANDROID_RESOURCE_NAME_INVALID "+key);
  return name;
}
function escape(s){
  if(typeof s!=="string"||!s.trim())throw Error("Empty locale string");
  return androidXmlPreviewText(s);
}
function render(row){
  const name=safeName(row.key);
  if(row.type==="string"){
    if(typeof row.candidate!=="string")throw Error("Invalid string candidate");
    return '  <string name="'+name+'">'+escape(row.candidate)+'</string>';
  }
  if(row.type==="plurals"){
    const q=row.candidate;
    if(!q || Array.isArray(q)||typeof q!=="object" ||
       JSON.stringify(Object.keys(q).sort())!==JSON.stringify(["one","other"]))
      throw Error("Plural quantity structure drift "+row.key);
    return '  <plurals name="'+name+'">\n'+
      ["one","other"].map(cat=>'    <item quantity="'+cat+'">'+escape(q[cat])+'</item>').join("\n")+
      '\n  </plurals>';
  }
  if(row.type==="array"){
    if(!Array.isArray(row.candidate)||row.candidate.length!==15)
      throw Error("Array item count drift "+row.key);
    return '  <string-array name="'+name+'">\n'+
      row.candidate.map(v=>'    <item>'+escape(v)+'</item>').join("\n")+
      '\n  </string-array>';
  }
  throw Error("Unsupported resource type "+row.type);
}
export function buildTypedResourceStage(packet,matrix){
  assert.equal(packet.schema,"acx-l12-b6-independent-review-packet/1");
  assert.equal(matrix.schema,"acx-b0-trilingual-canonical-matrix/1");
  assert.equal(packet.catalogueRevision,matrix.catalogueRevision);
  const source=new Map(matrix.rows.map(r=>[r.key,r]));
  if(packet.rows.length!==3100)throw Error("Expected 3100 independently tracked candidate rows");
  const byLocale={"zh-TW":[],"ja":[]},seen=new Set();
  for(const row of packet.rows){
    if(!LOCALES.includes(row.locale))throw Error("Unsupported locale "+row.locale);
    const id=row.key+"\0"+row.locale;
    if(seen.has(id))throw Error("Duplicate locale row "+id);
    seen.add(id);
    const m=source.get(row.key);
    if(!m || !m.translatable||m.type!==row.type||
       m.sourceHash!==row.sourceHash||m.sourceXml!==row.sourceXml)
      throw Error("L13-B source or resource kind drift "+id);
    if((row.locale==="zh-TW"?m.zhTw:m.ja).rawXml!==null)
      throw Error("L13-B would duplicate existing Android resource "+id);
    if(row.independentApproved!==false)throw Error("Model staging cannot claim approval "+id);
    byLocale[row.locale].push(row);
  }
  const files={};
  for(const locale of LOCALES){
    const rows=byLocale[locale].sort((a,b)=>a.key.localeCompare(b.key,"en"));
    if(rows.length!==1550)throw Error("Locale row count drift "+locale);
    const byType=Object.fromEntries(["string","plurals","array"].map(t=>
      [t,rows.filter(r=>r.type===t).length]));
    if(JSON.stringify(byType)!==JSON.stringify(TYPE_COUNTS))
      throw Error("Typed resources count drift "+locale);
    files[QU[locale]+"/"+RESOURCE_FILE]=
      '<?xml version="1.0" encoding="utf-8"?>\n'+
      '<!-- MODEL UNREVIEWED. ISOLATED L13-B STAGING ONLY, NEVER COPY INTO APP. -->\n'+
      '<resources>\n'+rows.map(render).join("\n")+'\n</resources>\n';
  }
  const manifest={
    schema:"acx-l13-b-resource-stage/1",sourceSha:packet.sourceSha,
    catalogueRevision:packet.catalogueRevision,reviewSetDigest:packet.reviewSetDigest,
    status:"ISOLATED_UNAPPROVED_TYPE_SAFE_STAGE_NOT_A_RELEASE",
    rowsPerLocale:1550,independentApproved:0,
    existingLocaleDefinitionsOverwritten:0,
    stagedFilesSha256:Object.fromEntries(Object.entries(files).map(([k,v])=>[k,HASH(v)]))
  };
  return {files,manifest};
}
export function releaseViolations({stage,packet,envelope,trustedKeyPem,glossary,g4Ledger,
  compilerAttestation,protectedRef}){
  const errors=[];
  if(protectedRef!==true)errors.push("GITHUB_PROTECTED_REF_REQUIRED");
  const review=validateSignedReviewIntake({packet,envelope,trustedPublicKeyPem:trustedKeyPem,
    glossary,g4Ledger});
  if(review.status!=="INDEPENDENT_REVIEW_EVIDENCE_VALID")
    errors.push(...review.errors);
  const proof=compilerAttestation;
  if(!proof || proof.schema!=="acx-l13-b-protected-compiler-evidence/1" ||
     proof.status!=="PASSED" ||
     proof.sourceSha!==packet.sourceSha||
     proof.reviewSetDigest!==packet.reviewSetDigest ||
     JSON.stringify(proof.stagedFilesSha256)!==JSON.stringify(stage.manifest.stagedFilesSha256)||
     !/^sha256:[0-9a-f]{64}$/.test(proof.compiledResourceZipSha256??""))
    errors.push("L13_B_AAPT2_EVIDENCE_MISSING_OR_STALE");
  return [...new Set(errors)];
}
/**
 * Only call AFTER protected release gates. A separate reviewed Git operation
 * must commit the generated official assets; no normal CI invokes this path.
 */
export function writeAuthorizedXml({stage,packet,envelope,trustedKeyPem,glossary,
  g4Ledger,compilerAttestation,protectedRef,repoRoot=ROOT}){
  const errors=releaseViolations({stage,packet,envelope,trustedKeyPem,glossary,
    g4Ledger,compilerAttestation,protectedRef});
  if(errors.length)throw Error("L13-B official writer BLOCKED: "+errors.join(", "));
  const paths=Object.keys(stage.files);
  const prepared=paths.map(rel=>{
    const target=join(repoRoot,"app/src/main/res",rel);
    if(existsSync(target))throw Error("L13-B refuses to overwrite an existing XML file: "+rel);
    return {rel,target,data:stage.files[rel].replace(
      "<!-- MODEL UNREVIEWED. ISOLATED L13-B STAGING ONLY, NEVER COPY INTO APP. -->",
      "<!-- Reviewed through protected L12-6 authorizations and L13-B source validation. -->")};
  });
  // Store only on a protected, externally reviewed branch. Never publish to main automatically.
  for(const f of prepared){
    mkdirSync(dirname(f.target),{recursive:true});
    const temp=f.target+".l13-tmp";
    if(existsSync(temp))throw Error("Existing incomplete XML temp file");
    writeFileSync(temp,f.data,{flag:"wx"});
    renameSync(temp,f.target);
  }
  return {status:"PROTECTED_WRITER_OUTPUT_CREATED_REQUIRES_FULL_GRADLE_AND_RUNTIME_REVIEW",
    files:prepared.map(x=>x.rel),digest:packet.reviewSetDigest};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const {packet,matrix,glossary,g4Ledger}=loadUnifiedInputs(ROOT,sha),
    stage=buildTypedResourceStage(packet,matrix);
  const mode=process.argv.includes("--mode")?
    process.argv[process.argv.indexOf("--mode")+1]:"staging";
  if(mode!=="staging"&&mode!=="release")throw Error("Unknown L13-B mode");
  if(mode==="staging"){
    const idx=process.argv.indexOf("--out-dir"),
      dest=idx>=0?resolve(process.argv[idx+1]??""):
        join(ROOT,"build/localization/L13_B_ISOLATED_UNAPPROVED");
    if(!dest.startsWith(join(ROOT,"build")+"/"))
      throw Error("L13-B staging must remain under build/");
    mkdirSync(dest,{recursive:true});
    for(const [path,value] of Object.entries(stage.files)){
      const file=join(dest,"res",path);
      mkdirSync(dirname(file),{recursive:true});
      writeFileSync(file,value);
    }
    writeFileSync(join(dest,"stage-manifest.json"),JSON.stringify(stage.manifest,null,2)+"\n");
    console.log("L13_B_ISOLATED_STAGE "+JSON.stringify(stage.manifest));
  }else{
    const keyPath=process.env.ACX_L12_PROTECTED_REVIEW_PUBLIC_KEY_FILE??"";
    if(!isAbsolute(keyPath)||keyPath.startsWith(ROOT+"/")||keyPath.startsWith(ROOT+"\\"))
      throw Error("L13-B official writer BLOCKED: EXTERNAL_TRUST_ANCHOR_REQUIRED");
    const flag=k=>{const i=process.argv.indexOf(k);return i<0?null:resolve(process.argv[i+1]??"")};
    const reviewFile=flag("--review-envelope"),compileFile=flag("--compiled-attestation");
    if(!reviewFile||!compileFile)throw Error("L13-B official writer BLOCKED: REVIEW_AND_AAPT2_EVIDENCE_REQUIRED");
    const parse=p=>JSON.parse(readFileSync(p,"utf8"));
    const result=writeAuthorizedXml({stage,packet,envelope:parse(reviewFile),
      trustedKeyPem:readFileSync(keyPath,"utf8"),glossary,g4Ledger,
      compilerAttestation:parse(compileFile),
      protectedRef:process.env.GITHUB_REF_PROTECTED==="true"});
    console.log("L13_B_PROTECTED_XML_ONLY "+JSON.stringify(result));
  }
}
