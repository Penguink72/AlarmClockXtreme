# LP-G4｜翻譯品質驗收與正式核准阻斷

本階段不是讓 LLM 給自己一個品質分數，而是兩層檢驗。

**機械層**（可自動）：200 Key 對應、來源 Hash、格式參數、語系、概念參照、G2 呼叫點證據、批次完整度及提交記錄。任何不合格立刻拒絕。

**語義層**（不得自動冒充）：操作含義、Snooze/Dismiss/Stop/Disable 分辨、是否增譯或漏譯、畫面實際情境、翻譯可讀性、來源更動。G1 詞彙核准與 G2 情境核准也是正式發布前的必要契約。

*　**目前 ledger approvals 為空**，原有譯文及模型提案一律不得進正式包。
*　核准紀錄未來必須明確包含審核者、日期、譯文雜湊、來源雜湊、術語版本、G2 情境證據雜湊、獨立語義與動作結果確認，以及可稽核理由。
*　G4 不提供 AI 自動代簽、過期批准沿用或只憑原文與翻譯相似度解鎖。
*　測試中的批准只是一筆記憶體中的 synthetic fixture，不寫入 Repo，也不算真實審核。

```sh
node scripts/localization/g4-review-gate.mjs --out localization/governance/g4/qa-report.generated.json
node scripts/localization/g4-review-gate.test.mjs
```
