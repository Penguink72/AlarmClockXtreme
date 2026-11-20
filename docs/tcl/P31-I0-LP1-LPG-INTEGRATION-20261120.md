# TCL｜I0 LP1 + LP-G integration baseline (2026-11-20)

## Scope and source of truth
- Scope: I0 only; do not perform S0 canonical English correction, G6 approvals, LP2 runtime UI replacement, or any main-branch merge here.
- From: PR #15 LP1 Android Debug Language Lab, SHA 9f85cc6efe07c83b6485891ac0eb02b6c6a2ca8f.
- From: PR #19 LP-G1–G5 governance and review, SHA 379938dcc26af57b3e75de3e3b7f257b3cceb60a.
- Common ancestor: PR #14 SHA 1d13e7070f26a84e2aa19a7698d19de8a4269ff2.
- Integration commit f11cf7fe892e773993c16188a67ea70025126616 has two parents, retaining both lines and adding the 16 LP1-only paths on the LP-G base, without overlapping file paths.
- Integration branch: integration/p31-i0-lp1-lpg-20261120; PR remains Draft; main remains unchanged.

## Delivery
1. LP1 debug-only Android Activity, JSON OpenDocument import/export, atomic readback, single/dual preview, 200-key assets and Kotlin Robolectric tests now coexist with LP-G glossary, source/context ledgers, candidate translations, audit and G5 pack generator.
2. Original LP0 English catalog and LP1 Debug catalog remain byte-equivalent; I0 cross-check enforces equality.
3. New Node-only bridge test runs on the G5-generated unreviewed preview JSON, verifies LP1 data contract, rejects stale revision, unknown key and printf mismatch, and checks 256-KiB bound. The G5 manifest must say preview/not official; the open source defect must not be marked resolved.
4. Workflows are activated on the integration branch for LP1 Node parity, LP-G Node pipeline plus I0 bridge, and the existing strict Android debug APK/unit tests/signing baseline. Record actual run SHAs rather than claiming success at commit time.
5. G5 generated preview zh-TW/ja JSON are intended for *manual* Debug Lab OpenDocument import; no automatic official locale replacement or release distribution is enabled.

## Remaining gates
- T1: source merged in an isolated integration branch.
- T2: pending exact merged source SHA completion of LP1 parity, LP-G/I0 bridge, Android strict build/unit/second-runner signing. A green prior PR result is not a substitute.
- T3: phone actual JSON import and post-restart bilingual rendering not yet verified.
- Semantic approvals: 0. Source defect: music_playlist_integration_pending OPEN, release-blocking. Official G5 release must remain denied.
- G5 preview import data compatibility in Node and existing LP1 Kotlin parser unit tests are mechanical evidence, not proof of device import.
- Excluded from I0: Room changes, AlarmManager, AlarmService, Media3, playback history, normal runtime stringResource/UI replacement.

## I0 merge-source evidence line rebase
The LP1 Settings home entry adds 22 lines within SettingsScreen.kt. The first LP-G run on merged SHA a03b942e failed at G4b because static sourceEvidence.line references pointed to pre-LP1 code positions. Each changed reference is rebased ONLY if the current SettingsScreen.kt line contains the exact original R.string.<key> token at oldLine+22; 11 references across four G4 review JSON files satisfy this condition. One other line in the same file (line 204) is unchanged and must not be shifted.
No source strings, translations, terminology, human approval counts, or source hashes are changed. Re-run G2→G5 on the integrated SHA; record fresh CI status. The opening failure is evidence-location drift, not evidence of semantic translation approval.
