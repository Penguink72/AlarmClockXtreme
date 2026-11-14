# Preview 2.2｜自動音樂庫與智慧過濾（2026-11-14）

## 1. 問題／來源證據

Android 16 實機回饋（使用者畫面）顯示 `limbus company` SAF 資料夾可掃描 24 首音訊、音樂池可以整批加入，但流程仍要求使用者手動尋找資料夾、管理來源、手動入池及切換鬧鐘。使用者需要的是：程序主動尋找已授權媒體，自動將合格音樂導入可播放音樂池，讓人僅管理例外，而不是逐層資料夾與逐首檔案。

截至 Preview 2.1 `d74c412`：MediaStore 掃描只抓 URI／title／filename，無 `DURATION`、`RELATIVE_PATH`、系統鈴聲標記；SAF 已能遞迴子樹；B9 的「新檔一律候選」雖避免鈴聲混入，但使正常音樂仍需人工入池。

## 2. 行為契約（以產品工作流程為 Done）

### 初次使用
1. 進入音樂庫時，若裝置媒體權限已有授權，**自動掃描 MediaStore Audio** 與所有已獲得 SAF 持久授權之目錄，無需先按掃描。首次未取得 `READ_MEDIA_AUDIO`，由使用者一次性決定權限；拒絕仍可使用已授權的 SAF。
2. 使用者新增一棵 SAF 上層資料夾，只要一次授權；程序遞迴索引其全部子資料夾。Android 11+ 不允許 SAF 任意選內部儲存／SD 卡根目錄；不繞過權限、不聲稱能掃描未授權影片。
3. 自動分類並將合格項目加入隨機音樂池。新增來源與既有來源的增量重掃都遵循同一規則。

### 預設音樂資格
- 音訊或帶可解碼音軌影片，**長度至少 60 秒**（使用者可調整 0、30、60、120 秒）；長度未知仍保留候選，不自動入池。
- MediaStore `IS_RINGTONE`、`IS_NOTIFICATION`、`IS_ALARM` 類型不自動入池；路徑中的 `Ringtones/`、`Notifications/`、`Alarms/` 不自動入池。
- `IS_MUSIC` 可作輔助資訊，不應被 OEM 錯誤標記變成全域硬性門檻。
- 不刪除、不移動任何實體媒體檔案；未入池仍可手動恢復或選入。

### 覆寫與狀態優先序
- `EXCLUDED`（使用者明確排除）高於任何自動規則；重新掃描、改變門檻、重新啟動均不得自動恢復。
- `INCLUDED`（使用者明確加入）同樣高於任何自動規則。
- `AUTO_INCLUDED`（程序自動加入）可以在下一次成功檢測到該曲目的元資料且不再合格時切成 `AUTO_SKIPPED`（自動略過）。
- `AUTO_SKIPPED` 對隨機播放不是有效資格，但手動加入可以轉成 `INCLUDED`；自動重新符合資格時可回到 `AUTO_INCLUDED`。
- MediaStore 掃描失敗、SAF 授權撤銷、來源失敗時 **不得根據空集合將舊成員批量移除**；只針對此次真正成功檢測的 URI 評估。
- 自動入池和批量加入應是 Room 原子交易；3,001+ 首不用 SQL `IN(?,?,...)` 巨量參數；避免併發掃描與 UI 加入造成覆寫。

## 3. 最小修改設計

以 Preview 2.1 修補分支為基準，維持 Room **v27／既有 Schema 及簽章**，先不用增加資料庫欄位。原因：這一批的自動資格只在掃描時判斷，已知狀態和人工覆寫留在現有 `music_pool_members.state`。未來若要在 UI 顯示每曲時長與排除原因，才評估升 v28。

變更：
- `MusicAutoPoolPolicy` 純 Kotlin：只判定本次媒體元資料（長度、MediaStore 類型旗標、相對路徑），含可調 0/30/60/120 秒門檻及未知值保守處理。
- `MusicLibraryIndexer`：MediaStore Query 納入 `DURATION`、鈴聲旗標、`RELATIVE_PATH`；SAF 索引延伸 `MediaMetadataRetriever` 時長判斷（可解碼音軌的原路徑不可拔除），回傳本次已評估／自動合格 URI 集合。失敗來源不列已評估。
- `MusicLibraryDao`：三種播放查詢皆承認 `INCLUDED` 和 `AUTO_INCLUDED`；新增交易批次 reconcile，自動值只更新 null／自動狀態，不能覆蓋手動狀態；手動資料夾批量加入可以覆寫 `AUTO_SKIPPED`，但不覆寫 `EXCLUDED`。
- `MusicLibraryViewModel`：進入音樂庫觸發自動掃描；掃描完成後同步 Reconcile；新增掃描結果中「自動入池」「自動略過」統計與閾值 UI。避免進度遮蔽錯誤，掃描期間鎖定相互衝突操作。
- `MusicLibraryDialog`：第一次開啟可觸發權限請求；即使拒絕亦可掃 SAF；顯示可調短音訊門檻，避免使用者翻找多層 Dialog。
- `MusicPoolDialog`：預設來源可看到自動辨識的 MediaStore 音樂；真正鈴聲／通知音留在裝置媒體範圍手動檢查。狀態欄將 `AUTO_INCLUDED` 視為已加入、`AUTO_SKIPPED` 視為候選。

## 4. 非目標與平台邊界

- 不要求 `MANAGE_EXTERNAL_STORAGE`，不存取其他 App 沙盒；不保證能自動探索 Android 11+ 禁止 SAF 選取的根目錄或未授權的影片。
- 不承諾 `MediaMetadataRetriever` 對每種編碼必定可得時長；未知時保留候選，不臆測。
- 不自動啟用任何原已停用鬧鐘；不自動改變鬧鐘時間、解除模式、播放音量。
- 不以單元測試代替 Android 16 的 `鎖屏→響鈴→連播→解除→歷史` 實機證據。

## 5. 具體回歸驗收

- 新掃描：60s 合格；59.9s 不入池；未知時長不入池；鈴聲旗標、系統目錄不入池。
- `EXCLUDED`／`INCLUDED` 人工覆寫在下一輪重掃仍保持；`AUTO_INCLUDED` 可以隨資格變更成 `AUTO_SKIPPED`，又可恢復。
- 失敗來源不清除舊池，3,001 個符合條件不超 SQL bind limit。
- 原有 B9 隨機跳曲、手機影音只播放音訊、播放紀錄、Room v27、固定簽章、三語切換不倒退。
- CI 完成編譯、指定測試、Schema v27 零差異、升級簽章；再以手機驗證授權、新增來源、二次掃描、篩選提示與實際響鈴。

## 6. 交付治理

實作分支獨立於 `main`、Preview 1、Preview 2、Preview 2.1；程式碼／測試／CI 同批交付；最終結果分別標記 `IMPLEMENTED`、`CI_VERIFIED`、`DEVICE_PENDING`。不把「已寫入規格」當成「已實作」，不把「CI 綠燈」當成「已在手機成功響鈴」。
