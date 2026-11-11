# G1｜3,000 首整批音樂庫、逐首重新抽選及簡易歷史

**T0 現行工作單／未修改 Kotlin、UI、資料庫**。2026-11-11。前置 G0 真正可下載 Debug APK。以 [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) 為準；本頁取代舊 G1 的 20 首／預洗牌計畫。

## 操作目標

一次授權→自動索引手機和 microSD 音訊，必要時指定資料夾含子資料夾補掃→顯示已找到 N 首（N≥3,000 的情境可用）→鬧鐘開啟新模式→每首自然播完重新獨立抽取→顯示當前曲名，真正開播追加時間／順序／歌名→解除後按日期查看。不逐首勾選，不記完成／中斷／失敗。

## 架構接線

1. MusicLibraryIndexer：非同步查 MediaStore 有權存取的全部可索引音樂，合併 SAF 已授權資料夾的遞迴結果；去重 URI，遇到權限或來源錯誤需提示；允許取消／刷新。
2. MusicLibraryStore：App 私有輕量索引，記每首的 URI、titleOrFilename、source；支援至少 3,000 筆且重開可讀；只存位置，不複製音檔；**不同於舊 20 首 ringtonePool 字串**。
3. AlarmEdit 模式：選擇性開啟 RANDOM_EACH_TRACK（預設舊行為）；每鬧鐘只保存新模式設定，不存 3,000 個 URI。如果改 Alarm entity，必須做正確 Room migration；不因歷史可遺失就丟掉舊鬧鐘設定。
4. AlarmService＋Media3：新模式從索引快照取一個 URI 播**當前一首**；自然 ENDED 時檢查當前 session/generation 是否有效，再從候選以 random.nextInt(N) 抽一首，允許 A→A。不用整庫 playlist、shuffleModeEnabled 或 REPEAT_MODE_ALL，不做避免重複。每首從頭播放，不沿用舊隨機跳到中段。
5. onTrackActuallyStarted：只有 isPlaying 或等效可靠事件確認後，顯示現在的歌曲，AppendOnlyPlaybackLog 一筆 {startedAt, playOrder, titleOrFilename, uri}；同首真正重新開播另記一筆，buffering／READY／暫停恢復不重記。
6. History UI：按日期列出時間、順序、歌名；關鬧鐘後可查。優先私有 JSONL 等簡單格式，**沒有**結束時回寫、完成/中斷/錯誤欄或強制跨版備份。

## 現有來源可用處

- ui/ringtone/RingtonePickerSheet.kt：現有 OpenDocumentTree 與持續 URI 授權，可沿用。
- ui/ringtone/RingtoneFolderStore.kt：目前只查直接子檔案，需加入遞迴及 MediaStore 索引接點。
- data/model/Alarm.kt：原 ringtonePool 最多 20 首，**保留舊設定相容，不把 3,000 首存入它**。
- ui/alarmedit/AlarmEditSoundSections.kt：新增模式開關／索引入口。
- service/AlarmService.kt：原本 pool.random 一首，startMedia3Tone 單曲循環／播完解除；新模式必須與舊模式明確分流，保留音量、watchdog、fallback。
- service/AlarmPlaybackPlayer.kt：只做必要的播放控制／回呼接線，不重寫整個播放器。
- data/local/AlarmDatabase.kt：Room v24；需要修改 entity 時才移轉，不為歷史多狀態模型做新表。

## 逐階段實作與驗收

| 階段 | 程式工作 | 自動化／人工驗收 |
|---|---|---|
| G1-01 | MediaStore 主索引＋SAF 指定目錄遞迴補掃 | 合成 0／1／3／3,000／3,001 筆、巢狀資料夾、重複 URI、權限失效；手機實際授權可查 |
| G1-02 | 私有索引保存＋入庫數顯示＋每鬧鐘模式開關 | 關 App 重開後 3,000 筆仍在；未開新模式的鬧鐘行為不變 |
| G1-03 | 每首播完重新隨機抽，僅播放當前一首 | N=0/1/3000、A→A 重複合法；幾首短 WAV/MP3 自然播完會切下一首 |
| G1-04 | 歌名顯示＋只追加播放歷史 | 真播才各記一筆、相同歌可重複記、READY 與暫停恢復不重複、日期順序可查 |
| G1-05 | 停止與安全備援 | dismiss/snooze/preempt/auto-silence、壞 URI、SD 拔除皆不會再亂響或完全失聲 |
| G1-06 | GitHub 建置、原版回歸、手機驗收 | 有可下載 G1 Debug APK，白天鎖屏鬧鐘、3000 首來源、隨機連播、歷史驗收 |

每片完成須有修改檔案清單、Commit、實際測試命令與結果。T2 無法取代 T3 手機測試。不要用正常起床鬧鐘當初版測試環境。

## 舊設計停止排程

20 首手動多選、Media3 shuffle＋repeat all、每日輪換／避免重複、完整播放事件狀態、跨版本歷史保證、統計／串流／MP4 影片等，都列 [DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)；只有使用者未來明確需要才恢復。

**GitHub 只改 feature/playback-history-rotation，不更動 main 或上游，不自行合併或強推。**