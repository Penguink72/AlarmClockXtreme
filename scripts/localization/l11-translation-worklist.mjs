#!/usr/bin/env node
/*
 * L11: create an auditable translation worklist and priority preview drafts
 * from L10/B0-T1 and G6 without modifying Android strings or approving packs.
 */
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { execFileSync } from "node:child_process";
import { buildL10 } from "./l10-source-binding-graph.mjs";
import { buildDispositionQueue,dispositionTsv } from "./l11-b3-source-dispositions.mjs";

const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const LC=["zh-TW","ja"];
const fmt=/%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]/g;
const norm=s=>s.replace(/\s+/g," ").trim();
const sorted=a=>a.slice().sort((a,b)=>a.localeCompare(b,"en"));

export function verifyDraft(row,draft){
  if(draft.expectedEnglishPreview!==row.sourcePreview)
    throw Error("L11 source drift for "+row.key+"; never silently reuse translation");
  if(!row.translatable)throw Error("L11 draft targeted nontranslatable "+row.key);
  const expected=sorted([...new Set(row.sourceFormatTokens)]);
  for(const locale of LC){
    if(row.locales[locale].hasDedicatedDefinition)
      throw Error("L11 draft would overwrite existing Android locale "+row.key+" / "+locale);
    const target=draft.translations[locale];
    if(typeof target!=="string" || !target.trim())throw Error("Missing "+locale+" draft "+row.key);
    const actual=sorted([...new Set(target.match(fmt)||[])]);
    if(JSON.stringify(actual)!==JSON.stringify(expected))
      throw Error("L11 format tokens disagree: "+row.key+" / "+locale+
        " expected "+expected.join(",")+" got "+actual.join(","));
  }
  return true;
}

function priority(key){
  if(/^onboarding_/.test(key))return "P0_ONBOARDING";
  if(/^(permissions_|settings_wake_|settings_battery_|settings_exact_alarm_access|settings_on_call_)/.test(key))
    return "P0_READINESS_PERMISSION";
  if(/^(settings_webhook_|settings_connection_|settings_connections_|settings_holiday_|alarm_edit_holidays_hint)/.test(key))
    return "P0_INTEGRATIONS";
  if(/^(alarm_|firing_|music_|ringtone_|settings_|notif_)/.test(key))
    return "P1_CORE_OR_SETTINGS";
  return "P2_LONG_TAIL";
}
function scopeDisposition(row){
  if(!row.translatable) return "NON_TRANSLATABLE_BY_SOURCE";
  if(/(?:^https?:\/\/|^[\w.-]+\/[\w./-]+$)/.test(row.sourcePreview))
    return "INTENTIONAL_FALLBACK_CANDIDATE_NEEDS_REVIEW";
  return "SHOULD_TRANSLATE_CANDIDATE_NEEDS_REVIEW";
}
function validatePack(catalog,packs){
  if(catalog.revision!=="g6-200-current-20261120" || catalog.entries.length!==200)
    throw Error("Unexpected G6 catalog version; refuse to silently rebind");
  const keys=new Set(catalog.entries.map(e=>e.key));
  if(keys.size!==200)throw Error("G6 duplicate catalog key");
  for(const locale of LC){
    const pack=packs[locale];
    if(pack.catalogRevision!==catalog.revision ||
       pack.locale!==locale || pack.format!=="acx-language-pack-probe/1")
      throw Error("G6 pack contract mismatch "+locale);
  }
}

