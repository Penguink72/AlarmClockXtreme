# ACX B0-L1｜語系狀態機的實際持久化／Android 系統設定接線

日期：2026-11-21。繼原 B0-L1 純 reducer `LocaleSelectionContract.kt` 通過 875/875 後，從 B1-U1 PR #36 再獨立建立 `feature/acx-b0-l1-coordinator-integration-20261121`。這是**同一 B0-L1 的第二片「整合」**，不是重開新狀態機，且不自動合併 main。

## 1. 程式交付

- `util/LocaleCoordinator.kt`：使用唯一 `LocaleSelectionContract.transition/decide`，不另造規則；將 `FOLLOW_SYSTEM/BUILTIN/CUSTOM`、主語言、次語言、dualEnabled、lastObservedAndroidAppLocales 以 `SharedPreferences` v1 儲存。採同步 `commit()` 先存原生語言寫入的意圖，寫入失敗則恢復先前狀態。
- `AndroidLocalePlatformPort`：Android 13+ 唯一經 API 保護存取系統 `LocaleManager.applicationLocales`；Android 26～32 不存取不存在的 API。設備語系順序使用 `Resources.getSystem().configuration.locales`，不依賴非原生的自訂語言包來修改全域 `Locale.setDefault`。
- **首次遷移**：若 App 本身已設日文／繁中／英文，無資料的協調器初始值取既有 OS app-locales，**不得首次啟動就覆蓋使用者在 Android 設定選的語言**。外部改 AppLocales 後以 B0-L1 reducer 判斷並保存；自身寫入以 ACK 區分，避免回呼循環。
- `SettingsScreen.kt`：原有系統／英文／繁中／日文四項設定全部改走 `LocaleCoordinator.forApp(context).dispatch(...)`，不再各自直接呼叫 `AppLanguageManager.setOption`。原有 `AppLanguageManager.currentOption` 仍用於顯示 Android 已套用的內建語言。
- `LanguageCenterActivity.kt`：在既有 B1-U1 全量逐 Key 編輯與 SAF 操作之外，新增跟隨系統、英／繁中／日文、自訂包 primary（預覽）、secondary、雙語切換和有效語系狀態顯示。自訂包選擇需要該 locale 已存在、通過 B1-C1 結構檢查，否則 reducer 拒絕。
- `app/src/main/assets/localization/v1/editor-labels.json`：新增對應英／繁中／日文偏好操作文案，未擅自把偏好狀態等同於已審核正式全 App 翻譯。
- `LocaleCoordinatorTest.kt`：12 項 Robolectric 測試涵蓋首次遷移、外部系統改語言、自身寫入 ACK、舊版 Android 預覽語系、失敗回退、已安裝自訂包檢查、移除後保留回復線索、secondary/dual 保留及惡意標籤拒絕。

## 2. 真正的語系優先序與邊界

| 狀態與事件 | 系統原生資源/UI | 持久化狀態 | B1-U1 語言中心 |
|---|---|---|---|
| 系統原生繁中，App Follow system | 系統繁中 | FOLLOW_SYSTEM | 顯示系統語言 |
| App 選英／繁中／日文，Android 13+ | 透過 LocaleManager 正式套用 | BUILTIN / 目標 tag + ACK | 顯示生效狀態 |
| Android 系統設定外部改 App language | OS 優先 | 更新 reducer 為 BUILTIN／FOLLOW_SYSTEM | 下次讀取反映 OS 更動 |
| App 自選 ko-KR 自訂包 | **仍使用上一個 Android 原生語系**，因 B2 Resolver 尚未接線 | CUSTOM / ko-KR | 顯示 ko-KR 已保存為預覽選擇 |
| Secondary zh-TW + dual | **不改正常 UI 布局** | secondaryTag=zh-TW, dual=true | 顯示偏好 |
| Android 12 以下自選內建語言 | **現有 App 原生資源仍跟隨系統** | 保存選擇供預覽 | 不誤稱平台切換成功 |
| 自訂包失效／刪除 | 安全使用原 native UI | primaryTag 保留、decision fallback en | 顯示包不可用與 fallback |
| 在 Settings 切回跟隨系統 | Android 13+ 清除 AppLocales | FOLLOW_SYSTEM | 保留既有自訂包本身 |

**關鍵安全限制**：自訂包的有效文字不代表已被 `LocaleTextResolver` 用於一般畫面；本輪不修改 `AlarmManager`、`AlarmService`、通知 PendingIntent、音樂播放、Room 或使用者既有鬧鐘。B2 真正套用時必須另外驗證 Snooze／Dismiss 操作語義、格式、重啟與通知一致性，避免「選擇成功」被誤認為「全畫面已翻譯」。

## 3. CI 與發行門檻

Android：`P0 Android baseline` 全量 Unit + F-Droid debug APK + 第二 Runner 簽章；Node：`lpg1-g5-translation-pipeline` 重建 B0-T1/B1/B1-U1 catalog/compare、G4/G5/G6 gate。必須以**最終 GitHub Run、實際 head SHA** 判定是否 T2 完成，先前 B0-L1 純 Kotlin T2 PASS 不能自動套給此次 UI 接線。

B1-U1 PR #36 與 B0-L1 後續 PR 都維持 Draft。正式 Android 26～32 App 語系更換、B2 自訂包覆蓋普通 UI、跨程序 SharedPreferences/AtomicFile 同步及真機 T3/P 尚未驗收。

參考：[原 B0-L1 reducer 設計](ACX-B0-L1-LOCALE-STATE-TRANSITION-CONTRACT-20261121.md)；[B1-U1 完整對照與編輯](ACX-B1-U1-LANGUAGE-CENTER-20261121.md)；[B1-C1 機械驗證](ACX-B1-C1-V1-PACK-VALIDATOR-AND-STORAGE-20261121.md)。
