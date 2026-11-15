# 2026-11-15｜Android 鬧鐘 App 競品、真實使用者需求、方法與工具研究

日期：2026-11-15（Asia/Taipei）
狀態：2026-11-15 公開來源研究＋Preview3 v147 原始碼只讀檢查；不是 APK 測試結果。
產品需求主文件：../tcl/preview3.1-5second-alarm-replay-and-snooze-contract-20261115.md

## 0. 研究目的與證據分級

問題：Google、Samsung 等高下載量時鐘 App 如何做到快速設時間與可靠起床？Alarmy、Sleep as Android、Sleep Cycle 這類主題 App 增加哪些功能？社群真實抱怨與使用者沒有提出的需求為何？對本案「5 秒設好鬧鐘，醒後 5 秒再聽歌曲」應採哪些前端／後端設計與工程驗證方法？

分類：
- P=官方產品功能／Android 開發文件，可引用為官方宣稱或 API 規範。
- U=Google Play、Reddit 使用者原話或評論，說明有人確實遇到／希望某能力，**不是問題普遍率、技術缺陷已獲證實或所有用戶共識**。
- I=基於證據和本案實測所提出的設計推論，尚須驗收。
- N=非公開內部方法／工具，不臆測廠商內部軟體架構、演算法或實際 MAU。

Google Play 的「下載 5B+／1B+」是**累積安裝級距**，不是同時活躍用戶數、月活或各國市占率；系統預裝也會影響解讀。不以此宣稱精確排名。

## 1. 高累積下載／具代表性 App 功能對照

| App | Google Play 顯示下載級距 | 官方公開能力（P） | 對本案可提取的設計方法（I） | 來源 |
|---|---:|---|---|---|
| Samsung Clock | 5B+ | 日期／每週鬧鐘、一次跳過、Snooze、世界時鐘、城市天氣、計時器、手機音訊；官方支援快速新增 | 時間是第一級操作，週期及跳過日放次級；原功能多但入口有層次；不意味 Samsung Clock 每個功能都要做 | https://play.google.com/store/apps/details?id=com.sec.android.app.clockpackage ；https://www.samsung.com/us/support/answer/ANS10002623/ |
| Google Clock | 1B+ | Alarm、Timer、Stopwatch、World Clock、bedtime、Wear OS；支援本地聲音及部分串流來源 | 極短的「新增→選時間→OK」主路徑；次級音樂來源可與聲音編輯靠近；權限失效原因在 UI 可見 | https://play.google.com/store/apps/details?id=com.google.android.deskclock ；https://support.google.com/clock/answer/2840926 |
| Alarmy | 50M+ | 多種解鈴任務：拍照、條碼／QR、數學、記憶、搖晃／深蹲；Wake Up Check、快速鬧鐘、早晨紀錄、睡眠聲、睡眠追蹤 | 長期需求不是只有設定時間，還要避免誤解除與喚醒失敗，但在本案應放進階而非預設擋住 5 秒路徑 | https://play.google.com/store/apps/details?id=droom.sleepIfUCan |
| Alarm Clock Xtreme（AVG） | 50M+ | 快速一次性鬧鐘、醒前跳過、漸增音量、大貪睡按鈕、縮短貪睡間隔、數學等解鎖；宣稱隨機歌曲鬧鈴 | 低認知成本的快捷預設 + 可選進階喚醒規則；維持明確區別 Snooze 與 Dismiss | https://play.google.com/store/apps/details?id=com.alarmclock.xtreme.free |
| Sleep Cycle | 10M+ | 睡眠分析、智慧起床時段、柔和喚醒 | 若未來需要智慧起床，可另作可關閉模組；不在本輪引入睡眠資料蒐集或主畫面複雜度 | https://play.google.com/store/apps/details?id=com.northcube.sleepcycle |
| Sleep as Android | 10M+ | 可選睡眠追蹤（聲納／聲音辨識）、Spotify 歌單、漸亮、CAPTCHA、貪睡上限、Wearables／Health Connect／IFTTT／MQTT／Tasker／Webhooks／備份 | 可觀察其模組化整合與深度使用者配置；但增加整合不等於符合本案 5 秒任務 | https://play.google.com/store/apps/details?id=com.urbandroid.sleep |
| XPlayer（影音播放器，非鬧鐘下載榜） | 100M+ | 自動辨識裝置及 SD 卡影音、資料夾、命名播放清單、音訊背景播放、影片音軌 | 本案的歌曲索引／歌單／歷史直接回放設計參照；不複製其品牌或閉源解碼器 | https://play.google.com/store/apps/details?id=video.player.videoplayer |

