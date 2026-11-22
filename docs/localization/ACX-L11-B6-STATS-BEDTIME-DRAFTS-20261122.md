# ACX L11-6｜統計與睡眠規劃雙語候選（2026-11-22）

> 本文件只代表 L11-6 的未審核翻譯候選、Node 工作表與 CI。沒有修改 Android 正式 XML、Kotlin Runtime、音樂池、鬧鐘排程或發布 APK。本分支接續 L11-5 Draft PR #46，起點 SHA 638600f28f2e22da5cfa5075f829afd97823542e。

## TCL 枝與實際交付

| 枝 | 成果 | 仍待驗證 |
|---|---|---|
| L11-6-S | statistics/history 67 Key × zh-TW/ja = 134 候選 | 歷史清除與實際鬧鐘刪除的差別、統計指標單位及 UI 語義 |
| L11-6-B | bedtime/jet lag 81 Key × 2 = 162 候選 | 勿擾模式、睡眠目標、時差與時區、時間格式、電量提示 |
| L11-6-Q | 5 項新來源／佔位符／重複／批准／完整分母檢查 | Android XML 解析、字體截斷與真實行為不在 Node L11 範圍 |
| L11-6-G | 原 L11 來源雜湊全量工作佇列重新生成 | 不自行升格既有 G6 或 L11 模型初稿 |

## 分母與驗收

- B0-T1 基準：英文 2,471 Key，translatable 2,462，繁中與日文各缺專屬 XML 1,550，原始缺漏合計 3,100 locale rows。
- L11-1～5 + G6 已有候選 1,778 locale rows；本次新增 148 Key / 296 rows，累計應為 2,074 rows，剩餘應為 1,026 rows。
- 原本 1,824 個 Android 繁中與日文專屬 XML 定義不得覆寫；所有 2,074 個翻譯候選均無獨立人類語義批准。正式資源新增 0、App Runtime 更動 0。
- 上述數字是驗證期望值，必須以本分支最後同 SHA 的 GitHub Actions `L11_TRANSLATION_DRAFTS` 和 `L11_B3_DISPOSITION_CANDIDATES` 為準。

## 產物

- `localization/candidates/l11-b6-statistics-drafts-20261122.json`：67 個統計與歷史 Key 的兩語草稿。
- `localization/candidates/l11-b6-bedtime-drafts-20261122.json`：81 個睡眠、勿擾、時差 Key 的兩語草稿。
- `scripts/localization/l11-translation-worklist.mjs`：新增 `batch6` 輸入，保留以前批次和來源版本。
- `scripts/localization/l11-b6-stats-bedtime.test.mjs`：5 項新增的驗證測試。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：新增 L11-B6 CI 測試及完整 Artifact。

## 後續與交接

1. L12 應直接讀取同源 L11 工作列，為各條候選生成占位符、Android XML 轉義、危險行為／術語、來源／Binding 對照的審核清單。
2. 特別核對 `stats_removes_recorded_alarm_outcomes_resets` 只清除統計結果而不刪鬧鐘，`stats_action_snoozed` 和 `stats_action_dismissed` 的含義，`bedtime_battery_warning` 的百分比轉義，`bedtime_do_not_disturb_at_bedtime` 的實際 DND 權限。
3. 未經正式內容審核與 Android 格式驗收的譯文不能寫入內建 XML；自訂使用者語言包仍維持非官方信任等級。
4. L13 接線、L14 Android 實際畫面驗收、L15 APK 需另外完成；本輪不改既有播放記錄與鬧鐘功能。

**完成定義**：148 Key × 2 候選成功通過精確來源及格式測試，完整佇列可重生，無正式翻譯批准或新 APK。
