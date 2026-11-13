# AlarmClockXtreme Preview 3：媒體優先影音庫 × 多策略持續連播 —— 完整實作契約

**版本**：P3-D1／2026-11-13
**狀態**：作者需求已決定；此文件屬技術設計／施工與驗收契約，**不是程式完成或 APK 已交付的證據**。
**程式基準**：Penguink72/AlarmClockXtreme，fix/preview2-pool-refresh-seams-20261113@d74c412fb9f02a047680dd8e8ab727815122fc3a（Preview 2.1，v143/144，Room v27）。
**舊規劃**：docs/tcl/preview3-zaljatni-media-first-browser-plan.md；若與本文衝突，以本文的八項最新作者決定為準。
**不可動**：main@a0cc501、preview/music-alarm-1@e7125b3、preview/music-alarm-2@e993652；在獨立 Preview 3 功能／整合分支施工，不強推既有分支。
**下個可交給使用者安裝的 APK**：所有本文核心能力一起具備；內部可以逐批提交與跑 CI，但不得把缺少 FLV、影片索引、完整三策略或持續連播的包稱為 Preview 3 完成版。下次正式版本碼至少 145／146，同 Application ID、沿用 B7 固定簽署。

## 0. 作者八項已決定需求（禁止退回二選一）

D1 自動搜尋手機**音訊及影片**；使用者也能一次授權額外資料夾，來源與授權可保存、重掃。
D2 支援 Media3 在裝置上能解碼的影音音軌；包含但不限於 MP4、MKV、FLV、WebM、M4A、FLAC、MP3 等；無法解碼的檔案必須可見原因，**不得悄悄消失或將副檔名等同可播放**。
D3 所有發現項目先是「候選」，可搜尋、選取及一鍵全選／批量加入；系統鈴聲／通知音／鬧鐘音預設不在推薦的整批加入集合中，使用者明確選取例外才可加入。原始檔案不可刪除。
D4 共用一份音樂池；各鬧鐘可以另設歌手或來源資料夾範圍，並保存**獨立播放清單及順序**。
D5 每鬧鐘三種策略都要：① 每首重新隨機抽樣、允許連續重複（預設）；② 每輪洗牌、不重複、播完重新洗牌；③ 依指定清單順序、清單播完重頭循環。
D6 同一次鬧鐘**持續接續播放直到使用者解除**；貪睡立刻停止當前播放，貪睡到期重新開始一場連播。不得因單曲結束、全域自動靜音或貪睡上限的「自動解除」提早宣告成功。
D7 獨立全畫面「音樂與影音庫」，分「歌曲／影片／資料夾／歌手／音樂池」，含搜尋、多選、批量加入／排除／恢復與試聽。
D8 **內部分批做，但下一個交付 APK 一次具備影音搜尋、FLV 可辨識與可支援音軌播放、三種選曲策略、持續連播及上述 UI**。無法驗收的項目要公開阻擋正式交付。

## 1. Zaljatni 參考邊界與不同處

Google Play 官方公開：隨機音樂鬧鐘、響鈴歷史、重聽、分享、以旋轉 UI 設鬧鐘；2.3.0 的官方資訊提及依歌手搜尋／選擇隨機曲目。可推測其操作哲學為「先讓 App 發現音樂，使用者再選播放條件」，**不能宣稱已取得 Zaljatni 原始碼、內部 SQLite／MediaStore 實作、支援影片或無限多曲連播**。

Preview 3 只採其「媒體先被看見、操作成本低」的使用原則。相較之下，**混合影片音軌、同次長時間連播、多策略、全域池＋個別鬧鐘播放清單**皆為本專案獨立的明確需求，不歸因於 Zaljatni。

## 2. 已核對程式的實際接縫與修改位置

