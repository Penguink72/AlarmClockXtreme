# ACX L11-4｜可見介面優先翻譯草稿與殘留分層（2026-11-22）

> 性質：**未經獨立語義批准的翻譯候選和 Node 產物**。本次只改候選 JSON、Node 產生器／測試、CI 和本文；沒有修改 Android `values-*`、Kotlin Runtime、鬧鐘排程、音樂庫、Room、Media3、G4/G5 批准帳本，也沒有建立 APK。文件建立在 L11-3 成功 SHA `4ea7cc374ce452f601865a32d68e54663bc12d6e` 之後；如 HEAD 再變更，精確 CI 證據要重查。

## 1. TCL root、枝、業

**Root**：在 L11-3 已把剩餘項目分層之後，繼續優先補足**設定鬧鐘、快速建立鬧鐘、首頁／天氣、響鈴挑戰錯誤**等使用者可見路徑，不為提高統計量而自動翻譯技術代碼或把模型候選寫入正式 XML。

| 枝 | 新產物 | 本片邊界 |
|---|---|---|
| L11-4-A 鬧鐘編輯與排程 | 72 Key × 2 = 144 locale rows | Time zone、輪班、太陽時刻、Guardian 緊急通知需後續行為核對 |
| L11-4-B 首頁、天氣、行事曆 | 64 Key × 2 = 128 locale rows | AQI、預報、資訊來源和過期資料需語義審查 |
| L11-4-C 快速模板、可靠性準備 | 47 Key × 2 = 94 locale rows | 模板時間、NFC、裝置感測器與權限提示仍屬候選 |
| L11-4-D 響鈴語音／手寫錯誤 | 34 Key × 2 = 68 locale rows | 文字備援、錯誤狀態和占位符保留；操作仍待 Android 實測 |
| L11-4-E 全量佇列與 CI | 結合 L11-1～L11-4、G6；6 項新增測試 | 不修改既有 XML、不得升格為 APPROVED |

**Karma**：Source String 有穩定 Key 與雜湊，不代表其多個 Context 都相同。「有候選」不是「完成譯文」，「沒有靜態 Binding」不是「未使用」，「URL 像英文」也不是「已批准 fallback」。

## 2. 實體檔案與可重建產物

| 檔案 | 用途 |
|---|---|
| `localization/candidates/l11-b4-alarm-editor-drafts-20261122.json` | 72 Key，明確保留格式參數和太陽偏移正號 |
| `localization/candidates/l11-b4-dashboard-drafts-20261122.json` | 64 Key，首頁氣象、位置、行事曆、AQI 候選 |
| `localization/candidates/l11-b4-templates-readiness-drafts-20261122.json` | 47 Key，快速模板和就緒診斷 |
| `localization/candidates/l11-b4-alarmfiring-errors-drafts-20261122.json` | 34 Key，語音／手寫／NFC／條碼錯誤與 fallback |
| `scripts/localization/l11-translation-worklist.mjs` | 新增 B4 輸入，繼續沿用完整 B0-T1、L10、G6 和既有 L11 來源版本判定 |
| `scripts/localization/l11-b4-visible-ui-drafts.test.mjs` | 六項來源、交付、格式、操作及未授權負向測試 |
| `.github/workflows/lpg1-g5-translation-pipeline.yml` | 新分支接線，重建 L11/B3 既有 JSON、TSV、Markdown Artifact |

```bash
node --test scripts/localization/l11-translation-worklist.test.mjs
node --test scripts/localization/l11-b2-draft-batches.test.mjs
node --test scripts/localization/l11-b3-draft-disposition.test.mjs
node --test scripts/localization/l11-b4-visible-ui-drafts.test.mjs
node scripts/localization/l11-translation-worklist.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
```

結果仍寫入 `l11-full-translation-queue.json`、`l11-priority-review.tsv`、`l11-source-drift-holds.json`、`l11-summary.md`、`l11-b3-source-disposition-suggestions.json` 與 `.tsv`。每份資料持有 sourceSha 或 catalogueRevision；不要把以前 Run 的數值冒充最後 HEAD。

## 3. TCL 帳本：精確缺漏分母與預期候選

沿用 L11-3 確認的來源基準：2,471 base Key、2,462 translatable、繁中缺 1,550／日文缺 1,550 個專屬 XML，缺漏列共 **3,100**。

| 類別 | 本次累積預期 |
|---|---:|
| L11-1 候選 | 146 |
| L11-2 候選 | 398 |
| L11-3 候選 | 314 |
| **L11-4 新候選** | **434** |
| G6 來源與格式相容的舊未審核候選 | 84 |
| **可供審核的累積候選** | **1,376** |
| **仍無候選** | **1,724** |
| 受保護的原有繁中／日文 XML | 1,824 |
| 模型候選的獨立語義批准／正式新 XML 修改 | 0／0 |

上表為同來源、全部草稿通過機械檢查時的**預期分母**；以最後 HEAD 的 CI `L11_TRANSLATION_DRAFTS` 和 `L11_B3_DISPOSITION_CANDIDATES` 為準。候選比例 1,376 / 3,100 ≈ 44.4%，不是產品完成率。

## 4. 操作與呈現風險：L12 待驗

1. `alarm_edit_guardian_direct_sms`、`alarm_edit_guardian_call_dialer`：緊急簡訊／通話與 Android 權限有重大操作後果，翻譯需對照真實 F-Droid 差異與 onClick／Service 路徑。
2. `alarm_edit_fixed_zone_hint`、`alarm_edit_shift_hint`、`alarm_edit_solar_after`：固定時區、輪班、相對日出日落的排程語義需與實際 scheduler 對照；保留 `+%1$d` 字面正號及格式參數。
3. `alarmlist_release_to_delete`：刪除的是該鬧鐘項目，而非手機音訊；這與 P1～P3 音樂來源的「移出清單」不同。
4. `dashboard_air_quality`、`dashboard_hazardous`、`dashboard_pollen`：天氣與 AQI 分級需比對數據來源定義，不得只由中文意譯決定級距。
5. `template_early_bird_description`、`template_power_nap_description`：模板時間、預設挑戰與實際建立的草稿須一致。
6. `alarmfiring_voice_heard`、`alarmfiring_too_slow_ntry_again`、`alarmfiring_complete_2`：須檢查 Android XML 雙引號／`\\n` 轉義與 `%%`，並實測語音失敗時能正常切換文字備援。
7. 日文長句、無障礙朗讀、低解析度畫面與通知裁切留給 L14；Node 格式測試不能宣稱版面完成。

## 5. 下一個真正可用產品的停止條件

**L11 仍未完成**。剩餘部分須由 L11 後續批次處理，將應翻譯、刻意保留原文、映射未知、結構特殊分開，持續依使用點與來源核對。

但此時不能只增加模型翻譯數。應**並行啟動 L12**，使用 G1 術語及實際 Kotlin 行為核對高風險項，建立誰能核准哪筆來源版本的紀錄；然後由 L13 將符合條件的翻譯接入現有 LocaleCoordinator、Compose、Service、Notification。L15 必須拿同一 SHA 的 Android build／必要實機證據才能交 APK。

**L11-4 的完成標準只限：217 Key×2 草稿與來源逐項對照、全量佇列可重建、Node CI 通過、正式 XML 一筆不改。**
