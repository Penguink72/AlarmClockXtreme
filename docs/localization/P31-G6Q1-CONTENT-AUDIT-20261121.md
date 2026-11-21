# G6-Q1｜現行 200-Key 中日內容初審、候選修訂與審核交接（2026-11-21）

> **權限邊界：MODEL_PRELIMINARY_ONLY，禁止視為獨立語義核准。** 本文中的所有修訂只是候選，不能直接覆寫正式 Android strings.xml、G6 Debug packs、G4 approved ledger 或解除 G5 release gate。

## 來源與這次實際完成的審查工作

- 權威英文：S0 修正後 `app/src/main/res/values/strings.xml`，對應 `g6-200-current-20261120` 的 200 個 Key；當前中日候選使用 `app/src/debug/assets/lp2/packs/{zh-TW,ja}.json`，均為未審核預覽包。
- 凍結歷史：LP0 `lp0-200-20261118`、G4a～G4e 原有模型紀錄及 S0 正式來源修正原地保留，不以本輪改寫過往結論。
- 本輪 **37 Key × 2 語 = 74 筆**初步模型內容分析：CRITICAL 10、HIGH 19、MEDIUM 8 個 Key。記錄來源文字、精確來源雜湊、候選譯文、兩語 origins、可能的操作後果、需獨立核對的差異及修訂建議。
- 在 37 Key 中沿用 G4 既有 **16 條**兩語修訂建議；新提出 **2 條**繁中通知用語修訂，總計 **18 筆修訂建議（14 Key）**。另外，S0 唯一來源變動 `music_playlist_integration_pending` 必須重審，不因文字已修正就自動通過。
- 以 Node 從本次 Git checkout 的 G6 目錄、語言包、G4 舊材料及 CI 重新產生的 G2 靜態呼叫證據組成 **400 筆待審核的 reviewer worklist**；其中 **74 筆已有模型初審註記，326 筆尚未進行這輪內容初審**。原本 200/200 候選覆蓋率不是 400/400 品質核准率。

## 需要特別審查的操作安全與語義

- `music_library_remove_source` — 移除已登錄的來源不是刪除手機媒體檔，原日文「削除」易誤導為檔案刪除。
- `music_playlist_remove` — 從播放清單移除一首曲目，與刪除原始音訊檔不同；原日文「削除」缺上下文。
- `firing_snooze_schedule_failed` — 來源確定鬧鐘繼續響鈴，繁中「應仍在」降低確定性；貪睡重試不等於解除。
- `alarm_edit_schedule_failed_new` — 排程被 Android 拒絕時新鬧鐘未建立，譯文需保留權限檢查及重試。
- `firing_controls_hold_description` — 長按解除會結束當次響鈴，快速貪睡是延後，二者不得合併；需要實機行為複核。
- `firing_status_swipe_or_snooze` — 左滑解除、右滑貪睡不能翻反；文字一致仍需真機測試。
- `firing_swipe_protected` — 左滑受保護須長按解除，日文『無効』可能改變對保護機制的理解。
- `replay_error_alarm_active` — 鬧鐘響鈴時回播必須停止讓位；翻譯不可倒置成停止鬧鐘。
- `alarm_list_delete` — 刪除鬧鐘定義不同於移除音樂來源／播放清單項目。
- `alarm_edit_challenge_default_description` — 完成解除挑戰才可停止鬧鐘響鈴；不能被理解為只停止音樂。

文字表面合理也不能保證執行行為正確：上述 CRITICAL 條目必須搭配實際 UI、手勢、AlarmService／ViewModel 操作後果、替代模式及失敗回退機制確認。舊 G2 只提供程式碼呼叫線索，並非運行時的獨立證明。

## 可直接交給審核者的 18 筆修訂候選

