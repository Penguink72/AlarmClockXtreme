# ACX L11-9｜新聞、YouTube、晨間資訊及日常 UI 雙語候選（2026-11-22）

> 分支基於 [L11-8＋L12-3 整合 PR #54](https://github.com/Penguink72/AlarmClockXtreme/pull/54) 成功來源 SHA `aee0b893e022f889661b3ddb3845a824ba3cbf64`。所有內容為 **MODEL_PRELIMINARY_NOT_APPROVED**，不更動正式 Android XML、Kotlin、Room、Media3、鬧鐘排程、既有音樂庫或 APK。與 L12-4 隔離 aapt2 編譯／品質警示查核平行。

## TCL 工作實績

| 枝 | 英文 Source Key | zh-TW＋ja 未審核 locale 草稿 | 主要語義風險 |
|---|---:|---:|---|
| B9-N｜新聞 RSS／來源／網路錯誤 | 37 | 74 | URL、TL​​S 失敗、timeout 與快取/正式新聞分開 |
| B9-Y｜YouTube 搜尋／音訊／下載器 | 33 | 66 | 音訊串流、下載引擎及網路錯誤不可任意保證功能 |
| B9-D｜晨間摘要、日曆、輪班、OEM 及版本導覽 | 53 | 106 | 日期、輪班名稱、系統背景限制與實際程式行為 |
| B9-W｜棋類挑戰、widget、統計、快速控制 | 30 | 60 | 棋譜文字、格式位置參數及刪除／重設方向 |
| **總計** | **153** | **306** | 保留高風險獨立審核 |

## 同源分母與完成定義

- B0-T1 英文 2,471 Key、可翻譯 2,462；zh-TW／ja 各缺專屬 XML 1,550，合計 **3,100** 筆。
- L11-1～8 和復用 G6 已有 **2,680** 筆候選；B9 加入 **306**，累計候選 **2,986／3,100**，仍待候選 **114**。
- 既有兩語 **1,824** 筆 XML 專屬定義不覆寫；正式翻譯批准 **0**、正式 XML 修改 **0**。
- 英文 sourcePreview 精確綁定，format token 集合相同、沒有重複新舊批次 Key、沒有假造批准。
- 舊 B8 測試 fixture 對 B9 切片隔離，當期 B9 與 L12-1／L12-3 驗證則更新完整分母為 2,986（各語系 1,493）。
- 此進度是候選產出，不是 App 翻譯完成率。要以最後 HEAD 同 SHA CI 結果與 Artifact 數據為準。

## 未解決的實際內容

1. 114 筆尚無候選中包含 URL、硬編碼專有標記、占位符與可能須保留原文的項目；不應為湊滿 100% 而無依據強行翻譯。
2. 保留英文的短語（如 YouTube、BBC、NPR、DuPont、Panama）可能是合法名稱或未完成翻譯，仍要審核其上下文與呈現方式。
3. 新的網路錯誤譯文不能保證網路正常、影片可下載或任何服務端 API 目前可用。
4. 晨間語音日期及輪班名稱需 Android 系統語系、時間格式與 UI/無障礙驗證。
5. L12-4 必須在同一 SHA 上重新編譯 B9 的 XML **隔離預覽**，不可以把語法編譯成功當成獨立語義批准。

檔案：
- `localization/candidates/l11-b9-news-drafts-20261122.json`（37）
- `localization/candidates/l11-b9-youtube-drafts-20261122.json`（33）
- `localization/candidates/l11-b9-daily-context-drafts-20261122.json`（53）
- `localization/candidates/l11-b9-widgets-chess-drafts-20261122.json`（30）
- `scripts/localization/l11-b9-news-media.test.mjs`：6 項來源與分母回歸測試
- `scripts/localization/l11-translation-worklist.mjs`：B9 注入，保留 B0/L10/L11/G6 版控來源

**下一階段**：L11-10 經具體原文檢查後對剩餘 114 筆逐條分類、L12 語義差分與審核警示處置、L13 正式資源接線，Android aapt2/實機 APK 均另驗收。
