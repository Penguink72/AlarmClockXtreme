# P3.1 → P2-U5：TCL 施工與驗收帳本（2026-11-16）

## 根任務
讓 AlarmClockXtreme 成為五秒內可設鬧鐘、可管理 4,935+ 首手機音樂、貪睡可可靠再響、可測試且可追查的日常 Android 鬧鐘。完成一項的定義不是「已有規劃」或「CI 綠燈」，而是 T0 需求、T1 程式、T2 機械證據、T3 真機證據分別有來源。

### 已確認基準
- U1：`integration/p31-u1-history-replay` `330f30ff2df6bbba6cc266ca3dad05464d6866ed`。v149／v150 773/773；手機已驗證升級、最近播放、首頁回播、音樂與警報互斥等。
- U1.1：PR #10 `850ff8496c3ac17cdcea9e64b2ecd4a314275041`。v153／v154 既有簽章；編輯頁試聽真機通過；播放清單全流程待驗。
- S1：PR #11 `e138aff735f1b50f83dd645f0cb21a620456249c`。v155／v156 CI 777/777；「亮屏右滑貪睡十分钟不再響」T3 尚未通過，須保持 P0。
- L1-A：PR #12 `43ec26f3fc0a0a2af7defbf2947548ce0a4abdbe`；Run `43807330592` 三個 Job 成功，782/782；91 個核心翻譯資源三語完整。舊響鈴、設定與引導尚未全部三語化。
- 此工作分支從 L1-A 建立。前述 PR 都尚未合併 `main`；本工作分支不可直接改 `main`。

## 施工序與驗收證據
| ID | 優先級 | 目標 | T1/T2/T3 判準 |
| --- | --- | --- | --- |
| S1-R | P0 | 對既有貪睡診斷提供可見／可匯出的事件時間線 | 事件準時/遲到、Service、實際 Media3 播放、螢幕狀態可以關聯，T3 仍須實際亮屏 10 分鐘 |
| B2 | P0 | 編輯頁「立即試響」與「排程測試」 | 立即試響不宣稱驗證 AlarmManager；排程測試必須經正式鬧鐘 Receiver→Service→音訊路徑並避免污染原鬧鐘及播放歷史 |
| U1.2 | P1 | 4,935 首索引下播放清單操作閉環 | 不強制重新掃描、搜尋演出者、批量加歌、套用並讀回、可測量查詢與保存 |
| L1-B | P1 | 實際響鈴、設定、新手引導三語化 | 各指定 Kotlin UI 引用零缺漏，參數型別一致；長文字需 T3 字級驗收 |
| R1 | P1 | 新 TCL 狀態帳本 | 區分待施工、程式完成、CI 通過、真機已通過、重大阻斷 |
| U3 | P2 | 5 秒鬧鐘 | 重用既有 QuickAlarmRow 與 `createQuickAlarm`，測量從首頁到新增成功，不重建底層排程 |
| U4 | P2 | 貪睡播放兩選項 | 同曲中斷位置重播／換另一首；必須綁定每次 fireId 並防止播放歷史虛假寫入 |
| U5 | P2 | 已索引系統音效一鍵排除 | 僅顯示候選並允許確認，來源不足需標記疑似，保留手動覆寫與回復，絕不在掃描時暗中排除或重掃原庫 |

## 跨切片禁令
- 禁止把 Media3 試聽當成正式排程測試；禁止利用真實 `AlarmEvent`、播放歷史或日常鬧鐘資料偽造測試成功。
- 禁止新增 Room v29，除非技術證據證明必要並另外記錄 migration。
- 禁止因翻譯而修改外部 API、DB 欄位、診斷 wire reasonCode 或既有使用者輸入的曲名。
- 每次新 APK versionCode 必須大於已驗證的 v158，簽章與 `com.sysadmindoc.alarmclock.debug` 不變。
- 只憑 CI 通過不能宣稱亮屏 10 分鐘貪睡缺陷已修復。
- 各工程切片可先入庫與 CI；只有「有完整新功能且 CI 合格」才要求使用者實機測試。