高安裝量時鐘不一定是「最強功能」，更不代表用戶都打開所有功能；可以將「大量低摩擦日常任務」與「少數深度控制任務」分別做入口。用戶明示的 5 秒任務應比添加非必要服務優先。

## 2. 直接使用者聲音（U）：不做無證據普遍性斷言

### 2.1 誤觸、早上不能處理複雜操作

- Reddit androidapps 2025-10-25：使用者希望巨大 Snooze/Dismiss 按鍵、逐漸增大音量、無廣告、權限不 intrusive、App 體積較小；認為更新後的 Google Clock 容易誤解除。此為個人需求，無法單憑貼文外推普遍率。https://www.reddit.com/r/androidapps/comments/1ofsipk/decent_alarm_clock_app/
- Reddit GooglePixel 2025-08-28：多位回覆認為新介面按鍵改動容易誤按 Snooze 或 Stop，也有人表示新方式比較容易用；**同一 UX 改版可能造成兩派相反反應**，因此本案應 A/B 低保真而非直接假設滑動一定較好。https://www.reddit.com/r/GooglePixel/comments/1n2kjkn/im_not_a_fan_of_the_new_alarm_screen/
- Reddit GooglePixel 2026-05-04：有人不想用語音處理 Snooze，抱怨找不到按鈕且不想多步操作。https://www.reddit.com/r/GooglePixel/comments/1t3krrh/change_to_snooze_alarm/

### 2.2 鬧鐘可靠性遠比功能表多更重要

- Reddit GooglePixel 2026-10-24：用戶看到「鬧鐘已自動靜音」的紀錄而誤判未響；社群對是否真正響鈴意見不同。需求是**可回查事件鏈和自動靜音原因**，不是直接認定平台 Bug。https://www.reddit.com/r/GooglePixel/comments/1xeebju/pixel_alarms_didnt_make_a_sound_this_morning_got/
- Google Play Samsung Clock 2026-08 使用者評論：有人回報音量變動導致錯過鬧鐘、群組操作可能連動其他鬧鐘；不能當作已證實的全域故障，但提醒測試音量與單一鬧鐘隔離。https://play.google.com/store/apps/details?id=com.sec.android.app.clockpackage
- Google 官方 Clock Help 明列 DND、音量過低、電池省電、通知權限、靜音、短時間太接近的雙鬧鐘等診斷情境；應將「下一次是否真的會響」的 readiness 濃縮為一個可點開的狀態。https://support.google.com/clock/answer/2840926

### 2.3 播放清單「有來源」不等於「真的遵守使用者意圖」

- Reddit androidapps 2022-03-04：有人想讓鬧鐘依序播放冥想音檔→口述提醒→隨機音樂，卻遇到串流服務將播放清單洗牌，導致固定順序被破壞；這是本案保留 PLAYLIST_ORDER 的根據。https://www.reddit.com/r/androidapps/comments/t6bpx4
- Reddit androidapps 2026-08-19：有人希望每個早晨從選好的幾首歌曲隨機抽一首。https://www.reddit.com/r/androidapps/comments/1vsqwho/looking_for_an_alarm_that_randomly_plays_a/
- Reddit androidapps 2026-08-30：有人希望每日不同音樂、沒有廣告；討論指出部分方案依賴串流訂閱，且若有廣告會破壞醒來體驗。https://www.reddit.com/r/androidapps/comments/1w2fzh4/is_there_any_free_alarm_app_with_no_ads_that_can/
- Reddit androidapps 2020-05-27 某 Shuffle Alarm 的作者提出響鈴時「下一首」「丟棄不想聽的歌」以及睡前 Play Now 測音量；這是創作者公開說明，**不是已驗證每台設備皆可靠**。本案可將「下一首／不再推薦」列為後續低複雜度功能。https://www.reddit.com/r/androidapps/comments/gratpl

