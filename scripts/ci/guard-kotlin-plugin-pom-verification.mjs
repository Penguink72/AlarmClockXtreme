#!/usr/bin/env node
/* Fail-closed integrity guard for exactly one independently audited Kotlin 2.1.0 plugin marker POM. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const xml=readFileSync("gradle/verification-metadata.xml","utf8");
const expected="0b1d7e9b7bf9d69b91c6b187b55d1dfc0ebd896567efd1bfe2b41e48cf39103a";
const portal="96e007b3ecb22cc6d9617e414484539fe5b77b28e6659c8c916b4fddf8961ced";
const components=[...xml.matchAll(/<component group="org\.jetbrains\.kotlin\.android" name="org\.jetbrains\.kotlin\.android\.gradle\.plugin" version="2\.1\.0">([\s\S]*?)<\/component>/g)];
assert.equal(components.length,1,"Exactly one Kotlin Android 2.1.0 plugin marker must exist");
const items=[...components[0][1].matchAll(/<artifact name="org\.jetbrains\.kotlin\.android\.gradle\.plugin-2\.1\.0\.pom">([\s\S]*?)<\/artifact>/g)];
assert.equal(items.length,1,"Exactly one signed-off plugin marker POM");
const primary=items[0][1].match(/<sha256 value="([0-9a-f]{64})"[^>]*>([\s\S]*?)<\/sha256>/);
assert(primary,"A nested POM sha256 allowlist must exist");
assert.equal(primary[1],expected,"Unexpected primary source digest");
const also=[...primary[2].matchAll(/<also-trust value="([0-9a-f]{64})"\s*\/>/g)].map(m=>m[1]);
assert.deepEqual(also,[portal],"Only the independently verified Gradle Plugin Portal POM may be added");
assert.equal([...xml.matchAll(/<also-trust value="/g)].length,1,"Unexpected additional alternative checksum approvals");
const commands=readFileSync(".github/workflows/p0-android-baseline.yml","utf8");
assert(!commands.includes("--dependency-verification=off"),"Do not bypass Gradle checks");
assert(commands.includes("node scripts/ci/guard-kotlin-plugin-pom-verification.mjs"),
  "Strict APK and unit workflows must both check the narrow checksum allowlist");
assert.equal(commands.split("node scripts/ci/guard-kotlin-plugin-pom-verification.mjs").length-1,2,
  "Both independent Gradle jobs must validate metadata before build");
console.log("KOTLIN_PLUGIN_POM_ALLOWLIST_PASS exactly two reviewed source digests for one Kotlin 2.1.0 POM, verification remains STRICT");
