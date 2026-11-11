# 個人音樂鬧鐘｜現行規劃入口

**T0：G0/G1 需求已修訂，App 功能程式未修改；G0 嚴格建置尚未通過。** 2026-11-11。Repo：Penguink72/AlarmClockXtreme，feature/playback-history-rotation；原版 main@a0cc501bcc3514ffd36483ca0b6e266aa234aaff。

## 優先閱讀

1. **[CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md)**：現行唯一需求權威。3,000 首一次索引、逐首獨立隨機抽、目前歌名、只記播放開始的簡單歷史。
2. **[TCL-G0-G1-OVERVIEW.md](TCL-G0-G1-OVERVIEW.md)**：G0/G1 根—葉與實際驗收。
3. **[G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)**：音樂索引、保存、連播、歌名歷史與安全接線工作單。
4. [G0-IMPLEMENTATION-PLAN.md](G0-IMPLEMENTATION-PLAN.md)：GitHub Actions APK 建置規劃；[P0-BASELINE.md](P0-BASELINE.md)：先前嚴格驗證問題、診斷版執行 675 項測試有 1 項失敗的歷史證據。

## 現在要做

保留既有定時鬧鐘；一次授權後自動把手機及 microSD 的音訊索引成至少 3,000 首的同一庫（必要時指定資料夾遞迴補掃）；不逐首勾選。每首自然播完重新抽一首、允許重複；真正播放才顯示曲名與追加日期／時間／順序紀錄；解除後按日期可查。失效音樂必須有內建鈴聲備援；原版舊鬧鐘未啟用新模式時不能改變。

## 舊設計延後，不自動恢復

**[DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)**：舊 20 首逐首選取方案、預洗牌、每日輪換、避免重複、完整播放事件狀態、跨版歷史備份等，**不是現行任務**。只有使用者日後實際需要並再次明示，才重新評估。原版 20 首 ringtonePool 必須保留相容，但新大庫不使用其容量上限。舊 G2 的曲名及歷史現在已納入 G1 必做。

## 歷史參考，不是工單

[B-ARCHITECTURE-AUDIT.md](B-ARCHITECTURE-AUDIT.md)、[B-FLOW-MAP.md](B-FLOW-MAP.md) 保留原始碼事實；[B-CHANGE-PLAN.md](B-CHANGE-PLAN.md)、[B-TEST-MATRIX.md](B-TEST-MATRIX.md)、[B-IMPLEMENTATION-BACKLOG.md](B-IMPLEMENTATION-BACKLOG.md) 均已不再適用現行需求，不得直接照舊版執行。

只在 feature branch 開發，不推 main／上游、不強推、不自行合併。T0 計畫不是 T1 程式實作；G0 真正嚴格 APK 和 T3 白天手機驗收需另外取得證據。