## 2026-11-16 遠端接續切片（在 PR #13 上）
- 延伸契約：[G1/G2/G3 施工與驗收閘門](P31-G1-G2-G3-REMOTE-CONTINUATION-20261116.md)，文件提交 `fbabaeb3cc330b2d7b2d51e051d70fb351af6d05`。
- U4-BASE-A：`AlarmPlaybackPlayer.positionMs(): Long?` 已加入 `MediaPlayerAlarmPlaybackPlayer` 與 `Media3AlarmPlaybackPlayer`，非有效播放位置回傳 null；提交 `53b1cca281afabd05d9ab96ac32a634147451e11`。
- U4-BASE-B：新增 `SnoozeMusicRestoreCheckpoint` 純資料契約（alarmId、原 fireId、下一次 fireId、URI、播放毫秒、playlistId、模式、時間有效期限），驗證跨鬧鐘／舊 fireId／失效位置／過期快照不可恢復。程式 `76c8862853d451abafda59cfdeadce1f52291756`；5 個 JUnit 個別測試 `f1b013e9f9fd31cb3ec959b1d81a3aaef7597292`。
- **證據裁定：以上 U4 底座為 T1；新提交的 T2 需讀回最新對應 GitHub Actions，T3 未開始。** 雖有位置 API 與快照型別，尚未接入正式 `AlarmService` 貪睡轉移，未保存 Room、未提供前端選項，也不表示可以續播或換曲。
- 本切片沒有修改既有 Room v28、正式播放器選曲或貪睡排程。S1 原故障仍為 P0，B2-0 不得當作正式音樂鬧鐘全流程驗證。
- 下一個可執行切片：優先 G1 的實機故障再現及事件證據；G2-A 獨立 test namespace／正式流程隔離；G3 的持久化與備份先做設計／遷移驗收，不可在無 T3 前重寫播放路徑。
- U4-BASE-C：補 `AlarmPlaybackPositionTest` 2 個 JUnit cases，MockK 驗證原生播放器位置以及無效／已釋放時回 null。程式提交 `213f444f4895ca6d459166c81ddf049bbae108d3`。對應 [Actions Run 43886583035](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43886583035) 已觸發但本次讀取時未完成；因此 T2 未裁定。


## 2026-11-16 夜間接續｜G1 與 G2-A（T1 已推送，T2 依最新 HEAD CI 另驗）

### G1 冷啟動貪睡前景服務修補（P0）
- 檢查發現 `SnoozeReceiver` 使用 `startForegroundService()`，而 `AlarmService.ACTION_SNOOZE` 從 `currentAlarmId=-1` 的冷啟動路徑僅恢復計數、非同步呼叫 `snoozeAlarm()`，沒有在 Service 回呼內即時 `startForeground()`。此接縫可能於 Android 14+ 在排程前被服務啟動時限中止；**尚未證實是原始亮屏十分鐘不再響的主因**。
- `ce4053a`：在冷啟動通知貪睡同步建立前景 placeholder，記錄 `COLD_SNOOZE_COMMAND_RECEIVED`；拒絕／例外時關閉僅屬於該火次的恢復服務，不影響後來搶占的鬧鐘。
- `5f4f2b3`、`c15eab2`：將失敗回收條件抽為 `shouldStopRejectedColdSnooze`，測試拒絕、已接受、跨鬧鐘、無效 fireId 等。 `1052743` 補明確可空 ACK 判定。
- **T2 必須看包含上述所有提交的最新完整 CI**；早期 `ce4053a` 單獨跑出的編譯失敗，是後續才新增的 helper 尚不存在，不可把中間 SHA 的結果冒充最終 SHA。T3 原樣保留「手機保持亮屏、右滑貪睡 10 分鐘必須再次起聲」。

