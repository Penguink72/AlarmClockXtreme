# LP-G5｜經治理驗證的語言包生成

兩種**嚴格隔離的輸出模式**：

1. `--mode preview`：導出 Android LP1 Debug 可以匯入的 JSON 範例，保留原有 155 條和模型新增 21 條，合計繁中/日文各 176/200。**每條都沒有取得正式語義核准**，包外另附 `manifest.json` 的 `UNREVIEWED_PREVIEW_NOT_RELEASE` 警語。它是試算資料，不是可發布語言包。
2. `--mode release`：只允許真實具備獨立人工作業審核、與原文和術語版本綁定的譯文。當 G1 術語庫尚未獲核准或 G4 沒有批准條目時，**故意失敗而不是產生假的正式包**。

機械檢查連結 `Source Catalog → G1 Glossary → G2 Callsite Evidence → G3 Candidate Queue → G4 Independent Review Ledger → G5 Pack`。更新任一來源都會使舊審核失效。

```sh
node scripts/localization/g5-build-language-packs.mjs --mode preview --out-dir build/localization/UNREVIEWED_PREVIEW_NOT_RELEASE
node scripts/localization/g5-build-language-packs.test.mjs
# Expected to fail safely until explicit approvals exist:
node scripts/localization/g5-build-language-packs.mjs --mode release
```

目前不允許正式發行，也未更動任何原本的 app/src/main/res 字串。

## 2026-11-20 預覽覆蓋率

G3 的兩批草稿共 45 Key、兩語共 90 條，G5 preview 各語言可輸出 200/200 Key；其中原有 155 條與模型候選 45 條仍均**未經獨立語義核准**。這是字串覆蓋，不是翻譯完成品質。G5 `--mode release` 仍需拒絕。

## 正式發布契約加嚴（2026-11-20）

先前只要求「至少一條翻譯核准」，理論上允許剩餘缺譯使用英文備援，與本 App 的完整單語目標矛盾。本次已修正：LP0 **兩種目標語言各 200/200 都要具備獨立、未過期、對應原文與術語版本的核准紀錄**，否則 `--mode release` 整體失敗。預覽模式不受影響。

G5 最終 CLI 會重新以真實 `review-ledger.json`、G2 實際程式參照及 G3 候選計算 G4 品質報告，並比對已生成的 QA JSON，防止只修改報告中的 `releaseAllowed` 偽造核准。測試裡的全核准資料僅存在記憶體，不會寫回 Repo，也不構成真實審核。
