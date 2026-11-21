# ACX B0-L1｜Android 系統語系、App 指定語言與自訂包狀態轉移契約

日期：2026-11-21；來源分支 `feature/acx-b0-full-locale-inventory-20261121` 固定 SHA `0ceab1d157a3083157b4219369d0925354eb7995`。

## 0. 實作範圍與重要限制

本片**已增加可執行 Kotlin 純決策模組和 13 項 JUnit 測試**：
- `app/src/main/java/com/sysadmindoc/alarmclock/util/LocaleSelectionContract.kt`
- `app/src/test/java/com/sysadmindoc/alarmclock/util/LocaleSelectionContractTest.kt`

**不宣稱正式 UI 已改用**：`AppLanguageManager`、`LocaleManager`、DataStore、`MainActivity`、Compose、`AlarmService`、通知及 Room **本片未動**。Android 13+ 既有 App 語言選擇可運作；Android 12 以下仍沒有正式 App 自選語系的相容介面。以免重複打造不可驗證的第二套語系設定，本片先交付純狀態轉移和版本相容的規則。

## 1. 唯一語系選擇狀態

`LocaleSelectionState`：
- `mode`：`FOLLOW_SYSTEM / BUILTIN / CUSTOM`，同一時間只能一種。
- `primaryTag`：BUILTIN 允許 `en`、`zh-TW`、`ja`（也可從 en-US、ja-JP、zh-Hant-HK 正規化）；CUSTOM 必須有對應已安裝包。
- `secondaryTag`：可選的內建語言或已安裝自訂包。
- `dualEnabled`：只影響**顯示**，不更改鬧鐘功能／OS 排程。
- `lastObservedAndroidAppLocales`：可為 null（尚未同步），用來辨別首次讀取、我方寫入 ACK、外部 Android 系統設定變更。

`LocaleSelectionEnvironment`：
- `deviceLocaleTags` 是 OS 語言偏好**順序表**，不是固定國家。
- `installedCustomPacks` 是可用且通過未來 B1 格式驗證的 locale 集合；本片純決策不可自行判定 JSON 檔安全。
- `androidAppLocaleApiAvailable`：Android 13+ 的系統專屬 App 語系 API 可用與否。

## 2. 決策矩陣（純運算）

| 原設定／事件 | 主語言輸出 | 次語言 | 保留資料與備援 |
|---|---|---|---|
| FOLLOW_SYSTEM + 裝置 zh-TW | zh-TW | 無 | 裝置切換 ja → ja |
| FOLLOW_SYSTEM + 裝置 fr-FR、ja-JP | ja | 無 | 選第一個內建支援的後續 OS 語言 |
| FOLLOW_SYSTEM + OS 只有不支援語言 | en | 無 | 顯式 `fallbackUsed=true` |
| BUILTIN(en) + OS 變為日文 | en | 無 | App 明確選擇優先 |
| CUSTOM(ko-KR) 且包已安裝 | ko-KR | 視設定 | native Android 資源的安全底層語言為 en |
| CUSTOM(ko-KR) 但包遺失 | en | 視設定 | 保留 `primaryTag=ko-KR`，提供可回復狀態，不刪檔 |
| BUILTIN(ja) + secondary(zh-TW)、dual | ja | zh-TW | 主次分別解析 |
| BUILTIN(ja) + secondary(ja)、dual | ja | 不重複顯示 | 保存 secondary 設定但隱藏重複顯示 |
| SECONDARY(ko-KR) 包遺失 | 主語言不動 | en（若與主語言不同） | `secondaryFallbackUsed=true` |
| Android AppLocales 首次讀取 | 原狀態不動 | 原狀態不動 | 建立觀測快照，不當外部切換 |
| App 自己寫入 Android AppLocales 後 ACK | 原狀態不動 | 原狀態不動 | 更新最後觀測值，防止迴圈 |
| 外部將 AppLocales 從 en 改 ja | BUILTIN(ja) | 設定保留 | 系統手動更改視為最新明示選擇 |
| 外部清除 AppLocales | FOLLOW_SYSTEM | 設定保留 | 重新跟隨裝置 |
| 外部更改為未支援的 fr-FR | 既有模式不動 | 設定保留 | 記錄未知來源，不誤稱可用翻譯 |
| Android 12 以下 | 純決策仍能表示 BUILTIN | 視設定 | 平台寫入不可用，B1/B2 才處理相容 Adapter |

**重要邊界**：自訂包不是 Android `res/values` XML；`LocaleSelectionDecision.recommendedPlatformAppLocales` 只是 B1/B2 未來的**建議值**，不代表本片已向系統寫入。自訂 pack 模式建議 native 底層固定英文以維持未命中 Key 的安全讀取；是否真的寫入 Android LocaleManager，必須在 B2 接線前另有 T2/T3 生命週期試驗。

