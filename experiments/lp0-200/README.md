# LP0-200 | 200-string language pack experiment

The source is the AlarmClockXtreme PR #13 version at commit c4dd6353f3ae5f95c9a2667599f294920eaa9619.

This is an independent browser proof-of-concept, NOT integrated into the installed Android app, and NOT an integration of the Tolgee/Crowdin SDK.

Contents:
- catalog.json: 200 unique original English Android string keys, printf placeholders and functional sections.
- packs/en.json: 200/200 actual English strings from Android resources.
- packs/zh-TW.json: 155/200 existing Traditional Chinese translations.
- packs/ja.json: 155/200 existing Japanese translations.
- The 45 missing translations per localized pack deliberately use English fallback. Never pretend an untranslated string is fully localized.
- web/index.html, web/catalog-data.js, web/probe.js: offline, self-contained interactive text preview, edit, pack import/export, new locale, single/dual mode.
- verify-resources.test.js: Node.js built-in-only resource fidelity test (no dependencies); probe-core.test.js tests pack contract, fallback and invalid imports.

## Running

1. Clone/download the branch with all files in this directory.
2. Open web/index.html directly in a browser. No HTTP server and no account are required.
3. Switch single/dual mode and primary/secondary locale; filter by category and untranslated strings.
4. Add a blank locale such as ko-KR, translate one key, and export it as JSON.
5. Import that same JSON back; the translated key must update immediately while remaining 199 keys use English fallback.
6. Test invalid input by injecting unknown keys, incorrect source catalog revision or missing printf parameters, which must be rejected atomically.

From repository root, run:

    node experiments/lp0-200/verify-resources.test.js
    node --check experiments/lp0-200/web/probe.js
    node experiments/lp0-200/probe-core.test.js

Imported files must be JSON smaller than 256 KiB and contain data only. The browser prototype does not send data to a network, and editing is ephemeral until explicit export.

Scope limitations:
- No Android Compose screen, Android notification locale, persistent language settings, ZIP installation, OTA, or runtime download.
- No completed 200-item Korean pack or fabricated translations.
- No ICU plural/resource markup handling, RTL, signed packages, release channels, or official security audit.
- The versioned format acx-language-pack-probe/1 is an experimental contract, not a promised public interface.
- Future LP1 should evaluate existing SDK compatibility separately; never add an unverified dependency into the alarm service.