| 目前路徑 | 基準實況 | Preview 3 工作 |
|---|---|---|
| ui/ringtone/MusicLibraryIndexer.kt | scanMediaStore 只讀 MediaStore.Audio.Media；SAF 可遞迴音訊和部分影片 | 加 MediaStore.Video，將發現與可播放驗證分離；處理多卷、權限、結果來源 |
| ui/ringtone/SupportedMediaFile.kt | videoExtensions 為 3gp/m4v/mkv/mov/mp4/webm，**沒有 flv** | 增 FLV 與 MIME 兼容，集中格式辨識規則；無聲影片狀態不被誤判 |
| ui/ringtone/VideoAudioTrackProbe.kt | 使用 Android MediaExtractor + MediaCodecList 判斷音軌；可能與 Media3 對 FLV 的可播放能力不一致 | 加 Media3 相容音軌 probe／分層待確認狀態，避免 MediaExtractor 不認得便靜默丟掉 |
| ui/ringtone/MusicLibraryTreeScanner.kt | 遞迴 SAF，只有 probe true 才入候選 | 保留 SAF 為補充來源，FLV 可被列出且顯示驗證結果；支持進度／取消 |
| ui/ringtone/MusicLibraryDialog.kt、MusicPoolDialog.kt | 多個小 AlertDialog；MediaStore 曲目預設可能藏在 Folders 範圍 | 換成全頁可互動結果，不以 DocumentsUI 為第一入口 |
| ui/ringtone/MusicLibraryViewModel.kt、MusicLibrarySourceStore.kt | 有來源、候選、批量操作，Preview 2.1 修過部分舊快取／來源競態 | 新搜尋狀態、媒體類型、來源分組、播放清單編輯、頁面生命週期與搜尋效能 |
| data/local/entity/MusicTrack.kt | uri/title/source/fileName/metadataTitle；無持久 artist/album/path/codecStatus | 追加 metadata 表或欄位，不改舊 URI 主鍵／歷史 |
| data/local/AlarmDatabase.kt、MusicLibraryDao.kt | Room v27，music_pool_members 為 INCLUDED/EXCLUDED；沒有鬧鐘獨立播放清單 | v27→v28 非破壞遷移，加入媒體 metadata、鬧鐘播放規則與清單資料 |
| data/model/Alarm.kt | randomMusicEnabled、ringtonePool，沒有三模式和獨立清單 | 不借用 comma-separated ringtonePool 假裝 playlist；新增專用設定存取契約 |
| ui/alarmedit/AlarmEditViewModel.kt、AlarmEditSoundSections.kt | 有 randomMusicEnabled 開關和打開音樂庫；沒有每鬧鐘策略／範圍／排序介面 | 每鬧鐘設定頁新增策略、歌手、資料夾與獨立清單入口；draft、儲存及重開一致 |
| service/AlarmService.kt | 自動連播單一隨機曲目；Player.isPlaying 才記錄；連續失敗 5 次備援；全域 autoSilenceMinutes（預設 10）；貪睡上限可 AUTO_DISMISS | 加獨立選曲 Session、三策略、持續連播，修關閉／貪睡／時間限制優先權 |
| data/backup/AlarmBackupMappers.kt 等、AlarmShareCodec | 只序列化既有 Alarm 欄位 | 配套備份／匯入／分享相容，既有資料不能丟 |
| .github/workflows/p0-android-baseline.yml、app/build.gradle.kts | 目前 Preview 2.1 143/144 雙 Runner 固定簽章、Room v27 KSP 驗證 | 新增 Preview 3 145/146、Room v28 Schema 鎖定、強制目標測試及手機直下 APK |

**特別重要**：現在 Media3 的音樂隨機模式是單曲終了時再開下一個播放器；建立三策略後應以單一 Session 選曲，不要由三種 UI 分別操控 ExoPlayer 狀態。

## 3. 最終使用者流程與頁面地圖

### 3.1 一次完成音樂庫建立，不強迫逐層找檔案

1. 使用者在鬧鐘編輯頁按「音樂與影音庫」，進入全畫面。初次啟動先展示已索引數量、權限、可用候選；必要時顯示「搜尋手機音訊」「搜尋手機影片」或「全部搜尋」。
2. Android 13+ 音訊與影片權限各自申請／明示目前狀態；只授權音訊也可用音訊清單；部分影片權限須明確顯示「僅部分可見」和重選入口。
3. 系統發現完成後，直接顯示歌曲／影片；搜尋欄可搜曲名、原檔名、歌手、專輯、虛擬資料夾。**不得只顯示「找到 N 首」卻沒有可選項目**。
4. 點「全選目前結果」或「勾選資料夾（含子資料夾）」後，顯示數量、來源範圍、預設排除的系統音效數，再明確確認「加入音樂池」。結果立即刷新，無需離開頁面。
5. 「未找到這個檔案？」才進入額外資料夾授權。SAF 系統授權介面只開一次，保存 persistable URI，之後可自行重新掃描；不能承諾 Android 11+ 不能授權的根目錄／Download 位置都可繞過。
6. 「音樂池」頁展示 INCLUDED、未加入、EXCLUDED、暫不可用與錯誤候選；排除／恢復不觸碰原檔案。

### 3.2 五個頁面

