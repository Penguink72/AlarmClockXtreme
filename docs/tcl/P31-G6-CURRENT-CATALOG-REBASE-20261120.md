# TCL｜G6 200-Key 現行來源重建與內容重審佇列（2026-11-20）

## 定位
以 S0 已修改的 Android 正式三語系資源為來源，建立 **G6 新目錄**，保留 11/18 LP0 與 LP1 Debug 舊目錄為不變的稽核快照。這輪只做來源重建、候選遷移、失效追蹤與機械檢查，不冒充人工審核，也不修改鬧鐘運作程式。

## TCL 來源枝
- S0 基準：b8c8b9c9655b03d54452d8947ee851f8588edba1，唯一正式英文修正 music_playlist_integration_pending。
- 舊目錄 revision：lp0-200-20261118；新候選 revision：g6-200-current-20261120。
- 新版 200 個原文 Key 直接依實際 Android values/strings.xml 核對；未登錄來源變更一律拒絕。
- 來源 SHA-256 使用 shaSource(key, source)，核對 S0 source-amendments.json 與歷史 LP0 來源矩陣。
- 對既有兩語候選沿用 G5 的 155 條既有未核准 + 45 條模型草稿；唯一已修正來源之中日候選，改採 S0 正式三語系檔案中的新候選，並標記重新審核。

## 產出與驗收
- GitHub Actions 生成 G6_UNREVIEWED_CURRENT_PREVIEW/manifest.json、current-catalog.json、current-review-queue.json、current-source-records.json、packs/en.json、packs/zh-TW.json、packs/ja.json。
- 200 個新英文來源、繁中／日文各 200 個候選文字，合計 400 筆待獨立審核，2 筆為已修改來源的候選；正式語義核准數 0。
- 新 `catalogRevision` 與歷史 LP1 Debug 目錄不同。因此 **目前 LP1 Debug 僅接受歷史版，不能直接匯入新的 G6 包**。需要 LP2 進行新的 Android catalog 適配。
- 機械驗證：舊資料不可被改寫、200 Key 不缺漏、來源修訂須與現行 Android XML 匹配、格式參數一致、不得有偽造批准、檔案大小不超過 256 KiB；另有 12 個負向變異測試。
- G6 release CLI 必須主動拒絕。禁止將 Node PASS 當成語義審核或手機 T3 實測。

## 下一步
- G6-Q：以新目錄做逐條人工／獨立內容審核；建立正式 approved-only 發行閘門。
- LP2-A：以新目錄進行 Debug 的真實主畫面動態文字替換；正式響鈴路徑不受語言預覽實驗影響。
- 不合併 main，不改 Room、AlarmManager、Media3、AlarmService 與播放歷史。
