#!/usr/bin/env node
/** L11-10: type-safe, source-pinned *unapproved* translations for remaining
 * string, plurals and string-array resources. Isolated staging ONLY.
 * Never mutates app/src/main/res and never grants G1/G4/release authority.
 */
import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildResidualClosure} from "./l11-b10-residual-closure.mjs";
import {androidXmlPreviewText} from "./l12-b3-android-resource-preflight.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const LOCALES=["zh-TW","ja"];
const token=/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g;
const eq=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function tokens(s){return (s.match(token)||[]).sort()}
function items(xml){return [...xml.matchAll(/<item\b([^>]*)>([\s\S]*?)<\/item>/g)].map(m=>({
  quantity:m[1].match(/\bquantity\s*=\s*(['"])(\w+)\1/)?.[2]??null,
  value:m[2].trim()
}));}
export function validateB10({residual,matrix,packs}){
  assert.equal(residual.schema,"acx-l11-b10-residual-source-queue/1");
  assert.equal(matrix.catalogueRevision,residual.catalogueRevision);
  for(const p of packs){
    if(p.independentApproval!==false || p.status!=="MODEL_PRELIMINARY_NOT_APPROVED")
      throw Error("L11-10 refuses self-approved candidate source");
  }
  if(packs.length!==3)throw Error("L11-10 expects string/plural/array packs");
  const sources=new Map(matrix.rows.map(x=>[x.key,x]));
  const need=new Set(residual.rows.map(x=>x.key+"\0"+x.locale));
  const result=[],used=new Set(),byType={string:0,plurals:0,array:0};
  for(const pack of packs)for(const r of pack.records){
    if(used.has(r.key))throw Error("Duplicate L11-10 key "+r.key);
    used.add(r.key);
    const source=sources.get(r.key);
    if(!source || !source.translatable)throw Error("Nontranslatable or missing source "+r.key);
    if(source.sourcePreview!==r.expectedEnglishPreview || source.sourceXml!==r.expectedSourceXml)
      throw Error("L11-10 source revision drift "+r.key);
    const type=r.key.split(":")[0];
    if(!["string","plurals","array"].includes(type)||source.type!==type)
      throw Error("L11-10 resource type mismatch "+r.key);
    if((type==="string"&&pack.schema!=="acx-l11-batch10-string-drafts/1")||
      (type==="plurals"&&pack.schema!=="acx-l11-batch10-plural-drafts/1")||
      (type==="array"&&pack.schema!=="acx-l11-batch10-array-drafts/1"))
      throw Error("L11-10 pack schema/type mismatch");
    const sourceItems=type==="string"?[]:items(r.expectedSourceXml);
    if(type==="array" && !eq(sourceItems.map(x=>x.value),r.expectedEnglishItems))
      throw Error("L11-10 array source items drift "+r.key);
    for(const locale of LOCALES){
      if(!need.has(r.key+"\0"+locale))throw Error("L11-10 would overwrite protected definition "+r.key+"/"+locale);
      const candidate=r.translations[locale];
      if(type==="string"){
        if(typeof candidate!=="string"||!candidate.trim()||!eq(tokens(source.sourceXml),tokens(candidate)))
          throw Error("L11-10 string format mismatch "+r.key+"/"+locale);
      }else if(type==="plurals"){
        if(!candidate||Array.isArray(candidate)||typeof candidate!=="object")throw Error("L11-10 malformed plurals");
        const cats=sourceItems.map(x=>x.quantity);
        if(!cats.every(Boolean)||new Set(cats).size!==cats.length||
          !eq(Object.keys(candidate).sort(),cats.slice().sort()))
          throw Error("L11-10 plural quantity mismatch "+r.key+"/"+locale);
        for(const s of sourceItems){
          if(typeof candidate[s.quantity]!=="string"||!candidate[s.quantity].trim()||
            !eq(tokens(s.value),tokens(candidate[s.quantity])))
            throw Error("L11-10 plural branch format mismatch "+r.key+"/"+locale+"/"+s.quantity);
        }
      }else{
        if(!Array.isArray(candidate)||candidate.length!==sourceItems.length)
          throw Error("L11-10 array length mismatch "+r.key+"/"+locale);
        for(let i=0;i<sourceItems.length;i++){
          if(typeof candidate[i]!=="string"||!candidate[i].trim()||
            !eq(tokens(sourceItems[i].value),tokens(candidate[i])))
            throw Error("L11-10 array item mismatch "+r.key+"/"+locale+"/"+i);
        }
      }
      result.push({key:r.key,locale,type,sourceHash:source.sourceHash,
        sourcePreview:source.sourcePreview,expectedSourceXml:r.expectedSourceXml,
        candidate,reviewRisk:r.reviewRisk,independentApproval:false,mayWriteOfficialXml:false});
      need.delete(r.key+"\0"+locale);
      byType[type]++;
    }
  }
  if(need.size)throw Error("L11-10 still missing locale rows: "+[...need].slice(0,6).join(", "));
  return {schema:"acx-l11-b10-complete-candidate-evidence/1",
    status:"MODEL_DRAFTS_COMPLETE_NOT_RELEASE_APPROVED",
    sourceSha:residual.sourceSha,catalogueRevision:residual.catalogueRevision,
    stats:{residualLocaleRows:result.length,byType,remainingUnfilled:need.size,independentApproved:0,
      officialResourcesWritten:0},records:result};
}
export function stageB10(data){
  assert.equal(data.schema,"acx-l11-b10-complete-candidate-evidence/1");
  const files={};
  for(const locale of LOCALES){
    const rows=data.records.filter(r=>r.locale===locale).sort((a,b)=>a.key.localeCompare(b.key,"en"));
    const render=r=>{
      const name=r.key.slice(r.key.indexOf(":")+1);
      if(r.type==="string")return '  <string name="'+name+'">'+androidXmlPreviewText(r.candidate)+'</string>';
      if(r.type==="plurals"){
        return '  <plurals name="'+name+'">\n'+Object.entries(r.candidate).map(([q,s])=>
          '    <item quantity="'+q+'">'+androidXmlPreviewText(s)+'</item>').join("\n")+
          '\n  </plurals>';
      }
      return '  <string-array name="'+name+'">\n'+r.candidate.map(s=>
        '    <item>'+androidXmlPreviewText(s)+'</item>').join("\n")+'\n  </string-array>';
    };
    files[(locale==="zh-TW"?"values-b+zh+Hant+TW":"values-ja")+"/strings.xml"]=
      '<?xml version="1.0" encoding="utf-8"?>\n'+
      '<!-- L11-10 MODEL-UNREVIEWED. ISOLATED STAGING, NEVER PRODUCTION. -->\n'+
      '<resources>\n'+rows.map(render).join("\n")+'\n</resources>\n';
  }
  return files;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim()}catch{}
  const inputs=loadL11Inputs(ROOT,sha),l11=buildL11(inputs),residual=buildResidualClosure(l11,inputs.graph),
    matrix=generateMatrix(ROOT),
    packs=["strings","plural","array"].map(kind=>JSON.parse(readFileSync(join(ROOT,
      "localization/candidates/l11-b10-"+kind+"-drafts-20261122.json"),"utf8"))),
    data=validateB10({residual,matrix,packs}),files=stageB10(data);
  const at=process.argv.indexOf("--out-dir");
  if(at>=0){
    const dest=resolve(process.argv[at+1]??"");
    if(!dest.startsWith(join(ROOT,"build")+"/"))throw Error("Output must stay under build/");
    mkdirSync(dest,{recursive:true});
    writeFileSync(join(dest,"l11-b10-review-evidence.json"),JSON.stringify(data,null,2)+"\n");
    for(const [path,content] of Object.entries(files)){
      const full=join(dest,"res",path);mkdirSync(dirname(full),{recursive:true});
      writeFileSync(full,content);
    }
  }
  console.log("L11_B10_TYPED_CANDIDATES "+JSON.stringify({sourceSha:sha,...data.stats,
    totalModelDraftRows:l11.review.length+data.records.length,totalMissing:residual.stats.totalMissingDedicatedDefinitions}));
}