| 分頁 | 首要互動 | 主要驗收 |
|---|---|---|
| 歌曲 | 標題／歌手／專輯，搜尋、依狀態篩、批量勾選、短暫試聽 | MediaStore Audio 不需 SAF 即能看到 |
| 影片 | 原檔名、容器、音訊軌狀態、錯誤原因、試聽 | MP4/MKV/FLV 等候選可見；可播才准加入 |
| 資料夾 | 按儲存卷 + RELATIVE_PATH 顯示的虛擬資料夾樹；父層可含子目錄選曲 | 不啟動 DocumentsUI 就能按 MediaStore 索引的資料夾選取 |
| 歌手 | 搜尋、複選歌手、其歌曲及可用數量；未知歌手單獨分類 | 支援 Zaljatni 式的歌手選曲 |
| 音樂池 | 全域候選／已加入／已排除；批量恢復；顯示有效數與警示 | 使用者看得懂哪些歌曲真的會被抽選 |

固定的列表設計：每列標題、來源／類型、已加入狀態、Checkbox；下方固定操作列顯示「已選 N 首」「加入」「排除」「恢復」「清除」。切頁面、來源範圍、搜尋條件時重新核定選取集合，不得把不可見 MediaStore 或別的資料夾納入全選。試聽按鈕獨立於勾選；試聽不寫鬧鐘播放歷史，鬧鐘響鈴時停用試聽。

專輯可作歌曲／歌手子分類，不另加第六個強制分頁。多語 English／繁中／日文沿用 Android resources；音樂 metadata 不翻譯。

### 3.3 每鬧鐘設定頁

在「隨機音樂庫」開關下增加一張完整設定區：

- **播放來源**：共用池全部／指定歌手與資料夾／這個鬧鐘自己的清單；可先選共用池，再在每鬧鐘建立獨立清單。
- **歌手、資料夾限制**：同類可複選（歌手 A 或 B；來源資料夾 C 或 D），跨類型同時啟用採交集；可清除限制，顯示目前符合資格的數量。
- **獨立播放清單**：自全域 INCLUDED 曲目建立屬於此 alarmId 的列表；支援加入、移除、拖動排序；允許同一 URI 在有序清單中出現多次，但須明確顯示重複項。
- **播放順序**：① 完全隨機可重複（預設）、② 每輪洗牌、③ 自定順序。選③而清單空時阻擋儲存該組設定或要求先建立清單，不默默換成其他模式。
- **持續播放**：此專用模式固定「直到解除」，顯示操作與電量提示；貪睡立即停止並於到期重新連播。舊的「單曲播完解除」與全域自動靜音對此模式不能暗中生效。
- **驗收預覽**：畫面顯示「共用音樂池 2,989 首 → 歌手／來源限制 → 本鬧鐘清單 → 最終可播 N 首」，若 N=0 顯示原因與「調整條件」入口，不能只顯示啟用開關。

## 4. 媒體發現、權限與 FLV 支援

### 4.1 發現層次

Primary：MediaStore.Audio.Media 與 MediaStore.Video.Media，依 Android 支援取得 _ID、content URI、DISPLAY_NAME、TITLE、ARTIST／ALBUM（如有）、RELATIVE_PATH（如有）、volume、MIME、DURATION、媒體分類、dateModified。以 Content URI 讀／播，不由 RELATIVE_PATH 拼 raw 檔案路徑。

Supplement：MediaStore 未索引的檔案（常見 FLV 或 Downloads 文件）經 SAF 額外授權資料夾遞迴取得；保留權限持久化、重掃、子資料夾及拔除 SD 卡時的錯誤。可評估 MediaStore.Files 合法可讀的候選，但不能把它當成繞過 scoped storage 的全面存取權。不得要求 MANAGE_EXTERNAL_STORAGE。

將「發現」「可存取」「音軌存在」「音軌可解碼」「實際曾開始播放」分離成不同證據，不把 MIME／副檔名說成成功播放。

### 4.2 FLV 與多容器

- 更新 SupportedMediaFile：納入 .flv、video/x-flv、application/x-flash-video 等常見 MIME，並保留 mp4/mkv/webm/mov/m4v/3gp、音訊類型；來源可能把 FLV 標成 application/octet-stream，允許已授權來源用檔頭／副檔名輔助判斷，不因 MIME 模糊就漏掉候選。
- Media3 ExoPlayer 的 Extractor 對 FLV 容器有支援；**Android MediaExtractor 不一定對 FLV 具同等辨識能力**。不得直接延續現有 AndroidPlayableAudioTrackProbe「MediaExtractor false → 丟掉檔案」作為 FLV 的唯一判斷。
- 建議 Media3 相容探測器或可控制的輕量 extractor 檢查，分批取得可用音訊軌與可解碼狀態；在必要的真機播放驗證之前保留「待檢查」，不要永久標無效。探測不能無限制啟動幾千個 ExoPlayer／大檔掃描，須限流、可取消、可重試、顯示進度。
- FLV 不能一般位置 seek：試聽若需從中間位置開始，提供明確降級（從頭播、不能拖曳進度），不要因 seek unsupported 誤判無法播放。
- 選曲過濾只有 VALIDATED_PLAYABLE；對 PENDING/UNSUPPORTED/BROKEN/NO_AUDIO/PERMISSION_LOST 顯示不同訊息。對 MediaStore 和 SAF 都採用相同驗證政策。
- Media3 播放端強制只選音軌、停用 video track、無視訊 Surface；但這不代表已在所有裝置證明有聲音，須在 Android16 與實際格式樣本試聽／鬧鐘實測。
- 不導入 FFmpeg 或私有 codec 擴充，除非後續使用者另行批准；本批「支援」以 Android 裝置可用 Media3 音訊解碼能力為界。
- 媒體支援依兩軸呈現：容器可識別、內部音軌可解碼。檔案沒有音軌和音軌編碼不支援應分開顯示。

