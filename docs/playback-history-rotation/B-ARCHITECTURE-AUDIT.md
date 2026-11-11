# B0｜固定版本原始碼盤點

> **歷史文件／舊設計已延後（2026-11-11）**：本頁的舊提案、實作階段、測試矩陣或設計假設**不再授權直接實作**。請改用 [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) 與 [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)；舊方案參見 [DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)。原始碼現況與流程盤點仍可作為歷史事實參考。


- Repo：`Penguink72/AlarmClockXtreme`
- Commit：`a0cc501bcc3514ffd36483ca0b6e266aa234aaff`
- 審查方法：GitHub 遠端唯讀檔案／搜尋，**未 clone／未編譯／未跑測試／未用手機操作**。
- 原始碼相對路徑：`app/src/main/java/com/sysadmindoc/alarmclock/`
- 引用標籤：SRC＝讀到原始碼、DOC＝README、TODO＝仍需追查、PROPOSAL＝設計。

## 1. 可直接沿用的能力

| 面向 | 證據 | 結論 |
|---|---|---|
| 鬧鐘持久設定 | [data/model/Alarm.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/model/Alarm.kt)，約 L101–109；L269–277 | SRC：已有 `dismissAtRingtoneEnd` 與 `ringtonePool`；後者採逗號分隔 URI，`sanitized()` 正規化，最多 20 首。 |
| 隨機選曲 | [service/AlarmService.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt)，約 L841–885 | SRC：`startAudio()` 每次呼叫 `pool.random()`，因此目前不是跨日固定輪換。 |
| Media3 播放 | [service/AlarmService.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt)，約 L1048–1130、L1250–1310 | SRC：可收到 READY、ENDED、錯誤；已有 watchdog，且單曲依 `dismissAtRingtoneEnd` 選擇重複或結束。 |
| 舊播放器 fallback | 同上，`startMedia3DefaultFallback()`、`startMediaPlayerAudioInternal()`，約 L1220–1510 | SRC：Media3 失敗後可回退至 MediaPlayer；修改不能只顧 Media3 成功路徑。 |
| 音量／停止 | 同上，`stopAlarmPlayback()` 約 L2260 後；[service/AlarmPlaybackPlayer.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmPlaybackPlayer.kt) | SRC：播放器抽象包含 volume/duration/seek/release；沒有下一首／播放清單抽象。 |
| 音源設定 UI | [ui/alarmedit/AlarmEditAdvancedSection.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt) 約 L348–455；[AlarmEditViewModel.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditViewModel.kt) 約 L519–532 | SRC：歌曲池以 chip editor 編輯，保留原儲存格式。 |
| 鬧鐘事件 | [data/local/entity/AlarmEvent.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/entity/AlarmEvent.kt) | SRC：保存 dismissed/snoozed/skipped/missed 等鬧鐘結果，非曲目級 history。 |
| 執行診斷 | [data/local/entity/AlarmIncidentEvent.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/entity/AlarmIncidentEvent.kt)；[AlarmIncidentEventDao.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/AlarmIncidentEventDao.kt) | SRC：有 `fireId`、`alarmId`、`reasonCode`、時間、近期查詢及刪舊事件；可借用診斷思想，但不能直接混充播放事件。 |
| Room | [data/local/AlarmDatabase.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/local/AlarmDatabase.kt) 約 L15–25、L347–393 | SRC：目前 DB v24，有完整 migrations 清單；新增表需規劃 v25 及 schema 匯出。 |
| 備份 | [data/backup/BackupManager.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/data/backup/BackupManager.kt) | SRC：既有備份格式版本 19，包含歌曲池與播完解除；新功能須定義是否一併備份。 |
| 測試入口 | [AlarmServiceMedia3SmokeTest.kt](../../app/src/androidTest/java/com/sysadmindoc/alarmclock/service/AlarmServiceMedia3SmokeTest.kt)、[AlarmDatabaseMigrationTest.kt](../../app/src/androidTest/java/com/sysadmindoc/alarmclock/data/local/AlarmDatabaseMigrationTest.kt)、[RandomRingtoneStartOffsetTest.kt](../../app/src/test/java/com/sysadmindoc/alarmclock/service/RandomRingtoneStartOffsetTest.kt) | SRC：現有 Media3 冒煙、Room migration、隨機起播位置測試；**存在不等於已執行通過**。 |
| 編譯條件 | [README.md](../../README.md) 約 L98–130 | DOC：JDK 17/21、SDK 36；`./gradlew :app:assemblePlayDebug :app:assembleFdroidDebug :wear:assembleDebug`。 |
| 授權 | [LICENSE](../../LICENSE) | SRC：Apache License 2.0。 |

