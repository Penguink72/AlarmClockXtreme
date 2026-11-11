# TCL｜G0 + G1 實作綱要（供作者理解與 Agent 交接）

狀態：**T0／已規劃、尚未實作**  
建檔日期：2026-11-11（Asia/Taipei）  
Repo：`Penguink72/AlarmClockXtreme`  
分支：`feature/playback-history-rotation`（不改 `main`）  
原版功能碼基準：`main@a0cc501bcc3514ffd36483ca0b6e266aa234aaff`

## 目標與邊界

**G0：GitHub Actions 穩定產生可供 Android 手機安裝的 Debug APK。**  
**G1：既有鬧鐘在使用者明確啟用「隨機連播」時，能從手機或 microSD 上的自訂歌曲清單連續隨機播放，直到解除／貪睡／既有自動停止條件成立。**

延後：G2「響鈴畫面顯示當前歌名＋按日期查看播放起始紀錄」；G3「每日輪換起始歌曲」。本輪不開發播放完成／中斷／錯誤狀態歷史。SIM 卡不是音樂儲存來源；所稱記憶卡以 microSD／Android 文件提供者為準。

## 根—葉（工作產物，而非按時間堆疊的任務）

| 葉片 | 給使用者的結果 | 程式／驗證邊界 | TCL 完成判斷 |
|---|---|---|---|
| G0-L1 可重現建置環境 | GitHub 能真正打包 Android | 固定 JDK/SDK/runner、嚴格 dependency verification | 真正 Gradle `assembleFdroidDebug` exit 0 |
| G0-L2 APK 可取得 | GitHub Actions 顯示 APK 附件 | 獨立 upload，驗證檔名、尺寸、包名、雜湊 | 附件存在且能下載 |
| G0-L3 基線證據 | 知道原版哪項測試失敗 | 不刪測試、不把已知錯誤算 PASS | 完整測試報告與差異；無新失敗 |
| G1-L1 歌曲選擇 | 能勾選自己 MP3/WAV | 沿用 SAF 資料夾／URI；歌曲池與選曲 UI 串接 | 選中 3 首後重開仍可讀 |
| G1-L2 隨機連播 | A→C→B 自動輪到下一首 | Media3 `setMediaItems`＋shuffle＋repeat all | 多首循環，不只單曲循環 |
| G1-L3 停止與備援 | 關鬧鐘就不再放下一首 | 解除／貪睡／搶占／超時、watchdog、fallback | 競態測試與手機測試成立 |
| G1-L4 舊行為保護 | 沒開新模式的鬧鐘仍照舊 | 預設舊行為，設定可回退；舊備份兼容 | 舊鬧鐘／資料庫升級回歸 |

## 根—業（會反覆出錯的接縫）

- **K0 依賴信任**：驗證清單缺項，不能用關閉驗證湊成功。
- **K1 假綠燈**：GitHub Actions 的步驟成功不等於 Gradle 成功；需根據 exit code 和 APK 存在性。
- **K2 音源路徑**：記憶卡路徑、URI 授權或檔案移動都會影響隔天播放；無效時必須備援。
- **K3 兩種結束行為衝突**：現有「單曲播完解除」不能與「播放下一首」同時生效。
- **K4 晚到回呼**：解除、貪睡、前一個鬧鐘被搶占後，不得再啟動下一首。
- **K5 範圍擴張**：不把 Play flavor、Spotify、網路電台、YouTube、每日輪換或歌名歷史混入 G1 第一版。

## 依賴順序

```text
G0-1 建置環境／依賴校驗
   ↓
G0-2 APK artifact ＋ G0-3 原版測試基線
   ↓（G0-T2 可建置且能下載 APK）
G1-1 歌曲來源／模式設定
   ↓
G1-2 Media3 隨機連播
   ↓
G1-3 停止、備援、回歸測試
   ↓
G1-T2 GitHub APK ＋ G1-T3 手機白天驗收
   ↓
G2 曲名顯示／播放起始紀錄（下一階段）
```

## T0～T3 證據

- T0：計畫、來源程式路徑、可檢驗需求（**本次完成**）。
- T1：實際 Kotlin／UI／CI 變更 Commit（**未開始**）。
- T2：真實 Gradle exit code、測試結果、可下載 APK（**G0 待完成**）。
- T3：你的 Android 手機實際選記憶卡、鎖屏響鈴、連播、解除（**必須實機**）。

**G0 放行 G1 的最小門檻：嚴格依賴驗證下 APK 編譯成功且附件可下載。** 原版中已知、與連播無關的測試失敗可明確列為 baseline debt，必須保留紅燈與證據；新功能不得再增加失敗。

## 詳細工作單

- [G0-IMPLEMENTATION-PLAN.md](G0-IMPLEMENTATION-PLAN.md)
- [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)
- 已有診斷依據：[P0-BASELINE.md](P0-BASELINE.md)
- 先前的擴充構想：[B-CHANGE-PLAN.md](B-CHANGE-PLAN.md)、[B-TEST-MATRIX.md](B-TEST-MATRIX.md)。本 TCL 為**較新、較窄的需求版本**；有衝突時先回報，不可默默採用舊版複雜的歌曲事件狀態。
