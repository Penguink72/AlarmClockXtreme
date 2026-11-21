#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { buildCanonicalCatalog, canonicalCatalogJson } from "./b1-build-canonical-catalog.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const asset=join(root,"app/src/main/assets/localization/v1/catalog.json");

test("B1-C1 catalog is complete and derived from all B0-T1 authoritative keys",()=>{
  const c=buildCanonicalCatalog();
  assert.equal(c.format,"acx-language-catalog/1");
  assert.equal(c.sourceKeyCount,2471);
  assert.equal(c.entries.length,2471);
  assert.equal(c.entries.filter(x=>x.translatable).length,2462);
  assert.equal(c.entries.filter(x=>x.type==="plurals").length,43);
  assert.equal(c.entries.filter(x=>x.type==="array").length,1);
  assert.match(c.catalogRevision,/^sha256:[a-f0-9]{64}$/);
  assert(c.entries.every(x=>/^sha256:[a-f0-9]{64}$/.test(x.sourceHash)));
});

test("single generated asset matches checked-in canonical catalog byte for byte",()=>{
  assert.equal(readFileSync(asset,"utf8"),canonicalCatalogJson());
});

test("B1-C1 manifest carries exact printf/plural contract and preserves source",()=>{
  const c=buildCanonicalCatalog();
  const settings=c.entries.find(x=>x.key==="string:settings_title");
  assert(settings);
  assert.equal(settings.type,"string");
  assert.match(settings.sourceXml,/Settings/i);
  const plural=c.entries.find(x=>x.type==="plurals");
  assert(plural);
  assert(plural.pluralQuantities.includes("other"));
  const array=c.entries.find(x=>x.type==="array");
  assert.equal(array.arrayItemCount,15);
});
