# AlarmClockXtreme｜A0→A1/A2｜B0/B1/B2｜C1｜D0 產品交付 TCL 規劃（2026-11-21）

> 使用者授權本輪**建立 A0 整合候選與全產品狀態帳本，規劃 A1、A2、B0、B1、B2、C1、D0，補充語言解析器／語言包／翻譯文本**。本計畫不將各後續階段誤稱已實作，也不授權直接合併 main、正式簽發語言包或破壞使用者資料。

## 1. 根與依賴

`R0` 日常可依賴且低摩擦的個人音樂鬧鐘 → `R1` 響鈴、`R2` 音樂、`R3` 操作、`R4` 狀態、`R5` 語言；`R6` 是橫向檢查與版本治理，不是另一個產品主功能。

- **A0（本次施工）**：從 Q2 確切 SHA `4ad5ee289d9c8e0191272601fc2333be18e80615` 建立 `integration/acx-r0-product-candidate-20261121`；新增全產品帳本、規劃和語言架構／翻譯文件。此 SHA 已包含 #13 核心與 #14～#29 對應祖先，無須 cherry-pick；不動 main。
- **A1（規劃待施工）**：可靠性、排程 ACK、貪睡與真實診斷；基於 A0 隔離枝，保持 G1/G2/G3 程式狀態及證據。
- **A2（規劃待施工）**：首頁五秒快設、歷史一鍵回播、音樂中心入口/聲音來源唯一狀態、播放清單與原廠鈴聲排除閉環。
- **B0（規劃待施工）**：全 App Android 資源／Compose／Context／通知／後台呼叫、plurals、array、格式化與可見操作盤點；正式解析器契約和語言包 V1。
- **B1（規劃待施工）**：使用者可建立、編輯、匯出、匯入、自訂 locale、單語與主／次語言雙語預覽／使用，獨立版本管理；不需全部官方包獨立核准才能做自訂功能。
- **B2（規劃待施工）**：擴至全 App 可用顯示點，包括參數／複數／通知／響鈴／無障礙；不得把 200-Key Debug 覆蓋率當全程達標。
- **C1（規劃待施工）**：G1 詞彙、G2 真實上下文、Q1/Q2 的 27 筆建議、五個不明靜態 Key、21 個格式 Key，逐一處置並形成可追溯的官方包審核管線。
- **D0（規劃待施工）**：整合 A/B/C 的功能交付條件，嚴格 APK／單測／簽章／回歸／安裝與目標效能核對，明確做出 release/no-go 裁定。正式 main 合併須另有授權。

A1、A2 與 B0 可在 A0 穩定時分開施工；B1 依賴 B0，B2 依賴 B1 的解析器契約；C1 可與 B0～B2 平行，**僅官方認證內容發布依賴 C1 審核**。D0 依賴所有欲列入本次發布範圍的工作包，而非強迫所有長期候選完成。

## 2. A0｜整合候選：不可只命名，不驗證

- 來源：PR #29 head `4ad5ee28`；PR #13 head `c4dd6353` 是其祖先。相對 main：ahead 384、behind 0（建立時快照）；Q2 比 B3 Android 測試 SHA `1d0920ed` 多四個提交，未更改 app/ 執行檔案。
- 分支內容：原有所有程式、語言資源與 CI，不改音樂或排程核心；新增 [R0 全產品帳本](ACX-R0-FULL-PRODUCT-STATUS-LEDGER-20261121.md)、本文與 [R5 解析與包契約](../localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)、[C1 翻譯來源與術語](../localization/ACX-C1-TRANSLATION-TEXT-EVIDENCE-20261121.md)。
- A0 自身驗收：可定位唯一候選 head、沒有平行分支手動合併、GitHub Draft PR 指向 #29、明示目前任何 CI 與 APK 屬於哪個 SHA；至少 Android F-Droid Debug 嚴格 build + full unit tests + 第二 Runner upgrade/signing、LP-G/G6 Node preflight 必須對 A0 head 重新執行並儲存 Run ID。未跑完前 `A0 status=INTEGRATION_CANDIDATE_UNVERIFIED`。
- A0 是可供工程與 APK 回歸的**候選**，不是可公開發布的簽章 Release。

