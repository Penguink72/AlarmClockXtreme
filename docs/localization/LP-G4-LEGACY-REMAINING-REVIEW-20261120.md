# LP-G4｜其餘 113 個原有字串模型表層核對（無獨立核准）

本輪核對 113 個 Key；6 Key 有待議修訂（中日合計 10 條），1 條英文來源過時，4 個在本批沒有直接靜態參照，3 個待進一步查核。

**模型未發現明顯矛盾不等於審核通過。LP0 原有中日譯文均未修改。**

## 建議調整

| Key | 語言 | 原文 | 候選 | 原因 |
|---|---|---|---|---|
| `settings_pane_personalization_description` | ja | テーマ、解除の難易度、プライバシー、アラーム画面の設定。 | テーマ、起床チャレンジの難易度、プライバシー、アラーム鳴動画面の動作。 | 日文舊譯文缺少 firing-screen behavior 的特定響鈴畫面語境。 |
| `settings_alarm_defaults_description` | zh-TW | 設定新鬧鐘的初始行為，減少重複設定。 | 設定新鬧鐘的預設行為，讓設定更快速、結果更容易預期。 | 保留原文 faster and more predictable 的目的。 |
| `settings_alarm_defaults_description` | ja | 新しいアラームの初期動作を設定します。 | 新しいアラームの初期動作を設定し、素早く一貫した設定ができるようにします。 | 保留原文 faster and more predictable 的目的。 |
| `settings_utilities_description` | zh-TW | 計時、統計及其他輔助工具。 | 快速開啟 App 的其他輔助工具。 | 原文只說快速開啟輔助工具，舊譯文多加具體功能類型。 |
| `settings_utilities_description` | ja | タイマー、統計、その他のツール。 | アプリの便利な補助ツールへすばやくアクセスできます。 | 原文只說快速開啟輔助工具，舊譯文多加具體功能類型。 |
| `settings_stopwatch_description` | zh-TW | 開啟碼錶。 | 記錄每圈時間，並特別標出最佳與最差分段成績。 | 舊中日譯文只說開啟碼錶，漏譯記錄圈數、最佳與最差分段。 |
| `settings_stopwatch_description` | ja | ストップウォッチを開きます。 | ラップを記録し、最速と最遅の区間タイムを強調表示します。 | 舊中日譯文只說開啟碼錶，漏譯記錄圈數、最佳與最差分段。 |
| `b2_test_requires_saved` | zh-TW | 請先儲存此鬧鐘，再執行正式音樂測試。 | 執行實際音樂響鈴測試前，請先儲存此鬧鐘。 | Real music test 不應被理解成一般正式鬧鐘排程。 |
| `b2_test_requires_saved` | ja | このアラームを保存してから音楽テストを実行してください。 | 実際の音楽アラームテストを行う前に、このアラームを保存してください。 | Real music test 不應被理解成一般正式鬧鐘排程。 |
| `u4_snooze_mode_note` | ja | 音源が利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | アラームの音楽に適用されます。曲を利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | 舊日文省略本規則適用鬧鐘音樂的首句。 |

## 英文來源錯誤

- `music_playlist_integration_pending` 仍說等待 Room v28，倉庫已經 `AlarmDatabase` v28；此字串在 repository 不可用時顯示。應先更正英文權威來源，再重新擷取並使現有譯文 stale，而不是讓 LLM 一直忠實翻譯過時內容。

## 五個沒有直接靜態引用的舊 Key

- `music_library_tab_videos`：Current video-category chip uses a different resource name.
- `alarm_music_settings_mode_shared`：No direct use of old shared-mode label; investigate dynamic menu bindings.
- `alarm_music_settings_save`：Current action says Apply draft; old Save key still requires migration audit.
- `alarm_music_settings_remove_track`：Removal exists but not with the exact old resource key.
- `music_library_history_row`：History UI uses a dedicated composable rather than the old format key.

