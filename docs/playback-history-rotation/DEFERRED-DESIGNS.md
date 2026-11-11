# 已延後設計｜歷史想法，不是現行工作

**DEFERRED／NOT AUTHORIZED（2026-11-11）**。除非使用者未來有實際需要並再次明確要求，Coding Agent 不得自行恢復、安排工期或實作以下方案。

| 舊需求／方案 | 現在的處理 |
|---|---|
| 20 首 URI 池／逐首勾選或貼 URI 作為新音樂庫 | 不符合 3,000 首整批導入；**原版 20 首功能保留以維持舊鬧鐘相容**，但不當新庫上限 |
| 先洗牌整個歌單、shuffleModeEnabled＋REPEAT_MODE_ALL、輪過全部才重複 | 延後；目前**播完即獨立再抽**，容許相同歌曲立即重複 |
| 每日輪換起始歌曲、最近 N 首不重複、shuffle bag、時區選曲、加權 | 延後 |
| completed／interrupted／failed、播放長度／結束時間、每次回寫 | 延後；目前只追加真正**開始播放時的歌名和時間** |
| PlaybackEvent 多狀態 Room table、完整事件模型及其 migration | 延後；先評估簡單私有日誌；**若為新鬧鐘模式修改 Alarm entity，仍需必要 Room migration** |
| 歷史跨重裝／跨版本保證、雲端備份、匯出、統計、按歷史重播 | 延後 |
| Spotify／Radio／YouTube 連播、影片容器、crossfade、逐首偏好權重 | 延後 |
| 將 3,000 首大庫或資料夾遞迴留到未來 | **已改為當前必做，不再延後** |
| 將歌名顯示／播放歷史延到舊 G2 | **已併入當前 G1 必做，不再延後** |

不可延後的底線：定時鬧鐘可靠、解除及貪睡後不再響、無效音樂備援、只記真正開始播放、Android 合法授權、測試和 APK 真實結果、原版設定相容。

優先順序：使用者最新明示要求 → [CURRENT-REQUIREMENTS.md](CURRENT-REQUIREMENTS.md) → [TCL-G0-G1-OVERVIEW.md](TCL-G0-G1-OVERVIEW.md) 和 [G1-IMPLEMENTATION-PLAN.md](G1-IMPLEMENTATION-PLAN.md) → G0／P0 建置證據 → B0 歷史資料（不得直接當施工單）。