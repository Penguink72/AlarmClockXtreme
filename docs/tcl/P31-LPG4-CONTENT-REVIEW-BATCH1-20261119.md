# P3.1 TCL｜LP-G 內容審核兩批狀態（2026-11-19）

## 根目標
為 AlarmClockXtreme 建立不受模型、語言包格式或單一詞庫影響的逐 Key 翻譯品質治理。先從既有 200 個真實字串做內容審核，不讓不可靠用詞直接進入正式語言包。

## 基底與分支
- Parent: PR #17 / `feature/p31-lpg1-g5-governed-language-pipeline-20261119`
- This worktree: `feature/p31-lpg-content-review-b1-20261119`
- Current translation source: LP0 200 strings, en=200, zh-TW=155, ja=155, source catalog unchanged
- Immutable product boundary: no changes to `app/`, Room, alarm playback, alarm scheduling, or `main`.

## Branches of work
| TCL | Work | Evidence/status |
|---|---|---|
| C1 | Read 18 glossary concepts | all 18 reviewed as model-assisted candidate opinions; 1 proposed Japanese Ringtone label changed from 着信音 to アラーム音 (candidate only) |
| C2 | Validate actual Kotlin callsite contexts | 21 missing bilingual draft keys mapped to exact source file/line; 9 incorrect heuristic concept links identified and marked as refutations |
| C3 | Inspect 21 new G3 draft keys, zh-TW+ja | 42 candidate texts reviewed; nine keys revised in G3 unreviewed draft file |
| C4 | Triage 52 LP0 high-priority keys | 6 existing translation corrections proposed, 9 context warnings, 8 absent both locale translations; pre-existing packs untouched |
| C5 | Run governance and release safety tests | Node-based CI checks G1/G2/G3/G4/G5, model review provenance, code-line evidence; reject official release without independent approval |
| C6 | Real independent approval | 0 approved terms, 0 approved translation rows; pending |
| C7 | Formal language pack release and LP2 Android UI | Not authorized by model review, intentionally blocked |

## Structural distinctions
1. `MODEL_CONTEXT_CHECK_DONE_HUMAN_PENDING` does not mean an independently reviewed translation.
2. `REFUTE_AUTOMATIC_CONCEPT_LINK` proves an identified matching heuristic is unsuitable (e.g. saved place is not Save), but the original LP0 candidate links remain preserved for audit. A future G1/G2 migration should explicitly reconcile them instead of silently overwriting evidence.
3. New G3 candidates may change only while marked DRAFT; current `review-ledger.json` has no approvals.
4. The 6 older-translation corrections remain in the suggestion ledger, **not** in original localization resource XML or LP0 packs.
5. G5 preview packages explicitly declare unreviewed status, and G5 release mode must continue to reject them.

## Material
- `localization/governance/g4/content-review-batch1.json`
- `localization/governance/g4/legacy-high-priority-triage.json`
- `docs/localization/LP-G4-CONTENT-REVIEW-BATCH1-20261119.md`
- `docs/localization/LP-G4-LEGACY-HIGH-PRIORITY-REVIEW-20261119.md`
- `scripts/localization/g4-content-review-audit.mjs` (+ negative tests)
- `scripts/localization/g4-legacy-priority-audit.mjs` (+ negative tests)

## Next gate
- G1: independent review of 18 concept meanings and context-specific wording variants; address the nine refuted heuristic links in the actual concept-binding data, without modifying source evidence.
- G2: inspect the five no-static-reference keys and the actual alarm action semantics for Snooze/Dismiss/Stop/Delete.
- G3: review remaining 24 missing keys per language and all remaining legacy translations; preserve per-key source hash, evidence and translator.
- G4: only then create signed-off independent human approval records.
- G5: verify complete desired language coverage before calling any official language pack fully localized.

No local Python is required. All automated checks are Node.js built-ins and GitHub Actions.
