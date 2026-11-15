# Preview 3.1｜5 秒設鬧鐘、5 秒找回歌曲、Snooze 續播與系統鈴聲排除

日期：2026-11-15
狀態：使用者現場回饋及只讀程式核對後的新需求契約；尚未實作。
既有安裝版：Preview 3 v147／v148，integraton 原始基準 integration/p3-xplayer-media-playlists@86ca79e48578a4f276a0de53990237d7f527bcd2。
研究報告：../research/2026-11-15-alarm-app-competitors-user-needs-and-methods.md
本文件在獨立 docs 分支建立，不覆寫已發布 Release、main 或既有 Preview 分支。

## 0. 使用者實際觀察與來源邊界

- 2026-11-15 真機測試：從音樂池連播隨機三首音樂已符合預期。這是成功路徑，後續不得因調整 Snooze、播放歷史而回退；上一輪懷疑音樂庫沒有接線的說法已被本次實測修正。
- 希望 Snooze 能配置「中斷後同曲同位置接著播」或「中斷後直接換不同歌曲」。
- 播放歷史沒有看到歌曲名稱，應先辨認使用者看到的是哪份紀錄，不等於歷史資料庫沒有記錄。
- 想一鍵排除手機出廠／系統鈴聲，保持自選音樂來源純淨。
- 兩個高優先任務：5 秒內設定鬧鐘時間；醒來想再聽剛才歌曲時，5 秒內直接找到並於本 App 播放，不要求開其他播放器或瀏覽原始檔案路徑。
- 使用者截圖的「Anime／最近 30 天／響鈴 3 次／平均貪睡 0.0／平均 47 秒後關閉」是某鬧鐘的**響鈴統計彈窗**，不是歌曲播放歷史；頁面底部 Today/Alarms/Timer/World/News/Settings 仍英語，並有不常用功能占主畫面。

## 1. 現有程式接縫核對（只讀，不是實作成功宣告）

- service/AlarmService.kt：randomMusicEnabled=true 進 startMusicSession，從 MusicLibraryRepository.loadPlaylistSnapshotForAlarm 取得曲目與策略，曲目結束會接播；false 進傳統 ringtoneUri/ringtonePool，Tone Player.REPEAT_MODE_ONE 只重複該鈴聲。實測確認音樂 Session 的三首連播可用。
- service/AlarmService.kt：onStarted 在真實 isPlaying 時寫 MusicPlaybackState.title，非同步執行 MusicLibraryRepository.recordStarted(fireId,startedAt,track)；失敗只 Log.e。不能只以畫面沒有歌曲名稱斷言 DB 無記錄。
- data/local/PlaybackHistoryDao.kt：表 playback_history 現有 fireId、startedAt、title、uri、playOrder；ui/ringtone/MusicLibraryDialog.kt 右上角可以開播放歷史，現有介面只有日期和歌曲純文字行，**沒有行內播放鍵**。AlarmList 的 Anime 統計與它不同。
- service/AlarmService.kt::snoozeAlarm() 在 stopAlarmPlayback() 後重排 Snooze。下一次重建 AlarmMusicSession，現有程式未保存 URI、playlistItemId、播放位置或短暫貪睡鏈識別。因此**兩種新的續播語義均尚未完整實作**。
- ui/ringtone/MusicAutoPoolPolicy.kt 已讀取 MediaStore flags 與 ringtones/notifications/alarms 路徑作初步系統音分類，主要適用於合格候選／推薦範圍；尚無用戶要求的「所有響鈴來源一鍵套用排除＋復原」一致動作。
- ui/navigation/AppNavigation.kt 直接硬編碼六個英文底部分頁文字；翻譯資源英文約 2,327 鍵、繁中與日文各約 529 鍵；這是造成語系混雜的主要來源，不能只補核心 strings 就宣稱全 App 完成翻譯。
- 以上為已發佈 SHA 程式檢查。每個假設需以真機、Room 實際內容與回歸測試確認。

## 2. Snooze：兩種互斥的每鬧鐘設定

新增枚舉 SnoozeMusicResumeMode，和原來的播放策略 RANDOM_WITH_REPEATS / SHUFFLE_WITHOUT_REPEATS / PLAYLIST_ORDER **正交**。

### 2.1 RESUME_INTERRUPTED：同一首從中斷點續播

