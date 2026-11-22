# ACX LWC-R1｜跨 App 本地化工作流研究與共通抽象（2026-11-22）

> **性質：研究紀錄／架構候選，不是程式實作或翻譯批准。** 本文將 2026-11-22 的 TCL 討論固定為可回顧文件；以 2026-11-21 已提交的 B0、B0-T1、B1-C1、B0-L1、G6-Q2 文件作既有 Repo 證據，以外部官方資料作方法論來源。LWC（Localization Workflow Contract）是本專案的候選名稱，**不是 W3C/OASIS/Unicode 正式標準**。不得以本文取代來源檔、CI 或真機驗收。

## 1. 問題及用戶決定

- P1～P3 音樂來源／播放清單實機版已由使用者回報「符合預期可用」（2026-11-22）；此次翻譯工作不重新開啟音樂池需求，也不修改 Room、Media3 或 AlarmService。
- 實機版本 1.15.35 的 Welcome 1～4、整合服務（Webhook、Public holidays）、設定（Wake readiness）仍見大量英文；這些截圖是**缺陷觸發線索**，不是翻譯盤點的必要輸入。
- **工作主軸已修正**：優先掃描程式碼／Android XML 的所有可見文字、呼叫鏈、翻譯資源與 LocaleResolver 接線；不要求使用者逐頁提供截圖。只有靜態分析無法判定的執行路徑、排版／截斷、字級與實際行為，才進行針對性 Runtime／實機檢查。
- 語言包治理可重用既有 LP0～LP-G5、G6、B0～B2；不重新建立與既有 Android 資源或外部 TMS 衝突的翻譯平台。
- LWC 的目標是**可遞交、可重建、可局部失效的來源與轉換證據**，避免 Agent/人類交接後重找全部映射；LLM 的閱讀能力不等於完成全 Repo AST、資料流及動態執行證明。

## 2. 成熟方法論與能力邊界（2026-11-22 核查）

| 既有標準／工具 | 實際可借用的能力 | 不應過度聲稱 |
|---|---|---|
| W3C ITS 2.0 | translate、term 等國際化／翻譯 metadata 類別，尤其 XML/HTML 內容 | 不能代替 Kotlin/Compose 呼叫圖 |
| OASIS XLIFF 2.1 | source/target、翻譯單位、備註、片段狀態與交換 | 不是 Android Runtime；專案審核授權仍須自行管理 |
| GNU gettext（`xgettext`／`msgmerge`） | 抽取與來源變動後合併更新舊譯文的成熟模式 | 不表示本案必須改用 PO |
| Mozilla Fluent | 具識別碼的 message/terms、變數與語言差異處理 | 不表示本案應取代 Android `strings.xml` |
| Weblate | Git 同步、持續翻譯及回寫／PR 協作 | Git 同步不自動證明譯文語義與鬧鐘操作安全 |
| Unicode CLDR／MessageFormat | 複數、數量、格式／locale 規則 | 不能單靠參數數量相同判定內容正確 |
| MQM Core / Full | 依 accuracy、terminology、fluency 等錯誤分類評估翻譯 | 需有明確錯誤證據，不能把 LLM 自評當正式批准 |
| Android localization／pseudo-locales | 原生 locale 資源、格式、配置和偽語言測試 | 偽語言不能證明語義或所有執行時畫面 |

**外部來源（以官方文檔為主）：**
1. W3C ITS 2.0：https://www.w3.org/TR/its20/
2. OASIS XLIFF 2.1：https://docs.oasis-open.org/xliff/xliff-core/v2.1/os/xliff-core-v2.1-os.html
3. GNU gettext（`xgettext`／`msgmerge`）：https://www.gnu.org/software/gettext/manual/gettext.html
4. Mozilla Fluent terms：https://projectfluent.org/fluent/guide/terms.html
5. Weblate continuous localization：https://docs.weblate.org/en/latest/admin/continuous.html
6. Weblate Git/PR integration：https://docs.weblate.org/en/latest/admin/code-hosting.html
7. Unicode CLDR plural rules：https://cldr.unicode.org/index/cldr-spec/plural-rules
8. Unicode MessageFormat：https://www.unicode.org/reports/tr35/tr35-messageFormat.html
9. MQM Core：https://www.themqm.org/mqm-pillars/the-mqm-core-typology/
10. Android 本地化：https://developer.android.com/guide/topics/resources/localization
11. Android 偽語言：https://developer.android.com/guide/topics/resources/pseudolocales

文獻只支持其各自的格式／流程能力。以下 LWC 中介表示、依賴失效規則、分類狀態機，屬**從多份來源抽象出來的專案提案**，不是單一標準既有定義。

