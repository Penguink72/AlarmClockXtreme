# Preview 2：B9 影音混合音樂池 × 中英日文介面整合計畫

日期：2026-11-13。TCL 規劃文件，**不是已實作證明**。此文件獨立於 App 程式碼與既有 main/Preview 1。

## 1. 基準、工作線、不可變契約

- Preview 1 基準：`preview/music-alarm-1@e7125b3f1db94ab3af7338fce6b4fb9b07f27e37`。
- B9 本機回報：`b9-av-mixed-music-pool@9db14a38381a9a829f018998d890778a1dae70fa`，遠端尚無此 commit，需交付 verified bundle / push；沒有 JAVA_HOME/SDK，未運行測試，也沒有 KSP v27 Schema。
- 三語功能：`feature/ui-language-switch-zh-en-ja-20261113@613cf49111512642303298bafa4c78dc8729c390`；[Draft PR #1](https://github.com/Penguink72/AlarmClockXtreme/pull/1)。英語基準 2,186 個 `<string>`，繁中與日文各 388（粗略字串覆蓋 17.7%，非畫面覆蓋）；語言功能已提交，不代表最新 CI 或手機實測通過。
- **影音需求不可改寫**：SAF 多個目錄遞迴發現 MP3/FLAC/M4A 等音訊和 MP4/MKV/WebM 等含可解碼音軌影片；共用同一音樂池；播放只輸出聲音、不顯示／不解碼影片畫面；依使用者明確的 INCLUDED/EXCLUDED 決定抽選；歷史只記真的開始播放。
- `main` 與 Preview 1 保持不動。目標新建 `preview/music-alarm-2`（B9＋三語；KSP v27；固定簽章 141/142）。

## 2. 翻譯實作結構

### 2.1 資源單一真相

- 所有使用者可見可翻譯的介面字串以英文 `app/src/main/res/values/strings.xml` 為主鍵；繁中 `values-b+zh+Hant+TW/strings.xml`，日文 `values-ja/strings.xml`。
- 使用 `stringResource()`（Compose）或 `getString()`（服務／通知），不在各畫面分別存一份語系表。動態歌名、URI、檔名、作者 metadata 不翻譯。
- Android 13+ 使用現有 AppLanguageManager/LocaleManager 與 Manifest localeConfig，Settings 的語言入口可到達並顯示已選語言；系統預設選項恢復系統語系。
- 新增 B9 英文字串鍵的**同一功能批次**必須附繁中＋日文對照；已存在未翻譯鍵循批次補齊，英文 fallback 可延用在非核心路徑。
- 審核 `string`、`plurals`、`string-array`、程式碼硬編碼使用者文字、服務通知、Widget、TalkBack semantics、錯誤與空狀態；現有 388/2186 只計算單純 `<string>`。

### 2.2 分批政策與優先順序

| 批次 | 門檻 | 預計內容 | 驗收 |
|---|---|---|---|
| I0（已編輯，待 CI/手機） | 可切換 | Settings 首頁入口、Android per-app locale、語言辨識、重要設定導航／提示 | 英／繁中／日文切換後，關閉重開仍保持；不影響鬧鐘 |
| I1（與 B9 一起整合，Preview 2 必需） | 核心流程端到端 | Music library 的來源、掃描、候選、已入池、已排除、批量加入／排除／恢復、重試、權限錯誤、播出歷史、空池備援；響鈴設定及音樂池開關 | 進入音樂池到設定鬧鐘的所有操作不需猜英文 |
| I2（Preview 2 後） | 鬧鐘重要流程 | 鬧鐘列表／新增編輯／排程、解鎖、貪睡、關閉、通知及危險提示，未翻譯的完整畫面分組補足 | 逐頁截圖、格式參數和輔助使用測試 |
| I3 | 計時器與常用設定 | 計時器、碼錶、顯示設定、電池權限、備份還原、數字／日期／時區格式 | 中日英切換、背景通知、複數／時間格式 |
| I4 | 進階功能 | 起床挑戰、睡眠、整合服務、設備設定、其餘低頻頁面 | 明確記錄翻譯覆蓋率及 fallback |
| I5 | 收尾 | 硬編碼掃描、其他資源類型、翻譯一致性、TalkBack、布局截斷 | 跨螢幕尺寸／深色模式／Android16 端到端檢驗 |

**每一批 1～3 個完整畫面／流程**，實作者先輸出缺鍵清單，新增資源及測試，再跑 CI、附畫面驗收證據；不要以自動翻譯一次填完 2,186 鍵作為交付。

### 2.3 最小工具化

新增可重跑的資源檢查腳本／測試（由後續實作者建立）：

1. 讀英文主鍵，列出繁中/日文缺失、重複、未使用 key；依 prefix＋畫面實際使用分類，輸出 coverage 報告。
2. 檢查 XML 可解析、`%1$s`/`%1$d` 與序號匹配、轉義、plurals/string-array 規則、硬編碼 UI 字串候選。
3. 核心 I1 key 對三語採嚴格缺字阻擋，非核心用英文 fallback 並列待翻譯。
4. Robolectric 以不同 Locale 取 `R.string`，實機驗證 Android 16 設定切換後 Compose、通知及重新開啟行為；音樂檔原生中日英歌名絕不隨語系被翻譯。
5. Coverage 報告記錄 `baseline SHA`、已翻譯 key 數、核心必要 key 缺漏與 `string-array/plurals` 缺漏；避免盲目顯示「100%」。

## 3. 音樂池功能和介面的最小整合設計

### 3.1 不可混淆的四層

- **Source**：SAF 授權資料夾（含子資料夾、重疊 URI 與失敗狀態）。
- **Catalog**：掃描得到的可解碼音軌候選，音訊或含音軌影片均可存在。
- **Membership**：不存在紀錄＝新候選未加入；INCLUDED＝可以抽選；EXCLUDED＝禁止抽選。v26→v27 只在遷移時一次性把原已索引內容納入，後續重掃不能使 EXCLUDED 復活。
- **History**：實際開始播放的不可隨成員狀態更改的歌名、時間、順序快照。

### 3.2 UI 導覽與最小互動

入口：`Alarms → Sound & vibration → Music library`（保留 B1/B2 直達），進入可承載 3,001+ 曲目的管理頁／擴展介面。**先接線 B9 已存在的 UI；確認後才決定是否將原有嵌套 Dialog 改成全頁，避免重做。**

介面區域：

1. **摘要**：候選總數、有效池數、已排除數；按音訊／影片音軌分別計數；掃描失敗／權限不可用顯示。
2. **來源**：加入多個資料夾、選子資料夾、重新掃描、來源移除；每個來源列出數量／權限和錯誤。
3. **曲目編輯**：搜尋（歌名／檔名）、來源篩選、`候選／已加入／已排除` 篩選；以穩定 URI 識別每列；來源及媒體類型標示；支援目前來源／篩選結果的全選與批量加入／排除／恢復。全選範圍需明示，不能將全手機 MediaStore 混入。
4. **播放紀錄**：日期、時間、歌名、順序，與池成員操作分離。

空狀態要可操作：沒有資料夾→加入資料夾；有候選但空池→全選加入；池內全失效→告知備援與授權檢查。不允許使用者誤把「刪除池成員」理解成「刪除手機檔案」。

**性能約束**：3,001+ 清單使用 LazyColumn/分頁查詢，穩定 URI key；不要在每次 Compose 重組重新掃描或一次生成數千元件；跨頁全選批次操作要用安全 SQL／分段更新，不把數千 URI 塞進單一參數列表。

### 3.3 接線與一致性

`SAF → Indexer → Catalog → Membership → MusicLibraryRepository.pickRandomTrack → AlarmService(Media3 audio-only) → PlaybackHistory`

- 隨機查詢及失敗排除查詢都只選 INCLUDE 成員，無播放資格不能靠舊 `pickRandom()` 旁路。
- 影片禁止顯示影像／選取視訊軌，但保留可用音軌；Media3 實機驗證 MP4/MKV 實際聲音。
- 掃描/成員批次編輯並發不得把使用者 EXCLUDED 蓋掉；來源失效不可清空；移除一個重疊來源不誤移其他來源的曲目。
- 播放中排除一首，當前歌曲可完成，下次抽選必須服從最新 membership；解除/貪睡立即停止。
- `history` 使用 `displayTitle` 記錄快照，不因重掃、改名、排除、切語言而改寫。
- 系統預設鈴聲不經使用者選擇不得進**新候選的有效池**，但故障備援鈴聲獨立保留。

## 4. 合併拓樸與工作分工

1. **先讓 Copilot 交付 B9 bundle**（既有 SHA `9db14a3`，parent `e7125b3`；完整 history），供 Argon 驗證。
2. **Argon 從 Preview 1 分支開 `preview/music-alarm-2`**，接 B9 提交；先修 Room/DAO/播放/UI 接縫，再挑入語言分支 PR #1 的程式變更。注意 B9 可能也修改 zh/ja/英文 strings：應逐 key 合併，不覆蓋任何一邊。不要把語言 PR 內僅供分支驗證的 139/140 workflow 設定直接作為新版號。
3. **將 I1 核心翻譯補齊**，以與 B9 介面實際出現的字串為準；保留 I2～I5 作為之後獨立小批次。
4. **正式 Room v27**：26→27 非破壞 migration、正式 KSP `27.json`、歷史 Schema/identityHash 零差異、24→27 資料保留。
5. **嚴格 CI**：APK 主包 141／升級包 142，同固定簽章、package `com.sysadmindoc.alarmclock.debug`；編譯、UI 資源、B9、新翻譯與既有回歸測試。原有 BedtimeReminder/TimerAlarmService 時間敏感基準失敗要分別報告。
6. **實機 T3**：Android 16 安裝→三語切換→搜尋影音目錄→限定來源批量加入→排除 12 首→MP3 與 MP4 純音訊連播→查看歷史→139/140→141/142 不卸載升級、資料與權限保留。

## 5. 里程碑／停止條件

- **M0**：B9 提交可讀、T1 程式實際審查；語言 PR 合併衝突方案確定。
- **M1**：Room v27＋成員限制與 UI 全選/排除運作；I1 三語 key 齊備。
- **M2**：KSP v27、APK v141/142 CI 通過新增功能測試、簽章不變、artifact 可下載；可放行 Preview 2 手機試用。若既有兩項基準測試失敗，不宣稱全部綠燈。
- **M3**：手機實測音軌連播、音樂池更改、語言切換與覆蓋更新；問題依可重現案例小修。
- **M4**：I2～I5 分批翻譯；不得拖延 M2 可用 APK 交付。

## 6. 證據分級

- 目前三語已在 GitHub 提交；第一個語言分支 Run `43364070557` APK Build 成功、719 單測 1 筆 BedtimeReminderBaselineTest 失敗；新增語系資源 Robolectric 測試所在最新 Run `43364202338` 須獨立查結果，不可提前宣稱通過。
- B9 的 `9db14a3` 不在遠端；其 Migration、Media3 軌選擇與池 UI 僅為 Copilot 回報，未獨立讀碼與編譯。
- `T0` 契約可確認；`T1` B9 讀碼、`T2` Preview 2 CI、`T3` Android16 實機尚待完成。


## 7. CI 追加核對（2026-11-13）

- 語言分支最新 `613cf491` 對應 GitHub Actions [Run 43364202338](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43364202338)：`testFdroidDebugUnitTest` 完成，**720 tests / 2 failed**；失敗為 `BedtimeReminderBaselineTest` 與 `TimerAlarmServiceTest`，本輪新增語系資源測試不在失敗清單。APK Build Job 在本次檢查時仍進行中，勿稱此 Run 全綠。
- 前一筆語言分支 [Run 43364070557](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/43364070557)：嚴格 APK Build 成功，719 tests / 1 failed（BedtimeReminderBaselineTest）；固定簽章第二 Runner Job 成功。這個前次 Build 並未包含最後新增的 `AppLanguageResourcesTest` 提交。