## 3. A1｜可靠性封閉（R1，高優先）

### A1-1 操作 ACK
從快速新增、單筆啟用、批次啟用、貪睡安排到解除，各自辨認 `EXACT / INEXACT / SUPPRESSED / FAILED`，排程被拒不顯示成功。不可用 Room 寫入成功冒充 OS 排程 ACK。

### A1-2 原亮屏貪睡問題
重用 G1 的冷啟動、ForegroundService、PendingIntent 與 Room 回滾、可匯出 incident timeline；Android 單測覆蓋正常、失敗、幽靈排程、取消、搶占。**原特定手機保持亮屏右滑貪睡 10 分鐘再次起聲的 T3 未實測，不可關閉**；依既有授權不阻斷後續 Code-Go。僅能記錄為剩餘 Release 風險。

### A1-3 G2 獨立真實測試
即時試聽 ≠ 正式排程測試；正式測試應通過 AlarmManager→Receiver→AlarmService→Media3／備援，fireId/UUID/scheduledAtMs 綁定，且測試不污染真實 AlarmEvent、播放歷史或第三方整合。

**A1 停止條件**：無未處理的 T2 P0 回歸；每個失敗情境有明確回報與回滾，T3 欄位誠實標記。禁止因為未做 T3 一直重寫已可驗的服務。

## 4. A2｜產品操作閉環（R2/R3/R4，高優先）

- **A2-1 五秒設鬧鐘**：首頁快速入口→選時間→確認 OS 已接受；量測**從第一個操作到確認**，而非僅計算程式排程時間。歷史指標 P50≤5s、P90≤8s；沒有量測資料就標 P=UNVERIFIED。
- **A2-2 歷史 1–2 操作回播**：首頁最近一首、音樂中心歷史列表可直接啟播，警報響鈴優先搶占；播放歷史只在觀察實際起聲才寫入。
- **A2-3 單一有效來源**：音樂庫／指定清單／系統鈴聲為每鬧鐘互斥選項，UI 編輯草稿→唯一儲存→OS 排程 ACK→讀回；匯出／匯入與來源失效不偷偷篡改使用者選擇。
- **A2-4 大曲庫與排除候選**：已有 SAF 遞迴、4935 索引與清單操作，測量實際搜尋、批次加入、套用與讀回延遲。原廠系統音僅展示疑似候選，使用者確認並可 Undo；禁止不可逆的掃描即刪除。
- **A2-5 音樂中心 UI**：同一入口按使用者意圖顯示近期回播、設定此鬧鐘來源、管理庫／清單／排除，不把 MediaStore、SAF、URI 等技術分類升為首頁導航。

**A2 停止條件**：同一套操作路徑端到端可達；只做缺口，不重建 Room/Media3。計時與跨狀態讀回的負向測試必須存在。

## 5. B0/B1/B2｜語言功能由實驗升級為真正產品（R5）

- 詳述與資料契約見 [R5 解析器架構](../localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)。
- **B0 盤點**：全 App 三語 `strings.xml` 的實際資源總量、Android `plurals/arrays`、`R.string` 靜態及間接引用、`context.getString`、通知／服務、Compose，產出測試與來源分類。`200` 是治理試驗樣本；不得繼續以其為整個 App 完成率分母。
- **B0 設計**：從 `Lp2PreviewText` 拆分 `catalog+pack validation`、`repository/importer`、`resolver`、`Compose adapter`、`Android context/notification adapter`、`fallback`、`diagnostics`；不將單純 Debug 的全域 session 直接改為 Release 全域副作用。凍結鍵名、哈雜湊及版本 schema。
- **B1 互動**：現有 LP1 Lab 先盤點重用；新包先驗證後原子寫入；維持有可用舊包的回滾。自訂語系可按 source revision 作差異遷移；缺漏在一般介面使用明示 fallback，但不能把未審候選標官方認證。
- **B2 接線**：先高頻 Compose 及設定／音樂／歷史，再處理帶參數、複數／格式、通知／響鈴；不同字級和 TalkBack 的 T3 由具代表性的高風險場景驗收。不要在響鈴控制介面直接把兩語長句拼成同一段不可操作文字。

