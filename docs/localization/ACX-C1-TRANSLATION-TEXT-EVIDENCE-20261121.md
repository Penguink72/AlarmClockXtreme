# ACX C1｜翻譯文本、詞彙對照、來源證據與語言包發行分層（2026-11-21）

> **本文整理真實現存的翻譯材料與下一輪審核契約；不是 400 筆獨立核准，也不回寫 strings.xml、Debug pack、G4 ledger。** 如有內容差異，原始工作檔與其 SHA 優先於本導覽副本。完整原文、現行候選與 27 筆建議分別在 Q1/Q2 JSON 和原報告；本頁只建立可操作的文字來源鏈、選例與處置方式。

## 1. 文字材料的六層權威（不可混用）

| 層 | 存放處 | 用途 | 目前證據 |
|---|---|---|---|
| L0 權威 Android 原文 | `app/src/main/res/values/strings.xml` | 真正 key/source 的當期權威，包含型別和格式；原文變更產生 sourceHash 失效 | S0 修正 `music_playlist_integration_pending` |
| L1 已有內建三語 | `app/src/main/res/values-b+zh+Hant+TW/strings.xml`、`values-ja/strings.xml` | 現有 App 內建繁中、日文；不可全部稱為未翻譯 | 長期已實際存在，完整覆蓋/語境仍須盤點 |
| L2 概念對照詞彙 | `docs/localization/LP-G1-GLOSSARY-CONTRACT-20261119.md` 與 `localization/governance/g1/` | 限定 Snooze/Dismiss/Save/Schedule/Remove 等不同操作含義 | 18 個概念候選，**尚未獲獨立批准** |
| L3 原文與用途證據 | `localization/governance/g2/context-evidence.json`、`unresolved-static-refs.json` | 畫面/呼叫／語境與風險；靜態直接引用 ≠ 執行結果 | 五個 Key 缺直接引用 |
| L4 候選與模型審核 | `app/src/debug/assets/lp2/packs/`、`localization/governance/g6q/model-triage-q1.json`、`model-triage-q2.json` | G6 的 200 個源 Key × zh-TW/ja 共 400 筆模型字面初審，27 筆修訂建議 | **MODEL_PRELIMINARY_ONLY** |
| L5 獨立核准與發行 | `localization/governance/g4/review-ledger.json`、G5 release CLI | 經獨立批准的官方認證包；因來源／術語／核准缺失 fail-closed | **G6 Q=0/400** |

LP0 `lp0-200-20261118` 與 G6 `g6-200-current-20261120` 是不同來源版本。來源變動時不能把舊譯文批准轉成新批准；也不可因 G6 模型初審涵蓋全部 400 列就宣稱官方包可發行。

## 2. 術語對照應綁功能動作，不做單字全域替換

| 概念 | 原文 | 繁中候選 | 日文候選 | 操作區別 |
|---|---|---|---|---|
| alarm.action.snooze | Snooze | 貪睡 | スヌーズ | 延後再響，不等於解除 |
| alarm.action.dismiss | Dismiss | 解除 | 停止 | 結束當次鬧鐘，不是只停音訊 |
| alarm.action.schedule | Schedule | 排程 | スケジュール | 系統接受排程，不等於按下儲存 |
| ui.action.save | Save | 儲存 | 保存 | 持久化設定，不保證成功排程 |
| ui.action.delete | Delete / Remove | 刪除 | 削除 | 應依物件範圍區分刪除鬧鐘、移除曲目、解除來源登錄 |
| ui.action.disable | Disable | 停用 | 無効 | 保留資料但停止作用 |
| music.library | Music library | 音樂庫 | 音楽ライブラリ | 可存取的音樂索引 |
| music.playlist | Playlist | 播放清單 | プレイリスト | 有順序/成員的清單，不是整個索引庫 |
| music.playback_history | Playback history | 播放歷史 | 再生履歴 | 已實際開播的曲目與時間，不含測試診斷 |
| music.object.ringtone | Ringtone | 鈴聲 | アラーム音 | 在鬧鐘情境不直譯成電話來電音 |

完整 18 個候選以原有 G1 檔案為準。上表不宣稱已批准；若新 locale 有不同語法，審核者可填寫適用的變體與禁用語。

## 3. G6-Q1/Q2 翻譯文本樣例與修訂權限

**Q1 原檔**：[37-Key／18 筆提案](P31-G6Q1-CONTENT-AUDIT-20261121.md)（`model-triage-q1.json` 提供 sourceHash、完整候選與上下文）。
**Q2 原檔**：[163-Key／9 筆提案](P31-G6Q2-ALL-200-MODEL-CONTENT-AUDIT-20261121.md)（`model-triage-q2.json` 提供原文、候選、模型註記）。
**合計**：27 筆未核准修改建議，不改動真正的 Debug 翻譯包；如需批量審核，使用 G6Q2 CI 的 400-row TSV/JSON，所有審核者欄位目前為空。

