# ACX LWC-TCL｜翻譯研究、共通架構與施工交接帳本（2026-11-22）

> **目的：供後續 Agent／HANDS／鬧鐘 App 討論快速接續，不代表施工授權。** 本輪只建立文件，未修改 Kotlin、XML、Room、語言包或 CI；不合併 `main`。LWC-1／LWC-2 是可驗證的候選資料契約，不是已運作的系統。

## 0. 一頁導航

- [R1｜外部成熟方法論、七種資料物件、狀態失效規則](../localization/ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)
- [W1｜全 App 繁中／日文翻譯來源映射與施工流程](../localization/ACX-LWC-TRANSLATION-WORKFLOW-20261122.md)
- [前置 R5 Runtime 契約](../localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)
- [前置 C1 翻譯內容與審核](../localization/ACX-C1-TRANSLATION-TEXT-EVIDENCE-20261121.md)
- [前置 A0～D0 全產品交付規劃](ACX-A0-TO-D0-PRODUCT-DELIVERY-ROADMAP-20261121.md)

## 1. TCL Root、Leaves、Karma（問題／局部效果）

**Root R5**：既有三語 Android UI 可見文字的資料治理、實際 Runtime 顯示及版本交付目前沒有一致的可重建證據鏈。使用者 2026-11-22 看到 Welcome／整合服務／Wake readiness 大量英文，要求利用程式碼而非截圖找出缺譯。再進一步要求抽象出可跨 App 復用的成熟本地化工作流並留下文本。

| Leaf | 已有來源 | 目前差距 | 下一步 |
|---|---|---|---|
| LWC-1 Source / Binding graph | B0、B0-T1、LP2-B3 | 未形成全 App 正規化的 Key→元件→surface→runtime 可重建索引 | L10 |
| LWC-1 Context / semantic | G1/G2/C1/G6 | 動作與譯文關係仍依單份審核表／候選 | L10、L11 |
| LWC-2 Change impact | `sourceHash`、CI | context/binding 變更的局部重驗尚非完整規則 | L10、L12 |
| Native & custom locale runtime | B0-L1、B1-C1/U1、B2 設計 | B2 正常 UI/notification/Service 全量接線未完成 | L13 |
| Translation review | G4/G5、G6-Q2 | 200-Key 候選的 0/400 獨立批准；全量 2,471 仍需分類 | L11/L12 |
| Acceptance | Android CI、Node／Robolectric | 缺口數 ≠ 畫面覆蓋率；Runtime 尚需驗證 | L14/L15 |

**Karma / 變更效果**：
- Source 改 → 只失效相依譯文、格式與使用點證據；
- Context/操作含義改 → 即使英文原文未變，該操作譯文需要重審；
- Binding/畫面路徑改 → 重新驗證接線與該畫面，只有語義連帶改變才重做語義；
- Locale resolver 改 → 重測切換／通知／fallback，不批次否定全部翻譯；
- 新語言加入 → 擴增該 locale 的翻譯／測試，不重造其他語系的來源。

這些是要驗證的 **dependency invalidation rules**，不是現行 CI 已完全支援的聲明。

## 2. Evidence ledger（驗證界線）

| 證據 | 可以證明 | 不能證明 |
|---|---|---|
| B0 2026-11-21 scanner | 2,471 base、2,462 translatable、兩語各 912 專屬／1,550 缺定義、369 格式、43 plurals、24 無直接引用、97 UI literal 候選 | 現在主線／2026-11-22 APK 仍是相同數字；語義翻譯品質 |
| B0-T1 逐 Key matrix | 既有三語 raw XML、來源及譯文 hash、格式型別 | 所有 Key 已運行可達、所有譯文都正確 |
| G6-Q2 400-row | 200 Key 兩語均有模型輔助初審、27 筆待審修訂 | 獨立人工批准、可對外簽發正式 pack |
| B1-C1 / B0-L1 分支 | V1 pack validator/repo、locale 協調器的候選實作與相應測試 | B2 已完成全 App Runtime |
| 2026-11-22 用戶實機 | P1～P3 音樂來源管理符合預期；少數截圖頁面有英文 | 全部 UI 的翻譯缺漏分母／CI 或 release 狀態 |
| LWC-R1 文獻 | 業界已有 ITS、XLIFF、gettext、Fluent、Weblate、CLDR、MQM 等成熟部件 | 本專案 LWC 是現有單一標準、已施工 |

