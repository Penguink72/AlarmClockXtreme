#!/usr/bin/env node
/**
 * B0-T1: deterministic, complete English / zh-TW / ja Android XML text matrix.
 * No generated translations, no source correction, no approval promotion.
 * The source hash binds key, resource type, translatability, raw Android XML
 * body and its declared lexical format/plural contract. Any change requires
 * a recomputed hash; whitespace can conservatively invalidate review.
 */
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { extractResourceDefinitions } from "./b0-resource-inventory.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const LANGUAGES=[["en","values"],["zh-TW","values-b+zh+Hant+TW"],["ja","values-ja"]];
const sha256=s=>createHash("sha256").update(s,"utf8").digest("hex");

export function extractRawResources(xml){
  const blank=s=>s.replace(/[^\n]/g," ");
  const masked=xml.replace(/<!--[\s\S]*?-->/g,blank)
    .replace(/<!\[CDATA\[[\s\S]*?\]\]>/g,blank);
  const pattern=/<(string-array|integer-array|plurals|array|string)\b([^>]*)>/g;
  const result=new Map();
  for(const m of masked.matchAll(pattern)){
    const named=m[2].match(/\bname\s*=\s*(['"])([^'"]+)\1/);
    if(!named)continue;
    const type=m[1].endsWith("-array")?"array":m[1];
    const key=type+":"+named[2];
    if(result.has(key))throw Error("Duplicate XML resource "+key);
    const after=m.index+m[0].length;
    const close=masked.indexOf("</"+m[1]+">",after);
    if(close<0 && !m[0].endsWith("/>"))throw Error("Unclosed "+key);
    result.set(key,{
      key,type,
      rawXml:m[0].endsWith("/>")?"":xml.slice(after,close),
      line:xml.slice(0,m.index).split("\n").length,
      translatable:!/\btranslatable\s*=\s*(['"])false\1/.test(m[2])
    });
  }
  return result;
}

export function readablePreview(raw){
  // Only an *approximate* human reading aid. rawXml is the true source;
  // Android escape semantics, spans and plural categories stay in rawXml.
  return raw.replace(/<item\s+quantity="([^"]+)"[^>]*>/g," [$1] ")
    .replace(/<item[^>]*>/g," | ")
    .replace(/<\/?[^>]*>/g,"")
    .replace(/&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);/g,(_,entity)=>{
      const known={amp:"&",lt:"<",gt:">",quot:'"',apos:"'"};
      if(known[entity]!==undefined)return known[entity];
      const n=entity.startsWith("#x")?parseInt(entity.slice(2),16):parseInt(entity.slice(1),10);
      return n<=0x10ffff?String.fromCodePoint(n):"�";
    })
    .replace(/\s+/g," ").trim();
}

export function canonicalSourceHash(row){
  const contract=[row.key,row.type,row.translatable,row.rawXml,
    row.formatTokens,row.pluralQuantities];
  return "sha256:"+sha256(JSON.stringify(contract));
}
const escapeCell=value=>JSON.stringify(value===undefined||value===null?"":value);
const present=(record)=>record?"DEFINED_IN_XML":"NO_LOCALE_DEFINITION";

export function generateMatrix(root=ROOT){
  const resources=LANGUAGES.map(([locale,qualifier])=>{
    const path=`app/src/main/res/${qualifier}/strings.xml`;
    const xml=readFileSync(join(root,path),"utf8");
    const raw=extractRawResources(xml),defs=extractResourceDefinitions(xml);
    if(raw.size!==defs.length)throw Error("Raw/metadata XML mismatch "+locale);
    const map=new Map(defs.map(entry=>{
      const value=raw.get(entry.key);
      if(!value)throw Error("Missing raw XML "+entry.key);
      return [entry.key,{...value,...entry,rawXml:value.rawXml,
        sourcePath:path}];
    }));
    return {locale,qualifier,path,map};
  });
  const base=resources[0].map,zh=resources[1].map,ja=resources[2].map;
  for(const loc of resources.slice(1)){
    for(const key of loc.map.keys()){
      if(!base.has(key))throw Error("Locale-specific orphaned resource "+loc.locale+" "+key);
    }
  }
  const rows=[...base.values()].sort((a,b)=>a.key.localeCompare(b.key,"en")).map(en=>{
    const targetZh=zh.get(en.key),targetJa=ja.get(en.key);
    const sourceHash=canonicalSourceHash(en);
    const target=(x)=>x?{rawXml:x.rawXml,preview:readablePreview(x.rawXml),
      status:"EXISTING_XML_NOT_INDEPENDENTLY_APPROVED",
      translationHash:"sha256:"+sha256(JSON.stringify([x.key,x.type,x.rawXml]))}
      :{rawXml:null,preview:null,status:en.translatable?
        "MISSING_LOCALE_DEFINITION":"BASE_NOT_TRANSLATABLE",translationHash:null};
    return {
      key:en.key,type:en.type,translatable:en.translatable,
      sourceXml:en.rawXml,sourcePreview:readablePreview(en.rawXml),
      sourceHash,sourcePath:en.sourcePath,sourceLine:en.line,
      formatTokens:en.formatTokens,pluralQuantities:en.pluralQuantities,
      hasRichMarkup:en.hasRichMarkup,
      zhTw:target(targetZh),ja:target(targetJa),
      zhTwLine:targetZh?.line??null,jaLine:targetJa?.line??null,
      semanticApproval:"NOT_ASSESSED_BY_B0_T1"
    };
  });
  const catalogueRevision="sha256:"+sha256(JSON.stringify(rows.map(x=>[x.key,x.sourceHash])));
  const counts={
    baseKeys:rows.length,
    translatable:rows.filter(x=>x.translatable).length,
    zhDefined:rows.filter(x=>x.zhTw.rawXml!==null).length,
    jaDefined:rows.filter(x=>x.ja.rawXml!==null).length,
    missingZh:rows.filter(x=>x.translatable&&x.zhTw.rawXml===null).length,
    missingJa:rows.filter(x=>x.translatable&&x.ja.rawXml===null).length,
    sourceWithFormats:rows.filter(x=>x.formatTokens.length>0).length,
    sourcePlurals:rows.filter(x=>x.type==="plurals").length
  };
  return {schema:"acx-b0-trilingual-canonical-matrix/1",catalogueRevision,
    authority:resources.map(x=>x.path),counts,rows};
}

export function matrixTsv(matrix){
  const columns=[
    "key","type","translatable","source_xml","source_preview","source_hash",
    "source_path","source_line","format_tokens","plural_quantities",
    "rich_markup","zh_TW_status","zh_TW_xml","zh_TW_preview","zh_TW_hash","zh_TW_line",
    "ja_status","ja_xml","ja_preview","ja_hash","ja_line","review_state"
  ];
  const entries=matrix.rows.map(row=>[
    row.key,row.type,row.translatable,row.sourceXml,row.sourcePreview,row.sourceHash,
    row.sourcePath,row.sourceLine,row.formatTokens,row.pluralQuantities,
    row.hasRichMarkup,row.zhTw.status,row.zhTw.rawXml,row.zhTw.preview,row.zhTw.translationHash,row.zhTwLine,
    row.ja.status,row.ja.rawXml,row.ja.preview,row.ja.translationHash,row.jaLine,
    row.semanticApproval
  ].map(escapeCell).join("\t"));
  // Single JSON-quoted cell per TSV field preserves literal tabs/newlines,
  // quotes, rich markup, and plural <item> XML without silent truncation.
  return columns.join("\t")+"\n"+entries.join("\n")+"\n";
}

export function matrixSummary(matrix){
  return `# B0-T1｜完整英／繁中／日文逐 Key 對照（source-only）

> Generated from exact main Android XML. This is not independent translation approval, nor evidence that all strings appear in a running UI.

- Base catalog: ${matrix.counts.baseKeys} keys; translatable: ${matrix.counts.translatable}.
- Existing zh-TW definitions: ${matrix.counts.zhDefined}; ja: ${matrix.counts.jaDefined}.
- Missing locale definitions among translatable: zh-TW ${matrix.counts.missingZh}, ja ${matrix.counts.missingJa}.
- Source with lexical printf tokens: ${matrix.counts.sourceWithFormats}; plural keys: ${matrix.counts.sourcePlurals}.
- Current catalog revision: \`${matrix.catalogueRevision}\`.
- Download/edit the generated TSV for human reading or review. Cells are JSON-quoted strings; raw XML remains intact, preview is approximate. A missing translation is null, not an invented English copy.
- source_hash is sha256(JSON.stringify([key,type,translatable,rawXml,formatTokens,pluralQuantities])); target hashes are separate and never imply approval.
- This output is reproducible with \`node scripts/localization/b0-t1-canonical-matrix.mjs --verify\`.
`;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const m=generateMatrix();
  const tsv=matrixTsv(m);
  const source=join(ROOT,"localization/catalog/b0-t1-trilingual-source-matrix.tsv");
  const verify=process.argv.includes("--verify");
  if(verify){
    if(!existsSync(source)||readFileSync(source,"utf8")!==tsv)
      throw Error("Committed trilingual matrix drift: regenerate and review before merging");
  }
  const at=process.argv.indexOf("--out-dir");
  if(at>=0){
    const out=resolve(process.argv[at+1]??"");
    if(!out.startsWith(ROOT+(process.platform==="win32"?"\\":"/")))
      throw Error("Output directory must be inside repository");
    mkdirSync(out,{recursive:true});
    writeFileSync(join(out,"b0-t1-trilingual-source-matrix.tsv"),tsv);
    writeFileSync(join(out,"b0-t1-trilingual-source-matrix.json"),JSON.stringify(m,null,2)+"\n");
    writeFileSync(join(out,"b0-t1-matrix-summary.md"),matrixSummary(m));
  }
  console.log("B0_T1_CANONICAL_MATRIX "+JSON.stringify({...m.counts,revision:m.catalogueRevision,checked:verify}));
}