### G2-A 隔離式正式測試底座（B2-production 仍未完成）
- `3ac4be2`、`814ed51`：`ProductionTestSession` 以 UUID + `b2-test:` 專用 fireId + 最多兩次起聲／15 分鐘 TTL 隔離，不可將一般 fireId 當作測試；完整證據必須依序 `REGISTERED→RECEIVER_DELIVERED→SERVICE_ACKNOWLEDGED→AUDIO_STARTED→SNOOZE_REGISTERED→SNOOZE_DELIVERED→AUDIO_RESTARTED→DISMISSED`，禁止跳步偽造成功。
- `9661650`、`2c95dff`、`6c263d9`：原子檔案 `ProductionTestSessionStore` 寫入 `noBackupFilesDir`，不使用使用者鬧鐘的 Room、播放歷史或既有 Onboarding proof；配備跨讀回、時鐘過期、其他 Session、拒絕跳步、取消等 Robolectric 測試。
- **本次 G2-A 只有隔離的資料模型與持久化憑證；尚未有 AlarmManager→正式 Receiver→AlarmService→Media3 的測試派送、未有 1 分鐘再響及 UI**。不能把 G2-A 當成 G2 完工；不能以舊 B2-0 系統送達測試取代。
- 不在 G2 正式隔離入口就緒之前開啟測試播放或轉發外部整合。避免測試錯改真實鬧鐘、寫入歷史、啟動 Guardian/webhook、碰撞現有播放 Session。
- **G3 保持原有 U4 底座，未接入正式播放路徑**。下一工程入口依 G2-B 安全隔離設計；G1 仍需 T3 完成才能完整結案。

- G2-A 互斥補強：`63d9790` 禁止新測試覆蓋未結束／未過期的舊 Session；`815d21f` 增加取消後才允許下一 Session 的測試。此層只記錄隔離測試憑證，不排程／不發聲，G2-B/C 必須另行證明。
- 最新含 G1 + G2-A 程式提交 `815d21fa937158e433f12c4e759708de31bd15e5`；[CI Run 43901925376](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43901925376) 本次核對為待執行。舊的單獨 `ce4053a` Run 43900172550 因引用尚未提交的 helper 而編譯失敗，後續 `5f4f2b3` 已補 helper；必須等待完整 HEAD CI，不得轉述為已通過。


## 2026-11-17｜使用者授權跳過 G1 T3 阻斷，繼續 G2→G3

裁定更新：作者明示不再以實機檢驗阻擋後續寫程式；因此 **Code-Go 允許推進 G2/G3**，但保留 T3 欄位「未驗」而不是「通過」。Android OS 的背景／排程／音訊廠牌限制仍可能使已編譯程式在某手機失敗；對正式作息仍需可靠備援。既有 Room v28、debug 簽章、`main` 保持不變。

### G2 正式音樂鬧鐘端到端診斷：T1 程式接線完成
- `ProductionTestAlarmScheduler`：獨立 UUID/fireId、資料 URI 區隔的 `PendingIntent`、1 分鐘後精準觸發、一分鐘貪睡再響、取消與 TTL；不調整正常 alarm row／nextTriggerTime。
- `AlarmReceiver` 測試路由：同一正式 Receiver 元件，收到測試專用 action 才驗證 Session/階段/時戳並進入正式 `AlarmService`；正常廣播流程不變。
- `AlarmService` 測試執行：專用 Session 模式、復用正式 Media3 隨機連播與系統警示音備援；只有 `onIsPlaying` 實際觀察到才前進測試 `AUDIO_STARTED/AUDIO_RESTARTED`。不寫正式鬧鐘事件／播放歷史，不執行 Guardian／Webhook／正常貪睡 count；真實鬧鐘可搶占測試。
- `ProductionTestAlarmActivity`：專用響鈴操作畫面；編輯頁保留舊版 B2-0 十秒送達測試，同時新增 **一分鐘正式音樂測試**與取消；中英日三語資源完整。
- G2-A 既有隔離狀態機與 noBackup proof 仍沿用。相關提交範圍 `3fd10f8`～`39b936d`；該段 CI 判準需以後續最終 HEAD 驗證，不可引用被取消的中間 CI。

