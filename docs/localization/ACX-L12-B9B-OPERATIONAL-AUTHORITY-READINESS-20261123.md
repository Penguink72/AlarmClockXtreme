# ACX L12-9 第二片｜獨立審核權限的操作交接

日期：2026-11-23。此工作承接 L12-9 PR #65，作為獨立 Draft 變更，不改 main。

## 本片解決的問題

L12-9 第一片已有三方 Ed25519 簽章驗證、L13-B Writer 接線，並完成 38 項測試。但「驗證器存在」不等於真人已審閱，也不等於 GitHub 已建立保護環境。L12-9 第二片把已固定版次的 3,100 筆來源變成可分派給真實審核者的工作包，且不授予自己正式批准權。

目前的手交接分母：

| 審核範圍 | zh-TW + ja 語系列 | 正式義務 |
| --- | ---: | --- |
| P0/P1/P2，加上 P3 保守升級 | 1,208 | 逐列人工獨立語義審核 |
| P3 被抽中樣本 | 206 | 逐列人工獨立語義審核 |
| P3 未抽中內容 | 1,686 | 12 個功能群各自真實簽署剩餘風險接受，或整群逐列審核 |
| 合計 | 3,100 | 必須全部有對應證據，不能把抽樣當作語義證明 |

## 本片新增工具與操作

scripts/localization/l12-b9b-authority-readiness.mjs

Windows 與 GitHub Actions 均使用 Node.js；不需要本機 Python：

    node scripts/localization/l12-b9b-authority-readiness.mjs --out-dir build/localization/L12_B9B_UNSIGNED_AUTHORITY_HANDOFF

產出到 build/ 目錄，包含：

1. authorization-readiness.json：每項前置條件、資料版本 SHA、G1/G4 目前觀察值、真實授權缺口，狀態永遠為 BLOCKED_AWAITING_EXTERNAL_APPROVAL。
2. unsigned-policy-subject.json：POLICY 角色核對用的來源/政策/G1/G4 digest 及條件，沒有簽章，不是已批准政策。
3. unsigned-row-review-queue.json：1,414 筆逐語系 Key 的原文、譯文、來源/翻譯/審核 revision；所有語義、callsite、format、operation 檢查皆從 false 開始。
4. unsigned-stratum-risk-receipts.json：12 組功能群，已抽/未抽 Key、樣本與剩餘清單 digest，以及待填的真人理由與時間；decision=null，unreviewedResidualExplicitlyAccepted=false。
5. unsigned-compiler-requirements.json：來源 SHA、正式 XML 預期 bytes digest、staging digest、真實 Android resource merge/link + Kotlin build 與 GitHub run 記錄需求；不是編譯證據的正式簽章。
6. READ_ME_UNAPPROVED.txt：不可自行發布、不得把草稿 JSON 直接當簽章的提醒。

可以透過 --preliminary-notes 檔案.json 匯入 L12-7 工作台的未簽署審閱筆記，檢查版本漂移與抽樣問題整群升級；不會把 LOOKS_ACCEPTABLE_UNVERIFIED 自動轉成已通過獨立語義批准。如果筆記結構不正確，工作流程會回報無效及非零退出碼。

CI 只發佈 Artifact acx-l12-b9b-unsigned-authority-readiness，並再次負面測試缺少三方簽章的 L12-9/L13-B 正式 writer 必須被阻擋。

## 本輪安全性修訂

L12-9 原先已要求 POLICY、REVIEWER、COMPILER 三個外部公鑰絕對路徑、不能指向 Repo。這次再加上 realpathSymlink 檢查：Repo 外的一個 symlink 如果實際指回 Repo 內，會拒絕作為信任錨；信任路徑也必須指向一般檔案，不是資料夾。對這種路徑加入回歸測試。

這項加固不代表三把真實私鑰已配置，也不保證真實審核者身分。權威必須由 Repo owner 在獨立受保護環境管理。

## 真正啟用正式政策所需的操作

1. Repository owner 進入 GitHub Repo Settings → Environments，建立專用 localization release environment，依帳戶方案與可用權限設定必要 reviewer、可部署的受保護 branch、禁止未授權自核准。GitHub 連線目前對 branch protection 讀取回傳 403 Resource not accessible by integration，故本片無法替使用者驗證或配置那些權限，**不能宣稱 GitHub 受保護環境已上線**。
2. 由真實的三方授權者各自持有不同的 Ed25519 私鑰（POLICY／REVIEWER／COMPILER），並從 Repo 外部受保護來源提供公鑰路徑。不得在一般 PR、普通 Actions 或測試 fixtures 中建立正式授權密鑰。L12-9 原有環境變數：ACX_L12_B9_POLICY_PUBLIC_KEY_FILE、ACX_L12_B9_REVIEWER_PUBLIC_KEY_FILE、ACX_L12_B9_COMPILER_PUBLIC_KEY_FILE。
3. G1 glossary 和 G4 ledger 需要真的獨立人工核對及來源責任紀錄；即使有人將 approval 欄位改成 true 或塞入陣列，也不能自行充當這種真人批准。
4. 獨立 reviewer 先根據這次 1,414 筆逐列工作單、原先 L12-7 的完整雙語審閱介面確認語義、上下文、Android 格式與操作後果，並對 12 個功能群分別給出合法 signed stratum disposition。抽樣一旦有問題，同群不得接受剩餘風險，必須升級全群審核。
5. 受保護 compiler 必須實際在與簽章來源相同 Git SHA 的受保護工作流執行 Android resource merge、Gradle Kotlin compile、正式 XML bytes 雜湊及真實 GitHub run 記錄，再由第三個獨立 signer 簽署。
6. 最後在受保護 ref 下由 L13-B writer 檢查完整政策信封、reviewer 信封與 compiler 信封，以及 G1/G4、全 3,100 筆涵蓋率，才可能生成正式 XML。產出 XML 後仍需 L14 的背景通知、Widget、Service、UI 長文字、真正鬧鐘行為及實機驗收。

目前程式碼可校驗 signer 聲明的 compiler 證據，但尚無獨立上線的簽章服務去逐項向 GitHub 取證；不能把「測試合成 COMPILER 信封通過」表述為「真實 Android release compiler 已簽名」。

## TCL 驗收結果的邊界

已做：完整 3,100 來源覆蓋、1,414 逐列人工審核工作清單、12 個功能群原始風險收受草稿、來源/政策/翻譯 digest、防檔案 symlink 替代外部信任錨、負面 release 測試。

仍未做：真實 GitHub Environment 的保護配置、三把正式獨立簽署者、G1/G4 獨立批准、真人語義審閱、12 群真正殘餘風險處置、正式 compiler attestation 與 L14 實機驗收。

此分支沒有修改 Android App 程式、app/src/main/res、Room、音樂庫、播放歷史或鬧鐘行為。正式人類批准數目仍為 0，正式 XML 與 APK 均未產生。
