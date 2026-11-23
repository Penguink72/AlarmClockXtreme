# ACX L13-C｜執行期語系橋接與隔離測試

狀態：KOTLIN_RUNTIME_BRIDGE_IMPLEMENTED，正式 3,100 筆中日候選仍 UNAPPROVED。

日期：2026-11-23。分支 feature/acx-l13-c-runtime-locale-surface-20261123，以上游 L12-9 Draft PR #65 HEAD 建立，沒有合併 main，也沒有更改 L12-6／L12-9／L13-B 的任何批准帳本或驗證門檻。

## 實際存在的 App 架構

已核對：
- SettingsScreen.kt 已有 Android 13+ 的本機 AppLanguageManager 設定入口；Android 系統依 App locale 自動讓 Compose stringResource 讀取對應的原生資源。
- LocaleSelectionContract.kt（L13-A）已能在不持久化狀態下，對 Follow System、builtin en／zh-TW／ja、未安裝 custom pack 及 Android app locale 改變作純函式決策。
- LocaleContextTextAdapter.kt（L13-A）已能 createConfigurationContext 讀取 string／format／plurals；但從未自動套用至 Service、Receiver 或全域設定。
- LP1/LP2 的語言包工作台存在於 app/src/debug 專用 Activity，不能視為經審核的正式 native 中文／日文 XML。

因此 L13-C 不重造 App 語言設定，而是接上 Android 平台設定與背景訊息文字讀取器；Compose 的既有 stringResource 路徑保留原樣。

## 已接線的執行期路徑

新增 app/src/main/java/com/sysadmindoc/alarmclock/util/LocaleRuntimeTextBridge.kt：

1. Android 13 以前：完全沿用原 Context 字串與原生 formatting。**不寫任何偏好資料，也不強制將舊手機的語言改成 English**。
2. Android 13+、per-app locale 選擇為空：直接沿用 Context 的裝置／App 原生 resource locale。
3. Android 13+ 明確選擇 en、zh-TW、ja：只在該次字串讀取透過 L13-A 的 ConfigurationContext 取得原生 Android text。每次呼叫都重新查詢當前 LocaleManager，不快取舊語言。
4. app locale 第一個 tag 是非支援值／查詢失敗：原樣保留平台 Context 讀取，不偷選次要 locale 或假稱原本沒有的專屬翻譯存在。
5. Android 回傳英語 fallback 是原生資源回退，**永遠不等同已通過 L12-6／L12-9 真人語義批准**。自定義 JSON 語言包的 debug overlay 不是正式 native string 資源。

AlarmService.kt 僅將文字顯示部分接入：
- B2 診斷通知 title／description。
- 正式響鈴通知 title、snooze 動作的分鐘格式化、dismiss 動作標籤。
- missed alarm notification title 和含 label／時間／分鐘的格式文字。

**保留原本 5 秒 foreground deadline 的 startForegroundWithPlaceholder() getString 原生快速路徑**。任何 service action Intent、AlarmScheduler、SnoozeCapPolicy、Room、音樂播放、通知觸發時機、資料格式和 webhook wireTime 都沒改。通知中既有 AlarmPublicText label 隱私規則也保留。

## Debug-only 可視化與回退驗證

既有 app/src/debug/.../Lp2SettingsPreviewActivity.kt 添加唯讀診斷區：同卡顯示 en／zh-TW／ja 目前原生 XML 的鬧鐘通知 title、10 分鐘 snooze、dismiss 字樣，以及 ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED 的原始來源標記。它不更改 Android App 語言、不會將模型候選寫入正式資源，也不會生成被聲稱已核准的譯文。現有 LP2 200 條 JSON 草稿包仍保持 debug 專用。

新增 Robolectric LocaleRuntimeTextBridgeTest.kt：
- SDK 28 舊版 Context 回退，不新增不支援的平台 App locale。
- Android 13+ 明確 en／zh-Hant-TW／ja 第一語言路由、非支援第一語系不偷選次要語言。
- 不動 Context configuration / global resource。
- 各 locale native formatted snooze/missed text 的參數保留。
- 拒絕非法或非 builtin 標籤的 debug-only native preview。
- 也回歸執行既有 LocaleContextTextAdapterTest 和 LocaleSelectionContractTest。

## CI：正式／隔離兩條不同證據線

工作流 .github/workflows/l13-c-runtime-locale-bridge.yml：

A. 不加入任何 L11/L12 未審的候選 XML，編譯當前既有 Android UI／Kotlin，執行三類 Robolectric unit tests。這是正式分支安全相容性測試。

B. 在一次性 GitHub Actions checkout 使用 L13-B staging 從 L12-6 綁定的 3,100 筆 MODEL UNREVIEWED 候選暫時複製成 values-b+zh+Hant+TW/strings_l13_ci_only_unapproved.xml 和 values-ja/strings_l13_ci_only_unapproved.xml，執行完整 resource merge、Kotlin compile 與相同 runtime tests；EXIT trap 清除暫存，最後檢查原始 app/src/main 無 diff。**這只是非發行的候選兼容性測試，不是把未批准譯文加入 App**。

C. CI 另以缺 L12-9 受保護證據的正式 Writer 命令進行預期失敗的負向測試。沒有產生真正受保護的 policy/reviewer/compiler 簽章、沒有 G1/G4 批准、沒有正式 XML 或 APK。

## TCL 驗收與缺口

| 枝 | 工作 | 狀態 |
| --- | --- | --- |
| L13-C1 | Android app locale → 背景顯示字串唯讀 bridge | 已實作 |
| L13-C2 | 響鈴／未接／B2 診斷通知關鍵字串接線 | 已實作 |
| L13-C3 | Debug 介面三語系 native preview／fallback 標記 | 已實作 |
| L13-C4 | SDK 28 / 33、format、null／unsupported 語系測試 | 已新增，CI 需核對 |
| L13-C5 | 隔離 3,100 候選下的完整 app resource/link/Kotlin 測試 | CI 需核對 |
| 後續 | 全部 Compose 視圖、通知頻道名稱更新策略、Receiver／Widget／Wear、字串截斷、UI 真實操作結果、實機鬧鐘／熄屏／重開機 | 尚未全面驗收 |
| L12-9 | 真實受保護三方授權、1,414 必審／抽樣列、12 群風險承擔、G1／G4 | 尚未完成 |

「L13-C 可以 compile 且 debug preview 可讀取」不等於「全部 3,100 筆中日翻譯已經發布」，也不等於 Android 服務／通知／Widget 的所有動態畫面都實機驗收成功。

## 稽核不變量

- 不產生 app/src/main/res 正式新翻譯，不覆寫既有已有人力維護的 zh-TW／ja 字串。
- 不更改 Room schema、音樂來源、播放清單、播放歷史或 snooze/dismiss 操作權限。
- 來源明確標記 ANDROID_NATIVE_RESOURCE_OR_FALLBACK_UNVERIFIED，避免把平台 fallback 誤稱審核批准。
- L13-B protected XML writer、L12-6／L12-9 簽章門檻皆保持 fail-closed；本輪不提供 APK。