按 Snooze 時，在 stopAlarmPlayback() **之前**記下 alarmId、snoozeChainId、目前 trackUri、playlistItemId、positionMs、source identity、已儲存時間。Snooze 到期重新建立安全的播放 Session，先確認權限、合格狀態和是否能 seek，再用 Media3 Player.seekTo(positionMs) 續播同一首。恢復的是曲內位置，不是從 0:00 重來，也不應誤標新一首。若容器（包括部份 FLV）不能 seek，明確顯示「無法精確續播」並從原曲 0:00 或下首安全回退；fallback 路徑要可追溯。

不能把暫停的 ExoPlayer 直接一直留在記憶體等待數分鐘 Snooze；進程被殺後仍應可恢復，需小型資料持久化與 timeout。到期後保留原策略的後續選曲進度／語義，避免順序模式永遠從第一首重來。二次 Snooze 更新 checkpoint；Dismiss 清除 checkpoint，且一個鬧鐘不能恢復另一個鬧鐘的歌曲。

### 2.2 NEXT_DIFFERENT：中斷後改播另一首

Snooze 到期時創建新 Session，第一次選曲從**其他 URI**選取。若可用 URI 至少兩首，第一首不得等於中斷前的 trackUri；只剩一首則安全播放唯一一首，絕不無聲。清單中重複了相同 URI 的不同 itemId 亦視為同一音源而跳過；有序清單從下一個不同 URI 開始，洗牌模式避免打破輪次的去重意義。第一首後回到既定策略。

重點：這個單次 Snooze 限制**不改變**平時 RANDOM_WITH_REPEATS 容許 A→A 的決定。對新建立音樂鬧鐘建議預設 NEXT_DIFFERENT；對既有鬧鐘須明確定義遷移預設及 UI 顯示，不能隱性改變舊行為。

### 2.3 驗收

S1. A 播至 01:23 Snooze → Resume → 約 01:23 起播（在裝置與容器允許的 seeking 精度下）；不是 00:00。
S2. A/B/C 三首且 NEXT_DIFFERENT，A 被 Snooze 打斷 → 首曲 B 或 C；只有 A 一首仍響鈴。
S3. A→A 的日常隨機仍可發生，但 Snooze NEXT_DIFFERENT 一次性不允許 A→A（多首時）。
S4. 第二次 Snooze、Doze、前景服務回收、解碼器不支援 seek、SD 卡拔除、SAF 授權失效、手動 Dismiss／換鬧鐘均有確定結果且無 ghost audio。

### 2.4 程式拆分

- Room v28 已發版，不改 28.json；後續以 v29 非破壞性 Migration 保存 Snooze 策略，另用 DataStore 或小型表保存短期 resume checkpoint。
- AlarmService：capture position 在 stop 之前，恢復時 verify+seek，不得再次誤用舊 Session callback。
- AlarmMusicSession：加入「本次 Snooze 首曲排除 URI」及恢復中斷曲位置的小契約，不重寫通用三策略。

## 3. 播放歷史＝可立即再聽的歌曲入口

### 3.1 三份資料和入口不可再混名

A. Alarm statistics：「最近30天／響了幾次／平均貪睡／平均幾秒解除」。
B. Alarm music playback history：本次 fireId 實際播放的曲目 URI、歌名、artist、起始時間、排序、是否貪睡打斷／續播／因錯誤跳曲／安全備援。
C. User replay history：使用者主動點擊歌曲重新播放，不代表鬧鐘又響一次。

目前 B 在 MusicLibraryDialog 右上角、只有文字與日期。**使用者想要在 A 的彈窗看到 B，或從 A 一步進 B，是合理的交互需求，但 A 本身不能充當歌曲歷史。**

### 3.2 直接回播 UX