### 4.3 權限與錯誤契約

Android 13+ READ_MEDIA_AUDIO / READ_MEDIA_VIDEO 分別授權；Android 14+ 影片可出現「選取部分媒體」，應依官方建議處理 READ_MEDIA_VISUAL_USER_SELECTED 並在 onResume 重查權限及 ContentResolver 可見項。權限撤銷與進程重啟不能依賴舊快取。特殊 Downloads／未被索引來源顯示 SAF 補充入口；不要求圖片權限來讀影片。

每個來源狀態：NOT_GRANTED / PARTIAL / GRANTED / SCANNING / READY / NO_RESULTS / ACCESS_LOST / ERROR。每個候選狀態：FOUND / AUDIO_PENDING / PLAYABLE / NO_AUDIO / CODEC_UNSUPPORTED / UNREADABLE / TEMPORARILY_MISSING。不能用「搜尋不到」掩蓋「未授權」。恢復權限後保持已保存的 INCLUDED／EXCLUDED，不因新索引重建讓舊排除復活。

**實作注意**：Content URI 在 MediaStore 和 SAF 可能不同，同名不一定同一檔；無可靠對應證據時不強行合併身分；應能保留多個來源指向同一 URI，避免移除一個重疊授權來源就錯刪仍可讀的曲目。

## 5. 共用音樂池與每鬧鐘獨立規則

### 5.1 三層資格

GLOBAL: music_pool_members 中 INCLUDED 是播放資格白名單；EXCLUDED 是全域硬排除；未加入候選不得播放。所有鬧鐘共享此基準。

PER_ALARM: 每個鬧鐘可另設允許的歌手集合、來源資料夾集合、獨立播放清單。它們只能**縮小** GLOBAL 有效集合，不能暗中繞過 EXCLUDED。若使用者想在自己的清單放一首未加入的歌，介面先明確詢問「同時加入共用池」，才可保存為可播項目。

RUNTIME: 實際可讀／已驗證可播、手機目前授權有效、未於本次播放被標記失敗。每次抽選以 GLOBAL ∩ PER_ALARM ∩ RUNTIME 過濾。

同一維度的多個歌手／資料夾採 OR；同時啟用不同維度採 AND；若啟用獨立清單再對清單項取交集。明確呈現最終可播數。UNKNOWN_ARTIST 可被使用者明確選中，否則不偷偷歸給某個歌手。

### 5.2 三種策略的精確可測定義

**RANDOM_WITH_REPLACEMENT（預設）**
- 每首結束以目前有效候選重新均勻抽一首，不因上一首已播而避開；若只有一首便持續重播。
- 可重複包括連續同一首；不承諾每首均勻出現於有限觀察區間。
- 若清單是明確有序且含相同 URI 多次，隨機抽選按「曲目 URI」還是「清單項權重」？為避免暗藏權重，**預設以去重 URI 均勻抽**；有序模式才保留重複清單項。

**SHUFFLE_CYCLE（每輪不重複）**
- 進入響鈴 Session 時以符合條件且可用的不同 URI 生成亂序佇列；成功開始播放的一首視為已消耗本輪；播完所有仍有效候選後重建下一輪。
- 本輪不得重複成功播放的 URI；單首播放失敗不算「成功播放」，但本輪不得無限重試同一失敗 URI，改本 Session 失敗清單。
- 若使用者在響鈴期間修改池／播放清單，**下一次新響鈴 Session 才生效**；避免當前回呼中清單被改而跳錯。
- 候選只剩 1 首時，下一輪可再播放同一首；不得停機。

