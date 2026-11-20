# TCL｜LP2-B2：鬧鐘編輯、音樂來源與統計的 G6 Debug 預覽擴充（2026-11-20）

- 隔離分支：feature/p31-lp2b2-edit-music-stats-preview-20261120，父提交 b2c03972（LP2-B1）。未合併 main。
- 新增 9 個真正的 Compose 畫面檔案，49 個已存在且不含格式參數的字串顯示呼叫，對應 40 個目標 Key，其中 4 個已在 LP2-A/B1 出現。
- 白名單新增 **36** 個 G6 Key；先前 51 個仍保留，所以目前實際總數為 **87/200**。
- 採完全限定名稱呼叫 `Lp2PreviewText.text`，沒有增加 import 或移動 Kotlin 行數，避免 G2/G4 證據位移。只改 Debug 的文字解析路徑，正式版回退至原 `stringResource`。
- 範圍：鬧鐘編輯三區塊、音樂池與音樂來源、鈴聲選取、統計、個人化設定及就緒狀態。鬧鐘響鈴、服務、Room、音樂播放歷史與排程皆未改。
- B2 Node 驗證 9 檔、49 顯示位置、36 新 Key、87 總 Key、現行 G6 原文及不含參數；Robolectric 驗證格式契約與候選語言解析。
- 保留 Gradle 2 個獨立驗證過的 marker POM 精確雙 SHA-256，沒有解除嚴格相依檢查。
- T2 需以 B2 最新提交的 Android CI 成功為準；T3 實機版面仍無驗證；G6 400 候選仍是 0 正式語義核准。

## 後續
- 對 113 個尚未直接接上本實驗的 Key（包含 21 個格式參數 Key），先補可辨識的實際 UI 呼叫清單，再做可追蹤的解析器適配。
- 對所有多行與無障礙顯示進行真機 T3；Debug 實驗不能聲稱正式版全 App 多語系已完成。

## 2026-11-20 最終 T2 驗收結論（附錄；原始施工說明保留）

- [LP2-B2 P0 Android Run 44762900025](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900025) 對 `6a0223e119f2d9b62e57f655bac5c98ed61bdeb2` **SUCCESS**；APK 建置、`859/859` 單元測試（0 失敗、0 錯誤、0 略過）和第二 Runner 升級簽章均已通過。
- [LP2-B2 Node 治理 44762900020](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900020)、[LP1 資產相容 44762900179](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44762900179) 於相同 SHA 成功。
- 程式切片已通過 **T2**，但 **T3 實機未驗證、G6 正式內容核准仍為 0/400、PR #25 仍是 Draft，沒有合併 main**。
- 本討論串索引：[TCL 增量與交付總索引](P31-DISCUSSION-DELTA-AND-DELIVERY-INDEX-20261120.md)。