### 2.4 工時不固定／多個關聯鬧鐘

- Reddit androidapps 2024-05-21：使用者希望只設定上班出門時間 H，就自動推算 H−75、H−70、H−65、H−60 等成組提醒，避免一週重設多次。此為可後續加入的「鬧鐘群組相對時差模板」，不應先於主要 5 秒快設任務。https://www.reddit.com/r/androidapps/comments/1cxbz2d

### 2.5 串流來源與鎖屏風險

- Sleep as Android 官方 Spotify 文檔承認其集成依賴非官方 workaround，鎖屏時可能無法切換播放清單而沿用上一首；說明不能把「有 Spotify 選項」等同於確定可控音源，尤其不該以它取代本地 MediaStore/SAF 作唯一安全來源。https://www.sleep.urbandroid.org/docs/services/spotify.html
- Reddit SleepAsAndroid 2022-09-27 同樣有「設定歌單但響同一首或回退備援」的個人回報。https://www.reddit.com/r/SleepAsAndroid/comments/xp8mya

## 3. 使用者未提但值得提案的功能（按本案效益排序）

| 優先 | 需求候選 | 本案 UX / Backend 對策 | 證據性質 |
|---|---|---|---|
| P0 | 正確時間響鈴、沒有權限或靜音時有可見原因 | AlarmManager exact-alarm readiness、通知／電池／DND／音量檢查；僅在有風險時給提示，不塞首頁設定長表 | P Google 官方 Android Help/Developer |
| P0 | 醒來「現在到底播哪首」與馬上再聽 | 真實播放歷史時間線、上次音樂一鍵播、音樂資訊快捷卡 | I 作者 5 秒需求 + v147 缺口 |
| P0 | 貪睡返回策略可預期 | RESUME_INTERRUPTED / NEXT_DIFFERENT，進度＋檔案不可 seek 的回退，禁止幽靈播放 | I 作者新需求 |
| P0 | 分清「原廠系統音」「自行放進 Alarms 的歌」 | MediaStore flags + ringtone URI + 低信心 path，附檢查／例外和 Undo；不刪手機媒體 | P Android 官方 API + I |
| P1 | 大貪睡鍵、避免誤按解除 | 48dp+ 觸控範圍、較不容易誤按的解除操作；用真機睡醒情境測試 | U Reddit + P Compose Accessibility |
| P1 | 睡前可直接聽明早會響的音樂／測音量 | 一鍵「試播明天鬧鐘」預演真正選曲來源；不正式排程、不寫入鬧鐘歷史 | U Reddit Shuffle Alarm + I |
| P1 | 鬧鐘響鈴時跳過不喜歡的歌 | [下一首] 和 [以後不要播這首]，僅當已在播放時顯示，不把全部操作擠主介面 | U 2020 Shuffle Alarm |
| P1 | 音量漸強但不會被系統音量不明變更影響 | 顯示預測音量、特定音源增益測試、測通話與藍牙／耳機路由 | P Alarmy/Alarm Clock Xtreme + U Samsung |
| P1 | 每週臨時跳過／在假期暫停不刪鬧鐘 | 單次跳過／下個響鈴暫停／跳過特定日期 | P Samsung/Google Clock |
| P2 | 一次設定 H 連動多個相對時間鬧鐘 | 「開始上班時間」模板，複製鬧鐘群組；不進 P3.1 最小範圍 | U 2024 Reddit |
| P2 | 多裝置／穿戴裝置同步 | 先調查確定來源和多裝置身份，再做可選整合；不可犧牲本機無網響鈴 | P Google/Sleep as Android |
| P3 | 睡眠追蹤、聲納、人工智慧晨醒評估 | 非作者現階段目的，暫緩；不引入隱私／電量成本 | P Sleep Cycle / Sleep as Android |