## 3. TCL Root：不同應用／翻譯程序的同構性

共同問題：使用者可見文字來自不同語言、資源型別、UI 與背景路徑；它會因程式更新而改變含義、引用及輸出。必須在**來源不變、語義不誤、顯示可達、驗收可追溯**之間建立可檢驗關係。

共同管線（各平台可替換兩端，維持中介層契約）：

```text
Android / Web / Desktop / Godot / Writing-tool
  └─ Extractor (平台專用：AST、XML、模板、UI schema)
       └─ Source + Context + Binding（可重建的 Localization IR）
            └─ glossary / Translation candidates / Review
                 └─ validator / compiler / pack generator
                      └─ Runtime adapter (平台專用：resource & locale resolver)
                           └─ UI, notification, service, accessibility
                                └─ CI evidence + acceptance + change impact
```

**不統一**：Android `R.string`、Godot TranslationServer、Web i18n 和寫作工具自己的 Renderer 應保持各自原生機制。**可統一**：來源身分、上下文、翻譯與審核證據、生成、失效、交付契約。

## 4. LWC-1 候選：七種資料物件與關係

| 節點 | 最小責任 | 既有可映射來源 |
|---|---|---|
| `Source` | key、resourceType、rawText/XML、sourceHash、catalogRevision | B0、B0-T1 |
| `Context` | 所在畫面、使用者意圖、術語、操作後果、格式/長度限制 | G1/G2、C1 |
| `Binding` | callsite、resource ID、UI surface、間接引用、動態來源及操作關聯 | B0 scanner、LP2 B3 白名單 |
| `Translation` | locale、候選／現有譯文、版本、source 依賴 | B0-T1、G3、G6 |
| `Review` | 機械／模型／獨立審查分級、風險、理由、審核者、證據版本 | G4/G5/G6 |
| `Runtime` | primary/secondary locale、resolver、fallback、formatter、Context/Compose adapter | B0-L1、B1、B2 設計 |
| `Delivery` | generated artifact、目標 SHA、CI、驗收矩陣、release/no-go | A0～D0 |

**關係**：Source --usedBy--> Binding --appearsOn--> UI surface；Source --constrainedBy--> Context；Translation --translates--> Source，且 --reviewedAgainst--> Context；Runtime --resolves--> Translation/Source；Delivery --bundles--> Runtime + translations + evidence。

所有節點必須分開現存、候選、已批准的證據層級。`Binding` 的檔案行號只可作提示，不能充當永久身分；需穩定 key + 所在符號／元件／結構化路徑，必要時 source snapshot。動態資源及反射應標 `UNRESOLVED_DYNAMIC`，不得因查無靜態呼叫就刪除。

**額外提案（尚未實作）**：在既有 sourceHash 之外加入 `contextRevision`（語義情境版本）及 `bindingRevision`（顯示／操作對映版本），可據以局部判斷既有翻譯和測試證據是否過期。不能把這兩個欄位誤說為目前 `acx-language-pack/1` 的欄位；V1 仍以已實作的 typed key + sourceHash 契約為準。

## 5. LWC-2 候選：三條獨立狀態軸與局部失效

- **來源／映射軸**：DISCOVERED → EXTRACTED → MAPPED → VERIFIED。
- **譯文／語義軸**：MISSING → DRAFT → STRUCTURALLY_CHECKED → SEMANTICALLY_REVIEWED（官方批准為具簽核證據的獨立屬性，不由模型填入）。
- **執行／交付軸**：GENERATED → INTEGRATED → TESTED → RELEASED。

這是 TCL 的提案模型，不是 XLIFF 的 `state` 屬性名稱原樣複製；狀態可以平行存在。不要用一個 `COMPLETE` 遮蔽「有譯文／未接線」「機械通過／語義未核准」「自訂包可用／官方包未批准」等差別。

| 變更事件 | 必須重查的依賴 | 不應強迫重做 |
|---|---|---|
| Source 原文／格式改變 | 此 key 譯文、格式、來源簽核、受影響執行測試 | 其他 sourceHash 未變 Key 的全部譯文 |
| Context 操作含義改變，原文不變 | 此 key 語義／術語及操作審核 | 所有無關 key |
| Binding/元件改變 | 使用點、是否已接線、動作對映、相關畫面驗證；**若語義也變則重審翻譯** | 無關語言包 |
| LocaleResolver／通知 adapter 改變 | Runtime 路徑、切換／fallback、重啟及安全 UI 測試 | 靜態譯文的全部語義重審 |
| 新增 locale | 新語言候選／審核／運行驗證 | 現有來源／其餘語言的全部作業 |

