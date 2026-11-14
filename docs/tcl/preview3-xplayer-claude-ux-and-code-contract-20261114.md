# Preview 3 UX-R2｜XPlayer 媒體庫＋播放清單、Claude Design 設計方法與實作契約

日期：2026-11-14
狀態：使用者已同意 XPlayer「自動搜尋／資料夾／歌手」和「建立／編輯播放清單」兩組功能；此文件為新的 UX 及相關程式契約，未實作、未合併、未建置 APK。
優先序：本 UX-R2 的使用者可見流程和新版資料契約，高於 2026-11-13 Preview 3 D1 文件的「五個平行分頁」與「每鬧鐘專屬清單是唯一清單」設計；D1 所有已確立的影音格式、SAF、共用音樂池、三種連播策略、直到解除、貪睡、歷史、備援、簽章等技術與安全要求不變。
專案：https://github.com/Penguink72/AlarmClockXtreme
程式共同基準：fix/preview2-pool-refresh-seams-20261113@d74c412fb9f02a047680dd8e8ab727815122fc3a。
Copilot UI 草案：feature/p3-media-browser-ui@8ce9fa93ab828b9973a85096a8bbc785318d8273，Draft PR #6。
並行變更：feature/preview2-2-auto-music-library-20261114@00232c1c15a5e972bfcd332c344e2919972569f3，Draft PR #5（需特別處理自動入池語義衝突和 145/146 版本碼）。
此文件所在分支：docs/preview3-zaljatni-media-browser-plan-20261113（規劃 PR #4）。

## 0. 設計前提與證據邊界

- XPlayer（InShot，Google Play 套件 video.player.videoplayer）官方列出自動辨識手機／SD 卡影片、資料夾式管理、MP4/MKV/FLV 等格式、背景音訊播放；播放清單的編排與分類介面可作操作樣板。不同版本可能改版；不可宣稱逐像素複製，亦不可把 XPlayer 的專有解碼能力當成本 App 已具備。
- Claude Design 官方案例著重「先明確任務 → 線框草稿 → 建立可沿用的設計系統 → 用現有產品脈絡產生可點擊的流程 → 反覆評估／修改 → 再交工程實作」；本案不是把 Claude 網頁色彩、字體、動畫直接移植到 Android。
- 本案保留使用者在 Preview 3 決定的需求：MediaStore Audio + Video 自動發現、額外 SAF 一次授權持久化、可支援的 MP4/MKV/FLV 音軌、候選須明確加入、每鬧鐘可選歌手／資料夾／獨立清單、三種策略、同次無限接續至合法解除、貪睡重起、三語、歷史。
- XPlayer 提供「影音檔案與清單操作」，Zaljatni 提供「簡單選曲的隨機鬧鐘」，AlarmClockXtreme 保留自己的響鈴 Session／備援／清單與跨鬧鐘規則。

官方參考：
XPlayer https://play.google.com/store/apps/details?id=video.player.videoplayer
XPlayer help https://inshotapp.com/website/XPlayer/help.html
Claude Design https://claude.com/resources/articles/how-the-product-designer-who-built-claude-design-uses-it-to-explore-ideas-before-building-them
Claude Design Guide https://support.claude.com/en/articles/14604416-get-started-with-claude-design

## 1. Copilot PR #6 的逐項讀碼裁定

### 已存在、應保留

- MusicLibraryDialog.kt：全尺寸 Dialog + Scaffold、ScrollableTabRow、歌曲搜尋、候選／INCLUDED／EXCLUDED 過濾、批量操作、LazyColumn；基本上讀取真實 Room v27 的 poolTracks。並非假列表。
- MusicLibraryViewModel.kt：新增 catalogSearch、catalogFilter、selectedCatalogUris、catalogSourceScope；filterLibraryCatalog 可搜尋標題／檔名／來源；多選只作用在可見來源。原使用者已排除的曲目不因一般「加入」恢復。
- MusicLibrarySupplementalCatalog.kt：提供影片、歌手、試聽的介面契約；正確標註尚無 implementation。
- AlarmMusicSettingsContract.kt / AlarmMusicSettingsScreen.kt：三策略名稱、歌手／來源條件與清單編輯元件雛型；已建立測試檔。
- 三語字串、部分 UI test／純邏輯測試存在，尚未 Gradle 編譯與手機驗收。

