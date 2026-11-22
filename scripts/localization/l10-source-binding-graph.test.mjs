#!/usr/bin/env node
import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import {
  maskNonCode, scanBindings, assembleL10, buildL10,
  bindingTsv, summaryMarkdown, surfaceFromPath
} from "./l10-source-binding-graph.mjs";
import { generateMatrix } from "./b0-t1-canonical-matrix.mjs";
import { buildInventory } from "./b0-resource-inventory.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
function fixture(){
  const root=mkdtempSync(join(tmpdir(),"acx-l10-"));
  const put=(p,s)=>{
    const file=join(root,p);
    mkdirSync(dirname(file),{recursive:true});
    writeFileSync(file,s,"utf8");
  };
  put("app/src/main/res/values/strings.xml",[
    "<resources>",
    '<string name="title">Title</string>',
    '<string name="remove_track">Remove from playlist</string>',
    '<string name="unused">Unused candidate</string>',
    '<string name="internal" translatable="false">API_TOKEN</string>',
    '<plurals name="minutes"><item quantity="one">%1$d minute</item><item quantity="other">%1$d minutes</item></plurals>',
    '<string-array name="options"><item>One</item><item>Two</item></string-array>',
    "</resources>"].join("\n"));
  put("app/src/main/res/values-b+zh+Hant+TW/strings.xml",
    '<resources><string name="title">標題</string><string name="remove_track">從清單移除</string></resources>');
  put("app/src/main/res/values-ja/strings.xml",
    '<resources><string name="title">タイトル</string></resources>');
  put("app/src/main/java/org/sample/ui/settings/SettingsScreen.kt",[
    'package org.sample.ui.settings',
    'fun render() {',
    '  Text(text = stringResource(R.string.title))',
    '  val buttonTitleRes = R.string.remove_track',
    '  // R.string.unused',
    '  /* R.string.internal',
    '     R.string.unused */',
    '  val preview = "R.string.unused"',
    '  Text("Human readable hardcoded label")',
    '  resources.getIdentifier(key, "string", "sample")',
    '}'
  ].join("\n"));
  put("app/src/main/res/layout/test.xml",'<TextView android:text="@string/title" />');
  return root;
}

test("L10 lexer does not treat comments or quoted Kotlin strings as resource callsites",()=>{
  const raw=[
    'R.string.title',
    '// R.string.hidden',
    '/* R.string.another',
    'R.string.other */',
    'val sample = "R.string.in_string"',
    "val char='R'",
    'val real = R.string.remove_track'
  ].join("\n");
  const masked=maskNonCode(raw);
  assert.match(masked,/R.string.title/);
  assert.match(masked,/R.string.remove_track/);
  for(const key of ["hidden","another","other","in_string"]) {
    assert(!masked.includes("R.string."+key));
  }
  assert.equal(masked.split("\n").length,raw.split("\n").length);
});

test("L10 keeps surface hints explicitly tentative",()=>{
  assert.equal(surfaceFromPath("app/src/main/java/pkg/ui/settings/Preferences.kt"),
    "SETTINGS_UI_CANDIDATE");
  assert.equal(surfaceFromPath("wear/src/main/java/pkg/Screen.kt"),"WEAR_CANDIDATE");
  assert.equal(surfaceFromPath("app/src/main/java/pkg/notification/Notify.kt"),"OTHER_SOURCE_CANDIDATE");
});

test("L10 full fixture keeps source identity, all bindings, locale gaps and unknowns separate",()=>{
  const root=fixture();
  try{
    const matrix=generateMatrix(root), inventory=buildInventory(root);
    const scan=scanBindings(root);
    const result=assembleL10(matrix,inventory,scan,"test-sha");
    assert.equal(result.summary.sourceKeys,6);
    assert.equal(result.summary.translatableKeys,5);
    assert.equal(result.summary.localeMissing["zh-TW"],3);
    assert.equal(result.summary.localeMissing.ja,4);
    const title=result.sources.find(x=>x.key==="string:title");
    assert.equal(title.bindings.length,2);
    assert(title.bindings.some(x=>x.surfaceHint==="SETTINGS_UI_CANDIDATE"));
    assert(title.bindings.some(x=>x.textApiHint==="ANDROID_XML_RESOURCE_CANDIDATE"));
    assert(title.bindings.every(x=>x.runtimeWiring==="NOT_VERIFIED"));
    assert(title.bindings.every(x=>x.actionSemantic==="UNVERIFIED"));
    const remove=result.sources.find(x=>x.key==="string:remove_track");
    assert.equal(remove.bindings.length,1);
    assert.equal(remove.bindings[0].textApiHint,"INDIRECT_ID_CANDIDATE");
    assert.equal(remove.locales.ja.hasDedicatedDefinition,false);
    const unused=result.sources.find(x=>x.key==="string:unused");
    assert.equal(unused.bindings.length,0);
    assert.equal(unused.mappingStatus,"UNKNOWN_MAPPING");
    assert(result.unresolvedMappings.some(x=>x.key==="string:unused"&&x.deletionAllowed===false));
    assert.equal(result.summary.dynamicOrIndirectHints,1);
    assert(result.literalCandidates.some(x=>x.preview.includes("Human readable")));
    assert.equal(result.summary.semanticApprovalsInferred,0);
    assert.equal(result.summary.runtimeUnwiredConfirmed,0);
    assert.equal(result.summary.staleTranslationsConfirmed,0);
    const nontrans=result.sources.find(x=>x.key==="string:internal");
    assert(nontrans&&!result.localeGaps.some(x=>x.key===nontrans.key));
    assert.equal(result.nonBaseBindings.length,0);
    assert.match(bindingTsv(result),/binding_revision/);
    assert.match(summaryMarkdown(result),/NOT measured/);
  }finally{rmSync(root,{recursive:true,force:true});}
});

test("L10 refuses B0/B0-T1 key mismatch rather than reporting false coverage",()=>{
  const root=fixture();
  try{
    const matrix=generateMatrix(root),inventory=buildInventory(root),scan=scanBindings(root);
    inventory.sourceKeys[0].type="fake";
    assert.throws(()=>assembleL10(matrix,inventory,scan),/source contract disagreement/);
  }finally{rmSync(root,{recursive:true,force:true});}
});

test("L10 real repo builds a full-app index without claiming runtime or human approval",()=>{
  const data=buildL10(ROOT,"test-sha");
  assert(data.summary.sourceKeys>200,"Must not use LP0 200-key subset");
  assert.equal(data.summary.sourceKeys,data.sources.length);
  assert.equal(data.summary.sourceKeys,
    data.summary.boundSourceKeys+data.summary.sourceKeysWithoutStaticUse);
  assert(data.summary.bindingCandidates>0);
  assert(data.summary.localeMissing["zh-TW"]>0);
  assert(data.summary.localeMissing.ja>0);
  assert(data.sources.every(x=>x.bindings.every(b=>
    b.runtimeWiring==="NOT_VERIFIED"&&b.semanticApproval==="NOT_INFERRED")));
  assert(data.sources.every(x=>x.locales["zh-TW"].semanticApproval==="NOT_ASSESSED_BY_L10"));
  assert(data.unresolvedMappings.every(x=>x.deletionAllowed===false));
  assert.equal(data.summary.runtimeUnwiredConfirmed,0);
  assert.equal(data.summary.staleTranslationsConfirmed,0);
});
