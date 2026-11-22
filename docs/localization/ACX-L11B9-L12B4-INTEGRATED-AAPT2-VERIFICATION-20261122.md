# ACX L11-9＋L12-4｜翻譯補譯與實際 aapt2 隔離編譯同版驗證（2026-11-22）

> 此分支基於 [L11-9 Draft PR #55](https://github.com/Penguink72/AlarmClockXtreme/pull/55) 的成功來源 SHA `431456877604738c4a57c7626558445354049b89`，將 [L12-4 Draft PR #56](https://github.com/Penguink72/AlarmClockXtreme/pull/56) 隔離資源編譯的程式、CI、警示追蹤與文件接入同一工作樹。原有兩個獨立 PR 不會反向改動。

## TCL 統一證據鏈

| 層級 | 當期應檢查內容 | 權限邊界 |
|---|---|---|
| B0/L10 | 英文 base 2,471、translatable 2,462；繁中/日文各缺專屬定義 1,550；總 3,100 | 原本 1,824 既有 locale XML 不可覆寫 |
| L11-1～9＋G6 | 2,680 舊候選＋B9 新 306＝**2,986 筆**，兩語各 **1,493**，剩餘 **114** | 僅模型初稿，0 獨立批准 |
| L12-1 | 2,986 筆 SourceHash、TranslationHash、Context、格式參數、G1 術語與風險重生 | 機械 PASS 不是語義批准 |
| L12-2 | 14 組具檔案版本的 Kotlin／DAO 靜態行為探針 | 不等同實機觸發、HTTP 傳輸或鬧鐘排程真實成功 |
| L12-3 | 2,986 筆 XML 預覽、來源追溯、獨立審核空白帳本，`--mode release` 必須拒絕 | 不寫入正式 XML |
| L12-4 | 生成獨立 `build/localization/L12_B4_AAPT2_ISOLATED_UNAPPROVED/res`，由**真實 Android SDK aapt2 compile** 生成 ZIP、SHA256、內容清單及警示追蹤 | 只證明隔離語系草稿被 compiler 接受；非完整 Gradle App compile／實機驗證，非發行證據 |

## CI／Artifact

所有 Node 測試、來源重建、L12-3 發行拒絕及 L12-4 的 Android `aapt2 compile` 必須使用此分支同一個最後 Git SHA。輸出：

- `acx-b0-full-app-locale-inventory`：來源定義與缺漏表；
- `acx-l12-preliminary-qa-review-evidence-not-approved`：逐筆格式／語義審查線索；
- `acx-l12-b2-code-behavior-evidence-not-approved`：14 組靜態探針；
- `acx-l12-b3-android-xml-preflight-not-release`：正式發行 gate 和完整審核交接；
- `acx-l12-b4-isolated-aapt2-unapproved-review`：實際 aapt2 編譯出來的 ZIP，候選檔案雜湊與警示。

預計不變更 Android App 正式 XML、Room／AlarmService／Media3，不發行 APK，也不授予任何 G1/G4 或獨立語義批准。

## 未完成枝

1. 剩餘 **114** 筆 locale 缺漏需要逐項處置（其中可能包含 URL、代碼或專有名稱；不能只為滿足數字就自動翻譯）。
2. L12 格式／翻譯警示須人工逐條處置；aapt2 成功不會取消需審狀態。
3. L13 實作受保護正式資源生成器與使用者授權，經審查完成的多語檔才可寫入正式 App。
4. 完整 Android Gradle build、通知／服務／鬧鐘可靠性及實機 UI 路徑、音樂資料夾來源和播放紀錄為 L14/L15 驗收。

**結案條件**：同版 CI 全綠，隔離 aapt2 編譯產物存在、無新正式 XML 寫入且正式發行命令持續拒絕未核准草稿。
