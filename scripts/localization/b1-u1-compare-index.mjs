#!/usr/bin/env node
/* B1-U1: side-by-side read-only view into the B0-T1 full 2471-key matrix. */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { generateMatrix } from "./b0-t1-canonical-matrix.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const PATH=join(ROOT,"app/src/main/assets/localization/v1/source-compare.json");
export function sourceCompareIndex(){
 const m=generateMatrix();
 return {schema:"acx-b1-u1-source-compare/1",catalogRevision:m.catalogueRevision,
  items:m.rows.map(r=>({key:r.key,en:r.sourcePreview,
    zhTw:r.zhTw.preview??null,ja:r.ja.preview??null}))};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
 const content=JSON.stringify(sourceCompareIndex());
 if(process.argv.includes("--verify")){
   if(readFileSync(PATH,"utf8")!==content)throw Error("B1-U1 source compare drift");
 } else writeFileSync(PATH,content);
 console.log("B1_U1_COMPARE_VERIFIED "+sourceCompareIndex().items.length);
}
