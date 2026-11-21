# ACX B0｜全 Android 語言資源基準盤點（2026-11-21）

> **來源基準**：R0 A0 候選 `9c721d71585c15a95018dc0dd7151427823436d3` 的實際 XML；依使用者 2026-11-21 授權，B0 已新建全 Repo 的 Node.js 盤點工具和純 Node 測試。本文件的數字是由權威 XML 定義直接核對，與稍後由 GitHub Actions 產生的 per-Key 及靜態呼叫檢查產物分開。沒有執行 UI/T3，就不能寫 P PASS。

## 1. 來源 XML 的可重現計數

| 資料集 | 來源路徑 | 名稱定義 |
|---|---|---:|
| App 基底英文 | `app/src/main/res/values/strings.xml` | **2471** |
| 其中 `translatable != false` | 同上 | **2462** |
| `string` / `plurals` | 同上 | **2428 / 43** |
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
