# G1｜自訂音樂隨機連播的最小實作工作單

狀態：**T0 已規劃；未修改 Kotlin／UI／資料庫**  
前置：G0 可穩定以嚴格驗證產生可下載 Debug APK。  
本輪不做：G2 歌名歷史頁、G3 每日輪換、Spotify／網路電台／YouTube 連播、MP4 影片播音。

## 使用者操作契約

1. 設定一個鬧鐘，從手機內部儲存或 microSD 選取 3 首以上的 MP3／WAV。
2. 開啟「隨機連播」模式並保存。
3. 鬧鐘響時隨機選歌曲，**一首播完自動播放另一首**；一輪歌單播完後繼續循環，直到解除／貪睡／既有自動停止機制。
4. 解除後不得再有下一首突然播放。
5. 新模式為**自選啟用**，沒有啟用的舊鬧鐘維持原本行為。

目前先不承諾每圈都重新洗牌：Media3 原生 shuffle 一場會建立隨機排序，`REPEAT_MODE_ALL` 循環該排序。這符合「隨機播放＋自動連播」，想每輪重新洗牌可放後續版本。

## 原始碼已證實的接點

- [Alarm.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/model/Alarm.kt)：`ringtonePool` 逗號分隔 URI、`sanitized()` 去重與最多 20 首、`dismissAtRingtoneEnd`。
- [RingtonePickerSheet.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/RingtonePickerSheet.kt)：已用 `OpenDocumentTree` 和 `takePersistableUriPermission` 取得資料夾權限。
- [RingtoneFolderStore.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/RingtoneFolderStore.kt)：能列出選取資料夾內的可辨識音樂檔，但**現有實作只列該資料夾直接子檔案，不遍歷所有子資料夾**。
- [AlarmEditAdvancedSection.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt)：歌曲池可顯示、移除，但新增主要是手動貼入 URI；需易用的勾選入口。
- [AlarmService.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt)：`startAudio()` 每次 `pool.random()`，`startMedia3Tone()` 只裝載一首 `MediaItem`，單曲循環或播完解除。
- [AlarmPlaybackPlayer.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmPlaybackPlayer.kt)：現有音量／seek／release 抽象；尚未提供歌曲清單 callback。
- [AlarmDatabase.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/AlarmDatabase.kt)：Room v24、`exportSchema=true`。

## G1 架構（不另起完整播放器）

```text
AlarmEdit（使用者選歌曲＋開啟隨機連播）
   ↓ 保存 URI 與 G1 模式至 Alarm 設定
AlarmRepository / Room（保留舊設定）
   ↓ 觸發時間仍由原版 AlarmScheduler 決定
AlarmService.startAudio()
   ├─ 舊模式 → 原版隨機一首／單曲循環／播完解除（不變）
   └─ SHUFFLE_CONTINUOUS
        → PoolPlaylistPolicy（篩選去重、0/1/N、限制 20、可測試）
        → Media3 ExoPlayer.setMediaItems(全清單)
             shuffleModeEnabled = true
             repeatMode = Player.REPEAT_MODE_ALL
             ↓ 保留既有音量、通知、watchdog、fallback
             └─ stopAlarmPlayback()／dismiss／snooze／preempt
                → 終止整個 session，拒絕晚到 callback

未來 G2 只在播放器「實際開始新曲」的回呼讀取現在的 MediaItem；
G1 不必先實作歷史、JSONL 或資料庫事件。
```

Media3 既有 API 可用 `setMediaItems`、`shuffleModeEnabled`、`REPEAT_MODE_ALL` 組合；**不可把現有 `REPEAT_MODE_ONE` 當成連播**。官方參考：https://developer.android.com/media/media3/exoplayer/playlists

## G1 分片／建議 Commit

### G1-01｜先定義模式（pure logic＋舊資料相容）

- 建議 per-alarm 新設定 `poolPlaybackMode`：`LEGACY`（預設）、`SHUFFLE_CONTINUOUS`（自選）。
- `LEGACY` 必須保留舊 `dismissAtRingtoneEnd` 的兩種結果：原本的單曲循環／播完解除。
- `SHUFFLE_CONTINUOUS` 時，歌曲結束必須進下一首，而非觸發 `dismissAlarm`；UI 對「播完解除」清楚禁用或說明兩者互斥。不可讓舊布林和新模式同時控制播放器。
- `Alarm` 為 Room entity。若新增 per-alarm 欄位，**需 Room v24→v25 migration、導出 schema、測試舊資料保留，並同步更新 AlarmEditViewModel、備份讀寫**。不因「播放歷史可遺失」而放棄既有鬧鐘設定的相容性。
- 替代方案（僅供審查）：把新模式存在獨立 per-alarm preferences 可免 DB migration，但容易造成狀態分裂、備份與刪除不同步；建議採 Room。

### G1-02｜歌曲選擇 UI 接線

