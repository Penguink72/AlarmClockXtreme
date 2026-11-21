# ACX B1-C1｜完整 catalog、V1 自訂語言包機械驗證與離線原子儲存

2026-11-21。隔離分支：`feature/acx-b1-c1-pack-validation-repo-20261121`。起點：[B0-T1 PR #33](https://github.com/Penguink72/AlarmClockXtreme/pull/33) + 精確拷入 [B0-L1 PR #34](https://github.com/Penguink72/AlarmClockXtreme/pull/34) 的 Kotlin 狀態轉移程式與測試；不合併 main，也不替換 App 現有語言選單。

## 1. 交付實體

- `app/src/main/assets/localization/v1/catalog.json`：**2,471 Key 全量 Android XML 基底**，含 `sourceHash`、型別、格式 tokens、原始 XML 與 plural/array 型別；`format=acx-language-catalog/1`，`catalogRevision=sha256:c608e163bb47aeefa169e3ca72b8e86ebd8deb37d89c59e47796d953fe95c70a`。此 manifest 是 **App 內資源契約**，不是已核准的翻譯包。
- `scripts/localization/b1-build-canonical-catalog.mjs`、`.test.mjs`：依 B0-T1 英文原文生成；`--verify` 保證 commit 內 asset **byte-for-byte** 可重生。
- `app/src/main/java/com/sysadmindoc/alarmclock/localization/LanguagePackV1.kt`：獨立載入 catalog、嚴格 JSON schema/重複鍵/深度/大小、locale、逐 Key 來源 hash、text/format/plural/array 檢查，並分別回報 `errors`、`warnings`、`staleKeys`、`fallbackKeys`、`usableKeys`。
- `LanguagePackV1Repository.kt`：透過 Android Context 初始化、以 `InputStream` 匯入、`OutputStream` 匯出；**先全量驗證再 `AtomicFile` 寫入**，保留前一版有效資料供 rollback，現行檔損壞時可只讀回退，不會擅自改 OS 語系。
- `LanguagePackV1Test.kt`：Robolectric 真正執行拒絕惡意 JSON、非法標籤、來源過時、未核准宣告、格式參數、plural other、array 長度、2MiB 上限、UTF-8、匯出／回滾／損壞讀取與原版本不覆蓋。
- 已帶入 B0-L1 `LocaleSelectionContract` 和既有 13 個測試，不需要另發明一套相衝突的語言狀態。

## 2. 明確的 V1 自訂語言包格式

```json
{
  "format": "acx-language-pack/1",
  "locale": "ko-KR",
  "catalogRevision": "sha256:c608e163bb47aeefa169e3ca72b8e86ebd8deb37d89c59e47796d953fe95c70a",
  "packRevision": "v1",
  "status": "USER_SUPPLIED_UNREVIEWED",
  "entries": {
    "string:settings_title": {
      "sourceHash": "<the-actual-sourceHash-from-catalog.json>",
      "type": "string",
      "text": "<user-supplied-translation>"
    }
  }
}
```

以上不是可直接匯入的已填字串（`sourceHash`、`text` 為示意值）；應從 catalog 的真正 Key/sourceHash 建立。**全量資源 Key 一律使用帶 type 前綴的 `string:...`、`plurals:...`、`array:...`**，以避開同名不同類型。不接受 Debug 舊 `acx-language-pack-probe/1` 直接冒充 V1。既有 B0～B2 設計稿中的無前綴 `settings_title` 僅為歷史示例，實際機械契約以本文件和程式為準。

其他型別：
- `plurals:...`：Entry 含 `type:"plurals"`、`sourceHash`、`forms:{"other":"...", "one":"..."}`；必須有 `other`，類別限定 `zero/one/two/few/many/other`，不允許新增不存在的格式參數。`other` 必須保留來源 `other` 的參數契約。
- `array:...`：Entry 含 `type:"array"`、`sourceHash`、`items:["..."]`，項目數必須等於原始 `string-array`。
- `string:...`：Entry 含 `type:"string"`、`sourceHash`、`text`；對目前不支援安全自訂渲染的 rich markup String，**拒絕自訂覆寫而非破壞 Android 原生 Spannable**。格式 Key 只能保留已知參數集合。這是保守的 B1-C1 驗證器，不是 B2 完整格式化及排版器。

## 3. 狀態與安全規則

| 狀況 | B1-C1 結果 |
|---|---|
| 結構合法、新 locale／少量 Key | 允許自訂匯入，缺漏 Key 列 `fallbackKeys`，不冒充翻譯完整 |
| `catalogRevision` 不同但某些逐 Key hash 一致 | 包可保留；相同 Key 可用，sourceHash 過期 Key 列 `staleKeys` 且不得渲染 |
| 自訂包試圖 `status=APPROVED` 或 `OFFICIALLY_REVIEWED` | 拒絕，不能繞過 G4/G5/G6 官方獨立核准 |
| Unknown Key、`translatable=false`、不符資源型別 | 拒絕 |
| JSON 物件重複鍵、非法 UTF-8、過深、超 2MiB、惡意控制字 | 拒絕 |
| 主文案 placeholder 不匹配、plural 沒有 `other`、array 數量錯 | 拒絕 |
| 匯入失敗／舊資料存在 | 不覆蓋舊版本 |
| 後續同 locale 匯入新版 | 先備份當前有效版本再原子取代；可手動 rollback |
| 目前檔案損壞且備份有效 | read-only 使用上一個有效包；不隱瞞 `recoveredFromPrevious` |

- 單包 UTF-8 上限 **2 MiB**，單項文案 **4,096 字元**，最大 entries 為目前 catalog 的 Key 數，JSON 結構最大深度 10。未來全語種大包應以實際測量再調整，不可讓檔案無上限。
- 嚴禁從 JSON 內容選擇 filesystem 路徑；持久化路徑只由經 BCP47 正規化的 locale 生成。
- Repo 中 **沒有任何使用者自訂 pack** 或匯入後的敏感文字；僅有空白 schema 與 Node 生成的權威英文基底。
- `LanguagePackV1Repository` 於單個 App process 內序列化匯入／回滾。**跨 process 原子協調、可取消的 SAF UI 和持續展示未核准標示留給 B1-U1**；目前先不將此 Repository 接到正式 SettingsScreen。

## 4. 驗收界線

`T1`：已提交可編譯候選程式與格式／儲存測試；`T2`：需要同一 code SHA 嚴格 Android APK、full unit、第二 Runner upgrade/signing 與 Node `--verify` 兩條 CI 分別成功；`T3/P`：**仍未驗證**。

不可聲稱目前已可以透過正式 App 選檔匯入 JSON、切換韓文或雙語響鈴。那是 B1-U1/B2 的後續接線。本次不動 `AppLanguageManager`、`Lp2PreviewText`、`AlarmService`、Room v28、任何鬧鐘排程、Media3 或來源 XML；不合併 main。

## 5. 後續 B1-U1 / B2 依賴

1. 用 SAF OpenDocument／CreateDocument 把畫面輸入／輸出接到 Repository；顯示可用／fallback／stale／reviewState 及明確的未審核提示。
2. 初次啟動遷移 Android 13+ AppLocales 與舊 Debug LP1/LP2 格式時，必須由明示轉換與版本紀錄，不擅自匯入舊草稿。
3. 在單一 `LocaleCoordinator` 接入設定頁與可觀察 Compose state，於 Android 26～32 另做相容處理。
4. `LocaleTextResolver` 分 text／formatted／quantity／array，安全鍵（Snooze／Dismiss）先保留已驗證的內建語言，不讓自訂候選文字改變按鈕行為。
5. 若效能或 metadata 過多使 App 啟動變慢，要測量 catalog 載入時間並作快取，不在每次 Compose recomposition 重新解析約 0.95 MB 資源。

關聯：[B0-T1 文字矩陣](ACX-B0-T1-TRILINGUAL-MATRIX-20261121.md)、[B0-L1 狀態機](ACX-B0-L1-LOCALE-STATE-TRANSITION-CONTRACT-20261121.md)、[B0～B2 原架構](ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)。

## 6. GitHub CI 實際驗收（2026-11-21）

**B1-C1 已完成 T2**：

- [Node G1～G6／B0／B1 Run #44905760153](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44905760153)：**completed / SUCCESS**，SHA `0019138398b4f18a1b7abc5d465ddf09733e38bb`。包含 B0-T1 TSV 的 2,471 Key 源 XML byte-verify、B1 完整 catalog asset byte-verify、全部既有翻譯治理 gate；官方缺獨立批准的 pack 仍預期拒絕。
- [Android P0 Run #44906233649](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44906233649)：**completed / SUCCESS**，code SHA `75e60bed1fe4c184cd21ba97e0ce050541248294`；F-Droid debug APK Build SUCCESS、**891/891 單元測試通過（0 failures、0 errors、0 skipped）**、第二 Runner v162 簽章／升級 APK 檢核 SUCCESS。
- 此兩者 source/runtime code 相同。Android CI SHA 只比 Node SHA 多一筆 **單元測試 fixture 修正**：舊格式的負向測試一開始對 JSON 字串執行 `.replace("acx-language-pack/1", "...probe/1")`，但 Android `JSONObject` 會將斜線轉義，替換實際沒有生效，因此初次 [Android Run #44905760124](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/44905760124) **890/891，1 failure**。已在修正 SHA `75e60bed1fe4c184cd21ba97e0ce050541248294` 將 fixture 改為 `JSONObject(...).put("format","acx-language-pack-probe/1")`；後續全量驗證 891/891 成功。原失敗紀錄不隱藏。
- **不可因此宣稱** V1 使用者匯入 UI 已完成或在真機切換語言可用。此片僅有 backend validator/repository 與產物；T3、個人包實機操作與 B2 顯示範圍 **仍未驗證**。

交付界線：PR #35 維持 Draft，**未合併 main 或其他平行 PR**；下一階段 B1-U1 SAF UI、B2 resolver 與單／雙語實際接線需再開隔離施工與新版 CI。