**ORDERED_PLAYLIST（指定順序）**
- 按該鬧鐘獨立清單的 position 依序播放；一輪完成從第一項重新開始，直到解除。
- 同一 URI 若被使用者放了兩次，按兩個清單項播放；無法存取／已排除／未入池的項目顯示警告且安全跳過，**不可把被排除歌曲當作 fallback**。
- 空清單在儲存編輯時顯示錯誤；如果之後因權限或全域排除導致全部失效，改進入備援鈴聲，直到使用者解除，不能自行關鬧鐘。
- 有序清單與歌手／資料夾附加條件若導致 0 首可播，在編輯頁先告警，不默默覆蓋設定。

### 5.3 Session 隔離與歷史

建議建立 AlarmMusicSession：
- 身分：alarmId + fireId（或不可重用的 generation）；起始時取得已儲存 config、可用 URI 快照、策略。
- 狀態：ALARM_STARTING / SELECTING / PREPARING / PLAYING / NEXT_TRACK / SNOOZED / DISMISSED / FALLBACK。
- 包含 random seed（測試可注入）、shuffle queue、ordered index、session failed URI、consecutive failures、activeTrackToken、播放紀錄序號。
- 僅 current session/generation 的 Media3 callback 可以變更播放；舊 session 的 onEnded/onError/onStarted 不可在已解除、貪睡、其他鬧鐘搶佔後再次喚醒音樂。
- 播放歷史一律在 isPlaying=true（開始進行）後寫入：同一首重複播放兩次記兩筆；保留原 displayTitle 與 playOrder、fireId；僅準備或已跳過不記錄；備援鈴聲單列 incident，不冒充音樂歷史。
- 同時有兩個鬧鐘觸發時沿用 AlarmService 原有搶佔／優先權機制，不讓一個 session 的音樂在另一個鬧鐘解除後復活；需要專門競態測試。

## 6. 持續響鈴、貪睡、解除與備援的優先權

這一節是安全關鍵，必須先建立測試才改 Service。

1. 當「新音樂連播模式」正在響鈴時，**忽略全域 AppSettings.autoSilenceMinutes（現預設 10）對這場響鈴的自動解除**；普通非連播鬧鐘仍沿用既有設定。使用者可以從鬧鐘通知與響鈴頁明確解除。
2. 連播模式忽略 dismissAtRingtoneEnd 的「單曲結束自動解除」；編輯頁顯示這個既有選項對連播不適用，不保留語義衝突。
3. 達到 maxSnoozeCount 時，不能走 SnoozeCapPolicy.AUTO_DISMISS 使本模式自動解除；預設改為**拒絕更多貪睡並持續響鈴**，同時顯示可用的解除操作；對需挑戰解鎖的鬧鐘仍遵守挑戰門檻。
4. 接到合法的「解除」操作（通知解除、畫面完成解除條件、可配置的實體按鍵操作）時，立即失效 session generation，取消 pending picker／watchdog／音量 job／Media3 player，再走既有記錄與排程；不可在停止後由晚到 callback 重新開始。
5. 貪睡操作立刻停止所有音訊、清除尚未開始的下一首佇列、退出當前連播 session；貪睡到期**建立全新 session**，隨機模式重新抽、洗牌模式重新洗牌、有序模式從第一首重新開始；保留跨響鈴的播放歷史快照。
6. 當前歌曲正常結束時切下一首；在 PLAYER_STATE_READY 但尚未 isPlaying=true 時保持起播 watchdog；ENDED_WITHOUT_START、無權限、解碼失敗記為失敗，不死循環。
7. 延續最多**連續 5 首失敗**後轉系統保底聲；備援鈴聲必須**持續響直到使用者解除**，不能因歌曲池失效自動停止、也不能反覆重入音樂池讓鬧鐘靜音。
8. 電話、音訊焦點、系統音量、藍牙路由、DND 與 OEM 背景限制仍可能影響實際可聽性。isPlaying 並非喇叭可聽的物理證明；不能宣稱 Android 能在所有 OEM、強制停止、裝置斷電、開機前鎖定時永遠保證發聲。應保留現有前景服務、通知、備援及長時間壓力測試。
9. 若連播中用戶編輯音樂池，下一次新 Session 生效；當前 session 播放前仍檢查必要的 URI 授權與可用性。用戶按「停止試聽」不等同「解除鬧鐘」。

## 7. 資料模型、遷移與相容性

建議 **Room v28** 作為一次涵蓋新介面 metadata、每鬧鐘策略和獨立播放清單的非破壞升級；最終欄位以實際設計驗收和 KSP 為準，不事先手寫 identityHash。

