#!/usr/bin/env node
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname,join,resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const file=p=>readFileSync(join(root,p),"utf8");
const specs={
  "ui/alarmedit/AlarmEditAdvancedSection.kt": [
    "alarm_edit_shift_pattern",
    "alarm_edit_disabled",
    "alarm_edit_add_ringtone",
    "alarm_edit_add_ringtone_accessibility",
    "alarm_edit_add_ringtone_title",
    "alarm_edit_ringtone_uri_hint",
    "alarm_edit_ringtone_duplicate"
  ],
  "ui/alarmedit/AlarmEditDismissSections.kt": [
    "snooze_duration",
    "alarm_edit_progressive_snooze",
    "alarm_edit_progressive_snooze_hint",
    "alarm_edit_squats_required",
    "alarm_edit_capture_photo_hint",
    "alarm_edit_challenge_chain",
    "alarm_edit_snooze_limit_unlimited"
  ],
  "ui/alarmedit/AlarmEditSoundSections.kt": [
    "music_library_title",
    "alarm_music_settings_playlist_smart_all",
    "replay_action_stop",
    "alarm_edit_delivery_test_limitation",
    "b2_test_cancel",
    "b2_test_saved_only",
    "alarm_edit_heartbeat",
    "alarm_edit_immediately_default"
  ],
  "ui/ringtone/MusicPoolDialog.kt": [
    "music_library_pool_source_label",
    "music_library_pool_all_candidates",
    "music_library_close"
  ],
  "ui/ringtone/MusicLibrarySourcesDialog.kt": [
    "music_library_add_folder",
    "music_library_close",
    "music_library_source_unknown_origin",
    "music_library_remove_source"
  ],
  "ui/ringtone/RingtonePickerSheet.kt": [
    "ringtone_preview_unavailable",
    "ringtone_folder"
  ],
  "ui/stats/StatsScreen.kt": [
    "stats_track_consistency_snooze_behavior_and_which",
    "stats_snoozed",
    "stats_recent_history",
    "stats_recent_health_connect_sessions_stay_local",
    "stats_alarm_history_optional_health_connect",
    "stats_compares_recent_sleep_duration_with_dismiss"
  ],
  "ui/settings/SettingsPersonalizationSection.kt": [
    "settings_cover_to_snooze_description",
    "settings_long_press_threshold_description"
  ],
  "ui/settings/SettingsReadinessSections.kt": [
    "settings_export_incident_diagnostics",
    "settings_on_call_mode_description"
  ]
};
const catalog=JSON.parse(file("app/src/debug/assets/lp2/catalog.json"));
assert.equal(catalog.revision,"g6-200-current-20261120");
assert.equal(catalog.entries.length,200);
const source=new Map(catalog.entries.map(e=>[e.key,e]));
const resolver=file("app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/Lp2PreviewText.kt");
function keyset(name){
 const at=resolver.indexOf("val "+name+": Set<String> = setOf(");
 assert(at>=0,"Missing Kotlin allowlist "+name);
 const end=resolver.indexOf("\n    )",at);
 assert(end>at);
 return [...resolver.slice(at,end).matchAll(/"([a-z0-9_]+)"/g)].map(m=>m[1]);
}
const pilot=keyset("pilotKeys"),cross=keyset("crossScreenKeys"),extended=keyset("extendedKeys");
assert.equal(pilot.length,21);
assert.equal(cross.length,31);
assert.equal(extended.length,36);
const distinct=new Set([...pilot,...cross,...extended]);
assert.equal(distinct.size,87);
const allTarget=new Set(Object.values(specs).flat());
assert.equal(allTarget.size,40);
assert.deepEqual(extended.slice().sort(),[...allTarget].filter(k=>!new Set([...pilot,...cross]).has(k)).sort(),
 "Only G6 not-yet-covered keys may enter LP2-B2 extension");
let callsites=0;
const rx=/com\.sysadmindoc\.alarmclock\.ui\.settings\.Lp2PreviewText\.text\(\s*R\.string\.([a-z0-9_]+),\s*"([a-z0-9_]+)"\s*\)/g;
for(const [relative,keys] of Object.entries(specs)){
 const text=file("app/src/main/java/com/sysadmindoc/alarmclock/"+relative);
 const matches=[...text.matchAll(rx)];
 const seen=[...new Set(matches.map(m=>m[1]))];
 for(const match of matches)assert.equal(match[1],match[2],"Bad G6 stable key binding: "+relative);
 assert.deepEqual(seen.sort(),keys.slice().sort(),"Wrong preview bindings in "+relative);
 callsites+=matches.length;
}
assert.equal(callsites,49,"B2 expected 49 direct real UI callsites");
for(const key of distinct) {
 assert(source.has(key),"Not in G6 current catalog: "+key);
 assert.deepEqual(source.get(key).placeholders,[],"Needs formatting: "+key);
}
assert(resolver.includes("key !in previewKeys"),"B2 allowlist runtime gate removed");
assert(resolver.includes("BuildConfig.DEBUG"),"Debug-only gate missing");
const main=file("app/src/main/java/com/sysadmindoc/alarmclock/MainActivity.kt");
assert.equal([...main.matchAll(/override fun onResume\(\)/g)].length,1,"Alarm lifecycle must stay single");
assert(main.indexOf("AlarmService.activeAlarm.get()")<main.indexOf("Lp2PreviewText.refresh(this)"),
 "Alarm active check must precede any preview work");
const releaseMetadata=file("gradle/verification-metadata.xml");
assert.equal([...releaseMetadata.matchAll(/<also-trust value="/g)].length,2,
 "Only two independently audited Kotlin marker POM variants may be trusted");
console.log("LP2B2_PREVIEW_PASS "+JSON.stringify({
 surfaces:Object.keys(specs).length,expandedUniqueKeys:extended.length,
 previousUniqueKeys:51,totalUniqueKeys:distinct.size,newCallsites:callsites,
 officialReleaseApprovals:0
}));
