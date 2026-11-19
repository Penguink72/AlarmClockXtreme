# LP-G 內容審核｜第一批（模型協助核對，未人工核准）

本文件依 PR #17 的 LP0/G1/G3 資料及 `app/src/main` 真實 Kotlin 呼叫位置核對。僅對 18 個術語概念、21 組新增中日草稿進行內容審核；原有各 155 條譯文**不宣稱已審核完成**。

**授權邊界**：使用者同意繼續內容審核，不等於逐條核准翻譯。G1 / G4 真正的正式核准依然為 0，正式語言包依然不能發行。

## 第一批關鍵結論

- `stats_snoozed` 在 `StatsScreen.kt` 與 `stats.snoozeRate%` 配對，應將「已貪睡／スヌーズ済み」修成「貪睡率／スヌーズ率」。
- `settings_health_no_session` 是 `formatSleepMinutes(null)`，不宜譯成「沒有工作階段」，改成睡眠情境。
- `stats_recent_history` 在 `StatsScreen.kt` 列舉鬧鐘結果，不是音樂歷史。
- `settings_readiness_exact_alarms` 由 `canScheduleExactAlarms` 失敗路徑加入缺失摘要，應表達精確鬧鐘的允許／權限。
- 鈴聲池的同一功能在三個新增草稿中混用「候選池」，改為辨識度較高的「鈴聲池」。日文候選不再將 App 鬧鐘音統稱來電音「着信音」。

## 21 個缺譯 Key 的逐條審核

| Key | 英文來源 | 繁中候選（本批版） | 日文候選（本批版） | 處置 | 程式證據 |
|---|---|---|---|---|---|
| `alarm_edit_feature_wifi` | Wi-Fi rule | Wi-Fi 規則 | Wi-Fi ルール | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditSupport.kt:285` |
| `alarm_edit_default_ringtone_placeholder` | Leave blank to use default ringtone | 留空則使用預設鈴聲 | 空欄の場合は既定のアラーム音を使用 | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditIntegrationSections.kt:93` |
| `alarm_edit_emergency_phone` | Emergency phone number | 緊急聯絡電話號碼 | 緊急連絡先の電話番号 | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditIntegrationSections.kt:280` |
| `alarm_edit_shift_pattern` | Shift pattern | 輪班模式 | シフトパターン | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:169` |
| `alarm_edit_add_ringtone` | Add ringtone | 新增鬧鐘鈴聲 | アラーム音を追加 | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:404` |
| `alarm_edit_add_ringtone_accessibility` | Add ringtone to pool | 將鈴聲加入鈴聲池 | アラーム音を再生候補に追加 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:406` |
| `alarm_edit_add_ringtone_title` | Add ringtone to pool | 將鈴聲加入鈴聲池 | アラーム音を再生候補に追加 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:422` |
| `alarm_edit_ringtone_duplicate` | That ringtone is already in the pool. | 這個鈴聲已在鈴聲池中。 | このアラーム音は既に再生候補にあります。 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:442` |
| `settings_flip_snooze` | Flip phone to snooze | 翻轉手機以貪睡 | 端末を裏返してスヌーズ | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:497` |
| `settings_show_today_tab` | Show Today tab | 顯示「今日」分頁 | 「今日」タブを表示 | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:786` |
| `settings_license` | License | 授權條款 | ライセンス | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:966` |
| `settings_hue_api_placeholder` | Press the Hue bridge button first | 請先按下 Hue 橋接器按鈕 | 先に Hue ブリッジのボタンを押してください | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsIntegrationSections.kt:432` |
| `settings_health_no_session` | No session | 尚無睡眠紀錄 | 睡眠セッションがありません | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsIntegrationSections.kt:684` |
| `settings_connection_sunrise_skipped` | Sunrise simulation skipped | 已略過日出模擬 | 日の出シミュレーションをスキップしました | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsIntegrationSections.kt:772` |
| `settings_accent_violet` | Violet | 紫羅蘭色 | バイオレット | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsPersonalizationSection.kt:467` |
| `settings_readiness_exact_alarms` | exact alarms | 精確鬧鐘權限 | 正確なアラームの許可 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:1622` |
| `stats_snoozed` | Snoozed | 貪睡率 | スヌーズ率 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:217` |
| `stats_recent_history` | Recent history | 最近鬧鐘紀錄 | 最近のアラーム履歴 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:390` |
| `settings_backup_support_bundle_failed` | Couldn’t create support bundle. | 無法建立支援用診斷資料包。 | サポート用診断データを作成できませんでした。 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/BackupStatusCopy.kt:66` |
| `readiness_ready` | Ready to use. | 已準備就緒。 | 準備ができています。 | 修訂候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/ChallengeReadiness.kt:186` |
| `readiness_needs_photo` | Capture a reference photo before saving. | 儲存前請拍攝參考照片。 | 保存前に参照写真を撮影してください。 | 保留候選 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/ChallengeReadiness.kt:133` |

## 術語與 G1 啟發式誤判

18 個詞彙概念均已有模型對照意見，並未核准。尤其以下連結應從實際概念匹配中排除，避免模型依字根強迫套用術語。

| 誤連結概念 | Key | 原因 |
|---|---|---|
| `music.repeat` | `alarm_edit_repeat_weekends` | 週末重複指鬧鐘重複日，非播放音訊的 Repeat |
| `music.repeat` | `alarm_edit_section_anti_snooze_description` | repeated delay 指一再延後起床，非重播音樂 |
| `ui.action.save` | `firing_left_saved_place` | saved place 為已存地點的形容詞，不是儲存按鈕 |
| `ui.action.save` | `firing_location_snooze_hint` | saved place 描述既有儲存位置，非進行儲存 |
| `ui.action.save` | `firing_challenge_photo_support` | saved reference 為已存參考照，非儲存動作 |
| `ui.action.delete` | `alarm_edit_delete_digit` | 刪除輸入字元，不是刪除持久資料 |
| `music.action.stop` | `alarm_edit_challenge_default_description` | 文字說的是鬧鐘結束響鈴，不是媒體重播停止 |
| `music.action.stop` | `settings_return_to_app_after_dismiss_description` | 文字說的是解除鬧鐘後停止，非播放清單操作 |
| `music.track` | `music_library_video_audio_track_detected` | 音訊軌指影音容器 track，不一定是歌曲曲目 |

## 品質與發布狀態

- 18 個概念完成本批**模型輔助內容核對**，只有 1 個概念的日文候選標籤已修訂；沒有概念獲正式核准。
- 21 個 Key × 2 語言 = 42 條模型譯文都已檢視，9 個 Key 的草稿候選文字修訂；不代表 42 條已核准。
- G2 的參照只證明 Key 在程式碼中被呼叫。這些審核意見使用相鄰程式碼推測功能，尚未經手機畫面與完整行為證明。
- 未查完的原有 155 條譯文及高風險畫面持續保持未核准狀態。
- CI 需重新輸出 G3/G4/G5，確認本次修訂的候選仍能通過 Node.js、LP1 資料格式與正式發布阻斷檢查。