export function buildL11({graph,catalog,packs,curated,batch2=[],batch3=[]}){
  if(curated.schema!=="acx-l11-priority-translation-drafts/1" ||
     curated.independentApproval!==false)throw Error("Untrusted curated draft schema/authority");
  validatePack(catalog,packs);
  for(const pack of batch2){
    if(pack.schema!=="acx-l11-batch2-drafts/1" ||
       pack.independentApproval!==false ||
       pack.status!=="MODEL_PRELIMINARY_NOT_APPROVED" ||
       !Array.isArray(pack.records))
      throw Error("Untrusted L11-B2 draft batch schema/authority");
  }
  for(const pack of batch3){
    if(pack.schema!=="acx-l11-batch3-drafts/1" ||
       pack.independentApproval!==false ||
       pack.status!=="MODEL_PRELIMINARY_NOT_APPROVED" ||
       !Array.isArray(pack.records))
      throw Error("Untrusted L11-B3 draft batch schema/authority");
  }
  const base=new Map(graph.sources.map(row=>[row.key,row]));
  const g6=new Map(catalog.entries.map(x=>["string:"+x.key,x]));
  const authored=new Map();
  const drafts=[
    ...curated.records.map(r=>({...r,batchOrigin:"L11_MODEL_DRAFT_UNREVIEWED"})),
    ...batch2.flatMap(pack=>pack.records.map(r=>({...r,
      batchOrigin:"L11_B2_MODEL_DRAFT_UNREVIEWED"}))),
    ...batch3.flatMap(pack=>pack.records.map(r=>({...r,
      batchOrigin:"L11_B3_MODEL_DRAFT_UNREVIEWED"})))
  ];
  for(const r of drafts){
    const key="string:"+r.key;
    if(authored.has(key))throw Error("Duplicate curated draft: "+key);
    const src=base.get(key);
    if(!src)throw Error("Curated source missing: "+key);
    verifyDraft(src,r);
    authored.set(key,r);
  }
  const queue=[],sourceDrift=[],review=[];
  const summary={
    baseKeys:graph.summary.sourceKeys,translatableKeys:graph.summary.translatableKeys,
    missingByLocale:{"zh-TW":0,ja:0},
    curatedKeys:curated.records.length,curatedLocaleRows:0,
    batch2Keys:batch2.reduce((n,x)=>n+x.records.length,0),batch2LocaleRows:0,
    batch3Keys:batch3.reduce((n,x)=>n+x.records.length,0),batch3LocaleRows:0,
    reusedG6LocaleRows:0,existingLocaleProtected:0,
    sourceDriftHolds:0,untranslatedLocaleRows:0,
    officialApproved:0,androidXmlModified:false
  };
  for(const row of graph.sources){
    for(const locale of LC){
      const existing=row.locales[locale].hasDedicatedDefinition;
      if(existing){summary.existingLocaleProtected++;continue;}
      if(!row.translatable)continue;
      summary.missingByLocale[locale]++;
      const record={key:row.key,locale,sourcePreview:row.sourcePreview,
        sourceHash:row.sourceHash,sourceFormatTokens:row.sourceFormatTokens,
        targetExists:false,bindingCandidates:row.bindingCount,
        surfaceHints:sorted([...new Set(row.bindings.map(b=>b.surfaceHint))]),
        priority:priority(row.key),dispositionCandidate:scopeDisposition(row),
        contextEvidence:row.contextEvidence,
        actionSemantic:"NOT_VERIFIED_BY_L11",reviewStatus:"UNREVIEWED",
        humanApproval:false,independentApproval:false,
        translation:null,translationOrigin:"NONE",candidateState:"MISSING",
        g6OldCandidate:null,reviewRisk:"UNASSESSED"};
      const candidate=authored.get(row.key);
      const old=g6.get(row.key);
      if(candidate){
        record.translation=candidate.translations[locale];
        record.translationOrigin=candidate.batchOrigin;
        record.reviewRisk=candidate.reviewRisk??"UNASSESSED";
        record.candidateState="DRAFT_NEEDS_SEMANTIC_REVIEW";
        if(candidate.batchOrigin==="L11_B3_MODEL_DRAFT_UNREVIEWED")
          summary.batch3LocaleRows++;
        else if(candidate.batchOrigin==="L11_B2_MODEL_DRAFT_UNREVIEWED")
          summary.batch2LocaleRows++;
        else summary.curatedLocaleRows++;
        if(old && norm(old.source)===norm(row.sourcePreview)){
          record.g6OldCandidate=packs[locale].translations[old.key]||null;
          if(record.g6OldCandidate && record.g6OldCandidate!==record.translation)
            record.candidateState="MULTIPLE_UNREVIEWED_DRAFTS_REVIEW_REQUIRED";
        }
      } else if(old){
        const oldText=packs[locale].translations[old.key];
        if(norm(old.source)!==norm(row.sourcePreview)){
          record.candidateState="SOURCE_DRIFT_HOLD";
          record.translationOrigin="G6_REUSE_REFUSED_SOURCE_MISMATCH";
          summary.sourceDriftHolds++;
          sourceDrift.push({key:row.key,locale,g6Source:old.source,
            currentSource:row.sourcePreview,sourceHash:row.sourceHash});
        } else if(typeof oldText!=="string" || !oldText.trim()){
          record.candidateState="G6_CANDIDATE_ABSENT";
          summary.untranslatedLocaleRows++;
        } else if(JSON.stringify(sorted([...new Set(oldText.match(fmt)||[])])) !==
          JSON.stringify(sorted([...new Set(row.sourceFormatTokens)]))){
          record.candidateState="G6_FORMAT_HOLD";
          record.translationOrigin="G6_REUSE_REFUSED_FORMAT_MISMATCH";
          summary.sourceDriftHolds++;
          sourceDrift.push({key:row.key,locale,reason:"FORMAT_MISMATCH"});
        } else {
          record.translation=oldText;
          record.translationOrigin="G6_DEBUG_MODEL_CANDIDATE_REUSED_UNREVIEWED";
          record.candidateState="DRAFT_NEEDS_SEMANTIC_REVIEW";
          summary.reusedG6LocaleRows++;
        }
      } else {
        summary.untranslatedLocaleRows++;
      }
      queue.push(record);
      if(record.translation!==null)review.push(record);
    }
  }
  queue.sort((a,b)=>a.priority.localeCompare(b.priority,"en")||
    a.key.localeCompare(b.key,"en")||a.locale.localeCompare(b.locale,"en"));
  review.sort((a,b)=>a.priority.localeCompare(b.priority,"en")||
    a.key.localeCompare(b.key,"en")||a.locale.localeCompare(b.locale,"en"));
  return {
    schema:"acx-l11-full-locale-candidate-queue/1",
    sourceSha:graph.sourceSha,catalogueRevision:graph.catalogueRevision,
    status:"DRAFT_ONLY_NOT_OFFICIAL_OR_REVIEWED",
    authority:"B0-T1 source + L10 lexical candidate graph + G6 versioned preliminary pack",
    summary,queue,review,sourceDrift,
    constraints:[
      "Existing Android XML is never overwritten by these candidates.",
      "G6 content is reused only when exact normalized English source matches and format tokens match.",
      "Both curated and reused G6 drafts remain unreviewed regardless of tests passing.",
      "Priority labels are filename/key heuristics, not proof of actual screen reachability.",
      "Source and locale values are not edited; output is a review queue only."
    ]
  };
}

