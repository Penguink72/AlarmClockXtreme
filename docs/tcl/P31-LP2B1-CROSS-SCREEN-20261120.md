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
