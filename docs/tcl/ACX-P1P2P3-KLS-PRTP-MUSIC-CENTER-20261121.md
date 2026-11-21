# ACX P1–P3｜從 KLS／PRTP 問題重新表徵到獨立音樂中心

- 日期：2026-11-21（Asia/Taipei）
- 證據來源：本輪使用者已確認「原始隨機音樂鬧鐘＋實際播放紀錄」滿足需求，並明示同意 P1–P3 施工；原始設計保留為已獲使用者完成確認的 Q0
- 本候選：`feature/acx-p1p2p3-music-workspace-20261121`（PR #38，base PR #37）
- 資格：工程候選。程式與單測結構的存在不等同最終 Android T2；未經實機操作不得升格 T3/P；未授權合併 main
- 不修改：Room schema、用戶歌曲／檔案、歷史已有的 start event、排程、AlarmService／Media3／Snooze/Dismiss、B1/R5 翻譯內容正本

## 0. KLS 根—枝—葉—業

**根（Q0）：** 能用手機歌曲作鬧鐘隨機連播並看到實際播放記錄。使用者已確認滿足；此結論是使用者當時安裝版的產品回饋，不宣稱新分支所有 OS 都經手機測過。

**新根（Q1）：** 不進任一鬧鐘就能整理手機媒體資料夾、維護具名播放清單，從實際播放歷史另存喜歡的曲目；鬧鐘僅引用已保存的池或指定清單，設定來源選擇不干涉獨立清單編輯。

**枝：**
1. P1 資料夾優先的獨立音樂中心
2. P2 歷史歌曲到現有／收藏播放清單的有界引用
3. P3 具名清單和 Smart All 分離（除非使用者明確執行全域加入／排除）

**KLS 分層：**
- L0 Source：MediaStore / SAF 來源與權限／真實檔案；從 App 移除不等於刪除裝置檔案
- L1 Occurrence：同 URI 可以存在多份播出歷史、不同清單位置；音樂檔案不複製
- L2 Spatial：真實 relativePath 聚合音訊／有音軌影片；清單順序不等於隨機播放順序
- L3 Relation：playlist item 與 alarm playlistId 為獨立關係；對具名清單增減不暗中修改 Smart All
- L4 Event：歷史是已發生的播放事件；收藏是新增關係，不改過去事件
- L5 Decision：Q0 的使用者已滿足、P1–P3 T1、CI T2 與實機 T3 分別記錄

**業的症狀：** 大量 index / DAO / 介面存在卻缺「資料夾→音樂池」「歷史→清單」的短路；工程擴張又使原本滿足的 Q0 被誤述成無窮未完成。

## 1. 逐片對照

| 切片 | 本分支實際更動 | 可觀察的驗收 |
|---|---|---|
| P1 主畫面入口 | `AlarmListScreen` 新增獨立音樂中心，不必創建／編輯鬧鐘；`MusicLibraryDialog` 預設 FOLDERS | 由首頁一個入口進入資料夾列，不先展開幾千首歌曲 |
| P1 裝置媒體路徑 | `MusicDeviceFolderGroups` 以 MediaStore `relativePath` 分組音訊與有音軌影片，顯示該組數量／已加入；`DeviceFolder` exact-match 篩選 | Music/ 與 Music/AB 不混選；無 RELATIVE_PATH（舊系統）落在「裝置媒體」未知組 |
| P1 來源管理 | `MusicFolderPoolRules` 以 device path／SAF sourceId 保存 INCLUDE／EXCLUDE，對明選整個資料夾的既有候選整批操作；下次掃描只把新候選納入 INCLUDE，保留後續個別排除；EXCLUDE 重掃時仍有效 | 重啟仍有狀態，重新掃描不默默推翻使用者資料夾決定，刪 SAF 來源清理其舊規則 |
| P1 權限 | 資料夾初始頁可直接要求 READ_MEDIA_AUDIO／舊版 READ_EXTERNAL_STORAGE；READ_MEDIA_VIDEO 不冒充 READ_MEDIA_AUDIO | Android 13+ 只授權影片時仍顯示音訊授權入口 |
| P2 歷史收藏 | `HistoryTrackRow` 多了加入清單按鈕，選現有 Favorites／具名清單；`MusicLibraryRepository.ensureFavorites()` 在缺席時一次建立預設清單；`addHistoryTrackOnceTransaction` 由 Room 事務排除同一清單同 URI 重複收藏 | 播放歷史不變，同一 URI 重複點選不堆積多份收藏，來源不可用／EXCLUDED 不暗中恢復 |
| P3 全域耦合切斷 | `addTracksToPlaylistTransaction` 對全域 membership=null 不再暗中設 INCLUDE；具名清單數量、UI 可用性及 AlarmService 快照以「存在、可播放、未 EXCLUDED」判斷；Smart All 只認明確 INCLUDE | 單獨增減清單 A 不改 Smart All 候選；已指定清單的鬧鐘下次響鈴讀取更新後清單；明確 EXCLUDED 保護仍在 |

