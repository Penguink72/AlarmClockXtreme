#!/usr/bin/env node
/* LP2-A: current G6 generated preview assets must match frozen committed Debug-only assets. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import core from "../../experiments/lp0-200/web/probe-core.js";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const generated=join(root,"build/localization/G6_UNREVIEWED_CURRENT_PREVIEW");
const debug=join(root,"app/src/debug/assets/lp2");
const read=path=>JSON.parse(readFileSync(path,"utf8"));
const expectedCatalog=read(join(generated,"current-catalog.json"));
const actualCatalog=read(join(debug,"catalog.json"));
assert.deepEqual(actualCatalog,expectedCatalog,"LP2 Debug catalog drifted from generated G6 current source");
assert.equal(actualCatalog.revision,"g6-200-current-20261120");
assert.equal(actualCatalog.entries.length,200);
const manifest=read(join(generated,"manifest.json"));
assert.equal(manifest.mayDistributeAsOfficial,false);
assert.equal(manifest.semanticallyApproved,0);
for(const locale of ["en","zh-TW","ja"]){
 const expected=read(join(generated,"packs",locale+".json"));
 const actual=read(join(debug,"packs",locale+".json"));
 assert.deepEqual(actual,expected,"LP2 Debug "+locale+" asset is not the G6 output");
 assert.equal(actual.catalogRevision,actualCatalog.revision);
 assert.equal(Object.keys(core.validatePack(actual,actualCatalog).translations).length,200);
 assert(Buffer.byteLength(JSON.stringify(actual),"utf8")<=256*1024);
}
const settings=readFileSync(join(root,"app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt"),"utf8");
const regex=/Lp2PreviewText\.text\(R\.string\.([a-z0-9_]+), "([a-z0-9_]+)"\)/g;
const targets=[...settings.matchAll(regex)];
const keys=[...new Set(targets.map(x=>x[1]))];
assert.equal(keys.length,21,"LP2-A must target exactly 21 real Settings keys");
assert.equal(targets.length,23,"Settings actual composable callsite contract changed");
for(const match of targets)assert.equal(match[1],match[2],"Live binding uses wrong stable key");
const catalogKeys=new Map(actualCatalog.entries.map(e=>[e.key,e]));
for(const key of keys){
 assert(catalogKeys.has(key),"Live UI key missing from G6 catalog "+key);
 assert.deepEqual(catalogKeys.get(key).placeholders,[],"Pilot needs formatting but was not designed for it: "+key);
}
const hook=readFileSync(join(root,"app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/Lp2PreviewText.kt"),"utf8");
for(const required of ["BuildConfig.DEBUG","mutableStateOf<Session?>(null)","AtomicFile(","getSharedPreferences(PREFS","catalogRevision","LP2 preview is Debug-only"]){
 assert(hook.includes(required),"LP2 preview isolation contract missing "+required);
}
const manifestXml=readFileSync(join(root,"app/src/debug/AndroidManifest.xml"),"utf8");
assert(manifestXml.includes(".ui.languagelab.Lp2SettingsPreviewActivity"));
assert(manifestXml.includes('android:exported="false"'));
const mainManifest=readFileSync(join(root,"app/src/main/AndroidManifest.xml"),"utf8");
assert(!mainManifest.includes("Lp2SettingsPreviewActivity"),"Debug Activity leaked into the release manifest");
for(const file of ["values","values-b+zh+Hant+TW","values-ja"]){
 const content=readFileSync(join(root,"app/src/debug/res",file,"strings.xml"),"utf8");
 assert(content.includes('name="lp2a_header"'));
 assert(content.includes('name="lp2a_open_legacy"'));
}
const invalid=structuredClone(read(join(debug,"packs","ja.json")));
invalid.catalogRevision="lp0-200-20261118";
assert.throws(()=>core.validatePack(invalid,actualCatalog),/不相容/);
console.log("LP2A_G6_ASSET_PARITY PASS: 200-key current G6 assets, 21 live Settings strings, Debug isolation, unreviewed-only guard");