建議邏輯表：
- media_catalog_details：uri PK（對應既有 music_tracks.uri）、providerKind（MEDIASTORE_AUDIO/VIDEO 或 SAF）、mediaKind（AUDIO/VIDEO_AUDIO）、volumeName、relativePath、artist、album、mimeType、duration、audioProbeState、audioCodecHint、lastSeen、availability、failureReason 等。可針對歌手、路徑建立索引；不要以 fileName/artist 當 PK。
- alarm_music_settings：alarmId PK/FK、playMode（RANDOM_WITH_REPLACEMENT/SHUFFLE_CYCLE/ORDERED_PLAYLIST）、sourceMode、artistFilters、folderFilters、playlistEnabled、continuousUntilDismiss 等，版本／預設明確。可把篩選集合放獨立正規化表；本文件描述邏輯契約，不強行指定 JSON 或一張表。
- alarm_music_playlist_items：以 alarmId + position/order 形成有序清單，記 URI 與序號；同一 URI 可重複加入不同 position；刪鬧鐘時清理關聯，但刪單一曲目／來源不能破壞其他鬧鐘資料及播放歷史。
- 必要時 media_source_links：URI 與來源 ID 的多對多，用以處理 SAF 重疊根目錄與虛擬資料夾；不得一筆 source 欄位取代多重來源事實。

**既有資料保留**：music_tracks、music_pool_members、playback_history、alarms、SAF 授權／來源、目前所有 27.json 以前的 schema 檔案均維持完整；Room 27→28 禁用 destructive migration；正式 KSP 產生 28.json，驗證 24→28 與 27→28。

**舊鬧鐘轉換**：保留原 randomMusicEnabled、ringtonePool、ringtoneUri、排程及其餘行為；舊 randomMusicEnabled=true 的鬧鐘若自動啟用新持續連播，必須明示升級提示。新建連播設定預設 RANDOM_WITH_REPLACEMENT + UNTIL_USER_DISMISS；原非隨機音樂鬧鐘的 auto-silence、單曲結束等行為保持不變。缺少新設定行時使用明確、可測的 backward-compatible 預設，不可推定舊清單位置。
  
**存檔原子性**：AlarmEditViewModel.save() 目前分別組出 Alarm 物件並透過 repository.save；新增每鬧鐘設定／清單，需在同一 Room Transaction 寫入 Alarm 與其音樂設定，新增 Alarm 時先取得 alarmId，再存清單；成功後才排程。草稿、取消編輯、另存新鬧鐘、複製鬧鐘須能保留或正確複製播放設定，避免列表和音樂條件互相失配。

**備份和分享**：擴充 AlarmBackupMappers、BackupManager、AlarmShareCodec 及對應資料模型的版本化內容，能 round-trip 每鬧鐘策略、歌手／來源篩選、清單 URI 和順序；匯入到不同手機時 URI 可不存在，保留條目並標 unavailable、請使用者重新指定，不將原作者裝置的 URI 偽裝成新手機可讀。舊版 JSON/分享連結導入不應崩潰。

## 8. 架構分工與實作依賴

~~~text
Android MediaStore.Audio + MediaStore.Video + persisted SAF
    ↓
MediaDiscovery (permissions, indexing, optional FLV extractor probe)
    ↓
Unified catalog (uri + metadata + provenance + playability)
    ↓
Global music_pool_members (INCLUDED / EXCLUDED / candidate)
    ↓
Alarm-specific scope (artist/folder filters, independent ordered playlist)
    ↓
SelectionPolicy (repeat-random | shuffle-cycle | ordered)
    ↓
AlarmMusicSession (fireId, generation, queue, failure budget)
    ↓
Media3 playback (audio-only; transition / snooze / dismiss / fallback)
    ↓
PlaybackHistory snapshots and AlarmIncident records
~~~

來源／索引不得知道鬧鐘服務的 session；播放器不得直接解析 Compose 篩選狀態；選曲器用純 Kotlin 可重演測試；Service 只負責協調和確保停止／備援。避免把所有邏輯放進巨大的 AlarmService 或 MusicLibraryViewModel。

## 9. 分批開發：內部多批、外部一次交 APK

