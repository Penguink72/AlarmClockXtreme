# ACX L12-8B｜風險分層審核政策與診斷抽樣（2026-11-23）

> L12-8B 是使用者選定的風險分層審核方案。**目前交付的是可重現的審核政策提案與審核工具，並非正式翻譯政策生效／簽署／放行。** 不修改既有 L12-6 逐列正式批准條件、G1／G4 權威帳本、L13-B 正式 XML Writer、App 音樂及鬧鐘程式。

## 上游來源與架構

- 上游：L12-6 PR #61 的 3,100 筆候選（英文來源 B0 T1，繁中／日文各 1,550 筆），及 L12-7 PR #63 的 1,550 張中日對照 Key 卡、114 個分批，版次固定。
- 政策：`localization/policies/l12-b8b-risk-tiered-review-policy.proposal.json`，狀態 `PROPOSED_NOT_AUTHORIZED`。
- 實作：`scripts/localization/l12-b8b-risk-tiered-policy.mjs`。輸出政策雜湊 `policyDigest`，附 `reviewSetDigest` 與 `batchPlanDigest`，任何來源、譯文、政策變更皆需重產抽樣與審核證據。
- 原始 L12-7 P0／P1／P2 完全保留逐列審核；對 P3 新增動作／權限動詞與 Android 型別／警示複查，複查異常必須移入逐列審核，不得繼續抽樣。
- 剩下的 P3 依 `featureFamily` 分層，以雙語 Key 卡做抽樣單位；每個被抽到的 Key 一次顯示繁中＋日文，但**兩個語系仍各自需要審核**。
- 抽樣採來源／譯文／政策雜湊鎖定的 SHA-256 重現式排序，原則每層約 10%，每個非空層至少 2 個 Key。這是可重現的**診斷抽樣**，不是隨機統計推論、不是翻譯正確率或置信區間。

## 第一輪實際分層計算

以下是同一個已經完成的 L12-6／L12-7 來源集在政策提案 v0.1 的輸出：

| 階段 | Key 對照卡 | zh-TW／ja 語系列 | 處理方式 |
| --- | ---: | ---: | --- |
| P0：操作／安全／資料 | 467 | 934 | 逐列審核 |
| P1：媒體播放／音樂來源 | 26 | 52 | 逐列審核 |
| P2：Android 格式／警示 | 58 | 116 | 保守逐列審核，格式可先機械檢查 |
| P3：額外保守風險複檢而升級 | 53 | 106 | 逐列審核 |
| P3：診斷抽樣 | 103 | 206 | 12 個功能群、38 個抽樣批次；雙語一起顯示 |
| P3：未抽中、待政策審核 | 843 | 1,686 | **仍未審核，不可發布** |
| **合計** | **1,550** | **3,100** | |

- 初始人工重點：1,208 筆必須逐列審核 + 206 筆 P3 診斷樣本 = **1,414 筆**。這是可能節省導航／人工處理成本的**優先工作清單**，不是說只檢查 1,414 筆就可以直接發布全部語系。
- 原 L12-6 中 986 筆非一般 UI 的風險列未失去保護；額外再升級 106 列原本的 P3 候選。
- 任何診斷樣本標記「需要修改」「需要更多上下文」：該 `featureFamily` **全群**轉入逐列審核。抽樣未完成則仍待審；「看似可用」只算未簽名暫存筆記，絕不解鎖發布。
- 發現跨功能的安全分類錯誤，必須擴大檢查全部受影響來源；不能因為同一英文或同一術語而跨語系自動批准。

## 交付檔案、使用方式

工作流：`.github/workflows/l12-b8b-risk-tiered-review.yml`，以 GitHub Actions Node 22 執行 L12-6、L12-7、L12-8B 所有單元測試並發布 Artifact：
`acx-l12-b8b-proposed-risk-tiered-review`。

Windows 本機只要 Node.js，不需要 Python：

`node scripts/localization/l12-b8b-risk-tiered-policy.mjs --out-dir build/localization/L12_B8B_UNAUTHORIZED_DIAGNOSTIC_REVIEW`

Artifacts 與本機產出內容：

- `policy-proposal.json`：政策提案與約束。
- `risk-plan-summary.json`：完整統計、政策／資料 digest、發布阻擋狀態。
- `mandatory-full-review-queue.json`：保留原文、逐語譯文與修訂雜湊的必審清單。
- `p3-risk-rechecks.json`：從 P3 升級為逐列審核的 53 個 Key 與原因。
- `diagnostic-strata.json`：12 層的完整、已選與未選 Key 索引。
- `human-focus-worklist.tsv`：必審和診斷樣本的優先順序表。
- `p3-diagnostic-sample-desk.html`：可直接在桌面瀏覽器開啟的 **103 張 Key 的繁中／日文離線抽樣工作台**，含匯入／匯出未簽名審閱筆記。正式 P0／P1／P2 詳細審核可延用 L12-7 的完整離線工作台。

從離線工作台匯出的筆記，可用：

`node scripts/localization/l12-b8b-risk-tiered-policy.mjs --notes <review-notes.json>`

取得 `L12_B8B_UNTRUSTED_PROGRESS`（未簽名暫存進度、整群升級狀態）。若筆記版本不符、偽造批准或資料結構不正確，則拒絕；即使樣本標記全部看似可用，仍顯示正式批准 0。

## 發布權限和 L12-8B 真正生效條件

**政策 B 的選擇只授權工程設計和分層審核方案，不代表授權跳過原有獨立語義批准。**

1. L12-8B 政策文件目前僅 `PROPOSED_NOT_AUTHORIZED`，不得自行修改成正式批准，亦不得在同一個普通 PR 內自簽權威公鑰。
2. 如日後決定讓「抽樣＋授權政策」取代部分逐列批准，需建立**受保護的政策批准流程**，載明政策版次、適用功能群、抽樣結果與異常擴審處置，並由獨立可信 reviewer 授權。
3. 原 L12-6 的 `validateSignedReviewIntake` 仍要求 3,100 筆完整獨立人工批准；L13-B 的受保護 Writer 仍依原閘門執行。後續只有經授權修改、測試驗證與安全審查，才可考慮新的 `POLICY_ACCEPTED` 路徑。
4. G1 Glossary 尚未獨立批准、G4 Ledger 仍未有審核決定。L12-8B **不寫入**任何 G1/G4 權威紀錄，任何分群、CI success 或機械檢查不能冒充批准。
5. Android 真實畫面、文字超長、資源使用位置、背景 Service／通知及 P1～P3 既有鬧鐘音樂行為仍需要 L13-C／L14 等獨立實測。沒有正式可發布 APK。

## TCL 状態

- **已完成枝**：政策草案、二次保守風險複檢、雙語 Key 抽樣、全量覆蓋／重複拒絕、內容／政策版本指紋、問題整群升級、離線 P3 樣本工作台、CLI 測試與 CI。
- **未完成枝**：受保護政策授權者與公鑰、L12-6／L13-B 正式政策替代路徑、真人語義實審、G1/G4 審核入庫、完整 Android runtime 與 APK 驗收。
- **目前的完成定義**：「L12-8B 可供診斷式審核使用」不等於「正式政策 B 已生效」，更不等於「3,100 筆翻譯已批准」。
