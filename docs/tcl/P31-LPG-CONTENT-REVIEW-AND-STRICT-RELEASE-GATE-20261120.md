# AlarmClockXtreme TCL｜LP-G 內容審核與正式發布門檻（2026-11-20）

## 已完成的工程與資料證據

**來源**：PR #18 衍生的 Draft PR #19；保留原 LP0 200-Key 快照和三語既有譯文，未更新 `app/`、Room、AlarmService、Media3 或 main。

| 分支 | 可證實的進度 | 未完成／阻斷 |
|---|---|---|
| LP0 | 200 真實來源 Key、英文 200、zh-TW 155、ja 155 的原始快照與 SHA-256 | 非完整 App 2,421 Key |
| LP-G1 | 18 個術語候選概念、操作後果及非同義詞規則 | 尚未由獨立審核者核准 |
| LP-G2 | 309 個來源檔、262 個直接字串參照、195/200 Key 有靜態參照；12 條錯誤概念連結排除，有效映射與原啟發式結果分離 | 5 個 Key 無直接參照；行為語義不等於靜態參照 |
| LP-G3 | 21 + 24 個原缺譯 Key 各產生繁中／日文候選，共 45 Key、90 條模型草稿；各語言預覽 200/200 字串存在 | 所有候選皆未獨立核准 |
| LP-G4a | 21 Key × 2 語言候選，模型檢閱並調整 9 個 Key 的草稿；另外記錄 9 個無效術語連結 | 非人類批准 |
| LP-G4b | 24 Key × 2 語言新增草稿，附實際源碼位置、佔位符與語義注意事項 | 非人類批准 |
| LP-G4c | 52 個高優先字串初審，其中原有譯文 42 Key、原缺譯 10 Key；對原有內容提出 6 條修改建議 | 不代表 52 Key 全部語義完整通過 |
| LP-G4d | 另有 113 個原有 Key 的模型文字表層核對，6 Key／10 條語言文字建議修訂；與高優先篩查的 42 Key 合計涵蓋原有 155 Key | 對其餘內容僅代表沒辨識出明顯矛盾，**不是審核核准** |
| LP-G4e | 新增來源缺陷 `music_playlist_integration_pending`：英文仍寫等待 Room v28，但正式程式已宣告 Room v28 | 先修正權威英文、重新建立來源 Hash 及重審譯文 |
| LP-G5 | 200/200 × 2 語言預覽包成功，與 LP1 解析器相容；正式模式要求兩語各 200/200 完整、有效、獨立核准，重算 G4 ledger 且禁止開啟中來源缺陷 | 真正 APPROVED 仍為 0，`release` 故意拒絕 |

## 一個由翻譯審核反查到的產品來源 BUG

- `app/src/main/java/com/sysadmindoc/alarmclock/data/local/AlarmDatabase.kt:40` 已宣告 `version = 28`。
- `app/src/main/java/com/sysadmindoc/alarmclock/ui/ringtone/MusicPlaylistsWorkspace.kt:178` 的 `repository == null` 分支顯示 `R.string.music_playlist_integration_pending`。
- 目前英文原文仍稱等待 Room v28 整合、Room v27 選歌功能可用，但播放清單修改尚不儲存。
- 這是**英文來源文字過時**，翻譯成多國語言只會加劇錯誤。修訂原文前不准當作已審核詞條。
- 這次只在 G4 提出新英文及中日候選，**未修改 Android 正式資源**，也未讓模型自行核准。

## 2026-11-20 G5 防線修訂

原有 G5 可以在一部分 Key 獲批准時輸出部分正式包，剩下回退英文；已修正為 **zh-TW 200/200 和 ja 200/200 全部具有效批准**才允許本 200-Key 實驗的正式包，並在 CLI 最後重新從真實 ledger 與 context evidence 計算 G4 報告。存在 `source-defects.json` 未解決的阻斷問題時，即使合成測試提供 400 個批准項目，也不得產出正式包。測試裡的合成批准只在記憶體，不入庫。

## 已驗證／未驗證

- 所有工作流程為 Node.js built-ins；沒有 Python/npm install 依賴。
- GitHub CI 最新結果待核對並在 PR #19 註明。
- 瀏覽器與 Android LP1 Preview 的 JSON 解析契約已涵蓋；**沒有**增加 Android APK。
- 不宣稱完整 App 已完成任意語言包單語／雙語 UI 替換。

## 下一個必要轉換

1. 隔離修正過時英文來源，觸發 LP0 sourceHash 變更與既有譯文 STALE 流程。
2. 對上述 6+6 個 Key 的修改提案進行真正獨立確認，擴展至 200 Key 的獨立審核紀錄；G1 概念核准另作判定。
3. 針對五個無靜態參照 Key，完成動態使用與用戶可見性檢查；不可依推測刪除。
4. 待 200 Key 的 G5 approved-only release 結構與內容核准完成後，再擴至 Android 全部資源及 LP2 完整單語／雙語 UI。

**不得**將 200/200 候選文字存在率誤報為 200/200 翻譯品質完成率。
