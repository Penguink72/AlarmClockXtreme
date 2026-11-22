# ACX L12-7｜3,100 列翻譯候選風險分批與離線審閱工作台

記錄日期：2026-11-22。對應 L12-6 Draft PR #61，本工作獨立 Draft PR，不修改 main、不改 AlarmService／Room／Media3，也不發布任何正式語言 XML 或 APK。

## 背景與不可突破的閘門

- L12-6 將中日兩語共 3,100 筆待補專屬定義整理成版次固定的獨立審核工作包；3036 string rows、62 plurals rows、2 string-array rows。
- 上述 3,100 筆只是模型翻譯候選，**獨立語義批准仍為 0**；G1 glossary 未獨立批准、G4 review ledger 仍空白。
- L12-7 的目標是減少**導航、切換、查找上下文、記錄問題**的操作成本，不是透過 heuristic 取代獨立審核。
- 格式相同、來源英文相同、處於同一批次或同一 UI 區域都不表示可以共用批准。

## 已實作的審核分組

輸入來源：

1. L12-6 的完整 `independent-review-packet`，保留每筆 key／locale／type／sourceHash／translationHash／reviewRevision／candidate／risks／glossaryHints。
2. L12-5 的 43 筆 warning disposition，固定對比 L12-6 的版本雜湊。任何同鍵譯文變更或 warning 來源漂移都 fail closed。
3. 同一英文 Key 的繁中與日文放在同一張「對照卡」；**卡片內各語保留各自的獨立待審狀態**。不對跨 Key 或跨語言做自動批准。

一次性 CLI 生成後實測資料：

| 階層 | 內容 | Key 卡數 | 語系 rows | 批次 |
| --- | --- | ---: | ---: | ---: |
| P0 | 鬧鐘操作、資料損失、備份還原、權限與安全 | 467 | 934 | 53 |
| P1 | 音樂庫、播放、播放清單與媒體來源 | 26 | 52 | 7 |
| P2 | 其他 Android 格式、未決語義警示或型別檢查 | 58 | 116 | 12 |
| P3 | 一般 UI 用語 | 999 | 1998 | 42 |
| **全部** | | **1,550** | **3,100** | **114** |

- 986 筆被 L12-6 原始風險規則標成非一般 UI 的工作列，等於 P0＋P1（每 Key 中日兩列）。
- 34 組不同 Key 具有完全相同的英語文字與資源型別；工作台會標注交叉對照，但**不據此自動共享語義批准**。
- 單批 Key 上限：P0 10、P1 16、P2 16、P3 28；分組只供操作，不是研判正確性的證據。
- 審核進度區分 `未標註`、`看似可用（未核准）`、`需修改`、`需更多上下文`、`先略過`；所有狀態仍為 **0 正式批准**。

## 輸出與使用方法（Windows／Node.js，無須 Python）

GitHub Actions workflow：`.github/workflows/l12-b7-review-batches.yml`。

從 GitHub Actions 成功執行頁面的 **Artifacts** 下載：
`acx-l12-b7-offline-unapproved-review-workbench`。

解壓後提供：

- `review-desk.html`：直接在桌面瀏覽器開啟；英文原文、繁中、日文同卡對照，按優先順序批次瀏覽、搜尋、標記問題與匯出 JSON。離線，不向伺服器提交，不寫本機持久儲存；**關閉頁面前請匯出筆記**。
- `review-plan-index.json`：批次索引與統計。
- `review-worklist.tsv`：逐語系 Key 的來源／譯文／警示／待審版本。
- `batches/L12B7-*.json`：114 份批次詳細來源與譯文證據。

若本機有 Node.js 且 checkout 本 PR，可重新產生：

`node scripts/localization/l12-b7-review-batches.mjs --out-dir build/localization/L12_B7_UNAPPROVED_REVIEW_DESK`

HTML 匯出的 JSON 屬於 `acx-l12-b7-untrusted-review-notes/1`，可以校驗版本：

`node scripts/localization/l12-b7-review-batches.mjs --validate-notes <筆記JSON路徑>`

**上述命令不簽署、批准或發布譯文。** 如果原文、翻譯或審核版本改變，舊筆記會被拒絕；這不同於受保護 L12-6 的 Ed25519 審核流程。

## CI 證據與工程邊界

- Node 測試包含：3,100 筆完整對映、1,550 張中日對照卡、986 筆敏感風險列、分批尺寸、來源相同的異 Key 不被共用批准、版本漂移、重複筆記、偽造批准與未授權標籤。
- CI 重新生成所有工作台檔案，並驗證 L12-6 外部簽章缺失時**正式 release 仍被阻擋**。
- 本分支沒有修改：`localization/governance/g1/glossary-contract.json`、`g4/review-ledger.json`、`l12/independent-review-ledger.json` 及 `app/src/main/res`。
- **L12-7 不聲稱完成獨立審核。** 它完成的是可追溯的審核排列、相似項提示、離線人工備註工具與工作量降低所需的前置資料。

## 後續決策

L12-8 可在**明確取得使用者批准修改審核政策**後，設計低風險抽樣、重複模式的規則化批准與高風險逐列審核的權限分級；在批准政策修訂前，L12-6 仍要求每個 key＋locale 有獨立的人類語義決定。任何抽樣建議或相似度群組不能直接解鎖 L13-B 的正式 XML writer。

### 實測分支基準

- L12-6 PR #61：`feature/acx-l12-b6-independent-review-intake-20261122`，HEAD 起點 `b349a107380d3d22cb1dc2f592b28319ffdc37ba`。
- L12-7 獨立分支：`feature/acx-l12-b7-risk-batched-review-20261122`。
