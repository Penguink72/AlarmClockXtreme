# B9 → Preview 2：影音混合音樂池整合接縫（TCL 只讀盤點）

> 編製：2026-11-13。本文是待驗收工作紀錄，不代表 B9 已編譯或功能通過。僅在獨立文件分支建立，不變更應用程式碼、main 或 Preview 1。

## 0. 溯源與邊界

- **已驗證的遠端起點**：`preview/music-alarm-1@e7125b3f1db94ab3af7338fce6b4fb9b07f27e37`（Room v26、固定簽章、B1/B2/B3/B4/B8）。
- **Copilot 本機回報，尚未於 GitHub 可讀**：`b9-av-mixed-music-pool@9db14a38381a9a829f018998d890778a1dae70fa`。缺 JDK/SDK，因此未編譯；`27.json` 尚未正式匯出。必須透過經驗證的 bundle 或 GitHub push 取得程式，才能作 T1 差異驗證。
- **使用者需求**：SAF 多資料夾遞迴找出可播放音訊 + 含可解碼音軌的影片，兩種媒體同池批量加入、排除／恢復；鬧鐘**只播放聲音且不顯示影像**；記錄已實際播放歌曲；APK 可覆蓋升級且保留資料。**不是只搜尋音訊**。
- **暫不做**：多套音樂池、各鬧鐘獨立選池、額外影音 UI、全量介面翻譯或改寫整個播放器。
- **操作語義**：來源 Source（授權資料夾）→ 候選 Catalog（掃描得到）→ 成員 Membership（INCLUDED/EXCLUDED、未加入）→ 實際播放 History。不要再將「掃描成功」等同「有播放資格」。

## 1. 遠端已確認的接縫

| ID | 來源證據（Preview 1） | B9/Preview 2 必須驗證的結果 | 狀態 |
|---|---|---|---|
| S1 選曲 SQL | `MusicLibraryDao.pickRandom()/pickRandomExcluding()` 目前直接查 `music_tracks` | 兩條路徑均只查有效 INCLUDED 成員；候選及 EXCLUDED 不入選；不能被 SQLite bind 參數數量限制卡住 | 確認缺口，待檢 B9 |
| S2 掃描≠選池 | `MusicLibraryIndexer.scan()` 包含 MediaStore 全域音訊及 SAF，`MusicLibraryViewModel.scan()` 後同步索引 | 新掃描僅加候選、不默默進池；可針對指定來源／搜尋結果**一鍵整批加入**，不需要逐首點選；預設系統音效不能未經選擇入池 | 確認缺口，待檢 B9 |
| S3 重掃與排除 | `MusicLibraryDao.replaceAll()` 清空重建 Catalog；來源快照另存 JSON | Membership 使用獨立、可持續的 Room 狀態；重掃、重啟、刪除重疊來源、權限撤銷、SD 卡移除不復活 EXCLUDED、不得刪手機原檔或播放歷史；JSON／Room 失敗須可恢復 | 確認風險，待檢 B9 |
| S4 正式 Room 27 | `AlarmDatabase` 遠端仍為 v26；有已驗證 `25.json`、`26.json` | 非破壞性 `MIGRATION_26_27`；正式 KSP `27.json`、runtime identityHash 與零差異 CI；24→25→26→27 資料全保留 | 尚未實作於遠端 |
| S5 影音純音訊播放 | `AlarmService` 用 Media3 播放 URI、`onEnded` 再抽下一首 | MP3/FLAC 與有音軌 MP4/MKV 共用同池；明確停用影片軌解碼/畫面；跳過不可播／超時媒體、有界重試後安全備援；解除、貪睡、逾時停止 | 待檢 B9、待實機 |
| S6 播放紀錄 | `MusicLibraryRepository.recordStarted()` 已使用 `displayTitle`；`AlarmService.onStarted` 寫入 | 僅真的開始播放才寫紀錄，保留中日英名稱、時間、順序；重掃／排除／來源消失不改舊歷史；播放中修改池，下一首按新狀態抽取 | 既有接線確認，B9 回歸待驗 |
| S7 APK 版本與簽章 | Preview 1 CI 寫死 `versionCode=139/140`、固定 Debug 憑證 | Preview 2 用 141 主安裝／142 升級驗證；簽章指紋維持 `4F:15:3F:91:16:0F:A5:89:C6:CF:D5:5C:DE:1B:4A:CA:B3:81:A7:0B:0D:CF:31:D7:55:FF:39:40:4C:56:A2:1E`；Package `com.sysadmindoc.alarmclock.debug`；後續不倒退版本 | 已確認需更新 |
| S8 UI 與安全備援 | Preview 1 有 Music library / Manage folders 入口與 B1/B2 閃退修補；`startMusicDefaultFallback()` 獨立存在 | 候選／已加入／已排除、搜尋、批次加入／排除／恢復；操作範圍明確、不能誤把全 MediaStore 入池；使用者排除的系統鈴聲與**故障時預設鬧鐘備援**不同，後者不能被刪除 | 待檢 B9 |

