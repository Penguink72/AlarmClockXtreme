# AlarmClockXtreme｜P3.1 S0～S4 狀態偏差接縫修補紀錄

- 日期：2026-11-17（Asia/Taipei）
- 分支：`feature/p31-b2-s1r-u12-l1b-u3-u4-u5-20261116`，PR #13（Draft），基底 `feature/p31-l1-trilingual-core-time-locale`
- 本批目的：封閉「畫面顯示成功但資料來源／排程／回放不是相同狀態」以及部分競態接縫。**Code、CI、實機證據分開；不可從程式通過推導手機一定會響。**
- 本批依作者授權 S0 → S1＋S2 → S3 → S4 遠端執行。所有提交都在既有功能分支；沒有合併 main、沒有改 Room v28、沒有更換 Debug 簽章。

## TCL 主枝（T0 設計／T1 已推程式／T2 CI／T3 裝置）

| 枝 | 這輪新增實作（T1） | 對應偏差 | CI Gate |
| --- | --- | --- | --- |
| S0 測試可信度 | 測試等待實際 `Dispatchers.IO` 曲庫來源讀取完成，先驗證 `poolTracks` 已載入，才檢查雙擊只寫一次 | 原 U5 測試在 `loadingPool` 尚未結束時操作，錯誤地測不到 DAO 寫入 | [Run 44112496531](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44112496531)：**3/3 SUCCESS**，code `de00251f` |
| S1 聲音單一草稿權威 | Modal 的「儲存」改為「套用至鬧鐘草稿」；所有正式持久化走整體 Save；`AlarmMusicSettingsDraft` 改動納入離開確認；三語說明；儲存前同時快照 Alarm 與 Music；失敗補償恢復 Alarm/Music/貪睡偏好 | D01、D02、D03、D05、D06、D08（核心流程；仍須 T3 語意實測） | 初版整合 code `87c80882` [Run 44114197333](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44114197333)：**3/3 SUCCESS；838/838 單元測試**。追加補償另須最新 SHA CI |
| S2 排程 ACK | `AlarmScheduler.scheduleWithResult` 回 `EXACT / INEXACT / SUPPRESSED / FAILED`，與保留舊 `schedule` 相容；編輯頁只有已註冊才完成；失敗不得 `onComplete`；新建未註冊會清除新 Alarm/Music/模式，避免重試複製；刻意抑制則明確告知 | D04、首頁其他排程成功語義的基礎 | 同上，最後追加的新鬧鐘失敗補償須最新 SHA CI |
| S3 複製／備份完整性 | 複製鬧鐘連同專用 Playlist 設定及 `AlarmSnoozeMusicModeStore`，全流程連點鎖與失敗清理；備份匯出音樂資料讀取失敗不再靜默輸出空集合，匯入音樂資料不可用／部分失敗不再回報完整成功；加備份、複製測試 | D07、D09、D10 | code `b5a7bed8` 及測試 `82b63bc7`；以最新 SHA CI 作 Gate |
| S4 非同步與寫入時序 | Catalog 載入同步宣告 busy；History Recent/ByDate 共用 request generation 防晚到覆蓋；Playlist 複製、刪除、批量移除、單筆移除、重排、撤銷排序，都在 coroutine dispatch 前 claim 共用 busy；加晚到查詢測試 | D11、D12、D13 | code `7aec0e1f`；以最新 SHA CI 作 Gate |

