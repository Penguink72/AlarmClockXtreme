#!/usr/bin/env node
/* Read-only provenance check. Never modify gradle/verification-metadata.xml or bypass strict Gradle verification. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const artifact="org.jetbrains.kotlin.plugin.compose.gradle.plugin-2.1.0.pom";
const coordinate="org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.1.0";
const targetPath="org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/2.1.0/"+artifact;
const meta=readFileSync("gradle/verification-metadata.xml","utf8");
const marker=meta.match(/<component group="org\.jetbrains\.kotlin\.plugin\.compose" name="org\.jetbrains\.kotlin\.android\.gradle\.plugin" version="2\.1\.0">([\s\S]*?)<\/component>/);
assert(marker,"Kotlin plugin lock entry absent");
const locked=marker[1].match(/<artifact name="org\.jetbrains\.kotlin\.android\.gradle\.plugin-2\.1\.0\.pom">[\s\S]*?<sha256 value="([0-9a-f]{64})"/);
assert(locked,"Kotlin plugin SHA-256 lock absent");
const expected=locked[1];
const observedByUnitJob="fe78fa62efbf0c89ebef4bb0695ab219f41d802e854aafe0fedc561b1bc4a0d7";
const targets=[
 {name:"maven-central",url:"https://repo.maven.apache.org/maven2/"+targetPath},
 {name:"gradle-plugin-portal",url:"https://plugins.gradle.org/m2/"+targetPath}
];
async function fetchText(url){
 const response=await fetch(url,{signal:AbortSignal.timeout(25000)});
 if(!response.ok)throw Error("HTTP "+response.status);
 const data=new Uint8Array(await response.arrayBuffer());
 assert(data.length>0&&data.length<1024*1024,"POM size invalid");
 return {bytes:data,resolvedUrl:response.url,type:response.headers.get("content-type")||""};
}
const results=[];
for(const entry of targets){
 try{
   const p=await fetchText(entry.url);
   const text=new TextDecoder().decode(p.bytes);
   assert(text.includes("<project")&&text.includes("org.jetbrains.kotlin"),"Not a Maven POM");
   const sha=createHash("sha256").update(p.bytes).digest("hex");
   let sidecar=null;
   try{
     const external=await fetchText(entry.url+".sha256");
     sidecar=new TextDecoder().decode(external.bytes).trim().split(/\s+/)[0].toLowerCase();
     if(!/^[0-9a-f]{64}$/.test(sidecar))sidecar="UNRECOGNIZED";
   }catch(e){sidecar="UNAVAILABLE: "+String(e.message).slice(0,90);}
   const dependencies=[...text.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map(match=>{
     const block=match[1],field=name=>block.match(new RegExp("<"+name+">([^<]+)</"+name+">"))?.[1]||"";
     return {groupId:field("groupId"),artifactId:field("artifactId"),version:field("version")};
   }).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)));
   assert(dependencies.length>0,"The fetched Maven POM contains no plugin-marker dependencies");
   results.push({name:entry.name,status:"fetched",source:entry.url,resolvedUrl:p.resolvedUrl,
     sha256:sha,bytes:p.bytes.length,contentType:p.type,sidecarSha256:sidecar,
     sidecarMatches:sidecar===sha,matchesLocked:sha===expected,matchesFailedRun:sha===observedByUnitJob,
     dependencyGraph:dependencies,xmlContent:text});
 }catch(e){
   results.push({name:entry.name,status:"unavailable",source:entry.url,error:String(e.message).slice(0,190)});
 }
}
const reported=results.filter(x=>x.status==="fetched");
const sameDependencies=reported.length===2 && JSON.stringify(reported[0].dependencyGraph)===JSON.stringify(reported[1].dependencyGraph);
const result={
 schema:"acx-gradle-dependency-provenance-diagnostic/2",coordinate,artifact,
 bothOfficialPomsFetched:reported.length===2,dependencyGraphEquivalent:sameDependencies,
 failure:"The P0 strict build job rejected the compose plugin marker POM after the Kotlin Android marker mismatch was resolved.",
 expectedSha256:expected,failedUnitJobObservedSha256:observedByUnitJob,
 observedIsDifferentFromLock:expected!==observedByUnitJob,
 securityPolicy:"FAIL_CLOSED_DO_NOT_CHANGE_LOCK_UNTIL_SOURCE_BYTES_ARE_INDEPENDENTLY_REVIEWED",
 results
};
mkdirSync("build/diagnostics",{recursive:true});
writeFileSync("build/diagnostics/compose-plugin-pom.json",JSON.stringify(result,null,2)+"\n");
console.log("POM_PROVENANCE "+JSON.stringify(result));
if(results.every(x=>x.status!=="fetched")){
 console.error("DIAGNOSTIC_INCOMPLETE: neither upstream source was reachable");
 process.exitCode=2;
}else{
 console.log("DIAGNOSTIC_COMPLETE: evidence only; CI dependency verification remains unchanged");
}
