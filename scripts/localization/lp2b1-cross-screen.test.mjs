#!/usr/bin/env node
/* LP2-B1: guard the audited Debug-only cross-screen language preview scope. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname,join,resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const file=p=>readFileSync(join(root,p),"utf8");
const cat=JSON.parse(file("app/src/debug/assets/lp2/catalog.json"));
assert.equal(cat.revision,"g6-200-current-20261120");
assert.equal(cat.entries.length,200);
const byKey=new Map(cat.entries.map(e=>[e.key,e]));
const modules={
 "ui/alarmlist/AlarmListScreen.kt":["settings_close","alarm_list_clear_filters","alarm_edit_disabled","alarm_list_delete","settings_ready","alarm_list_pause","settings_not_selected"],
 "ui/alarmedit/AlarmEditScreen.kt":["alarm_edit_background_selected","alarm_edit_background_permission_warning","alarm_edit_saving"],
 "ui/ringtone/MusicLibraryDialog.kt":["music_library_title","music_library_history_title","music_library_system_tones_cancel","music_library_category_music","music_library_source_scope_all","music_library_pool_all_candidates","music_library_pool_status_included","music_library_history_filter_recent","music_library_no_history"],
 "ui/components/HistoryReplayUi.kt":["recent_playback_title","recent_playback_empty","replay_status_playing","replay_action_pause","replay_open_in_external"],
 "ui/alarmedit/AlarmMusicSettingsScreen.kt":["alarm_music_settings_playlist_smart_all","u4_snooze_mode_note"],
 "ui/ringtone/MusicPlaylistsWorkspace.kt":["music_playlist_integration_pending","music_playlist_apply_to_alarm","music_playlist_empty","music_playlist_rename","music_playlist_remove"]
};
const rx=/Lp2PreviewText\.text\(R\.string\.([A-Za-z0-9_]+), "([A-Za-z0-9_]+)"\)/g;
let totalCallsites=0;const all=[];
for(const [suffix,keys] of Object.entries(modules)){
 const source=file("app/src/main/java/com/sysadmindoc/alarmclock/"+suffix);
 assert(source.includes("import com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText"),
    "Missing controlled resolver import: "+suffix);
 const matches=[...source.matchAll(rx)];
 const found=[...new Set(matches.map(x=>x[1]))];
 assert.deepEqual(found.slice().sort(),keys.slice().sort(),"Unexpected cross-screen bindings in "+suffix);
 for(const match of matches)assert.equal(match[1],match[2],"String resource mismatches its catalog key");
 for(const key of keys)assert.deepEqual(byKey.get(key)?.placeholders,[],"Formatted key cannot be previewed without args: "+key);
 totalCallsites+=matches.length;all.push(...found);
}
assert.equal(totalCallsites,42,"Cross-screen LP2-B1 text callsites changed unexpectedly");
assert.equal(all.length,31,"Cross-screen whitelist should contain 31 unique-per-file keys");
assert.equal(new Set(all).size,31,"Cross-screen whitelist contains duplicates");
const repo=file("app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/Lp2PreviewText.kt");
function extractSet(name){
 const start=repo.indexOf("val "+name+": Set<String> = setOf(");
 assert(start>=0,"Missing Kotlin "+name+" declaration");
 const end=repo.indexOf("\n    )",start);
 assert(end>start);
 const segment=repo.slice(start,end);
 return [...segment.matchAll(/"([a-z0-9_]+)"/g)].map(x=>x[1]);
}
const pilot=extractSet("pilotKeys"),cross=extractSet("crossScreenKeys");
assert.equal(pilot.length,21);
assert.equal(cross.length,31);
assert.deepEqual(cross.slice().sort(),all.slice().sort(),"Resolver allowlist does not match real cross-screen UI");
const union=new Set([...pilot,...cross]);
assert.equal(union.size,51,"21+31 should have exactly one overlap");
assert(union.has("settings_not_selected"));
assert(repo.includes("key !in previewKeys"),"Cross-screen UI must use guarded allowlist");
for(const key of union)assert.deepEqual(byKey.get(key)?.placeholders,[],"Unexpected placeholder key "+key);
assert(repo.includes("BuildConfig.DEBUG"),"Release isolation missing");
const main=file("app/src/main/java/com/sysadmindoc/alarmclock/MainActivity.kt");
assert(main.includes("if (BuildConfig.DEBUG) Lp2PreviewText.refresh(this)"),
  "Preview must refresh on MainActivity resume, including after a cold restart");
const corrected=JSON.parse(file("localization/governance/s0/source-amendments.json")).items[0];
const packs=["en","zh-TW","ja"].map(locale=>JSON.parse(file("app/src/debug/assets/lp2/packs/"+locale+".json")));
for(const pack of packs)assert.equal(pack.catalogRevision,cat.revision);
assert.equal(packs[0].translations[corrected.key],corrected.currentSource);
for(const [i,locale] of ["zh-TW","ja"].entries())
 assert.equal(packs[i+1].translations[corrected.key],corrected.locales[locale].currentCandidate);
const changed=structuredClone(modules);
changed["ui/components/HistoryReplayUi.kt"][0]="invented_key";
assert.notDeepEqual(Object.values(changed).flat().sort(),all.slice().sort());
console.log("LP2B1_CROSS_SCREEN_PASS "+JSON.stringify({
 files:Object.keys(modules).length,liveCallsites:totalCallsites,
 newCrossScreenKeys:31,distinctPreviewKeys:union.size,
 humanApproved:0,releaseUnchanged:true
}));
