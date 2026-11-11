# P0｜Android SDK 36 基線建置與診斷紀錄

日期：2026-11-11（Asia/Taipei）  
Repo：`Penguink72/AlarmClockXtreme`  
分支：`feature/playback-history-rotation`  
原始功能碼基線：`main@a0cc501bcc3514ffd36483ca0b6e266aa234aaff`  
狀態：**P0 STRICT NOT PASS**；原始執行程式未修改；不應合併 `main`。

## 1. 目標與環境

以 GitHub Actions 的 Ubuntu runner、Temurin JDK 17、Android SDK API 36、Gradle wrapper 8.13，在開發分支執行：

- `bash ./gradlew --no-daemon --stacktrace :app:testFdroidDebugUnitTest`
- `bash ./gradlew --no-daemon --stacktrace :app:assembleFdroidDebug`

使用 F-Droid Debug flavor 是為了先驗證共用鬧鐘引擎，不代表 Play flavor／Wear／真機已經通過。原始專案的 `gradlew` 在 Linux runner 沒有可直接執行的權限，故透過 `bash ./gradlew` 啟動，未修改原始碼檔案模式。

## 2. 遠端執行證據

| Run | GitHub Actions 內容 | 真正結果 |
|---|---|---|
| [42926616200](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42926616200) | 初始 P0 | SDK/JDK 成功；`gradlew` 權限拒絕；測試／編譯未開始。 |
| [42926744106](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42926744106) | 改 `bash ./gradlew` | Gradle 因 metadata 漏項拒絕依賴；測試／編譯失敗，無 APK。先前 `continue-on-error` 造成綠色步驟誤導，後已修正。 |
| [42927761087](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42927761087) | Maven Central hash 探針 | 取得並核對三筆依賴檔案的發布端 checksum；未成功建置。 |
| [42928307651](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42928307651) | 補三筆 SHA-256 後重新嚴格驗證 | 又遇到 `guava-parent-33.0.0-jre.pom` 未列入信任 metadata，P0 未通過。 |
| [42928847204](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42928847204) | 嚴格 P0 + 隔離 metadata proposal | 嚴格命令仍失敗；**僅在候選 checksum 臨時環境內**成功執行 Kotlin 編譯、`assembleFdroidDebug` 並跑完 675 項單元測試，其中 1 項失敗。 |

注意：綠色的「Upload artifact」表示執行上傳步驟成功，不能代替 Gradle PASS。正式 APK 產物尚未透過嚴格 P0 生成並提供。

## 3. 已核對並正式補錄的三個 checksum

