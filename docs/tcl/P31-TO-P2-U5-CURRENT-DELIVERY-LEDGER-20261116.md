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

- G1 非精準排程補強 `cacc65a` 後的逆向負例 `85cf8bd`：即使事件 `AlarmIncidentRepository.recordAsync()` 主動拋錯，OS+Room 已接受的 `scheduleSnooze()` 與 `scheduleSnoozeAt()` 必須仍回傳成功、不可呼叫 `AlarmManager.cancel`。
- G2 額外證據 `43602ec`：實際 `AlarmReceiver` 的第二次火次路由，測試先寫 `SNOOZE_REGISTERED` 的目標時間，再以正確第二 fireId 搭配**第一火次的時間**重放，確保不啟動 Service；用真正新時間才接受並送入 `AlarmService.ACTION_START_ALARM`。
- 本輪最終 App 程式 SHA `43602ec23f4da01c25fa41ebece61ae2e311c1dc`。[CI Run 43987702305](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43987702305) 在此記錄時為 pending；先前 SHA 的 cancelled 只是 concurrent push 的取消，不代表失敗。等同 SHA 之完整 CI 與 F-Droid build／upgrade signing 未成功前，G1-R2/G2-R2/G3-R2 標 T1，T2「待驗」，T3「作者選擇非阻斷」。


## 2026-11-17 中午｜CI 恢復綠燈後接續 U1／U2／U3／U5

### 先決 CI 閘門
- 前一輪 `43602ec` [Run 43987702305](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43987702305) 不是 PASS：建置及第二 Runner 簽章 Job 成功，但 unit-test Kotlin 因 MockK `AlarmManager.cancel(any())` 多載歧義於第 128/176 行無法編譯（測試檔，非實際 Android 程式）。
- `ad3369191e46024d57bca299d9cbf753c480513a` 明確改成 `any<PendingIntent>()`；[Run 44016231152](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44016231152) 已完成 **3/3 SUCCESS**：F-Droid debug APK、unit tests **826/826**（0 failures/errors/skipped）、v160 second-runner persistent signing。
- **依作者「若通過才接續 U1/U2/U3/U5」授權，所有 U 切片均在上述綠燈後才開始提交。**

### U1／U1.2：播放回播保留，大曲庫至鬧鐘閉環
- 保留已完成的播放歷史一鍵回播、歌曲歌名及一首一首 Media3 起聲紀錄，不重寫核心播放器。
- `410ad368`、`9c8cb1cb`、`3360b9cf`：同一 `MusicLibraryDialog` 的 `MusicPlaylistsWorkspace` 加入「套用至鬧鐘」操作，只有有可播曲目的清單可用。只在鬧鐘編輯頁提供 `onPlaylistChosenForAlarm`，使用共用的 `AlarmMusicSettingsAction.SelectPlaylist`；其他音樂中心入口不另造設定狀態。使用者仍需明示儲存，避免寫入未確認來源。
- `1c8f66ca`：新增 `MusicLargeLibraryPlaylistRoundTripTest`：4,935 首已索引曲目、批次挑選 200+ 首、建立清單、儲存特定鬧鐘清單和順序模式、重建 Repository 後讀回與正式火次 snapshot；既有 4,935 首索引不強制重新掃描。
- U1 T3 既有回播基準不回退；U1.2 完整真機 UX/秒數仍未驗，這一批屬 T1，待新 T2。

### U2：唯一來源／Sound Hub 一致化
- `8a51f452`：`AlarmEditViewModel` 改為選鈴聲後停用 `randomMusicEnabled`，選任何播放清單即啟用音樂來源；只保存非啟用來源的偏好供將來切換，不同時有兩個正式有效播放來源。
- 同一提交：音樂來源估算採 `musicEvaluationRequest` 序號及當前草稿雙重驗證；舊的 4,935 首查詢晚到不回寫舊清單／舊曲數／錯誤狀態；完成儲存時只對相同草稿回報 saved，不把之後的新草稿假標為成功。
- `ddc827e8`：編輯頁鈴聲欄若音樂播放為主則顯示「音樂庫」而非休眠中的鈴聲 URI。 `70fd0d60`：新增 U2 單一生效來源與非同步草稿防護單元測試。
- 套用清單按鈕有英文、繁中、日文資源 `music_playlist_apply_to_alarm`；不能把此切片冒稱完成了全 App 所有來源 Picker 合併。

