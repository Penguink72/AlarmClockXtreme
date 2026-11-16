# P3.1-L1｜鬧鐘核心畫面三語系與時間格式契約

日期：2026-11-16。來源：2026-11-16 使用者提供之「編輯鬧鐘」與「貪睡與關閉」Android 真機截圖，以及當次回顧的 AlarmClockXtreme 三語系資源盤點。

## T0｜權威基準與界線
- Repository：`Penguink72/AlarmClockXtreme`。
- 來源提交：S1 hotfix `e138aff735f1b50f83dd645f0cb21a620456249c`，其 GitHub Actions Run `43781101758` 已通過。
- 本施工分支：`feature/p31-l1-trilingual-core-time-locale`。禁止直接修改 `main`、PR #10、PR #11，禁止改變 Room v28 Schema、S1 Service/AlarmManager 行為、播放策略與已保存鬧鐘。
- 完成一批即推送一批，建立 Draft PR，CI 全綠後只標記 T2；沒有手機實測不得標記 T3。
- Android `values/strings.xml` 是英文 fallback；`values-b+zh+Hant+TW` 和 `values-ja` 分別存放繁體中文（台灣）與日文。
- 目前英文約 2,401 個名稱，繁中／日文各約 564 個；多數舊畫面缺漏會自動退回英文。一次全填 1,837 個名稱並不適合不經人工檢閱的熱修補。
- 涉及品牌、URI、API wire value、log reasonCode、資料庫欄位名稱、非翻譯診斷鍵、既有使用者自訂文字，不應進行翻譯。

## T1｜三語詞彙與核心範圍
| Concept key | en | zh-Hant-TW | ja |
| --- | --- | --- | --- |
| alarm_edit_preview_title | Alarm preview | 鬧鐘預覽 | アラームのプレビュー |
| alarm_edit_existing_status | Editing existing alarm | 正在編輯鬧鐘 | 既存のアラームを編集中 |
| snooze_duration | Snooze duration | 貪睡間隔 | スヌーズ間隔 |
| alarm_edit_snooze_limit | Snooze limit | 貪睡次數上限 | スヌーズ回数の上限 |
| alarm_edit_progressive_snooze | Progressive snooze (shorter each time) | 逐次縮短貪睡間隔 | スヌーズ間隔を毎回短縮 |
| alarm_edit_challenge_none | No challenge | 無解除挑戰 | 解除チャレンジなし |
| alarm_edit_day_monday_short | Mo | 一 | 月 |
| alarm_edit_day_sunday_short | Su | 日 | 日 |

### L1-A（本輪直接施工）
1. `AlarmEditOverviewSections.kt` 用到的所有可翻譯英文資源名稱，包含鬧鐘預覽、建立／編輯、群組、時間提示。
2. `AlarmEditDismissSections.kt` 用到的核心貪睡與解除挑戰資源名稱，保留既有 `translatable=false` 技術碼。
3. `AlarmEditSupport.kt` 的星期短名稱、重複週期、時間輸入及常見挑戰名稱，確保截圖中的週縮寫與「No challenge」不再回落英文。
4. 鬧鐘預覽時間倒數，改由純時間數值計算＋ Compose `stringResource`（英文、繁中、日文）；不得讓 `NextAlarmCalculator` 的 `d/h/m/now` 硬編碼直接顯示在此畫面。
5. 12 小時制的 AM/PM 使用目前 `LocalConfiguration` 的 locale，禁止依賴 JVM 預設 locale，對 `System.currentTimeMillis()` 的依賴限於觸發畫面更新的 clock tick；純函式與單元測試使用可注入 `nowMillis`。
6. CI 新增核心資源名稱覆蓋檢查與 printf placeholder（含 `%1$d`、`%1$s` 等）相容檢查；逐批擴大範圍，不得用空字串、英文複製或將所有缺字串直接省略的方式取得綠燈。

### L1-B（下個獨立切片，不能冒充本輪完工）
- `AlarmFiringScreen.kt` 現有約 149 個繁中／日文缺漏；包含右滑貪睡、解鈴按鈕、挑戰提示、通知與無障礙描述。
- `AlarmEditAdvancedSection.kt`、`AlarmEditWakeSections.kt`、`SettingsScreen.kt` 舊設定、Onboarding 仍大量缺翻譯。
- 完整多語 UI 截圖比對、字級放大與 TalkBack 實機驗收。

## T2｜時間、佔位符與品質
- 格式：英文 `in 18h 56m` 不可在中文畫面與中文外層文字混用；以 `R.string.alarm_edit_remaining_* ` 依 day/hour/minute 結構輸出，時間基數由 `RemainingTimeParts.from(triggerAt, nowMillis)` 純函式產生。
- 過去／現在、少於一分鐘、1 分／59 分、跨日、夏令時間切換時不可出現負數文字；計算使用 duration/epoch 毫秒，不將本機日期文字轉回 epoch。
- `%1$d` 與 `%1$s` 等參數規格要一致；`plurals` quantity 必須含 `other`，繁中／日文可只使用 `other`（但保留精確參數型別）。
- `verifyLocalizedPrimaryScreens` 是 Kotlin 字面量靜態檢查，並不能防止 locale 資源缺漏。新的 `verifyCoreLocaleCoverage` 不取代它。
- 所有新增文字及修正的資源必須有單元或 CI 驗證；不利用真實等待 10 分鐘做單元測試。

## T3｜真機驗收
1. 系統繁中（台灣）：編輯現有鬧鐘的預覽與貪睡頁不再混入上述英文；星期一～日、剩餘時間、分／次複數正確。
2. 系統英文：標題、長文字、星期正常；日文：短星期及星期與貪睡字串不截斷。
3. 調大 Android 字級，確認星期按鈕與底部保存不重疊。
4. 與 S1 分離驗證；此包不宣稱修好「螢幕持續亮著時貪睡到期不響鈴」。S1 尚須真機獨立驗證。

## 留存狀態
- 本文件與程式位於 L1 獨立分支。
- L1-A 實際完成範圍以 Git commit、CI 成績及翻譯覆蓋輸出為準；L1-B 不得因有計畫即標記完成。