| Key | 語言 | 目前 G6 候選 | 提議替換（未核准） | 來源 |
|---|---|---|---|---|
| `music_library_remove_source` | ja | ソースを削除 | 音楽ソースの登録を解除 | G4 既有模型建議 |
| `music_playlist_remove` | ja | 削除 | プレイリストから外す | G4 既有模型建議 |
| `settings_return_to_app_after_dismiss_description` | zh-TW | 確認鬧鐘停止後返回鬧鐘 App；關閉則返回原本的手機畫面。鎖定畫面仍保持保護。 | 確認鬧鐘已停止後顯示「鬧鐘」畫面；關閉此選項則返回先前畫面，並維持鎖定畫面保護。 | G4 既有模型建議 |
| `firing_snooze_schedule_failed` | zh-TW | 貪睡排程失敗，鬧鐘應仍在響鈴。請重新貪睡或直接解除。 | 貪睡排程失敗，鬧鐘仍在響鈴。請重試貪睡或直接解除鬧鐘。 | G4 既有模型建議 |
| `b2_test_snooze` | ja | スヌーズテスト：1 分後に再通知 | スヌーズテスト：1分後に再び鳴ります | G4 既有模型建議 |
| `alarm_edit_schedule_failed_new` | ja | Android が通知予定を受け付けなかったため、新しいアラームは作成されませんでした。権限を確認して再試行してください。 | Android がアラームのスケジュールを受け付けなかったため、新しいアラームは作成されませんでした。アラームの権限を確認して再試行してください。 | G4 既有模型建議 |
| `settings_pane_personalization_description` | ja | テーマ、解除の難易度、プライバシー、アラーム画面の設定。 | テーマ、起床チャレンジの難易度、プライバシー、アラーム鳴動画面の動作。 | G4 既有模型建議 |
| `settings_alarm_defaults_description` | zh-TW | 設定新鬧鐘的初始行為，減少重複設定。 | 設定新鬧鐘的預設行為，讓設定更快速、結果更容易預期。 | G4 既有模型建議 |
| `settings_alarm_defaults_description` | ja | 新しいアラームの初期動作を設定します。 | 新しいアラームの初期動作を設定し、素早く一貫した設定ができるようにします。 | G4 既有模型建議 |
| `settings_utilities_description` | zh-TW | 計時、統計及其他輔助工具。 | 快速開啟 App 的其他輔助工具。 | G4 既有模型建議 |
| `settings_utilities_description` | ja | タイマー、統計、その他のツール。 | アプリの便利な補助ツールへすばやくアクセスできます。 | G4 既有模型建議 |
| `settings_stopwatch_description` | zh-TW | 開啟碼錶。 | 記錄每圈時間，並特別標出最佳與最差分段成績。 | G4 既有模型建議 |
| `settings_stopwatch_description` | ja | ストップウォッチを開きます。 | ラップを記録し、最速と最遅の区間タイムを強調表示します。 | G4 既有模型建議 |
| `b2_test_requires_saved` | zh-TW | 請先儲存此鬧鐘，再執行正式音樂測試。 | 執行實際音樂響鈴測試前，請先儲存此鬧鐘。 | G4 既有模型建議 |
| `b2_test_requires_saved` | ja | このアラームを保存してから音楽テストを実行してください。 | 実際の音楽アラームテストを行う前に、このアラームを保存してください。 | G4 既有模型建議 |
| `u4_snooze_mode_note` | ja | 音源が利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | アラームの音楽に適用されます。曲を利用できない場合やシークできない場合は、再生可能な代替音を使用します。 | G4 既有模型建議 |
| `notif_snooze_countdown_title` | zh-TW | 已稍後提醒的鬧鐘 | 貪睡中的鬧鐘 | G6-Q1 新模型建議 |
| `notif_snooze_countdown_subtext` | zh-TW | 稍後提醒倒數 | 貪睡倒數 | G6-Q1 新模型建議 |

**解釋：** 先前 G4 的模型建議只作為比較基準；本輪新提的兩條通知標題／副標題改用更一致的「貪睡」語義，仍待核對通知場景及母語可讀性。任何單項建議不直接寫入 G6 200-Key Debug packs。

## 來源更正但仍未完成審核

- `music_playlist_integration_pending`：新版英文已改成「播放清單儲存不可用，音樂庫仍可瀏覽，儲存恢復前變更無法保存」的含義。舊版提及 Room v28 整合等待的問題已在 S0 修改了**正式原文**，但 Q1 的中日仍需人工檢閱／確認 repository-null 情境。
- LP0 歷史 `sourceHash` 與 S0 當前 `sourceHash` 不同。G2 原始靜態情境資料來自舊 LP0 原文，對此 Key 的兩種語言都必須標 `HISTORICAL_SOURCE_HASH_REBASE_REQUIRED`，不可只照舊版情境證據簽核。
- 舊 `localization/governance/g4/source-defects.json` 在 LP0 歷史 revision 仍留有 `OPEN` 的原始來源缺陷。這份紀錄不能因現行 S0 的改寫而被默默清除；將來 G6 對應的正式解鎖需要獨立的已驗證處置紀錄。

## 400 筆人工作業資料

在 GitHub Actions 以 Node-only 產生：

```sh
node scripts/localization/g5-build-language-packs.mjs --mode preview --out-dir build/localization/UNREVIEWED_PREVIEW_NOT_RELEASE
node scripts/localization/g6-rebase-current-catalog.mjs --out-dir build/localization/G6_UNREVIEWED_CURRENT_PREVIEW
node scripts/localization/g2-index-context.mjs --out localization/governance/g2/context-evidence.json
node scripts/localization/g6q1-review-worklist.mjs --out-dir build/localization/G6Q1_PRELIMINARY_NOT_APPROVED
node scripts/localization/g6q1-review-worklist.test.mjs
```

預期輸出在 `build/localization/G6Q1_PRELIMINARY_NOT_APPROVED/`：`manifest.json`、`human-review-worklist.json`、`human-review-worklist.tsv`、`README.txt`。TSV 含空白的審核人／決定／日期／理由欄位，**不接受模型自動填寫，不構成正式 approval ledger**。TSV 文字經跳脫以防不受控的試算表公式執行。

## 發行閘門與下一階段

- G1 術語庫依然 `UNAPPROVED_FOR_RELEASE`；G4 歷史 `review-ledger.approvals=[]`；本 G6-Q1 的 `independentApprovals=0`。正式模式應拒絕，不能從模型初審推得 releaseAllowed。
- Q2：分批由獨立審核者逐條檢查雙語譯文、操作後果、畫面截斷、術語異同與來源 SHA，並記錄可追溯的 reviewer ID、日期、 rationale；退回不合格候選，重提版本後再審。
- Q3：待 G1 術語先有正式核准、G2 來源情境證據符合現行版本、G6 400 筆都通過獨立審核，再另設現行版 approved-only pack 的嚴格門檻；**目前沒有任何正式候選可發布**。

[TCL 流程與分支紀錄](../tcl/P31-G6Q1-REVIEW-ROADMAP-20261121.md) ｜ [Q1 具來源與候選快照的模型初審資料](../../localization/governance/g6q/model-triage-q1.json)
