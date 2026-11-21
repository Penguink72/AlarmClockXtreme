# ACX R5｜B0～B2 完整語言解析器、語言包與顯示架構（設計稿，2026-11-21）

> **這是待施工的 V1 設計契約，不代表正式 App 已完成。** 2026-11-21 遠端實證為：`Lp2PreviewText.kt` 在 Debug、明確開啟、命中 `previewKeys` 才替換文字；白名單 122 個 / G6 實驗 200 個，禁止帶 printf 的 Key；`app/src/debug/assets/lp2/` 的修訂固定為 `g6-200-current-20261120`。舊 LP1 語言包編輯室可創建／編輯／匯入／匯出，但仍是 Debug 實驗而非所有正常 UI 的正式解析系統。

## 1. 產品能力而非翻譯樣本

### 應達成的五個使用者行為

1. 在 App 語言中心建立新 locale（例如 ko-KR），以原始字串和現有中日／英文文本為對照逐條翻譯。
2. 載入自己的 JSON 語言包，檢查格式、修訂與安全，明確告知缺漏／已過時／未審核狀態；可匯出、備份、回復。
3. 選擇一種主語言切換 App 主要可見文字；重啟後保持設定。使用者媒體名稱、URI、外部整合資料不翻譯。
4. 開啟雙語時任選主／次語言，UI 顯示能自行排版而不是無條件字串加換行；短控制詞和安全重要操作要依情境呈現，避免長文字遮住解除／貪睡按鈕。
5. 從任意時期的來源字串更新至新 catalog 時，保留舊包，指出哪幾個 sourceHash 失效，允許重新翻譯或轉換而不靜默覆蓋。

**B0 的第一項不是把剩下 78 個抽樣 Key 接滿，而是全 App 資源與使用位置 inventory**。盤點 Android `res/values*/strings.xml`、`plurals`、`string-array`、XLIFF/HTML/Spannable、直接與間接 R.string、Compose stringResource、getString/getQuantityString、動態 Context、通知／Receiver／Service、A11y labels、文字格式占位符。掃出來源、使用點、uiSurface、資料類別（text/format/plural/array/styled/user-data）和活動狀態；不得假定歷史提及的 2421 Key 就是目前精確分母。

## 2. 建議責任分層

```text
Android resources / source inventory (authoritative English, localized built-in XML)
       ↓ sourceHash, key, type, constraints
CanonicalCatalog  ─── Glossary / context / source evidence (authoring input)
       ↓
PackValidator (offline, pure Node tests + Android parity)
       ↓ valid user pack / built-in language
PackRepository (SAF import/export, atomic replace, archive versions, rollback)
       ↓
LocalePreference (primary, secondary, dual, status; lifecycle persistence)
       ↓
LocaleResolver (text / formatted / quantity / array / fallback / status)
       ↓                            ↓
ComposeUiTextAdapter          AndroidContextTextAdapter
       ↓                            ↓
Home, editor, Music Hub       notifications, service, ringing / A11y
       ↓                            ↓
UI T2, T3, P                    safety-specific T2, T3
```

- **CanonicalCatalog** 對應一次固定版本的 Android 原文；key 為穩定邏輯名稱，`sourceHash` 是 key+原文（以及型別/格式契約）之明確一致性對照。改原文必使對應譯文 stale；無關 Key 不應全部失效。
- **PackValidator** 只驗證包機械結構、來源相容、格式／占位符、大小與資料安全，不自動宣稱語義品質。驗證結果含 `errors`、`warnings`、`fallbackKeys`、`staleKeys`，不能把 warning 誤作 approved。
- **PackRepository** 外部輸入視為不可信純文字資料；匯入先全量驗證，通過後以 AtomicFile 持久化，保留舊版本回退。JSON 單檔為 V1；若未來導入 ZIP，另設解壓路徑穿越／炸彈與總大小限制。
- **LocaleResolver** 不綁 BuildConfig.DEBUG；可於正式 UI 受控使用，但不改 AlarmManager/Room/Media3 等非文字契約。先讀啟用且有效的主語言包，再讀相對應內建 Android locale，最後 fallback 英文原文；雙語分別解析，不能以主語言備援冒充次語言。
- **UI Adapters** Compose 使用可觀察的 UI state 和正規的穩定資源查詢以觸發 recomposition；非 Compose 通知／背景工作在建立通知時根據明確 locale 設定解析。不可在服務中直接依賴 Activity 尚未初始化的 session，亦不可修改使用者已建立鬧鐘的時間。
- **Diagnostics** 記錄缺漏、fallback、stale revision、格式錯誤的 key/revision/locale，不輸出使用者輸入的曲名、URI、文件內容或其他隱私值。

## 3. V1 語言包格式提案（與 LP1/LP2 現有 schema 不可混淆）

V1 建議採 JSON UTF-8、嚴格 Key 集與每一條 sourceHash；它是**新 schema 提案**，不得假設現有 `acx-language-pack-probe/1` 直接相容。

```json
{
  "format": "acx-language-pack/1",
  "locale": "ko-KR",
  "catalogRevision": "catalog-<computed-current-revision>",
  "packRevision": "2026-11-21-local-1",
  "status": "USER_SUPPLIED_UNREVIEWED",
  "entries": {
    "settings_title": {
      "sourceHash": "sha256:<hash-from-canonical-catalog>",
      "type": "text",
      "text": "<reviewer-supplied-translation>"
    }
  }
}
```

