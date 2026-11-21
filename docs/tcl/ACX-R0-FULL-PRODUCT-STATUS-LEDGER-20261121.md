# AlarmClockXtreme｜R0 全產品 TCL 狀態帳本（2026-11-21）

> **定位**：這是橫跨全部產品能力的版本化狀態帳本，不是 G6 翻譯專案的子報告。依使用者 2026-11-21 決定恢復「R0 產品目標 → R1～R5 功能 → R6 工程」主從關係。只報告可追溯證據；不憑 Node/CI PASS 寫成使用者已能完成全部操作。**此文件為 A0 整合候選起始快照，尚非正式發布或 main 合併。**

## 0. 提交與證據固定

- Repo：Penguink72/AlarmClockXtreme；整合候選分支：integration/acx-r0-product-candidate-20261121。
- 來源釘選：G6-Q2 Draft PR #29，`4ad5ee289d9c8e0191272601fc2333be18e80615`；其父系包含 PR #13 的 `c4dd6353f3ae5f95c9a2667599f294920eaa9619`，相較 #13 ahead 79、behind 0。不是新作一輪 cherry-pick。
- 最新 Android App 的實際程式基準為 LP2-B3：PR #27，`1d0920edfdb706959b1c57e947b9e7a4d2545670`；其後至 #29 未變更 app/ 下的 Android 執行檔案，增加 G6-Q1/Q2 Node／治理／文件。B3 [Android Run 44865048570](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44865048570) 的 APK、完整單測、第二 Runner 簽章工作均 SUCCESS。
- G6-Q2 [Node Run 44871629809](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44871629809) 的 governed-language-pipeline SUCCESS；它不是此整合分支獨立的 Android T2。
- `main` 與 Q2 比較是 ahead 384 / behind 0（2026-11-21 核對快照）；`main` 未合併上述 Draft PR 鏈。任何數值須在新的 SHA 重驗。
- 本整合分支新增的文件／工作流變更不代表 APK 版本已實機驗證；**I1 重新跑本候選 SHA 的 CI 後更新下方證據欄**。
- 主要來源：[P3.1→U5 原始施工帳本](P31-TO-P2-U5-CURRENT-DELIVERY-LEDGER-20261116.md)、[LP2-B3](P31-LP2B3-DIRECT-INDIRECT-20261121.md)、[G6-Q2](P31-G6Q2-COMPLETE-MODEL-SURFACE-AUDIT-20261121.md)、[G6 翻譯完整報告](../localization/P31-G6Q2-ALL-200-MODEL-CONTENT-AUDIT-20261121.md)。

## 1. 根任務與 T/P/Q 定義

**R0：五秒設好、準時響起、持續播歌、容易找回、設定不遺失，並可擴充完整單語／雙語介面。**

- **T0** 規格／使用者決定；**T1** 可定位程式；**T2** 指定 SHA 的自動測試／建置；**T3** 真機特定條件；**P** 產品動作達成；**Q** 內容的獨立品質核准。
- `PASS` 只在對應層存在實證時使用。沒有 T3 不妨礙受授權的 Code-Go，但不得寫成 T3 PASS。
- 完成率分母必須是對應全 App 實際呼叫點／產品任務；`122/200` 是 Debug 200-Key 抽樣而非全 App 翻譯率；`400/400` 是模型字面初審而非 Q 核准。
- 評估 `P` 時，記錄操作入口、點擊數、開始與完成時刻、OS 排程 ACK、播放實際起聲、失敗與恢復；使用者提供的舊手機回饋是**當時那部手機／版本的實證**，不是新整合包的普遍保證。

## 2. 全產品能力矩陣

