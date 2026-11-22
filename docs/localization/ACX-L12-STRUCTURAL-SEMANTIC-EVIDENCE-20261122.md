# ACX L12-1｜全量翻譯結構 QA 與操作語義審核證據（2026-11-22）

> 這是 L12 第 1 個可執行階段，不是 L12 完整批准與發行。只做 Node.js 機械 QA、G1 術語提示、L10 使用點關聯、可檢索的高風險審核佇列及人類審核空白權威文件；不修改 Android 內建 XML、Kotlin、Room、Media3、鬧鐘排程或 APK。本分支疊加在 L11-6 Draft PR #47 之後，源基準 SHA 5a67498a0cfd8eef675bde93d6a95b61b696fb0d。

## TCL Root／枝／業

Root：把 L11 已有的全部繁中／日文候選從「格式大致符合」推進到「可逐筆查證何以能／不能發行」；不能只靠模型初審、Node 測試或術語字串相似度宣稱語義正確。

| 枝 | 可實際執行的檢查 | 權威限制 |
|---|---|---|
| L12-S：Source | B0-T1 SourceHash 與 L10/L11 catalogueRevision 一致 | 不對來源自行更正、也不推論 runtime |
| L12-F：格式 QA | 比對 printf placeholder 的**出現次數**（L11 先前只比對去重集合）、檢查 Unicode、無效 Android 反斜線、保留換行線索、百分比、rich XML | 通過只表示機械候選，尚未通過 aapt2 |
| L12-G：術語線索 | 使用現有 G1 18 個概念，對 Snooze／Dismiss／Ringtone／Playlist 等提供概念及候選詞提示 | G1 自身無獨立批准，詞不相同≠錯譯 |
| L12-C：使用點 | 從 L10 Binding 取 path、line、symbol、revision，與每筆譯文／英文雜湊一起持久化 | 靜態字面使用點≠已驗證操作後果 |
| L12-R：審核佇列 | 依資料刪除／還原、鬧鐘操作、私隱與外部連線、音樂來源、一般 UI 的優先序建議人工審核 | 人類審核身分、時間、決定、授權仍需外部獨立證據 |
| L12-X：發行防線 | 明確標記 reviewerIdentity=null、independentSemanticApproval=false、officialReleaseEligible=false；保留 G4/G5 正式 gate | 不能自動核准官方 XML／語言包／APK |

業：使用者要求從歷史記錄取出音樂建立播放清單、以資料夾管理音樂來源；清除統計不是刪鬧鐘，從鈴聲池移除不是刪手機媒體，附加還原不等於覆蓋舊鬧鐘。L12 的靜態關聯只能指出『應查哪段程式』，不能取代真實互動證據。

## 工程交付

- scripts/localization/l11-translation-worklist.mjs：將原先 CLI 零散讀取整合成可重用 loadL11Inputs(root, sourceSha)，保證 L11 和 L12 共用相同候選分母。
- scripts/localization/l12-structural-semantic-evidence.mjs：完整 QA 與審核佇列產生器，資料來源是 B0-T1、L10、G1、L11。
- scripts/localization/l12-structural-semantic-evidence.test.mjs：七項全量分母／版本雜湊／行為風險／格式 multiplicity／Unicode／越權批准負向測試。
- .github/workflows/lpg1-g5-translation-pipeline.yml：加入 L12 Job 與獨立 artifact。

執行命令：

```bash
node --test scripts/localization/l12-structural-semantic-evidence.test.mjs
node scripts/localization/l12-structural-semantic-evidence.mjs --out-dir build/localization/L12_PRELIMINARY_QA_NOT_APPROVED
```

Artifact 名稱：acx-l12-preliminary-qa-review-evidence-not-approved。包含：
- l12-evidence-review-queue.json：完整來源、翻譯、來源/譯文/review 版本及使用點資訊；
- l12-evidence-review-queue.tsv：依風險排序供人類搜尋／編輯的審核交接表；
- l12-mechanical-summary.md：結構問題分布，包含潛在的術語不一致；
- l12-review-authority.json：空白獨立批准紀錄；release gate 維持 BLOCKED。

## 工程基準與預期數量

承接 L11-6：英文 base 2,471／可翻譯 2,462；兩個 locale 缺專屬 XML 的工作列總數 3,100；已有未審核候選 2,074（繁中／日文各 1,037）；仍無候選 1,026。正式 locale XML 既有定義 1,824 筆保留不變。

上述數字必須在**本分支最後 HEAD** 同一 SHA 的 CI 印出 L12_STRUCTURAL_AND_SEMANTIC_QA 後才算實測。機械檢查通過多少筆、結構 HOLD 幾筆、多少筆有術語偏差，是 CI 實際審核報告的數字，不得提前宣稱。

## L12 後續兩關與前置阻擋

1. L12-2 真正的 Context/操作驗證：逐條檢查高風險 Kotlin/Compose/Service 的事件、刪除、排程及外部傳輸路徑，保留相應審核證據與來源修訂；對有歧義的字串拆分 Source Key 或改候選，並觸發相依差分失效。
2. L12-3 正式資源：待獨立語義批准且 G4/G5 驗證、Android 文字轉義、plural/array、aapt2 與必要畫面測試通過後才生成發行版 XML／V1 pack。不能將臨時 preview 或 QA PASS 冒充正式批准。
3. L13 正常 App 語系接線與 L14/L15 APK 實測仍各有獨立退出條件；L11-6 與 L12-1 成功不改變目前已可用的 P1-P3 音樂功能。

**本片完成標準**：L11-6 全量候選均取得可追溯 L12 QA 審核列、相關格式問題可辨識、G1 用語候選有保留、G4/G5 不核准無來源譯文、Node CI 同 SHA 通過。