## 對照表

| Key | English | zh-TW（原有） | ja（原有） | 模型表層檢查 |
|---|---|---|---|---|
| `settings_title` | Settings | 設定 | 設定 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_controls_title` | Alarm controls | 鬧鐘控制 | アラーム操作 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_exact_snooze_hint` | Applies once to this alarm. | 只對本次鬧鐘生效。 | 今回のアラームにのみ適用されます。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_left_saved_place` | Left saved place | 已離開儲存地點 | 保存場所から離れました | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_snooze_until_hint` | The alarm will fire at the next occurrence of this time. | 鬧鐘會在下一次到達此時刻時響鈴。 | 次に指定時刻になったときにアラームが鳴ります。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_challenge_handwriting_title` | Draw the wake-up word | 手寫指定文字 | 指定された語句を手書き | CONTEXT_FOLLOWUP |
| `firing_challenge_simon_title` | Play back the pattern | 重現顏色順序 | 色の順番を再現 | CONTEXT_FOLLOWUP |
| `firing_challenge_none_support` | No extra challenge is required for this alarm. | 此鬧鐘不需額外解除挑戰。 | このアラームには追加のチャレンジはありません。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_challenge_photo_support` | Take a fresh photo that closely matches the saved reference. | 重新拍照，並盡量符合參考照片。 | 保存した参照写真に近い写真を撮影します。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_challenge_speed_support` | Groggy fingers slow you down, so prove you can type at speed. | 以一定速度正確打字，確認手指已清醒。 | 素早く正確に入力できるか確認します。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_challenge_voice_status` | Say the phrase shown below, or use the typed fallback. | 說出下方短句，或使用文字輸入備援。 | 下のフレーズを話すか、文字入力に切り替えます。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `firing_challenge_sheep_status` | Tap every sheep; avoid the goats. | 點選每隻羊，避開山羊。 | 羊だけをタップし、ヤギを避けます。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_page_integrations` | Integrations & safety | 整合與安全 | 連携と安全 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_smart_alarm` | Smart alarm | 智慧鬧鐘 | スマートアラーム | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_section_hue_description` | Coordinate bedside lighting with the alarm for a gentler rise. | 配合床邊燈光，讓起床過程更平緩。 | ベッドサイドの照明と連携し、穏やかに目覚められます。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_section_anti_snooze_description` | Add guardrails that make repeated delay harder. | 增加限制，避免一再延後起床。 | スヌーズの繰り返しを抑える制限を追加します。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_background_selected` | Background image selected. | 已選取背景圖片。 | 背景画像を選択しました。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_background_permission_warning` | Background image selected. Re-select it if Android revokes access. | 已選取背景圖片。若 Android 撤銷存取權，請重新選取。 | 背景画像を選択しました。Android がアクセス権を取り消した場合は選び直してください。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_saving` | Saving alarm… | 正在儲存鬧鐘… | アラームを保存中… | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_minutes_seconds_short` | %1$dm %2$ds | %1$d 分 %2$d 秒 | %1$d 分 %2$d 秒 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_group_custom_name` | Custom group name | 自訂群組名稱 | カスタムグループ名 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_heartbeat` | Heartbeat | 心跳 | 心拍 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_immediately_default` | Immediately (default) | 立即（預設） | すぐに（デフォルト） | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_repeat_weekends` | Weekends | 週末 | 週末 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_squats_required` | Squats required | 所需深蹲次數 | 必要なスクワット回数 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_capture_photo_hint` | Capture a reference photo from the place or angle the alarm should require. | 請在要作為解鈴條件的地點或角度拍攝參考照片。 | 解除条件にする場所や角度で参照写真を撮影してください。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_challenge_chain` | Challenge chain | 連續解除挑戰 | 連続解除チャレンジ | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_challenge_shake` | Shake phone | 搖晃手機 | 端末を振る | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_challenge_pushup` | Push-ups | 伏地挺身 | 腕立て伏せ | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_challenge_pvt` | Reaction test | 反應時間測驗 | 反応速度テスト | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_snooze_limit_unlimited` | Unlimited | 不限次數 | 無制限 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_24_hour` | 24-hour time | 24 小時制 | 24時間表示 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_pane_readiness` | Readiness | 使用準備 | 使用準備 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_pane_defaults_description` | New-alarm behavior, dashboard content, and navigation tabs. | 新鬧鐘行為、首頁內容及導覽分頁。 | 新規アラーム、ホーム画面、ナビゲーションタブの設定。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_pane_integrations_description` | Webhook, holidays, Hue, Health Connect, and connection status. | Webhook、假日、Hue、Health Connect 與連線狀態。 | Webhook、休日、Hue、Health Connect、接続状態。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_pane_personalization_description` | Theme, challenge difficulty, privacy, and firing-screen behavior. | 主題、解除難度、隱私及響鈴畫面行為。 | テーマ、解除の難易度、プライバシー、アラーム画面の設定。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `settings_pane_backup_description` | Encrypted export, restore preview, and conflict-safe import. | 加密匯出、還原預覽及避免衝突的匯入。 | 暗号化エクスポート、復元プレビュー、安全なインポート。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_pane_utilities_description` | Companion tools, support bundle export, app version, and license. | 附屬工具、支援資料匯出、版本及授權。 | 関連ツール、サポートデータ、アプリのバージョンとライセンス。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_share_support_bundle` | Share support bundle | 分享支援資料 | サポートデータを共有 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_battery_protected` | Battery protected | 電池設定已保護 | バッテリー設定は保護済み | CONTEXT_FOLLOWUP |
| `settings_battery_setup_needed` | Needs battery setup | 需要設定電池權限 | バッテリー設定が必要 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_choose_group_hint` | Choose a group to keep wide screens focused. | 選擇分類以聚焦顯示內容。 | カテゴリーを選択して表示内容を絞り込みます。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_setup_needed` | Setup needed | 需要設定 | 設定が必要 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_not_selected` | Not selected | 未選取 | 未選択 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_alarm_defaults_description` | Set the behavior new alarms start with so setup feels faster and more predictable. | 設定新鬧鐘的初始行為，減少重複設定。 | 新しいアラームの初期動作を設定します。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `settings_minutes_short` | %1$d min | %1$d 分鐘 | %1$d 分 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_dashboard_description` | Control what appears on the My Day screen so it stays useful without feeling busy. | 控制每日資訊畫面顯示哪些內容。 | 今日の情報画面に表示する項目を選択します。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_utilities_description` | Quick access to companion tools that round out the app. | 計時、統計及其他輔助工具。 | タイマー、統計、その他のツール。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `settings_alarm_statistics` | Alarm statistics | 鬧鐘統計 | アラーム統計 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_stopwatch_description` | Track laps with best and worst splits highlighted. | 開啟碼錶。 | ストップウォッチを開きます。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `settings_language_description` | Choose the app language. Core alarm, sound, and music screens are available in English, Traditional Chinese, and Japanese; other screens may remain in English. | 選擇 App 語言。鬧鐘、音效與音樂核心畫面支援英文、繁體中文及日文；其他畫面可能仍顯示英文。 | アプリの言語を選択します。アラーム、サウンド、音楽の主要画面は英語、繁体字中国語、日本語に対応しています。他の画面は英語のままの場合があります。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_language_dialog_title` | Choose app language | 選擇 App 語言 | アプリの言語を選択 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_language_english` | English | 英文 | 英語 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_language_japanese` | Japanese | 日文 | 日本語 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_ready` | Ready | 就緒 | 準備完了 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_close` | Close | 關閉 | 閉じる | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_list_clear_filters` | Clear filters | 清除篩選條件 | フィルターを解除 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_list_pause` | Pause | 暫停 | 一時停止 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `ringtone_folder` | From folder | 資料夾 | フォルダー | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_title` | Music library and playback history | 音樂庫與播放歷史 | 音楽ライブラリと再生履歴 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_history_title` | Playback history | 播放歷史 | 再生履歴 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_catalog_empty` | No indexed tracks yet. Allow audio access or scan an authorized music folder. | 尚無已索引曲目。請允許音訊存取權或掃描已授權的音樂資料夾。 | 索引済みの曲はありません。音声アクセスを許可するか、許可済みフォルダーをスキャンしてください。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_tab_videos` | Videos | 影片 | 動画 | NO_DIRECT_STATIC_REFERENCE |
| `music_library_video_audio_track_detected` | %1$s · audio track found during folder scan; decoder support is not verified · %2$s | %1$s · 資料夾掃描已偵測到音軌；尚未驗證解碼支援 · %2$s | %1$s · フォルダーのスキャンで音声トラックを検出（デコーダー対応は未検証） · %2$s | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_source_scope_all` | All sources | 所有來源 | すべてのソース | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_preview` | Preview track | 試聽曲目 | 曲を試聴 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_music_settings_mode_shared` | Use the shared music pool | 使用共用音樂池 | 共有音楽プールを使う | NO_DIRECT_STATIC_REFERENCE |
| `alarm_music_settings_strategy_order` | Playlist order | 依播放清單順序 | プレイリスト順 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_music_settings_artists` | Artists | 歌手 | アーティスト | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_music_settings_save` | Save | 儲存 | 保存 | NO_DIRECT_STATIC_REFERENCE |
| `music_library_add_folder` | Add folder | 新增資料夾 | フォルダーを追加 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_source_origin` | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_source_unknown_origin` | Storage provider | 儲存空間提供者 | ストレージプロバイダー | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_sources_read_failed` | Could not read music sources: %1$s | 無法讀取音樂來源：%1$s | 音楽ソースを読み取れませんでした：%1$s | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_no_history` | No playback history for this date. | 這一天沒有播放紀錄。 | この日の再生履歴はありません。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_close` | Close | 關閉 | 閉じる | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_history_row` | %1$d. %2$s — %3$s | %1$d. %2$s — %3$s | %1$d. %2$s — %3$s | NO_DIRECT_STATIC_REFERENCE |
| `music_library_pool_all_candidates` | All candidates | 全部候選 | すべての候補 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_pool_clear_selection` | Clear selection | 清除選取 | 選択を解除 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_pool_status_included` | Added to music pool | 已加入音樂池 | 音楽プールに追加済み | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_pool_source_label` | Source | 來源 | ソース | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_pool_type_audio` | Audio file | 音訊檔 | 音声ファイル | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `ringtone_preview_unavailable` | Preview unavailable | 無法試聽 | 試聴できません | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `ringtone_nothing_matches` | Nothing matches “%1$s”. Try a shorter or different term. | 找不到符合「%1$s」的項目。請縮短或更換搜尋詞。 | 「%1$s」に一致する項目はありません。検索語を短くするか変更してください。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarmlist_alarm` | %1$s alarm | %1$s 個鬧鐘 | %1$s 件のアラーム | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `ringtone_folder_item` | %1$s (folder) | %1$s（資料夾） | %1$s（フォルダー） | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarmlist_share_alarm` | Share alarm | 分享鬧鐘 | アラームを共有 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `ringtone_saved_to_alarms` | Saved “%1$s” to your alarms. | 已將「%1$s」儲存至鬧鐘。 | 「%1$s」をアラームに保存しました。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_list_quick_alarm_for` | Set quick alarm for %1$s | 設定於 %1$s 的快速鬧鐘 | %1$s にクイックアラームを設定 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_category_music` | Music | 音樂 | 音楽 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_integration_pending` | Playlist storage is waiting for the Room v28 integration. Existing Room v27 search and music-pool actions remain available; playlist changes are not saved yet. | 播放清單儲存仍待 Room v28 整合；現有 Room v27 搜尋與音樂池操作仍可使用，目前不會儲存播放清單變更。 | プレイリスト保存は Room v28 の統合待ちです。既存の Room v27 検索と音楽プール操作は引き続き利用できますが、プレイリストの変更はまだ保存されません。 | STALE_ENGLISH_SOURCE_BLOCKS_APPROVAL |
| `music_playlist_add_selected` | Add %1$d selected | 加入已選的 %1$d 項 | 選択した %1$d 件を追加 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_rename` | Rename | 重新命名 | 名前を変更 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_empty` | This playlist is empty. | 此播放清單目前沒有曲目。 | このプレイリストには曲がありません。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_unavailable_count` | %1$d tracks could not be added because they are unavailable. | %1$d 首曲目無法存取，因此未能加入。 | %1$d 件は利用できないため追加できませんでした。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_music_settings_playlist_smart_all` | All available music | 所有可用音樂 | 利用可能なすべての音楽 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_selected_items` | %1$d selected items | 已選取 %1$d 個項目 | %1$d 件選択中 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_playlist_partial_add` | Added %1$d tracks; %2$d unavailable tracks were skipped. | 已加入 %1$d 首曲目；略過 %2$d 首無法存取的曲目。 | %1$d 件を追加し、利用できない %2$d 件はスキップしました。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `recent_playback_title` | Recently played | 最近播放 | 最近の再生 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `recent_playback_empty` | No alarm playback history yet | 尚無音樂鬧鐘播放紀錄 | アラーム再生履歴はありません | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `replay_action_pause` | Pause | 暫停 | 一時停止 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `replay_status_playing` | Playing | 播放中 | 再生中 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `replay_open_in_external` | Open with other app | 以其他 App 開啟 | 他のアプリで開く | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `music_library_history_filter_recent` | Recent | 最近播放 | 最近の再生 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `alarm_edit_remaining_days_hours_minutes` | %1$d d %2$d h %3$d min | %1$d 天 %2$d 小時 %3$d 分鐘 | %1$d日 %2$d時間 %3$d分 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_show_incident_events` | Show recent event chain | 查看近期事件時間線 | 直近のイベント履歴を表示 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_export_incident_diagnostics` | Share diagnostic bundle | 分享診斷資料 | 診断データを共有 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `settings_incident_row_reason` | Source: %1$s · Code: %2$s | 來源：%1$s · 代碼：%2$s | 発生元：%1$s · コード：%2$s | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `b2_test_requires_saved` | Save this alarm before running the real music test. | 請先儲存此鬧鐘，再執行正式音樂測試。 | このアラームを保存してから音楽テストを実行してください。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `b2_test_saved_only` | The test uses the last saved alarm and music settings. Unsaved edits are not included. | 測試採用上次儲存的鬧鐘與音樂設定，不含尚未儲存的修改。 | テストには保存済みのアラームと音楽設定を使用します。未保存の変更は含まれません。 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `u4_snooze_resume_position` | Continue the interrupted song at its last position | 從同一首音樂的中斷位置續播 | 同じ曲の中断位置から再開 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |
| `u4_snooze_mode_note` | Applies to alarm music. If a track cannot be accessed or seeked, the alarm uses a playable fallback. | 適用於音樂鬧鐘。若音樂失效或無法定位播放位置，會改用可播放的備援。 | 音源が利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | REVISION_SUGGESTED_SOURCE_UNCHANGED |
| `music_playlist_apply_to_alarm` | Apply this playlist to the alarm | 將此播放清單套用到鬧鐘 | このプレイリストをアラームに適用 | NO_CLEAR_CONFLICT_IN_MODEL_SURFACE_CHECK |

**核准仍為 0，不曾獨立驗證全數 155 個既有翻譯的操作語義。**
