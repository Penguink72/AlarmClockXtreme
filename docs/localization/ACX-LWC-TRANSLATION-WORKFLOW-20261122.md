# ACX LWC-W1｜全 App 中／日翻譯流程、映射與驗收契約（2026-11-22）

> **規劃文件；未實作 Runtime、未修改字串、未批准官方 pack。** 本文件將 [LWC-R1 研究](ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md) 接到 AlarmClockXtreme 現有的 B0／B0-T1／B1／B2／C1／G1～G6。2026-11-22 使用者明確要求「程式碼分析為主、實機截圖不是必填」。後續 Agent 應從現存程式／產物直接重建證據，不重複請使用者截圖。

## 1. 範圍與不變條件

目標：對正式產品所有使用者可見文字建立可追溯翻譯來源、繁中／日文覆蓋及執行接線，維護來源變動後的局部重驗。保留 Android 原生 strings、plurals、arrays、LocaleManager、`stringResource`／`getString`。不得用自製 resolver 破壞既有平台能力。

**不可更改**：P1～P3 已被使用者驗收的 4,935 索引／4,920 可播放音樂池、資料夾加入排除、播放清單；Room／排程／Media3；真正貪睡／解除行為。語言包處理不得刪除手機原始音訊。LWC 不授權修改 Repo 以外產品；不授權合併 `main` 或發布官方已核准包。

正式內建包與使用者自訂包為**兩條不同發布權限**：自訂包經 B1-C1 結構驗證後可作受控個人使用，但不可藉 `status=APPROVED` 宣稱官方批准；C1/G4/G5 的獨立語義審核門檻不得繞過。

## 2. W0：重新讀取同一 SHA 的權威來源（輸入）

1. 鎖定目標 Git ref、exact code SHA、Android variant、source catalogRevision；先辨識 B0/B1/L1/LP2 各分支是否已整合，**不可把平行 PR 的功能當作 `main` 現況**。
2. 用 B0 的 `scripts/localization/b0-resource-inventory.mjs`、`b0-ui-and-unused-triage.mjs` 與 B0-T1 `b0-t1-canonical-matrix.mjs --verify` 重新抽取當前完整鍵集合及呼叫位置；沿用 `sourceHash` 與 typed key 契約。
3. 從 B1-C1 的 catalog/repository 讀取真正 `acx-language-pack/1` 格式；舊 `acx-language-pack-probe/1` 不直接轉成 V1 批准。
4. 以原始 XML／Kotlin／Compose／Notification／Service 檔案為證據。過期報告、截圖及討論摘要只能提供待查線索，不作當期執行狀態的最終證據。

**輸出**：`source-inventory.json`、`trilingual-matrix.tsv`、`source-sha-manifest.json`（路徑／格式為規劃示意，盡量重用 B0 現有產物命名）；不可自稱已運行的產物。

## 3. W1：建立 Source → Binding → Surface 映射（主要工程缺口）

檢查順序：
- Android XML：`string`、`plurals`、`string-array`、rich text／轉義、`translatable=false`。
- 直接 Kotlin/Compose：`stringResource(R.string.*)`、`pluralStringResource`、`getString`、`getQuantityString`、`R.array.*`。
- 間接：`titleRes`／`descriptionRes` 傳入的 resource ID、wrapper/composable/helper、Context 引用、通知／Receiver／Service、widget、accessibility、Wear。
- 可見字面：`Text("...")`、`contentDescription`、`Snackbar`、`Toast`、對話框按鈕、錯誤提示、導覽及 onboarding；排除非自然語言的 class 名稱、數字和符號。
- 動態查詢與外部內容：`getIdentifier`、反射、runtime 組字、第三方字串、使用者音樂檔名。無法靜態保證時標 `UNRESOLVED_DYNAMIC` 或 `EXTERNAL_NONTRANSLATABLE`，不得刪除來源。

每筆 Binding 最少記 `resourceKey`、`resourceType`、`symbol/component`、`sourcePath`、`sourceRevision`、`surface`、`textApi`、`resolutionPath`、`directOrIndirect`、`actionSemantic`（若已查證）、`evidenceStatus`。行號可記，但不得單獨充當永久 identity。

