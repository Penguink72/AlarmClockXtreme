# TCL｜G6-Q1 現行雙語內容審核與完整交接（2026-11-21）

## 根
使用者要求將 G6 的候選翻譯逐條對照實際英文來源、詞彙與操作結果，真正推進內容審核，而不讓模型自我宣布 400/400 已批准。現行 App 的 LP2 Debug 可顯示候選，不代表翻譯品質達到正式發行標準。

## 施工與父子關係
- Base：LP2-B3 `feature/p31-lp2b3-direct-indirect-ui-preview-20261121`（`1d0920edf`），該分支 Android T2 已完成；Q1 自獨立分支只新增 `localization/governance/g6q/`、Node 審核腳本、文件和 workflow，避免與平行 UI 更新衝突。
- 不改 app Kotlin／Room／AlarmService／AlarmManager／Media3，正式 Android string resources、G4 ledger、G1 glossary、S0 source amendments 全部不變。
- 資料：`localization/governance/g6q/model-triage-q1.json` 37 Key 的來源與中日候選快照／逐條問題／候選修訂，不是獨立核准。完整 400-row queue 由 `scripts/localization/g6q1-review-worklist.mjs` 在 CI 當次產生供審核。

## TCL 狀態枝
| 枝 | 工作與證據 | 結果／下一步 |
|---|---|---|
| Q0 Sources | G6 200 英文 Key、S0 唯一修訂、400 當前中日候選、G1/G4 版本 | 來源受 SHA 驗證；內容尚未獨立核准 |
| Q1 Prioritized model triage | 37 Key、74 個 locale rows，16 筆 G4 舊提案、2 筆新建議 | 內容初審／審核交接，不是 APPROVED |
| Q1 400-row reviewer worklist | 從現行 G6 catalog + packs + 當次生成的 G2 callsites 合成；TSV 空白獨立審核欄位 | 400 筆可供實際核對；未初審 326 筆 |
| Q1 Safety | 10 Key 屬 CRITICAL，含解除／貪睡、排程失敗、刪除／移除、響鈴優先 | 必須有真實操作情境，字面相同不等於語義核准 |
| Q2 Independent review | G1 術語核准、逐條中日與操作方向、來源 hash、審核者證據 | **尚未完成** |
| Q3 Release | 必須 200/200 zh-TW、200/200 ja 真實有效批准，且無阻斷性來源缺陷 | **禁止發布**，不寫入正式資源 |

## 需要拒絕的偽核准
- LLM 補足 45 Key × 2 語並不等於翻譯獨立審核；歷史 G4 四批模型初查也不得繼承為 APPROVED。
- S0 原文修訂會使舊來源雜湊失效：必須保留修訂後獨立審核的空欄，不能自動挪用舊 200-Key Q/A。
- 用戶的語言切換可用性與 Debug APK 通過，不能替代文字品質、法律／權限／風險提示及真正手機端操作驗證。
- 對機械測試、候選 TSV 與 Node CI 一律使用 PRELIMINARY / UNAPPROVED 欄位；如果偽造 approval、來源、提議版本或 G1 release 狀態，驗證必須拒絕。

## CI 與後續
- G6Q1 Node 指令：`node scripts/localization/g6q1-review-worklist.mjs --out-dir build/localization/G6Q1_PRELIMINARY_NOT_APPROVED`，另跑 `g6q1-review-worklist.test.mjs`。依賴的 G2、G5、G6 都先經現有 GitHub Workflow 重新生成。
- CI 必須明示 400／400 筆工作資料、74 筆模型註記、18 筆候選修訂、0 真實核准、release deny；產物只供人工檢閱。
- Q2 可接續高風險 10 Key 的 UI／程式情境確認，再完成其他 326 筆的分類初審；正式批准須由有證據的獨立審核流程產生，不在此階段代理簽章。

[37-Key 中日初審與 18 筆修訂候選](../localization/P31-G6Q1-CONTENT-AUDIT-20261121.md)