### U3：五秒快速設鬧鐘有真實量測
- `31273b11`：`AlarmListViewModel.createQuickAlarm` 在按鈕觸發時計 `SystemClock.elapsedRealtime()`，收到 Scheduler 成功／失敗結果後公開 `QuickAlarmCreationMetric(elapsedMs,scheduled)`；僅成功且 `elapsedMs <= 5000` 才表示達到既定機械時間目標。已存在的原子防重複提交與失敗時停用無效鬧鐘保留。
- `9f802f85`：首頁成功註冊後顯示實測毫秒數；英文、繁中、日文 `alarmlist_quick_alarm_elapsed_ms` 文字。這量測是**從點擊至 OS 排程登記確認**，不包含使用者閱讀／觸控反應，不代表已實機完成全流程 T3。
- `a7a7a7bf`：`AlarmListQuickAlarmTest` 驗證雙擊只建立一筆、OS 拒絕時保存列被停用、不能報成功，以及五秒門檻的邊界。
- 不假設效能必達 5 秒；故不把 T3 先標通過。

### U5：原廠系統音候選確認隔離
- `ffa5085f`：`confirmedSystemToneUris()` 僅允許使用者在確認視窗看過的 Uri ∩ 最新索引中仍判定疑似系統音的 Uri；排除/還原操作先同步設 `loadingPool` 再排程 DAO，避免快點兩次覆蓋舊 Undo 快照。
- `ccd8c0c0`：`MusicLibraryDialog` 將確認當下的確切 Uri 集送回 ViewModel，不能把後來才新增的系統音候選暗中一起排除。
- `3bd44210`：測試確認後才出現的候選、使用者 SAF 自有音、未確認及已排除之音均不應新增排除。
- 保留使用者逐首包含/排除、還原及掃描原資料，不改 Room v28。

### 本切片機械驗收
- 本輪最後 App 修改：`a7a7a7bf26a87b807991a73804b716c310b0da8e`。
- [CI Run 44017961918](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44017961918) 在寫入本段時尚未完整驗證；**U1.2/U2/U3/U5 = T1；T2 待本 Run 完成；T3 仍非阻斷但未驗。**
- 未合併 PR #9～#13、未動 main、未改 Room v28 或 CI 簽章。若 Run 失敗，先根據直接日誌修補，不依舊綠燈放行。


## 2026-11-17 傍晚｜U 系列 T2 驗收與競態收斂（本次）

### 先核對遠端證據再允許施工
- 先前最後 App 變更 `a7a7a7bf26a87b807991a73804b716c310b0da8e` 的 [CI Run 44017961918](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44017961918) 已於 2026-11-17 04:49:16 UTC **3/3 成功**：F-Droid debug APK 建置、完整單元測試 **833/833**（files=152、failures=0、errors=0、skipped=0）與第二 Runner v160 簽章／升級相容性檢查均成功。可核定 **U1.2/U2/U3/U5 上一切片 T2 PASS**。
- PR #13 仍 open Draft、不合併 main。T3 特定手機曲庫／五秒操作／亮屏十分鐘貪睡仍未驗，且作者既已允許其作為非阻斷驗收欄位。
- 本切片查出新的非同步與 Undo 接縫，繼續按既有 T1→新 T2 閘門進行；**不可拿 833/833 冒充本切片**。

