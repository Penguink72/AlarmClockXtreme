# B0｜驗收與測試矩陣

> **歷史文件／舊設計已延後（2026-11-11）**：本頁的舊提案、實作階段、測試矩陣或設計假設**不再授權直接實作**。請改用 [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) 與 [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)；舊方案參見 [DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)。原始碼現況與流程盤點仍可作為歷史事實參考。


**狀態：全部 NOT RUN**。本輪未執行 Gradle、Android emulator 或手機驗收；下表為之後的驗收契約。

基準：`a0cc501bcc3514ffd36483ca0b6e266aa234aaff`。R 類應比對未修改基線。測試資料使用合成短 WAV／可預期 URI；不可依賴真實起床鬧鐘。

| ID | 測試與輸入 | 預期結果 | 層級 | 狀態 |
|---|---|---|---|---|
| P0-01 | 原 `./gradlew :app:testPlayDebugUnitTest` | 記錄原版本 pass/fail、環境、時長；不把失敗隱藏 | JVM | NOT RUN |
| P0-02 | 原 Media3 smoke test | Media3 正常開始聲音或有可稽核 fallback | instrumentation | NOT RUN |
| P0-03 | 原 Room migration tests | 既有 schema 可升到 v24 | instrumentation | NOT RUN |
| D-01 | 3 首歌、3 個日期、固定種子 | 每日第一首可重現，歌曲足夠時避免相鄰重複 | JVM | NOT RUN |
| D-02 | 同日同鬧鐘重響／snooze | 不意外更換每日第一首 | JVM + integration | NOT RUN |
| D-03 | 歌曲池 0 首／1 首／重複 URI | 有有效 fallback；不進入死迴圈；有效候選去重規則明確 | JVM | NOT RUN |
| D-04 | 23:59 開始、跨到翌日 | 目前場次播放清單不被午夜重新選曲覆蓋 | JVM | NOT RUN |
| D-05 | app 重啟／手機時區改變 | 狀態重建依定義的 timezone policy；不能依假定本地日 | JVM + integration | NOT RUN |
| S-01 | 3 個 0.5–2 秒合成短 WAV | 依序連播；正常結束不自動解除；每首只啟動一次 | JVM + instrumentation | NOT RUN |
| S-02 | 舊曲 ENDED 同時 DISMISS | 新歌絕不在解除後響起 | JVM concurrency + instrumentation | NOT RUN |
| S-03 | 舊曲 ENDED 同時 SNOOZE | 貪睡後無新歌；下次重響是新場次 | JVM concurrency + instrumentation | NOT RUN |
| S-04 | 無效 URI、解碼失敗、全部無效 | 有界重試，進入安全預設鈴聲而非完全靜音 | integration | NOT RUN |
| S-05 | 既有 default / dismissAtEnd 設定 | 升級後未開新模式時行為保持原樣 | JVM + instrumentation | NOT RUN |
| S-06 | watchdog 與 onError 同時 | 最多單一路徑進入 fallback，不啟動雙播放器 | JVM concurrency + instrumentation | NOT RUN |
| S-07 | A 鬧鐘被 B 鬧鐘搶占 | A 的晚到 ENDED 無法結束／覆蓋 B | JVM concurrency + instrumentation | NOT RUN |
| S-08 | 已開始連播後到 auto-silence 上限 | 場次依原時間截止，不因切歌延長 | integration | NOT RUN |
| S-09 | Spotify、radio、YouTube 非本地歌曲池 | 舊有行為維持，不誤認為已支援跨曲 | regression | NOT RUN |
| S-10 | 連播時進入電話靜音、音量漸強 | 音量／ducking 行為符合原定義 | instrumentation | NOT RUN |
| H-01 | 一場三首短歌 | 3 筆歌曲級 STARTED／結果事件可區分且歸屬同場 | DAO + integration | NOT RUN |
| H-02 | 同一曲回呼重送、重複寫入 | 事件數不增加；unique index／DAO 冪等有效 | DAO | NOT RUN |
| H-03 | process kill 後重開 | 已落盤事件保留；未完成事件不被偽報 completed | DAO + integration | NOT RUN |
| H-04 | `STATE_READY` 但不播放 | 不建立已實際播放的 STARTED 記錄 | JVM + instrumentation | NOT RUN |
| H-05 | fallback 啟用預設音 | 歷史標示真正播出的音源而非候選歌曲 | integration | NOT RUN |
| H-06 | 清空歷史 | 不刪除音樂檔案與鬧鐘設定；需顯式用戶動作 | DAO + UI | NOT RUN |
| H-07 | 歷史查詢數量極多 | 有上限、可排序、沒有無限成長 | DAO | NOT RUN |
| R-01 | DB v24 → 新 v25 | 舊鬧鐘欄位、設定、事件完整保留，新 schema 合法 | Room migration | NOT RUN |
| R-02 | 各已匯出舊版 schema → 最新版 | 所有支援升級路徑不丟資料 | Room migration | NOT RUN |
| R-03 | 舊版 JSON 備份 → 新版匯入 | 舊欄位保留、模式預設正確，不自動清空歌曲池 | instrumentation | NOT RUN |
| R-04 | 鎖屏、Doze、重開機、精準鬧鐘 | 舊鬧鐘可靠性不退化 | 真機 | NOT RUN |
| R-05 | 正式起床前的白天測試與 Debug APK | 使用獨立測試鬧鐘／裝置配置，完成實機復測 | 真機 | NOT RUN |

## 交付證據要求

每一項完成時需附：Git Commit、測試指令、實際 log／報告、測試日期、環境（JDK/SDK/API Level/手機型號如有）、期待／實得、剩餘限制。只有檔案存在或編譯成功不能標成全部測試通過。

## 最低合併門檻

1. 原專案與新功能相關單元測試可重現執行。
2. Room migration 全部通過，且 schema v25 有匯出；絕不使用 destructive migration 避開失敗。
3. 每個日輪換、連播、歷史的核心案例通過。
4. 具有至少一次「連播 vs dismiss/snooze/前場搶占」的競態驗收。
5. 針對本機歌曲失效、失聲 watchdog 和 fallback 進行實機測試。
6. 新增介面預設不影響既有鬧鐘。
7. 尚未實跑的真機項目一律維持 NOT RUN，不用模擬結果填成 PASS。
