# B0｜Coding Agent 實作拆分與管控

Repo：`Penguink72/AlarmClockXtreme`  
Branch：`feature/playback-history-rotation`  
基準：`a0cc501bcc3514ffd36483ca0b6e266aa234aaff`  
狀態：**設計 backlog；沒有原始程式碼修改**。

## 角色分工

- **Agent A（ZalJatni）**：外部只讀觀測，若沒有實機 ADB/metadata 日誌，能力裁定為 UNKNOWN；不修改第三方 APK。
- **Agent B（Implementation）**：本 Fork 的 Android Kotlin／Room／Media3 實作；逐階段交付，不碰 `main`。
- **Agent C（Verifier）**：核對 B 的每個結論是否有檔案與測試證據；獨立檢查競態、fallback、資料庫 migration、備份相容性；只讀 review。

## P0：固定基線，先做

**實測進度（2026-11-11）：[P0-BASELINE.md](P0-BASELINE.md)。嚴格依賴驗證未通過；候選 metadata 診斷版曾編譯 APK、執行 675 項測試（1 項既有睡前提醒測試失敗）。未交付正式 APK；先處理依賴信任清單與測試基線，不開始 P1。**

1. 在獨立 worktree／clone 鎖定本專案當前 branch SHA。
2. 依 [README.md](../../README.md) 使用 JDK 17/21、Android SDK 36、Gradle wrapper。
3. 運行 `./gradlew :app:testPlayDebugUnitTest`、`./gradlew :app:assemblePlayDebug :app:assembleFdroidDebug`；若有實機／emulator 再執行 instrumentation。
4. 紀錄原始失敗、缺少環境／權限及不相關 flaky cases；保留 log。
5. 不修改正式手機或正式鬧鐘設定。

交付：`P0-BASELINE.md`、原始測試輸出、確認發現和阻礙。

## P1：每日選曲純邏輯

輸入：已正規化候選、alarmId、localDate/timezonePolicy、保存狀態與注入亂數。  
輸出：第一首、後續選擇狀態、選曲理由。  
範圍：pure Kotlin + JVM test；尚不碰 AlarmService。  
驗收：D-01～D-05，至少 D-01/D-03 必須先通過。

## P2：曲目級事件資料

建立 Room entity/DAO/repository，版本 v24→v25 並輸出 schema。  
要求：唯一 ID、`sessionId`、`playIndex`、`fireId`、實際開始證據、完成／中斷語義、查詢及保留上限。  
驗收：H-01～H-07 中 DAO／遷移部分及 R-01/R-02；沒有 migration PASS 不進 P3。

## P3：同次連播對接

讓 end-of-track 採明確互斥策略，連播回呼帶 session generation/目前播放器身分。維持 Media3 watchdog、Spotify/radio 既有分支、MediaPlayer fallback、auto-silence、音量與貪睡行為。  
驗收：S-01～S-10，特別 C-01～C-05 競態。  
注意：如果目前的播放器抽象不夠，優先加入最小封裝，不應改寫整個 service。

## P4：UI、歷史查詢與備份

- 模式選擇 UI，舊設定預設維持舊行為。
- 歷史讀取、重播、清除（與刪檔分離）。
- 明確決定備份中是否包含選曲狀態／播放歷史，避免私密歷史無意同步；舊備份向前相容。
- 驗收：H-06、R-03 及人工互動。

## P5：實機可靠性

白天，以 2～3 首短 WAV 測試螢幕鎖定、手動解除、貪睡、連播、重啟、權限失效、Doze。**原本正式鬧鐘持續使用，沒有實機證據前不可把測試版當唯一叫醒工具。**

## Agent 工作規則

1. 每輪先輸出將改動的檔案、目前基線、風險與對應測試。
2. 只允許改這個 feature branch；禁止對 `main` push/merge，禁止改上游。
3. 每次提交只涵蓋單一可驗收能力；不得跨階段混改。
4. 不能以提示詞取代資料庫約束；播放事件需要資料層的 unique key。
5. 禁止刪除資料庫、重置使用者鬧鐘、安裝到用戶手機、提交密鑰或憑證。
6. 未經允許不得跳過失敗測試、降低警告層級、強推或變更依賴以掩蓋錯誤。
7. 每輪報告：改動檔案清單、Commit、測試命令與真實結果、失敗證據、尚未處理接縫。
8. 若使用另一個模型繼續，先閱讀本文件及現有 Git commit；不許從口述猜測未提交的改動。

## 尚需作者決定，不擅自硬寫

- 同日固定首曲的日期政策：手機當地日期 vs 鬧鐘固定時區。
- 不重複範圍：避免前一天同首、整池輪完才重複、或最近 N 首。
- 手動「下一首」是否列入首版。
- 切歌是完全自然結束後切換，還是允許交叉淡入淡出（首版建議無交叉淡入淡出）。
- 歷史保存筆數／天數與是否在 JSON 備份中輸出。
- 只有 1 首歌時連播行為（首版建議播放完重頭再播，維持響鈴直到解除）。

## 下一輪只讀驗證任務

請獨立 Verifier 對照程式碼確認：所有源碼路徑存在、`fireId` 在 snooze 前後語義、`seekToRandomPoolOffset` 與 end policy 的優先序、`stopAlarmPlayback()` 釋放順序、任何本文件不成立的假定。先回報 discrepancy，再授權開始 P0。
