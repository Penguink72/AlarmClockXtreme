# ACX L10｜Source／Context／Binding／Surface 可重建索引（2026-11-22）

> **狀態：遠端隔離施工候選，需依精確 SHA 的 Node CI 結果判定 T2。** 本文記錄 L10 掃描器、證據界線與後續接線；不代表全 App 語義審核、Runtime、正式譯文、APK 或 P1～P3 功能改動。分支基準：B0-T1 commit `c47f92abb52d5a311728646468fab5e21cebbcf0`。

## 0. 前置與對應

本片直接使用現有 `b0-resource-inventory.mjs`、`b0-ui-and-unused-triage.mjs` 與 `b0-t1-canonical-matrix.mjs`，不重新翻譯 2,471 個原始 Key。相關規劃：
- [LWC-W1｜翻譯工程計畫](https://github.com/Penguink72/AlarmClockXtreme/blob/docs/acx-lwc-localization-research-20261122/docs/localization/ACX-LWC-TRANSLATION-WORKFLOW-20261122.md)
- [LWC-R1｜七物件與差分](https://github.com/Penguink72/AlarmClockXtreme/blob/docs/acx-lwc-localization-research-20261122/docs/localization/ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)
- [LWC-IR2｜Partial-State／TGG 候選](https://github.com/Penguink72/AlarmClockXtreme/blob/docs/acx-lwc-partial-state-tgg-20261122/docs/localization/ACX-LWC-PARTIAL-STATE-TGG-CROSSWALK-20261122.md)

B1-C1 語言包驗證器、B1-U1 語言中心、B0-L1 Coordinator、G6-Q2 翻譯初審均在其他分支；它們的 CI 不可冒充本片驗收。

## 1. TCL Root／枝／業

**Root**：以同一份 source SHA 重建原文 Key→語言定義→詞法使用點→場景/程式線索→未知映射，讓後續 Agent 無需手工比對所有 XML。L10 只分析，不修改鬧鐘與翻譯內容。

| 枝 | 實作與檢查 | 不可推定 |
|---|---|---|
| A 原文身分 | B0-T1 raw XML、格式型別、sourceHash、catalogueRevision | 舊分支數值永遠等於當前 main |
| B 使用點 | Kotlin/Java/XML 全部符合規則的靜態引用，不只 B0 前 20 筆 | Regex 命中等於真實 UI 執行 |
| C Context | path、最近函式/型別、API 和程式片段作候選提示 | 路徑等於已核實的操作語義 |
| D 差分基準 | Git SHA、來源 Key hash、程式檔案 hash、binding fingerprint | sourceHash 能發現所有語義變更 |
| E 缺口 | LOCALE_MISSING、UNKNOWN_MAPPING、VISIBLE_LITERAL_CANDIDATE | 沒找到引用就能刪 Key、所有缺譯都需要人工翻譯 |
| F CI | Node 測試加到既有 G1–G6/B0 工作流及 artifact | CI PASS 等於完成 B2 Runtime、T3/P/Q |

**Karma**：同一英文 Resource Key 可能有多個不同 Binding；不同使用點可能有不同操作語義。每個使用點的 `actionSemantic` 都是 `UNVERIFIED`，`runtimeWiring` 都是 `NOT_VERIFIED`。

## 2. 實體交付與執行

- `scripts/localization/l10-source-binding-graph.mjs`：生成來源、Binding、Context 候選、分類缺口；排除 Kotlin/Java 註解、字串內非實際的資源引用，XML 註解亦不算引用；所有未解問題留表。
- `scripts/localization/l10-source-binding-graph.test.mjs`：隔離 fixture、兩個使用點、XML/Compose、直接與間接 API、翻譯缺漏、無引用不可刪、審核不升格、全 Repo 掃描回歸。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：在現有 Node CI 的 B0/T1 之後執行 L10 並上傳原有 B0 Artifact。

### Node.js 指令

```bash
node --test scripts/localization/l10-source-binding-graph.test.mjs
node scripts/localization/l10-source-binding-graph.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
```

新增五份可重建產物（在 CI 的 B0 artifact 中）：

| 檔案 | 用途 |
|---|---|
| `l10-binding-graph.json` | 來源 Key、版本、每個靜態使用點、locale 狀態、未解引用 |
| `l10-binding-index.tsv` | 逐 Key／逐 Binding 的 JSON-quoted TSV，供搜尋與差分 |
| `l10-gap-report.json` | Locale 缺漏、疑似 literal、未知映射與後續診斷 |
| `l10-source-sha-manifest.json` | exact Git SHA、catalogueRevision、各程式檔案 SHA |
| `l10-summary.md` | 有分母的統計與尚未證明的 Runtime／語義界線 |

## 3. 必須保留的證據等級

| 分類 | 靜態證據可以判斷 | 目前不能判斷 |
|---|---|---|
| `LOCALE_MISSING` | 可翻譯 Key 在該 locale XML 無專屬定義 | 一定必須翻譯／實際畫面可見 |
| `VISIBLE_LITERAL_CANDIDATE` | B0 文字掃描偵測出疑似硬編碼 | 已經確認實際顯示 |
| `UNKNOWN_MAPPING` | 找不到直接靜態引用／有動態 getIdentifier 等線索 | 該資源可刪除、完全未使用 |
| `RUNTIME_UNWIRED` | L10 不驗證 Runtime，實測值 **未量測** | 絕不因「確認 0 筆」宣稱無問題 |
| `STALE_TRANSLATION` | 來源／譯文字串 hash 可供後續版本比較 | 沒有 Review 版本時，不能冒稱正式過期 |
| `SEMANTIC_APPROVAL` | 可用 G1/G6/程式呼叫點作後續審核輸入 | 模型初審、字面存在不等於人工獨立核准 |

`bindingId` 由 path／附近函式／Key／該行文字與重複序號生成，只有**候選穩定性**；line 只是當期 SHA 的定位提示，不能跨 ref 當永久身分。現有 `contextRevision` 不足，暫維持 null，不杜撰其版本。

## 4. 驗收與負向案例

1. 來源 Key 若有兩個使用點，需完整記錄兩筆 Binding，而不是只記第一筆；各 locale 可各自有翻譯／缺漏。
2. Kotlin 註解、block comment、字串內 `R.string.foo` 不能冒充真正的 Kotlin 資源引用。
3. 未找到靜態引用的 Key 保留 `UNKNOWN_MAPPING`、`deletionAllowed=false`；不可偷偷從 catalog 刪除。
4. 靜態命中不使 `semanticApproval` 或 `runtimeWiring` 升格；沒有聲明獨立審核／Runtime 證據就必須 UNKNOWN。
5. 不修改 XML、Kotlin Android 執行檔、AlarmService、Room、Media3、音樂池與既有使用者曲目。

## 5. 後續交接

- L10 的程序 T2 要以本分支 GitHub Actions 的精確 SHA 與 run conclusion 回報，不能引用先前 B0 成功 Run。
- 有來源但沒有正規化行為/操作語義的 Binding 繼續標 `UNVERIFIED`，供 L11 審譯者與 L13 Runtime 追查。
- L11 依缺漏分類 SHOULD_TRANSLATE／INTENTIONAL_FALLBACK／NON_TRANSLATABLE／UNKNOWN；先補 Welcome、設定、整合服務與高風險文字。
- L13 使用 B0-L1／B1-C1／B1-U1／B2 的本地 Owner，不引入新的 Scala／TGG runtime；L14 做小型 UI / Service 驗證。
- L10-P Partial-State／TGG 只作後續負向 fixture 候選，**本片不以抽象架構研究阻擋實際翻譯工作**。

**文件落地、產物可重建與通過 Node CI，是 L10 的工程交付；完整動態 Binding 和所有 Runtime 未驗問題仍需後續補證，不以此片聲稱產品翻譯完成。**