### 真正的問題／不可宣稱完成

- 使用者看到的是 5 個平行頂部分頁，和一排狀態 Chip、四個 OutlinedButton 及來源操作；歌曲／音樂池重複操作，仍很像「系統管理面板」，不是 XPlayer 式兩步入清單。
- MusicLibraryDialog 本質仍是覆蓋畫面的 Dialog（usePlatformDefaultWidth=false），不是獨立導航目的地；內部仍可開 MusicLibrarySourcesDialog，屬巢狀視窗。
- SONGS 使用 filterLibraryCatalog(videoOnly=false)，實際含音訊與 SAF 影片，分類標籤語義可能不符；VIDEOS 則只挑現有 SAF_VIDEO。影片與歌手 SupplementalCatalog 未有實際提供者，ArtistCatalogUnavailable 是占位頁；資料夾頁主要是 SAF source 而非 MediaStore RELATIVE_PATH 虛擬路徑。
- MusicCatalogPreviewController 沒有實作，且 MusicLibraryDialog 的 previewController 預設 null；「試聽」不可用是正確的限制，不可宣稱功能完整。
- AlarmMusicSourceMode.SHARED_POOL/FILTERED_POOL/INDEPENDENT_PLAYLIST 是互斥分支，無法完整表達「選命名清單＋選歌手和資料夾附加限制」。應把「清單來源」與「進階篩選條件」分離。
- AlarmMusicSettingsDraft.playlist 是按 URI 的列表，AlarmMusicSettingsScreen 的 itemsIndexed 以 uri 作 key，RemovePlaylistTrack／MovePlaylistTrack 也按 uri 識別：同一 URI 出現兩次時無法可靠指認哪個清單項，須改 stable playlistItemId。
- C3-3 設定只是介面與 Repository 契約，尚未接 AlarmEditViewModel.save、Room v28、AlarmService。無實機 APK，沒有通過完整 Android 編譯／裝置測試的證據。

## 2. 新的資訊架構（兩個主要工作空間，不再五個同級分頁）

使用者從既有「鬧鐘」主功能進入「音樂與影音」；不增建一個與整個鬧鐘 App 主導覽衝突的全域底導覽。

音樂與影音的**兩個主頁**：
1. **媒體庫**（Media Library）：手機已索引的影音檔案。頂部搜尋；子切換「全部｜音樂｜影片」，同頁的「依資料夾」與「依歌手／專輯」是瀏覽方式／篩選入口，沒有五個平行頁面。進入預設直接顯示已找到的可見曲目。
2. **播放清單**（Playlists）：命名清單列表、我的最愛、全部可用音樂（智慧清單／參照），新建、重新命名、複製、刪除與逐項排序。播放清單是主要可見功能，不再把 music_pool_members 的 Included/Excluded 當作一個主要分頁。

點進任何命名清單→清單編輯頁（breadcrumb／返回）；進入鬧鐘音樂設定→選清單＋選播放模式。來源與權限問題可由媒體庫頂端的「重新整理／管理掃描範圍」提供，不在每頁暴露後台名詞。

**底層仍保留 MusicPoolEntry（INCLUDED／EXCLUDED／未加入）**，但 UI 用「可供鬧鐘播放」「已排除」「尚未加入」表述；僅必要時在單曲選單或「媒體可用性設定」中編輯，不增設同級主分頁。

## 3. Claude Design 方法落到具體 UI 規則

