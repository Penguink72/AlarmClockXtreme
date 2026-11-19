# LP0｜200 字串首次治理盤點（2026-11-19）

## 真實來源

- 來源：PR #14 已存在的 200 Key 的 `experiments/lp0-200/catalog.json` 及 `packs/{en,zh-TW,ja}.json`。
- 此次只建立資料治理對照：沒有重新翻譯、沒有修改既有字串，沒有提升任何一條審核狀態。
- 統計：英文 200；繁中有字串 155、缺 45；日文有字串 155、缺 45。
- 200 條的 UI 位置都僅依 Key 名稱推測，未檢查 Compose 呼叫點，故標記 `UNVERIFIED`。
- 第一輪依關鍵詞作高優先語義複核標記 **52** 條；此為候選，不是 52 條錯誤。
- 18 個術語概念為初稿，已用來源 Key 或英文原文的模式關聯 **151** 條；49 條未有候選概念，並不代表需要新增 49 個術語。

## 實際候選差異（非定罪）

`snooze_duration` 的繁中是「貪睡間隔」；`notif_snooze_countdown_title` 是「已稍後提醒的鬧鐘」；`firing_swipe_right_snooze` 是「向右滑動以稍後提醒」；`firing_tap_snooze` 是「點選以貪睡」。

- **可能的問題**：用詞未經統一，按鈕、手勢提示和通知中是否有許可變體尚未定義。
- **也可能合理**：提示文字較長，使用「稍後提醒」讓初次使用者更容易理解。
- **目前處置**：只登記 `alarm.action.snooze` 概念候選，不自動取代、不升級狀態。

`firing_status_swipe_or_snooze` 同句含 Dismiss/Snooze；`firing_challenge_before_dismiss` 是解除前的挑戰提示。此類動作方向與後果應列 LP-G2 真實行為對照優先。

## 允許進入語言包的東西

現有 LP0/LP1 的 JSON 可作技術實驗的原始翻譯包，仍有未審詞及英文備援。這次 LP0 不會把它們改為正式發行的「品質核准語言包」。

後續建立新翻譯、讓任意 LLM 提交成果時，必須先有來源、語言、Key、來源 hash、上下文版本與審核簽收，然後才由獨立 build gate 輸出正式語言包。

## 執行

在 repo 根目錄執行：

```sh
node scripts/localization/lp0-audit.mjs
node scripts/localization/lp0-audit.test.mjs
node experiments/lp0-200/verify-resources.test.js
node experiments/lp0-200/probe-core.test.js
```

只使用 Node.js 內建模組，不需要 npm install，也不需要 Python。CI 只讀、只檢查資料，不啟用 Android SDK。
