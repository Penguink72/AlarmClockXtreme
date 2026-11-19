# LP-G3｜逐 Key 候選譯文隔離區

LP0 既有 155/200 譯文不是 LLM 新翻譯，但也**未經品質審核**；G3 會保留其原始值及身份，禁止 LLM 提案覆蓋。

本輪由 GPT-6 對原本缺漏的 21 個 Key 各提出繁中、日文候選（共 42 條草稿）。其餘 24 個缺漏 Key、每種語言均保持 MISSING，避免把不確定的文本填進正式包。

- 資料：`localization/governance/g3/draft-proposals.json`
- 工具：`scripts/localization/g3-build-candidates.mjs`、同名測試
- 每條候選都保有 Key、sourceHash、格式佔位符、概念、G2 來源證據與`humanApproval:null`。
- 出現未知 Key、重複、格式參數不一致、想覆蓋既有譯文時，生成整批中止。
- **此階段不准承諾翻譯正確，不會直接修改 app/src/main/res 任何字串。**

```sh
node scripts/localization/g3-build-candidates.mjs --out localization/governance/g3/review-queue.generated.json
node scripts/localization/g3-build-candidates.test.mjs
```

## Batch 2（2026-11-20）

補上剩餘 24 個缺漏 Key，各提供繁中與日文候選，共 48 條新增模型草稿。現在各語言共有 **155 條既有未審譯文 + 45 條模型草稿 = 200/200 有文字**；獨立核准數仍是 **0**。G3 不更動 LP0 原始 packs，且不把這些草稿當作正式譯文。

注意 `stats_recent_events` 的 `%1$s` 必須原樣保留；`bedtime_update_health_connect_before_recent_sleep` 對應 PROVIDER_UPDATE_REQUIRED，不是要求同步睡眠資料。