| 根枝 | 使用者真正要完成的動作／契約 | 目前 T1／既有證據 | 剩餘驗收／阻斷 | 裁定 |
|---|---|---|---|---|
| R1-1 排程／解除 | 從編輯／清單真正排入 Android OS，正確報告接受／拒絕 | PR #13 已有 schedule ACK、清單啟用確認與失敗回滾；Android B3 程式保留 | 最新候選回歸；特定 OS 精準權限與降級區別 | 已實作；P 未封閉 |
| R1-2 貪睡再響 | 亮屏、右滑貪睡十分鐘後再次響起 | G1 針對冷啟動 FGS、OS/Room 補償、隔離事件與 Session 測試 | 使用者原「亮屏 10 分鐘不再響」的 T3 缺失未封閉 | **R1 P0、T3 未驗** |
| R1-3 正式測試與診斷 | 明確區分立即試聽與 Receiver→Service→Media3 實際測試 | G2 正式 test session、fireId、noBackup 與 incident timeline | 不冒充特定 OEM 起聲；測試不得污染日常鬧鐘／歷史 | T1/T2 既有、T3 未封閉 |
| R1/R2-4 貪睡音樂 | 每鬧鐘「同曲斷點續播」／「下次換另一首」，單曲池可回退 | G3 已有 checkpoint、選曲與恢復接線及回歸測試 | 特定編碼、跨程序、URI 失效下再次起聲 | 已實作；T3/P 待驗 |
| R2-1 大量音樂 | MediaStore＋SAF 遞迴，不逐首勾選，至少 3000 首，索引持久化 | 舊版 3001+、4935 筆索引測試；Room v28、URI 來源索引 | 大曲庫手機掃描／搜尋延遲、撤銷來源後讀回 | T1/T2 有證據；P 部分待驗 |
| R2-2 播放策略 | 單次響鈴連播、允許重複隨機／不重複洗牌／指定清單順序 | 11/15 實機觀察過三首隨機連播；後續策略程式與測試 | 新候選各策略及失效備援的實播 | 基準成立；新候選未 T3 |
| R2/R3-3 歷史與回播 | 真正起聲才記曲名與時間，最近歌曲一鍵回播／歷史列可播放 | v149 曾有實機紀錄與首頁回播；U1/控制器與可操作列存在 | 最新候選 1～2 次操作、回播讓位響鈴 | 已實作；P 回歸 |
| R2/R3-4 系統鈴聲排除 | 一鍵顯示預設鈴聲候選，可取消與 Undo，不暗中刪除 | U5 候選清單、手動確認、回復與串列化操作 | OEM 分類準確性、實際操作流 | T1/T2 有證據；T3/P 未驗 |
| R3-1 五秒快設 | 首頁按＋、設定時間、確認 OS 建立成功 | U3 快速入口、`createQuickAlarm`、ACK 量測已存在 | 使用者五秒整段時間；P50≤5s、P90≤8s 為歷史驗收目標，未實測 | **P 未封閉** |
| R3-2 統一音樂中心 | 音樂庫／播放清單／系統鈴聲單入口，按用途顯示，能套用到鬧鐘 | U2 保存互斥與部分 UI 統一、清單 bulk add/apply | 不同入口的同一設定結果、讀回、立即試聽／排程測試語義 | P 未封閉 |
| R4-1 設定狀態 | Desired / Persisted / Execution / Observed 不混淆；失效時備援不默改原設定 | PR #13 多項競態、雙儲存、ACK、playlist generation 防護 | 跨來源備份 atomicity；實際退避與恢復 | 部分已實作，尚有殘留 |
| R4-2 升級／歷史保留 | 既有鬧鐘、歷史、索引不丟失，Room v28 不破壞 | 既有 v149 覆蓋升級與簽章實測；B3 二次 Runner 簽章通過 | 整合候選升級並讀回；不可推稱所有廠牌 PASS | T2 基準成立、T3 待驗 |
| R5-1 既有三語 | 英文、繁中、日文核心畫面顯示正確 | L1-A/L1-B 主資源及字串；R5 實驗另有 LP0/LP1 | 全 App 動態字串、長字串、通知/響鈴測試 | 部分完成 |
| R5-2 自訂語言包 | 互動建立／編輯／匯入／匯出，能切換單語與任意兩語 | LP0 Web、LP1 Android Debug Lab、LP2 Debug 真實畫面；122/200 抽樣 | 解析器脫離 Debug 白名單、全 App 掃描、正式使用者自訂包路徑 | **R5 主缺口** |
| R5-3 翻譯內容 | 來源對照、術語、候選審查、差異失效、可追溯核准 | G6-Q1+Q2 200 Key × 2 語 400/400 模型初審，27 筆候選 | G1 未核准，Q 0/400；五個靜態引用疑點、21 個格式 Key | 治理基礎完成，Q 未完成 |
| R6 版本與交付 | 單一可追溯可安裝候選、全部 CI 並明示 T3/P/Q | PR #13→#29 堆疊、B3 Android 三 Job SUCCESS、Q2 Node SUCCESS | 新整合分支確切 SHA CI、APK 附件、使用者安裝證據、獨立 main 合併決定 | **A0 本輪進行中** |

## 3. 應避免重開的舊需求衝突

- **仍保留**「單首獨立隨機且允許重複」；Preview 3 後另有「洗牌不重複」「清單順序」，不可因舊 G1 簡化而刪除。
- 「每日依日期更換歌曲」曾延後，保留在需求候選，不假稱已完成；只有經新授權才排入當前工程優先。
- 實際歌曲播放歷史只能由起聲事件記錄；診斷／測試事件走獨立記錄，不污染使用者歷史。
- 語言包不得翻譯或覆寫使用者的曲名、檔案路徑、URI、診斷 reasonCode、DB 欄位與外部 API 契約。
- 既有 Android 主資源已有繁中／日文，不可把「自訂語言包官方核准 0/400」寫成「App 沒有三語」。
- 既有狀態是歷史證據，新增分支要引用精確 SHA，不得把歷史舊測試當最新成功。

