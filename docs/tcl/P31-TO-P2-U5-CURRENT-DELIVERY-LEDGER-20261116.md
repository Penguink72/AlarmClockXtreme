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