- 設計前先用三個任務檢驗：A. 開 App 後找到手機歌曲並建立清單；B. 從影片資料夾把 FLV 音軌加入清單；C. 某鬧鐘選該清單與策略並儲存。先畫低保真 wireframe、再用真實 Compose 元件做可點擊 prototype；每輪以實際手機測試決定保留哪一種流程。
- 行動命名用使用者詞彙：搜尋手機影音、播放清單、加入清單、移除項目、允許鬧鐘播放、排除；禁止在主介面出現 MediaStore/SAF/Room/INCLUDED/EXCLUDED 等技術名詞。
- 漸進揭露：媒體庫預設只露「搜尋、媒體列表、右側更多操作」；進入多選模式才出現底部「已選 N 項、加入清單、排除」操作列；只在需要時展開「進階篩選／掃描範圍」。
- 每個頁面一個主要動作：媒體庫是「選曲」、清單頁是「建立／編輯」、鬧鐘音樂頁是「選清單與播放策略」；避免長列水平捲動的按鈕群。
- 色彩只供狀態區分、與目前 Material3 和既有 App 主題一致；不模仿 XPlayer 綠色或 Claude 網頁字體。使用 8dp spacing scale、至少 48dp 可觸及區域、長標題最多兩行、對比和 TalkBack；系統字體放大下主要 CTA 不被遮擋。
- 空狀態必須說明原因和下一步：未授權音訊／影片、允許部分內容、尚無索引、搜尋無結果、清單是空的、音軌未通過、授權已撤銷、SD 卡斷線。不得只顯示空白或「未知錯誤」。
- 不在介面每處放警告紅字；針對系統鈴聲、可能無法播放的 FLV、重複曲目、加入已排除歌曲，**只在觸發相關動作時**給明確提示。
- 先做可用的 2 套低保真線框（簡潔列表／來源資料夾優先），使用真實 repository 狀態操作，採能完成三個任務且點按次數較少的一版；視覺最後再 polish。可利用 Claude Design 作互動評估，但不能把 prototype 當成 Android 程式。

## 4. 使用者完整互動合約（Interaction Contract）

### 4.1 自動搜尋與資料夾

首次開啟：顯示「從手機搜尋音樂與影片」，權限分 Audio / Video；拒絕時仍顯示已授權 SAF 資料。授權後直接顯示候選（未加入不影響可見性），重開時直接顯示索引快取並可背景刷新。不得每次重開就強制全量掃描導致卡住。

主媒體庫頂部搜尋／重新整理；子分類「全部／音樂／影片」，同頁瀏覽工具可切「資料夾」或「歌手／專輯」。MediaStore 使用 volume + RELATIVE_PATH 構建虛擬資料夾，不要求 DocumentsUI。列表顯示檔名、長度、音軌／格式、曲名／歌手（若存在）；同一檔案的音樂與影片分類明確。普通點擊開啟單曲操作或明確的試聽控制，**長按進入多選**；清單右側選單有「加入播放清單」「試聽」「允許／排除」。

「管理掃描範圍」提供可見來源清單、隱藏無關資料夾、一次 SAF 增加額外目錄與重新掃描；隱藏來源與 EXCLUDED 不同，不能偷偷排除已存入清單的歌曲。不承諾掃到所有 Android scoped storage 不允許的檔案。FLV 等若未被 MediaStore 收錄，可從合法的 SAF 來源補入；Media3／codec probe 負責可播判斷而非僅靠副檔名。

### 4.2 把檔案加入播放清單

使用者在媒體庫長按一筆→勾選多筆（可按目前資料夾含子目錄、歌手範圍全選）→固定底部操作列「加入播放清單」→選既有清單或「新建清單」→完成後顯示具體加入數量，原頁可繼續選。

