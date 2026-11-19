# LP-G4｜既有高優先譯文第二批初審（未核准）

LP0 高優先字串 52；建議修訂 6；缺中日譯文 8；待核對語義／情境 9。

**這是模型輔助初篩，不等於 52 條完整情境驗收。原有 155/200 繁中及日文資源全部保持不變。**

| Key | 語言 | 既有內容 | 建議譯文 | 程式理由 |
|---|---|---|---|---|
| `music_library_remove_source` | ja | ソースを削除 | 音楽ソースの登録を解除 | viewModel.removeSource 移除索引來源與關聯曲目索引，不是刪除手機媒體檔 |
| `music_playlist_remove` | ja | 削除 | プレイリストから外す | 實際 onRemove 呼叫 repository.removeItem；是從播放清單移除，不是刪除原始檔 |
| `settings_return_to_app_after_dismiss_description` | zh-TW | 確認鬧鐘停止後返回鬧鐘 App；關閉則返回原本的手機畫面。鎖定畫面仍保持保護。 | 確認鬧鐘已停止後顯示「鬧鐘」畫面；關閉此選項則返回先前畫面，並維持鎖定畫面保護。 | 原文是 Alarms screen 和 previous screen，不是籠統 App 與手機畫面 |
| `firing_snooze_schedule_failed` | zh-TW | 貪睡排程失敗，鬧鐘應仍在響鈴。請重新貪睡或直接解除。 | 貪睡排程失敗，鬧鐘仍在響鈴。請重試貪睡或直接解除鬧鐘。 | 英文肯定鬧鐘仍在響鈴，原繁中『應』降低故障訊息確定性 |
| `b2_test_snooze` | ja | スヌーズテスト：1 分後に再通知 | スヌーズテスト：1分後に再び鳴ります | 英文 ring again = 再次響鈴，既有日文『再通知』把鬧鐘鈴聲事件誤作通知 |
| `alarm_edit_schedule_failed_new` | ja | Android が通知予定を受け付けなかったため、新しいアラームは作成されませんでした。権限を確認して再試行してください。 | Android がアラームのスケジュールを受け付けなかったため、新しいアラームは作成されませんでした。アラームの権限を確認して再試行してください。 | 原文 Alarm schedule；原日文『通知予定』不是鬧鐘排程 |

## 仍待調查
- `firing_status_swipe_or_snooze`：左右滑動的字面譯文吻合，但尚未執行設備上的安全行為複核
- `firing_swipe_protected`：Protected 可能指保護操作而非單純禁用，應與長按解除條件核對
- `firing_swipe_right_snooze`：稍後提醒／貪睡可能屬允許的說明文字變體，不能全域替換
- `firing_location_snooze_hint`：涉及已保存地點才能解除的實際位置條件，需另做行為查核
- `alarm_music_settings_remove_track`：G2 無直接靜態呼叫點；日文『曲を削除』可能被誤解成刪檔，暫不可定案
- `alarm_edit_delivery_test_limitation`：測試用 default-tone 音源是否就是系統預設仍須明確核對
- `b2_test_scheduled`：Real music test 可能與正式生產響鈴用語混淆，需看具體測試契約
- `alarm_edit_challenge_default_description`：缺乏中日譯文，不能作已審核判斷
- `settings_cover_to_snooze_description`：涉及接近感測器約 1.5 秒的實際行為，翻譯不可遺漏

## 判斷界線

所有建議只進候選審核報告，沒有修改現有 `app/src/main/res`、原 LP0 packs、G4 人工核准 ledger 或 G5 正式發布標記。
資料完整性可用 Node.js 工具 `scripts/localization/g4-legacy-priority-audit.mjs` 與 `.test.mjs` 驗證。
