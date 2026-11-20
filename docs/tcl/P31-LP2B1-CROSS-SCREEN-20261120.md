# TCL｜LP2-B1 跨畫面 Debug 語言切換實作（2026-11-20）

## 範圍
- 自 LP2-A PR #23／提交 c87a3daf 開立隔離分支；不合併 main。
- 在不變更鬧鐘排程、通知、播放動作的前提下，把現行 G6 的 31 個跨畫面 Key 接到六個真實 Compose 畫面，合計 42 個畫面文字位置：鬧鐘清單、鬧鐘編輯、音樂庫、音樂播放設定、播放紀錄、播放清單。
- 其中 settings_not_selected 已在 LP2-A 設定頁 21 Key 白名單中，因此整體共 51 個不同 Key。
- 新鍵都不含 printf 參數；仍維持 200-Key G6 Debug 資產作候選資料來源。

## 顯示模式與生命週期
- Compose Lp2PreviewText.text() 在 DEBUG 且明確啟用時顯示主要語言，或兩行顯示兩種語言；缺譯以英文備援。
- 在正常 release 或停用預覽時，六畫面和設定頁一律走原本的 stringResource。
- MainActivity.onResume 恢復用戶選定的 Debug 預覽狀態；不要求先開啟 SettingsScreen 才能顯示已保存的翻譯選擇。
- 音樂庫與歷史畫面只改使用者看到的文案，不動播放引擎／回播控制。

## 治理與測試
- 為新增 import 後的 +1 行號位移，精確重錨 3 筆 G4 原始碼證據；來源雜湊、譯文、原始文字、審核狀態不變。
- Node 機械檢驗驗證 6 個檔案、31 Key、42 文字位置、51 Key 白名單、G6 版本、S0 正式來源及 Debug 閘門。
- Robolectric 測試檢查跨畫面 Key 與格式參數、譯文解析、重啟恢復／停用預覽。
- 仍沒有任何獨立語義審核：0/400；所有預覽與匯入都僅供 Debug，不是正式發布語言包。
- T3 手機端開啟、匯入及雙語排版驗證尚未完成；Android CI 只能提供 T2 證據。

## 後續規劃
- LP2-B2：繼續擴張剩餘 Key，逐步移除畫面硬編碼與跨流程格式化落差。
- G6-Q：獨立內容審核、術語契約及正式發布完整驗證。
- 不改 Room、AlarmManager、Media3、AlarmService、正式通知及播放歷史紀錄。

## 失敗修補與驗收紀錄
- 首輪 P0 Android 編譯失敗：MainActivity 已有 onResume 處理 AlarmService.activeAlarm；LP2-B1 新增重複 override，Kotlin 回報 Conflicting overloads。
- 修補後保留唯一 onResume，先取得 active alarm；若存在則維持原響鈴畫面轉導與 fireId，僅在沒有活躍鬧鐘時載入 Debug 預覽；正式 Release 不讀入預覽檔。
- 加入 Node 反向靜態護欄：onResume 僅能宣告一次、活躍鬧鐘檢查必須先於預覽載入、響鈴必要 extra 不可消失。
- Android T2 必須重新以修補後的最新提交驗證，不能沿用先前失敗或取消的工作流。

## CI Kotlin 插件 POM 短期阻斷與嚴格修補（2026-11-20）
- PR #24 於 `acafcf77` 的單元測試 Runner 兩次在 Gradle 檢查 POM 時失敗，並非 Kotlin 測試斷言失敗；該 SHA 的 APK 建置與兩 Runner 簽章通過。
- [診斷 Run 44759332185](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44759332185)：Maven Central POM 1411 bytes，SHA-256 `0b1d7e9b...`，與原鎖定相符；Gradle Plugin Portal POM 679 bytes，SHA-256 `96e007b3...`，與測試 Runner 失敗時所取檔相同。
- [內容比對 Run 44759646691](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44759646691)：兩份原始 XML 的 dependency 清單均僅有 `org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0`。未聲稱兩份 POM 位元相同或具簽章；僅確認兩個官方儲存庫的 POM 都指向同一個 Gradle plugin 依賴。
- 依 Gradle 官方 `Trusting Multiple Checksums` 規則，只在 `gradle/verification-metadata.xml` 這一個 2.1.0 POM 的 sha256 項下加入 `also-trust` 值 `96e007b3ecb22cc6d9617e414484539fe5b77b28e6659c8c916b4fddf8961ced`；原主 checksum 原封不動，其他依賴與版本不受影響。
- 新增 `scripts/ci/guard-kotlin-plugin-pom-verification.mjs`，在 APK 和單元測試兩個獨立 Runner 上都檢查只容許這兩個精確雜湊、沒有額外批准、不得關閉 Gradle 相依套件驗證。
- 修補後仍必須等待完整精確 SHA 的 Android CI 通過後才能將 LP2-B1 標為 T2 完成。

## Compose 2.1.0 marker POM 附加來源證據
- [診斷 Run 44761041843](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761041843) 同時獨立下載兩個 Kotlin 2.1.0 插件的 Maven Central 及 Gradle Plugin Portal POM。
- Compose marker 在 Maven Central 的原鎖定 SHA-256 為 `db3bde26de3ea7433086163644a5750e280a1540990e30f1ae99fe80f2d5f160`；Gradle Plugin Portal 另發布 `fe78fa62efbf0c89ebef4bb0695ab219f41d802e854aafe0fedc561b1bc4a0d7`，與第二次失敗的 Build Runner 摘要相同。
- 兩個 Compose POM 都只有同一個 `org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0` 相依項。這是獨立來源與 XML 依賴宣告的核對，不聲稱 XML 位元完全相等或有發布者 PGP 簽章。
- 對 Compose 此一精確 POM 另外加入 `also-trust`；同時更新唯讀 Node 護欄，現在限定 **只有 Android Kotlin + Compose 這兩個 marker POM** 可各有一個額外的已核查雜湊。
- 兩份主原始 SHA-256 不變，所有其他套件仍保持嚴格驗證。若新版 Build Runner 有其他 POM 不一致，必須再逐一獨立確認；不可全域停用依賴驗證。

## 2026-11-20 最終 T2 驗收結論（附錄；不覆寫原始施工／失敗記錄）

- [P0 Android Run 44761537401](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44761537401) 針對本片最終提交 `b2c03972a5aba58804796244d3634bbb253909b9` **SUCCESS**。
- 嚴格依賴驗證下的 F-Droid Debug APK 建置 PASS；`856/856` 單元測試，`0` 失敗、錯誤、略過；第二 Runner 升級簽章 PASS。
- 此處的 T2 完成**不包含 T3 實機**，也不構成 G6 候選翻譯獨立核准；仍維持 Draft PR、不合併 main。
- Gradle 兩個 Kotlin marker POM 的原始來源、SHA 和修補證據請見 [CI 來源鑑別紀錄](../ci/P31-KOTLIN-COMPOSE-POM-PROVENANCE-20261120.md)。
