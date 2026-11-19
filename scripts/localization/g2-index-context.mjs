#!/usr/bin/env node
/* LP-G2: evidence from real checked-out source files; no inferred callsite claims. */
import { readdirSync, readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, relative, join, dirname, extname } from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";
import { loadJson } from "./lp0-audit.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const matrix = loadJson("localization/governance/lp0/source-matrix.json");
const terms = loadJson("localization/governance/g1/glossary-contract.json");
const byKey = new Map(matrix.records.map(r => [r.key,r]));
const uses = new Map(matrix.records.map(r => [r.key,[]]));
const scanned=[];

function walk(folder) {
  for (const d of readdirSync(folder,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,"en"))) {
    const path=join(folder,d.name);
    if (d.isDirectory()) walk(path);
    else if (d.isFile() && [".kt",".java",".xml"].includes(extname(path))) {
      // Only source callsites, never resource definitions or generated build outputs.
      const rel=relative(root,path).replaceAll("\\","/");
      if (rel.startsWith("app/src/main/res/values") || rel.includes("/build/")) continue;
      if (!rel.startsWith("app/src/main/") && !rel.startsWith("wear/src/main/")) continue;
      scanned.push(rel);
      const lines=readFileSync(path,"utf8").split(/\r?\n/);
      const pattern=/\bR\s*\.\s*string\s*\.\s*([a-z][a-z0-9_]*)\b|@string\/([a-z][a-z0-9_]*)\b/g;
      for (const [index,line] of lines.entries()) {
        // Strip line comments; occurrences in block comments / string literals are tentative static references.
        const raw=line.replace(/\/\/.*$/,"");
        for (const m of raw.matchAll(pattern)) {
          const key=m[1]||m[2];
          if (!uses.has(key)) continue;
          const v=uses.get(key);
          v.push({path:rel,line:index+1,match:m[0],snippet:line.trim().slice(0,220),
            evidence:"STATIC_SOURCE_REFERENCE_ONLY",
            // A file location is not a proof of screen visibility or operation semantics.
            surfaceHint: classify(rel)});
        }
      }
    }
  }
}
function classify(path){
  if (/\/ui\/alarmfiring\//.test(path))return"alarm-firing-ui";
  if (/\/ui\/alarmedit\//.test(path))return"alarm-edit-ui";
  if (/\/ui\/alarmlist\//.test(path))return"alarm-list-ui";
  if (/\/ui\/ringtone\//.test(path))return"music-ui";
  if (/\/ui\/settings\//.test(path))return"settings-ui";
  if (/\/ui\/stats\//.test(path))return"statistics-ui";
  if (/\/ui\/onboarding\//.test(path))return"onboarding-ui";
  if (/\/ui\/navigation\//.test(path))return"navigation-ui";
  if (/\/service\//.test(path))return"service";
  if (/\/receiver\//.test(path))return"receiver";
  if (/\/worker\//.test(path))return"worker";
  if (/\/ui\//.test(path))return"other-ui";
  return"other-code-or-xml";
}
walk(join(root,"app/src/main"));
const wear=join(root,"wear/src/main");
try { walk(wear); } catch (e) { if(e.code!=="ENOENT")throw e; }
const rows=matrix.records.map(r => ({
  key:r.key,sourceHash:r.sourceHash,section:r.section,source:r.source,
  conceptIds:r.candidateConceptIds,
  sourceCallsites:uses.get(r.key).sort((a,b)=>a.path.localeCompare(b.path,"en")||a.line-b.line),
  callsiteStatus:uses.get(r.key).length?"STATIC_SOURCE_REFERENCE_FOUND":"NO_STATIC_REFERENCE_FOUND",
  semanticReview:"NOT_REVIEWED",
  screenshotEvidence:[],
  behaviorEvidence:[],
  guessedSurface:r.context.surfaceGuess,
  // Guarantee the guess never becomes a verified evidence item.
  guessedSurfaceVerified:false
}));
const stats={total:rows.length,withStaticReference:rows.filter(x=>x.sourceCallsites.length>0).length,
 noStaticReference:rows.filter(x=>!x.sourceCallsites.length).length,
 totalStaticReferences:rows.reduce((n,x)=>n+x.sourceCallsites.length,0),
 filesScanned:scanned.length,semanticallyApproved:0};
const out={schema:"acx-g2-context-evidence/1",catalogRevision:matrix.baselineRevision,
 sourceCommit:matrix.sourceCommit,glossaryRevision:terms.glossaryRevision,
 method:"STATIC_SOURCE_REFERENCE_R.string_OR_XML_@string_ONLY",stats,records:rows};
assert.equal(rows.length,200);
if(stats.filesScanned===0)throw Error("No real Android source files scanned");
const outArg=process.argv.indexOf("--out");
if(outArg>=0){
 const target=resolve(process.argv[outArg+1]||"");
 assert(target.startsWith(root+ (process.platform==="win32"?"\\":"/")),"Output must be within repo");
 mkdirSync(dirname(target),{recursive:true});
 writeFileSync(target,JSON.stringify(out,null,2)+"\n");
}
console.log("G2_SOURCE_REFERENCE_INDEX PASS "+JSON.stringify(stats));
export { out };