- 解鈴後 AlarmList 顯示小型、可收合卡片：「剛剛播放：{真實歌名／藝人}　[播放] [查看全部]」；直接按播放起用本 App Media3 音訊播放器，不開系統檔案管理器和外部播放器。
- 點「查看全部」→ 最近 1 次鬧鐘的 A/B/C 清單（按時序，含起播時刻）。每首一個 [播放]，可收藏／加入清單／查看來源。若有影片只播放音軌。
- 支援今天、昨天、7 天時間範圍及名稱搜尋；無法解碼／檔案不在／SAF 授權丟失時顯示清楚原因與修復入口，不只顯示 URI；標題缺失採 metadataTitle→track.title→fileName→未知歌曲，原始 URI 只在詳情。
- Manual replay 與 AlarmService 音訊所有權分離，鬧鐘優先級最高；正在響鈴時不允許手動播放器搶 Audio Focus。若要離頁後繼續聽，應用 MediaSessionService 加 mini player，明確釐清現有 PreviewController 關閉 Dialog 後 stop 的限制。
- 建議播放事件包含 STARTED、SNOOZE_INTERRUPTED、RESUMED、ENDED、FALLBACK、USER_REPLAY_START；舊 playback_history 的資料不可消失。播放歷史寫入失敗要有診斷或可回看錯誤，不可只在 Log.e。

### 3.3 驗收

H1. 真機連播 A→B→C 解鈴，新歷史顯示 A/B/C 真實歌名與依序起播時間。
H2. 不超過 2 次點按就能從首頁「最近播放」起播 B；對最近一首只要 1 次點按，目標 5 秒內開始發聲。
H3. 重新聽 B 不會增加鬧鐘響鈴次數、也不污染 Alarm Stats；新的鬧鐘到時會中斷手動聽歌。
H4. 原先只有統計彈窗的 Anime 提供「查看此鬧鐘播放歌曲」入口，但不硬塞所有歷史在統計面板。

## 4. 一鍵排除原廠系統鈴聲（不依賴通用固定路徑）

UI 顯示：「排除手機系統鈴聲」（預設開啟，作為**隨機音樂候選集合**的來源規則）；按「檢視」展示可能被排除數，按「套用排除」一次將高信心項目設 EXCLUDED，並提供可撤銷記錄。不要刪除手機內任何媒體。

- 第 1 級：MediaStore.Audio flags：IS_ALARM、IS_RINGTONE、IS_NOTIFICATION；結合 is_music、來源及補助 metadata。
- 第 2 級：RingtoneManager(TYPE_ALARM、TYPE_NOTIFICATION、TYPE_RINGTONE) 可見 URI 列表與當前預設聲音 URI。Android RingtoneManager.getDefaultUri(TYPE_ALARM) 是**象徵 URI**，不是固定檔案路徑。
- 第 3 級：MediaStore RELATIVE_PATH 的 Ringtones/Notifications/Alarms/UI 等片段。常見 ROM 系統音也可能位於 /system/media/audio、/product/media/audio、/vendor/...，但 Android scoped storage 無法保證每台有同一個可讀絕對路徑，RELATIVE_PATH 只能組織不能建 raw path。
- 第 4 級：檔名與時長只能輔助，短歌不自動視為系統音；自行匯入 Alarms 的長 MP3 允許加入例外／人工保留。分類記 SYSTEM_HIGH、SYSTEM_POSSIBLE、USER_AUDIO、UNKNOWN 與證據來源，低信心者不偷偷排除。
- 使用者曾明確設 INCLUDED 的項目若按「批量排除」才變 EXCLUDED；先展示受影響歌曲數與撤銷方式，不能因背景重新掃描自動重設。
- 排除規則要真正套到 runtime PlaylistSnapshot（GLOBAL INCLUDED ∩ playlist ∩ filters ∩ noSystemTones），不能只把 UI 列表隱藏；系統預設聲音作為播放失敗的安全備援仍保留。

### 驗收

X1. 高信心原廠系統音不會混進全域隨機音樂；自建歌曲不被誤刪。
X2. 下載放在 Alarms/ 的歌能查看排除原因並選擇保留。
X3. 一鍵排除、批次數量、可撤銷；重掃不偷恢復；下一次響鈴立即生效。
X4. 只在使用者點「系統預設鈴聲」當來源時可正常用系統原音；不因排除音樂庫裡的系統音就破壞安全備援。

## 5. 兩個「5 秒」核心使用者旅程（可量測）

