# G6-Q2｜補齊其餘 163 Key 的中日內容初審（2026-11-21）

> **本輪完成的是模型輔助的字面對照、風險分級與審核交接，不是獨立語義核准。** 不能將 400/400 模型初審覆蓋率等同正式翻譯品質通過率。

## 來源與執行範圍

- 權威來源：S0 修正後的 Android 英文資源、`g6-200-current-20261120` 的 200-Key 目錄、中日各 200 個 G6 Debug 候選。原 LP0、G4、S0、Q1 皆不回寫或改稱已核准。
- Q1 37 Key × 2 語＝74 locale rows。本輪 Q2 完成其餘 **163 Key × 2 語＝326 rows** 的原文／候選快照、表層差異檢查、語義關注點及格式參數分類。合計 **200 Key／400 筆**模型輔助初審。
- Q2 的 **43 Key** 另有個別操作、資料隱私、格式或錯誤後果的風險註記；其餘初審只表示「目前字面沒有辨識出明確矛盾」，**不表示全部畫面、運行路徑或母語語感已驗證**。
- **21 Key 含 printf 格式參數**：Node 驗證候選參數型別和順序，尚未驗證 Android 實際變數值、長文字截斷、單複數或格式化動態語言切換。
- Q2 建議 **9 筆譯文修訂（6 Key）**，加上 Q1 18 筆，目前共 **27 筆未核准的候選修訂**。未套用到正式 Android 資源、G6 Debug packs 或 G4 來源帳本。

## Q2 的 9 筆新修訂建議

| Key | 語系 | 現行候選 | 建議替換（未核准） | 問題 |
|---|---|---|---|---|
| `settings_share_support_bundle` | zh-TW | 分享支援資料 | 分享支援用診斷資料包 | `BUNDLE_SCOPE_OMISSION` |
| `settings_share_support_bundle` | ja | サポートデータを共有 | サポート用データ一式を共有 | `BUNDLE_SCOPE_OMISSION` |
| `settings_dashboard_description` | zh-TW | 控制每日資訊畫面顯示哪些內容。 | 設定每日資訊畫面顯示的項目，兼顧實用性，避免資訊過多。 | `PURPOSE_OMISSION` |
| `settings_dashboard_description` | ja | 今日の情報画面に表示する項目を選択します。 | 今日の情報画面に表示する項目を選び、必要な情報を見やすく保ちます。 | `PURPOSE_OMISSION` |
| `ringtone_folder` | zh-TW | 資料夾 | 從資料夾選取 | `SOURCE_DIRECTION_OMISSION` |
| `ringtone_folder` | ja | フォルダー | フォルダーから | `SOURCE_DIRECTION_OMISSION` |
| `alarm_music_settings_remove_track` | ja | 曲を削除 | 曲をリストから外す | `DELETE_VS_REMOVE` |
| `recent_playback_empty` | zh-TW | 尚無音樂鬧鐘播放紀錄 | 尚無鬧鐘播放紀錄 | `SOURCE_SCOPE_NARROWED` |
| `b2_test_scheduled` | zh-TW | 已安排約一分鐘後進行正式音樂測試。 | 已排定約一分鐘後進行實際音樂鬧鐘測試。 | `TEST_VS_LIVE` |

### 重要區別

- `alarm_music_settings_remove_track` 的日文「曲を削除」可能被解讀為刪除裝置音訊。候選「曲をリストから外す」只是假設從列表移除，須查明實際 onClick／資料操作後才能採納。
- `ringtone_folder` 是連到 Android `OpenDocumentTree()` 的來源按鈕；新增「從資料夾」描述有相對應的程式位置，但是否過長仍待 T3。
- `recent_playback_empty` 英文沒有將播放歷史限定為「音樂」，現行繁中額外限定「音樂鬧鐘」；建議移除，但仍要核對實際歷史存取行為。
- `settings_dashboard_description` 英文含「資訊有用且不會雜亂」的目的，原譯省略；新增候選不應憑空改變 App 導航名稱。
- `b2_test_scheduled` 指真實音樂播放器路徑的測試，不是日常用途的正式鬧鐘排程。

## 字面不一致不等於翻譯錯誤

`stats_snoozed` 英文是「Snoozed」，中日候選是「貪睡率／スヌーズ率」。實際 `StatsScreen.kt` 的同一統計卡數值使用 `stats.snoozeRate%`，因此「率」有畫面上下文依據；這輪**保留原譯、不提議改成『已貪睡』**。Q2 Node 驗證該程式證據仍存在。

