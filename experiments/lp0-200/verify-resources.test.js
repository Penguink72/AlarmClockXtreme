"use strict";
/* LP0-200 resource fidelity tests. Node.js built-ins only, no Python or npm install. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const here = __dirname;
const root = path.resolve(here,"../..");
const catalogue = require("./catalog.json");
const s0 = require("../../localization/governance/s0/source-amendments.json");
assert.equal(s0.schema, "acx-s0-canonical-source-amendments/1");
assert.equal(s0.baselineCatalogRevision, catalogue.revision);
const changes = new Map(s0.items.map(item => [item.key,item]));
assert.equal(changes.size,1,"S0 allows one canonical correction");
const locales = {
  "en":"values",
  "zh-TW":"values-b+zh+Hant+TW",
  "ja":"values-ja"
};
const expectedSections = {alarm:50,snooze:30,music:45,history:20,settings:40,diagnostics:15};
const core = require("./web/probe-core.js");
const entries = new Map(catalogue.entries.map(e=>[e.key,e]));
function unescapeXml(value) {
  return value.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi,(matched,entity)=>{
    if(entity[0]==="#")return String.fromCodePoint(entity[1].toLowerCase()==="x"?parseInt(entity.slice(2),16):parseInt(entity.slice(1),10));
    return ({amp:"&",lt:"<",gt:">",quot:'"',apos:"'"})[entity.toLowerCase()];
  });
}
function stringsFromXml(localeDir) {
  const xml = fs.readFileSync(path.join(root,"app/src/main/res",localeDir,"strings.xml"),"utf8");
  const re = /<string\b([^>]*)\bname="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  const result = new Map();
  for(const match of xml.matchAll(re)){
    const attrs=match[1]+match[3];
    if(/translatable\s*=\s*"false"/.test(attrs)||/<[^>]+>/.test(match[4]))continue;
    assert(!result.has(match[2]), "duplicate resource key "+match[2]);
    result.set(match[2],unescapeXml(match[4]));
  }
  return result;
}
assert.equal(catalogue.format,"acx-language-catalog-probe/1");
assert.equal(catalogue.count,200);
assert.equal(catalogue.entries.length,200);
assert.equal(entries.size,200);
assert.deepEqual(Object.fromEntries(Object.keys(expectedSections).map(section=>[
  section,catalogue.entries.filter(e=>e.section===section).length
])),expectedSections);
const source = stringsFromXml("values");
for(const item of catalogue.entries) {
  const s0Item = changes.get(item.key);
  if(s0Item) {
    assert.equal(item.source,s0Item.baselineSource,"Frozen English source changed");
    assert.equal(source.get(item.key),s0Item.currentSource,"Canonical English S0 drift");
  } else assert.equal(item.source,source.get(item.key),"English source drift: "+item.key);
  assert.deepEqual(item.placeholders,core.placeholders(item.source),"Source parameter drift: "+item.key);
}
for(const [locale,dir]of Object.entries(locales)){
  const pack = JSON.parse(fs.readFileSync(path.join(here,"packs",locale+".json"),"utf8"));
  const actual = stringsFromXml(dir);
  const count=Object.keys(core.validatePack(pack,catalogue).translations).length;
  assert.equal(count,locale==="en"?200:155,locale+" coverage drift");
  for(const [key,value]of Object.entries(pack.translations)){
    const s0Item=changes.get(key);
    if(s0Item && locale==="en") {
      assert.equal(value,s0Item.baselineSource,"Frozen English pack drift");
      assert.equal(actual.get(key),s0Item.currentSource,"Canonical English drift");
    } else if(s0Item) {
      assert.equal(value,s0Item.locales[locale].baseline,"Frozen locale pack drift");
      assert.equal(actual.get(key),s0Item.locales[locale].currentCandidate,"Canonical locale drift");
    } else assert.equal(value,actual.get(key),locale+" value drift: "+key);
  }
  for(const entry of catalogue.entries){
    if(!(entry.key in pack.translations)){
      assert.notEqual(locale,"en","English cannot miss a key");
      assert.equal(entry.source,source.get(entry.key),"fallback source must remain English");
    }
  }
}
const payload=require("fs").readFileSync(path.join(here,"web/catalog-data.js"),"utf8").trim();
assert(payload.startsWith("window.ACX_LP0_DATA = "), "embedded browser catalogue missing");
const embedded=JSON.parse(payload.slice("window.ACX_LP0_DATA = ".length,-1));
assert.deepEqual(embedded.catalog,catalogue);
for(const locale of Object.keys(locales)){
  assert.deepEqual(embedded.packs[locale],JSON.parse(fs.readFileSync(path.join(here,"packs",locale+".json"),"utf8")));
}
console.log("LP0_RESOURCE_CHECKS PASS 200 frozen snapshot keys, 1 explicit S0 canonical amendment, 155 historic localized keys per locale");
