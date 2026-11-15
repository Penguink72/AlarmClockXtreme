# Preview 3.1-U1.1｜v149 真機回饋及下一版施工紀錄

日期：2026-11-15。施工基準：`integration/p31-u1-history-replay@330f30ff2df6bbba6cc266ca3dad05464d6866ed`。
施工分支：`feature/p31-u11-dismiss-navigation-and-music-save-20261115`。狀態：程式已提交，CI 與真機結果須以實際新版本執行紀錄為準。

## 1. 使用者已驗收的 v149 事實

- v149 覆蓋安裝、保留原有資料：通過。
- 鬧鐘播放歷史即時更新、首頁一鍵起聲：通過。
- 鬧鐘優先權：通過。
- 從音樂目錄試聽切換到歷史回播：通過。
- 歷史回播反向切到目錄試聽：尚未手動操作；已由 U1 自動測試覆蓋，暫非高頻入口需求。
- 新問題：解除鬧鐘後返回手機桌面；選曲時缺少方便的直接試聽；播放清單建立與選擇入口不清楚。

## 2. U1.1 本輪程式切片

### A. 解除後的返回行為

- DataStore 增加 `returnToAppAfterDismiss`，預設 true，在設定頁可切換。
- `AlarmFiringActivity` 解除時不再在送出 Service 指令失敗後直接 finish。
- 返回 App 模式：等待 `AlarmService.activeAlarm` 不再指向被解除的鬧鐘後，若手機已解鎖，開啟 `acx://navigate/alarm_list`；不從鎖定畫面強制打開 App。
- 返回原畫面模式：保持關閉獨立響鈴 Activity 的原有行為。
- 待真機檢查：從桌面、其他 App、鎖屏、連續兩個鬧鐘、Service 起動失敗或逾時的結果。

### B. 鬧鐘與音樂設定保存一致性

- 不再忽略 `musicLibraryRepository.saveSettings` 的失敗結果。
- 若剛建立的新鬧鐘尚未排程而音樂設定寫入失敗，回復新增的 Alarm row；編輯舊鬧鐘時回復先前的 Alarm row。
- 目標：不顯示假成功、不造成重按儲存產生重複鬧鐘；不得把這視為已完成所有跨資料庫一致性／斷電測試。

### C. 音樂來源快速操作

- 音樂編輯區顯示目前所選播放清單、匹配可播放歌曲數與策略。
- 新增直接進入播放清單建立／管理分頁的入口。
- 新增「試聽目前聲音」：從尚未保存的草稿解析一首可播放樣本，使用原本的 `MusicCatalogPreviewController`；不建立鬧鐘、不寫入正式歷史／統計。
- 此動作 **不是** 真正 AlarmService 的完整響鈴測試，不能拿單曲 Media3 試聽推論音量漸強、貪睡、鎖屏與整批連播均成功。

## 3. 尚未施工的完整響鈴測試

「立即測試完整響鈴」須先設計 `testFireId` 隔離契約，確保不更動正式 AlarmManager 排程、統計、播放歷史、Snooze 鏈與通知。不能直接對既有鬧鐘呼叫正式 `ACTION_START_ALARM` 而污染資料。
此項另列 U2-B2，不冒充本輪樣本試聽的實作成果。

## 4. 交付與測試條件

- 本版預定以 CI 產出 `versionCode=151`，第二 Runner 簽章驗證 `versionCode=152`，與既有 v148/v149/v150 相同 package 和憑證。
- Room v28 不得改 schema。
- 完整測試、真機退出導航與播放清單可用性，在新版本經過驗證之前標記「待驗收」。
- 既有 v149 功能維持可回退版本；此分支暫不合併 main。

## 5. 後續工包

U2-B2：獨立測試響鈴事件（含隔離及完整 Service 驗證）；U3：5 秒建立新鬧鐘；U4：兩種 Snooze 音樂續播／切曲；U5：可撤銷的系統音批量排除。

## 6. v151 真機驗收回饋（2026-11-16）

使用者畫面：音樂庫已索引 4,935 首；搜尋 `Jia Peng Fang`，顯示「掃描中...」「正在掃描音訊來源...」「0 個結果」，且無法完成播放清單選曲流程。

使用者陳述：
- 設定「解除鬧鐘後返回鬧鐘列表」的開關找不到。
- 單曲「試聽目前聲音」正常運作，標記 **真機通過**。
- 播放清單的搜尋太久，無法完成後續設定。
- 先前測試項目 4（重新開啟檢查播放清單與連播）**尚未進行**，不能記為失敗。

已核對根因／修補（此次提交後待新 CI）：
1. `SettingsScreen.kt` 原把返回偏好放在 Defaults > Dashboard，現在移到 Defaults > Alarm Defaults 第一列，避免使用者找不到；不是新增第二份設定。
2. `MusicLibraryDialog.kt` 原進入畫面後必做 `viewModel.scan()`，每次重新枚舉 MediaStore/SAF 來源；現在先讀既有 Room 索引，只有索引完全空白且已有權限／資料夾來源時才自動掃描。仍保留使用者手動掃描。
3. `MusicLibraryViewModel.kt` 原搜尋只比對 `MusicTrack` 的歌名／檔名／來源名稱，漏掉 `MusicCatalogTrackMetadata` 的 artist/album/folderPath；現在加入同索引 metadata 比對，不需要 Room schema 更動。
4. `MusicLibraryDialog.kt` 將 4,000+ 首的篩選列表記憶化；不因播放狀態、掃描狀態等無關重組重做全部字串過濾。
5. `MusicPoolFilterLogicTest.kt` 增加 `Jia Peng Fang` 演出者及專輯／資料夾搜尋測試，以及 4,935 首索引存在時「不要再次自動掃描」的純函式測試。

本次產物版號進位：Runner1 `versionCode=153`、Runner2 `versionCode=154`，需保有 Preview3 v148 至 U1.1 v152 相同 debug 簽章。不得卸載清除使用者音樂庫。

真機後續驗收：從設定 > 預設 > 鬧鐘預設 找到返回開關；音樂庫再次開啟後在沒有按重新掃描時可直接搜尋 4,935 首已有索引曲目；在有 artist 標籤的音軌用人名搜尋可找到；選歌→加入播放清單→回編輯畫面套用→保存→重新開啟讀回。若檔案沒有該演出者 metadata 且檔名不含關鍵字，搜尋為零仍屬正確結果，應以已知存在的檔名交叉驗證。