**加入播放清單是使用者明確選擇「可供鬧鐘播放」**。若尚是候選，單次交易先設 INCLUDED 再存清單項，避免要求做兩個按鈕；若是 EXCLUDED，彈出清楚確認「這首已排除，是否解除排除並加入？」。按取消不改動 EXCLUDED；同批部分排除要顯示已加入／已跳過數量，不可沉默地解除排除。只有挑到的 URI 被改，其他同資料夾檔案不變。

系統鈴聲、通知音、短片段：不進「一鍵選所有建議音樂」的預設集合，可在使用者手動選中時加入；不沿用並行 PR #5 的 AUTO_INCLUDED 掃描行為。權限不可用或音軌不支援的檔案可在清單顯示為不可用，但不能冒充已可播，並給修復路徑。

### 4.3 清單首頁及編輯

首頁展示「全部可用音樂」（智慧範圍，不等於實體清單）、我的最愛（可編輯）、使用者命名清單、可選每鬧鐘專屬清單。每張清單顯示名稱、可用／不可用筆數、最近修改，主操作為開啟／新建。

進入清單：逐項列表＋順序號，支援加入、長按多選移除、試聽、修改名稱、複製、移到頂端／底端／指定位置、拖曳重排、批量排序、復原（Undo），不要求用手指拖 3000 行。清單是一個本地持久物件；**不得刪除或移動手機原始媒體檔案**。刪清單時若多個鬧鐘共用此清單需提示影響、阻止不明確的破壞性刪除或要求替代清單選擇。

同一 URI 可在 ORDERED 清單出現兩次且依兩個不同項次播放；每項需獨立 stable itemId。移除其中一個項目不影響另一個，也不自動移除 music_pool_members 的 INCLUDED（除非使用者另外明確全域排除）。

命名清單可以多鬧鐘共用；鬧鐘可以一鍵複製成專屬版本，後續修改互不影響。清單更名不改 playlistId；清單刪除不得讓引用者在沒有提示下變成空音樂。

### 4.4 每鬧鐘音樂設定：兩個主要控制＋進階

正常狀態只顯示：
- **播放清單**：全部可用音樂、某個命名清單、我的最愛、該鬧鐘專屬清單（可跳到清單頁編輯）
- **播放方式**：① 完全隨機、可重複（預設）；② 洗牌、播過不重複；③ 依清單順序循環（當使用全部可用智慧範圍且無排序時禁用並提示先選命名清單）。

固定說明：「持續播放直到解除；貪睡時暫停，到期重新連播」。歌手／來源資料夾條件放「進階篩選」折疊區，歌手或來源可多選、同維度 OR／跨維度 AND；額外篩選只縮小所選播放清單與全域可播放資格的交集。最終顯示「可用 N 首」，N=0 具體指出「空清單／無權限／都被排除／無音軌／篩選無符合」。

### 4.5 播放、試聽、響鈴三者清楚分離

試聽使用專用 Media3 音訊會話，不寫鬧鐘實際播放歷史，離開頁面停止；鬧鐘響鈴時禁止試聽搶 Audio Focus。點播放清單內的試聽不等於啟動鬧鐘。

鬧鐘 Session 從 playlistId + filter 快照選取 GLOBAL INCLUDED 且可播放項；每首結束依選定策略抽下一首，不因單曲結束關閉；連續 5 次無法起播進安全備援聲，直到使用者合法解除。貪睡停止當前 Session，到期建立新的 Session；已發出的 onEnded/onError callback 不得復活已取消的播放。

## 5. 具體資料契約（修正 Copilot 現有互斥模式）

