#!/usr/bin/env node
/**
 * L12-2: code-backed operation probes for high-risk localization.
 * Bounded source evidence ONLY. No runtime proof or translation approval.
 */
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {createHash} from "node:crypto";
import {dirname,join,resolve} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";
import {execFileSync} from "node:child_process";
import {maskNonCode} from "./l10-source-binding-graph.mjs";
import {generateMatrix} from "./b0-t1-canonical-matrix.mjs";
import {buildL11,loadL11Inputs} from "./l11-translation-worklist.mjs";
import {buildL12} from "./l12-structural-semantic-evidence.mjs";
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const HASH=t=>"sha256:"+createHash("sha256").update(t,"utf8").digest("hex");
const P="app/src/main/java/com/sysadmindoc/alarmclock/";
export const PROBES=[
  {id:"BACKUP_APPEND_VS_REPLACE_UI",domain:"BACKUP_RESTORE",keys:["settings_append_alarms","settings_replace_alarms"],
   path:P+"ui/settings/SettingsBackupSections.kt",
   anchors:["onImport(BackupImportMode.Append)","onImport(BackupImportMode.Replace)"],
   claim:"UI exposes distinct Append and Replace actions",limit:"No proof of transaction completion or dialog layout at runtime"},
  {id:"BACKUP_REPLACE_MUTATIONS",domain:"BACKUP_RESTORE",keys:["settings_replace_alarms","settings_append_alarms","settings_keep_restored_disabled"],
   path:P+"data/backup/BackupManager.kt",
   anchors:["options.mode == BackupImportMode.Replace","repository.save(alarmToSave)","replacedIds.filterNot { it in savedIds }","repository.deleteById(id)","scheduler.schedule(alarm)"],
   claim:"Replace path preserves matching IDs and removes old unmatched entries after imports; enabled rows are scheduled",
   limit:"Not proof of atomic rollback, successful AlarmManager registration or full dataset restoration"},
  {id:"BACKUP_UNTRUSTED_INTEGRATIONS",domain:"SECURITY_PRIVACY",keys:["settings_keep_integrations_hint","settings_keep_integrations"],
   path:P+"data/backup/BackupManager.kt",
   anchors:["if (options.keepIntegrationsAndContacts)","withoutIntegrationsAndContacts(s)","guardianEnabled = false, guardianPhone ="],
   claim:"Import has an opt-in switch for integrations/contacts and clears per-alarm Guardian fields if false",
   limit:"Does not prove all device-specific or external-service data are inert"},
  {id:"BACKUP_ENCRYPTION",domain:"SECURITY_PRIVACY",keys:["settings_encrypted_backup_description","settings_plain_backup_description"],
   path:P+"data/backup/EncryptedBackupCodec.kt",
   anchors:["CIPHER_ALGORITHM = \"AES/GCM/NoPadding\"","Cipher.ENCRYPT_MODE","Cipher.DECRYPT_MODE"],
   claim:"Encrypted backup codec declares AES-GCM with explicit encrypt and decrypt paths",
   limit:"Not a cryptographic audit or proof that every exported field uses this codec"},
  {id:"ALARM_SNOOZE_BEFORE_AUDIO_STOP",domain:"ALARM_FIRING_CONTROL",keys:["stats_action_snoozed","alarm_edit_hardware_snooze"],
   path:P+"service/AlarmService.kt",
   anchors:["alarmScheduler.scheduleSnoozeAt(alarm, targetAt)","if (!registered)","saveSnoozedMusicPosition(alarm, previousFireId, targetAt)","stopAlarmPlayback()","recordEvent(alarm, AlarmEvent.ACTION_SNOOZED)"],
   claim:"Snooze attempts to arm next occurrence before stopping playback; a failed registration aborts",
   limit:"AUTO_DISMISS and overlapping alarms are separate branches; not all paths schedule another occurrence"},
  {id:"ALARM_DISMISS_EVENT",domain:"ALARM_FIRING_CONTROL",keys:["stats_action_dismissed","alarm_edit_dismiss_action"],
   path:P+"service/AlarmService.kt",
   anchors:["private suspend fun dismissAlarm(","action = AlarmEvent.ACTION_DISMISSED","event = WebhookEvent.AlarmDismissed","dismissActionExecutor.executeAsync(alarm)"],
   claim:"Normal dismiss branch records a dismiss event and starts webhook plus configured post-dismiss action",
   limit:"No guarantee of webhook delivery; production-test and missing-row branches differ"},
  {id:"MEDIA_SOURCE_REMOVE_STORE",domain:"MEDIA_SOURCE_PLAYBACK",keys:["music_library_remove_source"],
   path:P+"ui/ringtone/MusicLibrarySourceStore.kt",
   anchors:["fun remove(context: Context, sourceId: String)","write(context, sources.filterNot { it.id == sourceId })","releasePersistableUriPermission("],
   claim:"Source removal updates tracked sources and releases persisted read permission when no other source shares the tree URI",
   limit:"Source removal is not file deletion; other call paths and index effects need separate review"},
  {id:"MEDIA_SOURCE_REMOVE_VIEWMODEL",domain:"MEDIA_SOURCE_PLAYBACK",keys:["music_library_remove_source"],
   path:P+"ui/ringtone/MusicLibraryViewModel.kt",
   anchors:["fun removeSource(sourceId: String)","MusicTrackIndexReconciler.afterSourceRemoval(","repository.replaceTracks(retainedTracks)","MusicLibrarySourceStore.remove(context, sourceId)"],
   claim:"UI source removal reconciles library track indices and removes the saved source",
   limit:"A source removed from the index is not the same as a file deleted from device storage"},
  {id:"PLAYLIST_REMOVE_ITEMS",domain:"MEDIA_SOURCE_PLAYBACK",keys:["music_playlist_remove_selected"],
   path:P+"ui/ringtone/MusicPlaylistsWorkspace.kt",
   anchors:["val removedIds = selectedPlaylistItemIds.toList()","repo.removeItems(playlistId, removedIds)"],
   claim:"Playlist removal delegates selected playlist-item IDs to repository removal",
   limit:"Cannot infer media file deletion or exact repository transaction from UI alone"},
  {id:"WEBHOOK_EVENTS_AND_HTTPS",domain:"SECURITY_PRIVACY",keys:["settings_enable_webhook_description","settings_enable_webhook"],
   path:P+"service/WebhookService.kt",
   anchors:["AlarmFired(\"alarm_fired\")","AlarmSnoozed(\"alarm_snoozed\")","AlarmDismissed(\"alarm_dismissed\")",
     "AlarmMissed(\"alarm_missed\")","AlarmSkipped(\"alarm_skipped\")","fun isAllowedWebhookUrl(url: String): Boolean","return parsed.isHttps"],
   claim:"Webhook wire event names have fixed identifiers and configured URL validation accepts HTTPS only",
   limit:"Does not certify remote endpoint identity or guarantee delivery"},
  {id:"HUE_FIRST_USE_PINNING",domain:"SECURITY_PRIVACY",keys:["settings_hue_forget_message"],
   path:P+"integration/hue/HueTrustStore.kt",
   anchors:["suspend fun rememberFirstUse(observedFingerprint: String)","FINGERPRINT.matches(normalized)","hueBridgeCertFingerprint = normalized","HuePinResult.Changed(effective, normalized)"],
   claim:"Hue first use records fingerprint and detects subsequent fingerprint change",
   limit:"Forgetting an existing fingerprint requires separate settings-flow inspection; not certified by this probe"},
  {id:"STATISTICS_CLEAR_HISTORY_VM",domain:"DATA_LOSS_OR_RESTORE",keys:["stats_removes_recorded_alarm_outcomes_resets"],
   path:P+"ui/stats/StatsViewModel.kt",
   anchors:["fun clearHistory()","eventRepository.clearHistory()"],
   claim:"Statistics clear action delegates to alarm-event history repository",
   limit:"Does not claim every possible history table is cleared"},
  {id:"STATISTICS_HISTORY_DAO",domain:"DATA_LOSS_OR_RESTORE",keys:["stats_removes_recorded_alarm_outcomes_resets"],
   path:P+"data/repository/AlarmEventRepository.kt",
   anchors:["suspend fun clearHistory() = dao.deleteAll()"],
   claim:"Alarm-event repository clears event records via DAO deleteAll",
   limit:"DAO table and actual DB effects must be verified separately"},
  {id:"STATISTICS_EVENT_TABLE",domain:"DATA_LOSS_OR_RESTORE",keys:["stats_removes_recorded_alarm_outcomes_resets"],
   path:P+"data/local/AlarmEventDao.kt",
   anchors:["@Query(\"DELETE FROM alarm_events\")","suspend fun deleteAll()"],
   claim:"DAO clear operation targets alarm_events, not alarm definition table",
   limit:"Cannot rule out other side effects from callers without integration tests"}
];
function findLine(src,needle){
  const idx=src.indexOf(needle);if(idx<0)return null;
  return src.slice(0,idx).split("\n").length;
}
/** Supports in-memory mutation testing, and fails before emitting any approval. */
export function inspectBehavior(root,l12,matrix,probes=PROBES,reader=null){
  if(l12.schema!=="acx-l12-evidence-worklist/1"||
     matrix.schema!=="acx-b0-trilingual-canonical-matrix/1"||
     l12.catalogueRevision!==matrix.catalogueRevision)
    throw Error("L12-2 source versions disagree");
  const byKey=new Map(matrix.rows.map(x=>[x.key,x]));
  const byLocale=new Map(l12.records.map(x=>[x.key+"\0"+x.locale,x]));
  const sourceFiles=new Map(),cases=[],localeEvidence=[];
  for(const p of probes){
    if(!p.path.startsWith("app/src/main/java/")||p.path.includes("..")||
       !p.anchors?.length||!p.claim||!p.limit)throw Error("Invalid probe contract "+p.id);
    const raw=reader?reader(p.path):readFileSync(join(root,p.path),"utf8");
    if(typeof raw!=="string")throw Error("No Kotlin file "+p.path);
    const code=maskNonCode(raw),hits=[];
    for(const token of p.anchors){
      // Masking confirms evidence is executable source, not just a comment/string.
      const permitted=token.includes('"')?raw:code;
      const line=findLine(permitted,token);
      if(line===null)throw Error("Missing L12-2 source anchor "+p.id+" / "+token);
      hits.push({token,line});
    }
    for(let i=1;i<hits.length;i++){
      if(hits[i].line<hits[i-1].line)
        throw Error("Out-of-order source anchors "+p.id);
    }
    const fileRevision=HASH(raw);
    sourceFiles.set(p.path,fileRevision);
    const sourceKeys=[];
    for(const key of p.keys){
      const src=byKey.get("string:"+key);
      if(!src)throw Error("Probe targets unknown source key "+key);
      sourceKeys.push({key:"string:"+key,sourceHash:src.sourceHash,
        availableLocaleDrafts:["zh-TW","ja"].filter(locale=>byLocale.has("string:"+key+"\0"+locale))});
      for(const locale of ["zh-TW","ja"]){
        const parent=byLocale.get("string:"+key+"\0"+locale);
        if(!parent)continue;
        localeEvidence.push({
          probeId:p.id,key:"string:"+key,locale,
          sourceHash:parent.sourceHash,translationHash:parent.translationHash,
          reviewRevision:parent.reviewRevision,
          sourcePath:p.path,fileRevision,
          anchorLines:hits.map(x=>x.line),
          claim:p.claim,limitation:p.limit,
          evidenceStatus:"STATIC_CODE_PATH_EVIDENCE_ONLY",
          runtimeVerified:false,semanticCorrectnessApproved:false,
          independentApproval:false,officialReleaseEligible:false
        });
      }
    }
    cases.push({id:p.id,domain:p.domain,path:p.path,fileRevision,
      sourceKeys,anchorEvidence:hits,claim:p.claim,limitation:p.limit,
      evidenceStatus:"STATIC_CODE_PATH_EVIDENCE_ONLY",
      runtimeVerified:false,independentApproval:false});
  }
  const ids=new Set(localeEvidence.map(x=>x.probeId+"\0"+x.key+"\0"+x.locale));
  if(ids.size!==localeEvidence.length)throw Error("Duplicate localization/probe row");
  const totals={
    probes:cases.length,sourceFiles:sourceFiles.size,
    candidateLocaleEvidence:localeEvidence.length,
    independentSemanticApprovals:0,officialReleaseEligible:0,
    runtimeConfirmed:0,unresolvedBehaviorProof:cases.length
  };
  return {schema:"acx-l12-b2-operation-evidence/1",
    sourceSha:l12.sourceSha,catalogueRevision:l12.catalogueRevision,
    evidenceAuthority:"INSPECTED_KOTLIN_STATIC_CODE_ONLY",
    approvalState:"NONE",
    warnings:[
      "A confirmed source anchor is not Android runtime proof.",
      "A media source, alarm definition, playlist item and on-device media file are distinct objects.",
      "A translation may need correction even where the related code path is present.",
      "No model-authored artifact can create independent semantic approval or official locale XML."
    ],totals,cases,localeEvidence};
}
export function evidenceTsv(data){
  const columns=["probe","key","locale","source_hash","translation_hash","review_revision",
    "path","file_revision","lines","static_claim","limitation","semantic_approval"];
  return columns.join("\t")+"\n"+data.localeEvidence.map(x=>[
    x.probeId,x.key,x.locale,x.sourceHash,x.translationHash,x.reviewRevision,
    x.sourcePath,x.fileRevision,x.anchorLines.join(","),x.claim,x.limitation,x.independentApproval
  ].map(v=>JSON.stringify(v??"")).join("\t")).join("\n")+"\n";
}
export function evidenceSummary(d){
  return ["# L12-2｜程式行為對應探針（非 Runtime/語義批准）",
    "","- Source SHA: "+d.sourceSha,
    "- Verified static code-path probe contracts: "+d.totals.probes,
    "- Candidate locale rows with anchored source evidence: "+d.totals.candidateLocaleEvidence,
    "- Independent approvals: 0; official release eligible: 0",
    "","## Proof boundaries",
    ...d.cases.map(x=>"- "+x.id+" ("+x.path+") : "+x.claim+"; LIMIT: "+x.limitation),
    "","Code anchor revisions and each candidate's source/translation/review hashes are in JSON/TSV.",
    ""].join("\n");
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  let sha="unavailable";
  try{sha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}catch{}
  const a=process.argv.indexOf("--out-dir"),
    out=a>=0?resolve(process.argv[a+1]??""):join(ROOT,"build/localization/L12_B2_CODE_PROBES_NOT_APPROVED");
  if(!out.startsWith(ROOT+"/")&&!out.startsWith(ROOT+"\\"))
    throw Error("Output must stay under repository");
  const input=loadL11Inputs(ROOT,sha),l11=buildL11(input),matrix=generateMatrix(ROOT);
  const glossary=JSON.parse(readFileSync(join(ROOT,"localization/governance/g1/glossary-contract.json"),"utf8"));
  const l12=buildL12({l11,graph:input.graph,matrix,glossary});
  const report=inspectBehavior(ROOT,l12,matrix);
  mkdirSync(out,{recursive:true});
  writeFileSync(join(out,"l12-b2-operation-evidence.json"),JSON.stringify(report,null,2)+"\n");
  writeFileSync(join(out,"l12-b2-operation-evidence.tsv"),evidenceTsv(report));
  writeFileSync(join(out,"l12-b2-evidence-summary.md"),evidenceSummary(report));
  console.log("L12_B2_CODE_BEHAVIOR_PROBES "+JSON.stringify(report.totals));
}