## 4. 當前 Release 阻斷

1. **正式 G6 language pack**：G1 術語未核准；G4/G6 獨立語義批准 0/400；歷史 `music_playlist_integration_pending` 的 source defect 雖 S0 更正，舊 G4 `OPEN, releaseBlocking=true` 尚不能憑空解除。官方 pack gate 仍 fail-closed。
2. **產品使用完成宣稱**：R1 亮屏貪睡、R3 五秒操作、R5 自訂完整語言等沒有新版完整 P/T3；可繼續施工，但不能宣稱完成。
3. **發行**：此候選不是正式 Play/F-Droid 發行，不改簽章、Room schema、原有 alarm execution path，也不合併 main。
4. **資料來源**：GitHub Actions artifacts 為短期驗證產物，不是永久批准紀錄；需把 Run ID + SHA + 結果寫入本文。

## 5. 下一輪更新欄位（每項功能都需填）

`id | owner_branch | source_sha | source_file | T0 | T1 | T2_run_and_sha | T3_device_date | P_task_and_measure | Q_authority | open_blocker | next_gate | decision_supersedes`

**禁止**僅憑新增 Test 數量、Key 數量或 CI 綠燈提高整體產品 P 完成率。後續施工序參考 [A0～D0 執行計畫](ACX-A0-TO-D0-PRODUCT-DELIVERY-ROADMAP-20261121.md)。

## 6. 2026-11-21 A0 獨立 CI 重新核對及分線

以下是**工作流來源 GitHub Actions 本身的結果**，不是模型估計：

| 工作流 | 實際提交 SHA | 工作執行與結果 |
|---|---|---|
| [Android P0 baseline](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44889250691) | `5208169dc41beb6deca9ba811b6bd07dcbc66178` | Build F-Droid debug APK、全單元測試、第二 Runner upgrade/signing，三 Job 全 SUCCESS |
| [LP-G1~G6 Node](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44889254791) | `9c721d71585c15a95018dc0dd7151427823436d3` | governed-language-pipeline SUCCESS；官方未審包拒絕發布仍為預期結果 |

**SHA 界線**：兩 Run 皆由 PR #30 的整合分支 `push` 觸發。Android Run 所測 SHA 與 Node Run 的 SHA 相差一次工作流白名單更新，**沒有 Android 程式碼或字串資源改動**。因而 Android 工程基準可視為該候選所含的相同產品 code 狀態通過，但不冒稱兩個 Run 是同一 SHA，也不代表 PR #30 後續文件提交重新跑過三項 Android 工作。