反例：`Remove` 可以表示「從音樂池排除」，也可能是「刪除手機檔案」。即使英文原文沒變，按鈕動作改變仍應使翻譯操作語義審核失效。反過來，只改某個 Compose 佈局不必使所有中日文字重新翻譯。

## 6. 例外、風險及防止無限治理

1. 機械可查：少了 locale XML、字串直接硬編碼、Key/format/plurals/array 錯誤、一般直接與間接資源引用。
2. 須追查：反射、動態 ID、服務／通知不同 Context、系統外部字串、使用者自建歌曲名稱、長字串截斷及 TalkBack。
3. 人工／獨立語義檢查：刪除 vs 排除、貪睡 vs 解除、權限／隱私、資料損失、操作後果。模型只能產生初審與風險排序，不自行偽造批准。
4. 截圖：僅在具體排版／執行問題上作**可選補充證據**；資源缺口與靜態映射不得以「使用者尚未截圖」為阻斷。
5. LWC 優先做**結構化輸出規格／CI 交換契約**，不是新大型平台。現有 Weblate、Android 原生資源、B0 scanner、B1 Validator 已能滿足的能力不重造。
6. 每次結果要保存 `repo SHA`、規則版本、輸入 catalogRevision、產出雜湊及未解決項，讓下一個 Agent 可以重新生成與核對；不可把過期摘要當作最新運行證據。

## 7. Repo 既有基準（歷史快照，非目前全線合併狀態）

- B0／B0-T1 的 2026-11-21 XML 快照：**2,471** base Key、**2,462** 可翻譯、繁中與日文各 **912** 內建定義、各 **1,550** 可翻譯 Key 無該語專屬定義；**369** 帶 format tokens，**43** plurals，**24** 無直接靜態引用，**97** 疑似 UI literal（其中 25 處較可能是自然語言）。
- 這些數量是「資源定義與靜態掃描」而非運行畫面覆蓋率、已審核率或 2026-11-22 精確 APK 字串數；1,550 不代表全部都必須翻譯，需 `SHOULD_TRANSLATE / INTENTIONAL_FALLBACK / NON_TRANSLATABLE / UNKNOWN` 分類。
- G6-Q2：200 source Key × 兩語 = 400 locale rows 的**模型初審**，27 筆待審建議，獨立核准 **0/400**，不能發行經官方認證的 G6 pack。
- B1-C1：`acx-language-pack/1`、typed keys（`string:...`、`plurals:...`、`array:...`）、AtomicFile 匯入與回滾已在獨立 PR 實作；B0-L1 的三態協調器與 B1-U1 編輯器也各在工作分支。未完成完整 B2 Runtime 覆蓋，不得從文件推定已在 `main`。

**精確原始文件（依實際分支，勿假設已合併）：**
- [R5 B0～B2 Runtime 契約](https://github.com/Penguink72/AlarmClockXtreme/blob/integration/acx-r0-product-candidate-20261121/docs/localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)
- [C1 翻譯來源權威與術語](https://github.com/Penguink72/AlarmClockXtreme/blob/integration/acx-r0-product-candidate-20261121/docs/localization/ACX-C1-TRANSLATION-TEXT-EVIDENCE-20261121.md)
- [B0 全 App 資源盤點](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)
- [B0-T1 三語逐 Key 矩陣](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-t1-trilingual-catalog-20261121/docs/localization/ACX-B0-T1-TRILINGUAL-MATRIX-20261121.md)
- [B1-C1 V1 pack validator/repository](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b1-c1-pack-validation-repo-20261121/docs/localization/ACX-B1-C1-V1-PACK-VALIDATOR-AND-STORAGE-20261121.md)
- [B0-L1 語言狀態整合](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-l1-coordinator-integration-20261121/docs/localization/ACX-B0-L1-COORDINATOR-INTEGRATION-20261121.md)
- [G6-Q2 200 Key 審核](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/p31-g6q2-full-200-key-model-audit-20261121/docs/localization/P31-G6Q2-ALL-200-MODEL-CONTENT-AUDIT-20261121.md)

## 8. 導航及下一步

- [LWC-W1｜AlarmClockXtreme 的實際翻譯流程與驗收](ACX-LWC-TRANSLATION-WORKFLOW-20261122.md)
- [LWC-TCL｜狀態樹、工作分工與停止條件](../tcl/ACX-LWC-LOCALIZATION-TCL-ROADMAP-20261122.md)

**下一個可驗收成果**應是基於 B0 生成可重建的 `Key → Source → Context → Binding → Locale → UI/Service` 依賴映射，並產生未翻譯／硬編碼／未接線／版本過期清單；僅發現現有工具無法表示的特定關係時，才擴充 LWC schema。