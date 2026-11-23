# ACX S1＋S2：一般中英日翻譯與簡化發布

2026-11-23。從 L13-C PR #66 開立獨立功能分支。不合併 main；不修改已驗收的鬧鐘時間、Room、音樂來源與播放歷史。

## S1：一般翻譯不再要求人工批准

一般翻譯走 STANDARD 技術發布路徑：重用原有 L11＋L12 與 L13-B typed XML 建置器，檢查來源差分、XML 型別、格式參數、無重複 Key，再交由 Gradle resource merger 與 Kotlin compiler 驗證。G1 詞彙表及 G4 品質紀錄不作為普通翻譯生成／測試版輸出的必要批准。L12-9 PROTECTED 三方簽章契約完整保留，僅供其他高保證流程自願採用，與普通三語更新完全隔離。

## S2：完整 Android native 資源

原本英文 app/src/main/res/values/strings.xml 保持不動。每個 locale 缺漏 1,550 個資源，每語言含 1,518 string、31 plurals、1 string-array，總計 3,100 個新增中日語系項目。保留現有 values-b+zh+Hant+TW/strings.xml 和 values-ja/strings.xml 內人工編輯內容，新增檔名 strings_s2_machine_translated.xml。該檔明示 MACHINE_TRANSLATED_NOT_SEMANTICALLY_VERIFIED，不宣稱人類逐列批准。

Node.js 操作：

    node --test scripts/localization/s1s2-standard-locales.test.mjs
    node scripts/localization/s1s2-standard-locales.mjs --mode stage --out-dir build/localization/S2_STANDARD_MACHINE_TRANSLATED
    node scripts/localization/s1s2-standard-locales.mjs --mode materialize --user-approved-machine-translation
    node scripts/localization/s1s2-standard-locales.mjs --mode verify

mode materialize 必須明示接受機器翻譯。程式不能覆蓋已有且不同的翻譯檔案，不得覆蓋既有人工 locale strings.xml；遇到 source/typing/placeholder 漂移直接拒絕。不會讀取或更改 G1/G4 的批准狀態。

## GitHub CI：隔離先編譯，再提交功能枝

工作流 .github/workflows/s1-s2-standard-trilingual-resources.yml：第一個 read-only job 產生 3,100 條 typed XML、執行 Node 測試，把生成 XML 暫時拷貝至一次性 Android checkout，完成 resource merge/Kotlin compile，再確認原始 app/src/main 無差分，並上傳 artifact。第二個單獨權限 job 只有在第一個 job 成功後，才從 CI artifact 取得兩份生成 XML，無須再次執行候選程式，使用最小 contents:write 權限，只提交在 S1/S2 專用功能枝、不 force push、不合併 main。若 Actions Token 不具有寫入權，仍保留 Artifact 且標示無法提交，不可聲稱已完成靜態入庫。

## 後續 S3/S4

S3 才將主要語言／第二語言／雙語開關與跨 Android 版本的偏好保存接入主 App；S4 才將雙語顯示真正接入首頁、響鈴、音樂庫、播放歷史等 UI。這一階段僅保證原生三語資源可編譯，不把多語資源的存在誤認為「雙語同時顯示已完成」。

## 驗收定義

S1：普通翻譯在沒有任何 G1/G4 人工批准及三方公鑰時也能完整經過技術驗證，原保護模式仍 fail-closed。S2：兩份生成 XML 真正存在於 branch 的 app/src/main/res，與 source manifest 完全吻合，且 Node、Gradle 及資源連結成功。CI-only 測試不等同於 APK 真實安裝驗收。