### G3 每鬧鐘貪睡音樂模式：T1 正式路徑已接線
- 編輯音樂設定可選 `RESUME_INTERRUPTED`（同一首歌曲的實際中斷毫秒位置）或 `NEXT_DIFFERENT`（下一次響鈴避免上一首 URI；只有一首可播時保留該首），與原 RANDOM_WITH_REPEATS／SHUFFLE_WITHOUT_REPEATS／PLAYLIST_ORDER 策略並存。
- `AlarmSnoozeMusicModeStore` 以每鬧鐘 ID 的同步 preference 保存，未修改 Room v28。新建鬧鐘首次儲存與既有鬧鐘編輯皆保存；`AlarmMusicSettingsBackup.snoozeMusicMode` 可供手動匯出／重新匯入時還原新 alarmId 對應偏好。
- `SnoozeMusicRestoreStore` 使用 noBackup/AtomicFile 寫入，跨程序重啟保留下次 fireId、來源 fireId、URI、positionMs、playlistId、mode、TTL；無效／過期／跨火次拒絕還原，不保留到手動備份。
- 正式 `AlarmService` 在系統確認下一次貪睡已登記後、釋放 Media3 之前，擷取真正在 `onIsPlaying` 播放的候選與 positionMs。下一次火次驗證清單、Uri 與期限後，同曲從指定位置播放，或使用既有選曲策略排除上一首；無法定位（如 FLV）時回原播放策略及系統鬧鈴備援，避免靜音。
- 針對 READY 事件早於播放器參照建立的競態，`16df548` 將初始 Media3 seek 移到 `setMediaItem` 後、`prepare` 前。
- 新增 `SnoozeMusicRuntimeStoreTest`、`SnoozeNextDifferentPolicyTest` 與原有 `SnoozeMusicRestoreCheckpointTest`，驗證過期／跨鬧鐘／單曲／三種策略／原生持久化。
- G3 程式到 `16df548a4c7bcf07195a68971333c49f88246136`。對應 [CI Run 43963256748](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43963256748) 目前待完成；**T2 不宣稱通過，T3 依作者選擇暫緩**。
- 範圍註記：G2 test 模式自有 one-minute snooze，不寫入日常 U4 checkpoint，避免試響覆蓋使用者正在等待的真實貪睡曲目。G2 驗的是正式 Receiver/Service/Media3 起聲及再響；U4 專門測試由本段新測試提供，不冒充 G2 已實機驗證。

### 待收斂
1. 以包含最後一個 App 修改 SHA 的 CI build／unit tests／second-runner signing 確認 G2+G3，修正實際編譯、測試或簽章錯誤。
2. 守護跨程序重入、外部取消、來源權限撤銷、普通鬧鐘優先與無候選系統備援。未有 T3 者維持欄位未驗；但**不再因此停止開發**。
3. 若所有 CI 成功，維持 PR Draft 等後續使用者合併決定，不自行改 main。

- U4 一致性補強：`a1558ba` 修復「新建立鬧鐘首次儲存」同步保存 snooze mode 的缺口；`a51e905` 避免極短曲目 `onIsPlaying` 先於播放器 adapter 指派，使下一次貪睡找不到上一首歌曲實際播放者。
- 修補後完整 App SHA `a51e90513e7a0261fc45949d7cb9282bdfc18370`；[CI Run 43963741288](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43963741288) 已觸發，最後一次讀取仍待執行。GitHub workflow 使用單 branch 並行保護，中間提交的 run 大部分取消，**不能以取消或舊版綠燈為最新程式 T2 結論**。

- 最終 CI 證據補充（本輪）：`34052056df28b1254e684d363e95ed71b12d0647` 的 [Run 43964110045](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43964110045) 已確認 **Build F-Droid debug APK 成功**、**F-Droid unit tests 816/816（failures=0, errors=0, skipped=0）**；跨 Runner v160 簽章 Job 最後讀取仍 in_progress，故完整 3/3 尚未裁定。
- `994134ad414336de0965ee769e519334291462fb` 修復 U4 持久化快照讀回時可空 playlistId 的解析，屬前一個成功測試 SHA 之後的新程式變動；[最新 CI Run 43964849123](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43964849123) 最後查核為 pending。**不得使用 `34052056` 的 816/816 代表 `994134a` 已機械驗證。**
- 與作者授權一致：原 G1 亮屏十分鐘 T3 未完成，不再阻擋 G2/G3 開發，亦不自動填為 PASS。仍保留真實生活鬧鐘應有的系統備援。


## 2026-11-17 上午｜G1/G2/G3 安全回歸切片（後續已推送）

### 本切片的輸入與閘門
- 遠端 PR #13 基準 `0966356df551da8369ae4df3705c921f884572f9`，在施工前與 PR 的 HEAD 相同；無平行新提交。
- [前一輪 CI Run 43964849123](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43964849123) 已完整 **3/3 綠燈、816/816 測試**（SHA `994134ad414336de0965ee769e519334291462fb`）。本輪程式不同 SHA，必須另外取得 CI，禁止沿用這個成功作為新程式的 T2。
- 作者既有授權：**T3 實機測試非 Code-Go 阻斷條件**。因此仍持續遠端施工，但安全與發聲行為未經裝置實證時，T3 永遠保留未驗，不能改列 PASS。

