# ACX L12-2｜真實程式碼路徑與翻譯操作後果查核（2026-11-22）

> **資料性質：靜態程式碼證據，不是 UI 執行期測試、獨立翻譯核准或發行憑證。** 此分支從 L12-1 [PR #48](https://github.com/Penguink72/AlarmClockXtreme/pull/48) SHA `58694759ff0607b58beb1889fd8f3059514b275d` 建立，與 L11-7 分支並行；本分支不自動帶入 L11-7 新增的翻譯候選。

## 1. TCL Root／枝

**Root**：L12-1 已為 2,074 筆 L11 草稿完成初步機械驗證，但「字串文義是否符合真實操作」仍沒有足夠證據。這次以**受約束、可重建的 Kotlin 原始碼探針**，把「被哪個函式/資料操作支持」、「只看到單一靜態呼叫點」、「沒有 Android Runtime 證據」分開記錄。不建立新的語意執行框架。

| 枝 | 靜態實際查核點 | 可支持之主張與界線 |
|---|---|---|
| B2-A｜Backup Restore | `SettingsBackupSections.kt`、`BackupManager.kt` | UI 分別傳入 Append／Replace；Replace 透過保留 ID、刪除不在匯入集中的既有鬧鐘來處理；不代表整個還原交易具原子性或所有排程成功 |
| B2-B｜Privacy | `BackupManager.kt`、`EncryptedBackupCodec.kt` | 預設備份整合資料需選擇；Guardian 欄位會被清除；加密 codec 宣告 AES-GCM；不能直接保證外部端點完全安全 |
| B2-C｜Alarm Fire | `AlarmService.kt` | 貪睡先請求排程下一次響鈴，失敗則提前返回；解除會記錄 Dismiss 事件並觸發後續動作；Auto Dismiss、測試鬧鐘和資料缺失為其他分支 |
| B2-D｜Music Source/Playlist | `MusicLibrarySourceStore.kt`、`MusicLibraryViewModel.kt`、`MusicPlaylistsWorkspace.kt` | 來源移除影響已記錄來源／媒體索引／存取授權；播放清單項目移除交給 repository。**不能把從清單或音樂來源移除譯成刪除手機上的音檔** |
| B2-E｜External actions | `WebhookService.kt`、`HueTrustStore.kt` | Webhook wire event ID 固定、網址要求 HTTPS；Hue 首次使用憑證指紋存檔並比較後續變化；沒有保證網路一定送達 |
| B2-F｜Statistics | `StatsViewModel.kt`、`AlarmEventRepository.kt`、`AlarmEventDao.kt` | 清除統計歷史透過 AlarmEventRepository 及 DAO 的 `DELETE FROM alarm_events`；**不能宣稱清除整個 App 的所有歷史** |
| B2-Q｜驗證／帳本 | Source sha256、精確檔案與行號、規格片段順序、Key/sourceHash/translationHash/reviewRevision | 原始碼變更或缺少錨點，CI fail-closed；獨立語義批准仍為 0 |

## 2. 實作及資料欄位

- `scripts/localization/l12-b2-code-behavior-probes.mjs`：14 個具體程式碼探針，逐一確認所列 Kotlin／DAO 片段出現在當期檔案，且片段順序符合實作。若錨點消失或調換，拒絕假裝已驗證。
- `scripts/localization/l12-b2-code-behavior-probes.test.mjs`：五項實際專案測試與負向測試。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：執行 Node 驗證並上傳獨立 Artifact `acx-l12-b2-code-behavior-evidence-not-approved`。
- 完整產物：`l12-b2-operation-evidence.json`、`l12-b2-operation-evidence.tsv`、`l12-b2-evidence-summary.md`。
- 每一筆可用候選均攜帶 `sourceHash`、`translationHash`、`reviewRevision`、程式檔案 `fileRevision`、錨點行號與明示 `limitation`；沒有「AI 判斷合理就標成 APPROVED」路徑。

## 3. 對 L11 翻譯的直接結論

1. `settings_append_alarms` 與 `settings_replace_alarms` **不能當作相同功能**；尤其 Replace 有實際替換/刪除舊紀錄之風險。
2. `stats_action_snoozed` 與 `stats_action_dismissed` 不應互譯；前者存在重排響鈴失敗的返回路徑，後者會觸發解除動作。
3. `music_library_remove_source` 與播放清單移除操作要明確保留「來源／項目」目標；沒有來源程式證據支持「刪除手機檔案」。
4. `settings_enable_webhook_description` 的 `alarm_fired` 等五個事件 ID 是協定欄位，不應翻譯。
5. `settings_hue_forget_message` 目前只找到首次固定及指紋變化路徑，完整「忘記憑證」使用者操作仍需獨立檢查。
6. `stats_removes_recorded_alarm_outcomes_resets` 的目前實作查到的 DAO 只刪 `alarm_events`，不是鬧鐘定義本身；但不代表其他追蹤表一定同步清理。

## 4. 殘留與結案界線

本次只對 14 個探針合約建立**可重複的來源證據**，不是對 L12-1 全部 826 筆高風險 locale 候選完成逐一語義核准。尚未核對的 caller、資料庫交易細節、通知/Service 執行時序、網路回覆及多裝置 Android 行為均維持 UNKNOWN。

最後必須由與產生草稿不同的合適審核者，針對精確來源/翻譯/呼叫點版本記下內容結論；通過 Android resource 編譯／必要實機測試，才可由 L13 接線並開始正式 XML／V1 語言包與 APK 驗收。本分支**不合併到 main、不寫正式 XML、不宣稱任何模型語義批准**。
