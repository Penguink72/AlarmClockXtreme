# ACX B1-U1｜正式語言中心、三語對照與自訂語言包互動

日期：2026-11-21。獨立工作分支 `feature/acx-b1-u1-language-center-20261121`，基於已驗證的 B1-C1 PR #35，不合併 main。

## 本輪新增的實作

- `app/src/main/java/com/sysadmindoc/alarmclock/localization/LanguageCenterActivity.kt`：正式 Build 也包含的 Compose 語言中心，註冊於主 `AndroidManifest.xml`，`exported=false`，從 Settings 首頁及 Utilities 的自訂語言入口開啟。
- `app/src/main/java/com/sysadmindoc/alarmclock/localization/LanguageCenterData.kt`：驗證比對資料的 catalogRevision/Key 集合；依 B1-C1 V1 契約組合使用者 JSON，編輯一 Key 只刷新該 Key sourceHash，其他已過期項保持 stale，不能偷渡成已核准。
- `app/src/main/assets/localization/v1/source-compare.json`：以 B0-T1 真實來源矩陣衍生的 **2,471 列**英文、現存繁中、現存日文對照。缺少的內建翻譯為 `null`，絕不假造英文複製。
- `scripts/localization/b1-u1-compare-index.mjs --verify`：CI 逐 byte 對照該 2,471 列。
- `app/src/main/assets/localization/v1/editor-labels.json`：語言中心這一個新增實驗性操作面的英文／繁中／日文文案。這是另外管理的 UI 編輯文案，不屬 B0-T1 原始 2,471 分母，也不代表 G4/G6 官方翻譯批准；B2 後續應把正式畫面資源對齊 Android 標準資源及版本管理。
- `LanguageCenterDataTest.kt`：對照總數、語言缺漏、空白包、單 Key 編輯／刪除、舊 Key 保持過期、plurals/array 型別、儲存與三語操作文案完整性。

## 可操作流程

1. **設定 > App language > Custom** 開啟語言中心（兩個設定分頁皆可）。
2. 在 `ko-KR` 等標準 BCP47 語言標籤建立 JSON 包，或從 Android SAF `OpenDocument` 匯入 `acx-language-pack/1` JSON。舊 LP1/LP2 probe 格式拒絕直接冒充正式包。
3. 全量 2,471 個 Key 可用 Key／英文／繁中／日文內容搜尋；點擊後顯示英文原文和現有中日文字，缺漏有標示。
4. 指定使用者包後，可編輯單個普通 `string`，或 `plurals` 類別和 `array` 逐項文字；格式佔位符必須通過 B1-C1 Validator；帶樣式的普通字串暫唯讀，不能破壞原有 span。
5. 「儲存」實際呼叫 B1-C1 `LanguagePackV1Repository.importRaw`，先全量驗證再 `AtomicFile` 寫入；「移除」只刪除該 Key 的包內翻譯，原文不變。
6. 用 Android SAF `CreateDocument` 匯出使用者包。支援一版 rollback，損壞包讀取舊版時顯示回復狀態。清單標出可用／過期／fallback 總數，並始終顯示 `USER_SUPPLIED_UNREVIEWED`。
7. **尚未將自訂 pack 套用到正常 App UI 或 AlarmService**；只有文字編輯與預覽。系統語系／自訂包／雙語的狀態同步屬後續 B0-L1 整合與 B2 真正的 UI Text Resolver。

## 問題、保護及測試證據

- UI 原始編譯檢查 `verifyLocalizedPrimaryScreens` 禁止硬編碼，因此已使用既有正式資源及三語編輯器文案作備援，不繞過 guard。
- 既有 G4 治理流程曾把一個 SettingsScreen Key 的 **歷史 line number** 當成永久 identity；新增設定列使行號位移而報錯。修正 `g4-content-review-audit`、`g4-legacy-priority-audit`、`g4-batch2-audit`、`g4-remaining-legacy-audit`：必須仍在同檔、同 Key、原記錄附近 40 行內有真正 `R.string.key` 引用；不取消人類審核門檻或對缺來源證據放行。
- Android Robolectric／APK／第二 Runner 簽章和 Node CI 使用各自 GitHub Run 的精確 SHA 記錄。沒有真機 UX 或安全操作驗收時，不能把 T3/P 標成 PASS。
- 分支與後續 B0-L1 的偏好持久化／OS AppLocales 接線獨立，避免未完成的系統語系行為擾亂 B1-U1 編輯資料。A1 鬧鐘、Room v28、Media3、通知、調度均未改動。

## 後續必要的 B0-L1 整合及 B2

- `LocaleCoordinator` 統一持久化三態、主／次語言、雙語及 Android 13+ per-app locale 的 external change / own-write acknowledgement；26～32 不可假裝支援完整原生切換。
- 在 Settings 既有語言選單選系統／英／繁中／日文時，走同一 coordinator；自訂包可先成為已持久化的使用者選擇，但須標示 **normal UI 尚未套用**。
- B2 需要真正將使用者自訂翻譯接到 Compose、通知、背景 Service、格式化／複數及核心 Snooze/Dismiss 安全文字，再做實機語義與大字級驗證。
