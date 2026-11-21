# G6 → LP2：現行字串來源、審核佇列與 Debug 覆蓋快照（2026-11-20）

## 目的與權威鏈

本文件專門保存 2026-11-20 討論串對語言包流程的最新要求，不重新開啟舊版 LP0 或自動核准語義。

`Android strings.xml（權威原文） → S0 修訂及 hash → G6 現行 catalog → G3/G4 翻譯候選與獨立審核 → G5 approved-only release → LP2 Debug/UI 接線`。

目前是**候選與預覽階段**，不是正式可發布的語言包。所有正式發布仍須滿足詞彙、來源、佔位符及目標語言全數內容審核。

## 版本矩陣

| 欄位 | 歷史 LP0／LP1 | 現行 G6／LP2 |
|---|---|---|
| Catalog revision | `lp0-200-20261118` | `g6-200-current-20261120` |
| 內容來源 | 2026-11-18 的 200-Key 快照 | S0 更新後的 Android 200-Key 權威資源 |
| 實驗用途 | 歷史稽核、LP1 Debug Lab | LP2 Debug 匯入、設定頁及跨畫面候選顯示 |
| 路徑 | `experiments/lp0-200/`、LP1 舊資源 | `app/src/debug/assets/lp2/catalog.json`、`packs/{en,zh-TW,ja}.json` |
| 版本相容 | 不自動接受 G6 包 | 不接受舊 LP0 revision |
| 審核狀態 | 不將歷史草稿推定已核准 | 400 筆候選，正式核准 **0** |

唯一變動來源 Key 是 `music_playlist_integration_pending`。S0 於 `app/src/main/res` 的英／繁中／日文三語正式字串中修正過時敘述；`localization/governance/s0/source-amendments.json` 記錄舊／新文字、SHA-256 與重審狀態。G6 `current-review-queue.json` 只承載需獨立審核的候選，不冒稱獨立批准。

## Debug 白名單進度（只針對抽樣 200 Key）

| 階段 | 不同 Key 的總數 | 本片新增加（去重） | 呼叫位置 |
|---|---:|---:|---|
| LP2-A 設定頁 | 21 | 21 | 23 |
| LP2-B1 鬧鐘／音樂／歷史六個畫面 | 51 | 30（跨六畫面使用 31 Key，其中 1 Key 與 A 重複） | +42 |
| LP2-B2 編輯／來源／統計九個畫面 | **87** | **36**（使用 40 Key，其中 4 Key 已在 A/B1） | +49 |

200 Key 中不帶格式參數 179、有格式參數 21；**87 已接入、92 個無參數與 21 個有參數尚未接入**（共 113），且這只是抽樣目錄覆蓋，並不等於 Android 全 App 所有文字的覆蓋率。

## 功能與防線

- `Lp2PreviewText` 僅在 Debug、明確啟用且命中 Key 白名單時替換；非 Debug 與停用時繼續使用原生 `stringResource()`。
- LP2-A 提供 SAF OpenDocument JSON 匯入、256 KiB 限制、正確 `catalogRevision`、locale、Key、printf 檢查、`AtomicFile` 寫入與重啟恢復；支援單語與雙語，缺譯英文備援。
- 對讀取選擇的生命周期不得擋住正在響鈴的鬧鐘。任何非 Debug 發行與正式鬧鐘流程不可載入未審核候選語言包。
- LP2-B1 的音樂庫標題於 Debug 雙語模式允許兩行，非雙語維持原樣。這是局部防截斷，不是全面排版保證。
- 內容核准 `0／400`；G6 修訂影響的繁中與日文兩條候選明記 `STALE_REVIEW_REQUIRED`，不可繼承歷史批准。
- 正式發布模式應維持 fail-closed：缺少來源、術語、全部兩語獨立批准或存在 open release-blocking defect 時拒絕；**Node PASS 與 CI PASS 都不會改變這點**。

## 驗證索引

- [G6 Node CI 44740399112](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44740399112) — 200 Key 與 400 審核佇列 PASS。
- [LP2-A Android CI 44744104437](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44744104437) — PASS。
- [LP2-B1 Android CI 44761537401](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761537401) — 856／856 單測、APK、簽章 PASS。
- [LP2-B2 Android CI 44762900025](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900025) — 859／859 單測、APK、簽章 PASS；[Node 治理 CI 44762900020](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900020) PASS。

**尚未驗證：** T3 實機語言匯入、雙語排版、重新開啟／切換、無障礙與響鈴優先級。待審核候選不能打包成正式發行。
## 2026-11-21 B3 新切片（上方 B2 表格為 11/20 歷史快照）

- 從 92 個未接線、無參數 Key 中找到 35 個有實際可定位 UI 呼叫的 Key：13 個直接 `stringResource(R.string.xxx)`、22 個透過 enum／category／條件式取得的 `R.string` ID。
- 新接線位置：直接 15 處 + 間接 19 處；新增 `textForResource` 只對 22 個精確 ID 進行 G6 映射，其他資源保持原生 Android fallback。
- Debug 白名單累計 **122／200**；仍未接線 **57 個無參數 Key** 及 **21 個有參數 Key**。這只是實驗目錄數字，並非 App 全資源覆蓋。
- 為避免響鈴操作文字導致雙語截斷或影響警報，`AlarmFiringScreen`、通知、服務不在 B3 範圍。詳見 [LP2-B3 TCL](../tcl/P31-LP2B3-DIRECT-INDIRECT-20261121.md)。
- 中日兩語 400 筆候選的正式獨立語義核准仍為 0；新增 UI 接線**不會解除發布阻擋**。正式發布依然需要 G6-Q。
