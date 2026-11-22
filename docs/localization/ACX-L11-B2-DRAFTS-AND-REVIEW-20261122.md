# ACX L11-2｜繁中與日文優先翻譯候選（2026-11-22）

> **性質：設計與翻譯候選，並非已發行譯文。** 本分支接在 L11-1（L10 的後繼）之上，所有變更限於候選 JSON、Node 產生器與測試、CI、文件。未修改 Android 內建 XML、Kotlin、鬧鐘排程、音訊功能或正式審核帳本；PR 合併與發行仍需另外判定。

## 0. 精確來源與異步位置

- 上游 L11-1 commit：`8e4ca958fcfa521e74801a64cd8620a6c6f62baa`，在 [PR #42](https://github.com/Penguink72/AlarmClockXtreme/pull/42)。
- 首次成功的 L11-2 Node CI：[Run #44996356236](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44996356236)，SHA `05f60bc84372e9f462fae7ba4b9a9af35fb419b7`，整體成功，L11-B2 新增六項測試 6/6。
- 前次失敗 [Run #44996323271](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44996323271) 是測試誤用不存在的 TSV 標題；已改驗 `candidate_text`，保留失敗歷史。
- 此後如有新提交，需依最後 HEAD 的 CI 結果驗證；成功 Run 不可移植到另一 SHA。

## 1. TCL 結構

**根：** 用真實英文 Source、B0-T1 的 SourceHash、L10 的 Binding/Context 線索，依風險與使用頻率分批產出繁中、日文候選。未經核准不得寫入正式 Android 資源。

| 枝 | 本輪新增 | 約束 |
|---|---:|---|
| L11-2-A 設定、可靠性、通勤、診斷與隱私 | 108 Key × 2 = 216 列 | 既有 XML 與 G6 候選均不覆蓋 |
| L11-2-B 通知、鬧鐘編輯、解除挑戰 | 91 Key × 2 = 182 列 | 格式參數與操作後果需再審 |
| L11-2-C 多批次候選合併 | 與 L11-1 146 列、G6 可復用 84 列合併 | 同 Key 重複拒絕；跨來源不冒用審核 |
| L11-2-D Node CI 負向測試 | 新增 6 個 | Source 漂移、format 遺失、覆寫、虛構批准應被拒絕 |
| L11-2-E 其餘佇列 | 2,472 locale 工作列仍無候選 | 保留未解、繼續 L11-3 |

**業：** 候選新增不代表 app 已顯示。路徑推測的 Surface 不是經執行驗證的操作；未經查核的翻譯不代表人工語義批准，更不能用其修改媒體來源和鬧鐘行為。

## 2. 產物

- `localization/candidates/l11-b2-settings-drafts-20261122.json`：設定、可靠性相關 108 Key。
- `localization/candidates/l11-b2-alarm-notification-drafts-20261122.json`：通知與鬧鐘編輯相關 91 Key。
- `scripts/localization/l11-translation-worklist.mjs`：新增 L11-B2 批次輸入，同時保留 L11-1 和來源相容的 G6 初審候選，禁止重複及自動批准。
- `scripts/localization/l11-b2-draft-batches.test.mjs`：六項真實來源、格式、變更及不升格負向測試。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：新分支觸發及測試，生成的完整工作表上傳既有 B0 Artifact。

生成結果仍為 `l11-full-translation-queue.json`、`l11-priority-review.tsv`、`l11-source-drift-holds.json`、`l11-summary.md`。以下指令可重建：

```bash
node --test scripts/localization/l11-translation-worklist.test.mjs
node --test scripts/localization/l11-b2-draft-batches.test.mjs
node scripts/localization/l11-translation-worklist.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
```

## 3. 當前完整覆蓋分母

| 指標 | 目前數量 |
|---|---:|
| Base Key | 2,471 |
| 可翻譯 Key | 2,462 |
| 繁中缺專屬 XML／日文缺專屬 XML | 各 1,550 |
| 缺翻譯的 locale 工作列總數 | 3,100 |
| L11-1 新初稿 | 146 |
| L11-2 新初稿 | 398 |
| G6 來源／格式相符、可復用但未審核候選 | 84 |
| **累計可供審核候選** | **628** |
| **仍無候選** | **2,472** |
| 原本已有的專屬 XML（未被覆寫） | 1,824 |
| 獨立語義批准／新正式 XML | 0／0 |

這些數值只適用於上述基準分支／成功 Run。現有 XML 定義數與新增候選數不能混為正式 App 可見翻譯覆蓋率。

## 4. 審核重點與已知限制

1. `notif_database_downgrade_text`：還原資料來自較新版、鬧鐘未保留與排程消失的操作後果，需對照實際資料庫降版回復處理。
2. `alarm_edit_remove_challenge`：只移除鬧鐘挑戰列表中的一項，不能誤譯成刪除手機檔案。
3. `settings_pause_alarms` 與 `settings_clear_diagnostics_message`：需分別核對是暫停鬧鐘或清除診斷事件，不能帶出其他資料消失的暗示。
4. `alarm_edit_challenge_speed_description`：英文 `wpm` 指每分鐘單字數，繁中譯文已修正為「每分鐘至少 15 個單字」；發布前仍需依產品實際判定方式再核。
5. 日文長句、通知截斷、不同 UI 元件寬度屬 L14 專項，Node CI 並未驗證版面。
6. G6 的舊候選只在當期英文 Source 與占位符契約相符時被復用，未獲正式批准。

## 5. 後續分片

- **L11-3**：從仍無候選的 2,472 筆中，優先處理目前 L10 能定位的高頻鬧鐘、音樂、解除與設定項；將 SHOULD_TRANSLATE、INTENTIONAL_FALLBACK、NON_TRANSLATABLE、UNKNOWN 分別列出。不要因翻譯率而強行翻專有名詞或代碼。
- **L12**：選定具可靠來源、術語審核、格式 QA、操作驗證與獨立授權的候選，再生成內建 XML 與符合 V1 的語言包。自訂包的驗證與官方核准須分開。
- **L13**：以既有 B0-L1、B1-C1、B1-U1 與 B2 的 Owner 完成 Compose、通知及 Service 的 locale 接線，不建立另一套 Scala／TGG 引擎。
- **L14/L15**：同 SHA 全量驗證與 APK 交付，必須另有明確發布授權。

**L11-2 已完成的是「多批次未審核翻譯候選＋可重建佇列＋機械測試」；全 App 譯文及 UI 仍未完成。**