const escape=value=>JSON.stringify(value??"");
export function draftTsv(data){
  const headers=["priority","key","locale","source_hash","source_preview",
    "candidate_text","origin","candidate_state","binding_candidates",
    "surface_hints","format_tokens","g6_old_candidate","review_status"];
  const lines=data.review.map(x=>[
    x.priority,x.key,x.locale,x.sourceHash,x.sourcePreview,
    x.translation,x.translationOrigin,x.candidateState,
    x.bindingCandidates,x.surfaceHints.join(";"),
    x.sourceFormatTokens.join(";"),x.g6OldCandidate,x.reviewStatus
  ].map(escape).join("\t"));
  return headers.join("\t")+"\n"+lines.join("\n")+"\n";
}

export function summaryMd(data){
  const s=data.summary;
  return [
    "# L11｜繁中／日文完整缺漏分類與多批次未核准譯文候選",
    "",
    "> L11 drafts do not modify official Android XML, G4/G5 approval, or App runtime.",
    "",
    "- Source SHA: "+data.sourceSha,
    "- Catalog revision: "+data.catalogueRevision,
    "- Base keys: "+s.baseKeys+"; translatable "+s.translatableKeys,
    "- Missing dedicated XML: zh-TW "+s.missingByLocale["zh-TW"]+"; ja "+s.missingByLocale.ja,
    "- New source-pinned curated rows: "+s.curatedLocaleRows+" ("+s.curatedKeys+" keys)",
    "- L11-B2 source-pinned unreviewed rows: "+s.batch2LocaleRows+" ("+s.batch2Keys+" keys)",
    "- L11-B3 source-pinned unreviewed rows: "+s.batch3LocaleRows+" ("+s.batch3Keys+" keys)",
    "- Reused existing G6 unreviewed rows: "+s.reusedG6LocaleRows,
    "- G6 source/format mismatches on proposed reuse: "+s.sourceDriftHolds,
    "- Remaining untranslated locale rows: "+s.untranslatedLocaleRows,
    "- Existing locale definitions protected: "+s.existingLocaleProtected,
    "- Human/independent approvals granted: "+s.officialApproved,
    "",
    "## Next gates",
    "",
    "1. Review all action and security sensitive drafts against real Kotlin/Compose behavior and G1 glossary.",
    "2. Classify untranslated rows as SHOULD_TRANSLATE / INTENTIONAL_FALLBACK / NON_TRANSLATABLE / UNKNOWN.",
    "3. Confirm format, XML escapes, plurals, arrays and string length before generating strings.xml.",
    "4. Separate L13 runtime wiring and L14 Android verification from the L11 translation draft state.",
    "",
    "Artifacts: l11-full-translation-queue.json, l11-priority-review.tsv, l11-source-drift-holds.json, l11-summary.md",
    ""
  ].join("\n");
}

