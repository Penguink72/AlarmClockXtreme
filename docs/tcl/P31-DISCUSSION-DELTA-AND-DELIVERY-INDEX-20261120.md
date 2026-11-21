# TCL｜2026-11-20 鬧鐘 App 討論串文件增量與交付總索引

> 範圍：本討論串 I0 → S0 → G6 → LP2-A → LP2-B1 → LP2-B2、跨階段 CI 診斷及待辦。
> 核對時間：2026-11-20（Asia/Taipei）。**這是當次提交的證據快照；GitHub 現況以該提交及各工作流為準**。
> 使用方式：先查本頁定位文件，再進入對應單一施工紀錄與 GitHub Run；不將歷史計畫當作最新實作狀態。

## 1. TCL 根節點：使用者要完成甚麼

在 Android 鬧鐘 App 中，讓不同語言包只要符合權威原文字串與格式契約，就能被匯入，支援設定頁及逐步擴充到其他實際 UI 的單語／雙語顯示。翻譯前先對照**正式英文來源、現存中文／日文及詞彙規則**，逐條紀錄問題與版本關係，避免模型生成候選文字後假定審核已完成。

既定邊界：使用者本機僅要求 Node.js 流程，不依賴本機 Python；Android CI 可以用 Gradle／Robolectric。禁止改動正常鬧鐘排程、響鈴服務、播放控制、Room schema、播放歷史。**Debug 預覽不等於正式發行；T2 CI 不等於 T3 實機驗證。**

## 2. 隔離分支與提交關係