## 2. P1/2/3 反例與界線

1. 舊版 Android 無 MediaStore RELATIVE_PATH：只能顯示「裝置媒體」未分類區；不偽造檔案路徑，SAF 授權來源仍可按資料夾管理。
2. Device audio/videos 出現相同相對路徑：目前按路徑聚合一組；若同一相對路徑存在多個儲存卷，來源卷識別與完全分隔是日後加強項，不能宣稱已百分之百辨識物理資料夾。
3. 手機撤銷來源權限：只有已索引、當下可見的內容可加入；沒有宣稱規避 Android SAF 限制或複製原始檔案。
4. 裝置自帶鈴聲在 MediaStore 中：資料夾 UI 有提示，並保留既有一鍵可審核的系統聲音排除；因假陽性風險，不私自刪除媒體。
5. 從歷史加入 Playlist X：不會新增一筆播放歷史，也不自動加入 Smart All；如果音訊已不存在或被 EXCLUDED，不冒充成功新增。
6. 用戶明確在清單 UI 選「恢復已排除的曲目到共用音樂池」：仍是一個**明示跨域操作**，不同於一般加入清單。需要保留既有確認文案。
7. 使用者選整個資料夾「加入音樂池」：這是明確的全域變更，可以覆蓋本資料夾舊的排除；後續重新掃描則僅補新曲目，不會默默恢復手動排除。
8. 清單若在鬧鐘正在響鈴時更新，現有 Service 先取這次響鈴快照；不保證立即替換正在播放的選曲，但**下次響鈴**讀取新內容。
9. 歷史事件 URI 不在當前媒體索引：畫面仍保留歷史；使用者不應誤以為可以無來源播放／收藏。
10. `P` 驗收：真機依序測「首頁→資料夾→整批加入／排除→重掃／重開」及「首頁→歷史→加入收藏→播放清單→鬧鐘選定」，通過且不干擾 5 秒快速設鬧鐘後才可宣布 P1–P3 使用者完成。

## 3. PRTP 事件視圖（候選，未向 HANDS 正本寫入）

- `Q0`：原始音樂鬧鐘/播放紀錄；`question_action=retain_closed_by_user`
- `E1`：遠端 Room v28 已有清單與來源，但首頁入口、真實資料夾分組、歷史收藏仍不通順
- `E2`：DAO 實際存在「加入具名清單時順帶 INCLUDE 到 Smart All」耦合
- `Q1`：獨立音樂管理便利性；`question_action=split_and_reframe`
- `test`：更改具名清單，不應改變未指派此清單鬧鐘的候選；來源排除重掃仍排除；歷史引用不改歷史事件
- `authority`：本輪使用者明示同意 P1、P2、P3；未授權擴展到其他 repo、主線合併或 PRTP Skill 的全域採納

## 4. CI 和完成宣告

- Node governed gate 覆蓋 B0-T1 / G4–G6；用實際 Run SHA 記錄。G4e 對歷史 UI 的舊 line pin 改為**同來源檔、原記錄位置 ±128 行內唯一 exact snippet**，錯行／缺檔／模糊 source 依舊擋下。
- Android full F-Droid unit / release-safe build / 第二 runner v162 簽章；更新 DAO 反映「named != Smart All」，對 4,935 曲目與同 URI 收藏使用 Room 測試。
- 當前分支與 GitHub PR 狀態以最新提交為準。若最終 Android Run 未成功，保持 `T2_PENDING` 並記錄失敗，不能稱已完成；真機 `T3` 與 UX `P` 另行核對。
- 這輪僅改 P1–P3，不啟動 B2 自訂語言包正常 UI、HANDS 或 Writing-tool 的 runtime 工程。

### P1 裝置全音樂快捷操作補充

資料夾頁在個別路徑列表之上提供「裝置媒體／加入音樂池」快捷操作：只針對 MediaStore **音訊**，排除已辨識的系統聲音；影片音軌不跟著加入，仍由其資料夾個別管理。全音樂快捷加入只納入 pool member 為 null 的候選，**不恢復**已明示排除曲目；即使未來掃描新增檔案，先執行 broad include，再套用指定資料夾 EXCLUDE。若使用者明確點選單一資料夾加入，才會重選該資料夾內所有已索引曲目。

路徑識別保留 Android 提供的 **大小寫**：`Music/A` 和 `music/a` 不會因 app 的大小寫折疊而錯誤合併或跨資料夾排除；只正規化斜線與尾斜線。目前跨儲存卷同一相對路徑識別仍需額外改善，不能視為已完成完全一致的實體目錄標識。
