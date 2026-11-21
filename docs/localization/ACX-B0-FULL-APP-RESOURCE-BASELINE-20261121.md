# ACX B0｜全 Android 語言資源基準盤點（2026-11-21）

> **來源基準**：R0 A0 候選 `9c721d71585c15a95018dc0dd7151427823436d3` 的實際 XML；依使用者 2026-11-21 授權，B0 已新建全 Repo 的 Node.js 盤點工具和純 Node 測試。本文件的數字是由權威 XML 定義直接核對，與稍後由 GitHub Actions 產生的 per-Key 及靜態呼叫檢查產物分開。沒有執行 UI/T3，就不能寫 P PASS。

## 1. 來源 XML 的可重現計數

| 資料集 | 來源路徑 | 名稱定義 |
|---|---|---:|
| App 基底英文 | `app/src/main/res/values/strings.xml` | **2471** |
| 其中 `translatable != false` | 同上 | **2462** |
| `string` / `plurals` / `string-array` | 同上 | **2427 / 43 / 1** |
| 繁中 Android 內建 | `app/src/main/res/values-b+zh+Hant+TW/strings.xml` | **912** |
| 日文 Android 內建 | `app/src/main/res/values-ja/strings.xml` | **912** |
| 繁中 string / plurals | 同上 | 900 / 12 |
| 日文 string / plurals | 同上 | 900 / 12 |
| Wear 英文 | `wear/src/main/res/values/strings.xml` | 5 |
| Debug 英文額外定義 | `app/src/debug/res/values/strings.xml` | 18 |

- 用 Android 基底 XML 的 translatable 定義作分母，繁中和日文各**缺 1550 個權威基底 Key 的專屬翻譯定義**，現有的各 912 Key 都可以在英文基底找到對應項。兩語的 Key 缺漏集合相同，但**不能因此宣稱字面翻譯相同或正確**。
- 「缺語言專屬翻譯」不等於必須翻譯該鍵；部分專有名詞、資料、技術或保留英文是可接受決定，B0 後續需逐 Key 審查 `intentionalFallback`、`shouldTranslate` 或 `unknown`。
- 此前的 LP0/G6 **200-Key 抽樣**並不是全 App；200/2462 約 8.1% 的可翻譯資源定義。LP2 的 122/200 Debug 白名單則約 5.0% 的可翻譯基底定義，而且這些百分比皆不是**執行時 UI 覆蓋率**。
- 這是 XML 定義數的詞法計算，不包括使用者自建 locale、實際存在的動態 UI 呼叫次數，也不是可見畫面總數。

## 2. 已提交的 B0 實作

- `scripts/localization/b0-resource-inventory.mjs`：對完整 `app/src/main/res`、`app/src/debug/res`、`wear/src/main/res` 做 source Key/type/format/qualifier 盤點；掃描 `app/src/main`、`wear/src/main` 的 Kotlin/Java/XML 直接資源引用；保留 `NO_STATIC_REFERENCE_FOUND` 而不擅自判 unused。
- `scripts/localization/b0-resource-inventory.test.mjs`：測 XML comment、CDATA、format/plurals、重複 Key；實際掃全 repo 驗證分母超過 200、原有 2 個疑點資源 key 仍可找到、兩語 XML 確實存在。
- 此分支 `.github/workflows/lpg1-g5-translation-pipeline.yml` 新增 B0 Node 掃描測試與 artifact `acx-b0-full-app-locale-inventory`，輸出 `build/localization/B0_FULL_APP_INVENTORY/full-resource-inventory.json` 和 `summary.md`。
- B0 artifact 的 `sourceKeys[]` 會記：Key、型別、來源檔與行、帶參數和富文字標記、限定語系是否存在、直接呼叫點（截斷標記）以及沒有直接引用的狀態。**Node/CI PASS 只證明這項詞法盤點可重現，不是所有內容的語義批准**。

## 3. 接下來的 B0 整合工作

1. 依 artifact 實際數值分組 `B0-1 format/plurals`、`B0-2 Compose direct`、`B0-3 Context/notification/background`、`B0-4 dynamic or generated ID`、`B0-5 Wear`。
2. 對 1550 缺少本地專屬定義的 Key 分 `SHOULD_TRANSLATE / INTENTIONAL_FALLBACK / NON_TRANSLATABLE / UNKNOWN`，不能機器直接把欠缺全部當需修正 Bug。
3. 依 B0 每個 Key 的型別建立 `CanonicalCatalog` 與 generated R.id mapping，驗證「同一 Key 同一 formatter」和官方原文 hash 後再進入 B1 的使用者包匯入；不要靠原先 200 Key 手寫白名單覆蓋。
4. B0 最後完成還需要處置動態 ID/間接查詢及通知／Service 程式上下文。此切片是**第一批全 App 靜態盤點引擎**，並非全部可見 UI 已自動接上語言解析器。

參考 [R5 解析器／語言包設計](ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md) 和 [R0 產品狀態帳本](../tcl/ACX-R0-FULL-PRODUCT-STATUS-LEDGER-20261121.md)。

## 4. 實際全程 Node 盤點成果與最新 T2

[B0 Node 成功 Run 44890645147](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44890645147) 在 scanner SHA `b738e747f74e54b32ea99448fe9b3f19a0dde72e` 成功執行，Artifact：`acx-b0-full-app-locale-inventory`。該 Artifact 帶有 sourceSha、逐 Key、最多 20 筆直接引用位置、所有語言專屬缺漏與潛在 UI literal 候選；到期前可重取，之後直接使用已提交的 Node scanner 重建。

| 指標 | 實測結果 |
|---|---:|
| 全 App 基底資源 | 2471 = 2427 string + 43 plurals + 1 string-array |
| 可翻譯基底 Key | 2462 |
| 含 printf / 字串格式符號 | 369 |
| 含富文字標記 | 44 |
| 有直接靜態引用的 Key | 2447 |
| **找不到直接靜態引用**（不等於未使用） | **24** |
| Kotlin/Java/XML 掃描的使用點檔案 | 312 |
| 疑似硬編碼 UI 文案位置（候選、尚未核准） | 97 |
| G6 200-Key 抽樣落在基底中的交集 | 200 |

24 個未找到直接引用的 Key 包含 `music_library_tab_*`、`alarm_music_settings_*`、`music_library_history_row` 等，與先前 G2 的五個疑點相互印證。其餘仍需逐筆辨認有無動態引用，不能靠這個數據直接刪除 Key。

97 個候選位置**不是 97 個確定硬編碼缺陷**：不少是動畫命名、數值、符號、`@string/` 參照或 UI 組字。但 Debug LP1 Language Lab 內有直接 `Text("…")` 繁中文字面，例如「語言包資料無法載入」「匯入 JSON」「建立」與多段編輯提示；若希望語言包工具本身支援不同語言，這些文字應列入後續 B1/B2 的真實 UI 掃描，而不是假設 R.string 覆蓋已包含。

本次源資料分類與文字篩選仍是**語法與靜態證據**，不代表任意 locale 已在正常 Release UI 中可切換。若新增掃描器或資源變更，需另用新 SHA 的 Node CI 產生新的 artifact；本次基準不外推到未來代碼。
