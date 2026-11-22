# ACX L11-8 ＋ L12-3｜同來源版本整合驗證（2026-11-22）

> 此整合分支基於 [L11-8 Draft PR #52](https://github.com/Penguink72/AlarmClockXtreme/pull/52) 最後成功 SHA `634d2d18ecf9538b589c7f262f50f901fa813f3d`，納入 [L12-3 Draft PR #53](https://github.com/Penguink72/AlarmClockXtreme/pull/53) 的機械檢查、獨立審核空白帳本與發行拒絕工作。沒有修改正式 Android XML、Kotlin、鬧鐘排程、音樂資料庫、G4/G5 原有核准紀錄或 APK。

## TCL 同版證據鏈

L11-8 增加 187 個 Key×2 語＝374 筆草稿，不能直接沿用 L12-3 獨立分支的舊分母 2,306。此整合分支會以**同一 Git SHA**重新建構：

- B0-T1 英文基底 2,471、可翻譯 2,462 Key、兩個語系各缺專屬 XML 1,550，總分母 3,100。
- L11-1～8 與可復用 G6 草稿累計應為 **2,680**，其中繁中與日文各 1,340；剩餘無候選應為 **420**。
- L12-1 機械格式、Unicode、術語、Binding 使用點與審核來源版本完整重算，不沿用舊批次的 hardcoded numbers。
- L12-2 原有 14 個跨 12 程式檔的靜態操作探針維持有效，但其輸出必須重新綁定當期版本與新增候選，不等於 Android 真實運作驗證。
- L12-3 為當期全部 **2,680** 個候選生成 `l12-b3-resource-preflight.json`、`l12-b3-review-queue.tsv`、`l12-b3-xml-review-examples.json`、`l12-b3-gate-summary.md`。
- 預覽片段只用於人類審核，未執行 aapt2，不能自動複製至 `app/src/main/res`。
- CI 必須明確測試 `--mode release` 在未有合法獨立審核、G1/G4 通過與 Android 編譯證據時**失敗**。

## 合流後仍未完成

1. 剩餘 420 筆 locale 工作列中的自然語言翻譯及 10 筆候選英文 fallback 的分類。
2. Android XML 引號、特殊字元、百分比、plural/array 的完整 AAPT2 編譯與 UI/通知/Service 多語驗證。
3. 對高風險「解除鬧鐘、貪睡、備份替換、清除紀錄、音訊檔案／資料夾來源、相機／麥克風挑戰」逐筆核對並由適格獨立審核者授權。
4. L13 正式語系接線與受保護的 XML writer、L14/L15 Android 實機／APK 驗證。

本輪工程完成僅表示來源鎖定、審核產物、拒絕非法發行的程式及**同 SHA CI 成功**。沒有任何模型自動批准，沒有正式 XML 或新 APK。
