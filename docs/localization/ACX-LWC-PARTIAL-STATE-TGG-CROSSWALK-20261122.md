# ACX LWC-IR2｜Partial-State Lenses × TGG × Typed Contract 與本地化流程接續（2026-11-22）

> **本文件是候選研究與工程切片規劃，不是已施工的翻譯包或 Runtime。** 本輪不改 `strings.xml`、Kotlin/Compose/Room、AlarmService、Media3、CI、APK；不合併任何平行產品 PR。沿用 2026-11-22 已記錄的 [LWC-R1](ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)、[LWC-W1](ACX-LWC-TRANSLATION-WORKFLOW-20261122.md) 與 [LWC-TCL](../tcl/ACX-LWC-LOCALIZATION-TCL-ROADMAP-20261122.md)。本文件 branch 以 `docs/acx-lwc-localization-research-20261122@03054c4cf755fabcbe3bc06dbb7a6ffabf2b0fa7` 為起點，PR 應對準該候選文件分支，勿誤說已在 `main`。

## 0. 異實異時異步互鏈

| Repo | 本輪對應角色 | 連結 |
|---|---|---|
| HANDS | Localization IR 的 `PartialIntentFacet`、`CorrespondenceMap`、權限／證據與反分叉 Seed 候選 | [HANDS 新 Seed](https://github.com/Penguink72/HANDS/blob/docs/localization-ir-partial-intent-seed-20261122/HANDS/06_planning/subsystem-federation-line/HANDS-LOCALIZATION-IR-PARTIAL-INTENT-TGG-ASYNC-SEED-001.md) |
| Writing-tool | 空間超文本、Persona／八識十地、局部 Scheme 的本地適配 | [Writing-tool 新候選](https://github.com/Penguink72/Writing-tool/blob/docs/localization-ir-spatial-partial-state-20261122/%E8%A6%8F%E5%8A%83/2026-11-22-Localization-IR-Partial-State-%E7%A9%BA%E9%96%93Persona%E8%88%87%E8%A8%98%E6%86%B6%E6%8A%95%E5%BD%B1-%E5%80%99%E9%81%B8.md) |
| AlarmClockXtreme | Android locale 資源、既有 LWC 與 Node scanner 的實際來源映射 | 本文件 |

**維持三 Repo 異步和各自 Done**。HANDS 跨域契約的名字與候選狀態，不自動改寫本 App 翻譯資源，也不把正式 UI 文案批准交由其他 Repo。

## 1. TCL Root：文字翻譯如何反證通用 IR？

現況原始證據（歷史快照，不當作本日 main 真相）：
- B0 2026-11-21 抽取 **2,471** base Key，其中 **2,462** 可翻譯；繁中／日文各 **912** 有定義、各 **1,550** 缺該語專屬定義。
- B0-T1 matrix、B1-C1 自訂 pack validator/repository、B0-L1 locale coordinator、G6 200-key 初審分處不同分支；不能當作已整合全 App。
- P1～P3 音樂來源、資料夾、歷史等已在實機由使用者回報符合預期；本輪只規劃本地化／轉換測試，**不重新開啟音樂功能**。

**Root**：同一 Source Key 可能被多個 Binding 使用，而其操作語義依 Context 改變。本地化轉換要區分 literal/text、Locale、UI surface、action semantics；當 Source 或 Context 局部改變，不應使所有翻譯和播放功能全部失效。

## 2. 四種研究的工程分工

| 方法 | ACX 應借用 | 不應做 |
|---|---|---|
| Scala 3 typed contract（HANDS 候選） | 型別種類、ID namespace、Knowledge 與意圖分軸的規則概念；可用 Node test 表達 | 為 Android 引入另一個 Scala 檢查器或強制 HANDS runtime |
| Hazel typed holes | 對尚缺 Context/Binder 的字串保留待確認位置，不亂填假資訊 | 把 `UNKNOWN_MAPPING` 誤當可直接翻譯 |
| Partial-State Lenses | 新 locale 候選只指定相應語系／目標 key，其他語言可保持不指定；明示 Preserve／Clear 的差異 | 把 Unspecified 誤當 Unknown 值、把驗證 PASS 當可發版 |
| TGG correspondence | SourceGraph/BindingCorrespondence/LocaleTargetGraph、雙向來源查回與局部受影響分析 | 不加證據地推導操作意圖，不在 Android runtime 引入 Neo4j／TGG 引擎 |

論文：[Lenses for Partially-Specified States](https://link.springer.com/chapter/10.1007/978-3-032-22723-2_2)；[Hazel](https://hazel.org/)；[TGG eMoflon Neo](https://emoflon.org/neo/)；[Scala 3 ADT](https://docs.scala-lang.org/scala3/reference/enums/adts.html)。注意 **IBeX-TGG 2024 年已停止開發**，不推薦列為必裝依賴。

## 3. 具體例子：相同 Remove，不同意義

```text
SourceGraph:
  text="Remove", key=action_remove, sourceHash=X
Correspondence:
  binding=playlist_remove, context="從播放池排除"
  actionSemantic="not deleting source audio file"
Target:
  zh-Hant-TW="從清單移除"（僅候選例，非已審核譯文）
  ja="リストから削除"（僅候選例，非已審核譯文）
另一 Binding:
  binding=delete_audio_file, actionSemantic="delete original file"
  => 即使原始 Source 字面相同，也不得宣稱上述譯文可安全重用
```

這是行為歧義測例，不是說現在程式真的有 `action_remove` Key 或永久刪檔操作；實作前必須掃描真實來源並用真實 key／function 改寫 fixture。語義是操作契約；翻譯文字只是在指定操作下的候選呈現。

**本地 Key→Binding→Target 最小需求**：`resourceType`、`resourceKey`、`sourceHash`、`bindingRef`、`surface`、`contextRevision?`（新候選）、`locale`、`translationRevision`、`reviewEvidence`、`runtimePath`、`unknownDynamic`。不得默認 `contextRevision` 已存在於 `acx-language-pack/1`。V1 typed key + sourceHash 仍是既有的真實格式。

## 4. PartialIntent：與 Scala Knowledge 軸正交

| 例 | Knowledge / evidence | 這次更新意圖 |
|---|---|---|
| 繁中 Key 尚未查出該用何種術語 | Unknown[TermSense] | Unspecified（暫不要求寫入） |
| 已確認新繁中候選 | Known(candidate) | SetTo(candidate)（僅該語系 key） |
| 日文已有獨立核准內容，這次不允許更新 | Known(approved revision) | Preserve(revision) |
| 使用者清除自訂語言包的某個覆寫 | Known(override revision) | Clear（僅清除允許的覆寫，不刪內建字串） |
| 來源語義不清或操作不明 | Conflicted[ActionSense] | NeedsEvidence，不得靜默合併 |

`Unspecified` 表示這次未指定，不等於可任意改其他 key。`Preserve` 是有基準版本的明確限制。`Clear` 在不同資源狀態下行為各異，候選試驗必須禁止清除必要原始 key／設備音訊。此為**本 App 適配器的設計**，不是 ps-lens 論文原始 API。

## 5. LWC 來源依賴圖與選擇性重驗

```text
Source.resourceKey --used_by--> Binding.callsite
      |                             |
      | sourceHash                  | actionSemantic / Context
      v                             v
Locale.Target(zh/ja) <--validates-- Review / Formatter
      |                             |
      v                             v
Android UI / Service / Notification ----> evidence / CI
```

- Source 改：局部重新檢查這個 key 的 locale 候選、格式與相依 surfaces。
- Context / operation 改但 literal 不變：語義重新審核、action-specific UI test。
- Binding／resolver 改：局部重測 locale 切換／通知／重啟，不能自行推論所有譯文失效。
- 新增 locale：舊 locale 若未受影響，保持原先核准記錄；可用 `Preserve` 明示新作業不得寫入舊包。
- Source／Target 關係不唯一：保留多 Binding，標 `AMBIGUOUS_MAPPING` 或 `UNRESOLVED_DYNAMIC`，不可推定一對一。
- 來源變動的舊審核證據保留為舊 epoch 歷史；新 epoch 是否適用另行標記，不覆蓋先前使用者實機驗收。

## 6. TCL 工程施工切片，優先順序不變

| Slice | 類型 | 預期可檢結果 | 狀態 |
|---|---|---|---|
| L10（原主線） | B0/B0-T1 Code-first Source→Binding→Surface，完整缺口分類 | 可重建 TSV/JSON、未解動態引用明示 | 尚待實作 |
| L10-P | 僅為 L10 加一組 PartialIntent／Correspondence 負向 fixture | `Unknown≠Unspecified≠Preserve` 被測試區分 | 新候選 |
| L11/L12 | 逐 Key 中日候選、結構／語義審核、產物 | 不覆寫既有批准、未通過不冒稱已審 | 原規劃 |
| L13 | LocaleCoordinator + B1-C1 + B2 runtime 解析 | Context/Compose/通知與 fallback 正確 | 原規劃 |
| L14 | 本地 JVM／UI＋定向實機 | 新增操作不破壞 P1～P3；僅受影響 case 重測 | 原規劃 |
| L15 | same SHA 的正式整合與授權 | PR/CI 與驗收不互相冒用，另行批准 | 原規劃 |

**完成點**：本文件 docs-only 落地、能連回 LWC 原案、與 HANDS/Writing-tool 互鏈；沒有先把 Scala/TGG runtime 放進 Android。對原 App 最有價值的實作仍是 **L10 全 App 資源與使用點映射**，不讓研究框架阻塞已證可用的產品路徑。

## 7. 五項跨域反例與 PRTP 停止條件

1. 身分：同一 Resource Key 多個操作 Binding，不能折疊為一個操作。
2. 語義：`Remove from playlist` 不可被當成 `delete source file`。
3. 操作：切換 Locale/自訂包不得修改媒體來源、播放池、排程。
4. 權限：模型初審與局部 correspondence PASS 不等於正式官方翻譯 pack 批准。
5. 回放與拒絕：相同源版本能重算；操作語義未知時明示 UNKNOWN，沒有 source binding 不得偽造。
6. PRTP 補充：如 `L10` 已足以解決缺譯，保留原題 `RETAIN`，不因研究跨系統理論而改變產品驗收分母。

## 8. 遠端原案查詢

- [全 App Resource baseline（B0）](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-full-locale-inventory-20261121/docs/localization/ACX-B0-FULL-APP-RESOURCE-BASELINE-20261121.md)
- [三語矩陣（B0-T1）](https://github.com/Penguink72/AlarmClockXtreme/blob/feature/acx-b0-t1-trilingual-catalog-20261121/docs/localization/ACX-B0-T1-TRILINGUAL-MATRIX-20261121.md)
- [R5 B0～B2 Locale Runtime／Pack Contract](https://github.com/Penguink72/AlarmClockXtreme/blob/integration/acx-r0-product-candidate-20261121/docs/localization/ACX-B0-B2-LOCALE-RUNTIME-AND-PACK-CONTRACT-20261121.md)
- [LWC-Research](ACX-LWC-CROSS-APP-LOCALIZATION-RESEARCH-20261122.md)
- [LWC-Workflow](ACX-LWC-TRANSLATION-WORKFLOW-20261122.md)
- [LWC-TCL](../tcl/ACX-LWC-LOCALIZATION-TCL-ROADMAP-20261122.md)

**沒有更新 `AGENTS.md`、測試、字串或任何正式程式。**