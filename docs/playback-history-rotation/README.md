# 音樂鬧鐘雙模式／播放歷史：開發交接入口

狀態：**B0 原始碼只讀盤點，未修改執行程式，未執行測試**  
基準：`Penguink72/AlarmClockXtreme`，`main@a0cc501bcc3514ffd36483ca0b6e266aa234aaff`  
建立日期：2026-11-11  
上游：`SysAdminDoc/AlarmClockXtreme`  
工作分支：`feature/playback-history-rotation`

## 目標

- **D 每日輪換**：每天有相對穩定的第一首歌曲；依既有 URI 池避免近期重複。分別處理同日重響、貪睡及午夜。
- **S 同次連播**：一場正在響的鬧鐘，在歌曲自然結束時播放下一首；解除、貪睡或被另一鬧鐘搶占後不得再啟動下一首。
- **H 歌曲播放歷史**：記錄實際開始播放、完成、停止與錯誤；同一事件重入不製造重複紀錄；能查看及重播。
- **保留現有鬧鐘可靠性**：精準排程、前景服務、音量、振動、Direct Boot、音訊 fallback、任務式解除、備份等不能因新功能而退化。

## 文件

- [P0-BASELINE.md](P0-BASELINE.md) — 2026-11-11 雲端建置實測紀錄：嚴格驗證缺項，診斷版 675 項測試 1 項失敗，未交付正式 APK。

1. [B-ARCHITECTURE-AUDIT.md](B-ARCHITECTURE-AUDIT.md) — 已讀源碼、已確定事實、未定問題。
2. [B-FLOW-MAP.md](B-FLOW-MAP.md) — 既有呼叫流程及競態接縫。
3. [B-CHANGE-PLAN.md](B-CHANGE-PLAN.md) — 相容的模型／選曲／播放器／事件設計。
4. [B-TEST-MATRIX.md](B-TEST-MATRIX.md) — 逐項測試與完成判準。
5. [B-IMPLEMENTATION-BACKLOG.md](B-IMPLEMENTATION-BACKLOG.md) — Coding Agent 分工、執行順序與提交門檻。

## 明確不做

- 不逆向或修改第三方 ZalJatni／AVG 的 APK。
- 不先支援 Spotify、網路電台或 YouTube 在同次連播內切歌；第一版限制於**本機可讀的 URI 歌曲池**。
- 不在沒有 migration 測試前改 Room schema。
- 不使用正式起床鬧鐘作為自動化測試環境。
- 不把 `STATE_READY`、`onReady`、預定選曲，直接寫成「已播放」。
- 不授權 Agent 推送到 `main`、合併 PR、強推或修改上游倉庫。

## 證據等級

- **SRC**：已從固定 Commit 的源碼讀取；只證明程式寫了什麼，不保證實機正確。
- **DOC**：README 或原專案文件聲明。
- **PROPOSAL**：本次新增設計，還不是程式既有行為。
- **TODO**：尚未讀全所有呼叫路徑、尚未測試或需作者決定。
- **PASS/FAIL**：只有實際執行且具備輸出記錄才可標註。目前測試全部未執行。

## A 路線同步狀態

ZalJatni 的商店文字提及隨機歌曲與歷史／重播；但「能否透過 MediaSession／NotificationListener 讀取曲名、真正播放狀態、下一首能力」仍須 Android 手機實測。不能用 A 路線未證實能力阻擋 B 線只讀與本機歌曲池開發。

## 下一個允許的階段

先讓第二位只讀審查 Agent 核對本資料夾所引程式行號、反例與測試缺口；然後在此分支進行 P0 原專案建置與既有測試，記錄基線。正式修改應從獨立的每日選曲 pure policy 開始。