以 AST／可編譯的引用資料優先，regex 作候選掃描；對无法解析的點保留佇列及正負測試。**程式可證明的缺漏直接修，不要要求截圖**。Runtime 截圖只用於排版、字級、動態路徑的專項驗收。

### 可供 CI 機械分類的五種缺口

| 類別 | 如何檢出 | 下一步 |
|---|---|---|
| `LOCALE_MISSING` | 英文可翻譯 Key 缺 zh-TW／ja 專屬定義 | 依意圖分 SHOULD_TRANSLATE / INTENTIONAL_FALLBACK / NON_TRANSLATABLE / UNKNOWN |
| `VISIBLE_LITERAL` | 可見 UI 英／中文案直接寫在程式 | 抽成 Resource／保留明確例外 |
| `RUNTIME_UNWIRED` | 選擇語言後該 surface 不讀正確 locale | 修 Compose／Context/Service adapter |
| `STALE_TRANSLATION` | sourceHash 或已核實的 contextRevision 過期 | 局部翻譯／語義再審 |
| `UNKNOWN_MAPPING` | 間接/動態資源與行為映射不足 | 追呼叫鏈／定向 Runtime test，不武斷刪除 |

同一 Key 可以具有多個 Binding；同一 Binding 的語言有各自翻譯狀態。**不要以 Key 翻譯數字充當 UI 位置的覆蓋率**。

## 4. W2：翻譯程序（可增量、可分語言交付）

1. `Source freeze`：對每個 Key 鎖定英文、resource type、format/plural/array 契約，排除原文本身過時／誤導的文案。舊缺陷記錄（例：`music_playlist_integration_pending`）保留，不靜默消去。
2. `Context evidence`：來源符號、畫面、操作前後狀態、術語／禁用語與格式限制；共用 G1 18 個候選概念及 G2 呼叫證據，必要時標明「尚未核實」。
3. `Translation drafts`：從 2026-11-21 B0-T1 已有 912×2 XML、G6 200×2 候選及翻譯記憶復用；只為欠缺與過時 Key 產生新候選，不覆寫現有已核准譯文。
4. `Structural QA`：locale、key 完整性、非法 XML／控制字、占位符、重複參數、`plurals/other`、array 長度、rich markup、長度風險。沿用 B1-C1 validator 和 Node/Robolectric tests。
5. `Semantic QA`：MQM 類別做缺義／加義／術語／誤導風險定位。貪睡／停止／刪除／排除／權限／Webhook/隱私等高影響文案需依程式操作證據做嚴格審核；低風險說明可批次規則＋抽查。模型初審 ≠ 獨立批准。
6. `Generate`：內建繁中／日文 Android XML，及 V1 `acx-language-pack/1` 的可用自訂 JSON，**來源證據與使用者自訂 pack 的批准狀態分離**。
7. `Apply`：LocaleCoordinator 的 FOLLOW_SYSTEM / BUILTIN / CUSTOM、主次語言及雙語狀態，B2 resolver 對 Compose、通知、Service、ringing 依資源種類解析，正確顯示 fallback 來源；不碰媒體與排程。
8. `Deliver`：產物、source SHA、測試 Run 與使用者實機結果獨立記錄，不能跨提交冒用 CI PASS。

## 5. W3：依風險排序的 Runtime 驗證

| 順序 | Surface | 主測目的 |
|---|---|---|
| A | Welcome 四步、設定、整合服務、Wake readiness、導航 | 缺翻譯／Locale 實際套用；2026-11-22 截圖是觸發例 |
| B | Today、鬧鐘編輯、音樂庫／播放清單、歷史 | 驗證已通過 P1～P3 功能沒有回歸 |
| C | 計時器、碼錶、世界時鐘、新聞、天氣、日曆與統計 | 檢查數值、複數與日期格式 |
| D | 解除任務、真正響鈴畫面、通知、Widget、無障礙 | 翻譯是否改變操作含義／安全按鈕 |
| E | 自訂包語言中心、低頻錯誤、Debug/Wear/外部整合 | 無未分類可見文字與錯誤 fallback |

靜態 CI 證明完整資源與直接／已解析引用；純 JVM/Robolectric 驗證 resolver 和格式；Instrumentation/UI 驗證語系切換、重啟、通知、字級、偽語言；只有剩餘的具體風險才安排 T3 手機測試。不要用全面實機截圖當作 W1 的進入條件。