目標 Room v28 邏輯資料表：
- media_catalog_metadata：URI PK、provider/source links、mediaType、volume、relativePath、artist、album、duration、container、mime、audioTrackProbeStatus、decoderStatus、permissionAvailability、lastSeen。
- music_playlists：playlistId PK、name、kind（SMART_ALL / FAVORITES / NAMED / ALARM_COPY）、ownerAlarmId nullable、createdAt、updatedAt。SMART_ALL 為查詢虛擬清單，不需要為全庫寫入 3000 個 playlist items。
- music_playlist_items：playlistItemId PK、playlistId FK、trackUri、position、addedAt；允許相同 trackUri 多次，但 playlistItemId 不同；保存顯示排序／可用狀態。
- alarm_music_settings：alarmId FK、playlistId nullable（null 表示 SMART_ALL）、strategy、continuousUntilDismiss；保留 legacy randomMusicEnabled 讀法並將新設定作明確優先權。
- alarm_music_facet_filters：alarmId、kind（ARTIST/FOLDER）、facetId，避免把清單與篩選混為互斥 enum；選擇現有清單仍能再選歌手與資料夾。
- music_pool_members：既有 INCLUDED / EXCLUDED / 未指定，不刪既有紀錄；明確的 UI「加入清單」可在同個 transaction 設 INCLUDED，但掃描不自動升為 INCLUDED。
- playback_history、SAF source store、alarms：保留既有內容及 URI 身分；非破壞性 Room 27→28，正式 KSP 28.json。

建議替代 UI API：
- MediaCatalogRepository.observeBrowsableMedia(query, typeFilter, groupBy, pathScope, artistIds, sort, paging)；refresh(scope)；sourceStatus；probeStatus。
- PlaylistRepository.observePlaylists()、create(name)、rename(id)、duplicate(id, ownerAlarmId?)、addTracks(playlistId, selectedUris, excludedResolution)、removeItem(playlistItemId)、reorder(playlistId, itemIds)、deleteWithReferencesCheck(id)；增刪與 pool membership 在必要時同交易。
- AlarmMusicSettingsRepository.observe(alarmId)、save(alarmId, playlistId, strategy, artistIds, folderIds)；與 AlarmEditViewModel.save／AlarmRepository 原子保存／備份／分享序列化一致。
- PreviewAudioController.play/pause/stop，guardActiveAlarm=true，禁止寫歷史。
- PlaybackSelector.selectNext(session, globalEligible, playlistSnapshot, filters, strategy)，純 Kotlin 可注入 RNG，三策略與 Session 與 Preview 3 D1 相同。

舊 Copilot AlarmMusicSourceMode 的 SHARED_POOL/FILTERED_POOL/INDEPENDENT_PLAYLIST 只留相容／過渡適配器，正式狀態改為**playlistId（或 SMART_ALL）+ filters + strategy 三個正交維度**。舊 Add/Remove/MovePlaylistTrack 以 URI 操作的 command 全部改以 itemId；Add 允許相同 URI 多次。

## 6. PR #5 與 PR #6 衝突及整合策略

已查遠端：
- Draft PR #6，feature/p3-media-browser-ui@8ce9fa93，從 d74c412 向前 4 commit；有 13 個改動檔案，Android Gradle／Compose UI tests 未跑，沒有 APK。採其全畫面 Scaffold、搜尋、多選、ViewModel 與可重用 UI 元件，不採五平行主分頁與互斥來源模式作最終契約。
- Draft PR #5，feature/preview2-2-auto-music-library-20261114@00232c1c，從同一 d74c412 出發，改 MediaStore duration/RELATIVE_PATH/system ringtone flags、掃描／Room/自動池；提出 AUTO_INCLUDED/AUTO_SKIPPED 且掃描自動入池，與作者 D3「先列候選、明確加入」衝突。其 CI 改成 versionCode 145/146，而先前 P3 規劃也預留 145/146。
- 重疊檔案至少有 MusicLibraryDialog.kt、MusicLibraryViewModel.kt、app/build.gradle.kts、三語 strings.xml、MusicPoolFilterLogicTest.kt。**禁止把 PR #5 和 #6 直接合併、或簡單以一方檔案覆蓋另一方**，應逐項採擇／重接程式，並建立整合測試。