**A. 設好時間**：從 App 鬧鐘首頁可操作時開始，點固定 [＋] → 數字鍵直接輸入 HH:MM → [完成] → 首頁顯示下次真實排程成功（包含日期、時間、重複模式摘要）。所有新鬧鐘共用使用者已設定的預設「音樂來源／清單／策略／Snooze」，不必每次再設。常用鬧鐘可一點啟用；其他挑戰、天氣等不阻塞。
- 目標：warm-start P50 ≤5s、P90 ≤8s；確認由 AlarmManager 真正排程，不只畫面存了時間。首次安裝的權限流程另測，不能算「已排程」。
- 緊急防呆：跨午夜、時間已過、誤輸 12/24h、系統鬧鐘權限不可用、鎖屏／Doze 要給明確結果；不能只有成功 Toast。

**B. 再聽剛才的歌**：從解除鬧鐘的首頁開始，直接點「剛剛播放／播放」→ app 自己發聲；次近歌曲從「查看最近播放」選中曲並播放。從歷史看影片音軌亦同。
- 目標：最近一首 1 tap，最近三首 ≤2 taps；P50 ≤5s、P90 ≤8s，到達 Media3 的 isPlaying=true／音訊實際起播，不以顯示歌名或 URI 當成功。
- 較久以前的歌曲可搜尋，3,001 首庫查詢與索引分離；首次熱快取搜尋顯示目標 P90 ≤300ms，是新設計目標未實測。

## 6. UI、語系與功能可見性

- 預設「精簡模式」首頁：鬧鐘列表、＋ 快速設定、下一個鬧鐘、剛剛播放。媒體庫、播放清單、最近播放是可直接到達的主功能；新聞／天氣／計時器／世界時鐘／不常用挑戰放「更多功能」，不刪原能力、不改既有排程。
- 完整模式保留舊工具；自訂模式選可見功能與排序，且提供恢復預設。
- 介面語系：純繁中／純英文／純日文，或主要＋次要語言對照（其他兩種語言任選一種），顯示策略「僅標籤」「標籤與說明」「點擊展開」。不要把兩語拼在同一個 strings.xml 值，使用可測的 LocalizedText Key resolver；所有 5 秒核心頁面須完全翻譯且無硬編碼英語。剩餘 App 逐批擴張語系覆蓋測試，不誤稱 100%。
- 清醒前 UI 避免誤按解除/貪睡；至少 48dp 觸控目標，可選滑動／按住解除。截圖中的「Anime 30 天統計」應縮為進階統計或二級入口，不占鬧鐘首頁關鍵操作空間。

## 7. 施工拆分與停止條件

P3.1-0：凍結已發布 Preview3 v147 SHA，建獨立 fix/p31-five-second-alarm-replay-and-snooze 分支；先加 UI/DB/Service 診斷，不能破壞三首隨機連播成功路徑。
P3.1-1（Android Agent）：AlarmService 捕捉 Snooze 位置、恢復後 seek／換下一首；Room v29 Migration＋正式 schema；audio event history；系統音分類與 runtime 排除；失效與回退可解釋。
P3.1-2（Copilot UI）：快設數字時間；歷史與一鍵播放及 mini-player；Snooze 兩選項；原廠音批量排除與復原；精簡／完整／自訂 UI；語系 Key＋雙語對照。只接真 Repository、不假成功。
P3.1-3（驗證）：實機 Snooze 1:23→1:23、A→B；歷史 A/B/C→點播 B；一鍵排除；5秒測試；Android 16 Doze/鎖屏/權限/多鬧鐘；舊 749 項單元測試並將既有 Bedtime 基準失敗單獨報告。
新安裝包 versionCode 高於已發布 v148；固定簽章和 package 保留。Room 28.json 不覆寫；嚴格建置、資料遷移與真機響鈴未過不得宣稱穩定。
本文件不包含 Android 實作或已通過測試的承諾。

## 8. 官方技術參考

- Media3 ExoPlayer：play/pause/seekTo https://developer.android.com/media/media3/exoplayer/hello-world
- Media3 Player.seekTo：https://developer.android.com/reference/androidx/media3/common/Player
- MediaStore Audio flags：https://developer.android.com/reference/android/provider/MediaStore.Audio.AudioColumns
- MediaStore RELATIVE_PATH 限制：https://developer.android.com/reference/android/provider/MediaStore.MediaColumns
- RingtoneManager default symbolic URI：https://developer.android.com/reference/android/media/RingtoneManager
- Android exact alarms：https://developer.android.com/develop/background-work/services/alarms
- Compose 最小觸控尺寸：https://developer.android.com/develop/ui/compose/accessibility/api-defaults