if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const at=process.argv.indexOf("--out-dir");
  const out=at<0?join(ROOT,"build/localization/B0_FULL_APP_INVENTORY"):
    resolve(process.argv[at+1]??"");
  if(!out.startsWith(ROOT+(process.platform==="win32"?"\\":"/")))
    throw Error("Output dir must stay inside repo");
  let sourceSha="unavailable";
  try{sourceSha=execFileSync("git",["rev-parse","HEAD"],{cwd:ROOT,encoding:"utf8"}).trim();}
  catch{/* Non-Git snapshot is kept explicitly unverified. */}
  const read=p=>JSON.parse(readFileSync(join(ROOT,p),"utf8"));
  const graph=buildL10(ROOT,sourceSha);
  const data=buildL11({
    graph,
    catalog:read("app/src/debug/assets/lp2/catalog.json"),
    packs:{"zh-TW":read("app/src/debug/assets/lp2/packs/zh-TW.json"),
      ja:read("app/src/debug/assets/lp2/packs/ja.json")},
    curated:read("localization/candidates/l11-priority-unreviewed-drafts-20261122.json"),
    batch2:[
      read("localization/candidates/l11-b2-settings-drafts-20261122.json"),
      read("localization/candidates/l11-b2-alarm-notification-drafts-20261122.json")
    ],
    batch3:[
      read("localization/candidates/l11-b3-music-alarm-drafts-20261122.json"),
      read("localization/candidates/l11-b3-firing-permissions-drafts-20261122.json")
    ]
  });
  mkdirSync(out,{recursive:true});
  const write=(name,item)=>writeFileSync(join(out,name),JSON.stringify(item,null,2)+"\n");
  write("l11-full-translation-queue.json",{
    schema:data.schema,sourceSha:data.sourceSha,catalogueRevision:data.catalogueRevision,
    status:data.status,authority:data.authority,summary:data.summary,queue:data.queue,
    constraints:data.constraints
  });
  write("l11-source-drift-holds.json",{
    sourceSha:data.sourceSha,sourceDrift:data.sourceDrift,
    reason:"Never reuse stale G6 drafts silently"
  });
  writeFileSync(join(out,"l11-priority-review.tsv"),draftTsv(data));
  const triage=buildDispositionQueue(data,graph);
  write("l11-b3-source-disposition-suggestions.json",triage);
  writeFileSync(join(out,"l11-b3-source-disposition-suggestions.tsv"),dispositionTsv(triage));
  writeFileSync(join(out,"l11-summary.md"),summaryMd(data)+[
    "",
    "## L11-3 candidate-only lexical disposition triage",
    "",
    "- Remaining untranslated locale rows: "+triage.stats.undecidedLocaleRows,
    ...Object.entries(triage.stats.byDisposition).sort((a,b)=>a[0].localeCompare(b[0],"en"))
      .map(([key,value])=>"- "+key+": "+value),
    "- Nontranslatable base keys outside missing locale denominator: "+triage.stats.excludedNonTranslatableBaseKeys,
    "- Approved fallbacks: 0. L11 cannot authorize official XML or review ledgers.",
    ""
  ].join("\n"));
  console.log("L11_TRANSLATION_DRAFTS "+JSON.stringify(data.summary));
  console.log("L11_B3_DISPOSITION_CANDIDATES "+JSON.stringify(triage.stats));
}