整合順序：
1. 以 d74c412 為共同祖先建立新 integration/p3-xplayer-media-playlists 分支；絕不直接推到 main/Preview1/Preview2.1。
2. 優先納入 PR #5 **只讀媒體 metadata 掃描與 duration/relativePath/system tone 判定的可用部分**，但不得採掃描 AUTO_INCLUDED 或修改 random picker 的自動授權語義；必要時自行重寫分離 Discovery / Membership。
3. 從 PR #6 移植已接線的 Compose Scaffold、搜索及批量選取，改成兩主頁＋內部分類；先跑編譯並修正內部 Widget key 與導覽，再改共享清單相關契約。
4. 由 Android Agent 實作 MediaStore Video、FLV Audio probe、Room v28、PlaylistRepository、選曲 Session 和備份；由 UI Agent 接上新的 Repo 與試聽。避免兩個 Agent 同時編輯 AlarmDatabase/AlarmService/MusicLibraryViewModel/strings.xml；提交粒度以 PR／單檔差異可審核。
5. v145/v146 已被 PR #5 預留，若該 Draft 仍存在或可能出貨，建議新完整 P3 **保守預留 147/148**，由整合者最後確定是否有更高已發布版；原簽章、com.sysadmindoc.alarmclock.debug 不變。
6. Preview 3 依作者既有決定：內部分批做，但**下個對使用者正式交付的完整 Preview 3 APK** 必須涵蓋 Audio+Video+FLV 搜尋、兩主頁及命名清單、試聽、每鬧鐘清單和三策略、持續連播直到解除、貪睡／備援；不能用演示佔位頁交差。

## 7. 施工批次與交付／停止條件

R2-0：先以真實 Copilot 分支和 Preview2.2 並行 PR 做 Read-only 审核，記錄所有重疊檔和資料契約衝突；產出 2 款 wireframe（列表優先、資料夾優先），用 3 項核心使用任務做點按流程比較；不要立即批次修改大畫面。
R2-1：媒體庫兩主頁與入口：消除五平行主分頁、狹小按鈕橫捲、重複「池」主頁；以現有 Room 資料接入可操作曲目，實作狀態／空狀態／多選。驗收：現有 Audio 不需 SAF 就能被看到／入清單。
R2-2：MediaStore Audio+Video＋SAF FLV，權限、虛擬 folder/artist/album metadata、可解碼／不可解碼狀態、漸進搜尋與增量掃描。驗收：手機可見影片音軌與 SAF FLV 且真實可試聽（只有聲音），無權限／壞檔不誤報空資料。
R2-3：Room v28、可多鬧鐘共用的命名清單、我的最愛、複製成專屬、重排/Undo、playlist item stable ID，導入一鍵「加入清單即手動授權使用」交易。驗收：雙鬧鐘共用清單、指定清單獨立修改、相同 URI 多次加入及排序／移除都可重啟保存。
R2-4：每鬧鐘「清單＋模式」簡化頁與展開歌手／資料夾篩選；已接 Repository、AlarmEdit 儲存及備份；命名清單排序模式可選、不受舊互斥 enum 限制。驗收：三種模式／清單可持久保存，清單不存在或全部不可用有明確提示。
R2-5：Media3 試聽及 AlarmMusicSession 持續響鈴、三策略、貪睡、解除、超過 autoSilence 的行為、五次失敗保底聲。驗收：舊播放器 callback 不復活、單曲結束接播、試聽不搶鬧鐘與不寫歷史。
R2-6：三語一致、Compose UI + Room Robolectric + Media3 instrumented + 真機 Android16 連播、簽章及覆蓋更新；取得 GitHub Actions、Schema 28.json、APK sha256，發佈 Github Releases 直接 APK，不只 Actions ZIP。

## 8. 可判斷的使用者驗收