### U5-R2：確認快照與 Undo 對後續人工操作的尊重
- `2f56b402`：`MusicLibraryDialog` 在使用者點開「可能的系統音候選」確認視窗**當下凍結候選 URI 及顯示名稱**，不再以可動的背景 pool 狀態即時增長確認對象。點確認只提交這份凍結清單；後端再以當前索引與候選標記取交集。
- `772b4534`：`MusicLibraryDao.restoreSystemToneExclusion()` 在同一 Room transaction 只還原**當下仍處於 EXCLUDED 且仍在索引**中的 URI；若使用者在自動排除後重新 INCLUDE，其更晚的明示選擇優先，舊 Undo 不覆寫；不重新製造索引已刪除項目的成員列。
- `67f3423d`：手動 pool membership 變更、整來源批次加歌在**協程派送前**清空先前的 system-tone Undo 快照並同步鎖定操作，避免反覆點擊與跨操作 Undo 污染。原 catalog 修改路徑的快照失效機制仍保留。
- `0c46800c`：DAO Robolectric 新測試：已確認排除的數首音效中，後來被手動還原的歌曲不可被舊 Undo 改回先前狀態；未另行操作的項目才還原。
- 承認 OEM 認定只能標「疑似」，不得憑這些邏輯把系統音辨識宣稱 100% 準確。T3 尚未驗。

### U2-R2：舊非同步結果及雙擊 Save
- `9b353a78`：音樂設定的 Save 以 `AtomicBoolean` 在 coroutine 派送之前單飛鎖定；避免雙擊 Save 排入兩筆可能逆序完成的持久化。若儲存開始後草稿改變，舊成功 ACK 不能標記新草稿已儲存；Cancel、錯誤與 finally 都會解鎖。
- `45914008`：評估查詢的比對忽略不影響曲庫計算的 `snoozeMusicMode`；否則只有貪睡模式變更時，4,935 首背景查詢晚到卻被棄掉、畫面 `loading` 永遠不清。複製播放清單的結果如果在使用者另選清單後才返回，不再把選擇蓋回較舊來源。
- `c17434e9`：追加只變更貪睡模式的查詢比對測試。
- `17e647e8`：新增 `AlarmEditMusicSaveConcurrencyTest`：實際 ViewModel + Robolectric + 注入未完成的 Repository save，檢查雙擊只調用一次；儲存途中改模式，新草稿不獲得舊 ACK；再度儲存才回報已保存。
- 維持 U2 來源唯一有效狀態，不重寫舊 backup/Room 結構。此切片仍需新 CI 驗證。

### U1.2-R2：大型清單批次操作防連點
- `07d74ec3`：`MusicPlaylistsWorkspace.addSelectionToPlaylist()` 在 click callback 中先設 `busy=true` 再啟動 suspend addTracks，防止同一 frame 連點令同一批 200+ URIs 被加入兩次（原 contract 允許清單重複 URI，不能用資料庫 Unique 消除錯誤雙擊）。
- U3 沿用已通過 T2 的 monotonic tap-to-AlarmManager-ack 計時；並**沒有**新實機五秒達標證據。

### 本輪新 CI 閘門
- 本切片最新 App SHA `0c46800c385bce79e512df0d1aa5c52fe8bb5fe6`。
- [Actions Run 44057827720](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44057827720) 在記錄時 `pending`；等待完整 unit tests／APK／跨 runner sign 驗證，若編譯或測試失敗須修補後以新程式 SHA 重跑。
- 繼續保持 PR Draft、不動 main、Room v28、既有 debug 簽章以及原音樂播放歷史資料。

- `a963e32b`：U5 確認 UI 以前只列出前 5 個候選名稱，不能逐首檢視排除風險。現在使用凍結候選集的 LazyColumn 顯示所有可能的出廠音效，並允許單筆取消勾選；真正送至 ViewModel 的只有當下仍勾選的 URI，然後再與最新 metadata 取交集。不聲稱 OEM 音效辨識準確率，也不默默刪除音樂檔案。
- **本輪最新 App SHA**：`a963e32bccd776da6479ca394c221104e984c44b`；[CI Run 44058063317](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44058063317) 已觸發，記錄時 `pending`。前一 `0c46800c` Run 被 concurrency 取消，不能作為成功或失敗結論。只有新完整 Run 3/3 才能把本切片提升 T2。


## 2026-11-17 夜間｜TCL「現在先做」：最新 CI 編譯修補與 G1/G2/G3 證據整理

