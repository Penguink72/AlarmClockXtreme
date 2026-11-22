#!/usr/bin/env node
/*
 * L10: deterministic, read-only Source -> Context -> Binding -> Surface index.
 * Source authority comes from B0-T1 XML, not translated previews. References
 * are lexical candidates; no runtime visibility, action semantics or approval
 * is inferred from filenames/regex matches. Node.js only.
 */
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, extname, join, relative, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { buildInventory } from "./b0-resource-inventory.mjs";
import { generateMatrix } from "./b0-t1-canonical-matrix.mjs";
import { classifyLiteral } from "./b0-ui-and-unused-triage.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const REF = /\bR\s*\.\s*(string|plurals|array)\s*\.\s*([A-Za-z_][A-Za-z_0-9]*)\b|@(string|plurals|array)\/([A-Za-z_][A-Za-z_0-9]*)\b/g;
const sha = value => "sha256:" + createHash("sha256").update(value, "utf8").digest("hex");
const unix = path => path.replaceAll("\\", "/");
const sorted = values => values.slice().sort((a,b)=>a.localeCompare(b,"en"));

function walk(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir,{withFileTypes:true})
    .sort((a,b)=>a.name.localeCompare(b.name,"en"))
    .flatMap(entry => entry.isDirectory() ? walk(join(dir,entry.name))
      : entry.isFile() ? [join(dir,entry.name)] : []);
}

/** Masks Kotlin/Java comments, strings and chars, preserving code offsets. */
export function maskNonCode(text) {
  let result = "", state = "code";
  const put = c => c === "\n" || c === "\r" ? c : " ";
  for (let i=0; i<text.length;) {
    const a = text[i], b = text[i+1], three = text.slice(i,i+3);
    if (state === "code") {
      if (a === "/" && b === "/") { result += "  "; i+=2; state="line"; continue; }
      if (a === "/" && b === "*") { result += "  "; i+=2; state="block"; continue; }
      if (three === '"""') { result += "   "; i+=3; state="triple"; continue; }
      if (a === '"') { result += " "; i++; state="double"; continue; }
      if (a === "'") { result += " "; i++; state="single"; continue; }
      result += a; i++; continue;
    }
    if (state === "line") {
      result += put(a); i++;
      if (a === "\n") state="code";
      continue;
    }
    if (state === "block") {
      if (a === "*" && b === "/") { result += "  "; i+=2; state="code"; }
      else { result += put(a); i++; }
      continue;
    }
    if (state === "triple") {
      if (three === '"""') { result += "   "; i+=3; state="code"; }
      else { result += put(a); i++; }
      continue;
    }
    if (state === "double" || state === "single") {
      const quote = state === "double" ? '"' : "'";
      if (a === "\\" && i+1<text.length) {
        result += put(a) + put(b); i+=2;
      } else if (a === quote) {
        result += " "; i++; state="code";
      } else { result += put(a); i++; }
    }
  }
  return result;
}

