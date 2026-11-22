# ACX L11-7｜殘餘統計、系統提示與就寢 Sonar 雙語候選（2026-11-22）

> **狀態：翻譯模型候選與 Node 工程，不是正式語系資源。** 以 L12-1 [PR #48](https://github.com/Penguink72/AlarmClockXtreme/pull/48) 最後成功 SHA `58694759ff0607b58beb1889fd8f3059514b275d` 為基底，與 L12-2 行為核對獨立並行。此分支沒有納入 L12-2 14 項新探針；後續必須在共同基底整合測試。

## TCL 結構

**根**：避免 L11 補譯工作集中於比較少見的細部選項，剩餘部分優先選取「歷史數據與清除後果、常見系統設定、就寢提醒、Sonar 音訊隱私、勿擾模式與健康資料權限」。利用 B0-T1 英文來源預覽、L10 使用點線索與既有 G1 術語契約產生可追溯的 zh-TW／ja 草稿；不得假設這些文字已在正式 App UI 中生效。

| 枝 | 新增內容 | 語義界線 |
|---|---:|---|
| L11-7-S｜統計／設定／小工具提示 | 44 Key × 2 = 88 筆 | 統計資料不等於正在播放的音樂歷史；清除「歷史」應明確指對象 |
| L11-7-B｜就寢與 Sonar | 72 Key × 2 = 144 筆 | 不保存原始音訊、麥克風存取權、DND 設定與睡眠資料描述需在 L12 實際核對 |
| L11-7-Q｜Node 候選完整性 | 5 項新測試 | Source 漂移、格式參數、批次 Key 重複、既有 XML 覆寫、偽造批准全部拒絕 |
| L11-7-G｜剩餘處置分類 | 更新 L11 既有 JSON/TSV | 誤認代碼為可保留英文或未知映射為未使用都不得自動核准 |

## 實體交付

- `localization/candidates/l11-b7-stats-settings-drafts-20261122.json`：44 個英文 Key、兩語候選。
- `localization/candidates/l11-b7-bedtime-sonar-drafts-20261122.json`：72 個英文 Key、兩語候選。
- `scripts/localization/l11-translation-worklist.mjs`：擴充 `batch7[]`，保留 L11-1～6/G6 全部來源與狀態。
- `scripts/localization/l11-b7-visible-residual.test.mjs`：5 項原始 Source／format／權限負向測試。
- `scripts/localization/l12-structural-semantic-evidence.test.mjs`：按 B7 最新候選分母更新 L12-QA 回歸測試，**不改 L12-QA 規則或現有審核批准**。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：新分支跑 B7，並在同一 Artifact 重新生成所有 L11＋L12-1 工作列。

## 分母與完成門檻

| 項目 | 以本分支當期來源預期 |
|---|---:|
| B0-T1 英文 base／可翻譯 Key | 2,471／2,462 |
| zh-TW／ja 各缺專屬 XML | 1,550 |
| 原始缺漏工作列 | 3,100 |
| L11-1～6 與可復用 G6 累計 | 2,074 |
| **L11-7 本次新增候選** | **232** |
| **合計可供審核的未核准候選** | **2,306** |
| **剩餘沒有候選** | **794** |
| 原有 locale XML（不更動） | 1,824 |
| 獨立語義批准／本輪正式 XML | 0／0 |

候選涵蓋 2,306／3,100 約 74.4%，**並不代表正式 App 翻譯率**。最終需比對 GitHub Actions 最後精確 SHA 的 `L11_TRANSLATION_DRAFTS`、`L11_B3_DISPOSITION_CANDIDATES`、`L12_STRUCTURAL_AND_SEMANTIC_QA`。

## L12 核對與後續

- `stats_grant_read_sleep_to_add_sleep`、`bedtime_grant_read_sleep_in_settings_to`：Health Connect 權限不能宣稱讀取其他健康資訊，或擅自認定使用者已授權。
- `stats_sonar_decision_detail`、`bedtime_monitoring_movement_and_loud_sleep_sounds`、`bedtime_noise_last_measured`：必須對照程式確認「原始音訊不保存」與實際本機摘要。
- `settings_clear_history` 和統計清除行為要與 L12-2 現有事件資料表的檢查關聯，不能推論會刪除真實鬧鐘。
- `bedtime_dnd_status_sync_failed`、`bedtime_sonar_start_failed`：後續需用實際失敗路徑核對，避免使用者誤以為睡眠提醒或鬧鐘已正常排定。
- 後續應把本分支和 L12-2 探針分支整合至單一候選＋驗證 SHA 後跑完整 CI，再進入 L12-3 的人工語義與正式 XML 準備；正式批准、L13 Runtime 與 APK 都不在本片內。

**本輪完成定義：** 116 Key×2 來源綁定草稿、完整殘缺分類與同 SHA Node CI 成功，沒有發行資源更動。
