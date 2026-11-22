# ACX L12-3｜全量 Android 字串預覽、審核帳本與正式發行閘門（2026-11-22）

> **已實作可執行的審核前置程序，不是正式翻譯核准、aapt2 編譯或 APK。** 此分支從 [L11-7＋L12-2 整合 PR #51](https://github.com/Penguink72/AlarmClockXtreme/pull/51) SHA `000d3af5b2834e790465e65bc7ed18ac04ac7dce` 建立，與 L11-8 平行更新。當期候選分母 2,306 筆；合流後須使用最新 L11-8 分母 2,680 重新驗證。

## 一、TCL Root 與執行枝

**Root：** 將 L12-1 的來源及格式 QA、L12-2 的程式操作探針轉換成可供獨立審核及 Android 正式資源製作的**前置資料契約**。保持「模型產生候選、機械規則核查、使用者／獨立審核者批准、Android 編譯與實機驗收」五層權威分離。任何較前層的 PASS 都不能冒充後面一層的 PASS。

| 枝 | 已實作 | 尚未完成、不得宣稱 |
|---|---|---|
| L12-3-S Source Revision | 每列驗證 B0-T1/L11/L12 `catalogueRevision`、`sourceHash`、`translationHash`、`reviewRevision` | 不能推論使用者已認可譯文 |
| L12-3-F Format QA | 再檢查重複 printf 參數、XML resource name、Unicode 轉義；產生只供審核的 Android XML entity/quote 預覽 | **非 aapt2 編譯通過**，不能直接複製到 `app/src/main/res` |
| L12-3-G G1/G4 治理 | 沿用現有 G1 18 概念候選與 G4 審核帳本；明確拒絕模型自簽 | G1 仍為 `UNAPPROVED_FOR_RELEASE`，G4 原始 approvals 為空 |
| L12-3-R Reviewer Intake | 新增空白 `localization/governance/l12/independent-review-ledger.json`，定義完整審核條件 | 只靠編輯 JSON 無法建立獨立批准；需要另外受保護的可稽核審核入口 |
| L12-3-X Official Release | `--mode release` 強制驗證所有工作列、正式詞彙批准、G4 證據、逐列來源/翻譯/審核版本及 aapt2 同版編譯證據；任一缺失 fail-closed | 即使全部理由消失，發行 writer 仍未實作，**本分支永不寫正式 XML** |
| L12-3-Q CI | 六項新增測試＋負向 release 檢驗，獨立 review artifact | 沒有 Android 實機、所有 UI 字串裁切、朗讀、背景服務驗證 |

## 二、工程文件與輸出

- `scripts/localization/l12-b3-android-resource-preflight.mjs`：完整前置檢查與審核表產生器。預設只寫 `build/localization/L12_B3_REVIEW_ONLY_NOT_RELEASE/`。
- `scripts/localization/l12-b3-android-resource-preflight.test.mjs`：六項來源版本、格式重複、XML escaping、偽造簽核及正式發行拒絕測試。
- `localization/governance/l12/independent-review-ledger.json`：**intentionally empty**。審核者資訊、簽核時間與理由均不得由模型或 CI 自行填入。
- `.github/workflows/lpg1-g5-translation-pipeline.yml`：在原有 L11/L12-1/L12-2 測試之後新增 L12-3，並強制證明缺批准時 `--mode release` 會失敗。
- `l12-b3-resource-preflight.json`：逐列 Key、locale、SourceHash、TranslationHash、ReviewRevision、來源位置、結構警告、XML 預覽、未批准狀態。
- `l12-b3-review-queue.tsv`：獨立審核可分發文字表。
- `l12-b3-xml-review-examples.json`：標示 **EXAMPLE_ONLY_NOT_APPROVED_NOT_AAPT2_VALIDATED** 的前 120 筆預覽。
- `l12-b3-gate-summary.md`：CI 當期分母、警示及發行阻擋理由。

執行：

```bash
node --test scripts/localization/l12-b3-android-resource-preflight.test.mjs
node scripts/localization/l12-b3-android-resource-preflight.mjs --out-dir build/localization/L12_B3_REVIEW_ONLY_NOT_RELEASE
node scripts/localization/l12-b3-android-resource-preflight.mjs --mode release
# 最後一行在目前獨立語義批准為零時必須回傳非零錯誤碼。
```

## 三、目前可驗證的前置結果（以最後 SHA CI 為準）

在此分支初版 CI Run #45026429072（提交 `44c68b0ffbcee9c5dd21b5d68f6c1a769ec92459`）已驗證：

- B0-T1 缺專屬 XML 分母 **3,100**，當期可審核 L11 草稿 **2,306**，無候選 **794**。
- 機械層 **0** 結構硬阻擋；**30** 列仍有內容或 Android 百分比警示（兩種警示可能交疊）。
- **4** 列涉及 XML 引號／特殊字元注意事項。
- 來源版本不符 **0**，Android 格式參數重複次數不符 **0**。
- 已執行的 aapt2 編譯 **0**，獨立語義核准 **0**，正式 XML 新增 **0**，APK **0**。
- `--mode release` 正確拒絕：G1 未批准、G4 空白、候選不完整、未附同版 aapt2 證據、並非每一 locale row 都有人類審核。

**注意：** 以上初版 Run 成功不是本文及最新 HEAD 的完成證據；本分支最後 SHA、與 L11-8 合流後，必須重新核對 CI 同版產物。

## 四、真正將候選轉換成正式 XML 的條件

1. 審查每個需要翻譯或需要保留英文的 locale row，釐清 Source/Context、使用者會觸發的真實操作效果、字面格式與術語差分。
2. 不得以 AI、G1 字詞相似度或靜態 Kotlin 錨點充當**獨立語義批准**。正式審核必須有與譯文產生過程可區分的審核者與可驗證的事證，記錄身份、時間、理由、`sourceHash`、`translationHash`、`reviewRevision`。
3. 若英文來源、中文/日文譯文、綁定 Context 或 G1 術語版本變動，任何已做出的決定必須重新核驗，不能沿用舊簽核。
4. 先解決 Android XML 的引號／特殊字元／格式參數／plurals/arrays，再從完整正規資源 staging 執行 `aapt2`，保存同版輸出雜湊與 CI 結果。這一步在本輪**尚未執行**。
5. L13 需另實作**受保護且經明確授權的正式 XML writer**。L12-3 即使所有資料日後齊全，仍不會自行寫入應用正式資源。
6. Android UI、無障礙朗讀、通知、Service、鬧鐘可靠性及 APK 仍由 L14/L15 獨立驗證。

**結論**：L12-3 的工程退出條件是完整來源版本的審核交接產物、可執行的正式發行拒絕與 CI 成功；不是宣稱全部譯文完成或預先授權發行。
