# B0｜最小改動設計與相容性規劃

> **歷史文件／舊設計已延後（2026-11-11）**：本頁的舊提案、實作階段、測試矩陣或設計假設**不再授權直接實作**。請改用 [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) 與 [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)；舊方案參見 [DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)。原始碼現況與流程盤點仍可作為歷史事實參考。


基準 Commit：`a0cc501bcc3514ffd36483ca0b6e266aa234aaff`  
本文件為 **PROPOSAL**，並未實作。

## 1. 模式建議（相容舊資料）

避免 `dismissAtRingtoneEnd` + 多個布林互斥不明：

- 選曲策略 `dailySelectionMode`：
  - `RANDOM_EACH_FIRE`（舊行為，預設，沿用 `pool.random()`）
  - `DAILY_ROTATE`（新功能，依鬧鐘與本地日／明確時區固定當日第一首，近況避免重複）
- 歌曲結束策略 `trackEndPolicy`：
  - `REPEAT_ONE`（舊 `dismissAtRingtoneEnd=false`）
  - `DISMISS_AT_END`（舊 `dismissAtRingtoneEnd=true`）
  - `CONTINUE_POOL`（新功能）
- 新資料欄位的預設要重現升級前的實際行為。可在資料層保留舊布林作過渡，但不可讓衝突組合進入播放器。**實作前先寫決策表與遷移測試。**

第一版使用本機可讀 URI；Spotify、HTTP(S) 電台、YouTube 音源的跨歌連播不納入本輪，仍沿用舊流程。

## 2. 每日選曲引擎

優先建立與 Android 無關、可用 JVM 測試的 pure Kotlin 策略，建議輸入：
`alarmId, localDate, timezonePolicy, normalizedPool, priorRotationState, seed`。

輸出：
`selectedTrackUri, nextRotationState, reason`。

建議約束：
1. 空池採既有預設音源；單曲不出現死迴圈。
2. URI 正規化遵守 `Alarm.sanitized()`，最多 20 首。
3. 多曲時，在合理情況下不連續重複；確定性測試可注入亂數種子。
4. 同一日期／鬧鐘結果穩定；新一天的首次響鈴再輪換。
5. 同場是否跨午夜不更換已開始的 playlist。
6. timezonePolicy / 夏令時間／手機時區改變之影響必須先決定。

建議不要以 **目前系統時間 + 隨機函數** 直接決定下一首，否則同日重試與程序重啟不具可重現性。

## 3. 同場連播引擎

優先在既有 service 外新增小型 `SessionPlaylistPolicy`／session state，再讓 service 對接，不另寫第二套鬧鐘排程引擎。

狀態建議：
`IDLE → PREPARING → PLAYING → (TRACK_ENDED → PREPARING_NEXT) → STOPPED`；
錯誤轉入 `FALLBACK`，且不可無限重試。

關鍵不變條件：
- 每個聲音回呼綁住 `sessionId`、generation、playbackRef。
- `snooze/dismiss/preempt` 是 terminal transition，之後任何舊回呼只能忽略。
- 自動關鈴限時計算從場次起始時間開始，不因播放下一首重設。
- 每首歌的 start/end 事件與 loop/playlist 轉換定義清楚。
- Media3 成功路徑與 MediaPlayer fallback 保留既有聲音可靠性。
- 同場連播時從歌曲開頭播放；`seekToRandomPoolOffset()` 不應意外縮短整首歌。

## 4. 播放歷史模型（建議獨立資料表）

新增 `PlaybackEvent`，至少包含：
- `eventId`：由可信任的應用邏輯建立唯一識別，NOT NULL / UNIQUE。
- `alarmId`、`fireId`、`sessionId`、`playIndex`。
- `trackUri`、可用時的顯示 `title`（不應假設每 URI 均可取得）。
- `startedAt`、`endedAt`、`result`。
- `source`：LOCAL_POOL / DEFAULT_FALLBACK 等，避免把 fallback 誤記成原候選。
- `startPositionMs`（若舊隨機中段播放需保留證據）。

可評估 `UNIQUE(sessionId, playIndex)`，但必須先確認 sessionId 不會在 snooze 回響、重新啟動後意外重用。不可僅用 `alarmId + 當日日期` 當事件 ID。

寫入原則：
- 僅在已確認實際播放時記 `STARTED`；準備中可另有不可見 attempt 事件，不得冒充播放。
- completed/interrupted/failed 只能合理轉換一次；重入冪等。
- 不確定的崩潰恢復標為 `UNKNOWN`／`INTERRUPTED_UNCONFIRMED`，不得偽造自然完播。
- 歷史在 App 私有資料庫；第一版不新增遠端上傳。
- 清除歷史與刪歌曲／刪鬧鐘設定分離；清除前有明確的用戶動作。
- 建議保留政策上限（例如 5000 筆或 180 日），但須與作者確定；不能直接抄成正式設定。
- DB v24 → v25；導出 schema，納入所有從已匯出舊版 schema 升級的測試，禁止 destructive migration。

## 5. 介面

在 [AlarmEditAdvancedSection.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt) 基礎上加入：
- 每日輪換模式（保留原「每次響鈴隨機」預設）。
- 歌曲結束策略（單曲循環／播完解除／同場連播）。
- 不適用音源的控制禁用與說明。
另增歌曲歷史檢視：日期、鬧鐘、曲名、播放結果、重新播放；優先小型 read-only UI，不更動既有統計頁行為。

## 6. 風險與驗收停止條件

- 若 P0 原專案建置或測試失敗，先固定**原本失敗清單**，不得宣稱由本功能造成，也不得盲目修全專案。
- 若 Media3 的真實播放事件無法穩定識別，先不要實作歌曲歷史「確實開始」承諾；提出含不確定性的狀態模型。
- 若遷移測試失敗，不可以提交資料庫變更到預備合併 PR。
- 若 fallback、鎖屏、精準鬧鐘或貪睡退化，不允許用關閉測試的方式通過。
- 每階段建立獨立 Commit，留 rollback 點；只有作者核准才合併 `main`。