| Key / locale | 目前候選 | 建議替換文字（未核准） | 必核對真實使用情境 |
|---|---|---|---|
| `music_library_remove_source` / ja | ソースを削除 | 音楽ソースの登録を解除 | 解除登錄不刪除手機檔案 |
| `music_playlist_remove` / ja | 削除 | プレイリストから外す | 清單移除不是檔案刪除 |
| `firing_snooze_schedule_failed` / zh-TW | 貪睡排程失敗，鬧鐘應仍在響鈴。請重新貪睡或直接解除。 | 貪睡排程失敗，鬧鐘仍在響鈴。請重試貪睡或直接解除鬧鐘。 | 是否確實仍在響鈴，需對照程序 |
| `notif_snooze_countdown_title` / zh-TW | 已稍後提醒的鬧鐘 | 貪睡中的鬧鐘 | 通知與當次火次狀態 |
| `notif_snooze_countdown_subtext` / zh-TW | 稍後提醒倒數 | 貪睡倒數 | 不是一般提醒 |
| `settings_share_support_bundle` / zh-TW | 分享支援資料 | 分享支援用診斷資料包 | 是否包含診斷與隱私提示 |
| `settings_share_support_bundle` / ja | サポートデータを共有 | サポート用データ一式を共有 | bundle 範圍 |
| `ringtone_folder` / zh-TW | 資料夾 | 從資料夾選取 | 連至 OpenDocumentTree，長文字是否截斷 |
| `ringtone_folder` / ja | フォルダー | フォルダーから | 資料夾來源的方向 |
| `alarm_music_settings_remove_track` / ja | 曲を削除 | 曲をリストから外す | 五個未明確引用 Key 之一，先證明真正使用與 onRemove 效果 |
| `recent_playback_empty` / zh-TW | 尚無音樂鬧鐘播放紀錄 | 尚無鬧鐘播放紀錄 | 歷史可能含非音樂來源 |
| `b2_test_scheduled` / zh-TW | 已安排約一分鐘後進行正式音樂測試。 | 已排定約一分鐘後進行實際音樂鬧鐘測試。 | 測試而非一般正式排程 |

其餘 Q1 的完整 18 筆和 Q2 的完整 9 筆**不得省略**：以相應原檔的資料列及 sourceHash 為準。此表為範例而不是替代原始矩陣；接受、退回與複核都需逐筆留下決定。

**保留正例**：`stats_snoozed` 原文 Snoozed，畫面數值為 `snoozeRate%`；既有 zh-TW「貪睡率」與 ja「スヌーズ率」有實際畫面依據，不可以英文表層不含 rate 就強制改成已貪睡。

## 4. 待核的五個 Key 與 21 個格式化 Key

五個 G2 未發現直接引用的 Key：`music_library_tab_videos`、`alarm_music_settings_mode_shared`、`alarm_music_settings_save`、`alarm_music_settings_remove_track`、`music_library_history_row`。各自沿著真實呼叫、參數組裝及 UI 操作追查；判為 `ACTIVE_DIRECT / ACTIVE_INDIRECT / LEGACY_UNUSED / UNKNOWN`。**UNKNOWN 不能直接刪除、批准或湊接入比例。**

21 個含 printf 的 Key 在 Q2 報告有完整清單；其機械佔位符比對成功不保證 `%1$d`、`%2$s` 在實際 locale/runtime 經過正確格式化。C1 審核內容語義，B0/B2 建立對應的動態文字解析及 Android 回退；避免兩組團隊互相假定對方已完成。

## 5. 每一筆正式可追溯的文本工作紀錄

建議 TSV / JSON 必有：

```text
catalogRevision | key | source | sourceHash | type | argumentContract |
uiSurface | callsite | behaviorEvidence | targetLocale |
existingTranslation | candidateText | proposalText | issueCode |
glossaryRevision | modelObservation | decision | decisionReason |
reviewerIdentity | reviewerDate | translationHash | approvalAuthority |
approvalStatus | staleReason | fallbackPolicy | releaseScope
```

- `decision` 允許 ACCEPT_CANDIDATE、REWRITE、REJECT、HOLD、LEGACY_UNUSED；這是審查處置，不等於 approval。
- `approvalStatus=APPROVED` 必須有**獨立審核者、版本、hash、上下文與授權證據**，模型不能自填。
- 單一個 sourceHash 改變，只使依賴它的翻譯過期。S0 的 `music_playlist_integration_pending` 需在 G6 現行版另建處置紀錄；`localization/governance/g4/source-defects.json` 的歷史 OPEN 保持供稽核，不靜默刪除。
- 顯示可用的 USER_SUPPLIED_UNREVIEWED 自訂包和 OFFICIALLY_REVIEWED 包要用不同信任等級。前者可供使用者選擇，但不解除既有 G4/G5/G6 官方發行安全閘門。修改此政策需另支實作及負向測試。
- 逐條查詢與搜尋要允許找出缺翻譯、格式問題、上下文待查、已退役 Key；不能只顯示一次模型審核的高百分比。

## 6. C1 閉環與停止條件

- 先確認實際 Active Key、原文型別、所在畫面與操作結果，再處置 27 建議；不合理或冗長提案可拒絕，不強迫全數採用。
- 對 14 CRITICAL Key 與 21 格式 Key 將審查分為語義、排版與 runtime 格式；必須有「待查」出口，不能以模型判讀取代獨立批准。
- 詞彙、Android 原文、模型候選與官方審核資料相互版本化，但不得因另一語言包尚無批准而阻止使用者自行匯入結構合法的本地包。
- 只在正式核准條件全部符合的特定 locale/source revision 上生成該 locale 的官方 pack；現有 G5/G6 release 在未核准時繼續拒絕。