總結：新發現不等於必須全部做。本案最高價值的差異化是「醒來可聽高自由度個人音樂、任意 Snooze 策略、醒後一鍵找歌、仍可靠準點響」。

## 4. 前端設計方法（可公開採用，而非宣稱各廠商真正在用）

1. **Task-first / Jobs-to-be-done**：明確任務「想 5 秒設時間」「醒後 5 秒重聽」「Snooze 要能預期」；每頁一個主要 CTA，其餘使用者能選簡潔／完整／自訂。
2. **Journey Map + State Table**：AlarmList→TimeEntry→Scheduled；AlarmFiring→Snooze→Resume or Next；AlarmDismiss→RecentlyPlayed→ManualMediaSession；每條流程列按鍵數、需要的權限、錯誤與回退。
3. **Progressive Disclosure**：所有使用者只看時間／來源／貪睡三件事；歌單歌手資料夾與安全備援放進階。
4. **5 秒計時可用性測試**：自己及不同熟悉程度的人，量測 warm-start P50/P90，記首次點擊／完成排程／實際出聲；不要以操作動畫開始當成功。
5. **Heuristic Evaluation、Accessibility Audit**：醒來眼睛模糊、手指亂按，Snooze、Dismiss 大按鈕、TalkBack、字體放大、繁中日英雙語時不遮住 CTA；同一人連續重測前後版本。
6. **Counterfactual / Failure-mode test**：檔案剛刪除、SD 卡斷線、被 Android 回收、剛系統更新、沒有音訊權限、分頁切換競態等測試。可靠性優先於功能數量。

## 5. 後端設計方法、API 與工具（本案應直接採用）

| 目的 | 技術／工具 | 具體可驗收輸出 | 官方依據 |
|---|---|---|---|
| 準點響鈴 | Android AlarmManager.setAlarmClock／精確鬧鐘權限檢查 | 真實下次時刻、未授權提示、重啟／Doze 測試 | https://developer.android.com/develop/background-work/services/alarms |
| 媒體與分類 | MediaStore.Audio/Video、IS_ALARM/IS_RINGTONE/IS_NOTIFICATION、RingtoneManager、SAF DocumentsContract | 高信心出廠鈴聲候選、例外與結果清單；資料夾分類用 RELATIVE_PATH，不直接構建 raw path | https://developer.android.com/reference/android/provider/MediaStore.Audio.AudioColumns ; https://developer.android.com/reference/android/provider/MediaStore.MediaColumns ; https://developer.android.com/reference/android/media/RingtoneManager |
| 音訊播放／續播 | AndroidX Media3 ExoPlayer + MediaSessionService / Controller + AudioFocus | positionMs、seekTo、失敗回退、音樂列表直接重播、不搶鬧鐘聲音 | https://developer.android.com/media/media3/exoplayer/hello-world ; https://developer.android.com/reference/androidx/media3/common/Player |
| 本地資料與追蹤 | Room v29/KSP 正式 schema、DataStore 持久的 Snooze checkpoint、PlaybackHistory event model | v28→v29 非破壞性遷移、與舊鬧鐘分離、安全恢復 | https://developer.android.com/training/data-storage/room |
| Compose UI | Kotlin / Jetpack Compose / Material3，單一 Navigation 主頁、LazyColumn、Semantics/testTag | 5 秒路徑、無重複操作入口、≥48dp 觸控 | https://developer.android.com/develop/ui/compose/accessibility/api-defaults |
| 結構化驗證 | JUnit + Robolectric + Compose UI test + Instrumented tests + GitHub Actions | 選曲、URI 例外、資料庫遷移、歷史資料及一鍵排除測試 | https://developer.android.com/develop/ui/compose/testing/semantics |
| 端到端效能 | AndroidX Macrobenchmark／UI Automator／Perfetto／Android Studio Profiler／Baseline Profiles | 真機 P50/P90 開啟至存檔與點播放至真的 isPlaying、3,001 首清單捲動無卡頓 | https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview |
| 語系品質 | Android strings.xml、Resource XML 靜態檢查＋硬編碼字串掃描＋三語截圖快照 + 可配置 LocalizedText | 核心頁沒有無意的英文 fallback；雙語主／副可獨立讀取，不把兩語塞進同一 string 值 | https://developer.android.com/guide/topics/resources/localization |
| 安全驗收 | Android 16 真機、Doze／鎖屏／通知權限／音量／耳機／藍牙／多重鬧鐘測試矩陣 | 任何失敗可從 incident events 還原原因，鬧鐘不靜音 | https://support.google.com/clock/answer/2840926 |

