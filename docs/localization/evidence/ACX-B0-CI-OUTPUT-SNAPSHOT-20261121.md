# B0｜2026-11-21 全 App 語言資源輸出文本與來源證據快照

> 文件用途：把本次 ChatGPT 討論中新增的 B0 **文字摘要**與全 Key 來源對照長期納入 Repo。由 CI 產生的 2.18 MB 逐呼叫點 JSON 不在此重複維護，改保留原始 JSON 的 checksum、來源 SHA、重建腳本與 CI Artifact 連結。**這不是人工翻譯內容核准、也不是 App 全語系完成率。**

## 來源固定與重建方式

- 扫描基準 SHA：`b738e747f74e54b32ea99448fe9b3f19a0dde72e`；[成功 Node CI Run #44890645147](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44890645147)；Artifact 名稱 `acx-b0-full-app-locale-inventory`（CI 暫存，不能當永久保存）。
- 原始 JSON：`full-resource-inventory.json`；SHA-256 `9c36ffc5d81a9411da61b57bb02e711aa713a6d1a515cc1b279cad2bdd998b8a`；2,184,484 bytes；為本討論串取得的該次 Artifact。摘要 Markdown SHA-256 `94ede849159fdf05f75bef5d0cd0a3d326b2c7a484d31356517917b69de23cb9`。
- 可再生來源為 `scripts/localization/b0-resource-inventory.mjs`。在 Repo 根目錄執行 `node --test scripts/localization/b0-resource-inventory.test.mjs` 與 `node scripts/localization/b0-resource-inventory.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY`。
- 長期可讀完整基底 Key 對照：[`localization/evidence/b0/full-base-resource-coverage-20261121.tsv`](../../../localization/evidence/b0/full-base-resource-coverage-20261121.tsv)，**2,471 個 Key 全保留**，包含類型、英文 XML 行、繁中/日文是否有專屬定義。
- 注意：此 TSV 的資料定義由 `app/src/main/res/values/strings.xml`、`values-b+zh+Hant+TW/strings.xml`、`values-ja/strings.xml` 直接核對；不應拿它推導顯示中的點擊行為或語義品質。

## 原始 B0 摘要（完整保留數字與限制）

| 機械盤點指標 | 實得 |
|---|---:|
| 權威基底資源 Key | **2,471**（2,427 string、43 plurals、1 array） |
| 可翻譯 Key | **2,462** |
| 帶格式符號的基底資源 | **369** |
| 含 rich markup 的基底資源 | **44** |
| 找到直接靜態引用的基底 Key | **2,447** |
| 未找到直接靜態引用的基底 Key | **24** |
| Kotlin/Java/XML 使用點掃描檔 | **312** |
| 動態/間接資源提示 | **0**（掃描器的有限規則下，不等於全部沒有間接引用） |
| 疑似硬編碼可見文案位置 | **97**（包含假陽性，未核准） |
| G6 抽樣與全基底交集 | **200/200** |

| Android 內建 locale | 存在的定義／可翻譯基底 | 未有對應專屬定義 |
|---|---:|---:|
| `values-b+zh+Hant+TW` | **912 / 2,462** | **1,550** |
| `values-ja` | **912 / 2,462** | **1,550** |

## 24 個沒有直接靜態引用的 Key（皆為 UNKNOWN，而非已廢棄）

1. `string:music_library_tab_songs`
2. `string:music_library_tab_videos`
3. `string:music_library_tab_folders`
4. `string:music_library_tab_artists`
5. `string:music_library_tab_pool`
6. `string:alarm_music_settings_source`
7. `string:alarm_music_settings_mode_shared`
8. `string:alarm_music_settings_mode_filtered`
9. `string:alarm_music_settings_mode_playlist`
10. `string:alarm_music_settings_playlist`
11. `string:alarm_music_settings_add_tracks`
12. `string:alarm_music_settings_save`
13. `string:alarm_music_settings_saving`
14. `string:alarm_music_settings_move_up`
15. `string:alarm_music_settings_move_down`
16. `string:alarm_music_settings_remove_track`
17. `string:music_library_media_counts`
18. `string:music_library_remove_needs_rescan`
19. `string:music_library_history_row`
20. `string:music_library_pool_manage`
21. `string:alarm_music_settings_open`
22. `string:settings_lp1_lab_label`
23. `string:settings_lp1_lab_open`
24. `string:settings_lp1_lab_description`

G2 原先指出的五個 `music_library_tab_videos`、`alarm_music_settings_mode_shared`、`alarm_music_settings_save`、`alarm_music_settings_remove_track`、`music_library_history_row` 均在上述集合中。其餘 Key 可能是舊 UI 遺留、間接動態資源或新元件改用不同 Key。需要查具體 Kotlin/Compose 與操作路徑，不能以 `NO_STATIC_REFERENCE_FOUND` 自動刪除、翻譯批准或當作 B2 已接線。

## 下一步分類

- `B0-1`：格式佔位符／plurals 的來源與現有譯文契約，不能把 369 全部判為 runtime 可用。
- `B0-2`：逐 Key 真實 Kotlin/Compose/通知引用及間接路徑，先標 `DIRECT_STATIC / POSSIBLE_INDIRECT / NO_REFERENCE_DETECTED`。
- `B0-3`：97 個疑似硬編碼文字去除動畫名、URI、程式 key、資源別名等假陽性，留下有具體畫面與用途的候選。
- `B0-4`：內建語系 1,550 缺漏逐 Key 分 `NEEDS_TRANSLATION / INTENTIONAL_FALLBACK / NON_TRANSLATABLE / UNKNOWN`，不可只因缺少 `values-ja` 就機械造譯文。
- `B1/B2`：需要完整資源目錄、格式型別與使用點才開始正式解析器/雙語 UI，200-Key Debug whitelist 不再作全 App 目標。

此快照應與 [B0 基準](../ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)、[R0 產品帳本](../../tcl/ACX-R0-FULL-PRODUCT-STATUS-LEDGER-20261121.md) 交叉查閱。版本化記錄只能隨新 SHA 新增，不能將此前錯誤的 CI 結果抹掉。