## 3. 狀態轉移事件

- `FollowSystem`：明確跟隨裝置，清除主要 App 內建／自訂選擇指標，**不刪任何語言包**。
- `SelectBuiltin(tag)`：先驗證支援，再切至 BUILTIN；不可把 zh-Hans 誤轉繁中。
- `SelectCustom(tag)`：只接受實際已安裝 pack locale，未安裝拒絕，不改舊狀態。
- `SelectSecondary(tag?)`、`SetDual(enabled)`：與主語言選擇分開。
- `ObserveAndroidAppLocales(tags)`：第一次只建立快照；若之後不同，已知內建語言採外部修改、清空改 FOLLOW_SYSTEM、未知 tag 保留安全狀態。
- `AcknowledgeAndroidAppLocalesWrite(tags)`：只記錄自身寫入結果，避免將我方同步回呼當成第二個使用者操作。

## 4. 後續 B1/B2 接線一定要補的契約

1. **持久化**：`LocaleSelectionState` 需設明確 version、與既有 Android 13+ `AppLanguageManager` 初次啟動遷移；不能讀到 null 便覆寫 OS 既有選擇。需測資料遺失、復原、更新、跨程序重啟、使用者在系統設定改語言等情境。
2. **單一決策擁有者**：現在 SettingsScreen 直接 `AppLanguageManager.setOption()`、LP2 Debug 另存偏好。B2 應建唯一 `LocaleCoordinator` 將兩者整合；不能讓 Debug LP2 session 變成 Release 全域單例。
3. **Context 與 Compose**：主／次語言需各獨立解析，不全域修改 `Locale.setDefault`；通知、背景 `Service` 在建立文案時讀持久狀態，禁止依賴 Activity 初始化。
4. **原生 Android 權限與版本**：API 26～32 的 App 自選語言 UI 目前不支援；要另外設資源 Context 轉換及狀態回寫方案，並對 APK 26／32／33／36 做相容性測試。
5. **自訂包與安全操作**：包被刪除或不完整時，仍能辨識 `Snooze`／`Dismiss` 的安全內建文案；XML 不存在的 Key 依 B0-T1 資料顯式回退。
6. **競態**：裝置 locale 廣播、Activity configuration 重建、系統 AppLocale 外部更新、使用者連續切換、進行中 SAF 匯入，應在單一序列化 reducer 中決策；外部操作偵測需有 last-observed 的 ACK 時序，不以 current Locale 推定事件來源。
7. **驗收**：不只測純狀態，還需 UI 自選→設定畫面更新→重啟→外部 Android 設定→通知→回退→歷史/音樂/鬧鐘不變，正面及故障都應有測試。

## 5. 與現有功能及文件的關係

- L1 不取代 `AppLanguageManager.kt`，它仍是目前 Android 13+ App 語系選擇唯一現有實作。
- G6 的 `200-Key` Debug 包為治理實驗，B0-T1 的 2,471-Key base 表是新的完整文字來源；L1 不聲稱有 2,471 個 UI 替換點。
- 任何缺字串、語義不正確、官方審核未完成的狀態不得由 `LocaleSelectionDecision` 推定為已批准；要回到 G4/G5/C1。
- L1 不影響 A1 原貪睡不再響的 T3 真機風險，不合併 main。

測試：`./gradlew :app:testFdroidDebugUnitTest --tests '*LocaleSelectionContractTest*'`（還需要嚴格 APK、全量 unit、跨 Runner 簽章 CI）。

## 6. 同 SHA 的 Android T2 驗證（2026-11-21）

[Run #44899090595](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44899090595) 針對 code SHA `691933c241aff801b8fda70af05fac5bdfead263` **completed / success**，三項 Job 皆成功：

1. F-Droid debug APK **Build SUCCESS**。
2. 全量 F-Droid unit tests **875/875 PASS，0 failures、0 errors、0 skipped**；含本片新增 13 個 `LocaleSelectionContractTest` 測試。Job `136830965585` 的 `UNIT_TEST_COUNTS` 為來源證據。
3. 第二 Runner 的 APK **v162** 簽章／可升級檢核 **SUCCESS**。

此 CI 的提交在本文件新增這段文字之前；文件追加不改 Kotlin、Gradle 或工作流可執行內容。**T2=PASS；T3/P=UNVERIFIED**，因為本切片的 LocaleCoordinator 還未連結真正 Android 系統語言設定與使用畫面。正式自訂包的 B1/B2 驗收不應繼承這個 PASS。