程式參考（皆指向不可變的 Preview 1 基準）：

- [MusicLibraryDao.kt](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/app/src/main/java/com/sysadmindoc/alarmclock/data/local/MusicLibraryDao.kt)
- [MusicLibraryIndexer.kt](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/MusicLibraryIndexer.kt)
- [MusicLibraryViewModel.kt](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/MusicLibraryViewModel.kt)
- [AlarmDatabase.kt](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/app/src/main/java/com/sysadmindoc/alarmclock/data/local/AlarmDatabase.kt)
- [AlarmService.kt](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/app/src/main/java/com/sysadmindoc/alarmclock/service/AlarmService.kt)
- [p0-android-baseline.yml](https://github.com/Penguink72/AlarmClockXtreme/blob/e7125b3f1db94ab3af7338fce6b4fb9b07f27e37/.github/workflows/p0-android-baseline.yml)

## 2. 整合步驟與放行閘門

- [ ] **T0：接收 B9**。核對 bundle/遠端提交 `9db14a3`、parent `e7125b3`、工作樹乾淨；新建 `preview/music-alarm-2`，不修改 main/Preview 1。
- [ ] **T1：程式差異檢查**。逐一檢驗 S1～S8，特別是 Dao 查詢條件、來源範圍的批量選取、Legacy 索引的一次性 INCLUDED 遷移、重掃後 EXCLUDED 持續、Media3 停用影片軌。問題確定後才最小修補。
- [ ] **T2：編譯與 Migration**。執行嚴格 `:app:assembleFdroidDebug`、`:app:testFdroidDebugUnitTest`、Room 24→27 遷移；正式產出提交 `27.json`，以 KSP 自動重生作零差異驗證；不可僅手改 hash 或使用 destructive migration。
- [ ] **T2：簽章與 APK**。主包 versionCode=141，另一 Runner 驗證 142；檢查 package、相同固定憑證、APK sha256、Artifact URL。金鑰不進 Git/log/artifact。
- [ ] **T3：Android 16 真機**。使用者安裝 → 選多資料夾 → 批量把混合影音入池 → 排除 12 首 → 隨機連播影片音軌（無畫面）→ 解除/貪睡停止 → 檢查歷史 → 不卸載更新 141→142，來源/設定/排除/歷史保留。T3 不能由純 CI 代替。

## 3. 最小測試矩陣

1. 同資料夾混合 MP3、FLAC、M4A、MP4、MKV、JPG、PDF、無音軌影片。僅有可播放音軌的影音可加入有效池，不顯示影片畫面。
2. 3,001 個媒體候選，一鍵入池，排除其中 12 首；隨機查詢絕不抽到已排除/未加入者；操作與失敗 URI 清單不得觸發 SQL 參數門檻。
3. 移除部分重疊來源、重新掃描、重啟、SD 卡失效再恢復，已排除狀態保留，播放歷史不變。
4. 24→27 非破壞性遷移：舊 v26 索引入池一次、無重複會員；之後掃描的新曲目仍為候選。特別檢查舊系統鈴聲可被批量排除，而不是取消故障備援。
5. 失敗/不可播曲目跳過並有界重試；全池不可用時安全備援；響鈴中編輯池至少在**下一首**依新狀態生效。
6. B1/B2 鈴聲面板不崩潰；標題與歷史用 `displayTitle`，中文／日文／英文不亂碼。
7. 新功能相關測試須通過。Preview 1 最終 Run `43261061170` 是 716 項中 2 項失敗（`BedtimeReminderBaselineTest`、`TimerAlarmServiceTest`），不得宣稱整套測試全綠；兩個時間相關失敗獨立分類並留待後續修補。

## 4. 交付與狀態

- **Copilot（本機）**：須交付可核驗 B9 bundle/commit；原回報尚無 JDK/Android SDK，沒有實測或正式 v27 Schema。
- **Argon（遠端整合）**：只在取得 B9 提交後檢查與修補，更新 CI、正式 schema、build 141/142，提交可下載 APK。
- **TCL（本文件）**：記錄已證實根因／候選風險／未驗證狀態；逐關回寫證據。不要用 Agent 的實作聲稱代替 CI 或手機驗收。
- **既有分支**：main、Preview 1 與 B7/DB25 保持不動。

**目前階段：T0 接縫紀錄完成；B9 遠端程式尚不可取得；T1/T2/T3 未驗證。**

## E. 2026-11-13 B9 遠端交付與實際 T1 只讀核對（取代前文「尚未推送」的狀態）

- Copilot 已推送 [`b9-av-mixed-music-pool@5799d7ba913961d42d8bc01a59ecef4755c3249b`](https://github.com/Penguink72/AlarmClockXtreme/tree/b9-av-mixed-music-pool)。該提交之父為 `9db14a38381a9a829f018998d890778a1dae70fa`，它以 Preview 1 `e7125b3` 為祖先。相對 Preview 1 新增 2 commits，總共修改 13 個檔案；`main` 與 Preview 1 未改。
- 遠端已讀 `AlarmDatabase` v27、`MIGRATION_26_27`、`MusicLibraryDao`、`MusicLibraryRepository`、`MusicPoolDialog`、`MusicLibraryViewModel`、`AlarmService`。**T1 可證明功能路徑存在，不能代替 Kotlin/KSP 編譯或實機**。
- 新增 `music_pool_members` 表，以 URI + INCLUDED/EXCLUDED 保存成員；DAO 的一般／排除 URI 隨機查詢均 INNER JOIN 成員表，Repository 對超過 900 的排除清單採本機過濾；尚需高負載驗證。
- 遷移只讓既有 `source IN ('SAF','SAF_VIDEO')` 進池，既有 `MEDIA_STORE` 保留候選狀態。與早先「全索引保留入池」設計不同，**應依最新提交和使用者不讓系統鈴聲混入的要求為準**。新增候選不自動 INCLUDED。
- `MusicPoolDialog` 有搜尋、ALL/INCLUDED/EXCLUDED、全選可見結果及批量加入／排除；`MusicLibraryViewModel.visiblePoolTracks()` 搜尋歌名、檔名、metadata、來源名稱。但目前 **沒有獨立來源 filter 控件**，按來源全選需透過搜尋名稱，可能出現名稱相近資料夾被一起選取，Argon 應補明確的來源範圍或驗證可安全執行。不要把「搜尋來源名稱」宣稱為「精確選取指定資料夾」。
- `AlarmService.createMedia3Playback()` 在音樂庫隨機播放時使用 `DefaultTrackSelector(...).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true)`；保留音訊、無影片畫面。實際 MP4/MKV 可播性、未開始播放跳過與手機編解碼需 T3。
- **T1 已確認的編譯阻斷**：B9 的 `MusicLibraryDialog.kt` 第 71～88 行第二個 `OutlinedButton` 誤插在第一個的參數列表，Kotlin 括號結構錯誤。GPT 已在獨立修補分支 [`fix/b9-pool-dialog-compile-20261113@03d9e596ac1d67c0cbd2779bc8f99377d68272c9`](https://github.com/Penguink72/AlarmClockXtreme/commit/03d9e596ac1d67c0cbd2779bc8f99377d68272c9) 修正；此分支祖先包含完整 B9 兩筆提交，沒有改動原 B9 或 Preview 1。**Argon 請以該分支作為 B9 源，避免重犯已知編譯錯誤；仍需實際 Gradle 驗證。**
- **待重點實測／可能需要修補**：音樂 `onReady` 目前在 `Player.STATE_READY` 就設 `playbackStarted=true` 並取消 watchdog，`recordStarted` 則等 `onIsPlayingChanged(true)`；如果已 READY 卻被播放抑制而未真正出聲，啟動逾時未必能跳過。Argon 應確認 watchdog 是以真正開始播放而非僅 READY 為依據，避免鬧鐘靜音（此為程式邏輯風險，非已證實的手機故障）。
- **仍未完成**：正式 KSP 27.json、Room 24→27 測試、Gradle、語言 PR #1 合併與 I1 翻譯、141/142 CI 和 Android 16 實機。
