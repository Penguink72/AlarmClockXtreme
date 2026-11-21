# TCL｜LP2-B3：直接／間接 Android UI 文字接線（2026-11-21）

## 根節點
在 LP2-B2 已通過完整 Android T2 的 87/200 Key 基準上，繼續盤點本次 G6 抽樣目錄中的 92 個未接入、無格式參數 Key。**只將實際 Compose UI 可以追溯的資源接入 Debug 預覽**；不拿沒有實際呼叫點的 Key 湊覆蓋率，不修改正式鬧鐘工作流。

## 實作切片（B3）
- 父分支：`docs/p31-discussion-delta-and-ci-evidence-20261120` / `119e3181`；其中已包含 PR #26 討論增量紀錄。B3 在獨立分支施工，不直接改 B2、docs 或 main。
- `b3DirectKeys`: **13 個**既有 `stringResource(R.string.<key>)`，在 8 個 UI 程式檔案內改用 Debug-gated `Lp2PreviewText.text`，總共 **15 處**原有文字呼叫。
- `b3IndirectKeys`: **22 個**藏在 `SettingsPaneCategory.titleRes / descriptionRes`、`AlarmEditorPage.titleRes`、`AlarmEditorSection`、Onboarding 描述、音樂庫條件分支和診斷切換條件的資源 ID。新增明確 `R.string.<key> → G6 <key>` 映射，並在 6 個 UI 程式檔案內將 19 處間接 `stringResource(resId)` 改走 `Lp2PreviewText.textForResource(resId)`。
- B3 `textForResource`：只有明確列入映射的 22 個 Key 會進到既有的 Debug 白名單解析器；未映射資源仍走 Android 原生 `stringResource(resId)`。不做模糊字串比對或推測轉換。
- 舊 87 Key + 新增 35 Key = **122／200 個不重複 Key**（僅代表 Debug 實驗樣本，不是全 App 翻譯覆蓋率）。剩餘 **57 個無參數 Key + 21 個有參數 Key = 78 Key**。
- 修改既有 UI 函式呼叫，不增加 Kotlin 程式檔行數；G2/G4 的原始碼行號證據必須保持有效。若來源定位驗證失敗，先判斷是否是文字載入形式變更而非靜默修改審核。

## 安全邊界與排除
- **完全排除** `AlarmFiringScreen.kt` 的視覺控制提示、Android 通知與背景服務等高風險顯示位置；它們不參與 B3 預覽白名單，實機驗收前不得假定雙語排版安全。
- 21 個包含 printf 佔位符的 Key 留到後續獨立格式適配，不擅自使用只適合純字串的 `text()`。
- 不改 Room、AlarmService、AlarmManager、Media3、鬧鐘排程、播放紀錄及通知路徑；Release 仍採原本原生 Android 資源。
- 不改 LP0 / LP1 歷史快照、G6 權威原文／source hash、G4 review ledger；候選中日翻譯仍 **0/400 正式語義核准**。

## T2 驗證
- `scripts/localization/lp2b3-indirected-ui.test.mjs`：檢查 13+22 Key 精確白名單、15+19 UI 呼叫、22 個 R.string ID 映射、21 格式 Key 排除、Android fallback、響鈴優先順序和未調整的 Gradle 嚴格驗證。
- `Lp2IndirectPreviewTextTest`：Robolectric 驗證 122 Key 與 22 個明確資源 ID、未列入映射的資源不被覆寫，以及三語候選資料可被取得。
- 舊 LP2-B1／B2 單測改成 **同時**檢查既有 51／87 Key 不變與 B3 全域 122 Key；不能簡單刪掉既有斷言。
- 精確 SHA 必須取得 LP-G Node + LP1 parity + Android 嚴格 F-Droid Debug APK、Robolectric／原有單測、第二 Runner 持續簽章的 SUCCESS 才算完成 T2。

## TCL 後續
- B3-T3：Android 實機測試語言匯入、設定分類、編輯區塊、音樂庫空狀態及按鈕長文／雙語截斷。這一階段 CI 無法代替實機。
- B4：對 57 個尚未接入的無參數 Key 依呼叫型態分類：高風險響鈴／通知、條件式資源、Context getString、無法確認的動態路徑；需要來源證據再決定接線。
- B5：對 21 個格式參數 Key 建立具備參數順序、格式、數量及複數保障的翻譯適配，不直接偷換。
- G6-Q：400 筆候選的獨立語義審核與正式 release 閘門；持續拒絕未審核包。
