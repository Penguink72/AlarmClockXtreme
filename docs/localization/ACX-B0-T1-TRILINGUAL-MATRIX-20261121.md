# ACX B0-T1｜完整三語逐 Key 文字來源、版本與審核狀態

日期：2026-11-21；基準：`feature/acx-b0-full-locale-inventory-20261121` HEAD `0ceab1d157a3083157b4219369d0925354eb7995`。本片不修改 Android 字串內容、不發行語言包、不升格任何譯文審核。

## 交付與權威

- **完整逐 Key 文字矩陣**：[`localization/catalog/b0-t1-trilingual-source-matrix.tsv`](../../localization/catalog/b0-t1-trilingual-source-matrix.tsv)，共 **2,471 個英文 Android base Key**。按 key 排序，每列包含 Android **英文 raw XML 全文**、人類閱讀預覽、繁中與日文 raw XML／預覽／存在狀態、來源 SHA-256、譯文自身 SHA-256、原始行號及格式/複數屬性。
- **生成器**：`scripts/localization/b0-t1-canonical-matrix.mjs`。單純讀取三份 `app/src/main/res/values*/strings.xml`，不做機翻或改寫；`--verify` 逐 byte 比較當前 XML 重新生成的矩陣是否與已提交版本一致，避免資料漂移。
- **完整 JSON 和摘要**：由 Node CI 生成到 `build/localization/B0_FULL_APP_INVENTORY/b0-t1-trilingual-source-matrix.json`／`b0-t1-matrix-summary.md`，與 TSV 一起作 Artifact。TSV 是永久 Repo 對照文本；JSON 是易搜尋的結構化輸出。
- 使用 `JSON.stringify` 將 TSV 每個儲存格引號化，因此內部換行、Tab、雙引號及 `<plurals><item>` 富文字結構不會造成資料列分裂。原始 Android XML 片段是權威；`preview` 僅是簡化閱讀，**不得當作 Android render 結果**。

## 機械來源與覆蓋

| 指標 | 數值 |
|---|---:|
| base resources | 2,471 |
| translatable=true | 2,462 |
| 內建 zh-TW 專屬 XML 定義 | 912 |
| 內建 ja 專屬 XML 定義 | 912 |
| 可翻譯而未有 zh-TW 專屬定義 | 1,550 |
| 可翻譯而未有 ja 專屬定義 | 1,550 |
| 帶 printf-like tokens source | 369 |
| base plurals | 43 |

> 欄位 `EXISTING_XML_NOT_INDEPENDENTLY_APPROVED` 表示「正式 Android 資源中確有文字」，不能與 G6/G4 的官方候選獨立核准混同；`MISSING_LOCALE_DEFINITION` 表示「無 XML 專屬定義」，Android 可能顯示英文 fallback；`NOT_ASSESSED_BY_B0_T1` 是審核未知，不是拒絕或通過。

### 每 Key 來源綁定

`sourceHash = sha256(JSON.stringify([key, resourceType, translatable, rawEnglishXml, formatTokens, pluralQuantities]))`

`catalogueRevision = sha256(JSON.stringify(sorted([key,sourceHash] pairs)))`

這裡的 `rawEnglishXml` 包含 XML 標記/Android 轉義。英文變動只讓該 Key 的 `sourceHash` 過期，其他 Key 保持原雜湊；XML 空白變動也會保守失效。**此新的全量 catalog revision 並非 LP0 舊 200 Key 或 G6 現行 200 Key 的 revision，不可冒充直接相容。**

重建與 CI：

```bash
node --test scripts/localization/b0-t1-canonical-matrix.test.mjs
node scripts/localization/b0-t1-canonical-matrix.mjs --verify --out-dir build/localization/B0_FULL_APP_INVENTORY
```

## 後續已知差距

1. B0 全量 canonical text 可以作翻譯 UI 的資料來源，但本輪尚無 **人工審核**或 `APPROVED` 清單。G6 400 locale rows 的獨立批准保持 **0/400**。
2. 全 App 既有 Kotlin/Compose/notification 使用位置仍以 B0 詞法掃描為候選證據；24 無直接靜態引用未判為刪除。
3. B1 Validator 需處理原始 XML/複數/array 的具體型別和正確轉義；不可把 `sourcePreview` 當成可直接匯入 Android 的格式化內容。
4. B0-L1 與 B0-T1 **平行隔離**；L1 要只處理語系選擇狀態轉移，B1/B2 才真正接到 UI/Service。
5. 若後續動到詞彙概念或原文（S0），要保留歷史 `review-ledger`/source defect，而非在生成器內批准模型候選。

關聯：[B0 語言掃描基準](ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)；[C1 來源治理](ACX-C1-TRANSLATION-TEXT-EVIDENCE-20261121.md)。
