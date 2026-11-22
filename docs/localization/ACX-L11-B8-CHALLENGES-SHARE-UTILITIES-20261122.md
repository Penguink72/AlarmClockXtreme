# ACX L11-8｜起床挑戰、共享匯入與生活小工具雙語草稿（2026-11-22）

> **治理性質：MODEL_PRELIMINARY_NOT_APPROVED。** 本分支基於 [L11-7＋L12-2 整合 Draft PR #51](https://github.com/Penguink72/AlarmClockXtreme/pull/51) SHA `000d3af5b2834e790465e65bc7ed18ac04ac7dce`，與 L12-3 從同一基準平行發展。只新增候選 JSON、Node 工作列／回歸測試與本文；不更改 Kotlin、Room、鬧鐘排程、音樂庫、正式語系 XML、G4/G5 核准帳本、APK。

## TCL Root、枝與差分責任

**Root**：選擇先前 794 筆無候選 locale row 中可見、可辨識的介面文字，而不是一味翻譯 URL、專有代碼或 Android 圖像資源。將兩語草稿完整綁定英文來源、格式參數及審核風險，並把完整候選工作列和 L12-1/2 版本持續重建。

| 本輪枝 | 新 Key | 中日候選 | 語義風險 |
|---|---:|---:|---|
| B8-C｜起床挑戰與備援 | 79 | 158 | 忘記 NFC／條碼／照片參考資料時的備援操作，與真正解除鬧鐘不可混淆 |
| B8-S｜分享鬧鐘／安全提醒 | 32 | 64 | 共用鬧鐘儲存但未啟用、私有參照移除、裝置精確鬧鐘權限 |
| B8-U｜碼錶／世界時鐘／天氣／呼吸／捷徑 | 76 | 152 | 「移除城市」≠刪資料檔；氣象翻譯需符合實際資料來源與 Android 文字限制 |
| B8-Q｜來源與格式測試 | 6 項 | — | source drift、格式 placeholder、舊 XML、重複 Key、模型自批均 fail-closed |

## 交付檔案

- `localization/candidates/l11-b8-challenges-drafts-20261122.json`：79 個英文 Key 的兩語候選。
- `localization/candidates/l11-b8-share-safety-drafts-20261122.json`：32 個分享／備份／可靠性文字。
- `localization/candidates/l11-b8-utility-widgets-drafts-20261122.json`：76 個小工具與天氣文字。
- `scripts/localization/l11-translation-worklist.mjs`：加入 `batch8[]`，沿用 B0-T1 與 L10 的來源鎖定、現有 G6/前七批候選。
- `scripts/localization/l11-b8-challenges-share.test.mjs`：6 個完整來源與權限回歸測試。
- `scripts/localization/l11-b7-visible-residual.test.mjs`：把舊批次回歸 fixture 限於 B7；避免新批次擴充意外改變舊批次自我驗收分母。
- `scripts/localization/l12-structural-semantic-evidence.test.mjs`：對最新當期候選分母做 L12-1 品質驗證。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：新分支觸發與 Artifact 重建。

## 分母、進度與批准邊界

| 指標 | 本輪預期 |
|---|---:|
| 英文 base／可翻譯 Key | 2,471／2,462 |
| 繁中與日文各缺專屬 XML | 1,550 |
| 缺專屬 locale row 總分母 | 3,100 |
| L11-1～7 + 同來源 G6 候選 | 2,306 |
| **本輪新增 L11-8** | **374** |
| **累計有候選** | **2,680** |
| **剩餘無候選** | **420** |
| 原有正式 zh-TW／ja locale XML | 1,824（不更動） |
| **獨立語義批准／正式 XML 寫入／新 APK** | **0／0／0** |

上述數字是待 GitHub Actions 最後 SHA 的 CI 產物確認的設計分母。2,680/3,100 約 86.5% 只代表缺譯工作列中已有模型／G6 未審核候選，**不是**產品真正已翻譯或取得正式核准的比例。

## L12 語義及內容查核

1. `challenge_no_tag_registered_yet_any`、`challenge_no_code_registered_yet_continue`、`challenge_no_reference_photo_registered_yet`：逐一查實「未設定時任何物件都可通過」究竟是正常流程還是必須明確警告的備援。
2. `challenge_typing_speed_instructions`、`challenge_pvt_average`、`challenge_emoji_pairs_found`：速度單位、百分比/格式占位符、題目數值需符合挑戰本身的實際邏輯。
3. `share_import_save_without_enabling`、`share_import_strip_private_references`、`share_import_sanitizing_keeps_wake_time_repeat`：是否停用、清除哪些私人資料、儲存前有無真實確認，均需對照程式操作。
4. `health_exact_alarms_revoked` 與 `direct_boot_alarm_message`：Android 系統權限、直啟鬧鐘、設備鎖定及可靠性聲明不能由翻譯推定。
5. `world_remove_city_message`、`stopwatch_reset`：局部刪除世界時鐘或重置計時器不等同實體資料刪除。
6. 所有天氣字串需核對 WeatherCodes／實際使用點，日文／繁中不同氣象術語不能只由機械比對推定正確。

**接續**：L11-9 再處理餘下自然語言項；L12-3 應為所有現有候選產出 Android 轉義審核表，但不得因模型翻譯比對成功就自行寫入正式 XML。L13/L14/L15 仍需獨立語義授權、正式 Android 資源編譯及實機驗證。
