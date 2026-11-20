# TCL | S0 正式來源更正與譯文失效狀態（2026-11-20）

## 施工背景
- 基於已完成 T2 的 I0：2b0d2223883d7d2fa604ef92b941342938812a82。
- S0 隔離分支：feature/p31-s0-canonical-source-refresh-20261120；不修改 main。
- 修正 music_playlist_integration_pending：Room v28 已存在，但 repository == null 顯示的舊英文仍稱等待 Room v28 整合。
- Android 英文、繁中、日文主資源已使用實際儲存不可用時的描述。中日翻譯僅為未審核候選。

## 哈雜湊追蹤與歷史快照
- LP0 200 Key 與 LP1 Debug assets 保留 lp0-200-20261118 歷史目錄，不覆寫原始來源或語義審查證據。
- 舊來源 Hash：sha256:93808f158152ec3e5592054d367e4c93ff848ad3ede93c9c79efd5f51330575d。
- 新正式來源 Hash：sha256:332b74a082326a3979132d74cf4d00f0632c09f6f98381392a4d4c8308bf3ed2。
- localization/governance/s0/source-amendments.json 保留原始／現行三語文案、原新雜湊、兩語 STALE_REVIEW_REQUIRED 與零核准。
- LP0 對照檢查僅允許這個單一明示來源差異；其他 Key 漂移照常拒絕。
- G4 source-defects 原始缺陷仍 OPEN 且 releaseBlocking=true，因為兩份舊譯文尚未完成獨立語義核准與新目錄遷移。

## 生成與驗收
- G5 preview 仍是歷史 200-key 語言包，現以 staleCanonicalSourceKeys 明示仍有過時文案，不能當成目前 App 的已核准完整翻譯包。
- G5 即使將舊缺陷狀態人工改成 RESOLVED，若新來源兩語審核未完成，仍必須拒絕正式包。
- Node-only S0 驗證：來源 Hash、三語正式 XML、歷史翻譯、格式參數、開啟的發布阻擋，以及 8 組反向變異測試。
- T2 必須以此隔離分支 HEAD 的 S0、LP0、LP1、LP-G、Android F-Droid Debug CI 為準。
- T3 手機顯示未經驗證；Q 內容獨立核准數仍為 0。

## 後續
- G6：建立新有效來源目錄並審核兩語翻譯，不可把舊 sourceHash 的批准搬運。
- LP2-A：以可匯入語言包進行正式 Android UI 的動態語言切換。
- 不動 Room schema、AlarmService、AlarmManager、Media3 或播放歷史。
