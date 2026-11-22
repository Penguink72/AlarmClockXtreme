# ACX L13-B｜完整型別化翻譯資源隔離整合與受保護 Writer（2026-11-22）

> **此 PR 是完整資源的工程預檢，不是繁中／日文正式啟用。** L12-6 尚無獨立審核批准，現有 G1/G4 都未授權，所有模型候選只能在 build/ 隔離生成與編譯。

## 同一工作基準

- 與 L12-6 共用 source-bound review packet：L11-1～9 的 2,986 個普通字串候選，加上 L11-10 的 114 個 string、plurals、string-array 候選。
- 完整 3,100 筆，繁中與日文各 1,550 筆：每語普通 string 1,518、plurals 31 組、string-array 1 組（15 items）。
- 主分支及原來已有的繁中與日文 XML 不覆寫，來源比對 B0-T1 sourceHash、sourceXml、typed key、locale 現有專屬資源定義；遇到重複 Key、錯型別、舊來源立即拒絕。
- 隔離輸出：build/localization/L13_B_ISOLATED_UNAPPROVED/res/values-b+zh+Hant+TW/strings_l13_reviewed.xml、res/values-ja/strings_l13_reviewed.xml。這些 staging 名稱包含 reviewed 只是未來預留的正式檔名；當前產物標記為 MODEL UNREVIEWED，嚴禁複製到 app/src/main/res。

## 實作與驗收

- scripts/localization/l13-b-protected-xml-writer.mjs：typed XML 生成、來源／類別／重複檢查、stagedFilesSha256、外部簽章及 G1/G4 的正式 release gate、只在保護工作流程才開放正式 writer。
- --mode staging 永遠只寫 build/，不讀取自稱批准的任意候選；--mode release 必須同時有 Repo 外的可信公鑰、L12-6 完整簽章、受保護 Git ref、獨立 glossary/G4、相同版次的真實 compiler evidence。
- 發布路徑只能產生兩個新的 strings_l13_reviewed.xml，不能覆寫任何現存 strings.xml；正式產物仍須經獨立 Git 審查和 Android 全量編譯後才能合併。
- scripts/localization/l13-b-protected-xml-writer.test.mjs：3,100 列正向、string/plurals/array 完整分母與型別、來源漂移、原有 locale 定義保護、未授權寫入的負向測試。
- .github/workflows/l13-b-protected-writer.yml：兩組 CI job，同版執行全部未審候選的真實 aapt2 隔離編譯；同時只編譯原本 App 既有資源與 Kotlin，不把模型翻譯安裝進去。

## 明確未完成

- L12-6 人類逐列語義批准 0；G1 與 G4 尚未批准；沒有設置真正外部受保護的 reviewer、信任錨、簽章文件。
- 目前 Gradle 現有 App 編譯成功只可說明程式分支沒破壞原本 App；不表示 3,100 筆未審語系已通過完整 Android Link。
- Compose UI、Service、Receiver、Widget、通知、貪睡／解除、排程後的實際顯示與語義行為，尚未完成正式語言資源接線驗收；L13-A adapter 是候選接縫，並非全 App runtime 啟用證據。
- L14/L15 的 Android 實機、安裝 APK 與版本升級驗收另行進行。

## 後續明確 Gate

1. L12-6 的外部 reviewer 與獨立 G1/G4 決定必須可供追溯，不能以本 PR 的自動測試產生批准。
2. Protected CI 重新鎖定原文與譯文版本並生成正式 XML，檢查實際 Android 完整資源 link／APK build。
3. L14 驗證所有翻譯觸及的 UI 及背景語義、格式、長字串和安全控制。
4. L15 在可保留 P1～P3 音樂庫／播放紀錄的前提下交付 APK。

**不變量：不改 AlarmService、AlarmManager、Room、Media3、P1～P3 音樂池與播放歷史。**

## L13-B 額外全 App 連結預檢（僅 CI 暫存）

後續新增 .github/workflows/l13-b-merged-resource-link.yml：在 GitHub Actions 的一次性 checkout 中產生 staging，將兩份未核准 XML 暫時加入 main res 的獨立 ci_only_unapproved 檔名，再執行 :app:processFdroidDebugResources 與 :app:compileFdroidDebugKotlin。Exit trap 移除暫存 XML；不提交任何翻譯檔，不產生 APK 或正式 release artifact。這是測試整體 Android resource merger 是否能接受候選 XML，**不是部署未經批准譯文**。

驗證應分別記錄：isolated aapt2 / 現有 App 資源編譯 / 暫存候選與全 App 資源同時 link 三種獨立結論；不可相互替代。
