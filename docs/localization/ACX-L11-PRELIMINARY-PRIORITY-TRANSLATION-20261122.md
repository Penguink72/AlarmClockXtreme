# ACX L11-1｜全語系缺漏佇列與優先繁中／日文候選（2026-11-22）

> **純 Node 翻譯候選與文件；不修改 App 內建翻譯 XML、不編譯新版 APK、不發行官方語言包、不核准 G1/G4/G5。** 此文件位於 L10 隔離分支的後續 L11 分支，不能代表主線已合併。基準 L10 commit `5175a64e7ef81ed09393bec31b3f80f3cfdf6dfc`；CI 驗收須查看後續 L11 精確 SHA。

## 0. TCL Root／枝／業

**Root**：把 B0-T1 的 2,462 可翻譯英文來源、每種 locale 1,550 個缺少專屬定義的項目，依 L10 使用點、字串型別、G6 初審材料轉成**可以逐條審核的完整工作佇列**，而非一次全量機翻且自動灌入 Android XML。先提供 Welcome／Onboarding、可靠性與權限、Webhook／連線／假日服務等核心畫面雙語候選，以供 L12 審核和 L13 Runtime 整合。

**枝與業**：
- L11-A 缺漏完整分母：依當期 L10 Graph 重新算，不硬填 2026-11-21 快照比例。
- L11-B 術語／Source 綁定：sourcePreview 必須與現有英文完全一致，保留原始 sourceHash；不重設舊 G6 source revision。
- L11-C 舊材料復用：只在 G6 200-Key 的英文字面、參數契約與當期來源吻合時，將其作 `G6_DEBUG_MODEL_CANDIDATE_REUSED_UNREVIEWED`；不繼承 G4/G5 approval。
- L11-D 新增初稿：`localization/candidates/l11-priority-unreviewed-drafts-20261122.json` 中 73 Key × zh-TW／ja＝146 筆，逐筆列 English Expected Preview 和兩種草稿。高風險語義須人工/獨立審查；不是正式翻譯。
- L11-E 差分／衝突：舊 G6 Source 不同則標 `SOURCE_DRIFT_HOLD`；G6 與新 L11 草稿不同則保留雙案供人類選擇，絕不靜默覆寫舊草稿。
- L11-F CLI／CI：來源版控、格式 token、是否已存在 locale 定義、語義批准越權等都以 Node 測試守門；不改 Kotlin、Room、Service、Media3 或音樂池。

## 1. 本片實際交付

| 路徑 | 功能 |
|---|---|
| `localization/candidates/l11-priority-unreviewed-drafts-20261122.json` | 73 個經英文預覽鎖定的中日譯文初稿（不覆寫任何 XML） |
| `scripts/localization/l11-translation-worklist.mjs` | 合併 L10 Source Graph、G6 Debug 候選，產生**全量**缺漏分類及第一批 review bundle |
| `scripts/localization/l11-translation-worklist.test.mjs` | 全量分母、source drift、格式占位符、保護現存 XML、不能假造 approval、雙語優先工作列 |
| `.github/workflows/lpg1-g5-translation-pipeline.yml` | L11 測試／生成後上傳至既有 B0 Artifact |

### 新增 CI Artifacts

| 輸出 | 內容 |
|---|---|
| `l11-full-translation-queue.json` | 每缺漏 locale 一列，含 key、sourceHash、譯文候選與優先序、L10 使用點線索 |
| `l11-priority-review.tsv` | 有候選的 Key／語系，JSON-quoted 儲存格供未來審核 |
| `l11-source-drift-holds.json` | 舊 G6 英文／格式不符，不准自動復用 |
| `l11-summary.md` | 來源 SHA、候選數、缺漏總量及可審核／未翻譯之區別 |

在本分支：
```bash
node --test scripts/localization/l11-translation-worklist.test.mjs
node scripts/localization/l11-translation-worklist.mjs --out-dir build/localization/B0_FULL_APP_INVENTORY
```

## 2. 本階段的「完成」不等於整個 L11 結束

本片 L11-1 可驗收：
- 2,462 個 translatable base Keys 各語系完整缺漏佇列可再生；原本 XML 的 912 個既有專屬定義保持原樣。
- 新 73 Key 兩種語系的 sourcePreview 與當期英文明確對照，含相同格式占位符；任何不符 fail-closed。
- 舊 G6 候選只可當未核准資料，並記來源差異和新舊兩案。
- CI Run 與程式 SHA 可讀回，沒有修改鬧鐘系統資料或正式翻譯。

未完成：
- 尚未逐一確認其餘缺漏屬 SHOULD_TRANSLATE／INTENTIONAL_FALLBACK／NON_TRANSLATABLE／UNKNOWN，本文的分類是**候選**。
- 73 個新 Key 的中日譯文尚待 G1 術語、程序行為與母語表達審核；先前 G6 200-Key 的 Q0/400 獨立批准亦不因這次測試升格。
- 對需要複數、標籤或 XML rich text 的項目仍須 L12 專用生成和結構驗證。
- 正式 Android resources、LocaleCoordinator／B2 resolver 接線及 APK 更新屬 L12/L13/L15，不能把本片 Draft PR 當成版本發布。

## 3. L11 後續完整翻譯進程

**L11-2**：處理剩餘缺漏中的高頻設定、鬧鐘與音樂中心詞條；對 TGG/Partial-State 研究只保留 `contextRevision?` 和更新意圖為候選，不建立新框架。

**L11-3**：依 L10 的 binding 註記對高風險翻譯（停止／貪睡／移除來源／刪除／Webhook／權限）取用程式操作證據。缺直接/間接使用點則標 UNKNOWN，等待 L10 補證；不能只根據字面採納。

**L12**：將符合結構與內容 gate 的特定語系候選轉成 XML 和可用 pack，保留 reviewer evidence；未核准的自訂包與官方包分開信任等級。

**L13**：把有來源的翻譯實際套用到正常 Android UI、Notification、Service；優先排除 Welcome 1–4、Wake readiness 和整合頁面英文 fallback。

## 4. 權威與反證

本地 `USER_SUPPLIED_UNREVIEWED` 自訂包和 `OFFICIALLY_REVIEWED` 發布要維持不同權限。L11 新聞字句、UI 文案和 G6 模型候選全部都不是正式批准；新多語候選不能造成音樂清單增減、媒體檔案刪除、鬧鐘排程和背景服務改變。

Source 文字不符、兩個語系佔位符對不上、舊 XML 已存在、有人嘗試把 `independentApproval` 改成 true，都必須讓本片檢查拒絕或保持 HOLD，而不是自動補資料湊完成率。

**Repo 文件入庫 ≠ 主線完成；L11-1 候選產出 ≠ L11 完整的內容/品質驗收。**
