# B0｜既有流程、提案流程與競態

> **歷史文件／舊設計已延後（2026-11-11）**：本頁的舊提案、實作階段、測試矩陣或設計假設**不再授權直接實作**。請改用 [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) 與 [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)；舊方案參見 [DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)。原始碼現況與流程盤點仍可作為歷史事實參考。


版本：`a0cc501bcc3514ffd36483ca0b6e266aa234aaff`。本文件只描述讀到的程式與待設計流程，**不是完成實作的證據**。

## 1. 已查到的既有主路徑

```text
AlarmScheduler / ACTION_START_ALARM
  → AlarmService.onStartCommand
       ├─ 如果不同鬧鐘仍在響 → finalizePreemptedAlarm (非同步)
       ├─ stopAlarmPlayback()
       ├─ currentAlarmId / currentScheduledAt / currentFireId
       ├─ activateAlarmMediaSession()
       ├─ startForegroundWithPlaceholder()
       └─ serviceScope.launch { startAlarm(alarmId) }
            └─ startAudio(alarm)
                 ├─ [ringtonePool 非空] pool.random()，替換 ringtoneUri
                 └─ startAudioInternal
                      ├─ Spotify delegated + watchdog
                      └─ startMedia3AudioInternal
                          ├─ HTTPS radio
                          └─ startMedia3Tone
                              ├─ READY → 回報 MEDIA3_PLAYER_STARTED
                              ├─ ENDED → dismissAtRingtoneEnd ? dismissAlarm : 無動作
                              └─ ERROR → 內建備援聲音
```

代碼定位：
- [AlarmService.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt) `onStartCommand` L274–400；`startAudio` L841–885；`startMedia3Tone` 約 L1048–1130。
- [AlarmPlaybackPlayer.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmPlaybackPlayer.kt)。
- [AlarmEvent.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/entity/AlarmEvent.kt)。

## 2. 原始退出路徑（已讀部分）

```text
ACTION_SNOOZE → snoozeAlarm()
    ├─ cap 檢查
    ├─ stopAlarmPlayback()
    ├─ persist snooze count / reschedule
    └─ stopSelf()

ACTION_DISMISS → dismissAlarm()
    ├─ stopAlarmPlayback()
    ├─ recordEvent(DISMISSED)
    ├─ clear runtime / currentAlarmId=-1 / activeAlarm=null
    └─ stopSelf()

stopAlarmPlayback()
    ├─ cancel volume / watchdog / vibration etc.
    ├─ release media session
    ├─ release player
    └─ restore volume and audio state
```

上圖 `DISMISSED` 是流程語義；實際常數請以 `AlarmEvent.ACTION_DISMISSED` 為準。

## 3. 建議的轉換（PROPOSAL）

```text
觸發鬧鐘
  → 確立 sessionId + sessionGeneration
  → DailyRotationPolicy 產生第一首 (本地歌曲池)
  → 播放器確認 isPlaying = true → PlaybackEvent STARTED
  → 播放自然結束
     ├─ generation 已失效 → 忽略晚到回呼
     ├─ LOOP_ONE → 維持舊循環
     ├─ DISMISS_AT_END → 走原 dismiss
     └─ CONTINUE_POOL → finalize 當前事件 + 選下一首
                               ├─ 能播放 → 開始下一事件
                               └─ 池失效 → 安全 fallback
```

**不可擅自將新的連播模式同時設定 `dismissAtRingtoneEnd=true`。** 須有單一決策點；舊資料未設定新 enum 時用舊布林值推導原行為。

## 4. 競態接縫

| ID | 操作交錯 | 可能故障 | 必須的防護／驗收 |
|---|---|---|---|
| C-01 | 舊曲 ENDED 晚於新場次 START | 舊回呼把新鬧鐘 dismiss 或播下一首 | 每次回呼檢查 `sessionId + generation + playbackRef` |
| C-02 | ENDED 與 ACTION_DISMISS 同時 | 已解除後仍開始下一首 | 統一狀態轉換，dismiss 使 session inactive |
| C-03 | ENDED 與 ACTION_SNOOZE 同時 | 貪睡後再出聲 | snooze 失效 generation；沒有異常音源重啟 |
| C-04 | ERROR 與 startup watchdog 同時 | 重複 fallback、雙播放器 | single-flight fallback + current player guard |
| C-05 | 兩個不同鬧鐘相近觸發 | 前場回呼破壞後場 | preemption 結束前場，拒絕晚到事件 |
| C-06 | READY 尚未真正播放 | 歷史誤報已播放 | `isPlaying`／真實播放證據，區分 PREPARING |
| C-07 | 歌曲池更新或 URI 權限失效 | 無限跳曲或靜音 | 最大嘗試數、預設鈴聲 fallback |
| C-08 | 23:59 → 00:00 | 同場突然更換每日首曲 | 場次建立後凍結該場 playlist |
| C-09 | 同日貪睡後重響 | 隨機重新抽歌 | 選曲策略與 session ID 分離 |
| C-10 | 同一回呼重複派送／程序恢復 | 歷史重複、錯誤覆寫 | 唯一事件 key、冪等寫入／終止 |
| C-11 | 自動關鈴／上限倒數 | 新歌永遠延長鬧鐘 | 限時以場次開始時計算，不因切歌重置 |

## 5. 必須釐清的狀態語義

- `selected`：選到某歌曲，不等於已載入。
- `prepared`：已可播放，不等於用戶真正聽到。
- `playing`：播放器確認正在播放，可建立歌曲 STARTED 事件。
- `completed`：自然播完。
- `interrupted`：使用者解除、貪睡、搶占、時間上限等。
- `failed`：解碼、URI、啟動或播放失敗。
- `unknown`：程序崩潰造成無法判定，不能冒充 completed。

設計審查時應逐一標示「確知」與「推定」，尤其程序意外終止後的結果。