export function surfaceFromPath(path) {
  if (path.startsWith("wear/")) return "WEAR_CANDIDATE";
  if (/\/(?:service|services)\//i.test(path)) return "SERVICE_CANDIDATE";
  if (/\/(?:receiver|receivers)\//i.test(path)) return "RECEIVER_CANDIDATE";
  if (/\/(?:worker|workers)\//i.test(path)) return "WORKER_CANDIDATE";
  if (/\/(?:widget|widgets)\//i.test(path)) return "WIDGET_CANDIDATE";
  if (/\/ui\/onboarding\//i.test(path)) return "ONBOARDING_UI_CANDIDATE";
  if (/\/ui\/settings\//i.test(path)) return "SETTINGS_UI_CANDIDATE";
  if (/\/ui\/alarmfiring\//i.test(path)) return "ALARM_FIRING_UI_CANDIDATE";
  if (/\/ui\/alarmedit\//i.test(path)) return "ALARM_EDIT_UI_CANDIDATE";
  if (/\/ui\/ringtone\//i.test(path)) return "MUSIC_UI_CANDIDATE";
  if (/\/ui\/stats\//i.test(path)) return "STATS_UI_CANDIDATE";
  if (/\/ui\/navigation\//i.test(path)) return "NAVIGATION_UI_CANDIDATE";
  if (/\/ui\//i.test(path)) return "OTHER_UI_CANDIDATE";
  if (/\/res\/layout\//.test(path)) return "ANDROID_XML_LAYOUT_CANDIDATE";
  return "OTHER_SOURCE_CANDIDATE";
}

function nearestSymbol(lines, n) {
  for (let i=n; i>=Math.max(0,n-90); i--) {
    const found = lines[i].match(/\b(?:fun|class|interface|object)\s+([A-Za-z_][\w]*)/);
    if (found) return found[1];
  }
  return "UNRESOLVED_SYMBOL";
}

function textApi(line, window, ext) {
  if (ext === ".xml") return "ANDROID_XML_RESOURCE_CANDIDATE";
  if (/\b(?:getQuantityString|pluralStringResource|getQuantityText)\s*\(/.test(line))
    return "QUANTITY_RESOURCE_API_CANDIDATE";
  if (/\b(?:getString|stringResource|getText)\s*\(/.test(line))
    return "DIRECT_RESOURCE_API_CANDIDATE";
  if (/(?:\b(?:titleRes|descriptionRes|labelRes|textForResource|resId|resourceId|iconTextRes)\b|\b\w+Res\b)/.test(line))
    return "INDIRECT_ID_CANDIDATE";
  if (/\b(?:getIdentifier|Class\.forName)\s*\(/.test(line))
    return "DYNAMIC_ID_NEEDS_REVIEW";
  if (/\b(?:getQuantityString|pluralStringResource|getQuantityText|getString|stringResource)\s*\(/.test(window))
    return "MULTILINE_RESOURCE_API_CANDIDATE";
  return "STATIC_RESOURCE_REFERENCE_ONLY";
}

/** Collects every lexical resource use, not only the first 20 from B0. */
export function scanBindings(root) {
  const paths=["app/src/main","app/src/debug","wear/src/main"]
    .flatMap(d=>walk(join(root,d)))
    .filter(p=>[".kt",".java",".xml"].includes(extname(p)) &&
      !/\/res\/values[^/]*\//.test(unix(p)));
  const bindings=[], unresolved=[], files=[];
  const fingerprints=new Map();
  for (const file of paths) {
    const path=unix(relative(root,file)),ext=extname(file);
    const source=readFileSync(file,"utf8");
    const masked=ext===".xml"
      ? source.replace(/<!--[\s\S]*?-->/g,m=>m.replace(/[^\n\r]/g," "))
      : maskNonCode(source);
    const srcLines=source.split(/\r?\n/), codeLines=masked.split(/\r?\n/);
    if (srcLines.length!==codeLines.length) throw Error("Line masking drift: "+path);
    const fileRevision=sha(source);
    files.push({path,fileRevision});
    for (let n=0;n<codeLines.length;n++) {
      const raw=srcLines[n],code=codeLines[n];
      const localWindow=codeLines.slice(Math.max(0,n-2),Math.min(codeLines.length,n+2)).join(" ");
      for (const hit of code.matchAll(REF)) {
        const type=hit[1]||hit[3], name=hit[2]||hit[4];
        const key=type+":"+name, symbol=nearestSymbol(codeLines,n);
        const api=textApi(code,localWindow,ext);
        const fingerprint=sha(JSON.stringify([path,symbol,key,raw.trim()]));
        const duplicate=fingerprints.get(fingerprint)||0;
        fingerprints.set(fingerprint,duplicate+1);
        const bindingId=sha(JSON.stringify([fingerprint,duplicate]));
        bindings.push({
          bindingId,key,path,line:n+1,symbolHint:symbol,
          fileRevision,bindingRevision:fingerprint,
          surfaceHint:surfaceFromPath(path),surfaceEvidence:"PATH_HEURISTIC_NOT_RUNTIME_VERIFIED",
          textApiHint:api,referenceSyntax:hit[0],referenceKind:"LEXICAL_STATIC_CANDIDATE",
          contextHint:raw.trim().slice(0,240),
          actionSemantic:"UNVERIFIED",contextRevision:null,
          runtimeWiring:"NOT_VERIFIED",semanticApproval:"NOT_INFERRED"
        });
      }
      // A hint cannot be resolved to a specific Key without data-flow proof.
      if (/\b(?:getIdentifier|Class\.forName|getTextArray|textForResource)\s*\(/.test(code)) {
        unresolved.push({
          path,line:n+1,fileRevision,surfaceHint:surfaceFromPath(path),
          kind:"INDIRECT_OR_DYNAMIC_CALLSITE_HINT",
          reason:"Unresolved call graph and/or runtime resource-ID binding",
          snippet:raw.trim().slice(0,200)
        });
      }
    }
  }
  bindings.sort((a,b)=>a.key.localeCompare(b.key,"en")||
    a.path.localeCompare(b.path,"en")||a.line-b.line||a.bindingId.localeCompare(b.bindingId,"en"));
  unresolved.sort((a,b)=>a.path.localeCompare(b.path,"en")||a.line-b.line);
  return {files,bindings,unresolved};
}

const riskForKey = key => {
  const text=key.toLowerCase();
  if (/snooze|dismiss|delete|remove|revoke|permission|privacy|webhook|schedule/.test(text))
    return "OPERATION_SEMANTIC_REVIEW_CANDIDATE";
  if (/notif|alarm_firing|ringing|history/.test(text))
    return "RUNTIME_CONTEXT_REVIEW_CANDIDATE";
  return "NORMAL_OR_UNCLASSIFIED";
};

/** Pure join of existing B0/B0-T1 inputs and L10 lexical bindings. */
export function assembleL10(matrix,inventory,scan,sourceSha="unavailable") {
  if (matrix.rows.length!==inventory.sourceKeys.length)
    throw Error("B0/B0-T1 source counts disagree");
  const inv=new Map(inventory.sourceKeys.map(x=>[x.key,x]));
  const rows=new Map(matrix.rows.map(x=>[x.key,x]));
  if(inv.size!==rows.size)throw Error("Source key identity mismatch");
  const use=new Map([...rows.keys()].map(key=>[key,[]]));
  const nonBase=[];
  for(const b of scan.bindings){
    if(use.has(b.key)) use.get(b.key).push(b);
    else nonBase.push(b);
  }
  const sources=[],localeGaps=[],unknown=[];
  for (const row of matrix.rows){
    const base=inv.get(row.key);
    if(!base || base.type!==row.type || base.translatable!==row.translatable)
      throw Error("B0/B0-T1 source contract disagreement for "+row.key);
    const bindings=use.get(row.key),keyRisk=riskForKey(row.key);
    const localize=(locale,target)=>{
      const missing=row.translatable&&target.rawXml===null;
      if(missing)localeGaps.push({
        kind:"LOCALE_MISSING",key:row.key,locale,sourceHash:row.sourceHash,
        disposition:"UNKNOWN_NEEDS_SHOULD_TRANSLATE_CLASSIFICATION",
        risk:keyRisk,bindingCandidates:bindings.length
      });
      return {
        status:target.status,translationHash:target.translationHash,
        hasDedicatedDefinition:target.rawXml!==null,
        lexicalFormatCheck:"REFER_TO_B0_FORMAT_CONTRACTS",
        semanticApproval:"NOT_ASSESSED_BY_L10",
        staleAssessment:"NOT_ASSESSED_NO_APPROVAL_REVISION"
      };
    };
    if(!bindings.length){
      unknown.push({
        kind:"UNKNOWN_MAPPING",key:row.key,reason:"NO_STATIC_CALLSITE_FOUND",
        deletionAllowed:false,
        note:"Indirect, generated or runtime uses are not excluded"
      });
    }
    sources.push({
      key:row.key,type:row.type,translatable:row.translatable,
      sourceHash:row.sourceHash,sourcePath:row.sourcePath,sourceLine:row.sourceLine,
      sourcePreview:row.sourcePreview,
      sourceFormatTokens:row.formatTokens,pluralQuantities:row.pluralQuantities,
      risk:keyRisk,
      contextEvidence:"CODE_PATH_AND_SYMBOL_HINT_ONLY",
      bindings,
      bindingCount:bindings.length,
      mappingStatus:bindings.length?"LEXICAL_BINDING_CANDIDATES":"UNKNOWN_MAPPING",
      locales:{"zh-TW":localize("zh-TW",row.zhTw),ja:localize("ja",row.ja)}
    });
  }
  for(const hint of scan.unresolved)unknown.push({
    kind:"UNKNOWN_MAPPING",reason:hint.kind,key:null,
    path:hint.path,line:hint.line,surfaceHint:hint.surfaceHint,
    fileRevision:hint.fileRevision,deletionAllowed:false,
    note:hint.reason
  });
  const literals=inventory.possibleHardcodedUiText.map(hit=>({
    ...hit,classification:classifyLiteral(hit.preview),
    gapKind:"VISIBLE_LITERAL_CANDIDATE",
    evidence:"LEXICAL_CANDIDATE_NOT_PROVEN_RUNTIME_VISIBLE"
  }));
  const summary={
    sourceKeys:sources.length,translatableKeys:sources.filter(x=>x.translatable).length,
    bindingCandidates:scan.bindings.filter(x=>rows.has(x.key)).length,
    boundSourceKeys:sources.filter(x=>x.bindingCount>0).length,
    sourceKeysWithoutStaticUse:sources.filter(x=>!x.bindingCount).length,
    nonBaseBindingCandidates:nonBase.length,
    localeMissing:{"zh-TW":localeGaps.filter(x=>x.locale==="zh-TW").length,
      ja:localeGaps.filter(x=>x.locale==="ja").length},
    dynamicOrIndirectHints:scan.unresolved.length,
    literalCandidates:literals.length,
    possibleNaturalLanguageLiterals:literals.filter(x=>x.classification==="HUMAN_LANGUAGE_CANDIDATE").length,
    runtimeUnwiredConfirmed:0,staleTranslationsConfirmed:0,
    semanticApprovalsInferred:0
  };
  return {
    schema:"acx-l10-source-context-binding-surface/1",
    sourceSha,catalogueRevision:matrix.catalogueRevision,
    authority:"Source XML + lexical code analysis. Not runtime, semantic or release proof.",
    warnings:[
      "Surface and symbol are filename/nearby-code hints, not verified UI identities.",
      "RUNTIME_UNWIRED and STALE_TRANSLATION cannot be confirmed without dynamic/review evidence.",
      "No static reference is not proof of unused resource; no deletions allowed.",
      "A translation definition or model review is not independent semantic approval."
    ],
    summary,sourceFiles:scan.files,sources,nonBaseBindings:nonBase,
    unresolvedMappings:unknown,localeGaps,literalCandidates:literals,
    runtimeUnwiredCandidates:scan.unresolved.map(x=>({
      path:x.path,line:x.line,status:"NEEDS_RUNTIME_OR_DATA_FLOW_PROOF"
    }))
  };
}

export function buildL10(root=ROOT,sourceSha="unavailable"){
  return assembleL10(generateMatrix(root),buildInventory(root),scanBindings(root),sourceSha);
}

const tsvCell=v=>JSON.stringify(v??"");
export function bindingTsv(report){
  const keys=["key","source_hash","resource_type","binding_id","path","line",
    "symbol_hint","surface_hint","text_api_hint","binding_revision",
    "runtime_wiring","action_semantic","zh_TW_status","ja_status"];
  const body=[];
  for(const row of report.sources){
    for(const b of row.bindings.length?row.bindings:[null]){
      const vals=[row.key,row.sourceHash,row.type,b?.bindingId,
        b?.path,b?.line,b?.symbolHint,b?.surfaceHint,b?.textApiHint,
        b?.bindingRevision,b?.runtimeWiring,b?.actionSemantic,
        row.locales["zh-TW"].status,row.locales.ja.status];
      body.push(vals.map(tsvCell).join("\t"));
    }
  }
  return keys.join("\t")+"\n"+body.join("\n")+"\n";
}

export function summaryMarkdown(report){
  const s=report.summary;
  return [
    "# L10｜全 App Source → Context → Binding → Surface 靜態索引",
    "",
    "> Node.js source-only evidence; not an Android Runtime, screenshot, translation approval or release claim.",
    "",
    "- Source commit: "+report.sourceSha,
    "- Catalogue revision: "+report.catalogueRevision,
    "- Base keys: "+s.sourceKeys+"; translatable: "+s.translatableKeys,
    "- Lexical binding candidates: "+s.bindingCandidates+"; keys with references: "+s.boundSourceKeys,
    "- Base keys without static callsites: "+s.sourceKeysWithoutStaticUse+" (not verified unused)",
    "- Missing locale definitions: zh-TW "+s.localeMissing["zh-TW"]+"; ja "+s.localeMissing.ja,
    "- Indirect/dynamic unresolved callsite hints: "+s.dynamicOrIndirectHints,
    "- Possible hardcoded literal locations: "+s.literalCandidates+
      " (natural-language candidates "+s.possibleNaturalLanguageLiterals+")",
    "",
    "## Explicitly unverified",
    "",
    "- Context meaning and action semantics need source behavior checks; they are never assigned from key names.",
    "- Surface hints come from filenames, not runtime UI instrumentation.",
    "- Runtime unwired and stale translation are NOT measured by this static index.",
    "- Missing translation is not proof translation is required; classify intentional fallback separately.",
    "- Per-key locale definitions do not prove independent human semantic approval.",
    "",
    "## Artifact entrypoints",
    "",
    "- l10-binding-graph.json: full source-to-binding candidates, file hashes and locale states.",
    "- l10-binding-index.tsv: one source/binding candidate per line, JSON-quoted cells.",
    "- l10-gap-report.json: missing translations, unresolved dynamic bindings and literal candidates.",
    "- l10-source-sha-manifest.json: Git/source metadata and scanned source-file hashes.",
    ""
  ].join("\n");
}

if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const index=process.argv.indexOf("--out-dir");
  const output=index<0?join(ROOT,"build/localization/B0_FULL_APP_INVENTORY"):
    resolve(process.argv[index+1]??"");
  if(!output.startsWith(ROOT+(process.platform==="win32"?"\\":"/")))
    throw Error("Output must remain inside repository");
  let sourceSha="unavailable";
  try { sourceSha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim(); }
  catch { /* Exported snapshots are not automatically Git worktrees. */ }
  const graph=buildL10(ROOT,sourceSha);
  mkdirSync(output,{recursive:true});
  const emit=(name,data)=>writeFileSync(join(output,name),JSON.stringify(data,null,2)+"\n");
  emit("l10-binding-graph.json",graph);
  writeFileSync(join(output,"l10-binding-index.tsv"),bindingTsv(graph));
  emit("l10-gap-report.json",{
    schema:"acx-l10-gap-report/1",sourceSha,catalogueRevision:graph.catalogueRevision,
    summary:graph.summary,localeGaps:graph.localeGaps,
    unknownMappings:graph.unresolvedMappings,literalCandidates:graph.literalCandidates,
    runtimeUnwiredConfirmed:[],staleTranslationConfirmed:[],
    note:"No semantic or runtime findings are promoted without independent evidence."
  });
  emit("l10-source-sha-manifest.json",{
    schema:"acx-l10-source-manifest/1",sourceSha,
    catalogueRevision:graph.catalogueRevision,
    files:graph.sourceFiles,sourceSchema:graph.schema
  });
  writeFileSync(join(output,"l10-summary.md"),summaryMarkdown(graph));
  console.log("L10_SOURCE_BINDING_GRAPH "+JSON.stringify(graph.summary));
}
