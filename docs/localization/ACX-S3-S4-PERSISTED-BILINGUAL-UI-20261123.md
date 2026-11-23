# ACX S3＋S4｜中英日單語切換、第二語言及正式介面雙語顯示

2026-11-23。本片接續已具 3,100 條中日 Android native 補充字串的 S1＋S2 Draft PR #68；本分支為 PR #69，尚未合併 main。

## 需求與已做範圍

使用者要英文、繁體中文、日文的單語切換，另可選兩種語言同時顯示；不需要 G1／G4 人工審批、翻譯保護 Environment 或多把簽章。

### S3：語系設定

- Android 13+：沿用既有 LocaleManager.applicationLocales，主語言選擇由 Android 系統保存，跟隨系統仍支援。
- Android 8～12：AppLanguageManager 以獨立、同步可讀的裝置 SharedPreferences 保存 EN、zh-TW、ja 或 Follow System，在 MainActivity、AlarmFiringActivity 的 attachBaseContext 使用 createConfigurationContext 套用。修改只重建設定所屬 Activity，不呼叫 Locale.setDefault，不改變系統語言。
- 所有版本：BilingualUiPreferences 另存 enabled（預設 false）與第二語言 tag（預設 ja）。支援三種語言及其六種主要＋次要排列；不合法的第二語言回退 ja。
- 設定畫面原本的 App 語言對話框增設雙語開關與第二語言清單，同一 Activity 中會監聽設定變更，即時重組 Compose 文本；重啟 App 仍保存偏好。
- 背景鬧鐘通知的既有 LocaleRuntimeTextBridge 在 Android 8～12 也依儲存的第一語言取得系統 native 字串。**通知維持單語**，不對 5 秒前景佔位通知加入耗時或跨語處理。

### S4：共用文字及已覆蓋的實際畫面

- BilingualUi.kt 使用 Compose stringResource 取得主要語言，以 Android ConfigurationContext 唯讀取得第二語言；不修改 JVM-global locale、鬧鐘資料庫或媒體庫字串。
- 顯示主要文字＋換行第二文字；內容一樣或第二語言與主語言相同則不重複。BilingualLabel 亦提供主文字及次文字不同 typography 的可重用元件。
- 已接線：底部及大螢幕 NavigationRail 六個導覽項目；鬧鐘首頁 Hero、空狀態、快速設鬧鐘及部分選單；鬧鐘編輯的頁面與所有 section heading／description、重要動作；響鈴畫面的狀態、解鎖／貪睡／解除及自訂貪睡文字；Settings 首頁及語言對話框；音樂庫標題、瀏覽方式、資料夾來源管理與播放清單操作；最近播放及部分重播狀態。
- 尚未逐一替換的次級頁面、全部舊設定文案、Widget、Wear、通知及系統層另有原生單語回退；**不能把本片說成每個字串在所有畫面都已雙語化**。S4 的可用核心表面已上線，後續依實機版面測試擴充覆蓋。

## 技術界線

雙語在 UI resource 層處理，不寫進 alarm.label、music track title、播放歷史內容、Room、鬧鐘排程、Intent Extra、音訊識別或 API wire format。UI 原本事件 onClick、Snooze/Dismiss gate、MediaStore/SAF 與重播控制不改。

S1＋S2 原先新增的 values-b+zh+Hant+TW、values-ja 專屬補充 XML 已入庫；這輪新增三語 S3 設定字串與 S4 六個導航字串，不覆蓋既有人工版 strings.xml。

## 測試與驗收

- BilingualUiOptionsTest：預設單語、第二語言合法化、關閉雙語保留先前選擇、六種有序語言排列、相同譯文去重。
- AppLanguageManagerTest：既有 en、zh-TW、zh-Hant-TW、ja、follow system 與未支援語種回退契約。
- GitHub workflow s3-s4-bilingual-ui.yml：F-Droid debug Android/Kotlin 編譯、Play 版資源處理、焦點 JVM 測試，以及與 S1/S2 基準比對，避免碰 Room、AlarmScheduler 或 AlarmService 原有程式。第一輪 Kotlin/測試皆成功，但 Git 因淺複製而無法讀 HEAD^；已改為完整 fetch 並使用與 S1/S2 分支的 merge-base 差分。
- 實機仍應核對：6 種語言排列在窄螢幕長文字是否截斷、首頁 5 秒設鬧鐘、熄屏響鈴、對應貪睡／解除、音樂庫來源和最近播放不回歸；尤其是舊 Android 和雙語版面高度。**不以 CI 取代實機驗收。**

## 下一片

先讓此功能枝 CI 全綠並提供可直接安裝的 F-Droid debug APK，再做必要的 UI 長文字修正及次級畫面覆蓋；不需要返回 G1／G4 或三方人工簽章制度。
