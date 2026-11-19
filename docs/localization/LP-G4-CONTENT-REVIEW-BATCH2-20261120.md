# LP-G4｜第二批 24 個缺譯 Key 中日草稿對照（未核准）

每條 Key 含來源、來源 hash、繁中及日文候選、Android 靜態參照行號及語義核對說明。**模型內容核對不表示獨立人員已核准**。

| Key | 英文 | zh-TW 候選 | ja 候選 | Android 原始碼位置 |
|---|---|---|---|---|
| `alarm_edit_challenge_voice_description` | Say a wake-up phrase with Android speech recognition, with typed fallback. | 使用 Android 語音辨識說出喚醒短句，也可改用文字輸入。 | Android の音声認識で起床用フレーズを話します。代わりに文字入力もできます。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditSupport.kt:839` |
| `alarm_edit_challenge_default_description` | Dismissal requires this challenge before the alarm can stop. | 解除鬧鐘並停止響鈴前，必須先完成此挑戰。 | このチャレンジを完了するまで、アラームを解除して鳴動を止めることはできません。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditSupport.kt:862` |
| `alarm_edit_holidays_hint` | Requires holiday auto-skip and country code configured in Settings → Integrations. | 需先在「設定」→「整合」中啟用假日自動略過，並設定國家代碼。 | 「設定」→「連携」で祝日の自動スキップを有効にし、国コードを設定する必要があります。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditScheduleSections.kt:167` |
| `alarm_edit_ringtone_uri_hint` | Paste a link to a sound file on this device. One entry from the pool plays each time this alarm fires. | 貼上此裝置中音效檔案的連結。每次鬧鐘響起時，會從鈴聲池選取一項播放。 | この端末内の音声ファイルへのリンクを貼り付けます。アラームが鳴るたびに、アラーム音の候補から1件を再生します。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditAdvancedSection.kt:426` |
| `settings_default_snooze_description` | Used whenever a new alarm doesn’t specify its own snooze length. | 新增鬧鐘未指定自己的貪睡時間時，會使用此預設值。 | 新しいアラームでスヌーズ時間を個別に指定しない場合、この設定を使います。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:506` |
| `settings_post_dismiss_summary_description` | After the final dismiss step, show cached weather and the next local calendar event. No network request is made. | 完成最後一步解除鬧鐘後，顯示已快取的天氣和下一筆本機行事曆事件；不會發出網路請求。 | 最後のアラーム解除操作の後、キャッシュ済みの天気と次の端末内カレンダーの予定を表示します。ネットワーク通信は行いません。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:599` |
| `settings_commute_history_description` | Stores only hashed route keys and up to eight recent durations per route on this device; locations are excluded from support bundles. | 此裝置只儲存路線鍵的雜湊值，以及每條路線最近最多 8 筆行程時間；支援用診斷資料包不包含位置資訊。 | この端末に保存するのは経路キーのハッシュ値と、経路ごとに直近最大8件の所要時間だけです。サポート用データには位置情報を含めません。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsScreen.kt:726` |
| `settings_on_call_mode_description` | Use this with a rotating shift pattern when an on-call alarm must break total-silence Do Not Disturb. | 值班鬧鐘需要在「勿擾」完全靜音時仍能響鈴，請搭配輪班模式使用。 | オンコール用アラームを「おやすみモード」の完全消音中にも鳴らす必要がある場合、交代勤務のシフトパターンと併用してください。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsReadinessSections.kt:479` |
| `settings_webhook_description` | Connect alarm events to automations, home setups, or debugging endpoints. | 將鬧鐘事件連接到自動化流程、智慧家庭系統或偵錯端點。 | アラームイベントを自動化処理、スマートホームの環境、またはデバッグ用エンドポイントに連携します。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsIntegrationSections.kt:174` |
| `settings_recent_deliveries` | Recent deliveries | 最近的 Webhook 傳送紀錄 | 最近のWebhook配信履歴 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsIntegrationSections.kt:294` |
| `settings_cover_to_snooze_description` | Hold a hand over the proximity sensor for ~1.5 s during an alarm to snooze. | 鬧鐘響鈴時，用手覆蓋接近感測器約 1.5 秒即可啟動貪睡。 | アラーム鳴動中に近接センサーの上へ約1.5秒間手をかざすと、スヌーズします。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsPersonalizationSection.kt:220` |
| `settings_long_press_threshold_description` | Choose how long the firing-screen dismiss button must be held. 0.5–5 seconds. | 設定響鈴畫面上「解除」按鈕須長按多久，可選 0.5～5 秒。 | アラーム鳴動画面の「停止」ボタンを押し続ける時間を0.5～5秒から選びます。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsPersonalizationSection.kt:288` |
| `settings_backup_locked_description` | Backup buttons stay locked until the result appears. | 備份按鈕會維持鎖定，直到顯示操作結果。 | 処理結果が表示されるまで、バックアップボタンは操作できません。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsBackupSections.kt:536` |
| `settings_encrypted_backup_private` | Encrypted backup includes private values | 加密備份包含私人設定值 | 暗号化バックアップには非公開の設定値が含まれます | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsBackupSections.kt:914` |
| `settings_fossify_import_failed_no_alarms` | Couldn\’t import Fossify alarms. The file has no valid alarms. | 無法匯入 Fossify 鬧鐘：檔案中沒有有效的鬧鐘資料。 | Fossify のアラームをインポートできませんでした。ファイルに有効なアラームがありません。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/SettingsViewModel.kt:757` |
| `alarm_edit_challenge_ref_generic` | challenge details | 挑戰詳細資訊 | チャレンジの詳細 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmedit/AlarmEditViewModel.kt:851` |
| `stats_alarm_history_optional_health_connect` | Alarm history and optional Health Connect sleep sessions will build this view locally. | 鬧鐘歷史紀錄與選用的 Health Connect 睡眠紀錄，會在本機構成此檢視畫面。 | アラーム履歴と、必要に応じて利用する Health Connect の睡眠セッションを端末内で組み合わせて、この画面を表示します。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:900` |
| `onboarding_see_weather_calendar_next_alarm` | See weather, calendar, and your next alarm in one place so the morning feels calmer from the first glance. | 在同一處查看天氣、行事曆與下一個鬧鐘，讓早晨從第一眼開始更從容。 | 天気、カレンダー、次のアラームを1か所で確認でき、朝の始まりを穏やかにします。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/onboarding/OnboardingScreen.kt:125` |
| `permissions_context_permissions` | Context permissions | 情境相關權限 | 状況に応じた権限 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/permissions/PermissionHelper.kt:142` |
| `bedtime_update_health_connect_before_recent_sleep` | Update Health Connect before recent sleep sessions can appear here. | 請先更新 Health Connect，才能在此顯示最近的睡眠紀錄。 | 最近の睡眠セッションをここに表示するには、Health Connect を更新してください。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/bedtime/BedtimeSleepTrackingSections.kt:310` |
| `stats_track_consistency_snooze_behavior_and_which` | Track consistency, snooze behavior, and which mornings are easiest to handle. | 追蹤起床規律、貪睡行為，以及哪些早晨最容易順利起床。 | 起床の安定性やスヌーズの傾向、無理なく起きられた朝を確認します。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:129` |
| `stats_recent_events` | %1$s recent events | 最近 %1$s 筆事件 | 最近のイベント %1$s 件 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:166` |
| `stats_recent_health_connect_sessions_stay_local` | Recent Health Connect sessions stay local and are shown beside alarm history. | 最近的 Health Connect 睡眠紀錄保留在本機，並與鬧鐘歷史並列顯示。 | 直近の Health Connect 睡眠セッションは端末内に保存され、アラーム履歴と並べて表示されます。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:767` |
| `stats_compares_recent_sleep_duration_with_dismiss` | Compares recent sleep duration with dismiss speed, snoozes, and challenge retries on this device. | 在本機比較最近的睡眠時長、解除鬧鐘的速度、貪睡情況及挑戰重試次數。 | この端末上で、最近の睡眠時間とアラーム解除までの時間、スヌーズの傾向、チャレンジの再試行回数を比較します。 | `app/src/main/java/com/sysadmindoc/alarmclock/ui/stats/StatsScreen.kt:1522` |

## 已特別核對的錯誤陷阱

- Recent deliveries 是 webhookDeliveryLog，不能譯成物流配送。
- Health Connect Update 對應 PROVIDER_UPDATE_REQUIRED，不能混淆成同步睡眠紀錄。
- Track consistency 的 Track 是動詞，不是音樂曲目。
- 路線歷史保留雜湊鍵、每條路線最多八筆資料及診斷包不得含位置的原意。
- 接近感測器貪睡約 1.5 秒，解除長按可選 0.5～5 秒，均保留。
- 最近事件的格式參數 `%1$s` 完整保留。

本批 48 條、前批 42 條都沒有 AI 自行批准資格；既有 155 條兩語資源未被覆寫。