**授權分類**：資料與規劃存檔 = 本輪已授權；新增掃描程式、正式繁中／日文 XML、B2 Runtime 接線及發布 = **後續獨立工作**，不可因本輪文檔落地而標記為完成。

## 3. TCL 工作片（可分別交付，無需一次大型合併）

| ID | 輸入 | 交付物 | Pass 條件 | 目前狀態 |
|---|---|---|---|---|
| **L10** | B0/B0-T1 + 現行 repo SHA | Key→Source→Context→Binding→UI/Service 映射及缺口清單 | 可重建、可定位、未解動態引用有明確 UNKNOWN | PLANNED |
| **L11** | L10 缺譯清單 + G1/G2/G6 | 繁中／日文逐 Key 候選、原文保留判定 | 候選與現行值不混用；來源及情境可追溯 | PLANNED |
| **L12** | 候選、B1 validator、G4/G5 | 結構／語義審核、XML/JSON 產物 | 格式及動作語義通過各自門檻；無偽批准 | PLANNED |
| **L13** | B0-L1 + B1-C1/U1 + B2 | Locale runtime 與各顯示適配 | 英／繁中／日文／可用自訂包切換和 fallback 可驗 | PLANNED |
| **L14** | L10～L13 的受影響 surface | JVM/Android/UI 測試與最小 T3 驗收 | 靜態可證者自動測；具體動態／排版風險另測 | PLANNED |
| **L15** | 同一 code SHA 的完整候選 | 實測報告、發布／拒絕決策 | P1～P3 不回歸、同 SHA CI、授權後方合併／發版 | PLANNED |

**平行策略**：L11 與 L13 可以在 L10 定義了穩定輸入後平行；L12 正式生成／發布依賴對應語系與來源版本的核准，不必讓日文未批准拖住繁中自訂包測試。各 PR 保持獨立，整合前核對共同 base SHA，不能以平行分支相同路徑文件冒充單一主線已完成。

## 4. 判定與停止條件（防止無止境規劃）

- **對 L10**：已逐 Key 明確定位或標 UNKNOWN，機械發現的缺譯／硬編碼／未接線有固定分類輸出，才算完成。僅憑「已有 2,471 Key」不能通過。
- **對 L11/L12**：候選可以標完成候選生成；獨立語義審核為不同狀態。不為追求 `100% approved` 強迫翻譯 `INTENTIONAL_FALLBACK`、不使用文字或退役 Key。
- **對 L13/L14**：所有正式產品可見文字有適當解析來源、語言切換讀回與主要通知路徑一致。自訂包不必偽裝官方審核。錯誤回退可用測試資料構造，非使用者提供截圖。
- **對產品**：P1～P3 已經實測可用；翻譯改動引起音樂池、排程、鬧鐘喚醒、音源檔損壞即為 regression，阻擋合併。
- **對研究**：只有 L10 實例證明現有 B0＋Android 原生資源＋外部成熟工具無法表示某種關係，才新增 LWC 實作欄位。其他時候只保留文檔層介面契約。

## 5. 後續 Agent 交接協定

下一輪執行者按此順序處理：
1. 讀 [LWC-R1](../localization/ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)、[LWC-W1](../localization/ACX-LWC-TRANSLATION-WORKFLOW-20261122.md) 以及 B0/B1/L1/G6 對應原檔。
2. 選定當期 commit SHA；不要套用本檔歷史快照數字。
3. 只讀執行 B0/T1 再生與差異分析，列出可機械定位和必須追查的缺口。
4. 依 L10 定義提供可重建的映射、source hash、測試結果與未解證據；若需修改程式，另建隔離 PR。
5. 保留過往 P1～P3 使用者驗收證據；切勿將翻譯工程擴張成既有音樂功能重新設計。
6. 回報格式固定：`已完成（證據）／待處理（理由）／未知（下一步如何查）／變更範圍（SHA+檔案）`。

## 6. 本次文件落地範圍

本輪創建三份 Markdown：
- `docs/localization/ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md`
- `docs/localization/ACX-LWC-TRANSLATION-WORKFLOW-20261122.md`
- `docs/tcl/ACX-LWC-LOCALIZATION-TCL-ROADMAP-20261122.md`

獨立分支 `docs/acx-lwc-localization-research-20261122`，起點 `integration/acx-r0-product-candidate-20261121`；純文件，不重寫舊 `docs/localization`、`docs/tcl` 及其他平行開發的文件。此處不宣稱文件 branch 已被合併；是否建立 Draft PR、CI 狀態及 commit SHA 應以當次實際 GitHub 回傳為準。