### G1-R2：貪睡排程／資料庫／診斷容錯（P0）
- `a0e393e`：補 `AlarmScheduler.scheduleSnooze()` 及 `scheduleSnoozeAt()` 的 inexact fallback。若 OS `setAndAllowWhileIdle` 已接受，但 `repository.updateNextTrigger` 失敗，回滾新 `PendingIntent`，不再留下「服務認為拒絕、OS 卻之後幽靈再響」的排程。
- `cacc65a`：只在 OS 和 Room 都成功後認可貪睡；事件紀錄系統寫入失敗不會把已儲存的有效排程誤判為失敗、也不會取消它。失敗路徑的記錄同樣隔離例外。
- `52208d5` + `85cf8bd`：注入 Room 寫入失敗與診斷紀錄例外，分別驗證「回滾兩種 snooze」與「已儲存排程保持有效」。
- 狀態：T1 已推；T2 見最新 CI；T3 原亮屏十分鐘問題仍未驗（使用者選擇不阻擋後續）。

### G2-R2：正式診斷測試的精確火次與隔離（P0）
- `c3879a2`、`84043f1`、`9f79659`：測試 Session 新增 `matchesDelivery()`，必須核對來源鬧鐘、Session UUID、fireId 和 **完整 scheduledAtMs**；`SNOOZE_REGISTERED` 會把 1 分鐘後的**實際新火次時間**與 `nextOccurrence=1` 原子保存，拒絕漏填、過期或造假的時間。
- `7776e7e`：正式 `AlarmReceiver` 診斷路徑不再在 `startForegroundService` 成功的瞬間釋放 15 秒交接 WakeLock；改與正式鬧鐘一致，等待有界 timeout，降低 Doze 廣播交給 Service 之間 CPU 睡眠導致的漏響。Service 啟動失敗時即釋放。
- `991451c`、`1c35eaa`：Service 再次核對完整火次，阻止普通 `ACTION_SNOOZE`／`ACTION_DISMISS` 操作 B2 測試所借用的真實 alarmId；對內部晚到 dismiss 加防護；測試備援音訊強制 `dismissAtRingtoneEnd=false`，避免修改真實鬧鐘、歷史及第三方動作。
- `475840f`、`7384fa5`：增加可持久化的第二火次時間、回放不重複接受、錯誤時間／跨來源／過期拒絕等單元測試。
- `93fa664`：新增 `ProductionTestReceiverDeliveryTest` Robolectric，經真實 `AlarmReceiver` 路由驗證測試 Service 只啟動一次，以及錯誤時間／舊 fireId 不應啟動 Service。
- 狀態：T1 已推，T2 最新 CI 另驗；不把 B2 形式上完整狀態機等同於已在特定 OEM 手機發聲。

### G3-R2：換曲後的清單游標正確性（P1）
- `8cbccd3`：`PlaylistOrderSelectionPolicy` 在 `NEXT_DIFFERENT` 的**第一首**會從被中斷 URI 後方選取，遇相鄰重複 URI 則跳過；成功選到後保留完整清單游標，下一首不會因前次縮小候選集合又播同一首。
- `8d6cecf`：測試三首歌中斷 B→C→A→B、重複 URI 插槽 A/A/B/C→B→C→A，以及只有一首 URI 重複時仍能發聲。一般隨機與洗牌策略不改動。
- 當同 URI 在播放清單中重複多個位置時，僅有 URI 而無前次 playlistItemId，將以第一個匹配插槽為契約。未假裝能還原被中斷的精確重複插槽。
- 狀態：T1 已推，T2 最新 CI 另驗。

### 新版機械驗證（仍以外部 Actions 為準）
- 本輪最後程式 SHA：`85cf8bd73520f4ab5b8a83f7f484bbe66f05fe95`。
- [Run 43987464864](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43987464864) 是此 SHA 對應的完整 F-Droid Debug / unit tests / v160 升級簽章工作流；文件追加時最近一次查核為 **pending**。前面連續 push 的 CI 被 `concurrency` 取消，不是測試失敗。
- 後續依最終 CI 日誌處理編譯與失敗案例，保留 PR #13 Draft，不動 main，不修改 Room v28／簽章，不要求這輪立刻進行 T3。
