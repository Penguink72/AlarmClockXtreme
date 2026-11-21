#!/usr/bin/env node
/* LP2-B3: source-backed 35-key extension; no Android runtime or reviewer shortcuts. */
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve,dirname,join} from "node:path";
import {fileURLToPath} from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const read=p=>readFileSync(join(root,p),"utf8");
const app="app/src/main/java/com/sysadmindoc/alarmclock/";
const direct={
  "ui/alarmedit/AlarmEditIntegrationSections.kt": [
    "alarm_edit_default_ringtone_placeholder",
    "alarm_edit_emergency_phone"
  ],
  "ui/alarmedit/AlarmEditOverviewSections.kt": [
    "alarm_edit_group_custom_name"
  ],
  "ui/alarmedit/AlarmEditScheduleSections.kt": [
    "alarm_edit_holidays_hint"
  ],
  "ui/settings/SettingsBackupSections.kt": [
    "settings_backup_locked_description"
  ],
  "ui/settings/SettingsIntegrationSections.kt": [
    "settings_webhook_description",
    "settings_recent_deliveries",
    "settings_hue_api_placeholder",
    "settings_health_no_session",
    "settings_connection_sunrise_skipped"
  ],
  "ui/bedtime/BedtimeSleepTrackingSections.kt": [
    "bedtime_update_health_connect_before_recent_sleep"
  ],
  "ui/timer/TimerScreen.kt": [
    "alarm_edit_delete_digit"
  ],
  "ui/permissions/PermissionHelper.kt": [
    "permissions_context_permissions"
  ]
};
const indirect={
  "ui/settings/SettingsScreen.kt": {
    "keys": [
      "settings_pane_readiness",
      "settings_pane_defaults_description",
      "settings_pane_integrations_description",
      "settings_pane_personalization_description",
      "settings_pane_backup_description",
      "settings_pane_utilities_description",
      "settings_language_description",
      "settings_battery_protected",
      "settings_battery_setup_needed",
      "settings_setup_needed"
    ],
    "calls": 10
  },
  "ui/alarmedit/AlarmEditScreen.kt": {
    "keys": [
      "alarm_edit_page_integrations",
      "alarm_edit_section_group_description",
      "alarm_edit_anti_snooze",
      "alarm_edit_section_anti_snooze_description",
      "alarm_edit_smart_alarm",
      "alarm_edit_section_hue_description",
      "alarm_edit_24_hour"
    ],
    "calls": 2
  },
  "ui/alarmedit/AlarmEditSupport.kt": {
    "keys": [],
    "calls": 2
  },
  "ui/onboarding/OnboardingScreen.kt": {
    "keys": [
      "onboarding_see_weather_calendar_next_alarm"
    ],
    "calls": 1
  },
  "ui/ringtone/MusicLibraryDialog.kt": {
    "keys": [
      "music_library_pool_clear_selection",
      "music_library_catalog_empty",
      "music_library_preview"
    ],
    "calls": 3
  },
  "ui/settings/SettingsReadinessSections.kt": {
    "keys": [
      "settings_show_incident_events"
    ],
    "calls": 1
  }
};
const catalog=JSON.parse(read("app/src/debug/assets/lp2/catalog.json"));
assert.equal(catalog.count,200);
assert.equal(catalog.revision,"g6-200-current-20261120");
const sources=new Map(catalog.entries.map(e=>[e.key,e]));
const resolver=read(app+"ui/settings/Lp2PreviewText.kt");
function getSet(name){
 const start=resolver.indexOf("val "+name+": Set<String> = setOf(");
 assert(start>=0,"Missing "+name);
 const end=resolver.indexOf("\n    )",start);
 assert(end>start);
 const list=[...resolver.slice(start,end).matchAll(/"([a-z0-9_]+)"/g)].map(m=>m[1]);
 assert.equal(list.length,new Set(list).size,"Duplicate "+name);
 return list;
}
const pilot=getSet("pilotKeys"),cross=getSet("crossScreenKeys"),extended=getSet("extendedKeys");
const b3Direct=getSet("b3DirectKeys"),b3Indirect=getSet("b3IndirectKeys");
assert.equal(pilot.length,21);
assert.equal(cross.length,31);
assert.equal(extended.length,36);
assert.equal(b3Direct.length,13);
assert.equal(b3Indirect.length,22);
const old=new Set([...pilot,...cross,...extended]);
assert.equal(old.size,87);
const total=new Set([...old,...b3Direct,...b3Indirect]);
assert.equal(total.size,122);
const actualDirect=new Set(Object.values(direct).flat());
const actualIndirect=new Set(Object.values(indirect).flatMap(x=>x.keys));
assert.deepEqual(b3Direct.slice().sort(),[...actualDirect].sort());
assert.deepEqual(b3Indirect.slice().sort(),[...actualIndirect].sort());
for(const key of total)assert.deepEqual(sources.get(key)?.placeholders,[],"B3 included formatted or missing key: "+key);
assert(catalog.entries.filter(e=>e.placeholders.length>0).length===21,"The formatted-key queue must remain untouched");
assert.equal(catalog.entries.filter(e=>!total.has(e.key)).length,78);
let directCount=0,indirectCount=0;
const directExpr=/com\.sysadmindoc\.alarmclock\.ui\.settings\.Lp2PreviewText\.text\(R\.string\.([a-z0-9_]+), "([a-z0-9_]+)"\)/g;
for(const [p,keys] of Object.entries(direct)){
 const txt=read(app+p);
 const found=[...txt.matchAll(directExpr)].filter(m=>actualDirect.has(m[1]));
 const mapped=new Set(found.map(m=>m[1]));
 assert.deepEqual([...mapped].sort(),keys.slice().sort(),"Wrong direct B3 keys: "+p);
 for(const match of found)assert.equal(match[1],match[2],"Resource key mismatch");
 directCount+=found.length;
}
assert.equal(directCount,15,"B3 direct callsites have drifted");
for(const [p,item] of Object.entries(indirect)){
 const txt=read(app+p);
 const calls=txt.match(/com\.sysadmindoc\.alarmclock\.ui\.settings\.Lp2PreviewText\.textForResource\(/g)||[];
 assert.equal(calls.length,item.calls,"Missing resource-ID indirect binding: "+p);
 for(const key of item.keys)assert(txt.includes("R.string."+key),"Missing real static resource owner: "+p+" / "+key);
 indirectCount+=calls.length;
}
assert.equal(indirectCount,19);
const mappings=[...resolver.matchAll(/R\.string\.([a-z0-9_]+) -> "([a-z0-9_]+)"/g)];
assert.equal(mappings.length,22);
for(const m of mappings)assert.equal(m[1],m[2],"Wrong resource-ID to canonical G6 key");
assert.deepEqual([...new Set(mappings.map(m=>m[1]))].sort(),b3Indirect.slice().sort());
assert(resolver.includes("return if (key == null) stringResource(resId) else text(resId, key)"),
 "Non-whitelisted indirect resource must preserve the Android source");
assert(resolver.includes("if (!BuildConfig.DEBUG || active == null || key !in previewKeys) return native"),
 "No preview can bypass the release/debug guard");
const firing=read(app+"ui/alarmfiring/AlarmFiringScreen.kt");
assert(!firing.includes("Lp2PreviewText."),"Do not add bilingual preview to firing controls");
const main=read(app+"MainActivity.kt");
assert(main.indexOf("AlarmService.activeAlarm.get()")<main.indexOf("Lp2PreviewText.refresh(this)"),
 "Never delay active alarms for preview loading");
const manifest=read("app/src/main/AndroidManifest.xml");
assert(!manifest.includes("Lp2SettingsPreviewActivity"),"Experimental activity leaked to release manifest");
const lock=read("gradle/verification-metadata.xml");
assert.equal([...lock.matchAll(/<also-trust value="/g)].length,2,"Unknown Gradle hash added");
console.log("LP2B3_SOURCE_PASS "+JSON.stringify({newKeys:35,directKeys:13,indirectKeys:22,totalKeys:122,directCallsites:directCount,indirectCallsites:indirectCount,remainingWithoutParams:57,remainingWithParams:21,releaseUnchanged:true}));
