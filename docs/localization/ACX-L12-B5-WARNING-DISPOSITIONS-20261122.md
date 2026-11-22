# ACX L12-5｜警示型別化與獨立審核工作列（2026-11-22）

> 僅檢查與整理 L11-9／L12-4 的 2,986 筆模型候選，不修改候選、官方語系 XML、G1/G4 批准或正式發布資源。

## 固定來源與結果
- 共 43 筆不同 key＋locale 警示、45 個原始警示旗標；類別重疊不重複計數。
- 進一步對警示進行型別分類：真正疑似未翻譯英文 1、可能保留的專有名詞或識別文字 15、僅含參數及分隔符號的格式骨架 13、Android 百分比格式 12、XML 引號／換行處理 4；其中操作敏感項目 20（類別重疊）。這是更精確的候選分類，不是正式批准。
- 未變英文包含 `Wi-Fi`、`Philips Hue`、`Spotify`、`%1$s • %2$s` 等，**不能一律當缺譯或直接判定已批准保留**。
- 百分比警示包含 `%1$d%%` 格式、說明文字 `50%`，需依 callsite 的格式化 API 與實際顯示用途進行判定；aapt2 隔離成功不能保證 Runtime。
- `\\n` 在部分候選中為 Android 字串跳脫，應以資源及畫面檢查而非無條件改成真實換行。

## 程式及 CI
- `scripts/localization/l12-b5-warning-disposition.mjs` 將 L12-4 triage 與 L12-3 preflight 一對一校驗；檢查來源雜湊、翻譯雜湊、ReviewRevision；不得接受上游偽造批准。
- `scripts/localization/l12-b5-warning-disposition.test.mjs`：分類、多旗標去重、來源漂移、未授權批准拒絕。
- `.github/workflows/l12-b5-review.yml`：保存 JSON review queue，不複製至正式 XML。
- 首次成功 CI：[Run #45036760233](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/45036760233)；修正格式骨架與品牌辨識後的最新成功 [Run #45038114632](https://github.com/Penguink72/AlarmClockXtreme/actions/runs/45038114632)，HEAD `7166c5a5dd76e703b1fbf76b83cd699d3ae33e4f`，Node tests 6/6，43 警示工作列、45 原始旗標。
- 實際獨立語義批准 0；警示解除 0；L12-6 尚待處理。

## 高風險審查指引
1. 鬧鐘停止／貪睡、備份恢復、外部傳輸的文字要核對 Kotlin 實際操作後果；來源與翻譯意義不合者只能出修訂候選。
2. 專有名詞／動態 placeholder 能保留原文，但必須有來源、語義用途與審核理由，不可把一般英文漏翻隱藏成 fallback。
3. 百分比格式需檢查參數個數、實際格式化路徑及畫面，不能用字面正規表達式取代 Android Runtime 證據。
4. 更新任一候選後，舊 `translationHash`／`reviewRevision` 證據失效，重新產生審查工作列。
