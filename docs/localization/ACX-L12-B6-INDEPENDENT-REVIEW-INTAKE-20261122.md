# ACX L12-6｜完整 3,100 列候選的獨立審核入口與簽章契約（2026-11-22）

> **本階段只實作待審工作包產生、外部簽章驗證；沒有冒名簽發任何獨立審核。** G1 仍未批准，G4 人類審核帳本空白，正式 XML、語言包、APK 仍不准釋出。

## 同版來源與實作

- 固定整合基準 SHA：076ed6d15135958010cc6fb0d6a90760f4285b85，來源是 PR #58 L11-10、PR #59 L12-5 與 PR #60 L13-A。
- scripts/localization/l12-b6-independent-review.mjs 重跑 B0-T1、L10、L11、L12 與 L11-B10 typed validation，產出一致的完整來源／譯文／審核修訂雜湊。
- 完整候選：3,100 列，其中普通字串 3,036，複數 62，字串陣列 2；繁中與日文各 1,550。
- 31 個 plurals、1 組 15 條 string-array 都保持正確 Android 資源型別，不折成普通 string。

## 審核入口

只在 build/localization/L12_B6_EXTERNAL_REVIEW_REQUIRED/ 輸出以下候選：
- independent-review-packet.json：包含 key、locale、type、sourceHash、translationHash、reviewRevision、source context、風險與未審內容。
- independent-review-worklist.tsv：供獨立 reviewer 核對。
- reviewSetDigest：完整固定工作列的共同 fingerprint；原文或譯文版本變更即必須重審。

受保護外部環境必須提供：
1. 全 3,100 列相符的逐列人工審核決定、審核者識別、時間與理由，並對 meaning / context / format / operation consequences 逐一確認。
2. Ed25519 簽章。可信 public key 由 Repo 外保護環境獨立注入，不接受本分支產生的金鑰自行取得正式權威。
3. 獨立 G1 glossary APPROVED_FOR_RELEASE 與非空且可信的 G4 review ledger。
4. Android aapt2、完整 Gradle build、runtime tests 另由 L13／L14 驗證，簽章本身不能代替編譯。

無可信公鑰、無簽章、來源版本改變、欄位不足、重複批准、G1/G4 未完成，一律 fail-closed。單元測試使用的短期合成 Ed25519 測試金鑰不等於真實 reviewer 或正式批准。

## 不得擅自修改

- localization/governance/l12/independent-review-ledger.json：維持 0 筆審核批准。
- localization/governance/g4/review-ledger.json：維持 0 筆人類批准。
- localization/governance/g1/glossary-contract.json：維持 UNAPPROVED_FOR_RELEASE。
- app/src/main/res 既有繁中日文 XML、AlarmService、Room、Media3、播放清單皆不變。

## 完成與待處理

L12-6 的工作包生成、驗證介面、拒絕無授權以及測試可以工程驗收。**3,100 筆實際獨立語義批准尚未完成**；受保護的外部 reviewer 與 GitHub Environment 信任錨仍待專案擁有者配置。L13-B 後續只可做隔離 XML 編譯與受保護 writer 的 fail-closed 測試，禁止產生未經批准的正式語言包。
