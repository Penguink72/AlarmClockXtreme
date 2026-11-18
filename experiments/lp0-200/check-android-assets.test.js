"use strict";
/* LP1 data parity checks using only installed Node.js built-ins. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname,"../../..");
const debug = path.join(root,"app/src/debug/assets/lp1");
const exp = path.join(root,"experiments/lp0-200");
const files = ["catalog.json","packs/en.json","packs/zh-TW.json","packs/ja.json"];
for (const file of files) {
  const sample = JSON.parse(fs.readFileSync(path.join(exp,file),"utf8"));
  const android = JSON.parse(fs.readFileSync(path.join(debug,file),"utf8"));
  assert.deepEqual(android,sample,"debug APK language asset drift: "+file);
}
const source = fs.readFileSync(path.join(root,"app/src/debug/java/com/sysadmindoc/alarmclock/ui/languagelab/Lp1Repository.kt"),"utf8");
assert(source.includes('private const val PACK_FORMAT = "acx-language-pack-probe/1"'));
assert(source.includes("catalog.byKey[key]"));
assert(source.includes("AtomicFile("));
console.log("LP1_ASSET_PARITY PASS 200-key sample, 3 localized JSON packs, offline Android reader contract");