UX-01：手機已有 1000+ MP3/FLAC 而沒有 SAF 來源，首開授權後直接能搜到音樂，選 20 首建「晨間日語」，不需進 DocumentsUI；重啟仍存在。
UX-02：從 Music/Japanese 虛擬資料夾對父層一次勾選含子資料夾的曲目，並可加入既有清單；不必逐層追到每個檔。
UX-03：找出 MP4/MKV/FLV，顯示 playable/pending/codec unsupported；能播音軌的檔案在清單及鬧鐘只出聲，無畫面。
UX-04：歌曲已 EXCLUDED 時加入清單必須明確選擇要不要解除排除；批量加入其他歌曲不能解除排除，不把掃描直接設 AUTO_INCLUDED。
UX-05：同一 URI 於有序清單放兩次，能精確移動、移除單獨項目；名稱變更不影響兩個鬧鐘共用的 playlistId，複製後各自修改不互相污染。
UX-06：鬧鐘畫面只需選「播放清單／播放方式」，能按需展開「歌手／資料夾」，顯示有效歌曲數，能正確存檔及重開。
UX-07：隨機可重複、洗牌循環、清單順序三種模式，正常一首接下一首直到使用者合法解除；貪睡時立即停止，到期建立新 Session。
UX-08：自動靜音設 10 分鐘但持續連播超過 30 分鐘仍未自行解除；無歌曲或連壞 5 首後持續備援鈴聲。
UX-09：3,001 首可搜尋、捲動、多選；橫豎螢幕、字體放大與三語不遮住主要 CTA；長列表調整不強制從頭拖到尾。
UX-10：Preview2.1→完整 P3 不卸載覆蓋，原簽章、鬧鐘、歷史、SAF 權限、INCLUDED／EXCLUDED 與清單完整保留，真機證據可回查。

## 9. 不可接受的捷徑

- 不複製 XPlayer 專有程式碼、品牌素材、廣告或整套私有解碼器。
- 不因「Claude Design」就額外加模糊的霓虹效果、過度漸層或無用途動畫。
- 不用空的歌手頁／只有 UI 契約／不可點的試聽，對外宣稱全功能完成。
- 不自動把所有掃描到的歌曲納入鬧鐘，共用池的含義與使用者選取必須一致。
- 不把虛擬資料夾顯示等同已有檔案原始路徑存取權；維持 Android scoped storage。
- 不直接 merge PR #5、#6 導致相互覆寫、手寫 Room identityHash 或刪掉失敗測試。
- 不把同名曲目當同一檔，也不因一首加入清單就刪掉另一處的 MusicPoolEntry。
- 不將 Preview 3 描述為已可下載，直到 Android 編譯、Room Schema、簽章與 T3 實機完成。

## 10. 下一步交給 Copilot 的最小工作包

在 feature/p3-media-browser-ui@8ce9fa93 上先**停在 C3-Corrections / R2-0**，以此文件為唯一 UI 新契約：
1. 先提交「五同級分頁→媒體庫／播放清單兩主頁」的導航線框和三條可點擊任務流程，**不改 AlarmService、Room Schema 或簽章**。
2. 選定線框後再重構 MusicLibraryDialog 成真正獨立導航目的地或等效全頁設計；保留已驗證的 catalog 搜尋、批量範圍保護及音樂歷史。
3. 播放清單 API 改以 playlistId 與 playlistItemId，並把 playlistId、篩選集合、策略拆為正交欄位；不要假裝 Repo 已連好。
4. 以純 UI 測試驗證「建立清單／加入／排序／複製／鬧鐘選清單」使用路徑；檔案編輯權由整合者分配，避免與 PR #5 重疊修改。
5. 回報 branch SHA、真實 UI 接線／佔位、待 Android Agent 接口及 Gradle/Compose 實測結果。若沒有 Java，照實報告；由整合分支 CI 驗證。

完整 Preview 3 的影音掃描、Room v28、Session、安全備援、簽章與 APK，仍由 Android Agent 接手；任何未達 D1+UX-R2 全量核心契約的 build 都只能是內部試作，不能作正式下一版對外交付。
