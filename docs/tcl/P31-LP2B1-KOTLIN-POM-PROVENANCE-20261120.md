# TCL｜LP2-B1 CI Kotlin plugin dependency verification failure（2026-11-20）

## Verified evidence
- B1 PR #24 commit `acafcf77d59ee6a4123ae6b247a2a173f91c0faf`.
- [Android run 44751034195](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44751034195): Debug APK build PASS, persistent signing second-runner PASS; unit-test job fails **before running tests**, so `0 tests` is not a test assertion failure.
- Original and rerun of that unit job both report `org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:2.1.0` POM SHA-256 mismatch.
- Locked digest `0b1d7e9b7bf9d69b91c6b187b55d1dfc0ebd896567efd1bfe2b41e48cf39103a`; fetched digest observed by failed runner `96e007b3ecb22cc6d9617e414484539fe5b77b28e6659c8c916b4fddf8961ced`. The failure report was retrieved from the GitHub Actions artifact, not inferred from console only.
- Gradle official documentation says changed checksums must be investigated; do not disable dependency verification or blindly approve an alternate hash.

## Read-only diagnostic
- This isolated diagnostics branch runs `scripts/ci/gradle-kotlin-pom-provenance.mjs` against upstream **Maven Central** and **Gradle Plugin Portal** using fetch; records actual content hashes and checksums separately.
- No patch to `gradle/verification-metadata.xml`, no `--dependency-verification=off`, no changed runtime, no forced release.
- Use the uploaded `p31-kotlin-pom-provenance` JSON to compare the locked digest, the failed-run digest, and independently available upstream artifacts.
- Only after reviewing actual publisher metadata should an explicit checksum or repository resolution correction be proposed.
- LP2-B2 UI feature work does not negate this T2 blocker. Do not claim T2 until an exact-HEAD full Android run succeeds.