以下只對原有信任 metadata 中漏列的指定檔案加 SHA-256，並已寫入目前開發分支的 `gradle/verification-metadata.xml`（Commit [a09404b](https://github.com/Penguink72/AlarmClockXtreme/commit/a09404b27e24b07601f51d07a708638b38ef8f6c)）。

| Artifact | SHA-256 | 校驗來源 |
|---|---|---|
| `junit-bom-5.10.2.module` | `de23b114b3e4119a8fe6eb17bed5a3852816698bace67071579d6d927ebb080a` | Maven Central SHA-256 |
| `guava-parent-33.3.1-android.pom` | `6e11986ea7250b51f847157e2dc937f32a306804dfce0007a5e81ddb9b95c579` | Maven Central HTTPS 下載，公布 SHA-1 一致，重新計算 SHA-256 |
| `kotlinx-coroutines-bom-1.9.0.pom` | `bea5511e9001f2c593ab5080df131b219ab5e2085cc5979ce583eacd4946fd78` | Maven Central SHA-256 |

沒有關閉 `verify-metadata` 或使用 `--dependency-verification=off` 來宣稱嚴格通過。

## 4. 尚未受信任的 Gradle 候選清單

[候選 artifact（保留七天）](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42928847204/artifacts/13815612125)  
[依賴驗證報告](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/42928847204/artifacts/13813739519)

CI 只在 **runner 的臨時工作目錄** 使用 `--write-verification-metadata sha256` 產生建議差異；它不改 GitHub 倉庫。完整提案檔與 patch 已上傳，**未審查、未採用**。

日誌中的 patch 只有 `14 insertions(+)`，涉及四個 artifact：

| Candidate | SHA-256（自動生成，未完成來源獨立比對） |
|---|---|
| `aapt2-8.11.1-12782657-linux.jar` | `5ce2f1c4262b99174047f3d413816c57ceef5fb00a520264cf5ed564576bf250` |
| `guava-parent-33.2.1-jre.pom` | `9095f6d8ee3765950207510785830cc2280e781067915fa99cfe1740d4865c14` |
| `junit-bom-5.8.2.pom` | `836069ca9e8ee3c56e48376222da291263f137bd3fd16d84fdd47efcc3f286e2` |
| `junit-bom-5.9.2.module` | `ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f` |

**差異待釐清：** 嚴格建置曾報缺 `guava-parent-33.0.0-jre.pom`，但這份自動 patch 未包含它；不能因候選成功跑得更遠，就把該缺項視為已不存在。下輪查解析路徑／缓存、不同 Gradle configuration 的實際 artifact 清單。

## 5. 原版診斷執行的測試與 APK

CI 日誌在使用候選 metadata 的暫存工作目錄後，顯示：

- `:app:compileFdroidDebugKotlin` 執行完畢。
- `:app:assembleFdroidDebug` 執行完畢。
- `:app:testFdroidDebugUnitTest`：**675 tests completed, 1 failed**。
- 失敗：`BedtimeReminderBaselineTest > the reminder repeats the last in-app measurement instead of taking one`，`org.junit.ComparisonFailure` 於 `BedtimeReminderBaselineTest.kt:67`。
- 這屬於原有「睡前提醒沿用上次噪音測量」測試，與未實作的音樂輪播／歷史無關。**根本原因尚未核定**。

源碼：
- [BedtimeReminderBaselineTest.kt](../../app/src/test/java/com/sysadmindoc/alarmclock/receiver/BedtimeReminderBaselineTest.kt) — 直接寫 SharedPreferences、觸發提醒、比對 Notification 文案。
- [BedtimeNoiseBaselineSampler.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/service/BedtimeNoiseBaselineSampler.kt) — 讀取該快照；本次只讀檢查未發現此處對快照的到期判定。
- [BedtimeReceiver.kt](../../app/src/main/java/com/sysadmindoc/alarmclock/receiver/BedtimeReceiver.kt) — 讀取快照並選通知字串。

測試含固定時間戳，但未見 `readSnapshot` 在此處判斷過期，所以**不能僅憑時間戳推定失敗原因**。必須取得測試 XML 的 expected/actual 才能判斷。

臨時 runner 內曾有診斷 APK，但此工作流程在診斷之前就執行 APK artifact 上傳，故**沒有上傳可交付 APK**。嚴格驗證仍未通過，禁止把診斷 APK 宣稱為正式驗收品。

## 6. 下一階段停止／放行條件

**P0 保持 BLOCKED；P1 每日選曲正式改碼暫不放行**，直到：

1. 從不同正式發布端核對四筆候選；至少對其原始檔案與廠商公布校驗資訊交叉檢查，不能自動信任 `--write-verification-metadata` 的結果。
2. 查明 `guava-parent-33.0.0-jre.pom` 為何只在嚴格路徑出現；保留決定結果及依據。
3. 取得 `BedtimeReminderBaselineTest` 錯誤的 expected/actual；記錄是否可穩定重現，避免擅自刪除／跳過該測試。允許以明確「原版既有失敗」留作已知 baseline，但不得把它算成 PASS。
4. 重新執行 **嚴格驗證模式** 的 `:app:testFdroidDebugUnitTest` 和 `:app:assembleFdroidDebug`，保留實際 log、測試報告與 APK artifact。
5. 標註 instrumented／手機鬧鐘回歸尚未跑；不能用雲端單元測試代替。

## 7. 版本及修改邊界

本次只增加文件、CI workflow，及三筆經核對的 Gradle dependency-verification checksum；沒有改 Kotlin 程式、Room migration、Media3 播放或既有鬧鐘數據；`main` 保留原始 commit。全部 GitHub Actions 只在隔離 runner 執行，未安裝到使用者手機。
