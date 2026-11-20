# P3.1-LP1 | Android offline 200-string language pack lab

Date: 2026-11-18. Base: LP0-200 PR #14 (Node-only success), not main.

## Implemented

- Debug-only Activity in app/src/debug. Entry in Settings home is hidden in release builds.
- 200 actual English source keys, 155 available Traditional Chinese, 155 Japanese in debug-only assets.
- Android Storage Access Framework OpenDocument JSON import / CreateDocument export, no remote account or network.
- Validate source catalog revision, 200 known keys, language tag, printf placeholders and 256 KiB maximum.
- Atomic local install in app private files folder. Restart preserves custom language packs.
- Sample Korean empty pack creation; per-key translation edit/save; primary/secondary locale; single/dual preview; English fallback visibly marked.
- No modifications to Room version, AlarmManager, AlarmService, foreground alarm screen, notifications or playback-history writing. Existing Android locale switch stays separate.
- No additional Gradle dependency required; Tolgee and Crowdin SDK remain future candidates rather than undocumented runtime assumptions.

## Evidence gates

- T0: scoped 200-key experiment, independent from user alarm safety.
- T1: debug-only Kotlin parser, asset bundles, Compose Lab Activity, translated Settings entry, Robolectric tests.
- T2: app F-Droid debug compile/unit tests, package and signing via workflow p0-android-baseline.yml; call this only PASS after actual run.
- T3: debug APK test on device still required before claiming Android language pack import works on user device.
- LP2: decide between local data-only adapter and OTA SDK, then integrate existing UI string resolution via explicit opt-in. No implicit restart of the alarm service during locale change.

## Node-only commands (no Python)

    node experiments/lp0-200/verify-resources.test.js
    node experiments/lp0-200/probe-core.test.js
    node --check experiments/lp0-200/web/probe.js

CI source string check is Node-only. No Python environment should be requested for local work.

## Upgrade package decision

Previously distributed device test build is versionCode 160. LP1 new CI produces versionCode 161, with 2nd-runner build versionCode 162 to check persistent signing and upgrade. Do not distribute the former v159 LP1 package as an upgrade over v160.
