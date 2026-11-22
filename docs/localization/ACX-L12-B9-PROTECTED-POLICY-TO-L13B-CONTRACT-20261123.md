# ACX L12-9｜受保護分層審核政策與 L13-B 正式資源 Writer 銜接

日期：2026-11-23。工程狀態：VERIFICATION_READY_NOT_AUTHORIZED。

**本階段完成驗證器、分層審核條件、獨立簽章與 L13-B writer 接線；不表示真人審核完成、保護環境已部署、中文／日文已發布。**

## 上游及整合基準

- L12-6 Draft PR #61：3,100 筆中日候選 source-bound 獨立逐列審核契約。
- L12-7 Draft PR #63：1,550 張 bilingual Key 卡、114 個審核批次。
- L12-8B Draft PR #64：P0／P1／P2＋保守風險複檢，剩餘 P3 作可重現分層診斷抽樣。
- L13-B Draft PR #62：typed XML staging、普通 L12-6 受保護正式 writer。
- L12-9 直接基於 PR #64 的工作分支，新分支以 Git 既有 blob 原樣導入 PR #62 的五份既存檔案（兩個 workflow、一份文件、writer＋原測試），未 merge 既有 PR、未更改 main。

## 三方獨立簽章與信任錨

L12-9 新增受保護證據驗證路徑：POLICY、REVIEWER、COMPILER。三個角色分別使用 Ed25519 金鑰，驗證器拒絕共用金鑰。金鑰私有部分完全不能放進 Repo、普通 CI、一般 PR。

1. POLICY：簽署 schema acx-l12-b9-signed-policy-envelope/1。簽署內容包括 policyId/revision/digest、reviewSetDigest、source/catalogue/glossary 版本、G1/G4 ledger digest、12 個功能群的已抽中與未抽中 Key、逐列必審清單 fingerprint，以及明確的剩餘風險政策授權文字。
2. REVIEWER：簽署 schema acx-l12-b9-signed-tiered-review-envelope/1。每個必審 key+locale 逐列包含來源／翻譯／審核 revision、meaning/context/format/operationConsequencesVerified、reviewerId、UTC 時間及文字理由。每個功能群的 stratum receipt 必須對抽樣集、剩餘 Key 集與來源 digest 負責，明確接受剩餘風險，或宣告該功能群改為全量逐列審核。
3. COMPILER：簽署 schema acx-l12-b9-signed-compiler-envelope/1。必須捆綁政策與人工信封 digest、source SHA、受保護 Git ref、真實 GitHub Actions workflow URL、實際 Android resource merge/Kotlin tasks，以及 staged XML 與**正式輸出 XML 原始 UTF-8 bytes** 的 SHA256 和編譯產物 SHA256。

注意：數學簽章驗證證明的是「受外部信任錨認可的私鑰簽署了該內容」，不等於簽署者真實完成了審核。真實人員身分、有效授權、實際編譯的事實仍必須由獨立受保護流程確認；本 PR 不部署或生成上述真實權限。

## 分層審核及 3,100 筆正式涵蓋率

| 類型 | zh-TW/ja 語系列 | 發布證據 |
| --- | ---: | --- |
| P0、P1、P2、P3 額外保守風險升級 | 1,208 | 獨立人工逐列審核 |
| P3 診斷抽樣列 | 206 | 獨立人工逐列審核 |
| P3 未抽中候選 | 1,686 | 明確 policy authorization + 每功能群 protected residual-risk acceptance；或該功能群全量逐列審核 |
| **總計** | **3,100** | 逐列驗證或明確的受保護政策接受；不可遺漏、重複、跨語共用批准 |

任何一個 stratum 的樣本被標記需修訂、需額外操作情境、缺漏、版本漂移，不能採樣本通過取代剩餘列；必須修訂後重新簽章，且對該 stratum 完成全量逐列審核。獨立簽署的 1,686 筆剩餘風險接受是政策責任承擔，**不是宣稱該 1,686 筆已經各自完成語義人工批准**。

在每次授權前，需重算 reviewSetDigest、policyDigest、G1/G4 ledger digest、source SHA 及 staging/official XML file digest；若任何版次漂移，拒絕舊簽章。

