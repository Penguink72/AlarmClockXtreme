# P3.1-LP0 | 200 real-string localization pack probe

Date: 2026-11-18
Branch: feature/p31-lp0-200-language-pack-probe-20261118
Base: c4dd6353 (PR #13 Draft)
Production impact: none; no changes to main, App, Room, AlarmManager, Media3 or recorded histories.

T0: data contract and six-surface sample, 200 source strings from actual Android resources; 155 existing zh-TW and 155 ja translations and 45 explicit fallback candidates each.
T1: standalone JSON pack data, offline browser editing/import/export and bilingual preview committed.
T2: source-vs-JSON, placeholder, fallback, invalid input and embedded browser data checks in dedicated GitHub Action.
T3: browser demonstration only. Real Android arbitrary language packs, single-/dual-language Compose and alarm-safe runtime changes are NOT implemented.

Decision gate:
- Offline browser accepts a one-translation ko-KR JSON and displays it instantly alongside another locale, with 199 fallback strings.
- Wrong printf placeholder, unknown key or catalog revision rejects entire pack without replacing the active one.
- LP1 then examines Tolgee Android SDK / self-hosted adapter compatibility; vendor use remains undecided until tested.
- Keep the user's v160 real alarm ring/playback-history proof as baseline, separate from this localization experiment.
