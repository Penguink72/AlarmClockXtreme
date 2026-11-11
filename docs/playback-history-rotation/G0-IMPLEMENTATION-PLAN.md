# G0｜GitHub 雲端建置與 APK 交付的實作工作單

狀態：**T0 已規劃；T1/T2 尚未完成**  
基準：`feature/playback-history-rotation` 之 [P0-BASELINE.md](P0-BASELINE.md)  
限制：**不改鬧鐘功能、不關閉 Gradle dependency verification、不修改 `main`。**

## 作者需要看懂的交付

按下 GitHub Actions 的建置頁，最後應取得 `app-fdroid-debug.apk`，可以下載到 Android 手機做白天鬧鐘測試。原版的測試結果清楚列示，不能以「步驟綠色」冒充測試全部通過。

## 已知基線（SRC＋實測）

- 原版 `main@a0cc501` 的 Android app 設定：`compileSdk=36`、`minSdk=26`、`targetSdk=36`、Kotlin/Java target 17；Gradle wrapper 8.13。
- 現有 workflow：`.github/workflows/p0-android-baseline.yml`。
- 第一輪 Gradle 權限問題已改為 `bash ./gradlew`。
- **嚴格模式未通過**：`gradle/verification-metadata.xml` 有缺項。此前核對三項 SHA-256 已補入；仍需查 `guava-parent:33.0.0-jre` 和未審查四筆候選。
- 診斷版暫時寫入候選 metadata 後曾完成 F-Droid Debug APK 編譯，並跑 675 個單元測試，**1 個原版 `BedtimeReminderBaselineTest` 失敗**。診斷結果不能冒充正式嚴格 PASS；APK 未被正式上傳。
- 詳細證據與 Run：參閱 [P0-BASELINE.md](P0-BASELINE.md)；不重設既有測試基線。

## G0 架構（只碰建置鏈）

```text
GitHub commit（feature branch）
  → Actions：固定 runner + JDK 17 + Android SDK 36
     → Gradle wrapper
        ├─ 嚴格檢驗依賴來源／hash（不可關閉）
        ├─ Unit test job → JUnit XML／HTML 報告（單獨呈現）
        └─ Build job → assembleFdroidDebug → APK
     → Upload artifacts（測試報告、APK、錯誤報告）
  → 使用者從 Actions 下載 APK，不自動安裝到手機
```

## 分片與建議 Commit

### G0-01｜固定且可重現的 CI 環境

- 將 `ubuntu-latest` 改為固定 runner 映像（如 `ubuntu-24.04`，先確認可用）；Android SDK 36、JDK 17、Gradle 8.13 固定。
- 依原始 `app/build.gradle.kts`、`README.md` 確認所有 flavor 的必要依賴。
- 每輪記錄 commit SHA、JDK/SDK/Gradle 版本與命令真正退出碼。
- 禁止 `--dependency-verification=off`，不任意降低安全設定，也不修改上游。

### G0-02｜一次盤點缺失的依賴 metadata 並人工核對

- 只將 `--write-verification-metadata sha256` 視為臨時環境的候選差異，不直接信任它。
- 分別從發布者可信來源取原始 artifact 與可用的公布 checksum／簽章；缺正式 SHA-256 時記錄替代校驗及不確定性。
- 特別核對 `guava-parent:33.0.0-jre`、`aapt2-*-linux.jar`、`guava-parent:33.2.1-jre`、`junit-bom:5.8.2.pom`、`junit-bom:5.9.2.module`；這是已知候選／缺項，不代表最終清單只有這些。
- 只補正確且實際需要的精確 artifact SHA-256；提供差異與逐項證據。
- 不大量改依賴版本，不無條件採信 Gradle 自動生成 hash。

### G0-03｜分離「測試」與「APK 編譯」的結果

- 分成獨立 CI jobs 或等效獨立結果，使原版非相關測試失敗不會把原本已成功的 APK 直接丟掉。
- Build job 用嚴格依賴驗證執行 `bash ./gradlew --no-daemon :app:assembleFdroidDebug`。
- Test job 用嚴格依賴驗證執行 `bash ./gradlew --no-daemon :app:testFdroidDebugUnitTest`。
- 測試失敗仍標 FAIL，APK build 成功也只標 build PASS，兩者不得混同。
- 補抓測試 XML／expected vs actual；查明並重跑原版 `BedtimeReminderBaselineTest`，不要刪除或擅自改 assertion。
- APK 產物附上來源 SHA、包名、檔案大小、SHA-256；`upload-artifact` 若無 APK 須顯示失敗（`if-no-files-found: error`）。

### G0-04｜交付 APK 與可下載路徑

- 先出 `FdroidDebug`，不處理 Play flavor、Wear、正式發行簽署；確保測試版包名的 `.debug` 後綴。
- Debug 簽章可能在不同雲端 runner 不同，不保證跨次 APK 直接覆蓋安裝；首版以**白天可安裝測試**為主要目標。個人長期更新用的固定簽署金鑰另案規劃，金鑰不可提交 Git。
- 如需要安裝後升級，先驗證簽名相容，不要假設能直接覆蓋；解除安裝會清除該 App 私有資料。
- 提供 GitHub Actions run URL、artifact URL、安裝與卸載警告。不將此 APK 宣稱已經手機驗收。

## G0 驗收契約

| ID | 驗收條件 | T 層 |
|---|---|---|
| G0-A | 嚴格驗證下 `assembleFdroidDebug` 真正退出碼 0 | T2 |
| G0-B | 可下載實際 APK，大小非零、SHA-256 與原始 Commit 可查 | T2 |
| G0-C | `testFdroidDebugUnitTest` 有完整報告；已知舊失敗與新失敗分開列 | T2 |
| G0-D | `main` 不變，無上游寫入，沒有密鑰／音樂檔進 Git | T1 |
| G0-E | 你能在白天於手機安裝、開啟並設定測試鬧鐘 | T3 |

**放行到 G1：G0-A、G0-B、G0-D 必須成立，G0-C 必須有可讀證據；原版不相關的已知失敗可保持紅燈並獨立追蹤，不能說全綠。**

## Agent 安全邊界

每個 Agent 提交需報告改了哪些檔案、真實 Run URL、完整結果、仍未解決的問題。禁止合併 `main`、強推、清空歷史或私自安裝手機。