## 6. 完成定義及可反證方式

**分母明確**：
- `coverageResource(locale)` = (該語系有適當譯文 + 明確批准的 intentional fallback) / 當期需要本地化的 Key；需另列各類分子，不可把 fallback 冒稱已翻譯。
- `coverageBinding(locale)` = 已定位並經正確解析路徑處理的使用者可見 Binding / 所有已確認可見 Binding；動態／未知另列，不能隱藏在分母外。
- `coverageSemantic(locale)` = 已通過指定審核規則的目標文字 / 適用該類規則的有效候選；模型與人工／獨立核准分開報。
- `coverageRuntime(locale)` = 已通過該版本執行驗證的關鍵 surface / 全部應測 surface；不以資源數取代。

**完成**：資源缺漏均已分類並處置，關鍵操作語義可信、Runtime 切換與 fallback 一致、CI 不存在 P0/P1 結構／回歸阻斷、其餘項目有具體排除或已知限制及版本。完成定義屬**目標**，不是本文件已達成結果。

**負向範例**：故意拿掉 Welcome Key、在 Compose 新增 `Text("Next")`、交換 `%1$d` 與 `%1$s`、將「從清單移除」改成「刪除原檔」、切語後重新啟動、提供不合法自訂 JSON，都應由對應靜態／結構／行為測試定位；若機械掃描認不出動態行為，需標 UNKNOWN 和補最小專項測試。

## 7. 可交付的工作分片及先後順序

- **L10**（先）：B0/T1 來源 + Binding graph，可重建的分類缺口表和未解引用表；無產品程式碼更動也可獨立驗收。
- **L11**（與 L13 可平行）：以 G1/G2/G6 為基礎補齊中日候選，清楚記錄意圖保留英文項；不得偽造官方批准。
- **L12**：基於既有 G4/G5 與 B1 Validator 做分級語義/結構審核和 XML／pack 生成。
- **L13**：沿 B0-L1/B1-C1/B1-U1 接 B2 正式 Runtime，不重新造第二個 locale manager；Android 原生資源優先。
- **L14**：按關鍵畫面／通知／服務驗證，再對無法靜態確認者做少量實機測試。
- **L15**：獨立整合候選、同一 SHA 全 CI／升級簽章、功能回歸／版本交付（需另有 merge/release 授權）。

**避免工作無限擴張**：既有工具已滿足資料需求時，LWC 只是一份資料契約；只有遇到無法表示的實際 Binding／Context／失效關係，才在隔離分支提出新欄位與測試。不要先打造通用平台，也不要以未提交的規劃冒充已實作。

## 8. 既有文件與操作入口

- [研究與共通 IR](ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)
- [TCL 任務、風險及證據帳本](../tcl/ACX-LWC-LOCALIZATION-TCL-ROADMAP-20261122.md)
- [B0 全量資源掃描文件](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)
- [B0-T1 三語矩陣](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-t1-trilingual-catalog-20261121/docs/localization/ACX-B0-T1-TRILINGUAL-MATRIX-20261121.md)
- [G6-Q2 候選審核](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/p31-g6q2-full-200-key-model-audit-20261121/docs/localization/P31-G6Q2-ALL-200-MODEL-CONTENT-AUDIT-20261121.md)
- [B1-C1 語言包格式／驗證](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b1-c1-pack-validation-repo-20261121/docs/localization/ACX-B1-C1-V1-PACK-VALIDATOR-AND-STORAGE-20261121.md)
- [B0-L1 Coordinator](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-l1-coordinator-integration-20261121/docs/localization/ACX-B0-L1-COORDINATOR-INTEGRATION-20261121.md)
- [R5 系統／自訂語言包 Runtime 設計](https://github.com/Penguink72/AlarmClockXtreme/blob/integration/acx-r0-product-candidate-20261121/docs/localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)

**下一輪 Agent 的最短入口**：先讀本文的 W0/W1、B0/T1 目前的 source SHA 與三語矩陣，再實際執行檢查；不能直接從過往討論的 2,471/912/1,550 推定新編譯版的確切數量。