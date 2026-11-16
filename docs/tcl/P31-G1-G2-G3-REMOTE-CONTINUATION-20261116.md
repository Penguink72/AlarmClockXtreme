# P3.1 → P2-U5｜TCL 遠端接續契約（2026-11-16）

基準：PR #13，`a45536b047528e13133ae990702ad4c9d64619ae`。既有 [施工帳本](P31-TO-P2-U5-CURRENT-DELIVERY-LEDGER-20261116.md) 與 [阻斷分析](P31-U4-B2-RELIABILITY-ARCHITECTURE-BLOCKER-20261116.md) 均保留。本文件記錄新的接續閘門，不推翻既有已驗收能力。

## 證據
- T0：規劃／需求。T1：已推送程式。T2：CI。T3：指定 Android 實機使用結果。
- `a45536b` 基準 CI Run 43859943907：3/3 jobs，788/788 單元測試；APK v159/v160；Room v28。
- S1 亮屏 10 分鐘貪睡仍缺 T3 成功證據。B2-0 10 秒送達測試是獨立 Onboarding Receiver/Activity 與系統鈴聲，**不涵蓋正式 AlarmService／Media3／貪睡**。U4 同曲續播／換曲未完成。

## G1：S1 貪睡可靠性（P0）
1. 使用現有 S1-R 事件時間線串接 `alarmId/fireId/scheduledAt`：排程已登記、Receiver 收到、Service 啟動、Media3 實際起聲、互動螢幕狀態及漏響補救。
2. 保留原問題「右滑貪睡 10 分鐘，螢幕持續亮起」真機驗收；關屏後才再響不能算成功。
3. 不在未取得 T3 前宣稱故障已修復；重要起床鬧鐘使用手機原生備援。

## G2：B2 正式音樂鬧鐘端到端測試（P0）
- B2-A：建立獨立 `testSessionId`、fireId／PendingIntent 命名空間、TTL、取消及重啟恢復；不修改真實鬧鐘 row、播放歷史與統計。
- B2-B：測試走正式 Receiver→AlarmService→Media3 音源路徑，隔離 Guardian、webhook、第三方整合。
- B2-C：正式起聲 → 一分鐘貪睡 → 再次起聲 → 解除，捕捉系統排程、Service 與音訊證據；負例涵蓋程序死亡、亮屏／息屏、權限與來源失效。
- **B2-0 已完成 ≠ B2-production 完成**。不以現有「10 秒送達」按鈕冒充完整音樂鬧鐘測試。

## G3：U4 兩種貪睡音樂行為（待 G1可靠性）
- `RESUME_INTERRUPTED`：記下真正播放過的 URI、實際毫秒位置，重建後驗證授權與 READY，再 seek；不支援 seek 時有明確有界備援。
- `NEXT_DIFFERENT`：下一首 URI 需不同於中斷前，除非候選唯一；仍保留原三種選曲策略。
- 快照至少綁定 alarmId、sourceFireId、URI、positionMs、playlistId、mode、expiresAt，並避免跨鬧鐘／舊命令污染。
- 第一個安全切片只可新增播放器「讀取目前位置」等底座與測試；**不得把這種底座報稱 U4 已完成**。每鬧鐘設定與備份、非破壞性 Room v29 Migration（或有證據的 sidecar）須另驗。
- G1 T3 未成立前，不重寫正式 AlarmService 選曲與貪睡路徑。

## G4／G5 待完成
- U1.2：搜尋 4,935 首→批次加入清單→套用鬧鐘→儲存讀回→真機驗證。
- U3：首頁五秒設定須量測實際排程完成，不等同於存在快捷鍵。
- L1-B 三語覆蓋剩餘核心介面；U5 OEM 系統音只可標「疑似」，一鍵排除可復原，備援鈴聲不可被排除。
- 已有功能及歷史資料須保留；不改 main、不 force push、不重設 Room v28 或既有簽章。

## 每個提交最低回報
工包 ID、父 SHA、修改檔案、提交 SHA、CI Run、通過／失敗、T3 待驗範圍、下個可執行切片。文件與程式權威分開，Agent 自述不替代遠端實證。
