> **2026-11-14 UX-R2 最新補充**：原本文檔和 11/13 D1 都不是最新介面規格；請優先閱讀 [XPlayer × Claude Design 媒體庫／命名播放清單規劃](./preview3-xplayer-claude-ux-and-code-contract-20261114.md)，並搭配 [D1 音軌與響鈴技術契約](./preview3-media-library-playback-contract-20261113.md)。主入口已由五個平行分頁改為「媒體庫、播放清單」兩主要工作區；播放清單可共用或複製到單一鬧鐘。

> **2026-11-13 最新需求覆蓋通知：本文為早期 P3 草案，已不再是施工／放行依據。** 使用者已確定：Audio+Video+FLV、每鬧鐘獨立清單與歌手／資料夾篩選、三種連播策略（可重複隨機／洗牌不重複／自訂順序）、一直播放至手動解除、貪睡到期重新開始，以及下一個交付 APK **必須一次具備全部核心功能**。新正式規格請閱讀 [Preview 3 全量實作契約](./preview3-media-library-playback-contract-20261113.md)。本文原本「P3-1 可以先交手機 APK」的建議已被最新需求取代，僅供歷史比較。

# AlarmClockXtreme Preview 3 — Zaljatni 音樂發現模型與媒體優先介面

日期：2026-11-13。TCL 設計文件，非功能已實作的證明。
參考程式基準：fix/preview2-pool-refresh-seams-20261113@d74c412fb9f02a047680dd8e8ab727815122fc3a（Preview 2.1 v143/v144；Room v27）。本文件分支只增文件。

## 一、競品證據與推論邊界

Zaljatni 官方 Google Play <https://play.google.com/store/apps/details?id=kr.zzzi>：隨機音樂鬧鐘；醒來後留存播放歷史，可重新聆聽及分享；旋轉式設定與停止；2.3.0 官方韓文更新明確新增「在設定中搜尋及選擇歌手，從選定歌手的音樂隨機響鈴」。

可直接確認：歌手搜尋／選擇、隨機選曲、播放歷史、鬧鐘中心式操作。
合理但未證實的系統設計：以 Android 系統媒體索引或等價音樂索引取得歌曲標題、歌手和 URI，再依歌手條件抽選。
無法宣稱：它使用 MediaStore 哪一個確切 API、它是否遞迴選資料夾、是否支援影片音軌、是否同一次鬧鐘連播多首。不得模仿未證實的內部行為。

最值得轉用的操作原則：**先顯示可用音樂，再讓使用者決定播放範圍**。不是讓使用者先找到文件資料夾並完成來源授權。

## 二、Preview 2.1 已確認的 UI 根因

- MusicLibraryIndexer.scanMediaStore 只有 MediaStore.Audio.Media 查詢；沒有 MediaStore.Video.Media。
- MusicLibrarySourcesDialog 使用 OpenDocumentTree；這是 Android 系統 DocumentsUI，App 無法自行隱藏文件或變更其逐層瀏覽規則。
- MusicLibraryDialog 是掃描／來源／音樂池按鈕及歷史的 AlertDialog，MusicPoolDialog 也是另一個 AlertDialog。
- MusicPoolSourceScope 的預設值 Folders，裝置 MediaStore Audio 需另切換 DeviceMedia，容易造成明明已索引卻看不到候選。
- 即使已支援 Search／Select visible／Include／Exclude，互動被放在很小的對話框內；搜尋按鈕不會直接導向可見曲目。

## 三、Preview 3 產品契約

一個全頁「音樂與影音庫」作業畫面，從鬧鐘設定或音樂庫入口均可直達；畫面最初就顯示已取得存取權的手機音樂候選。四個使用者分頁：
1. **所有影音**：搜尋結果可見、可勾選、可批量加入／排除／恢復；沒有 SAF 授權也能列出裝置已有的 MediaStore Audio。
2. **資料夾**：以 MediaStore 的 RELATIVE_PATH + storage volume 建立虛擬目錄，點父層可選擇含子目錄的可用媒體，不必開 DocumentsUI；非系統索引的媒體才使用「額外授權資料夾」。
3. **歌手／專輯**：歌曲以 artist、album 分組；空 metadata 歸未知，不丟棄。
4. **音樂池**：包含已加入／未加入／已排除；顯示有效／無效數量；播放器只抽 INCLUDED。

不要以自動匯入全部音訊取代使用者決定。第一次可用清晰的一鍵操作「將已找到的音樂加入池」，明示數量，推薦集合預設排除系統鈴聲／通知音／鬧鐘音；使用者仍可手動選取它們。

操作順序：打開音樂庫 → 權限說明或直接顯示可用曲目 → 搜尋／分組／勾選 → 加入池 → 返回鬧鐘並啟用隨機連播；整段不依賴 DocumentsUI。

## 四、掃描與存取架構

資料管線：MediaStore.Audio + MediaStore.Video + 持續保留的 SAF 授權來源 → Discovery（可見性與音軌驗證） → Catalog（候選和 metadata） → Membership（INCLUDED／EXCLUDED／未加入） → Media3 純音訊播放 → Playback History。