**B 線產品完成條件**：至少一份新 locale 的自訂語言包可以在正式受控使用模式匯入、儲存、切換、重啟保留，改變實際畫面；正常雙語模式能顯示兩語且不誤用使用者曲名/診斷。全 App 接線需要以盤點後的有效引用分母衡量。

## 6. C1｜翻譯文本與官方品質路徑（與 B 平行）

- 現有詞彙／文本：18 個 G1 候選概念；G6 的 200 個 Key、繁中／日文共 400 筆**模型表層初審**；27 筆未採用修訂；14 CRITICAL Key；5 個未找到直接引用的來源；21 個格式 Key。來源與具體欄位見 [C1 文本證據契約](../localization/ACX-C1-TRANSLATION-TEXT-EVIDENCE-20261121.md)。
- C1 必須區分 **原文**、**既有中日翻譯**、**模型候選**、**審核意見**、**獨立批准**、**使用者自訂包**。不得將 G4 的 `OPEN` 源缺陷從歷史刪掉；為 G6 現行來源建立可追溯的補救處置，僅審核有效的當前 Key。
- 正式官方認證包的 release gate 與自訂包機械匯入 gate **分開**；自訂包沒有獨立批准仍可作使用者指定用途，但不得暗示官方品質，安全相關字串需要有明確原生回退與風險告知。
- **C1 停止條件**：來源、用例及決定具可追溯欄位；已判定廢棄 Key 不必硬湊核准；獨立審核者未批准時官方包應持續拒絕，不應讓模型自己填 Q PASS。

## 7. D0｜版本交付決策

- 候選 SHA 同時通過 Android 嚴格 F-Droid Debug 編譯、全部單測、APK 升級簽章、Node 語言治理與負向案例；所有 CI 不能跨 SHA 冒用。
- 由 A1/A2/B1/B2 功能矩陣對照實際 APK 操作，記錄有無醒鐘再響風險、使用者資料遺失、無聲備援、來源撤銷、快速操作時長、語系切換與回退。
- **Release 判準分離**：(i) 既有三語 App 功能；(ii) 可匯入但非官方認證的自訂包功能；(iii) 官方受審的多語言包。只有 (iii) 需要該語系適用的獨立批准和完整有效 Key 覆蓋。現行 G5/G6 官方包 fail-closed 不得繞開；新契約先在另枝測試與授權後實作。
- Debug、正式商店發布和 main 合併是三個不同決策；D0 交付 `GO / CONDITIONAL / NO-GO` 及公開剩餘風險，不默自動執行發布或 main 合併。

## 8. 分支與提交協作契約

- A0 僅從固定 Q2 SHA 出發，**不得直接動 #13～#29 祖先分支**。後續 A1、A2、B0～B2、C1 分別建隔離枝與 Draft PR；必要時以新固定整合基準串接，避免錯誤分支 base 與跨支系重複提交。
- 新提交前讀回該枝 head，避免覆寫平行作者。禁止改動已使用的 Room v28、簽章與 packageId；資料結構遷移另立實證切片。文檔更新也要標明基準 SHA。
- 本輪只有 A0 文件／CI 入口為實作。A1/A2/B0/B1/B2/C1/D0 的程式、可操作介面、最終核准、發布均標記 **PLANNED_NOT_EXECUTED**。
