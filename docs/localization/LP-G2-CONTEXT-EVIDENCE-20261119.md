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