### 範圍與權限
- 本切片限定 **A1 測試編譯最小修補、A2 G1/G2/G3 機械驗收盤點、A3 PR 狀態更新**。U1.2/U2/U5 與全面 UI 整併屬下一階段。
- 保留 main、Room v28、既有歌曲/鬧鐘/播放歷史、Debug APK package/signing、其他 Draft PR；不合併、不強推。
- 作者已選擇 T3 非 Code-Go 阻斷；**T3 未驗不能填 PASS**，必須保留裝置可靠性風險。

### A1｜最新 CI 失敗及修補
- 前一程式 HEAD a963e32bccd776da6479ca394c221104e984c44b 的 [Run 44058063317](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44058063317)：APK Build SUCCESS、v160 第二 Runner 簽章 SUCCESS；Unit tests FAILURE，停在 Gradle :app:compileFdroidDebugUnitTestKotlin。直接日誌定位 AlarmEditMusicSaveConcurrencyTest.kt:15 的 Unresolved reference 'coAnswers'（錯誤的獨立 import）。
- [Commit 204054d544f5296b868f6cd2709a3083cd603e73](https://github.com/Penguink72/AlarmClockXtreme/commit/204054d544f5296b868f6cd2709a3083cd603e73) 只移除 import io.mockk.coAnswers，保留協程 Stub DSL 與原始競態斷言，不修改正式 App。
- 修補後 [Run 44099211724](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44099211724) 已由確切 code SHA 的 push 觸發；本段寫入時為 in_progress，**T2 待結論**。測試尚未完整通過前不可引用舊綠燈。
- 最近一次已通過的共同基準：[Run 44017961918](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44017961918)，code SHA a7a7a7bf26a87b807991a73804b716c310b0da8e，3/3 SUCCESS，833/833（0 failures/errors/skipped）。**僅代表該 SHA，不代表最新的 U5-R2/U2-R2/U1.2-R2。**

### A2｜G1/G2/G3 原始碼與測試矩陣

| 枝 | 正式程式接點 | 既有機械測試 | 已支持／待執行的驗收 | 不能宣稱 |
| --- | --- | --- | --- | --- |
| G1／S1 | AlarmScheduler.scheduleSnooze / scheduleSnoozeAt、SnoozeColdStartPolicy、AlarmService 冷啟動；內部分類 EXACT/INEXACT/FAILED | AlarmSnoozeRegistrationFailureTest、SnoozeColdStartPolicyTest | OS + Room 注入失敗拒絕成功；Room 失敗取消新 PendingIntent；診斷事件失敗不可取消有效排程；錯誤 fireId 不停止新響鈴 | 亮屏 10 分鐘後手機必然再次起聲；INEXACT 等同精準響鈴 |
| G2／B2 | ProductionTestAlarmScheduler、ProductionTestSessionContract、ProductionTestSessionStore、正式 AlarmReceiver 及 AlarmService / Media3 test mode | ProductionTestSessionContractTest、ProductionTestSessionStoreTest、ProductionTestReceiverDeliveryTest | sessionId/fireId/scheduledAtMs、TTL、重播及錯誤火次拒絕；階段不得跳過；noBackup 專用 proof 不污染正常 Room | Robolectric 代表 OEM 手機已真正起聲，或驗過聲壓與播放路由 |
| G3／U4 | AlarmSnoozeMusicModeStore、SnoozeMusicRestoreCheckpoint/Store、AlarmMusicSession、AlarmPlaybackPlayer.positionMs、AlarmService | SnoozeMusicRuntimeStoreTest、SnoozeMusicRestoreCheckpointTest、SnoozeNextDifferentPolicyTest、AlarmPlaybackPositionTest | 各鬧鐘模式獨立；快照 fireId/TTL/失效 seek 防護；三種策略、單曲與重複 URI 游標 | 所有格式能毫秒精準 seek；裝置跨程序恢復已實證 |

### A2｜TCL 機械 Gate
1. .github/workflows/p0-android-baseline.yml 對此分支 push 觸發三個 Jobs：F-Droid debug APK 建置、完整 F-Droid unit tests、v160 second-runner persistent signing。完整 :app:testFdroidDebugUnitTest 包含上表的 G1/G2/G3 測試類別；名為 isolated P3.1-U1 的前置測試步驟只有 U1 等既有目標，**不能假稱 G1/G2/G3 專項已由那個步驟執行**。
2. T2 PASS 必須是同一個最後 App 程式 SHA 的 Build/Unit/Signing 三個 Jobs SUCCESS，並且完整 XML 測試摘要 0 failures/errors/skipped。若止於測試原始碼編譯失敗，不得聲稱 833/833。
3. 若新 SHA 失敗，讀取直接 job log，做局部修補並重新 CI；禁止跳過測試、降低嚴格依賴驗證、刪除失敗斷言。
4. T3 保留 G1：「螢幕保持亮起，右滑貪睡 10 分鐘後實際再次起聲」；G2：「正式 Receiver→Service→Media3 首響及 1 分鐘再響，使用者資料未被測試污染」；G3：「同曲續播、換不同曲、單曲池及失效來源備援」。
5. G1 EXACT／INEXACT／FAILED 已在內部枚舉／診斷 reasonCode 區分，但部分公開 scheduleSnooze API 仍只回傳 Boolean；**前端是否充分提示降級**尚需後續設計。G2 AUDIO_STARTED 是 Media3 的機械狀態，非物理可聽音量保證。

### A3｜PR 更新規則
- PR #13 說明應改用本矩陣作最新狀態；舊的「B2/U4 全部未實作」只能標記為 PR 建立時歷史敘述。G2/G3 已正式接線，T1 已有，T2 依相同 code SHA 的最新 CI 裁定，T3 未驗。
- 不變更 main、Room v28、簽章與已保存資料；待 A Gate 完成才接續 U1.2/U2/U5 功能閉環。


### A1 實際 CI 讀回補充（Run 44099211724）
- 對應程式 SHA 204054d544f5296b868f6cd2709a3083cd603e73。
- Build F-Droid debug APK (strict)：SUCCESS。
- Unit tests F-Droid debug (strict)：SUCCESS。直接讀取 Job 134094366219 日誌的 UNIT_TEST_COUNTS：**files=153, tests=835, failures=0, errors=0, skipped=0**；修補後已實際完成單元測試，不再停在 Kotlin 測試編譯。
- Verify persistent signing on 2nd runner (v160 upgrade APK)：**in_progress**（本次最後讀取）。只有該 Job SUCCESS 才可裁定完整 T2 3/3。
- 後續文件只有證據同步，未再修改正式 App 或測試原始碼。主張「全綠」前仍應讀取 Run 最終結論。

## 2026-11-17 夜間續作｜U1.2/U2/U5 快速連點與來源快照

### A Gate 真正完成
- 前一段 A1 修補 code SHA `204054d544f5296b868f6cd2709a3083cd603e73` 的 [Run 44099211724](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44099211724) 完整 **3/3 SUCCESS**：F-Droid debug APK Build、Unit tests（**835/835**，153 XML，0 failures/errors/skipped）、第二 Runner v160 簽章升級檢查。後續的 `5b746bbd`／`ad4c95dc` 是文件提交，不修改 App。
- A2 G1/G2/G3 的可驗機械測試矩陣已在上節；T3 仍未驗，不能以完整單元測試代替手機亮屏貪睡實測。

### 本次封閉的三個接縫（T1 已推；T2 待新 Run）
- [程式提交 `e222a57d67cf3013f4990e52b11e88e807029f9f`](https://github.com/Penguink72/AlarmClockXtreme/commit/e222a57d67cf3013f4990e52b11e88e807029f9f)，單一 Git tree 原子提交，修改五個文件，無 Room Schema、正式鬧鐘 Service、簽章或版號變動。
- **U2 整體鬧鐘 Save 與音樂來源同步**：`AlarmEditViewModel.save()` 在 coroutine dispatch 前用 `alarmSaveInFlight` 單飛鎖；同一次點擊擷取 `AlarmEditUiState` 與 `AlarmMusicSettingsDraft`，避免 Alarm row 的 `randomMusicEnabled` 屬於 A 來源，但較晚持久化的 playlist 屬於 B 來源；處理預先檢查提早返回／協程取消時釋放鎖；正在保存期間拒絕新的音樂草稿 action。原有內層音樂設定 Save 的單飛鎖保持獨立。
- **U1.2 建立／重新命名**：`MusicPlaylistsWorkspace` 在 click callback 中立刻設定 `busy`，凍結提交名稱，保存中禁止對話框取消／滑動關閉；避免同 frame 連點與後來文字變更影響執行中的資料庫工作。
- **U5 手動加入／排除與 Undo**：`MusicLibraryViewModel.updateSelectedCatalogMembership` 先計算確實要更新的 URI，空集合不消耗先前的 Undo；非空集合才同步設 `loadingPool` 並失效舊 Undo 快照，避免雙擊或 Undo 跟手動 DAO 寫入交錯。
- 新增 `AlarmEditMusicSaveConcurrencyTest.wholeAlarmQuickDoubleSaveCreatesOneRowWithMatchingSoundSnapshot` 與 `MusicLibraryViewModelHistoryTest.doubleCatalogMembershipTapStartsOneWriteBeforeCoroutineDispatch`。原有 U1/U2/U5 測試保持。
- 新 App code SHA `e222a57d67cf3013f4990e52b11e88e807029f9f` 對應 [CI Run 44107395773](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44107395773)；本段寫入時 workflow 仍在進行。**只有該 SHA 的三個 jobs 都 SUCCESS 才能標 T2 PASS**，不可引用上一輪 835/835 直接替代。

### 下個獨立切片
1. 依 Run 44107395773 的直接單元測試日誌修補可能出現的失敗；維持既有測試，禁止為了綠燈直接略過。
2. 進一步核對播放清單複製／刪除／重排的連點鎖與「套用到指定鬧鐘後是否需再次儲存」的 UX 回饋；目前只修建立與重新命名。
3. U5 的可能系統音認定仍需 OEM 裝置抽查；目前無法證明 100% 辨識，Undo 不刪原始檔案。
4. 後續再接 L1-B 三語與 U3 使用者五秒操作測量，不因本次小改動要求立即重裝 APK。

### U1.2/U2/U5 本切片第一次 CI 直接日誌與測試補正
- [Run 44107395773](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44107395773) 的 targeted unit 測試 FAILED：新增的 `MusicLibraryViewModelHistoryTest.doubleCatalogMembershipTapStartsOneWriteBeforeCoroutineDispatch` 沒有觸發預期的 `setPoolMembership`。直接 job log 列出 `buildSupplementalCatalog(null, continuation {})`；測試 MockK Stub 卻以 `any()` 匹配 nullable 預設值，造成補充曲庫載入未能使用預設結果，並非已證明正式 U5 批次加入程式故障。失敗測試及斷言保留。
- 修補提交 [`0afd3c6e8622fa17631a8702b27d52aaa009fb44`](https://github.com/Penguink72/AlarmClockXtreme/commit/0afd3c6e8622fa17631a8702b27d52aaa009fb44)：只將 `repository.buildSupplementalCatalog(any())` Stub 改為 `repository.buildSupplementalCatalog(null)`，符合實際呼叫，不刪測試。
- 新 code SHA 0afd3c6e8622fa17631a8702b27d52aaa009fb44 已觸發 [CI Run 44108276894](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44108276894)。本段記錄時為 pending（GitHub workflow 同分支使用 concurrency 排隊，前一 Run 的簽章 job 尚在執行）。**T2 等新 Run，禁止把前一失敗或文件提交當作正式 App 功能回歸結論**。


## 2026-11-17 S0～S4｜偏差接縫集中修補

- 詳細程式、測試、TCL Gate 與非阻斷風險：[`P31-S0-S4-DIVERGENCE-SEAM-CLOSURE-20261117.md`](P31-S0-S4-DIVERGENCE-SEAM-CLOSURE-20261117.md)。
- 四批與首頁啟用 ACK 後補已提交到 PR #13，最新程式 SHA `2baaf0d5a287d3cea249eabbbe1609d244cee1e1` 應讀取 Run 44118596533 的 CI；前一 Run 44117243777 的 Unit Kotlin compile 因測試錯誤 import 而失敗。早期 S0 與 S1/S2 綠燈不能代替後續修改。