| 階段 | Draft PR | 程式／證據基準 SHA | 交付概要 | 可驗證的 T2 |
|---|---|---|---|---|
| I0 | [#20](https://github.com/Penguink72/AlarmClockXtreme/pull/20) | `2b0d2223883d7d2fa604ef92b941342938812a82` | LP1 Debug Lab 與 LP-G1～G5 接合，維持歷史快照 | [Android 44733855718](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44733855718) PASS |
| S0 | [#21](https://github.com/Penguink72/AlarmClockXtreme/pull/21) | `b8c8b9c9655b03d54452d8947ee851f8588edba1` | 修正 1 條過時原文及中日候選，保留來源雜湊與未審核標記 | [Android 44738706022](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44738706022) PASS |
| G6 | [#22](https://github.com/Penguink72/AlarmClockXtreme/pull/22) | `df97384d2b8dcbfc7b4f0468faaeb946ba022206` | 新 200-Key 現行來源目錄、400 筆候選審核佇列 | [Node 44740399112](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44740399112) PASS；不具獨立語義核准 |
| LP2-A | [#23](https://github.com/Penguink72/AlarmClockXtreme/pull/23) | `c87a3daf78880f56d7b0f26d2716dfd9662ab6f9` | 設定頁 21 Key／23 顯示位置、G6 JSON 匯入、雙語預覽 | [Android 44744104437](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44744104437) PASS |
| LP2-B1 | [#24](https://github.com/Penguink72/AlarmClockXtreme/pull/24) | `b2c03972a5aba58804796244d3634bbb253909b9` | 六畫面新增 31 Key／42 位置，與 A 重疊 1 Key，累計 51 | [Android 44761537401](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761537401) PASS；單測 856／856 |
| LP2-B2 | [#25](https://github.com/Penguink72/AlarmClockXtreme/pull/25) | `6a0223e119f2d9b62e57f655bac5c98ed61bdeb2` | 九畫面新增 36 不重複 Key／49 位置，累計 87 | [Android 44762900025](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900025) PASS；單測 859／859 |

上述 #20～#25 是有父子依賴的 **Draft PR 鏈**，不代表已合併 `main`，也不保證每一項功能已完成手機 T3。新建的本次歸檔分支只記錄文件，不改 App 程式碼或正式發行設定。

## 3. 各資料夾的權威文件

### 施工與 TCL（`docs/tcl/`）
- [I0 整合](P31-I0-LP1-LPG-INTEGRATION-20261120.md)
- [S0 正式原文更正與譯文失效](P31-S0-CANONICAL-SOURCE-REFRESH-20261120.md)
- [G6 現行來源目錄重建](P31-G6-CURRENT-CATALOG-REBASE-20261120.md)
- [LP2-A 設定頁預覽](P31-LP2A-LIVE-SETTINGS-20261120.md)
- [LP2-B1 跨畫面與生命週期修補](P31-LP2B1-CROSS-SCREEN-20261120.md)
- [LP2-B2 編輯／音樂／統計擴充](P31-LP2B2-EDIT-MUSIC-STATS-20261120.md)
- 本文：連接上述各片、CI 最終狀態與下一步；不取代各片原始工作紀錄。

### 語言治理（`docs/localization/` 與 `localization/governance/`）
- [G1 詞彙與概念契約](../localization/LP-G1-GLOSSARY-CONTRACT-20261119.md)
- [G2 文字來源與呼叫證據](../localization/LP-G2-CONTEXT-EVIDENCE-20261119.md)
- [G3 候選翻譯](../localization/LP-G3-CANDIDATE-TRANSLATIONS-20261119.md)
- [G4 審核與發布閘門](../localization/LP-G4-QA-REVIEW-GATE-20261119.md)
- [G5 正式發布政策](../localization/LP-G5-PACK-RELEASE-POLICY-20261119.md)
- [G6／LP2 的現行資料與待審矩陣](../localization/P31-G6-LP2-CURRENT-REVIEW-AND-COVERAGE-20261120.md)
- 機械證據原始檔位於 `localization/governance/{lp0,g1,g2,g3,g4,s0}/`，沒有把候選生成當作人工核准。

### CI 失敗診斷（`docs/ci/`）
- [Kotlin 與 Compose 2.1.0 marker POM 差異、逐檔雜湊核對及嚴格修補](../ci/P31-KOTLIN-COMPOSE-POM-PROVENANCE-20261120.md)
- 獨立診斷的原始只讀分支：`diagnostics/p31-lp2b1-kotlin-pom-provenance-20261120`；其工具未被併入正式 App 執行流程。

## 4. 本輪新增的決定與證據

1. **來源先於翻譯：** `music_playlist_integration_pending` 不能繼續宣稱 Room v28 尚待整合。S0 更正 Android 英文與兩語候選，G6 重建來源雜湊；11/18 LP0 與 LP1 的歷史版不改寫。
2. **版本不可混用：** 歷史 `lp0-200-20261118` 與現行 `g6-200-current-20261120` 不相容。LP2 將現行版放在 `app/src/debug/assets/lp2/`；舊 LP1 實驗室保留。
3. **內容審核未完成：** 200 英文 Key × 2 目標語言＝400 筆候選；2 筆因來源修訂需重審；**正式獨立語義核准 0／400**，G5／G6 正式發行門檻持續拒絕。
4. **真實畫面逐片接入：** A 21、B1 追加 30 個淨新增、B2 再追加 36 個不重複 Key，累計 **87／200**。切換只作用於 Debug 的白名單文字，非正常 Release 的多語系全域完成。
5. **原有響鈴優先：** `MainActivity.onResume` 先處理活躍鬧鐘轉導，沒有響鈴才讀取 Debug 的預覽設定。先前重複 `onResume` 導致 Kotlin 編譯失敗已修復並加護欄。
6. **安全不降級：** Kotlin Android／Compose 插件 2.1.0 的兩份官方 POM 位元雖不同，但各自宣告相同的實際插件依賴。只為這兩個指定 marker POM 加入經驗證的第二雜湊，其他 Gradle 嚴格檢查維持啟用。
7. **工作證據不跨 SHA 冒用：** 所有 T2 結論以表中**該提交**的實際工作流為準。B2 的 859／859 成功不能用作後續新代碼的 T2。

## 5. 尚未完成及下一輪可施工範圍

| 待辦枝 | 狀態／阻斷界線 |
|---|---|
| LP2-B3 後續直接文字接線 | 在本 200-Key 實驗中，尚有 **92 個無格式參數 Key** 未進入現有白名單。先查實際使用位置，不能臆造 callsite。|
| 格式參數與複數 | 尚有 **21 個含格式參數 Key** 未接入本實驗預覽；處理 printf 參數順序、數量、複數、時間／數字本地化及回退契約，另設測試。|
| G6-Q 內容審核 | 400 筆候選及詞彙契約獨立核准尚未進行，不能簽發正式語言包。|
| T3 真正手機操作 | 尚未提供匯入、切換、冷啟動恢復、雙語截斷、無障礙以及響鈴優先級的實機驗證。|
| Main 正式上線 | **未獲授權，也未合併。** 避免把 Draft PR 或 Debug 覆蓋率當正式部署。|

**保留來源與限制：** Android 資源與 GitHub workflow 是技術事實來源；本索引是討論增量整理，不能單獨作為執行成功的證據。
## 2026-11-21 更新：LP2-B3 接續

- [LP2-B3 直接與間接資源 ID 接線](P31-LP2B3-DIRECT-INDIRECT-20261121.md)：在既有 87 Key 基準下新增 13 個直接與 22 個資源 ID 間接 Key，將本 200-Key 抽樣的 Debug UI 接線擴張至 122 Key。
- 此處是 **11/21 新分支工程增量**；上方 11/20 的歷史 87-Key 快照與各階段 CI 狀態不被回寫。B3 T2 需以新分支的精確 SHA CI 證據裁定，T3 與內容獨立審核仍是未完成項目。