## 2. 本次新增需求的差距

### D — 每日輪換
- **SRC**：目前只看到 `pool.random()`；沒有證據表明現有程式已保存「某鬧鐘某日選曲」。
- **PROPOSAL**：新增純函式的選曲策略；由持久化狀態或可重現種子維持同日選曲，跨日遵循 shuffle bag／近期避免重複。
- **TODO**：決定以使用者本地曆日、固定時區，或原 Alarm 的 timezonePolicy 決定「日」；確認同一鬧鐘的貪睡 fireId 策略。

### S — 同場連播
- **SRC**：`startMedia3Tone()` 現有 onEnded 在 `dismissAtRingtoneEnd=true` 時直接 dismiss；`repeatMode` 隨布林而不同。
- **PROPOSAL**：定義互斥的 end-of-track policy，避免「播完解除」與「播放下一首」兩條路同時執行。預設保留舊行為。
- **TODO**：查清 fallback MediaPlayer 的 onCompletion、Spotify／radio 的差異，明確限定第一版的適用來源。

### H — 曲目級歷史
- **SRC**：`AlarmEvent` 不含 track URI、曲目序號或 started/ended，`AlarmIncidentEvent` 主要是 reasonCode 診斷。
- **PROPOSAL**：另外建立 `PlaybackEvent` + DAO；要求唯一事件身分、單場順序、完成／中斷狀態、保留上限。
- **TODO**：`fireId` 是否在 snooze re-fire 重用；不預設其唯一代表每場實際響鈴。建議單獨 `sessionId`。

## 3. 需保持的既有保護

1. 前景服務快速升級／placeholder notification，不能把耗時選曲、DB 讀寫放到此處阻塞。
2. 8 秒 Media3 startup watchdog 及失敗 fallback 不應被連播新路徑破壞。
3. auto-silence、貪睡、解除、另一個鬧鐘搶占都必須令原場次失效。
4. `seekToRandomPoolOffset()` 在 `dismissAtRingtoneEnd=true` 時已避免跳至歌曲尾段；連播模式也應從頭播放，是否保留舊模式隨機偏移須作者決定。
5. `STATE_READY` 不是充分的「確實正在播放」證據；新增 history 需觀測 `isPlaying` 或等效可靠回呼，記錄不確定狀態。

## 4. 本輪沒有完成的事

- 沒有編譯 Android 或在模擬器／實機響鈴。
- 沒有完全枚舉全部 Activity、排程器、備份匯入、所有測試。
- 沒有修改資料庫、生成 schema、安裝 APK。
- 沒有對新功能聲稱任何 PASS。

## 5. 下一輪盤點清單

- 檢查 `AlarmScheduler`、`AlarmRuntimeState`、`AlarmFireDismissContract` 的 `fireId` 在 snooze／reboot 的生命週期。
- 檢查 `AlarmService` 的 `onDestroy()`、`finalizePreemptedAlarm()`、`autoSilenceJob` 對播放事件關閉的時機。
- 檢查 `BackupManager` 匯入、DataStore、分享／序列化，確保新欄位有預設與相容處理。
- 檢查 schema v24 與 `AlarmDatabaseMigrationTest` 的升級保護，規劃 `24→25`。
- 檢查是否已有通知或媒體 metadata 的設置；不可把 `MediaSession` 已存在推論為曲目 metadata 已對外公開。