- **A1 起點**：[Draft PR #31](https://github.com/Penguink72/AlarmClockXtreme/pull/31)（`feature/acx-a1-reliability-closure-20261121`），針對精準排程診斷例外做實際程式切片與兩個負向測試；新增程式必須獨立 T2。
- **B0 起點**：[Draft PR #32](https://github.com/Penguink72/AlarmClockXtreme/pull/32)（`feature/acx-b0-full-locale-inventory-20261121`），全 App Node 靜態盤點、每 Key JSON 與測試；[B0 Run 44890316169](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44890316169) SUCCESS（SHA `2acb18faf479d29a231937d6525fa8eb6cdee51f`）；首兩次掃描器測試失敗為 `string-array` 被分類成 `string`，已以規則排序修復，不能引用失敗的舊 Run 為通過。
- B0 XML 獨立讀取確認 `2471` 個英文基底定義（`2427 string + 43 plurals + 1 string-array`），其中 `2462` 可翻譯；繁中與日文 XML 各 912 定義，各缺 1550 個有效基底 Key 的專屬翻譯定義。這些是**翻譯定義覆蓋差距，不是直接可見 UI 比率**。詳細資料在 PR #32 的 CI artifact `acx-b0-full-app-locale-inventory`（暫存）及 [B0 基準文檔](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)。

以上不影響原有 R1 亮屏貪睡 T3 及 R3 五秒實測的未封閉裁定。PR #31/#32 都未合併至 A0 或 main；任何下一輪狀態以各自精確 SHA 和 CI 重驗。

## 7. A1/B0 最新施工證據（2026-11-21，隔離分支不等於已整合）

| 分支 | 可追溯交付 | 本輪裁定 |
|---|---|---|
| [PR #31 A1](https://github.com/Penguink72/AlarmClockXtreme/pull/31) | `recordScheduleIncident` 捕捉同步診斷例外；後續 `safeSnoozeDelayMillis` 先將分鐘數限制 1～180 再以 Long 計算；5 個新增純/整合測試分批提交 | 第一片 [Run 44890033111](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44890033111) 的 864 項單測、APK、跨 Runner 簽章成功；**後續分鐘安全切片仍須自己的新 SHA CI，不能繼承第一片 PASS** |
| [PR #32 B0](https://github.com/Penguink72/AlarmClockXtreme/pull/32) | 2,471 個全 App 基底資源、兩語覆蓋、24 個未解引用、格式契約與疑似硬編碼候選；Node scanner/test 與 TSV 證據已提交 | [Run 44895076599](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44895076599) 已通過：369 格式 Key、738 語言列（254 詞法一致、484 缺專屬定義、0 詞法 mismatch），97 UI literal 分成 25 自然語言、10 資源引用、25 技術識別、12 結構字串、25 待查；不代表語義 Q 核准 |

**長期保存來源**：
- [B0 2471 Key 完整覆蓋 TSV](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/localization/evidence/b0/full-base-resource-coverage-20261121.tsv)
- [B0 CI 來源/摘要與 24 Key](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/evidence/ACX-B0-CI-OUTPUT-SNAPSHOT-20261121.md)
- [B0 25 個自然語言候選與 Debug Lab 原始行](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/evidence/ACX-B0-UI-LITERAL-TRIAGE-20261121.md)

**不得升格**：A1 的純測試不證明亮屏 10 分鐘貪睡已恢復；B0 的靜態引用數不證明全 App 中文/日文已完成；B1/B2 仍未開始正式 runtime 施工。上列兩條分支均為 Draft PR，沒有合併 A0 或 main。

## 8. B0-T1 三語正文與 B0-L1 語系狀態契約（2026-11-21）

### B0-T1｜完整版權威文本

- [Draft PR #33](https://github.com/Penguink72/AlarmClockXtreme/pull/33)，`feature/acx-b0-t1-trilingual-catalog-20261121`，基於 B0 PR #32 的固定 HEAD `0ceab1d1`，與 B0-L1 分支獨立。
- [完整 2471-Key 英／繁中／日文原始文本 TSV](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-t1-trilingual-catalog-20261121/localization/catalog/b0-t1-trilingual-source-matrix.tsv)，有逐條英文 source XML、現有繁中與日文 XML、可讀預覽、per-Key sourceHash、各語 translationHash、來源行號、格式/plural 契約與 UNKNOWN 審核狀態。不是只有「有無翻譯」。
- catalog revision：`sha256:c608e163bb47aeefa169e3ca72b8e86ebd8deb37d89c59e47796d953fe95c70a`；相對 G6 200-Key 審核目錄屬新全量權威，不能繼承舊 400-row 人工批准。
- [Node CI 44898937783](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44898937783) SUCCESS，`--verify` 再生 TSV 與 Repo 檔 byte-identical；2,471 keys、2,462 translatable、各內建 zh/ja 912、各缺 1,550。無自動譯文或人工作業核准。

### B0-L1｜系統／App／自訂／雙語語系狀態轉移

- [Draft PR #34](https://github.com/Penguink72/AlarmClockXtreme/pull/34)，`feature/acx-b0-l1-locale-state-contract-20261121`，基於相同 B0 固定 SHA。
- 已提交純 Kotlin `LocaleSelectionContract.kt` 與 **13 個 JUnit 測試**；情境包括裝置語言更改、內建指定、自訂包有效／缺失、主次語言、同語不重複、Android 外部 AppLocales 變動、自身寫入 ACK、Android 12 API 限制、安全 fallback。
- [狀態轉移矩陣](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-l1-locale-state-contract-20261121/docs/localization/ACX-B0-L1-LOCALE-STATE-TRANSITION-CONTRACT-20261121.md)。
- [Android CI 44899090595](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44899090595) 已啟動，結果必須以 run 最終狀態和 code SHA 裁定。暫未將新增 13 測試視為 PASS。
- **L1 尚未接入運作中的 Android LocaleManager/Settings/Compose/Service**。Android 13+ 原生選擇仍依既有 `AppLanguageManager`；Android 12 以下的內建 App 自選語言尚未正式實作。L1 是已具執行測試的狀態機，不是「所有機型都已能切換自訂語言包」。

**依賴與下一片**：B1 可以同時取用 T1 全量 catalog contract 與 L1 reducer，但必須新建整合分支驗證同步版本、匯入驗證／持久化／原生 fallback 及實際設定頁重啟；B2 才擴及通知、響鈴、安全操作和雙語排版。PR #33/#34 尚未合併 B0、A0 或 main；不因兩個 Node/Android test PASS 而推稱所有 T3 或官方翻譯 Q 完成。
