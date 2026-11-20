# CI 依賴來源鑑別｜Kotlin Android + Compose 插件 POM（2026-11-20）

## 起因：同一 SHA 的 APK 成功，單元測試卻在 Gradle 依賴驗證前置階段失敗

[LP2-B1 Run 44751034195](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44751034195) 針對 `acafcf77`：APK 建置與第二 Runner 簽章成功；單元測試 Runner 因 Kotlin 2.1.0 plugin-marker POM 雜湊不符而停在 Gradle plugin 解析階段，**沒有執行測試（0 項）**。同一 Run 的失敗工作重跑仍遇到相同問題，不可把它說成單元測試斷言失敗。

## 來源比對：兩個官方 Repo 並非同一組 bytes

獨立只讀工作分支 `diagnostics/p31-lp2b1-kotlin-pom-provenance-20261120` 使用 Node.js 直接取得 Maven Central 和 Gradle Plugin Portal 的實際 POM 內容，留存 XML 與 SHA-256：

| Maven marker（2.1.0） | Maven Central：原始鎖定 SHA-256 | Gradle Plugin Portal：另版 SHA-256 | 依賴宣告 |
|---|---|---|---|
| `org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin` | `0b1d7e9b7bf9d69b91c6b187b55d1dfc0ebd896567efd1bfe2b41e48cf39103a` | `96e007b3ecb22cc6d9617e414484539fe5b77b28e6659c8c916b4fddf8961ced` | `org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0` |
| `org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin` | `db3bde26de3ea7433086163644a5750e280a1540990e30f1ae99fe80f2d5f160` | `fe78fa62efbf0c89ebef4bb0695ab219f41d802e854aafe0fedc561b1bc4a0d7` | `org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0` |

診斷 [44759332185](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44759332185)、[44759646691](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44759646691)、[44761041843](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761041843) 分階段確認：兩個官方來源提供的 POM **內容與大小不同**，但每組都有上述**同版本、同指向的唯一插件依賴**。此事實不等於 POM 位元完全相同，也不構成 PGP／供應鏈身分證明。資料來源是 GitHub Actions 的網路下載與 Gradle 錯誤報告，而非模型推測。

## 已實施的最小修補與反向保護

- `gradle/verification-metadata.xml` 保留原 Maven Central 主 SHA-256，只對上述 **2 個精確 POM** 各新增一個已核對的 `<also-trust>` SHA-256，未新增全域 trusted-artifacts、未放寬其他相依或版本。
- `scripts/ci/guard-kotlin-plugin-pom-verification.mjs` 要求鎖定 SHA／alternates 精確等於白名單、最多這兩個 `also-trust`、禁止 `--dependency-verification=off`；`.github/workflows/p0-android-baseline.yml` 在 **Build** 與 **Unit** 各驗證一次，並監看 `scripts/ci/**` 變更。
- 維持 Gradle **嚴格依賴驗證**；未修改警報響鈴、資料庫或 Android 語言包內容。
- 對未來新的雜湊差異，先留存來源 bytes／官方座標／依賴清單／實際 runner 失敗證據，再決定具名修訂；**禁止自動接受未知 hash**。

## 修補完成的驗收結果

- [LP2-B1 Run 44761537401](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761537401) 對 `b2c03972`：APK、856／856 單元測試、第二 Runner 升級簽章 **全數 PASS**。
- [LP2-B2 Run 44762900025](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900025) 對 `6a0223e1`：APK、859／859 單元測試、第二 Runner 升級簽章 **全數 PASS**。
- 兩個結果分別只適用於這些確切程式提交；後續改動仍需依 SHA 重跑。

## 歷史材料位置

- 獨立診斷分支的原始筆記：`docs/tcl/P31-LP2B1-KOTLIN-POM-PROVENANCE-20261120.md`；原始 Node 工具 `scripts/ci/gradle-kotlin-pom-provenance.mjs` 與 `scripts/ci/gradle-compose-pom-provenance.mjs` **仍在診斷分支**。
- 本文件是對遠端紀錄的**結果整合**，不是將網路診斷工具、Debug 實驗或依賴批准自行合入 `main`。