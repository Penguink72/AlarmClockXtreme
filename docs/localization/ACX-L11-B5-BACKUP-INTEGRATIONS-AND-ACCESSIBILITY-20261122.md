# ACX L11-5｜備份還原、外部整合與無障礙翻譯候選（2026-11-22）

> 狀態：只有 Node 翻譯候選、差分索引和測試；不是正式 Android 語言資源或已發布 APK。基於 L11-4 SHA 401905f6e83490ef8394016617ca9c24b7763b41 的隔離分支，對應 Draft PR #45 為上游。最終結案以本分支 HEAD 的 CI 成功為準。

## TCL Root／枝／業

Root：依據 B0-T1 的確切英文來源與 L10 的 Binding 線索，針對資料遺失、外部連線與無障礙鬧鐘操作的文字新增繁中和日文候選，保留 G4/G5 獨立語義批准界線。

| 枝 | 本輪新增 | 需要的行為證據 |
|---|---:|---|
| B5-B 備份／還原／加密／Fossify | 76 Key × 2 = 152 候選 | Append 與 Replace 不同；匯入停用鬧鐘與覆蓋都需要真實程式核對 |
| B5-I Webhook／Hue／Health Connect | 78 Key × 2 = 156 候選 | 外部資料傳送、TLS 憑證、權限、F-Droid 功能差異需核對 |
| B5-P 個人化／無障礙／可靠性 | 47 Key × 2 = 94 候選 | 挑戰略過、貪睡比例、取消鎖的操作後果需核對 |
| B5-Q 測試與差分 | 7 項新 Node 測試 | Source preview、格式、去重、舊 XML 保護與無權升格均採 fail-closed |

業：文字定義存在，不代表畫面或 Service 已正確使用；候選翻譯存在，不等於語義經獨立批准。每個候選保留原始英文字面、當期 SourceHash、使用點及未審核狀態。沒有覆寫任何既有 zh-TW／ja Android XML。

## 交付路徑

- localization/candidates/l11-b5-backup-restore-drafts-20261122.json
- localization/candidates/l11-b5-integrations-health-drafts-20261122.json
- localization/candidates/l11-b5-personalization-accessibility-drafts-20261122.json
- scripts/localization/l11-translation-worklist.mjs（新增 batch5 輸入）
- scripts/localization/l11-b5-sensitive-ui-drafts.test.mjs（七項回歸與負向測試）
- .github/workflows/lpg1-g5-translation-pipeline.yml（B5 CI 與完整差分 artifact）

Node CI 生成的 l11-full-translation-queue.json、l11-priority-review.tsv、l11-source-drift-holds.json、l11-b3-source-disposition-suggestions.json 和 l11-summary.md 均可從 acx-b0-full-app-locale-inventory Artifact 取得，來源 SHA 應等於實際 Git HEAD。

## 完整分母與本批目標

| 指標 | 預期值 |
|---|---:|
| 英文基底／可翻譯 Key | 2,471／2,462 |
| 繁中與日文各缺專屬 XML | 1,550 |
| 缺漏 locale 工作列總數 | 3,100 |
| L11-1／L11-2／L11-3／L11-4 候選 | 146／398／314／434 |
| L11-5 新候選 | 402 |
| G6 可復用但未審核候選 | 84 |
| 累計有候選 | 1,778 |
| 尚無候選 | 1,322 |
| 原有繁中日文 XML 定義 | 1,824（本輪不改） |
| 獨立語義批准／新增正式 XML／APK | 0／0／0 |

上表數字以最後同一 SHA 的 CI 記錄為驗收依據；候選進度 1,778／3,100 約 57.4%，並不是正式 App 翻譯覆蓋率。

## L12 前置語義核對

1. settings_append_alarms、settings_replace_alarms、settings_import_disabled：分辨附加、覆寫與停用狀態，核對資料庫交易與警告。
2. settings_plain_backup_description、settings_encrypted_backup_description、settings_private_values_plain：明確說明明文 JSON、AES-256 與私人資料暴露。
3. settings_keep_integrations_hint：備份可能包含連往伺服器的事件端點及 Guardian 聯絡人，不可省去操作後果。
4. settings_hue_forget_message、settings_enable_webhook_description：憑證固定及事件名不得由譯者自行變更。
5. settings_health_read_sleep_description：核對只讀取睡眠資料、Health Connect 授權與 F-Droid 版本實際差異。
6. settings_challenge_bypass_description、settings_repeat_missed_description：需和鬧鐘服務的無障礙保護、再次響鈴語義一致。
7. Format token、百分比、精度與 Android XML 轉義仍需要 L12 專用 QA；通過 L11 源碼檢查並不代表 XML 可以直接發布。

## 下一步及退出門檻

剩餘 1,322 筆無候選工作列仍需分批翻譯或有證據的 fallback 分類。L12 應並行建立操作/安全/語義的獨立審核；之後由 L13 正式接線到 Compose、通知與 Service，L14/L15 才能進行 APK 驗收。

本輪只完成候選文件、可重建差分、Node CI。沒有修改音樂庫、鬧鐘排程、Room、Media3、AlarmService 或正式 XML；Draft PR 不代表合併／發布。