## 21 個格式 Key 的保留佇列

- `firing_snooze_for_minutes` — `%1$d`
- `firing_snooze_minutes` — `%1$d`
- `alarm_edit_minutes_seconds_short` — `%1$d`、`%2$d`
- `settings_minutes_short` — `%1$d`
- `music_library_video_audio_track_detected` — `%1$s`、`%2$s`
- `music_library_source_origin` — `%1$s`、`%2$s`
- `music_library_sources_read_failed` — `%1$s`
- `music_library_scan_failed` — `%1$s`
- `music_library_history_row` — `%1$d`、`%2$s`、`%3$s`
- `ringtone_nothing_matches` — `%1$s`
- `alarmlist_alarm` — `%1$s`
- `stats_recent_events` — `%1$s`
- `ringtone_folder_item` — `%1$s`
- `ringtone_saved_to_alarms` — `%1$s`
- `alarm_list_quick_alarm_for` — `%1$s`
- `music_playlist_add_selected` — `%1$d`
- `music_playlist_unavailable_count` — `%1$d`
- `music_playlist_selected_items` — `%1$d`
- `music_playlist_partial_add` — `%1$d`、`%2$d`
- `alarm_edit_remaining_days_hours_minutes` — `%1$d`、`%2$d`、`%3$d`
- `settings_incident_row_reason` — `%1$s`、`%2$s`

這 21 個 Key 的格式符號能通過靜態檢查，**不代表格式化 runtime UI、複數或動態內容已被真正驗證**。不能直接用 LP2 的純字串 resolver 強行覆蓋。

## TCL 驗收區分

| 指標 | 狀態 |
|---|---|
| 現行原文及候選對照 | 200 Key、400 locale rows |
| Q1 模型優先初審 | 37 Key／74 rows |
| Q2 模型字面初審 | **163 Key／326 rows** |
| Q2 額外風險註記 | **43 Key** |
| 提議修訂 | Q1 18 + Q2 9＝**27 筆**，未採用 |
| 格式參數 | 21 Key，僅靜態契約通過 |
| G1 術語正式核准 | 尚未完成 |
| G6-Q 獨立語義核准 | **0／400** |
| 正式語言包發布 | **拒絕**；G4/G5/G6 仍 fail-closed |
| 實機 T3 | 未驗證 |

## 產物與後續

Node-only CI 將產生 `build/localization/G6Q2_PRELIMINARY_NOT_APPROVED/{manifest.json,human-review-worklist.json,human-review-worklist.tsv,README.txt}`。400 列都包含來源與候選、風險、模型註記與待審查欄位；**獨立審核者、日期、批准、理由欄位保持空白**。資料表加上防試算表公式注入的跳脫。

下一階段 G6-Q3 應先獨立核對 Q1 10 個 CRITICAL Key、Q2 4 個 CRITICAL Key、全部 21 個格式 Key 的實際程序與母語呈現，再建立可追溯的獨立核准入口。G1 詞彙也須獨立批准，不能由 Node PASS 解鎖。

[Q2 原始 163-Key 矩陣](../../localization/governance/g6q/model-triage-q2.json) ｜ [TCL 分支規劃](../tcl/P31-G6Q2-COMPLETE-MODEL-SURFACE-AUDIT-20261121.md)

## Q2 另外發現：五個沒有直接靜態字串呼叫的 Key

本輪 CI 重新產生的 G2 靜態呼叫紀錄，與既有 `localization/governance/g2/unresolved-static-refs.json` 完全一致：下列五個 Key 在抽樣來源搜尋中**沒有直接 `R.string.<key>` 呼叫點**。這不等於功能不存在、可刪除或翻譯正確；可能由別名、舊版資源、動態資源 ID 或間接畫面組裝處理。

- `music_library_tab_videos` — 旁證為 `music_library_category_videos` 的實際分類呼叫。
- `alarm_music_settings_mode_shared` — 旁證在 `AlarmMusicSettingsScreen` 畫面定義。
- `alarm_music_settings_save` — 旁證在音樂設定的 `apply_draft` 按鈕。
- `alarm_music_settings_remove_track` — 旁證為播放清單移除按鈕 `onRemove`。本輪日文修訂提議**不能在不核對該操作的情況下採納**。
- `music_library_history_row` — 旁證為畫面 `HistoryTrackRow` 元件。

Q2 Node 程式現在要求這五項與 GitHub 已提交的 G2 未解決引用清單**精確相符**，只要來源移動或新增引用，必須重新核對並更新審核記錄，不能憑舊行號偷渡內容核准。
