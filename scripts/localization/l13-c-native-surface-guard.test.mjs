#!/usr/bin/env node
/** Source-diff contract, independent from external Gradle artifact mirrors. */
import test from "node:test";
import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {readFileSync,existsSync} from "node:fs";
import {join,dirname,resolve} from "node:path";
import {fileURLToPath} from "node:url";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>readFileSync(join(ROOT,p),"utf8");
const shaGitBlob=s=>{
  const raw=Buffer.from(s,"utf8");
  return createHash("sha1").update(Buffer.from("blob "+raw.length+"\0"))
    .update(raw).digest("hex");
};
const service="app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt";
const baselineBlob="1a719fa5f348212296080a50ec6686a82947dd00";
function revert(before,newText,oldText,name){
  assert.equal(before.split(newText).length-1,1,"L13-C "+name+" expected exactly once");
  return before.replace(newText,oldText);
}
test("L13-C preserves every original AlarmService byte except seven notification string replacements",()=>{
  let value=read(service);
  value=revert(value,
    "import com.sysadmindoc.alarmclock.util.LocaleRuntimeTextBridge\n",
    "","read-only bridge import");
  const pairs=[
    ["                    .setContentTitle(LocaleRuntimeTextBridge.text(this@AlarmService, R.string.b2_test_title))",
     "                    .setContentTitle(getString(R.string.b2_test_title))","B2 title"],
    ["                    .setContentText(LocaleRuntimeTextBridge.text(this@AlarmService, R.string.b2_test_description))",
     "                    .setContentText(getString(R.string.b2_test_description))","B2 description"],
    ["            .setContentTitle(LocaleRuntimeTextBridge.text(this, R.string.notif_alarm_title))",
     "            .setContentTitle(getString(R.string.notif_alarm_title))","full alarm title"],
    ["LocaleRuntimeTextBridge.formatted(this@AlarmService, R.string.notif_snooze_action, alarm.snoozeDurationMinutes)",
     "getString(R.string.notif_snooze_action, alarm.snoozeDurationMinutes)","snooze text"],
    ["LocaleRuntimeTextBridge.text(this@AlarmService, R.string.notif_dismiss_action)",
     "getString(R.string.notif_dismiss_action)","dismiss text"],
    ["            .setContentTitle(LocaleRuntimeTextBridge.text(this, R.string.notif_missed_title))",
     "            .setContentTitle(getString(R.string.notif_missed_title))","missed title"],
    ["                LocaleRuntimeTextBridge.formatted(\n                    this,\n                    R.string.notif_missed_auto_silenced,",
     "                getString(\n                    R.string.notif_missed_auto_silenced,","missed formatted text"]
  ];
  for(const [newText,oldText,name] of pairs)value=revert(value,newText,oldText,name);
  assert.equal(shaGitBlob(value),baselineBlob,
    "AlarmService changed outside the L13-C text-only integration scope");
});
test("L13-C leaves critical synchronous foreground promotion on the unchanged native path",()=>{
  const serviceSource=read(service);
  const start=serviceSource.indexOf("    private fun startForegroundWithPlaceholder() {");
  const end=serviceSource.indexOf("    private fun buildAlarmNotification(",start);
  assert(start>=0&&end>start);
  const urgent=serviceSource.slice(start,end);
  assert(urgent.includes("getString(R.string.notif_alarm_title)"));
  assert(urgent.includes("getString(R.string.notif_alarm_ringing)"));
  assert(!urgent.includes("LocaleRuntimeTextBridge"));
});
test("L13-C native resources stay unchanged and model draft XML is never committed",()=>{
  const paths=[
    "app/src/main/res/values-ja/strings_l13_reviewed.xml",
    "app/src/main/res/values-b+zh+Hant+TW/strings_l13_reviewed.xml",
    "app/src/main/res/values-ja/strings_l13_ci_only_unapproved.xml",
    "app/src/main/res/values-b+zh+Hant+TW/strings_l13_ci_only_unapproved.xml"
  ];
  for(const path of paths)assert(!existsSync(join(ROOT,path)),"unexpected model XML: "+path);
  const source=read("app/src/main/res/values/strings.xml");
  for(const key of ["notif_alarm_title","notif_snooze_action",
    "notif_dismiss_action","notif_missed_title",
    "notif_missed_auto_silenced","b2_test_title","b2_test_description"]){
    assert(new RegExp("<string\\b[^>]*\\bname=[\"']"+key+"[\"']").test(source),
      "missing canonical English Android resource "+key);
  }
});
test("L13-C debug preview is isolated from product UI and surfaces fallback provenance",()=>{
  const debug=read("app/src/debug/java/com/sysadmindoc/alarmclock/ui/languagelab/Lp2SettingsPreviewActivity.kt");
  const bridge=read("app/src/main/java/com/sysadmindoc/alarmclock/util/LocaleRuntimeTextBridge.kt");
  assert(debug.includes("LocaleRuntimeTextBridge.previewBuiltin"));
  assert(debug.includes("LocaleContextTextAdapter.formatted"));
  assert(bridge.includes("ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED")||
    read("app/src/main/java/com/sysadmindoc/alarmclock/util/LocaleContextTextAdapter.kt")
      .includes("ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED"));
  assert(bridge.includes("LocaleManager::class.java"));
  assert(bridge.includes("LocaleSelectionContract.builtinTag"));
  assert(bridge.includes("return context.getString(resId)"));
  assert(!bridge.includes("applicationLocales ="));
  assert(!bridge.includes("setApplicationLocales("));
});
