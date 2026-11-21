#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { extractRawResources, readablePreview, canonicalSourceHash,
  generateMatrix, matrixTsv } from "./b0-t1-canonical-matrix.mjs";

test("source text remains literal XML, with CDATA/markup and quoted characters",()=>{
  const xml='<resources><string name="demo">Use &lt;b&gt;<b>x</b> &amp; y</string>'+
    '<string name="cdata"><![CDATA[<b>not a definition</b>]]></string>'+
    '<plurals name="units"><item quantity="one">One</item>'+
    '<item quantity="other">%1$d things</item></plurals></resources>';
  const map=extractRawResources(xml);
  assert.equal(map.size,3);
  assert.equal(map.get("string:demo").rawXml,"Use &lt;b&gt;<b>x</b> &amp; y");
  assert.match(map.get("string:cdata").rawXml,/<!\[CDATA\[/);
  assert.match(map.get("plurals:units").rawXml,/<item quantity="other">/);
  assert.match(readablePreview(map.get("string:demo").rawXml),/Use <b>x & y/);
});

test("per-key source hash changes on source or printf contract, not target edits",()=>{
  const example={key:"string:abc",type:"string",translatable:true,
    rawXml:"A %1$d",formatTokens:["%1$d"],pluralQuantities:[]};
  const original=canonicalSourceHash(example);
  assert.match(original,/^sha256:[a-f0-9]{64}$/);
  assert.notEqual(original,canonicalSourceHash({...example,rawXml:"B %1$d"}));
  assert.notEqual(original,canonicalSourceHash({...example,formatTokens:["%1$s"]}));
  assert.equal(original,canonicalSourceHash({...example,ja:"Some text"}));
});

test("full current Android catalog contains exact bilingual texts, no invented missing text",()=>{
  const data=generateMatrix();
  assert.equal(data.counts.baseKeys,2471);
  assert.equal(data.counts.translatable,2462);
  assert.equal(data.counts.zhDefined,912);
  assert.equal(data.counts.jaDefined,912);
  assert.equal(data.counts.missingZh,1550);
  assert.equal(data.counts.missingJa,1550);
  const settings=data.rows.find(x=>x.key==="string:settings_language");
  assert(settings);
  assert.equal(settings.zhTw.sourceHash,undefined);
  assert.match(settings.sourceXml,/App language/i);
  assert.match(settings.zhTw.rawXml,/App 語言/);
  assert.match(settings.ja.rawXml,/アプリの言語/);
  assert(data.rows.some(x=>x.translatable&&x.zhTw.rawXml===null&&x.zhTw.status==="MISSING_LOCALE_DEFINITION"));
  assert(data.rows.some(x=>x.type==="plurals"&&x.pluralQuantities.length>0));
  const tsv=matrixTsv(data);
  assert.equal(tsv.trimEnd().split("\n").length,2472);
  assert(tsv.includes("source_hash"));
  assert(tsv.includes("EXISTING_XML_NOT_INDEPENDENTLY_APPROVED"));
});
