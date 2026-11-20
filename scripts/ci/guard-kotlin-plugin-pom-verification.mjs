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
const composeLock="db3bde26de3ea7433086163644a5750e280a1540990e30f1ae99fe80f2d5f160";
const composePortal="fe78fa62efbf0c89ebef4bb0695ab219f41d802e854aafe0fedc561b1bc4a0d7";
const composeComponents=[...xml.matchAll(/<component group="org\.jetbrains\.kotlin\.plugin\.compose" name="org\.jetbrains\.kotlin\.plugin\.compose\.gradle\.plugin" version="2\.1\.0">([\s\S]*?)<\/component>/g)];
assert.equal(composeComponents.length,1,"Exactly one Compose 2.1.0 marker must exist");
const composeItems=[...composeComponents[0][1].matchAll(/<artifact name="org\.jetbrains\.kotlin\.plugin\.compose\.gradle\.plugin-2\.1\.0\.pom">([\s\S]*?)<\/artifact>/g)];
assert.equal(composeItems.length,1);
const composeSha=composeItems[0][1].match(/<sha256 value="([0-9a-f]{64})"[^>]*>([\s\S]*?)<\/sha256>/);
assert(composeSha,"Compose marker POM digest missing");
assert.equal(composeSha[1],composeLock);
const composeAlso=[...composeSha[2].matchAll(/<also-trust value="([0-9a-f]{64})"\s*\/>/g)].map(m=>m[1]);
assert.deepEqual(composeAlso,[composePortal],"Compose portal digest must be exact and independently audited");
assert.equal([...xml.matchAll(/<also-trust value="/g)].length,2,"Only two audited plugin marker POMs may have alternative hashes");
const commands=readFileSync(".github/workflows/p0-android-baseline.yml","utf8");
assert(!commands.includes("--dependency-verification=off"),"Do not bypass Gradle checks");
assert(commands.includes("node scripts/ci/guard-kotlin-plugin-pom-verification.mjs"),
  "Strict APK and unit workflows must both check the narrow checksum allowlist");
assert.equal(commands.split("node scripts/ci/guard-kotlin-plugin-pom-verification.mjs").length-1,2,
  "Both independent Gradle jobs must validate metadata before build");
console.log("KOTLIN_PLUGIN_POM_ALLOWLIST_PASS two reviewed SHA-256 digests for each of Kotlin Android and Compose 2.1.0 marker POMs; verification remains STRICT");