| 批次 | 任務與主要檔案 | 交付證據 |
|---|---|---|
| P3-A 基準／契約 | 固定 d74c412 起點、測試矩陣、明確資料模型／Session API、Android 權限與 FLV 資料來源盤點；建立預期失敗測試 | 基準 commit、已存在/新增測試可重現；不改 main |
| P3-B 媒體發現 | MusicLibraryIndexer、SupportedMediaFile、VideoAudioTrackProbe、MusicLibraryTreeScanner；Audio/Video/FLV，權限／待驗證狀態、來源去重、增量掃描 | MP4/MKV/FLV 真實樣本辨識與不可播理由，Audio-only 不失效 |
| P3-C 共用池與 v28 | Room Migration 27→28、metadata、每鬧鐘設定及有序清單，讀寫 Repository，舊 v27 升級 | 正式 KSP 28.json、24→28、27→28，3,001+ 與 12 排除測試 |
| P3-D 全頁 UI | 五分頁、虛擬資料夾、歌手、搜尋、多選、試聽、共用池與每鬧鐘清單編輯，英文／繁中／日文文字 | 手機不用 DocumentsUI 便能搜尋已索引影音並加入池；所有五分頁可操作 |
| P3-E 三策略與生命週期 | 純 Kotlin selector、AlarmMusicSession、AlarmService、SnoozeCapPolicy、AutoSilence 分支、fallback | 三模式、無限接播、貪睡、解除、跨鬧鐘搶佔與錯誤降級目標測試 |
| P3-F 完整整合 | AlarmEditViewModel、備份／分享、通知、各模式聯調；Android 16 端到端、性能、固定簽章 | 全核心功能與 CI 項目通過；v145/146 直接 .apk GitHub Releases |

**依賴順序**：P3-A →（P3-B 與 P3-C 可在互不覆寫檔案下並行）→ P3-D、P3-E（依賴新 DAO/選曲契約）→ P3-F。若有兩個 Agent，不得同時無鎖修改 AlarmService、AlarmDatabase、strings.xml；由整合者採 PR／cherry-pick、逐鍵合併翻譯，不直接覆蓋。每個批次可用測試與 PR 交付，**不可發布功能不完整的下一版正式測試 APK**。

## 10. 測試與驗收矩陣

**T0 來源與契約**
- 核對每個改動文件與 branch HEAD；來源不會被文件或錯誤 AI 回報偽造。
- 基準 Preview2.1 CI 730 tests / 1 known BedtimeReminderBaselineTest timing failure；後續不能把新增失敗列成原有。不要刪原測試換取綠燈。

**T1 純邏輯單元測試**
- RNG 注入 deterministic seed：有 1 首、3 首、3,001 首；隨機重複合法；洗牌一輪不重複、第二輪重新洗牌；有序列表位置不錯亂且能循環；被排除 URI 永遠不抽；0 首明確 fallback。
- 多歌手 OR、資料夾 OR、兩類 AND、獨立列表與全域池交集；來源同名不同 ID 不串池；不同鬧鐘的選曲 Session 不共用 shuffle queue 或 ordered index。
- 新掃描候選不自動加入；系統鈴聲分類預設不在一鍵推薦；使用者單獨手選可加入；重掃、重啟、權限撤銷／恢復不重置 EXCLUDED。
- FLV MIME/extension（包括 octet-stream）、無聲 FLV、含 AAC/MP3 音訊 FLV、解碼器不支援 FLV、損毀文件；別把偵測器無法判定視為音訊不存在。
- 3,001+ 首搜尋、多選、排序、未加入和已排除計數；高負載查詢避免 SQLite 999 個參數陷阱。

**T2 Room／播放器與服務測試**
- KSP 對照 v28 committed Schema 零差異；v27→28、v24→28 確認 Alarm 及音樂池、來源、歷史、每鬧鐘清單資料保存；新 alarmId 的原子存檔和刪除清單。
- READY 但從未 isPlaying → watchdog／跳曲；播放當下才歷史入帳；onEnded→下一曲；壞檔連續 5 首→持續備援；被停用後晚到 onEnded/onError/onStarted 不可復活服務。
- 全域 autoSilenceMinutes=10、dismissAtRingtoneEnd=true、maxSnoozeCount 已滿，仍不得讓新連播模式自行解除；普通鬧鐘保持舊測試結果。
- 貪睡時停止，重響建立新 Session，記錄歷史 fireId；連續觸發兩個鬧鐘，不能繼承對方 queue；備份與分享 round-trip，從 v27 覆蓋到 v28 不丟失資料。

