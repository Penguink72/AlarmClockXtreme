# ACX A1｜排程診斷與實際排程隔離（2026-11-21）

## 根任務
R1：鬧鐘與貪睡依 Android AlarmManager 排程結果進行可靠決策；**診斷事件是觀察證據，不是允許 OS 排程的前置條件**。

## 本輪發現（程式證據）
- `app/src/main/java/com/sysadmindoc/alarmclock/domain/AlarmScheduler.kt` 的 `scheduleAlarmClock()` 會在 OS 呼叫前與成功後透過 `recordScheduleIncident()` 紀錄事件。
- 原 `recordScheduleIncident()` 直接呼叫 `AlarmIncidentRepository.recordAsync()`，若同步拋出例外，可能使 OS 尚未註冊、或 OS 已註冊卻讓使用者拿不到正確回傳。一般 repository 採 SupervisorJob 及非同步寫入，但這條排程路徑未防禦同步的診斷 sink 失敗。
- 既有 `scheduleSnooze()` 和 `scheduleSnoozeAt()` 的非精準權限撤銷分支已使用 `runCatching` 包住日誌；精準排程路徑此前保護不一致。

## 本輪程式切片（T1）
- 在 `recordScheduleIncident()` 內捕獲診斷紀錄同步例外並記入 Android Log，**仍維持原 reasonCode、type、status 與資料流**。
- 精準 OS 排程的成功/失敗與 Room 持久化 ACK 才影響 `scheduleAt()` 的回傳；診斷失效不再中止這個操作。
- 不改 `AlarmManager` 使用方式、`PendingIntent` identity、`AlarmService`、Room v28、Media3、語系、歷史或正式 UI；不嘗試在本切片聲稱已修好原「亮屏貪睡十分鐘再響」的特定 OEM 問題。

## 新增負向測試（T2 待實際 CI）
- `exactSnoozeSurvivesSynchronousIncidentLoggerFailure`：模擬 `recordAsync` 每次拋錯，確認精準 `setAlarmClock` 仍成功註冊、`updateNextTrigger` 正常持久化且回報成功。
- `exactRegistrationFailureRemainsFailureWhenIncidentLoggerAlsoFails`：模擬 OS `setAlarmClock` 失敗加診斷 sink 失敗，確認回報 false、沒有保存假的 nextTrigger。
- 原有 `AlarmSnoozeRegistrationFailureTest` 的非精準回滾、logger 失敗後保持已註冊、雙重 OS 拒絕負例都保留。

## 驗收與後續
- A1 此切片是「診斷例外隔離」而不是 A1 整體封閉。需 GitHub Action 同一 A1 code SHA 的嚴格 APK、全部測試、第二 Runner 簽章三 Job PASS。
- **尚未封閉**：亮屏右滑貪睡 10 分鐘後重新起聲、不同 Android 廠牌背景限制、跨程序重入及音源撤銷備援；T3=UNVERIFIED、P=UNVERIFIED。
- 預備下一片 A1：逐個追蹤 `AlarmService`/Receiver/Repository 的 pending wakeup／實際起聲 ACK，避免重做前述已實作的 G1/G2/G3 工作；只根據測試可重現的剩餘缺口動正式行為。
- 對照 [R0 全產品帳本](ACX-R0-FULL-PRODUCT-STATUS-LEDGER-20261121.md) 和 [A0～D0 路線](ACX-A0-TO-D0-PRODUCT-DELIVERY-ROADMAP-20261121.md)；此分支不合併 main。
