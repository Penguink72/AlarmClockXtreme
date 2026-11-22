# ACX L11-7＋L12-2｜雙枝共同來源整合驗證（2026-11-22）

> 整合驗證分支；**沒有合併 main，沒有正式 XML 修改、語義核准、Android Runtime 或新 APK。** 此分支從 L11-7 [PR #50](https://github.com/Penguink72/AlarmClockXtreme/pull/50) 的成功 HEAD `33132d4cc30b80ebf32f4ac6d9636f98db4050be` 建立，逐檔帶入 L12-2 [PR #49](https://github.com/Penguink72/AlarmClockXtreme/pull/49) 的程式碼探針與文件，不反向修改兩條既有 PR。

## TCL Root／分支結合

兩份獨立 PR 均從 L12-1 [PR #48](https://github.com/Penguink72/AlarmClockXtreme/pull/48) 衍生，但其中 L11-7 擴充 116 Key 的模型候選，L12-2 探針分析的是 L11-6 基準 2,074 筆候選。若直接把兩份獨立成功 CI 當作組合成功，會漏掉 L12 語義證據表更新和 CI workflow 相同檔案的衝突。此整合分支用**同一個 checkout SHA**重建 B0/T1、L10、L11-1～7、L12-1 QA 與 L12-2 行為探針，並同時執行原有 G4/G5 斷言。

| 驗收面 | 共用分母／證據 | 不代表 |
|---|---|---|
| L11 候選 | 2,306／3,100 筆待審 zh-TW/ja 候選；794 筆尚無候選 | 不是內建 App 翻譯完成率 |
| L12-1 結構檢查 | 對當期 2,306 筆候選重新計算來源、printf 格式重複次數、Unicode、轉義、詞彙提示 | 不等於 aapt2、真實 UI 或批准 |
| L12-2 操作探針 | 14 組 Kotlin/DAO 靜態源碼、fileRevision、操作聲明及受限結論 | 不等於全量 2,306 筆都經真實語義審核 |
| 發行 gate | 0 獨立核准、0 正式 XML 修改；無 runtime/語音/服務更動 | 不能交付一個有新譯文的 APK |

## 指令

```bash
node --test scripts/localization/l11-b7-visible-residual.test.mjs
node --test scripts/localization/l12-structural-semantic-evidence.test.mjs
node --test scripts/localization/l12-b2-code-behavior-probes.test.mjs
node scripts/localization/l11-translation-worklist.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
node scripts/localization/l12-structural-semantic-evidence.mjs --out-dir build/localization/L12_PRELIMINARY_QA_NOT_APPROVED
node scripts/localization/l12-b2-code-behavior-probes.mjs --out-dir build/localization/L12_B2_CODE_PROBES_NOT_APPROVED
```

## 後續

1. L11-8 先處理剩餘的 794 個 locale 工作列中真實可見、非專有代碼的候選，含起床挑戰、計時器及分享操作等；每次只改獨立候選 JSON 和 Node 測試。
2. L12-3 應逐項處理 CI 的內容/格式提示；對高風險語義保留實際程式 callsite／資料動作與適任審核者簽核；完整來源與譯文及 Binding 版本變動時重設批准。
3. 只有通過獨立語義審核、Android XML/format/plurals/array/aapt2 和 L13 Runtime 接線後，才進入 L14/L15 APK 驗收。

**整合完成定義：** 同 SHA 通過全部 Node 層檢查、兩份 Artifact 可重建，並能辨識 2,306 個翻譯候選與原 14 組程式行為證據，不提前授予發行權。
