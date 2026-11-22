# ACX L12-4｜實際 aapt2 編譯隔離語系候選與格式警示追蹤（2026-11-22）

> **此階段只驗證隔離資源的 Android 格式可編譯，不是語義審核、正式 App 資源或 APK。** 從 [L11-8＋L12-3 整合 PR #54](https://github.com/Penguink72/AlarmClockXtreme/pull/54) SHA `aee0b893e022f889661b3ddb3845a824ba3cbf64` 建立，與 L11-9 平行。沒有修改正式 `app/src/main/res`、AlarmService、資料庫、音樂庫或 G4/G5 批准帳本。

## TCL 機制及證據

| 枝 | 此階段實作 | 不得過度推論 |
|---|---|---|
| L12-4-I：獨立 staging | 從 B0/L10/L11/L12-3 同源資料生成 `build/localization/L12_B4_AAPT2_ISOLATED_UNAPPROVED/res/values-b+zh+Hant+TW/strings.xml` 與 `res/values-ja/strings.xml` | 只包含模型初稿，不包含 App 完整資源，**不允許複製至正式 App** |
| L12-4-A：真實 Android compiler | GitHub Actions 取得 Android SDK build-tools `aapt2`，執行 `aapt2 compile --dir ... -o .../unapproved-candidate-resources.zip`，保存編譯 ZIP、SHA-256、內容列表 | 只證實**隔離 candidate resources 編譯成功**，非完整 App 編譯、不等於 UI 及執行期正確 |
| L12-4-W：32+ 警示追蹤 | 延續 L12-3 的未翻譯英文與百分比警示，再納入 XML 引號／特殊字元，對應來源雜湊、譯文雜湊、ReviewRevision、尚未核准狀態 | 即使 aapt2 PASS，警示**不會自動消失或獲批准** |
| L12-4-X：權限與負向測試 | 重複 Key、錯誤版號、格式不符、未授權批准全部 fail-closed | 沒有簽名、G1/G4 獨立審核、受保護發行流程與 L13 writer |

## 已交付的工程項目

- `scripts/localization/l12-b4-aapt2-staging.mjs`：可重生、僅在 `build/` 中的候選 XML 和風險警示表。
- `scripts/localization/l12-b4-aapt2-staging.test.mjs`：六項來源追溯、候選分母、獨立審核與負向斷言。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：新增真實 Android aapt2 compiler step 與隔離 artifact。
- CI Artifact `acx-l12-b4-isolated-aapt2-unapproved-review`：兩語 staging XML、候選 compiled zip、SHA-256、zip list、`warning-triage-review.json`／TSV、版本 manifest。

**獨立分支第一次實際 compiler 成功證據**：成功 Run [#45033486040](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/45033486040)，SHA `8f5e256032cf50daf316ceaff2e6d760df70efe4`，Android aapt2 2.20-15087165，隔離候選 **2,680** 筆（繁中/日文各 1,340）；結構硬阻擋 0；警示追蹤 36 列（未翻英文 22、百分比警示 12、XML 特殊字元 4，類別可能重疊），獨立核准 0。

## L11-9 同版後重驗

當前獨立 L12-4 分支使用 2,680 筆 L11-8 候選。**必須與 L11-9 的 153 Key／306 locale 候選合流後，在同一 HEAD SHA 上重新執行 compiler。** 同版候選應增至 2,986、剩餘無候選 114；文字不變的預期警示數可能增加，不應沿用舊值。

## 下一步與完成定義

1. 解讀並逐條審查原文未變（專有詞／技術字串／真正未翻譯），對每一筆原因給出合理的 reviewer decision，尤其％格式與 Android `getString(...)` 的實際使用。
2. 確認 XML staging 僅編譯成功，不得聲稱已完成全 App 正式資源合併、實機可靠性或動態語言包匯入驗證。
3. L13 另需受保護的正式 writer 與明確授權：只接受經證實獨立人類審核、版本匹配、G1/G4 gate 和 Android compiler 全量證據的項目，並重新跑正式 App Gradle Android 資源編譯。
4. L14/L15 須在真實裝置驗證鬧鐘響鈴、音樂播放紀錄、檔案來源及多語畫面交互，不可省略重要風險測試。

**此片工程結論：** 成功取得真正 aapt2 **隔離編譯**證據、保留警示、完全不授權發行。