## 程式與執行介面

- scripts/localization/l12-b9-protected-policy-intake.mjs：三方 Ed25519 簽章、來源連鎖、逐列覆蓋、stratum 風險接受、G1/G4 審核 digest、正式資源 bytes compiler witness，及 unsigned 人工工作包。
- scripts/localization/l13-b-protected-xml-writer.mjs：預設的 full-per-row L12-6 release 契約不變；新增 review-mode = risk-tiered-policy，只有 L12-9 所有受保護驗證項目都通過才會生成正式 XML，且禁止覆寫既有資源。
- scripts/localization/l12-b9-protected-policy-intake.test.mjs：測試動態產生三把只存在記憶體的測試金鑰、假 G1/G4 狀態，驗證完整正向 1,414+1,686 架構；同時測試偽造、版本漂移、漏審、無受保護 ref、編譯證據不符以及共享權限被拒絕。**合成證據從未寫入 Repo，也不等於真實批准。**
- .github/workflows/l12-b9-protected-policy-bridge.yml：只跑 Node CI、寫入 unsigned worklist、驗證普通 CI 無金鑰必拒絕 release。它不是 protected signer，不會啟用 Android locale XML 或發 APK。

在 Repo checkout 用 Node.js （不需要 Python）可生成待審工作清單：

    node scripts/localization/l12-b9-protected-policy-intake.mjs --out-dir build/localization/L12_B9_UNSIGNED_UNAUTHORIZED_WORKLIST

GitHub Actions Artifact 名稱：acx-l12-b9-unsigned-protected-intake-requirements，內容包括：
- unsigned-review-requirements.json：完整 1,414 筆必審／抽樣列的來源、候選、hash，以及 12 個功能群的樣本與剩餘 Key／責任簽章欄位。
- protected-gate-status.json：BLOCKED、policyAuthorized=false、independentApproved=0、actualOfficialXmlWritten=0。

正常獨立分支 CI 不具有真實金鑰，執行以下正式模式應失敗：

    node scripts/localization/l13-b-protected-xml-writer.mjs --mode release --review-mode risk-tiered-policy --policy-envelope POLICY.json --tiered-review-envelope REVIEWER.json --compiler-envelope COMPILER.json

發布前必須由 Repo 外部受保護工作流程配置以下三個絕對路徑，並保證金鑰為獨立權威來源：
- ACX_L12_B9_POLICY_PUBLIC_KEY_FILE
- ACX_L12_B9_REVIEWER_PUBLIC_KEY_FILE
- ACX_L12_B9_COMPILER_PUBLIC_KEY_FILE

## 尚需真實完成的事項

1. Repo Owner 建立受保護且審核過的 GitHub Environment／受保護分支與指定 signer，實際配置三個角色的私鑰安全管理及外部公鑰；**現有普通 PR 沒有配置這些信任權限**。
2. 對 G1 glossary、G4 governance ledger 作出真的獨立批准，鎖定其簽章 digest，普通 CI 不得自行改為 approved。
3. 真人核對至少 1,414 個 key+locale 必審列，實際確認 12 個功能群的剩餘風險接受或升級全量逐列審核，生成獨立受保護的 REVIEWER 信封。
4. 在受保護 CI 依照**要發布的正式 XML bytes** 執行實際 Android resource merger/link、Gradle Kotlin tasks，查核來源 SHA 與 run URL，再由 COMPILER 簽署真實証據。L12-9 普通 CI 只證實驗證器及 fail-closed 邏輯，**沒有實作這個未來的真實簽署服務**。
5. 受保護 Writer 真正產出後仍需 L13-C/L14 的 UI、通知、Service/Receiver/Widget、格式文字及實機鬧鐘行為驗收才能發布 APK。

## TCL 結論

- L12-9 工程契約枝：已實作，可驗證。
- 3/3 protected signer 上線、G1/G4 批准、1,414 筆真人審核、12 個 stratum 處置、真實 compiler attest：**皆未完成**。
- 正式獨立批准仍 0；正式 XML 寫入 0；發行 APK 0。
- 本分支沒有修改 app/src/main/res、AlarmService、Room、Media3、播放清單、播放歷史及 P1～P3 已驗收的音樂行為。
