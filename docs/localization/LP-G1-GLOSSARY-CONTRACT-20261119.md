# LP-G1｜術語對照契約與禁止混淆規則（工程候選）

本版以 LP0 的 18 個術語概念為基礎，逐項加入「行為後果、容易混淆的概念、可允許的長句變體、風險」，避免只按英文單字進行全文替換。

**沒有授予任何譯文語義核准。** 此契約服務後續的翻譯候選與人工審查；即使程式能比對用詞，仍不能宣稱該句翻譯正確。

| 概念 ID | 英文 | 繁中候選 | 日文候選 | 風險 |
|---|---|---|---|---|
| `alarm.action.snooze` | Snooze | 貪睡 | スヌーズ | critical |
| `alarm.action.dismiss` | Dismiss | 解除 | 停止 | critical |
| `alarm.object.alarm` | Alarm | 鬧鐘 | アラーム | normal |
| `music.object.ringtone` | Ringtone | 鈴聲 | 着信音 | high |
| `music.library` | Music library | 音樂庫 | 音楽ライブラリ | high |
| `music.playback_history` | Playback history | 播放歷史 | 再生履歴 | high |
| `music.playlist` | Playlist | 播放清單 | プレイリスト | high |
| `music.track` | Track | 曲目 | 曲 | normal |
| `music.random` | Random / Shuffle | 隨機 | ランダム | normal |
| `music.repeat` | Repeat | 重複 | 繰り返し | high |
| `ui.action.save` | Save | 儲存 | 保存 | high |
| `ui.action.delete` | Delete / Remove | 刪除 | 削除 | critical |
| `ui.action.disable` | Disable | 停用 | 無効 | critical |
| `alarm.action.schedule` | Schedule | 排程 | スケジュール | critical |
| `music.action.resume` | Resume | 繼續播放 | 再開 | high |
| `music.action.stop` | Stop | 停止 | 停止 | critical |
| `app.permission` | Permission | 權限 | 権限 | high |
| `alarm.action.test` | Test alarm | 測試鬧鐘 | テストアラーム | high |

## 特別約束

- Snooze = 暫停這次響鈴並安排再次響鈴，不等於 Dismiss。繁中「稍後提醒」可作通知／說明句候選，**不代表已批准任意使用**。
- Dismiss = 結束本次響鈴；Stop audio 不代表 dismiss alarm。
- Delete = 刪除持久資料，Disable = 保留資料但停用。二者不可互換。
- Save = 保存設定，Schedule = 排入系統鬧鐘排程；不能以儲存成功宣稱已成功排程。
- Music library = 可存取音樂索引，Playlist = 選取或排列的曲目清單，二者不可互換。
- Test alarm 不作為正式鬧鐘可靠性的語義證據。

**此檔只建立候選用語及審核規則。** G2 未能追溯的 UI 位置仍視為未知，禁止 LLM 因候選詞典而自行核准原有翻譯。
