# ACX L11-3｜優先翻譯候選與剩餘缺漏分層（2026-11-22）

> **候選工程文件，不是正式翻譯批准。** 本輪僅更新 Node.js 候選／測試／文件／CI，不修改 Android XML、Kotlin、Room、AlarmService、Media3、G4/G5 審核帳本、APK 或主線。基於 L11-2 SHA `470451639427598b7785f61baa7339991edb1f8a` 建立獨立分支。驗收以最後 HEAD 的 GitHub Actions 結果為準。

## 0. TCL Root／枝／業

**根**：以 B0-T1 英文 XML 的真實 Source、L10 的使用點與來源雜湊為依據，繼續為鬧鐘響鈴、音樂來源與解除操作生成中日雙語候選。其餘沒有候選的缺漏不能全部視為理所當然的「應翻譯」，也不能因為看似技術名稱而自動排除；需留下 **evidence-bounded candidate-only disposition** 供後續翻譯／審核查詢。

| 枝 | 本輪可查詢成果 | 必須保留的責任界線 |
|---|---|---|
| L11-3-M | 音樂／鈴聲池／解除與控制 68 Key × 2 = 136 筆 | 從池移除 ≠ 刪手機媒體檔；播放結束 ≠ 使用者主動解除 |
| L11-3-F | 響鈴挑戰／權限／可靠性 89 Key × 2 = 178 筆 | 顯示文案 ≠ Runtime 行為已經核實 |
| L11-3-G | 未有候選的 locale 工作列逐條分層 | 靜態候選分層 ≠ 正式採納或刪除允許 |
| L11-3-Q | 6 項負向 Node 測試＋既有完整流程 | 許可、格式、來源和批准不合併 |
| L11-3-X | 供下輪複核的 JSON／TSV 與來源 SHA | 文本及 CI 不表示已發布 |

**業**：一個 Key 在不同 UI/Service Context 可能有不同後果；譯文與正確的來源連結並不能自動證明那個行為真的存在，不能讓 AI 自己把候選改為「獨立審核批准」。

## 1. 新增真實候選

- `localization/candidates/l11-b3-music-alarm-drafts-20261122.json`：68 個 Key，含鈴聲池、隨機播放、從鈴聲池移除、貪睡與解除、音訊淡出、清醒確認、網路廣播等。
- `localization/candidates/l11-b3-firing-permissions-drafts-20261122.json`：89 個 Key，含響鈴手勢、起床挑戰與進度、語音／手寫辨識、定位解除、權限與準備狀態等。
- `scripts/localization/l11-translation-worklist.mjs`：以來源與格式契約導入第三批並保留舊 L11-1／L11-2／G6 的授權與版本界線。
- `scripts/localization/l11-b3-source-dispositions.mjs`：對目前沒有候選的缺漏建議分類。
- `scripts/localization/l11-b3-draft-disposition.test.mjs`：6 項拒絕錯誤翻譯、覆寫、來源漂移、未審核升格及錯誤刪除的測試。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：執行上述腳本並上傳既有 B0 artifact。

## 2. 實測數字與來源

[成功 GitHub Actions Run #45007795375](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/45007795375)；來源提交 `b6fcde6702840fe2e415d8fe8220b0c1b758eaaa`；整個 Node 工作 SUCCESS，L11-3 新測試 **6/6 PASS**。

| 指標 | 實測 |
|---|---:|
| Base／可翻譯 Key | 2,471／2,462 |
| 繁中缺專屬 XML／日文缺專屬 XML | 各 1,550 |
| 雙語缺漏分母 | 3,100 |
| L11-1／L11-2／L11-3 | 146／398／314 未審核 locale 候選 |
| 可復用 G6 舊候選（仍未審核） | 84 |
| **累積可審候選** | **942** |
| **仍無翻譯候選** | **2,158** |
| 既有 locale XML 定義，保留不變 | 1,824 |
| 人工獨立批准／本輪正式 XML 寫入 | 0／0 |

## 3. 殘留類別：全是待確認的建議

| Candidate disposition | 目前實測 | 需要的後續證據 |
|---|---:|---|
| `SHOULD_TRANSLATE_CANDIDATE` | 2,124 | 英文自然語言＋靜態使用點線索；須核對 Context 並補譯 |
| `INTENTIONAL_FALLBACK_CANDIDATE` | 16 | 像 URI、URL、技術代碼、占位符；須決定是否真的保留原文 |
| `UNKNOWN_STRUCTURE_NEEDS_REVIEW` | 4 | XML、特殊轉義或結構；先結構檢查再翻譯 |
| `UNKNOWN_MAPPING` | 14 | 沒有找到靜態引用；不可據此推斷未使用或刪除 |
| **合計** | **2,158** | **全部仍需人類／本地 Owner 判斷** |

另有 9 個 base Key 明示 `translatable=false`，不在 3,100 個缺專屬語系定義的分母裡。來源文案、對應位置和推定角色是三種不同證據；分類規則不能冒充獨立語義審核。

## 4. 必須核對真實行為的例子

1. `alarm_edit_remove_ringtone`：將一項從鈴聲池移除；不能誤譯成刪除手機中的音樂或影片。
2. `alarm_edit_ringtone_pool_hint`：目前文字描述池與單一設定的覆蓋關係；必須以當期 P1～P3 音樂工作區行為為準。
3. `alarm_edit_dismiss_song_end`：播放結束可自動停止；需區分自然結束、迴圈播放和貪睡中斷。
4. `wake_confirm_keep_protection`：尚未確認清醒時的再次響鈴次數；需與實際安排一致。
5. `alarmfiring_waiting_for_a_location_fix_leave`：以 m 為單位的定位解除範圍；不得把操作預設成一定能取得位置權限。
6. `settings_firing_controls_description`：保留螢幕閱讀器使用者選擇按鈕的無障礙語義。

## 5. 建置與驗證

```bash
node --test scripts/localization/l11-translation-worklist.test.mjs
node --test scripts/localization/l11-b2-draft-batches.test.mjs
node --test scripts/localization/l11-b3-draft-disposition.test.mjs
node scripts/localization/l11-translation-worklist.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
```

原有 L11 輸出保留；新增：
- `l11-b3-source-disposition-suggestions.json`：全部 2,158 個未有候選 locale 工作列與來源／推定理由；
- `l11-b3-source-disposition-suggestions.tsv`：可篩選的明細；
- `l11-summary.md` 加入本批及各類統計。

## 6. 下一枝的停止條件

- **L11 下一批**：優先消化 2,124 個 `SHOULD_TRANSLATE_CANDIDATE` 中的可見操作路徑，而非翻譯所有技術代碼；其他類別維持待查並保留分母。
- **L12**：格式／複數／arrays、術語、Context、實際行為及有批准證據的內容才可進正式 Android XML／V1 語言包。
- **L13／L14**：讓語系實際套用到 Compose、通知、Service 並做必要的實機驗收，最後才交付 APK。
- 不需在此時引入 Scala Compiler、TGG Runtime 或另一套共享 Owner；本輪差分由 SourceHash、候選/使用點及 Node 測試承擔。

**完成定義：L11-3 候選檔、剩餘缺口的可重建分類和測試通過。不是全 App 翻譯完成，也不是已發行新 APK。**
