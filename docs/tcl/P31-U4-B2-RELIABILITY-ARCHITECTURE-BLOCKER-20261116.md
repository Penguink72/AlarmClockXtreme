# P3.1 B2／U4｜跨程序、資料隔離與可靠性施工阻斷點

日期：2026-11-16。來源：直接核對 `AlarmService.kt`、`AlarmPlaybackPlayer.kt`、`AlarmMusicSession.kt`、`OnboardingTestAlarm.kt`、`AlarmMusicSettingsDraft`、Room v28 以及 S1 的實機失敗回報。

## T0｜不得混淆的現況
- [已接入編輯頁] 的 10 秒「系統送達測試」繼承 `OnboardingTestAlarm`：使用**獨立 Receiver/Activity 和系統預設鈴聲**，可以驗證 Android 對鬧鐘廣播／通知／解鎖畫面的送達；**完全不是**正式 AlarmService/Media3/播放清單/貪睡連續兩次響鈴的測試。
- 正式完整測試不能以臨時改寫使用者的真實鬧鐘時間、播放清單、歷史紀錄來達成；它必須擁有獨立 test session id、alarmId/fireId namespace、取消與故障恢復。
- `AlarmMusicSession` 依 `(alarmId, fireId, generation)` 隔離。貪睡時 `AlarmService.stopAlarmPlayback()` 釋放播放器及重建 session，缺乏可持久化的「當前候選 URI＋實際播放毫秒位置」。
- `AlarmPlaybackPlayer` 目前只有 `durationMs()/seekTo()`，沒有 `positionMs()` API；無法從 Media3 正式可靠取回中斷點。
- `AlarmMusicSettingsDraft` 有播放策略（獨立隨機、洗牌不重複、清單順序），沒有新需求「貪睡續播／換曲」設定。原 Room v28 `AlarmMusicSettingsEntity` 也無此欄。
- 從系統／資料無法一律證明某個 MediaStore `IS_RINGTONE` 的檔案一定是 OEM 出廠鈴聲；U5 必須標示**疑似系統音效**並提供預覽與復原，不應偽稱工廠出處已確認。

## U4｜符合原需求的正式實作約束
1. **每鬧鐘模式**：`RESUME_INTERRUPTED`（回到同一 URI 的中斷毫秒位置）、`NEXT_DIFFERENT`（下一首不同 URI；唯一可播候選例外）。不應偷換原本的三種 playlist 選曲策略。
2. 從已真正收到 `onIsPlaying=true` 的 track 保存 `lastUri`、`actualPositionMs`、`fireId`、`playlistId`、`mode`、`expiresAt`；保存應先於 Service 停止，並限定上限／TTL、防止舊指令覆蓋。
3. 下次由同一 alarmId 貪睡到期觸發，依尚有效且對應前次 `snooze fireId` 的 restore snapshot 進入選曲。對 RESUME：重新 resolve URI/授權、Media3 READY 後 seek，失效 URI 仍有有界跳歌與保底聲；對 NEXT：依選曲策略抽取，但首首不可與上一首相同（若候選只有一首，顯示單曲限制）。
4. `playback_history` 只在音訊重新真正開始播放時新增紀錄；READY／seekTo／pause 的假開始不寫；不擅自更動原有歷史 Schema v28。
5. 新 preference 必須可被編輯、保存、讀回、備份／還原、換機時有明確行為。**選用 Room v29 migration＋備份格式同步**，或設計可精確證明一致性的 sidecar per-alarm store；不能把 `SharedPreferences` 的 global toggle 冒充每鬧鐘模式。
6. 必須增加正／反案例：重建 process、兩個鬧鐘搶占、貪睡上限、URI 無效、無 seek 音訊（FLV）、重複 PendingIntent、錯誤指令、媒體權限撤銷、playlist 變動、唯一候選。
7. 貪睡基本可靠性 S1 T3 尚未確認之前，不應同時重寫已在使用的 Service 播放路徑，避免將兩個根因混在一起。

## B2｜正式測試架構
1. 在 `AlarmManager` 註冊具獨立 test namespace 的 PendingIntent。
2. Receiver 使用與正式鬧鐘相同的 Service 起動與前景通知機制，Service 必須能讀取隔離 test payload，不可從正規 `alarms` 讀到並修改使用者真實 row。
3. Test mode 自動排除真實 alarm event、歷史與統計；不能誤發 webhook、Guardian、第三方整合；只有 test-only diagnostics。
4. 明確支援停止、1 分鐘貪睡、再次響鈴，並能在 process death／螢幕亮滅情境保留測試 session。
5. T2 機械測試涵蓋 source gating、防串寫、service collision、process restart；T3 使用實機確認原故障 10 分鐘亮屏。
6. 現有編輯頁「10 秒送達測試」只能算 B2-0，不得宣稱 B2 已完成。

## 本輪策略裁定
- **允許並已施工**：U5 可復原已索引疑似鈴聲排除、U3 首頁 quick alarm 入口、S1-R 可展開事件／匯出、L1-B 響鈴畫面翻譯、U1.2 搜尋與清單讀回測試，以及 B2-0 系統送達試驗。
- **阻斷正式完成**：B2-production 與 U4-resume。需要先確保 P0 基礎貪睡 T3 可信，再實作 test namespace 和完整播放器 restore snapshot/設定遷移。禁止以「可編譯」替代「功能可用」。
- 原案 `P2-U5 完成` 不是 `B2／U4 全部完成` 的同義詞；每支 work package 分別按 T1、T2、T3 入庫。
