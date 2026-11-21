# B0｜97 個疑似硬編碼文字的首輪用途分層（2026-11-21）

## 來源／界線

- [CI Run 44895076599](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44895076599) 成功，`acx-b0-full-app-locale-inventory` artifact 中 `unresolved-and-ui-literal-triage.json` 逐位置記錄 GitHub 已提交的 Kotlin/XML path、line、片段及 `NOT_RUNTIME_VERIFIED`。
- 本工具只找出 **candidate**。沒有螢幕取證及呼叫路徑證明，**不得把 97 項宣稱成 97 個實際缺陷**，也不得讓模型自行把新譯文標成已核准。

| 類別 | 筆數 |
|---|---:|
| 自然語言候選 `HUMAN_LANGUAGE_CANDIDATE` | **25** |
| `@string` 等資源引用、非硬編碼 | 10 |
| 動畫／變數／其他疑似技術識別字 | 25 |
| 動態內插／純結構符號 | 12 |
| 需要看上下文 | 25 |
| **合計** | **97** |

## 25 筆自然語言候選的程式分布

| 分布 | 筆數 | 應採取的動作 |
|---|---:|---|
| `app/src/debug/java/.../ui/languagelab/Lp1LanguageLabActivity.kt` | **20** | B1 對語言包工具自身的 UI 分離可見文案與資料，不再硬寫中文 |
| `app/src/debug/AndroidManifest.xml` | **2** | B1 語言實驗畫面 Activity label 建立適當資源 |
| `app/src/main/java/.../service/WebhookService.kt` | **1** | 追查 `Test Alarm` 是否為網路負載／診斷文字，不得不加判斷而翻譯 API 傳輸內容 |
| `app/src/main/java/.../ui/stats/StatsScreen.kt` | **2** | 一處 `$current / $goal days` 含英文單位，一處為 `stringResource(...)` 動態組字，需辨別格式器與偽陽性 |

### LP1 Debug 語言工具的具體候選位置

來源 `app/src/debug/java/com/sysadmindoc/alarmclock/ui/languagelab/Lp1LanguageLabActivity.kt`：

| 原始程式行 | 目前字面或顯示文字 |
|---:|---|
| 70 | 語言包資料無法載入 |
| 72 | 返回 |
| 124 | LP1 語言包實驗 |
| 125 | 返回 |
| 132 | 200 個真實字串。支援離線 JSON 匯入、編輯、重啟後保存及雙語預覽。 |
| 135 | 匯入 JSON |
| 138 | 匯出（後接動態值） |
| 146 | 新語言，如 ko-KR |
| 168 | 建立 |
| 172 | 主要語言 |
| 179 | 對照語言 |
| 196 | 搜尋 Key 或英文原文 |
| 205 | 字串項目：（後接動態值） |
| 215 | 原文：（後接動態值） |
| 228 | 變數格式不符，無法套用 |
| 243 | 儲存這條翻譯 |
| 245 | 即時預覽 |
| 248 | 主要語言缺漏：使用英文備援 |
| 251 | 對照語言缺漏：使用英文備援 |
| 255 | 不會更改已儲存鬧鐘、響鈴服務、通知與正式語系。 |

**優先級**：B1 的語言包作者工具不可只對一般 App UI 進行解析，否則使用者將得到「主 App 能顯示新語言，編輯語言包的工具仍固定繁中」的矛盾介面。本清單有具體程式行與既有字面，應先盤點完整畫面再設計替換，**此輪不擅自改 Debug Lab 以免影響 LP1 實驗基準**。

## B0 尚未封閉

- `src/main` 裡的疑似硬編碼候選必須沿實際 UI 或非 UI API 來源判讀，不能全部交給翻譯。
- `24` 個沒有直接靜態引用的 Key 仍為 `NO_STATIC_CALLSITE_YET`，不是可刪除清單。
- `B0_FORMAT_CONTRACT_REVIEW` 的 369 格式 Key／738 語言列及 97 位置初審只屬詞法證據，真正參數格式化、plural 排版與單／雙語切換留待 B1/B2。

關聯：[B0 全產品基準](../ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)；[R5 語言 Runtime 與 V1 Pack 設計](../ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)。
