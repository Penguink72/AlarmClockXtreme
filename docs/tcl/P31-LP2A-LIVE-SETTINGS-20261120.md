# TCL｜LP2-A Debug 真實設定頁即時語言切換（2026-11-20）

## 交付切片
- 源自 G6 PR #22（HEAD df97384d）。以 G6 200-Key 目錄另存 debug-only assets/lp2，**不改寫 LP1 11/18 歷史資產**。
- 實際 SettingsScreen 共有 21 個 G6 Key 不含動態 printf 參數，可直接套用；包含設定標題、貪睡、儀表板、工具、語言選擇、診斷與部分描述，共 23 個 Compose callsite。
- src/main/settings/Lp2PreviewText.kt 僅在 BuildConfig.DEBUG 且明確啟用時改寫選定 21 個 Key。其他文字、所有 release build、鬧鐘響鈴、服務、通知及歷史維持既有 stringResource。
- Debug Activity 內可匯入 G6 JSON（SAF OpenDocument、256 KiB 上限、AtomicFile、語言標籤與 placeholder 驗證），單語／雙語選擇，重啟後透過 SettingsScreen onResume 恢復預覽狀態。
- 關閉實驗後返回原本 Android 文字；顯示英文缺譯備援。保留從 LP2 面板開啟 LP1 歷史實驗室入口。
- UI 標示 G6 翻譯皆未獲獨立審核（核准數 0），Debug 工具文字提供繁中／英文／日文，不引入正式發行的翻譯包。

## 安全與證據
- G6 生成資產與 app/src/debug/assets/lp2 的 JSON 完整一致性使用 Node 測試。新 catalogRevision= g6-200-current-20261120；舊 LP0 匯入一律拒絕。
- Robolectric：現行目錄 200、設定白名單 21、韓文單條自訂包匯入及重啟讀回、拒絕錯誤 revision／Key／printf／超大檔。
- G4 SettingsScreen.kt 參考行號在此切片增加 3 行，僅有 11 條來源位置機械重錨；沒有改文字、審核結論或自動批准。
- 真實目標：關閉 Debug 面板返回設定頁時，原本設定的 21 個文字 Key 自動顯示所選語系；二語模式以二行對照。T3 實機仍未驗證。
- 不修改 Room schema、AlarmService、AlarmManager、Media3、播放歷史；沒有合併 main。

## 後續
- LP2-B 才擴展到全部 App 畫面及正式原子套用、系統資源與語言包更新管理。
- G6-Q 持續保留獨立內容品質核准；通過程式測試並不等於 400 筆翻譯可正式發布。
