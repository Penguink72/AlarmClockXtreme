# ACX L11-10｜57 Key／114 語系列最終候選補齊（2026-11-22）

> 隔離未審核候選，不更動正式 Android 資源、G1/G4 帳本、鬧鐘排程、音樂庫、Room 或 UI。不代表翻譯語義獲批准。

## 來源與實際缺口
- 源自 L11-9＋L12-4 整合 PR #57 HEAD `b6b37257bcb1fe1e82f7c09a5188c65d7053efb8`。
- L11-1～9 已有 2,986 筆中日未審候選，剩餘 114 個 locale rows = 57 Key × 2。
- **25 個 string、31 組 plurals、1 個 string-array**；不是全部普通字串。
- Source Preview 和原始 XML 固定在每筆候選中。驗證時比對 B0-T1 的 `sourcePreview` 與 `sourceXml`，不可沿用已變更來源。
- 來源依據為 `scripts/localization/l11-b10-residual-closure.mjs` 重新執行的確定缺口，不憑推測補 Key。

## 交付
- `localization/candidates/l11-b10-strings-drafts-20261122.json`：25 個一般字串候選，部分標示保留原文識別值，**仍待語義批准**。
- `localization/candidates/l11-b10-plural-drafts-20261122.json`：31 組複數資源，分別保存 `one`／`other`，逐分支檢查格式 token 數量。
- `localization/candidates/l11-b10-array-drafts-20261122.json`：15 條勵志文字組成一個字串陣列，兩語相同項數與順序。
- `scripts/localization/l11-b10-typed-candidates.mjs`：依實際 L11 殘餘佇列驗證 114 個 key+locale 對應，未列出的 Key／重複／缺少／來源漂移／占位符不符直接失敗。
- `scripts/localization/l11-b10-typed-candidates.test.mjs`：正向 3 類資源與來源漂移、格式、類別、未授權批准負向測試。
- `.github/workflows/l11-b10-residual.yml`：單一 HEAD SHA 的 Node tests、輸出 `build/localization/L11_B10_RESIDUAL/` 未審 XML、Android 真實 `aapt2 compile`、artifact。
- **不將生成的 staging XML 複製至 `app/src/main/res`**。

## 實際證據
- GitHub Actions [Run #45037256221](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/45037256221)，HEAD `0946716b950889c0d41a1d475eaf39c190ec9da3`，success。
- L11-B10 候選：string 50、plurals 62、array 2，共 114；加上 L11-1～9 的 2,986，**候選覆蓋 3,100／3,100**。
- Node 驗證與 Android `aapt2` 隔離編譯通過；獨立批准 **0**；正式 XML **0**；完整 Gradle Build / Android Runtime **未測試**。

## 下一步
L12-5／L12-6 對固定 sourceHash、上下文與高風險行為做獨立內容審核；L13-B 才能設計受保護正式 writer，必須另外把原有 L11 字串 staging 與本次三類型候選正確整合。不得以本次「候選覆蓋 100%」冒充「正式語系覆蓋 100%」。
