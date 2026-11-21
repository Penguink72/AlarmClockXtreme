# TCL｜G6-Q2：剩餘 163 Key 的候選對照與完整 400-row 交接（2026-11-21）

## Root

使用者要求繼續 G6-Q2，以現行 200-Key G6 來源完成中日語言包的逐條內容審核。Q2 定位為**模型文字初審**，不可偽裝成獨立人工批准。

**Base：**G6-Q1 Draft PR #28，提交 `d88da4a5`。Q2 使用新分支 `feature/p31-g6q2-full-200-key-model-audit-20261121`；不修改 Android 正式資源、Debug 候選、響鈴核心、Room、G1 術語批准或 G4 審核帳本。

## 分支與交付

| 工作枝 | 本輪完成或建立 | 尚未完成 |
|---|---|---|
| Q2-A Source | Q1 37 Key／74 rows + Q2 163 Key／326 rows，完整 200／400 | 仍需獨立運行時與母語驗證 |
| Q2-B Content | Q2 每 Key 原文、來源雜湊、中日候選、表層判讀、風險與格式標籤；43 Key 另有具體風險說明 | 表層沒有明顯問題不等於語義通過 |
| Q2-C Revisions | Q2 9 筆（6 Key），加 Q1 18 筆，共 27 筆未核准提案 | 候選尚未採用，不能改正式翻譯 |
| Q2-D Formats | 21 Key 的 printf 格式／順序比對，特別注意貪睡分鐘、歷史與清單數量 | 動態字串、複數、截斷、T3 尚未驗證 |
| Q2-E Provenance | Node 每次 CI 從 G6、LP0、S0、當次產生的 G2 與 Q1 取得來源；排除異版與重複 Key | G2 靜態呼叫不等於功能語義保證 |
| Q2-F Deny release | Node 負向測試攔截偽核准、提案擴權、候選竄改；導出 400-row reviewer JSON／TSV | G1 未批准、G6 0／400，G5/G6 release 繼續拒絕 |

## 驗收反例

`stats_snoozed` 雖只寫 Snoozed，真實 `StatsScreen` 顯示 `snoozeRate%`。既有繁中「貪睡率」、日文「スヌーズ率」反而是有上下文的標籤。程式檢查不允許按英文表面文字強迫改譯。

## 下一階段 G6-Q3

優先 14 個 CRITICAL Key（Q1 10、Q2 4）、21 個格式 Key、無靜態呼叫情境的 Key，以及 27 筆候選修訂的實際操作後果；需要獨立審核者、日期、原文／譯文雜湊、母語與上下文證據。不能由本模型自己寫入 APPROVED。

[完整 Q2 內容與 9 筆修訂](../localization/P31-G6Q2-ALL-200-MODEL-CONTENT-AUDIT-20261121.md)
