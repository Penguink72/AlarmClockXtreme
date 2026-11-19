# LP-G2｜字串真實呼叫點取證

`g2-index-context.mjs` 掃描目前 checkout 的 app/src/main 與 wear/src/main Kotlin、Java 和 XML，找出 `R.string.<key>` / `@string/<key>` 靜態參照。輸出 200 Key 的 context-evidence.json。

## 證據界線

- `STATIC_SOURCE_REFERENCE_FOUND` 只表示程式文字中看得到該 Key。**不能證明畫面實際顯示、翻譯語義正確或鬧鐘操作後果**。
- `NO_STATIC_REFERENCE_FOUND` 可能是動態引用、未用、其他來源或檢索不足，**不等於完全沒有使用**。
- `surfaceHint` 由找到的路徑判斷類別，`guessedSurface` 保留 LP0 舊推測；均不是人工核准情境。
- G4 語義核准還需檢查操作實際結果、正反詞與手勢方向，尤其 Snooze/Dismiss、Delete/Disable、Save/Schedule。
- G2 追溯輸出不會改 Android UI。

從 repo 根目錄：

```sh
node scripts/localization/g2-index-context.mjs --out localization/governance/g2/context-evidence.json
node scripts/localization/g2-index-context.test.mjs
```

語義審核維持 **0**；不可將已找到參照當作 approved。

## 2026-11-20 G2 行為概念例外層

新增 `localization/governance/g2/concept-exclusions.json`，保留原始 LP0 字根推測作為歷史證據，並對已確認不適用的 **10 條 Key／術語連結**建立可追溯的排除規則。前九條來自 G4 第一批，另新增 `stats_track_consistency_snooze_behavior_and_which` 的 Track 是動詞（追蹤規律），不是歌曲 Track。

G2 輸出分別保留 `rawHeuristicConceptIds`、`conceptIds`（有效映射）和 `excludedConceptIds`。G3 後續只從 G2 有效映射取得概念。G4 情境證據雜湊納入概念版本與排除關係，防止改了術語分類卻沿用舊審核。這是模型檢查的概念排除，不是人類翻譯核准。