### 逐段提交
1. S0 [`de00251f`](https://github.com/Penguink72/AlarmClockXtreme/commit/de00251f49cbd90f9cd083f61220d7107cd508e0) — 測試的 IO 等待及可觀測驗收前置。
2. S2 [`5433014b`](https://github.com/Penguink72/AlarmClockXtreme/commit/5433014b4947b6a17452cf6a79c79f7387cabd52) — SchedulingOutcome。
3. S1 [`87c80882`](https://github.com/Penguink72/AlarmClockXtreme/commit/87c80882b79c5e58510e86f788b6ab45fc5c2809) — 單一提交路徑、音樂草稿 dirty、三語文案與測試。
4. S3 [`b5a7bed8`](https://github.com/Penguink72/AlarmClockXtreme/commit/b5a7bed891fba616cce71481414d645f2e31ce3f) — clone + backup fail-closed。
5. S4 [`7aec0e1f`](https://github.com/Penguink72/AlarmClockXtreme/commit/7aec0e1f884711b40eacf87d52763dcd8cbd9c4a) — async request-generation 和 playlist 寫入鎖。
6. S1/S2 補償 [`25ac238c`](https://github.com/Penguink72/AlarmClockXtreme/commit/25ac238c3631a2bdb0977dabc9a4faf57ee8a78b) — 三存儲回滾、新鬧鐘拒絕排程清除。
7. S3 測試 [`82b63bc7`](https://github.com/Penguink72/AlarmClockXtreme/commit/82b63bc71619ad27e28f4f07e3a5551fa601fd49) — clone 自訂清單／貪睡模式讀回、拒絕排程清理。

## 重要契約決策及不可混淆的事實

1. `AlarmMusicSettingsScreen` 現在只編輯草稿；新舊鬧鐘都要回到編輯頁按最終「儲存」才持久化，避免新鬧鐘 `alarmId=-1` 也回 `saved=true`，以及既有鬧鐘先寫音樂卻沒寫 `randomMusicEnabled` 的不一致。這是**刻意變更的 UX 語意**，不可再稱音樂視窗按完「套用」就已永久儲存。
2. `AlarmSchedulingOutcome.EXACT / INEXACT` 是 Android OS 接受登記，**不是 Media3 真正可聽的聲音證據**；`SUPPRESSED` 是合法暫停／跳過／到期，不等同失敗；`FAILED` 是下次登記不成立。舊 `AlarmScheduler.schedule` 保持供背景再排程使用，UI 可靠性入口使用 `scheduleWithResult`。
3. 原 S1 新建鬧鐘最後 Schedule FAILED 可能留下已持久化且在同一 editor 重試生成新 ID 的缺口，後續 `25ac238c` 補清理。這次沒有引入 destructive Room migration。
4. `BackupManager` 寫入全球 AppSettings、Playlists、Alarm 等仍分屬多次程序；已避免**靜默**吞讀取失敗／不完整的音樂欄位，但**不宣稱整份備份跨多層原子性**。匯入中途失敗仍可能有局部已寫資料，應在日後專項交易切片重新驗收。
5. `S4` 的 request-generation 防舊歷史回應回填新日期；不能當成 Media3 音訊響鈴真機測試。

## CI 和接下來的 Gate
- S0 確認 [Run 44112496531](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44112496531) 3/3 SUCCESS。
- S1/S2 初版確認 [Run 44114197333](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44114197333) 3/3 SUCCESS、838/838，0 failures/errors/skipped。
- S3、S4 的早期 Code SHA workflow 曾因同分支排隊／後續 push 被取消；取消不是 PASS 或 FAIL。**最新完整程式 SHA `82b63bc71619ad27e28f4f07e3a5551fa601fd49`** 應由其專屬 [Actions](https://github.com/Penguink72/AlarmClockXtreme/actions) 確認 3/3，並從 unit XML 核對 0 failures/errors/skipped。本文撰寫時最終 Gate **尚未確認**，不得拿早期綠燈替代。
- 若測試失敗，先讀直接 job 日誌，保存測試斷言並做最小修復；禁止 skip/delete 測試、忽略 compiler 錯誤或降安全檢核。
- T3 未驗：亮屏 10 分鐘貪睡再響；多來源播放＋同曲續播/換曲；升級安裝；複製鬧鐘執行效果；跨機備份與 OEM 原廠音分類。T3 非目前作者設定的 Code-Go 阻斷，但**不是 T3 PASS**。

## 尚需保留的非阻斷風險
- 備份匯入「多行與多層存儲」完整回滾仍未原子化；目前是失敗顯示＋局部補償，不應宣稱整機回復無缺口。
- 原本獨立 `Save` 動作移除後，需在最終 UI 手動確認「套用草稿 → 編輯頁儲存」路徑及離開確認語意是否合理，並核對 5 秒完成任務的人類量測。
- 既有首頁啟用／批次啟用的 `schedule()` 仍有可忽略結果的入口；此切片已建立 `scheduleWithResult`，後續應將所有顯示成功的外層入口統一改用此契約。
- 不能把 APK 可編譯、CI unit PASS、或 `Media3.isPlaying` 事件等同物理響鈴、音量、Bluetooth/耳機路由的 T3 證據。
