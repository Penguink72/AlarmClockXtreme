#!/usr/bin/env node
/**
 * L11-3 lexical disposition suggestions for missing locale definitions.
 * These are triage hints, NEVER a "leave English" or official approval decision.
 * No code file is modified and no translation is auto-released.
 */
const rawFormat=/^(?:%(?:[1-9]\d*\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]\s*)+$/;
const vendorOrUrl=/^(?:(?:https?|content|file):\/\/\S+|(?:github\.com|dontkillmyapp\.com)\/\S+|(?:[a-z0-9-]+\.)+(?:com|org|dev|net|io)(?:\/\S*)?|[A-Z_][A-Z0-9_.-]{2,}|[A-Z]{2}(?:,\s*[A-Z]{2})+(?:…)?|(?:[a-z]+:){1,2}[\w:/.-]+)$/;
const technicalKey=/\b(?:api|webhook|https|uri|ssid|oauth|hmac|nfc|json|xml|sku|device|http|qr|error|url)\b|(?:remove|delete|clear|dismiss|snooze|revoke|alarm|media|source|permission|notify)/i;

export function suggestDisposition(entry){
  if(entry.translation!==null)
    throw Error("Cannot dispose already drafted row "+entry.key+" / "+entry.locale);
  const s=entry.sourcePreview.trim(),info={
    key:entry.key,locale:entry.locale,sourceHash:entry.sourceHash,
    sourcePreview:entry.sourcePreview,priority:entry.priority,
    bindingCandidates:entry.bindingCandidates,
    surfaceHints:entry.surfaceHints,
    riskHint:technicalKey.test(entry.key+" "+s)?"HIGH_CONTEXT_OR_ACTION_REVIEW":"ROUTINE_REVIEW",
    candidateOnly:true,humanReviewRequired:true,
    disposition:"UNKNOWN",reason:"UNCLASSIFIED",decisionEvidence:"STATIC_HEURISTIC_ONLY",
    officialFallbackApproved:false,officialTranslationApproved:false
  };
  if(entry.bindingCandidates===0){
    info.disposition="UNKNOWN_MAPPING";
    info.reason="NO_STATIC_BINDING_CANNOT_PROVE_UNUSED";
  }else if(vendorOrUrl.test(s) || rawFormat.test(s)){
    info.disposition="INTENTIONAL_FALLBACK_CANDIDATE";
    info.reason="POSSIBLE_IDENTIFIER_BRAND_URL_OR_FORMAT_ONLY";
  }else if(/<[^>]+>|&(?:lt|gt|amp|quot|apos);|\\n|\\u[0-9a-fA-F]{4}/.test(s)){
    info.disposition="UNKNOWN_STRUCTURE_NEEDS_REVIEW";
    info.reason="FORMATTED_OR_ESCAPED_SOURCE_REQUIRES_STRUCTURAL_CHECK";
  }else if(/[A-Za-z]/.test(s)){
    info.disposition="SHOULD_TRANSLATE_CANDIDATE";
    info.reason="ENGLISH_NATURAL_LANGUAGE_WITH_LEXICAL_BINDING_HINT";
  }else{
    info.disposition="UNKNOWN_CONTENT";
    info.reason="NO_RELIABLE_NATURAL_LANGUAGE_EVIDENCE";
  }
  return info;
}

export function buildDispositionQueue(full,graph){
  if(full.schema!=="acx-l11-full-locale-candidate-queue/1" ||
     !Array.isArray(full.queue)) throw Error("Unexpected L11 input");
  if(graph.schema!=="acx-l10-source-context-binding-surface/1" ||
     full.catalogueRevision!==graph.catalogueRevision)
    throw Error("L10/L11 revision disagreement");
  const targets=full.queue.filter(row=>row.translation===null);
  const suggestions=targets.map(suggestDisposition);
  const byDisposition={};
  for(const row of suggestions)
    byDisposition[row.disposition]=(byDisposition[row.disposition]||0)+1;
  const excludedFromGap=graph.sources.filter(row=>row.translatable===false)
    .map(x=>({key:x.key,sourceHash:x.sourceHash,
      disposition:"NON_TRANSLATABLE_BY_SOURCE",
      authority:"ANDROID_XML_ATTRIBUTE_NO_NEW_REVIEW",
      localeRowsExcluded:2}));
  const stats={
    totalMissingLocaleDefinitions:full.queue.length,
    candidateLocaleRows:full.review.length,
    undecidedLocaleRows:suggestions.length,
    excludedNonTranslatableBaseKeys:excludedFromGap.length,
    byDisposition,
    humanReviewed:0,
    newApprovedFallbacks:0,
    androidXmlModified:false
  };
  if(full.queue.length!==full.review.length+suggestions.length)
    throw Error("Disposition denominator mismatch");
  if(Object.values(byDisposition).reduce((a,b)=>a+b,0)!==suggestions.length)
    throw Error("Unaccounted disposition rows");
  return {
    schema:"acx-l11-b3-source-disposition-suggestions/1",
    sourceSha:full.sourceSha,catalogueRevision:full.catalogueRevision,
    authority:"SOURCE_HEURISTIC_ONLY_NOT_HUMAN_REVIEW",
    warnings:[
      "No source-language/locale gap is automatically classified as officially approved fallback.",
      "Missing static binding does not imply unused or removable resource.",
      "SHOULD_TRANSLATE is an actionable hypothesis, not independent semantic approval.",
      "Pure URLs, technical codes, format-only strings can still require contextual translation.",
      "Runtime surface and interaction semantics have not been verified by this classifier."
    ],
    stats,suggestions,nonTranslatableBySource:excludedFromGap
  };
}

export function dispositionTsv(result){
  const columns=["disposition","reason","priority","key","locale","source_hash",
    "english_preview","binding_candidates","surface_hints","risk_hint","review_required"];
  return columns.join("\t")+"\n"+result.suggestions.map(x=>[
    x.disposition,x.reason,x.priority,x.key,x.locale,x.sourceHash,
    x.sourcePreview,x.bindingCandidates,x.surfaceHints.join(";"),
    x.riskHint,x.humanReviewRequired
  ].map(v=>JSON.stringify(v)).join("\t")).join("\n")+"\n";
}