- MediaStore Audio：DISPLAY_NAME、TITLE、ARTIST、ALBUM、RELATIVE_PATH、VOLUME_NAME、MIME_TYPE、IS_MUSIC、IS_RINGTONE、IS_NOTIFICATION、IS_ALARM 等，按 API 支援情況安全查詢。IS_MUSIC 等是分類提示，不是禁止使用者手選的硬限制。
- MediaStore Video：Video Media 查詢候選 URI，背景分批檢查音軌可解碼性，結果分可播／不可播／無權限／待探測；只有可播的音軌才可入池。
- SAF：仍保留讓使用者補充未被 MediaStore 收錄或需另外授權的來源；不能使用 MANAGE_EXTERNAL_STORAGE 規避 scoped storage，不能承諾無條件掃描整個手機。
- Android 13+：Audio 權限 READ_MEDIA_AUDIO，Video 權限 READ_MEDIA_VIDEO。Android 14+ 的影片部分存取以 READ_MEDIA_VISUAL_USER_SELECTED 和官方權限流程處理；不為音樂功能要求圖片權限。只有音訊授權時仍正常列出音訊，未授權影片顯示原因和重新授權入口。
- RELATIVE_PATH 僅作顯示與分組用途，不拿來組裝檔案路徑；播放使用 Content URI。
- 用來源卷 + provider URI + 已授權 SAF document IDs 保留來源，不能因同名歌曲就判成相同實體文件；跨 Provider 去重僅在識別充分時使用。
- 媒體被移除、SD 卡失效、權限撤銷時只標 unavailable，保存既有 Membership 與 History，恢復後重新核對，不自動取消 EXCLUDED。

## 五、資料庫方案

先做 P3-1：Room v27 不動，直接把既有 MediaStore Audio 候選呈現在全頁 UI，修正進入音樂庫後沒有互動的問題。增加 1 鍵「把目前範圍已找到的音樂加入」；限定推薦集合，避免鈴聲自動入池。

當實作持久化 artist/album/virtual folder/source volume/mediaKind 索引時，新增媒體 metadata 紀錄或欄位，非破壞性 Room v27→v28，正式 KSP 28.json 與 24→28 migration 測試。保留 music_pool_members 目前 URI 身分、playback_history 快照及所有 SAF 來源，不做 destructive migration。

列表至少 3,001 項：LazyColumn/Paging，搜尋不應導致每次 Compose recompose 都掃全手機；背景掃描可取消、顯示進度、不要在 UI 主執行緒逐個 probe 影片。

## 六、分批執行與驗收

- **P3-1 最小可用版**：首先修 UI。打開音樂庫直接展示當前 MediaStore Audio 歌曲，可搜尋、全選範圍、批量加入／排除、查看歷史；不進 SAF、不要求手動點 DeviceMedia Chip。可先交一版 APK，驗證操作是否真正改善。
- **P3-2 影片索引**：新增 Android Video 權限與 MediaStore.Video；有聲 MP4/MKV/WebM 可被發現並加入同一音樂池；Media3 仍只出聲，無畫面。
- **P3-3 分類選擇**：虛擬資料夾（父資料夾含子目錄）、歌手、專輯和過濾組合；選取範圍明示、不可誤選其它來源。必要時導入 v28 metadata schema。
- **P3-4 穩定與 i18n**：Android 16 真機 3,001+ 曲目、部分授權、SD 卡失效、歷史不變、三語與可用性測試；版本至少 145/146，沿用固定簽章。
- 明確不做：強制全儲存權限、再增巢狀 Dialog、為了仿 Zaljatni 刪除影音混播和連播歷史。

## 七、放行測試

1. 手機有 1,200 首已索引音訊、沒有任何 SAF 來源：打開音樂庫即看到可搜尋清單；可一鍵選取並加入。
2. MediaStore 音樂與系統鈴聲同時存在：推薦批量加入不選鈴聲；手動仍可加入特定音效；備援獨立。
3. 影片專案：已索引 MP4/MKV 可見，無音軌影片不可加入；需視頻權限，部分授權時顯示狀態。
4. 虛擬 Music/Japanese/Artist/Album 分組可選父層含子資料夾，不跳 Android 文件管理器。
5. 3,001 首混合媒體排除 12 首：重掃／重開／更新後排除仍有效，不因來源臨時失效清空。
6. 歌手搜尋可選擇多個歌手（若設計允許），抽選只從可播放且 INCLUDED 中的適用結果；未知歌手保留手動選擇。
7. MP4 聲音可播放且不顯示影片，連播、解鈴、貪睡、故障備援維持；真機核對。
8. Android 16 + 繁體中文／日本語／English 在小螢幕可完成從搜尋到鬧鐘全流程；143/144→145/146 簽章一致且資料保留。

## 八、工作線

- Copilot：P3-1 的全頁 UI 與操作驗收、三語文字、純 UI 邏輯測試（獨立 feature 分支）。
- Android 整合 Agent：P3-2 Video/權限/音軌 probe、P3-3 metadata／Room v28、CI 與持續簽章（獨立整合分支）。
- GPT/TCL：核對實際查詢、DB 與操作可達性，證據分級；先 P3-1 真機體驗再開 P3-2。
- 使用者：只需驗證一件核心體驗：「不打開系統文件選擇器，我能看到、搜尋並批量加入手機已有的歌曲嗎？」

外部 API：
- <https://developer.android.com/reference/android/provider/MediaStore>
- <https://developer.android.com/reference/android/provider/MediaStore.Audio.Media>
- <https://developer.android.com/about/versions/14/changes/partial-photo-video-access>
- <https://developer.android.com/training/data-storage/shared/media>