上面佔位符明確是 schema 示意，不是已簽核韓文譯文。官方包由另外的 `approvalManifest` 及證據驗證連結，不接受包內自稱 `APPROVED` 的字串當作簽章或授權。使用者自訂包可載入，必須在 UI 標註未經官方審核。

**版本與相容處理：**

- `catalogRevision` 不同時先比每 Key 的 `sourceHash`，未改項可重用，改動項標 `STALE_TRANSLATION`，未知新 Key 使用 fallback；不可直接關閉整個現有 App 或覆寫仍有效包。
- `packRevision` 僅表示使用者包版本，不改官方 `catalogRevision`；對語言別各自保存，不要求所有其他語言一起批准。
- Locale 使用 BCP 47 正規化（例如 zh-TW、ja、ko-KR）；拒絕非法／重複／異常長語言標籤、重複 Key、超大值、深層 JSON、未知格式。保留現有 LP2 256 KiB 限額作**實驗現況**；B0 需根據全 App catalog 大小與記憶體成本重新設 V1 適度上限。
- 既有 LP1/LP2 Debug `acx-language-pack-probe/1` 是匯入舊格式的**顯式轉換來源**，不能改寫原有歷史樣本或讓新正式解析器暗中假設兩版完全相同。

## 4. 分類式解析 API（設計，不是現有類別）

```kotlin
interface LocaleTextResolver {
    fun text(key: String, locale: String): ResolvedText
    fun formatted(key: String, locale: String, args: Map<String, Any>): ResolvedText
    fun quantity(key: String, locale: String, count: Int, args: Map<String, Any>): ResolvedText
    fun array(key: String, locale: String): List<ResolvedText>
}
data class ResolvedText(
    val value: String,
    val origin: String,
    val fallbackUsed: Boolean,
    val stale: Boolean
)
```

`args` 需要由 catalog 中的具名參數型別（integer/string/number/date）機械驗證，避免把序號錯配成不同語義；Android `%1$d`、`%2$s`、`%%`、轉義、換行、複數分支與相同值重複出現都要測。禁止把無參數 `text()` 套到 21 個格式 Key；在成功格式化以前 fallback 回安全內建 `getString/getQuantityString`。

**建立 generated key↔R.string 映射**（從 XML 與實際 callsite 產生），不要靠執行期推測字串內容、搜尋翻譯字面或 Android `getIdentifier()` 滿足覆蓋率。動態 id／非 R.string 使用者資料應標 `DYNAMIC_CONTEXT` 或 `NON_TRANSLATABLE_DATA`；已確定未使用的舊 key 標 `LEGACY_UNUSED`，仍保留歷史來源對照。

## 5. 單語、雙語、通知與響鈴安全

- 單語：`primary` 一套解析結果；缺漏時使用內建同 locale 或權威英文 fallback；調試資訊只在 Debug/語言工具頁顯示，不將 `[EN fallback]` 技術提示直接混入正式響鈴控制。
- 雙語：`primary` 與 `secondary` 各自回傳語言與 fallback 來源，Compose 將兩行獨立 Text、各自語意標記、最大行數／展開控制；不能靠 `one + "\n" + two` 無條件拼接。
- 安全控制：鬧鐘停止／貪睡的高風險文字，在未經該 locale 的實際操作語義核對時使用已驗證的內建本地化資源；不能用模型未審候選更改按鈕操作的意義。翻譯不得影響 onClick、PendingIntent action、AlarmService 或通知發送的結果。
- 排版：較長文字、字級縮放、RTL、TalkBack/ScreenReader、窄畫面、橫向、對話框捲動，擇代表場景測試。數量格式與日期採對應 locale，不把使用者 URI、歌曲標題、檔名做機翻。

## 6. 必要負向與端到端測試

- 機械負向：錯 locale、未知 key、重複 key、惡意控制字符、非法 formatting、錯數值型別、來源 stale、巨大包、原子寫入中斷、損壞重啟後 fallback。
- 使用行為：新增一條 ko-KR 翻譯→匯出 JSON→清除自訂選擇→重新匯入→選 ko-KR 單語→實際設定頁顯示→重啟保留；再啟用 ko-KR + zh-TW 雙語，缺字串要有辨識得出的 fallback。
- 既有高風險：`firing_snooze_for_minutes` 的參數、貪睡／解除分界、音樂清單 remove vs delete、獨立響鈴通知、音樂回播被真正 alarm 搶占。
- 真正完成分母：全 App 有效產品文案引用數、不同資源類型覆蓋、可切換畫面數、格式成功率及代表性 T3/P；不要用 `200/200` 當全產品完成。

## 7. 來源與待處置記錄

- 現況：`app/src/main/java/com/sysadmindoc/alarmclock/ui/settings/Lp2PreviewText.kt`、`app/src/debug/assets/lp2/`、`app/src/debug/java/com/sysadmindoc/alarmclock/ui/languagelab/`。
- 來源治理：`docs/localization/LP-G1-GLOSSARY-CONTRACT-20261119.md`；`docs/localization/LP-G5-PACK-RELEASE-POLICY-20261119.md`；`localization/governance/g6q/model-triage-q1.json`、`model-triage-q2.json`。
- 此契約僅屬 B0～B2 預備設計；真正改 Android Runtime 時須另設隔離 PR、精確 Kotlin 呼叫點驗證、完整 CI 及必要 T3/P。
