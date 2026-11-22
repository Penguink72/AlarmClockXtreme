# ACX L13-A｜Android 原生語言狀態與非侵入式 Context 解析預檢（2026-11-22）

> **此工程片只建立可測試的原生資源解析接縫，未啟用完整語言包覆蓋、未接線全部 Compose／Service／Notification，未改正式資源或發布 APK。**

## 來源與 Owner
- 基線：AlarmClockXtreme L11-9＋L12-4 合流 SHA `b6b37257bcb1fe1e82f7c09a5188c65d7053efb8`（PR #57）。
- 復用 B0-L1 `feature/acx-b0-l1-locale-state-contract-20261121` 原有 `LocaleSelectionContract.kt` 和 `LocaleSelectionContractTest.kt`，內容不重寫，保存既有狀態與適用範圍。
- `AppLanguageManager.kt` 已有 Android 13+ 系統 App 語系選擇，對外 `en`／`zh-TW`／`ja`；L13-A **不替換**現有語系變更流程。
- `app/src/main/res/xml/locales_config.xml` 在本工作基線已列出 en、zh-TW、ja，不應再次修改。
- 自訂 JSON 語言包的驗證及持久化由其他既有架構負責，不能用 Android native adapter 冒充正式自訂包已經套用。

## 已實作接縫
- `app/src/main/java/com/sysadmindoc/alarmclock/util/LocaleContextTextAdapter.kt` 依 `LocaleSelectionDecision.nativeResourceLocale` 建立獨立 `Context.createConfigurationContext`，支援 `@StringRes`、帶參數字串、`@PluralsRes`。
- 原 Context、`LocaleManager.applicationLocales`、全域 Locale、鬧鐘時間、Room、播放紀錄均不改動。
- 若 locale 專屬資源不存在，Android 會回退到基底語言；因此回傳狀態 `ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED`，不謊稱命中已批准譯文。
- 讀取自訂語言包時僅能取得純契約所指定的 English native base，真正的自訂包 overlay 尚未接線，不執行未經授權的動態取代。

## 機械與執行期驗證
- B0-L1 原有狀態測試：跟隨系統、明確指定語系、外部 Android 語言變更、雙語候選、未安裝自訂包回退、錯誤標籤拒絕。
- 新增 Robolectric：獨立 Context 不改原始語系；zh-TW/ja/en 對應的 native 文字讀取；格式參數及複數讀取；自訂包與 native fallback 分離。
- CI：`.github/workflows/l13-a-native-context-preflight.yml`，只執行指定的 Kotlin 單元測試，不製作 APK；完整結果以 GitHub Actions 實際結論為準。

## 尚未完成的 L13-B 接線
1. 正式 Android `strings.xml`、`plurals`、`string-array` 必須完成獨立審核及受保護 XML writer。
2. Compose、鬧鐘響鈴、通知、Receiver、Service、Widget、TalkBack 需逐路徑接線並驗證。
3. Android 8～12 缺原生 per-app 語系 picker，持久化與更新策略必須獨立決策；L13-A 沒有把 API33 路徑誤用於舊 Android。
4. L14 的 UI／前景通知／背景服務／重啟回歸及 L15 的 APK 均未完成。

## 不變量
**任何文字解析更動，不得更改 AlarmService、AlarmManager、Room、Media3 或已驗收的 P1～P3 媒體來源與播放歷史。**
