# TCL｜LP-G1 ～ LP-G5 全線切片（2026-11-19）

- Base: LP0 governance Draft PR #16, commit b17c6c9.
- Branch: feature/p31-lpg1-g5-governed-language-pipeline-20261119.
- Objective: translate using source truth → reviewed terms → contextual source-callsite evidence → isolated draft → independent review ledger → pack generator.
- All tooling is **Node.js built-ins only**, with no Python, no npm dependencies, and no changes to Room, AlarmManager, Media3 or production strings.

## G1 terminology contract
18 existing LP0 concepts retained, with candidate preferred labels in en/zh-TW/ja, action consequences, non-equivalence pairs and context-bound variants. No glossary is approved for release.

## G2 source-code lookup
Reads actual app/src/main and wear/src/main code, obtains literal R.string.KEY or XML @string/KEY callsites with paths and line numbers. No static callsite does not prove no runtime usage. Source location does not prove business semantics. Each Key remains NOT_REVIEWED.

## G3 isolated translation candidates
Original en 200; existing zh-TW and ja 155 each unchanged. Adds **21 missing keys per language** as unreviewed GPT-6 proposals (42 strings), while keeping each remaining 24 missing. Every row retains source hash, key, terminology and code evidence. Unknown keys, placeholders, duplicates and overwrites are rejected.

## G4 review gate
Mechanical validation and independent human semantic approval are distinct. No review ledger approvals by default. Ledger must bind reviewer, precise source/translation hash, context evidence digest, glossary revision, action correctness and approval rationale. A self-check cannot grant approval.

## G5 builder
- PREVIEW: Android LP1 JSON can import 176 source-derived plus model-draft entries per locale for **debug experimentation only**. Output folder and manifest explicitly say UNREVIEWED_PREVIEW_NOT_RELEASE.
- RELEASE: requires approved G1 glossary and valid G4 independent approval data; intentionally fails with current unreviewed baseline. This is a **release gate**, not a missing implementation.
- Pipeline CI uploads an inspectable review bundle, not a production release or silently merged app change.

## Next gate
Review term definitions and meanings of concrete source use, then record specific approvals and launch validated official pack builds. LP2 whole-app single/bilingual resolver is separate, not yet completed.

## Node commands
```sh
node scripts/localization/g1-validate-glossary.test.mjs
node scripts/localization/g2-index-context.mjs --out localization/governance/g2/context-evidence.json
node scripts/localization/g2-index-context.test.mjs
node scripts/localization/g3-build-candidates.mjs --out localization/governance/g3/review-queue.generated.json
node scripts/localization/g3-build-candidates.test.mjs
node scripts/localization/g4-review-gate.mjs --out localization/governance/g4/qa-report.generated.json
node scripts/localization/g4-review-gate.test.mjs
node scripts/localization/g5-build-language-packs.mjs --mode preview --out-dir build/localization/UNREVIEWED_PREVIEW_NOT_RELEASE
node scripts/localization/g5-build-language-packs.test.mjs
```