**T3 手機實機驗收（Android 16）**
1. 零 SAF 授權但系統有 1,200 首音訊，使用者授權後立刻在歌曲頁可見可搜尋多選，不用先逐層選資料夾。
2. 系統可見 300 個影片候選；MP4、MKV、WebM 分別抽樣，含可用音軌者可加入；無聲影片清楚不可播；被拒 Video 權限不影響 Audio。
3. 有已授權資料夾中的 FLV（至少 AAC 或 MP3 音軌可支援樣本）可被看見且試聽、響鈴只出聲，顯示不能 seek 的限制；不支援 codec 顯示原因，不卡死或默默略過。
4. 虛擬 Music/Japanese/Artist/Album 不用 DocumentsUI 即可一次選包含子目錄的曲目；額外 SAF 根目錄授權一次後保存並重掃。
5. 3,001 首影音，排除 12 首，手機重新掃描、重開及覆蓋升級後仍不抽到；共用池與多鬧鐘各自的歌手／資料夾限制、獨立清單均保存。
6. 三種策略逐一實測：可重複隨機、整輪不重複、有序循環；至少連續跨越多首歌曲，播放 MP4/MKV/FLV 只有聲音。
7. 連播 30 分鐘超過舊自動靜音門檻，確定不被自動解除；延長壓力測試 60 分鐘檢查背景省電與前景服務，並測電話中斷、拔藍牙、鎖屏；不能僅靠 isPlaying 判定真的可聽。
8. 貪睡立刻停止且到期重啟、手動解除立即停止；在上首 onEnded 與解除同時發生時沒有幽靈音樂；全部壞檔進保底鬧鐘而非靜音。
9. 繁中／English／日本語五頁及鬧鐘編輯全流程不裁切、不變更原始歌名；可試聽但不進鬧鐘歷史。
10. 143/144 → 145/146 不卸載升級，appId、簽章指紋相同，鬧鐘排程、授權、包含／排除、單鬧鐘清單和歷史保存。

## 11. CI、APK 發布、版本治理

- 在新整合 branch 提升 ciVersionCode 與最低碼至 145，第二 Runner 使用 146；維持 com.sysadmindoc.alarmclock.debug 和目前固定 Debug Cert（SHA-256：4F:15:3F:91:16:0F:A5:89:C6:CF:D5:5C:DE:1B:4A:CA:B3:81:A7:0B:0D:CF:31:D7:55:FF:39:40:4C:56:A2:1E）。
- 嚴格依賴鎖定、Kotlin、Media3、Room v28 產物與歷史 Schema 確認；三語格式參數一致且所有新增核心介面關鍵字必備；更新測試須能辨識 BedtimeReminderBaselineTest 的既有失敗，不能隱藏新增失敗。
- 執行主 APK Build、Unit Tests、簽章比對、固定 SHA-256，輸出可核對的 GitHub Actions Run、提交 SHA、Artifact；T2 只代表可安裝，不代表手機響鈴通過。
- 手機直接下載是已明示使用者需求：完成 T2 後將經驗證的 145、146 **個別 .apk** 發布至公開 GitHub Releases prerelease，而非只提供 Actions ZIP 或短暫 sandbox 路徑，並確認永久頁面與直接檔案 URL 可開啟。
- 不得用演示圖或模擬器數據代替 Android16、FLV 真實樣本與連播 T3；正式放行報告區分「程式碼存在」「CI 通過」「真機通過」，任何不具備者不得說成功。
- 若 Android 背景／權限機制阻止部分內容讀取，顯示操作回復路徑；不可宣稱完全掃到手機所有檔案，亦不可解除安全限制追求全覆蓋。

## 12. 施工分工與停止條件

Copilot/UI Agent：P3-D 全頁交互、五分頁、音樂列表／多選、每鬧鐘播放設定、三語文字與 Compose/UI 測試，獨立 feature 分支。
Android/Argon/其他 Agent：P3-B MediaStore Audio/Video/FLV/SAF、P3-C Room v28／原子儲存、P3-E 三策略／服務生命週期、P3-F 固定簽章／APK，獨立工作樹或整合分支。若 Argon 額度不可用，可換具 Android 編譯能力的 Agent，不把角色當作依賴。
GPT/TCL：每批遠端讀碼核對，尤其 FLV probe、autoSilence、貪睡 cap、Session race、schema/backup、UI 可達性；未通過不得改標籤。
使用者 T3：從手機可直接進音樂庫並批量加入現有歌曲；真正 FLV 有聲音；三種策略及貪睡／解除正常；升級不丟資料。

**P3 放行的硬條件**：P3-B/C/D/E/F 完整進入同一個可下載 APK；MediaStore Audio+Video、SAF FLV、全頁五分類、全域池＋獨立清單、三模式、直到解除、貪睡與備援、Room v28、固定簽章、測試與真正 Android16 操作證據均可追溯。缺任一項僅能稱為中間開發版，不能以 Preview 3 完成版交付。

## 13. 本文採用的公開依據（架構規則來源）

- Zaljatni Google Play: https://play.google.com/store/apps/details?id=kr.zzzi
- Media3 ExoPlayer supported formats (包括 FLV 和不能 seek 的限制): https://developer.android.com/media/media3/exoplayer/supported-formats
- Player events / onIsPlayingChanged / onMediaItemTransition: https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- Shared media access, MediaStore permissions, Downloads 例外: https://developer.android.com/training/data-storage/shared/media
- Android 14 partial photo/video access: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
