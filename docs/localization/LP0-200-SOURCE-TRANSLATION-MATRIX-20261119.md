# LP0｜200 字串來源、繁中與日文逐條對照（未審核）

> 此檔為從 PR #14 原始資料擷取的**對照工作底稿**，不代表任何翻譯已獲核准。
> `sourceHash`、Key、原文、現有譯文以 `localization/governance/lp0/source-matrix.json` 為準；此檔僅供人工逐條閱讀。

來源：`app/src/main/res/values/strings.xml`，source commit `c4dd6353f3ae5f95c9a2667599f294920eaa9619`。字串數 200，繁中既有 155、日文既有 155，語義審核 0。

| 序 | 分類 | Key | 英文來源 | 繁體中文（現有） | 日本語（現有） | 情境推測 |
|---:|---|---|---|---|---|---|
| 1 | settings | `nav_stopwatch` | Stopwatch | 碼錶 | ストップウォッチ | navigation |
| 2 | snooze | `snooze_duration` | Snooze duration | 貪睡間隔 | スヌーズ間隔 | unclassified-screen |
| 3 | settings | `settings_title` | Settings | 設定 | 設定 | settings-screen |
| 4 | snooze | `notif_snooze_countdown_title` | Snoozed alarm | 已稍後提醒的鬧鐘 | スヌーズ中のアラーム | notification |
| 5 | snooze | `notif_snooze_countdown_subtext` | Snooze countdown | 稍後提醒倒數 | スヌーズの残り時間 | notification |
| 6 | alarm | `firing_controls_title` | Alarm controls | 鬧鐘控制 | アラーム操作 | alarm-firing-screen |
| 7 | snooze | `firing_snooze_hint` | Long-press Snooze for exact minutes, or choose a clock time. Your saved default stays unchanged. | 長按「貪睡」可指定分鐘數，或選擇貪睡到某個時間；已儲存的預設值不會改變。 | 「スヌーズ」を長押しすると分数や時刻を指定できます。保存済みの既定値は変更されません。 | alarm-firing-screen |
| 8 | snooze | `firing_exact_snooze_hint` | Applies once to this alarm. | 只對本次鬧鐘生效。 | 今回のアラームにのみ適用されます。 | alarm-firing-screen |
| 9 | snooze | `firing_flip_to_snooze` | Flip to snooze | 翻轉手機以稍後提醒 | 端末を裏返してスヌーズ | alarm-firing-screen |
| 10 | snooze | `firing_status_swipe_or_snooze` | Wake-up steps are complete. Swipe left to dismiss, or right to snooze. | 已完成喚醒任務。向左滑動解除，或向右滑動貪睡。 | 起床タスクが完了しました。左にスワイプで停止、右にスワイプでスヌーズします。 | alarm-firing-screen |
| 11 | alarm | `firing_swipe_protected` | Swipe-left is protected. Use Hold Dismiss. | 已保護向左滑動解除，請長按「解除」。 | 左スワイプによる停止は無効です。「停止」を長押ししてください。 | alarm-firing-screen |
| 12 | snooze | `firing_swipe_right_snooze` | Swipe right to snooze | 向右滑動以稍後提醒 | 右にスワイプしてスヌーズ | alarm-firing-screen |
| 13 | alarm | `firing_left_saved_place` | Left saved place | 已離開儲存地點 | 保存場所から離れました | alarm-firing-screen |
| 14 | snooze | `firing_location_snooze_hint` | Swipe right to snooze if you need a short reset. Dismiss unlocks after you leave the saved place. | 需要再休息片刻可向右滑動貪睡；離開儲存地點後才能解除。 | 少し休むなら右にスワイプしてスヌーズします。保存場所から離れると停止できます。 | alarm-firing-screen |
| 15 | alarm | `firing_controls_hold_description` | Hold to confirm dismissal. Snooze remains a quick action. | 長按以確認解除。貪睡仍可快速操作。 | 長押しして停止を確定します。スヌーズはすぐに使えます。 | alarm-firing-screen |
| 16 | snooze | `firing_tap_snooze` | Tap to snooze | 點選以貪睡 | タップしてスヌーズ | alarm-firing-screen |
| 17 | snooze | `firing_hide_snooze_choices` | Hide snooze choices | 隱藏貪睡選項 | スヌーズの選択肢を閉じる | alarm-firing-screen |
| 18 | snooze | `firing_choose_snooze_until` | Snooze until a time | 貪睡到指定時間 | 指定時刻までスヌーズ | alarm-firing-screen |
| 19 | snooze | `firing_snooze_until_hint` | The alarm will fire at the next occurrence of this time. | 鬧鐘會在下一次到達此時刻時響鈴。 | 次に指定時刻になったときにアラームが鳴ります。 | alarm-firing-screen |
| 20 | alarm | `firing_challenge_before_dismiss` | Finish the wake-up challenge before dismissing | 解除前請先完成喚醒挑戰 | 停止する前に起床チャレンジを完了してください | alarm-firing-screen |
| 21 | snooze | `firing_snooze_for_minutes` | Snooze for %1$d min | 貪睡 %1$d 分鐘 | %1$d分スヌーズ | alarm-firing-screen |
| 22 | snooze | `firing_increase_snooze` | Increase snooze minutes | 增加貪睡分鐘數 | スヌーズの分数を増やす | alarm-firing-screen |
| 23 | snooze | `firing_snooze_minutes` | Snooze %1$d min | 貪睡 %1$d 分鐘 | %1$d分スヌーズ | alarm-firing-screen |
| 24 | alarm | `firing_challenge_handwriting_title` | Draw the wake-up word | 手寫指定文字 | 指定された語句を手書き | alarm-firing-screen |
| 25 | alarm | `firing_challenge_simon_title` | Play back the pattern | 重現顏色順序 | 色の順番を再現 | alarm-firing-screen |
| 26 | alarm | `firing_challenge_none_support` | No extra challenge is required for this alarm. | 此鬧鐘不需額外解除挑戰。 | このアラームには追加のチャレンジはありません。 | alarm-firing-screen |
| 27 | alarm | `firing_challenge_photo_support` | Take a fresh photo that closely matches the saved reference. | 重新拍照，並盡量符合參考照片。 | 保存した参照写真に近い写真を撮影します。 | alarm-firing-screen |
| 28 | alarm | `firing_challenge_speed_support` | Groggy fingers slow you down, so prove you can type at speed. | 以一定速度正確打字，確認手指已清醒。 | 素早く正確に入力できるか確認します。 | alarm-firing-screen |
| 29 | alarm | `firing_challenge_voice_status` | Say the phrase shown below, or use the typed fallback. | 說出下方短句，或使用文字輸入備援。 | 下のフレーズを話すか、文字入力に切り替えます。 | alarm-firing-screen |
| 30 | alarm | `firing_challenge_sheep_status` | Tap every sheep; avoid the goats. | 點選每隻羊，避開山羊。 | 羊だけをタップし、ヤギを避けます。 | alarm-firing-screen |
| 31 | alarm | `firing_challenge_none_status` | Swipe or tap dismiss when you\’re ready. | 準備好時，滑動或點選即可解除。 | 準備ができたらスワイプまたはタップして停止します。 | alarm-firing-screen |
| 32 | alarm | `alarm_edit_page_integrations` | Integrations & safety | 整合與安全 | 連携と安全 | alarm-edit-screen |
| 33 | alarm | `alarm_edit_smart_alarm` | Smart alarm | 智慧鬧鐘 | スマートアラーム | alarm-edit-screen |
| 34 | snooze | `alarm_edit_anti_snooze` | Anti-snooze | 防止重複貪睡 | スヌーズ防止 | alarm-edit-screen |
| 35 | alarm | `alarm_edit_section_group_description` | Organize alarms by context so related schedules stay easy to manage. | 依情境整理鬧鐘，讓相關排程更容易管理。 | 用途ごとにアラームを整理し、関連する予定を管理しやすくします。 | alarm-edit-screen |
| 36 | alarm | `alarm_edit_section_hue_description` | Coordinate bedside lighting with the alarm for a gentler rise. | 配合床邊燈光，讓起床過程更平緩。 | ベッドサイドの照明と連携し、穏やかに目覚められます。 | alarm-edit-screen |
| 37 | snooze | `alarm_edit_section_anti_snooze_description` | Add guardrails that make repeated delay harder. | 增加限制，避免一再延後起床。 | スヌーズの繰り返しを抑える制限を追加します。 | alarm-edit-screen |
| 38 | alarm | `alarm_edit_background_selected` | Background image selected. | 已選取背景圖片。 | 背景画像を選択しました。 | alarm-edit-screen |
| 39 | diagnostics | `alarm_edit_background_permission_warning` | Background image selected. Re-select it if Android revokes access. | 已選取背景圖片。若 Android 撤銷存取權，請重新選取。 | 背景画像を選択しました。Android がアクセス権を取り消した場合は選び直してください。 | alarm-edit-screen |
| 40 | alarm | `alarm_edit_saving` | Saving alarm… | 正在儲存鬧鐘… | アラームを保存中… | alarm-edit-screen |
| 41 | alarm | `alarm_edit_disabled` | Disabled | 已停用 | 無効 | alarm-edit-screen |
| 42 | alarm | `alarm_edit_minutes_seconds_short` | %1$dm %2$ds | %1$d 分 %2$d 秒 | %1$d 分 %2$d 秒 | alarm-edit-screen |
| 43 | alarm | `alarm_edit_group_custom_name` | Custom group name | 自訂群組名稱 | カスタムグループ名 | alarm-edit-screen |
| 44 | alarm | `alarm_edit_heartbeat` | Heartbeat | 心跳 | 心拍 | alarm-edit-screen |
| 45 | alarm | `alarm_edit_immediately_default` | Immediately (default) | 立即（預設） | すぐに（デフォルト） | alarm-edit-screen |
| 46 | alarm | `alarm_edit_feature_wifi` | Wi-Fi rule | — | — | alarm-edit-screen |
| 47 | alarm | `alarm_edit_repeat_weekends` | Weekends | 週末 | 週末 | alarm-edit-screen |
| 48 | alarm | `alarm_edit_squats_required` | Squats required | 所需深蹲次數 | 必要なスクワット回数 | alarm-edit-screen |
| 49 | alarm | `alarm_edit_capture_photo_hint` | Capture a reference photo from the place or angle the alarm should require. | 請在要作為解鈴條件的地點或角度拍攝參考照片。 | 解除条件にする場所や角度で参照写真を撮影してください。 | alarm-edit-screen |
| 50 | alarm | `alarm_edit_challenge_chain` | Challenge chain | 連續解除挑戰 | 連続解除チャレンジ | alarm-edit-screen |
| 51 | alarm | `alarm_edit_challenge_shake` | Shake phone | 搖晃手機 | 端末を振る | alarm-edit-screen |
| 52 | alarm | `alarm_edit_challenge_pushup` | Push-ups | 伏地挺身 | 腕立て伏せ | alarm-edit-screen |
| 53 | alarm | `alarm_edit_challenge_pvt` | Reaction test | 反應時間測驗 | 反応速度テスト | alarm-edit-screen |
| 54 | alarm | `alarm_edit_challenge_voice_description` | Say a wake-up phrase with Android speech recognition, with typed fallback. | — | — | alarm-edit-screen |
| 55 | alarm | `alarm_edit_challenge_default_description` | Dismissal requires this challenge before the alarm can stop. | — | — | alarm-edit-screen |
| 56 | alarm | `alarm_edit_holidays_hint` | Requires holiday auto-skip and country code configured in Settings → Integrations. | — | — | alarm-edit-screen |
| 57 | music | `alarm_edit_default_ringtone_placeholder` | Leave blank to use default ringtone | — | — | alarm-edit-screen |
| 58 | snooze | `alarm_edit_snooze_limit_unlimited` | Unlimited | 不限次數 | 無制限 | alarm-edit-screen |
| 59 | snooze | `alarm_edit_progressive_snooze` | Progressive snooze (shorter each time) | 逐次縮短貪睡間隔 | スヌーズ間隔を毎回短縮 | alarm-edit-screen |
| 60 | snooze | `alarm_edit_progressive_snooze_hint` | Each snooze shortens by 1 minute, such as 10 → 9 → 8. | 每次貪睡縮短 1 分鐘，例如 10 → 9 → 8 分鐘。 | スヌーズのたびに1分短縮します（例：10 → 9 → 8分）。 | alarm-edit-screen |
| 61 | alarm | `alarm_edit_emergency_phone` | Emergency phone number | — | — | alarm-edit-screen |
| 62 | alarm | `alarm_edit_shift_pattern` | Shift pattern | — | — | alarm-edit-screen |
| 63 | alarm | `alarm_edit_add_ringtone` | Add ringtone | — | — | alarm-edit-screen |
| 64 | music | `alarm_edit_add_ringtone_accessibility` | Add ringtone to pool | — | — | alarm-edit-screen |
| 65 | music | `alarm_edit_add_ringtone_title` | Add ringtone to pool | — | — | alarm-edit-screen |
| 66 | music | `alarm_edit_ringtone_uri_hint` | Paste a link to a sound file on this device. One entry from the pool plays each time this alarm fires. | — | — | alarm-edit-screen |
| 67 | music | `alarm_edit_ringtone_duplicate` | That ringtone is already in the pool. | — | — | alarm-edit-screen |
| 68 | alarm | `alarm_edit_24_hour` | 24-hour time | 24 小時制 | 24時間表示 | alarm-edit-screen |
| 69 | diagnostics | `settings_pane_readiness` | Readiness | 使用準備 | 使用準備 | settings-screen |
| 70 | settings | `settings_pane_defaults_description` | New-alarm behavior, dashboard content, and navigation tabs. | 新鬧鐘行為、首頁內容及導覽分頁。 | 新規アラーム、ホーム画面、ナビゲーションタブの設定。 | settings-screen |
| 71 | settings | `settings_pane_integrations_description` | Webhook, holidays, Hue, Health Connect, and connection status. | Webhook、假日、Hue、Health Connect 與連線狀態。 | Webhook、休日、Hue、Health Connect、接続状態。 | settings-screen |
| 72 | settings | `settings_pane_personalization_description` | Theme, challenge difficulty, privacy, and firing-screen behavior. | 主題、解除難度、隱私及響鈴畫面行為。 | テーマ、解除の難易度、プライバシー、アラーム画面の設定。 | settings-screen |
| 73 | settings | `settings_pane_backup_description` | Encrypted export, restore preview, and conflict-safe import. | 加密匯出、還原預覽及避免衝突的匯入。 | 暗号化エクスポート、復元プレビュー、安全なインポート。 | settings-screen |
| 74 | settings | `settings_pane_utilities_description` | Companion tools, support bundle export, app version, and license. | 附屬工具、支援資料匯出、版本及授權。 | 関連ツール、サポートデータ、アプリのバージョンとライセンス。 | settings-screen |
| 75 | settings | `settings_share_support_bundle` | Share support bundle | 分享支援資料 | サポートデータを共有 | settings-screen |
| 76 | settings | `settings_battery_protected` | Battery protected | 電池設定已保護 | バッテリー設定は保護済み | settings-screen |
| 77 | settings | `settings_battery_setup_needed` | Needs battery setup | 需要設定電池權限 | バッテリー設定が必要 | settings-screen |
| 78 | settings | `settings_choose_group_hint` | Choose a group to keep wide screens focused. | 選擇分類以聚焦顯示內容。 | カテゴリーを選択して表示内容を絞り込みます。 | settings-screen |
| 79 | settings | `settings_setup_needed` | Setup needed | 需要設定 | 設定が必要 | settings-screen |
| 80 | settings | `settings_not_selected` | Not selected | 未選取 | 未選択 | settings-screen |
| 81 | settings | `settings_alarm_defaults_description` | Set the behavior new alarms start with so setup feels faster and more predictable. | 設定新鬧鐘的初始行為，減少重複設定。 | 新しいアラームの初期動作を設定します。 | settings-screen |
| 82 | snooze | `settings_flip_snooze` | Flip phone to snooze | — | — | settings-screen |
| 83 | snooze | `settings_default_snooze_description` | Used whenever a new alarm doesn’t specify its own snooze length. | — | — | settings-screen |
| 84 | settings | `settings_minutes_short` | %1$d min | %1$d 分鐘 | %1$d 分 | settings-screen |
| 85 | settings | `settings_dashboard_description` | Control what appears on the My Day screen so it stays useful without feeling busy. | 控制每日資訊畫面顯示哪些內容。 | 今日の情報画面に表示する項目を選択します。 | settings-screen |
| 86 | settings | `settings_post_dismiss_summary_description` | After the final dismiss step, show cached weather and the next local calendar event. No network request is made. | — | — | settings-screen |
| 87 | history | `settings_commute_history_description` | Stores only hashed route keys and up to eight recent durations per route on this device; locations are excluded from support bundles. | — | — | settings-screen |
| 88 | settings | `settings_show_today_tab` | Show Today tab | — | — | settings-screen |
| 89 | settings | `settings_utilities_description` | Quick access to companion tools that round out the app. | 計時、統計及其他輔助工具。 | タイマー、統計、その他のツール。 | settings-screen |
| 90 | settings | `settings_alarm_statistics` | Alarm statistics | 鬧鐘統計 | アラーム統計 | settings-screen |
| 91 | settings | `settings_stopwatch_description` | Track laps with best and worst splits highlighted. | 開啟碼錶。 | ストップウォッチを開きます。 | settings-screen |
| 92 | settings | `settings_language_description` | Choose the app language. Core alarm, sound, and music screens are available in English, Traditional Chinese, and Japanese; other screens may remain in English. | 選擇 App 語言。鬧鐘、音效與音樂核心畫面支援英文、繁體中文及日文；其他畫面可能仍顯示英文。 | アプリの言語を選択します。アラーム、サウンド、音楽の主要画面は英語、繁体字中国語、日本語に対応しています。他の画面は英語のままの場合があります。 | settings-screen |
| 93 | settings | `settings_language_dialog_title` | Choose app language | 選擇 App 語言 | アプリの言語を選択 | settings-screen |
| 94 | settings | `settings_language_english` | English | 英文 | 英語 | settings-screen |
| 95 | settings | `settings_language_japanese` | Japanese | 日文 | 日本語 | settings-screen |
| 96 | settings | `settings_license` | License | — | — | settings-screen |
| 97 | settings | `settings_ready` | Ready | 就緒 | 準備完了 | settings-screen |
| 98 | settings | `settings_on_call_mode_description` | Use this with a rotating shift pattern when an on-call alarm must break total-silence Do Not Disturb. | — | — | settings-screen |
| 99 | settings | `settings_webhook_description` | Connect alarm events to automations, home setups, or debugging endpoints. | — | — | settings-screen |
| 100 | history | `settings_recent_deliveries` | Recent deliveries | — | — | settings-screen |
| 101 | settings | `settings_hue_api_placeholder` | Press the Hue bridge button first | — | — | settings-screen |
| 102 | settings | `settings_health_no_session` | No session | — | — | settings-screen |
| 103 | settings | `settings_connection_sunrise_skipped` | Sunrise simulation skipped | — | — | settings-screen |
| 104 | snooze | `settings_cover_to_snooze_description` | Hold a hand over the proximity sensor for ~1.5 s during an alarm to snooze. | — | — | settings-screen |
| 105 | settings | `settings_long_press_threshold_description` | Choose how long the firing-screen dismiss button must be held. 0.5–5 seconds. | — | — | settings-screen |
| 106 | settings | `settings_accent_violet` | Violet | — | — | settings-screen |
| 107 | settings | `settings_backup_locked_description` | Backup buttons stay locked until the result appears. | — | — | settings-screen |
| 108 | settings | `settings_close` | Close | 關閉 | 閉じる | settings-screen |
| 109 | settings | `settings_encrypted_backup_private` | Encrypted backup includes private values | — | — | settings-screen |
| 110 | diagnostics | `settings_readiness_exact_alarms` | exact alarms | — | — | settings-screen |
| 111 | alarm | `alarm_edit_delete_digit` | Delete digit | 刪除數字 | 数字を削除 | alarm-edit-screen |
| 112 | settings | `settings_fossify_import_failed_no_alarms` | Couldn\’t import Fossify alarms. The file has no valid alarms. | — | — | settings-screen |
| 113 | alarm | `alarm_edit_challenge_ref_generic` | challenge details | — | — | alarm-edit-screen |
| 114 | alarm | `alarm_list_clear_filters` | Clear filters | 清除篩選條件 | フィルターを解除 | alarm-list-screen |
| 115 | alarm | `alarm_list_delete` | Delete | 刪除 | 削除 | alarm-list-screen |
| 116 | alarm | `alarm_list_pause` | Pause | 暫停 | 一時停止 | alarm-list-screen |
| 117 | snooze | `stats_snoozed` | Snoozed | — | — | statistics-screen |
| 118 | history | `stats_recent_history` | Recent history | — | — | statistics-screen |
| 119 | history | `stats_alarm_history_optional_health_connect` | Alarm history and optional Health Connect sleep sessions will build this view locally. | — | — | statistics-screen |
| 120 | diagnostics | `onboarding_see_weather_calendar_next_alarm` | See weather, calendar, and your next alarm in one place so the morning feels calmer from the first glance. | — | — | unclassified-screen |
| 121 | music | `ringtone_folder` | From folder | 資料夾 | フォルダー | music-screen-or-picker |
| 122 | music | `music_library_title` | Music library and playback history | 音樂庫與播放歷史 | 音楽ライブラリと再生履歴 | music-screen-or-picker |
| 123 | history | `music_library_history_title` | Playback history | 播放歷史 | 再生履歴 | music-screen-or-picker |
| 124 | music | `music_library_catalog_empty` | No indexed tracks yet. Allow audio access or scan an authorized music folder. | 尚無已索引曲目。請允許音訊存取權或掃描已授權的音樂資料夾。 | 索引済みの曲はありません。音声アクセスを許可するか、許可済みフォルダーをスキャンしてください。 | music-screen-or-picker |
| 125 | music | `music_library_tab_videos` | Videos | 影片 | 動画 | music-screen-or-picker |
| 126 | music | `music_library_video_audio_track_detected` | %1$s · audio track found during folder scan; decoder support is not verified · %2$s | %1$s · 資料夾掃描已偵測到音軌；尚未驗證解碼支援 · %2$s | %1$s · フォルダーのスキャンで音声トラックを検出（デコーダー対応は未検証） · %2$s | music-screen-or-picker |
| 127 | music | `music_library_source_scope_all` | All sources | 所有來源 | すべてのソース | music-screen-or-picker |
| 128 | music | `music_library_preview` | Preview track | 試聽曲目 | 曲を試聴 | music-screen-or-picker |
| 129 | music | `alarm_music_settings_mode_shared` | Use the shared music pool | 使用共用音樂池 | 共有音楽プールを使う | unclassified-screen |
| 130 | music | `alarm_music_settings_strategy_order` | Playlist order | 依播放清單順序 | プレイリスト順 | unclassified-screen |
| 131 | music | `alarm_music_settings_artists` | Artists | 歌手 | アーティスト | unclassified-screen |
| 132 | music | `alarm_music_settings_save` | Save | 儲存 | 保存 | unclassified-screen |
| 133 | music | `alarm_music_settings_remove_track` | Remove track | 移除曲目 | 曲を削除 | unclassified-screen |
| 134 | music | `music_library_add_folder` | Add folder | 新增資料夾 | フォルダーを追加 | music-screen-or-picker |
| 135 | music | `music_library_source_origin` | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | music-screen-or-picker |
| 136 | music | `music_library_source_unknown_origin` | Storage provider | 儲存空間提供者 | ストレージプロバイダー | music-screen-or-picker |
| 137 | music | `music_library_remove_source` | Remove source | 移除來源 | ソースを削除 | music-screen-or-picker |
| 138 | music | `music_library_sources_read_failed` | Could not read music sources: %1$s | 無法讀取音樂來源：%1$s | 音楽ソースを読み取れませんでした：%1$s | music-screen-or-picker |
| 139 | history | `music_library_no_history` | No playback history for this date. | 這一天沒有播放紀錄。 | この日の再生履歴はありません。 | music-screen-or-picker |
| 140 | music | `music_library_close` | Close | 關閉 | 閉じる | music-screen-or-picker |
| 141 | music | `music_library_scan_failed` | Music scan failed: %1$s | 音樂掃描失敗：%1$s | 音楽のスキャンに失敗しました：%1$s | music-screen-or-picker |
| 142 | history | `music_library_history_row` | %1$d. %2$s — %3$s | %1$d. %2$s — %3$s | %1$d. %2$s — %3$s | music-screen-or-picker |
| 143 | music | `music_library_pool_all_candidates` | All candidates | 全部候選 | すべての候補 | music-screen-or-picker |
| 144 | music | `music_library_pool_clear_selection` | Clear selection | 清除選取 | 選択を解除 | music-screen-or-picker |
| 145 | music | `music_library_pool_status_included` | Added to music pool | 已加入音樂池 | 音楽プールに追加済み | music-screen-or-picker |
| 146 | music | `music_library_pool_source_label` | Source | 來源 | ソース | music-screen-or-picker |
| 147 | music | `music_library_pool_type_audio` | Audio file | 音訊檔 | 音声ファイル | music-screen-or-picker |
| 148 | music | `ringtone_preview_unavailable` | Preview unavailable | 無法試聽 | 試聴できません | music-screen-or-picker |
| 149 | music | `ringtone_nothing_matches` | Nothing matches “%1$s”. Try a shorter or different term. | 找不到符合「%1$s」的項目。請縮短或更換搜尋詞。 | 「%1$s」に一致する項目はありません。検索語を短くするか変更してください。 | music-screen-or-picker |
| 150 | diagnostics | `permissions_context_permissions` | Context permissions | — | — | unclassified-screen |
| 151 | alarm | `alarmlist_alarm` | %1$s alarm | %1$s 個鬧鐘 | %1$s 件のアラーム | unclassified-screen |
| 152 | history | `bedtime_update_health_connect_before_recent_sleep` | Update Health Connect before recent sleep sessions can appear here. | — | — | unclassified-screen |
| 153 | snooze | `stats_track_consistency_snooze_behavior_and_which` | Track consistency, snooze behavior, and which mornings are easiest to handle. | — | — | statistics-screen |
| 154 | history | `stats_recent_events` | %1$s recent events | — | — | statistics-screen |
| 155 | history | `stats_recent_health_connect_sessions_stay_local` | Recent Health Connect sessions stay local and are shown beside alarm history. | — | — | statistics-screen |
| 156 | settings | `settings_backup_support_bundle_failed` | Couldn’t create support bundle. | — | — | settings-screen |
| 157 | music | `ringtone_folder_item` | %1$s (folder) | %1$s（資料夾） | %1$s（フォルダー） | music-screen-or-picker |
| 158 | history | `stats_compares_recent_sleep_duration_with_dismiss` | Compares recent sleep duration with dismiss speed, snoozes, and challenge retries on this device. | — | — | statistics-screen |
| 159 | alarm | `alarmlist_share_alarm` | Share alarm | 分享鬧鐘 | アラームを共有 | unclassified-screen |
| 160 | diagnostics | `readiness_ready` | Ready to use. | — | — | unclassified-screen |
| 161 | diagnostics | `readiness_needs_photo` | Capture a reference photo before saving. | — | — | unclassified-screen |
| 162 | music | `ringtone_saved_to_alarms` | Saved “%1$s” to your alarms. | 已將「%1$s」儲存至鬧鐘。 | 「%1$s」をアラームに保存しました。 | music-screen-or-picker |
| 163 | alarm | `alarm_list_quick_alarm_for` | Set quick alarm for %1$s | 設定於 %1$s 的快速鬧鐘 | %1$s にクイックアラームを設定 | alarm-list-screen |
| 164 | music | `music_library_category_music` | Music | 音樂 | 音楽 | music-screen-or-picker |
| 165 | music | `music_playlist_integration_pending` | Playlist storage is waiting for the Room v28 integration. Existing Room v27 search and music-pool actions remain available; playlist changes are not saved yet. | 播放清單儲存仍待 Room v28 整合；現有 Room v27 搜尋與音樂池操作仍可使用，目前不會儲存播放清單變更。 | プレイリスト保存は Room v28 の統合待ちです。既存の Room v27 検索と音楽プール操作は引き続き利用できますが、プレイリストの変更はまだ保存されません。 | music-screen-or-picker |
| 166 | music | `music_playlist_add_selected` | Add %1$d selected | 加入已選的 %1$d 項 | 選択した %1$d 件を追加 | music-screen-or-picker |
| 167 | music | `music_playlist_rename` | Rename | 重新命名 | 名前を変更 | music-screen-or-picker |
| 168 | music | `music_playlist_empty` | This playlist is empty. | 此播放清單目前沒有曲目。 | このプレイリストには曲がありません。 | music-screen-or-picker |
| 169 | music | `music_playlist_remove` | Remove | 移除 | 削除 | music-screen-or-picker |
| 170 | music | `music_playlist_unavailable_count` | %1$d tracks could not be added because they are unavailable. | %1$d 首曲目無法存取，因此未能加入。 | %1$d 件は利用できないため追加できませんでした。 | music-screen-or-picker |
| 171 | music | `alarm_music_settings_playlist_smart_all` | All available music | 所有可用音樂 | 利用可能なすべての音楽 | unclassified-screen |
| 172 | music | `music_playlist_selected_items` | %1$d selected items | 已選取 %1$d 個項目 | %1$d 件選択中 | music-screen-or-picker |
| 173 | music | `music_playlist_partial_add` | Added %1$d tracks; %2$d unavailable tracks were skipped. | 已加入 %1$d 首曲目；略過 %2$d 首無法存取的曲目。 | %1$d 件を追加し、利用できない %2$d 件はスキップしました。 | music-screen-or-picker |
| 174 | history | `recent_playback_title` | Recently played | 最近播放 | 最近の再生 | unclassified-screen |
| 175 | history | `recent_playback_empty` | No alarm playback history yet | 尚無音樂鬧鐘播放紀錄 | アラーム再生履歴はありません | unclassified-screen |
| 176 | history | `replay_action_pause` | Pause | 暫停 | 一時停止 | unclassified-screen |
| 177 | history | `replay_action_stop` | Stop | 停止 | 停止 | unclassified-screen |
| 178 | history | `replay_status_playing` | Playing | 播放中 | 再生中 | unclassified-screen |
| 179 | history | `replay_open_in_external` | Open with other app | 以其他 App 開啟 | 他のアプリで開く | unclassified-screen |
| 180 | history | `replay_error_alarm_active` | Alarm is ringing. Replay stopped to prioritize alarm. | 鬧鐘響鈴中，為確保鬧鐘優先已停止回播 | アラーム鳴動中のため、アラームを優先して再生を停止しました | unclassified-screen |
| 181 | history | `replay_error_playback_failed` | Cannot play this audio. File may be missing or permission revoked. | 無法播放此音訊（檔案可能已移除或權限已失效） | この音声を再生できません（ファイルが見つからないか権限が無効です） | unclassified-screen |
| 182 | history | `music_library_history_filter_recent` | Recent | 最近播放 | 最近の再生 | music-screen-or-picker |
| 183 | settings | `settings_return_to_app_after_dismiss_description` | Show the Alarms screen after the alarm is confirmed stopped. When off, return to your previous screen. Keep the lock screen protected. | 確認鬧鐘停止後返回鬧鐘 App；關閉則返回原本的手機畫面。鎖定畫面仍保持保護。 | 停止を確認した後、アラーム一覧に戻ります。オフの場合は元の画面に戻ります。ロック画面は保護されます。 | settings-screen |
| 184 | snooze | `firing_snooze_schedule_failed` | Snooze could not be scheduled. Alarm is still ringing. Try again or dismiss it. | 貪睡排程失敗，鬧鐘應仍在響鈴。請重新貪睡或直接解除。 | スヌーズを予約できませんでした。アラームは鳴り続けます。再試行するか停止してください。 | alarm-firing-screen |
| 185 | alarm | `alarm_edit_remaining_days_hours_minutes` | %1$d d %2$d h %3$d min | %1$d 天 %2$d 小時 %3$d 分鐘 | %1$d日 %2$d時間 %3$d分 | alarm-edit-screen |
| 186 | music | `music_library_system_tones_cancel` | Cancel | 取消 | キャンセル | music-screen-or-picker |
| 187 | alarm | `alarm_edit_delivery_test_limitation` | Uses an isolated default-tone test to check Android scheduling, notifications and wake screen. It does not test this alarm’s music playlist, production playback, or snooze chain. | 獨立使用系統預設鈴聲測試 Android 排程、通知與喚醒畫面；不會測試此鬧鐘的音樂清單、正式播放或貪睡流程。 | システム標準音を使った独立テストで、Androidの予約・通知・画面起動を確認します。このアラームのプレイリスト、通常の再生、スヌーズは検証しません。 | alarm-edit-screen |
| 188 | diagnostics | `settings_show_incident_events` | Show recent event chain | 查看近期事件時間線 | 直近のイベント履歴を表示 | settings-screen |
| 189 | diagnostics | `settings_export_incident_diagnostics` | Share diagnostic bundle | 分享診斷資料 | 診断データを共有 | settings-screen |
| 190 | diagnostics | `settings_incident_row_reason` | Source: %1$s · Code: %2$s | 來源：%1$s · 代碼：%2$s | 発生元：%1$s · コード：%2$s | settings-screen |
| 191 | diagnostics | `b2_test_description` | This diagnostic uses the saved music source and real alarm player, without changing alarm schedules or playback history. | 使用已儲存的音樂來源及正式響鈴播放器進行診斷，不改動原鬧鐘排程或播放歷史。 | 保存済みの音源とアラーム再生経路を使用します。通常の予定と再生履歴は変更しません。 | diagnostics |
| 192 | diagnostics | `b2_test_cancel` | Cancel music test | 取消音樂測試 | 音楽テストを中止 | diagnostics |
| 193 | snooze | `b2_test_snooze` | Test snooze: ring again in 1 min | 測試貪睡：1 分鐘後再響 | スヌーズテスト：1 分後に再通知 | diagnostics |
| 194 | diagnostics | `b2_test_scheduled` | Real music test scheduled for about one minute from now. | 已安排約一分鐘後進行正式音樂測試。 | 約 1 分後に音楽アラームのテストを設定しました。 | diagnostics |
| 195 | diagnostics | `b2_test_requires_saved` | Save this alarm before running the real music test. | 請先儲存此鬧鐘，再執行正式音樂測試。 | このアラームを保存してから音楽テストを実行してください。 | diagnostics |
| 196 | diagnostics | `b2_test_saved_only` | The test uses the last saved alarm and music settings. Unsaved edits are not included. | 測試採用上次儲存的鬧鐘與音樂設定，不含尚未儲存的修改。 | テストには保存済みのアラームと音楽設定を使用します。未保存の変更は含まれません。 | diagnostics |
| 197 | snooze | `u4_snooze_resume_position` | Continue the interrupted song at its last position | 從同一首音樂的中斷位置續播 | 同じ曲の中断位置から再開 | unclassified-screen |
| 198 | snooze | `u4_snooze_mode_note` | Applies to alarm music. If a track cannot be accessed or seeked, the alarm uses a playable fallback. | 適用於音樂鬧鐘。若音樂失效或無法定位播放位置，會改用可播放的備援。 | 音源が利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | unclassified-screen |
| 199 | music | `music_playlist_apply_to_alarm` | Apply this playlist to the alarm | 將此播放清單套用到鬧鐘 | このプレイリストをアラームに適用 | music-screen-or-picker |
| 200 | alarm | `alarm_edit_schedule_failed_new` | Alarm was not created because Android did not accept its schedule. Check alarm permissions and retry. | Android 未接受響鈴排程，因此沒有建立新的鬧鐘。請檢查鬧鐘權限後重試。 | Android が通知予定を受け付けなかったため、新しいアラームは作成されませんでした。権限を確認して再試行してください。 | alarm-edit-screen |
