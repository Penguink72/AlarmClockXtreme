# P3.1 LP0 Translation Governance Baseline (2026-11-19)

Source: PR #14 (LP0 200-key experiment), branch feature/p31-lp0-governance-200-baseline-20261119.
This new LP0 branch does not edit LP1 Debug runtime, main, Room, Media3 or AlarmService.

### T0 - baseline evidence
- Use existing 200 real keys in experiments/lp0-200/catalog.json.
- Cross-join pre-existing zh-TW/ja text without inventing translations or granting review.
- Preserve stable key and original text; record SHA256 of UTF8(key + NUL + source).
- Infer screen location from key prefix only, always mark UNVERIFIED.
- Draft candidate glossary: 18 concepts, 151 linked keys, 49 without candidates; all PROPOSED_NOT_APPROVED.
- Flag 52 keys for priority human review; not proven errors.
- All 155 existing zh-TW and 155 ja translations are UNREVIEWED_EXISTING, with 45 missing each; zero APPROVED.

### T1 - deliverables
- localization/governance/lp0/source-matrix.json (200 records)
- localization/governance/lp0/terminology-candidates.json (concept proposals)
- docs/localization/LP0-200-SOURCE-TRANSLATION-MATRIX-20261119.md (full reviewable table)
- docs/localization/LP0-TERMINOLOGY-POLICY-20261119.md (statuses and gates)
- docs/localization/LP0-200-AUDIT-FINDINGS-20261119.md (examples and diagnostics)
- scripts/localization/lp0-audit.mjs and lp0-audit.test.mjs (Node only)
- .github/workflows/lp0-governance-preflight.yml

### T2 and T3
T2: dedicated remote Node CI; PASS only if GitHub reports success.
T3: full semantic/functional context approval NOT attempted. LP0 may pass data integrity with zero approved translations; this is intentional and must not be reported as translation quality.

### Next
LP-G1 confirm concept meanings and permitted terms, then LP-G2 trace real UI and notification source call-sites, before LP-G3 LLM proposals or LP-G5 official packs.