- 優先沿用 `RingtonePickerSheet`／`RingtoneFolderStore` 的系統選取、持續授權，不要求使用者自己貼 `content://` URI。
- 在鬧鐘「聲音」頁提供「加入歌曲到播放清單」的多選入口；可預覽、去重、刪除、顯示首數。
- 首版沿用既有 `ringtonePool` **最多 20 首**：3 首基本驗收，0/1/20 首邊界測試。若要整個音樂庫數百首，必須另開歌曲清單持久化方案，不能假稱現有 20 首 URI 字串支援無限資料夾。
- 目前 `RingtoneFolderStore` 只讀直接子檔案；首版可要求歌曲在所選資料夾直屬層，子資料夾遞迴搜尋另案。
- 確認 URI 在 App 重啟、手機重開後仍授權；SD 卡移除／檔案搬移應進入 fallback。

### G1-03｜實作 Media3 清單播放（核心）

- 把「挑一首」與「組裝一整份 MediaItems」分流，不可直接以 `pool.random()` 覆蓋連播清單。
- 在原本 `createMedia3Playback()` 加**最小的清單輸入**／播放模式支援，仍由原本 service 控制前景、音訊路由、漸強音量。
- 音樂播放清單啟用 shuffle 與 repeat all；`SHUFFLE_CONTINUOUS` 禁用既有 `seekToRandomPoolOffset()`（從各曲開頭播放）。
- 保留 `onReady`、watchdog、MediaPlayer 預設鈴聲備援及原版媒體音量／通話抑制。
- 預留一個「當前歌曲變更」的小型 callback 給 G2 之後用，但不在 G1 寫播放日誌。

### G1-04｜停止、競態與可靠性

- 所有播放 callback 要綁定當前 session 身分或 generation；檢查是不是還在同一場鬧鐘，避免舊 callback 結束新鬧鐘或重新開始音樂。
- `dismiss`、`snooze`、另一個鬧鐘搶占、auto-silence 均不可留下會繼續切歌的播放器。
- 池為空→現有預設鈴聲；僅一首→正常循環，不可無限重新建 Player。
- 任何 URI 無效／解碼失敗→有界處理並優先確保仍可響鈴（可直接走既有預設音源 fallback）；不因無效曲目而完全靜音。
- 本次僅支援本地可讀 MP3/WAV（其他已支援格式可沿用，但不作為首版必過項）；Spotify、HTTP radio 不走新連播分支。

### G1-05｜自動測試與 APK

- pure Kotlin 測試：0、1、2、3、20 首，去重、模式切換、資料相容、隨機順序基本不變條件。
- Media3／Service 測試：三首合成短音檔能順序切換；single-file fallback、壞 URI、解除後不再播、貪睡後不再播、兩個鬧鐘搶占。
- Room migration：v24→v25 舊鬧鐘資料不變，新欄位預設 `LEGACY`；備份匯入保留舊行為。
- GitHub Actions：嚴格模式產生新的 G1 Debug APK；記錄測試 pass/fail 與原版已知 1 項失敗分開。
- 白天手機：以三首音檔設定測試鬧鐘，關屏、響鈴、自然切歌、解除、重開 App／手機再測一次。

## G1 驗收契約

| ID | 操作 | 預期結果 |
|---|---|---|
| G1-A | 於手機／microSD 選 3 首 MP3/WAV | 可直接勾選並保存，不需要手貼 URI |
| G1-B | 啟用連播並等待鬧鐘觸發 | 自動依隨機順序切換多首，不是單曲循環 |
| G1-C | 全部歌曲播放完 | 清單繼續循環；至少 2 首時不只停在同一首 |
| G1-D | 播放時按解除／貪睡 | 音樂立即停止，不得有晚到的下一首 |
| G1-E | 壞 URI／記憶卡移除 | 有可聽見的預設音源 fallback，不崩潰 |
| G1-F | 舊鬧鐘未選新模式 | 原本單曲／播完解除行為維持 |
| G1-G | app 重啟、資料庫 v24 升級 | 已設定鬧鐘及音源保留；Room migration 通過 |
| G1-H | GitHub Actions build | 可下載 G1 測試 APK；與原版測試結果區分 |
| G1-I | 手機鎖屏與正常響鈴 | 用戶真機驗收，不能只用 unit test 代替 |

## 關鍵決策與不做事項

- 先做「單場次隨機排序＋全部循環」，不先做每日跨日輪換／每圈重新洗牌。
- 先保留 **20 首** 舊歌曲池上限；要無上限歌曲庫再做獨立存放和資料夾同步。
- 音樂歷史的需求已簡化為「開始播放就記錄」，但它屬 **G2**；G1 不做完成／中斷／失敗事件與資料庫。
- 不重寫 `AlarmScheduler`、整份 Compose UI、MediaPlayer fallback、電話／振動／精準鬧鐘機制。
- 不碰上游與 `main`，每一片葉一個小 Commit，只有驗收成立才合併。

## Agent 交接順序

1. Agent B：只讀確認本文提及的程式 API／路徑、備份欄位及 migration 影響，提交缺口修正建議。
2. G0 通過後，Agent B 依 G1-01→05 逐片提交，不一次重構整個 service。
3. Agent C：獨立核對「舊行為相容、可靠響鈴、解除競態、SD 授權與 CI 真實結果」。
4. 作者在手機上做 G1-I；未實測記 `NOT RUN`。
