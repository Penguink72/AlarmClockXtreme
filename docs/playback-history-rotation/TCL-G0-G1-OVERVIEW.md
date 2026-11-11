# TCL｜G0＋G1 現行任務綱要

**T0 已規劃／程式尚未修改。2026-11-11。** Repo：Penguink72/AlarmClockXtreme；分支：feature/playback-history-rotation。需求權威：[CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md)。

## ROOT-ALARM-01

G0：GitHub Actions 嚴格依賴驗證下取得可下載 F-Droid Debug APK、留存真實測試基線。

G1：手機與 microSD **至少 3,000 首**可存取音訊，一次索引到統一庫；每首播完再獨立隨機抽一首（可立即重複）；響鈴顯示真實歌名、每首開始即追加日期／順序／歌名，解除後按日期查看。

## 根—葉

| 葉片 | 交付成果 | 通過條件 |
|---|---|---|
| G0-L1 | 嚴格可重現編譯 | assembleFdroidDebug 真實 exit code 0、不停用依賴驗證 |
| G0-L2 | APK artifact | 有可下載且非零的 APK，附 commit/雜湊 |
| G0-L3 | 單元測試基線 | 原版已知失敗與新增失敗分開，不冒充全部通過 |
| G1-L1 | 大量來源索引 | MediaStore 自動掃手機＋SD，必要時 SAF 授權目錄遞迴補掃；不逐首勾選 |
| G1-L2 | 持久統一歌庫 | 至少 3,000 筆可重開讀取的 URI／歌名；與原 20 首欄位隔離 |
| G1-L3 | 逐首獨立抽取 | 每次自然播完再 random.nextInt(N)；A→A 合法，不預洗牌 |
| G1-L4 | 目前歌名與簡易歷史 | 真正開始才記，按日期／時間／順序可查；無結束狀態 |
| G1-L5 | 停止、備援、相容 | 解鈴／貪睡／搶占後無下一首；失效音源備援；舊鬧鐘不變 |
| G1-L6 | APK＋手機驗收 | GitHub 可下載 G1 APK、白天手機鎖屏及 SD 實測 |

**G1-L4 已改為 G1 當前必做，不再延後至舊 G2。**

## 根—業

K0 依賴校驗假綠燈；K1 SD 授權或漏索引；K2 不得在響鈴當下掃 3,000 首；K3 READY 不是正在播；K4 舊 callback 解鈴後又切歌；K5 新模式不可破壞原有單曲。

## 順序及證據

G0 嚴格建置→ APK＋基線報告→ G1 自動索引→持久歌曲庫／開關→逐首重新抽歌→曲名＋只追加歷史→競態與備援回歸→新 APK→白天實機。

T0 本輪文件已規劃；T1 程式修改 NOT DONE；T2 嚴格 G0 NOT PASS；T3 手機 NOT RUN。

舊預洗牌、20 首多選、每日輪換、完整結果歷史均延後：[DEFERRED-DESIGNS.md](DEFERRED-DESIGNS.md)。工作單：[G0-IMPLEMENTATION-PLAN.md](G0-IMPLEMENTATION-PLAN.md)、[G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md)。