**重要來源限制**：Samsung/Google/Alarmy/Sleep Cycle/Sleep as Android/XPlayer 沒有在上述 Play 商店頁面揭露完整內部採用的 UI 設計軟體、Kotlin/Compose 實作、CI、分析工具或演算法。我們只能確認它們的公開功能與明示整合。上表是**對本案推薦的工程方法／工具**，不能寫成「這些公司內部都使用它們」。

## 6. 本案可公開觀察到的競品工具與整合

- Google Clock：Android Clock、Wear OS 穿戴資訊方塊、Android 平台時鐘能力；官方 Clock Help 表示可在部分環境選 YouTube Music、Spotify、Pandora、Calm 來源，依地區／訂閱／授權有所限制。https://support.google.com/clock/answer/2840926
- Samsung Clock：Samsung 內建時鐘、鬧鐘排程／Snooze／世界時鐘天氣及新增日期功能；官方未揭露所有設計工具或內部基礎架構。https://play.google.com/store/apps/details?id=com.sec.android.app.clockpackage
- Alarmy：QRCode/條碼、相機、數學、記憶、搖晃／深蹲等解鈴方式；官方頁面有聲明部分功能用 Accessibility Service／Device Admin，應注意權限／信任成本。https://play.google.com/store/apps/details?id=droom.sleepIfUCan
- Sleep as Android：官方公開 Wear OS、Health Connect、Tasker、IFTTT、MQTT、Webhook、Spotify、Google Calendar 與備份整合；Spotify 連動限制有官方風險文件。https://play.google.com/store/apps/details?id=com.urbandroid.sleep ; https://www.sleep.urbandroid.org/docs/services/spotify.html
- Sleep Cycle：睡眠監測／聲音分析／智慧起床；未有必要將其睡眠資料收集架構套入本案。https://play.google.com/store/apps/details?id=com.northcube.sleepcycle
- XPlayer：支援手機／SD 卡自動媒體庫、playlist、背景播放及影片音軌；作為媒體及歌單操作樣板，而非照搬實際閉源 UI 檔案。https://play.google.com/store/apps/details?id=video.player.videoplayer

## 7. 研究結論及實作取捨

- 大量安裝不是「越多功能放首頁越好」的證據。選鬧鐘時間／解鈴／立即再聽應是顯性主操作。
- 先落地 **Snooze 續播模式、真正可播放的音樂歷史、一鍵排除系統鈴聲**，再集中優化 5 秒路徑；不先做 AI 睡眠分析或更多任務解鈴。
- 必須保留作者真機成功的「同次三首隨機連播」作回歸；研究中的所有提案要分清已發現機會、未決風險與實際已通過功能。
- 下一輪先以此文件和 P3.1 需求文件分派 Copilot（介面）／Android Agent（Session、history、Room、sound classification）不同檔案所有權，避免多人同時碰 AlarmService 和資料庫。
