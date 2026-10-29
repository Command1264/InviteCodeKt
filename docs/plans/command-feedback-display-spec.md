# 指令回饋與 messages 整體規格

## 2026-10-30 驗收修正：原因、單句錯誤與輸入逾時

使用者核准沿目前功能分支修正。`/ic status` 沿既有資格理由次序，只顯示第一個主要原因：
已建立邀請碼優先於已有邀請關係，再其次才是登記期限到期。只調整呈現，不放寬資格判定。
不插入多原因換行；客戶端仍可能依聊天寬度自然折行。
`/ic claim` 的多原因回覆目前保留，先將原文列為討論項，不自行擴大為全專案理由篩選。

邀請碼格式錯誤、逾時與其他同類錯誤＋重試指引採單句、單一紅色；保留雙語語意、變數與指令。
狀態報告與成功後的下一步多行指引不套用這項錯誤句型規則。
原生輸入標籤為「邀請碼」／「Invite code」，不限制成六位文案；實際格式驗證及儲存均不變。

原生輸入沿 `claim.chat-input-timeout`，預設 60 秒。到期包含剛好等於期限，關閉輸入框並提示；
排程若先於實際時間到期，按剩餘時間再次排程，不因一次未到期而永久失去關閉工作。
提交處再次核對到期，避免晚到的提交悄悄消失；若取消先於到期排程處理，仍明確顯示取消。
確認階段沿原 `confirmation-timeout`；共用會話比較使用同一到期邊界。
新候選的原生畫面仍需真人複驗；先保留 B 已登記的邀請完成離線補發，再停服部署，不重設 B。

狀態：2026-10-18 使用者確認整體規格通過。第一、二階段已完成訊息實作及封裝；[第三階段證據](../testing/evidence/2026-10-18-command-feedback-stage3.md) 記錄獲准的八核心與兩個網路玩家流程、最新 Folia 及渲染客戶端限制。前一候選已部署 Human，但第四階段因首頁資訊過量正在修正，尚未完成真人外觀；[實作與測試計畫](command-feedback-implementation-plan.md) 保留後續範圍與 remote 邊界。

## 回覆與 prefix

- 使用各語系 `presentation.prefix`，預設 `&7[&6InviteCode&7]&r`；允許空字串關閉。
- prefix 是單行，不含尾端空格；一般回覆由輸出層加一個分隔空格，多行只在首行標示。
- 確認區將 prefix 獨立成第一行，沒有尾端空格。關閉時省略 prefix 及空行；Dialog 改用「確認邀請碼／Confirm Invite Code」作標題，不重複內文標題。
- 按鈕、hover、欄位名稱與值不是獨立回覆，不各自加 prefix。伺服器 logger 沿用既有插件標識，不再重複。
- 一般指令、help、status／stats、管理查詢、互動元件與 MESSAGE 獎勵都需涵蓋，不只修改一般 send 入口。

## 玩家聊天與 Dialog

- 聊天靠左，Dialog 標題及確認內文置中；使用平台提供的配置，不以空格補位置。
- 確認區七個邏輯區塊：prefix、確認邀請碼、邀請人、邀請碼、是否使用、選擇期限、同意／不同意。長文或窄視窗仍可能換行。
- 期限提示使用 `請在 {seconds} 秒內選擇`，末尾無冒號，不每秒刷新。
- 邀請碼直接點擊複製與 HEX 彩蛋保留。按鈕綠／紅字與 hover 保留，Dialog 底色由客戶端控制。
- 玩家首頁及詳細用法的操作指令點擊只填入聊天列；help 分類、頁碼與返回可直接執行唯讀導覽。確認區的同意／不同意維持原本操作。
- 原生輸入 Dialog 同樣使用 prefix 作標題，內文放輸入用途與欄位；空 prefix 時以輸入用途作標題。不增加新的輸入模式。

## 色彩與 console 已確認規則

所有模板接受基本色 `&0`–`&f`、樣式 `&k`–`&o`、重設 `&r` 及 HEX `&#RRGGBB`。第一階段實作底層解析，第二階段接入指令／聊天／Dialog。保留未支援的文字為原文，不新增格式套件。

| 輸出 | 顏色 | 粗體及其他樣式 |
| --- | --- | --- |
| 支援 RGB 的玩家聊天／Dialog | 基本色與 HEX；邀請碼依六碼顏色呈現 | 依平台能力呈現；內建文案不靠粗體辨識結果 |
| 舊版玩家聊天 | HEX 轉最近基本色，保留既有邀請碼降色策略 | 依平台能力呈現 |
| Console 命令回覆 | 保留同一組成功／警告／錯誤色彩；預設 16 色，HEX 轉基本色，過暗結果改成可讀的亮色 | 不額外套用粗體；移除模板的粗體、斜體、底線、刪除線及隨機字樣式，保留文字與顏色 |
| 無色彩能力的宿主輸出 | 由伺服器既有輸出能力處理；不得直接印出格式碼或自行塞 ANSI escape | 純文字仍有完整結果與原因 |

Console 不新增 HEX 自動偵測或 JLine 依賴；即使宿主支援 true color，預設仍用 16 色。建議 HEX 從基本色選最接近者，以標準黑底為降色可讀性基準；若結果過暗，改選亮色子集。內建文案以灰／白／金／黃／綠／紅為主，不使用黑字。實際終端機可自訂色盤及底色，不能保證所有主題的對比；後續驗收核對實際宿主。

粗體不適合作為 console 必要層級：部分終端機把它當成亮度，不一定增加字重。上述「不額外套粗體」不禁止宿主用亮度實作亮色。

依據：[Microsoft Console VT 文件](https://learn.microsoft.com/en-us/windows/console/console-virtual-terminal-sequences) 的 SGR 1 為 Bold/Bright，且色盤可自訂；[JLine attributed strings](https://jline.org/docs/advanced/attributed-strings/) 說明輸出取決於終端能力。這些資料不證明本機 Minecraft 輸出路徑已支援任一效果。

Console 的內容與權限依指令用途判斷，不因使用 console 而一律拒絕。只有玩家專用指令回覆「此指令只能由玩家執行／Only a player can run this command.」。Console help 隱藏玩家專用列；管理員玩家只看有權限的列。

## Help 與個人查詢

完整指令、參數、權限與限制見 [help 核對表](command-feedback-help-reference.md)，依實際 parser／controller 整理。不新增指令，不用省略號代替正式用法。

- `/ic` 首頁只列 `code`、`claim`、`status`、`stats`、`help`；各列仍需相應權限。具管理權限時只增加一個 `/ic help admin` 入口。
- `/ic help` 顯示玩家索引；console 改顯示管理索引。`/ic help admin [page]` 顯示管理分類，每頁最多五項，先按權限篩選再分頁。
- 使用完整路徑 `/ic help admin invitation` 查看分類，再以 `/ic help admin invitation transfer` 查看完整參數、用途與範例。不提供 `/ic help invitation` 簡寫。
- `confirm`／`cancel` 不列在首頁與一般說明；claim 互動情境保留必要提示，可明確查詢 `/ic help confirm`／`ic help cancel`。Tab 可依權限探索這些明確命令。
- `/ic reload` 是主要操作寫法，`/ic admin reload` 保留別名；help 統一在 `/ic help admin reload` 查閱。
- 所有 help 路徑與可用頁碼提供權限分級 Tab；實際操作參數只在正確位置提示玩家名稱、backend 或資格 profile ID。UUID、邀請碼、原因、完整指令尾端與未知／超長路徑回傳空集合。
- help 標題、分類、詳細文案、導航與錯誤均由雙語 messages 設定。完整回覆只顯示一次 prefix；未授權或不存在的頁面不洩漏操作參數。
- `/ic status` 逐欄顯示可登記、邀請關係、待補發獎勵；無法登記時列出真實原因。
- `/ic stats` 逐欄顯示等待達成資格與已達成資格人數；不新增排行榜、金額或猜測的百分比。
- 「建立關係」「達成資格」「交付成功」分開敘述；資格通知不等於所有獎勵已入帳。
- 管理查詢沿用必要識別符、狀態與診斷資料，不藉文案修改變更資料查詢／管理操作語意。

## 配置與復位

- [繁中完整草案](command-feedback-messages-zh-TW.draft.yml) 與 [英文完整草案](command-feedback-messages-en-US.draft.yml) 具有相同鍵、變數與互動語意。prefix、文案及顏色放各語系 messages，語系選擇仍在 config.yml。
- 多行使用 YAML `|-` 字串；只有 prefix 可為空，不將其他必填鍵改成可任意空白。
- 模板樣式與動態文字分開處理，動態值不再解析成格式指令；複製值與 click payload 不含 prefix 或色碼。
- 保留既有語系選擇規則；缺鍵可用同語系內建預設作備援並提示管理員，不改寫管理員的 YAML。
- 啟動與 reload 載入前，從 JAR 補回白名單中缺少的初始設定檔；已有檔案不覆寫。損壞、無權限、無法讀取時明確報錯；reload 保留最後有效設定。
- 補回 storage.yml 不代表可在 reload 直接切換儲存；繼續沿用既有切換與重啟限制。資料庫與 anti-abuse key 不屬於設定檔復位，不重建現有資料或金鑰。
- 保留 JAR 的 `config/` 資源結構與目前執行期輸出位置。不只因文案更新調高 messages-version；任何格式不相容須另外確認版本處理。

## 後續驗證與範圍

實作後需要自動回歸：全部訊息鍵／變數與語系備援、prefix 次數及空白、樣式解析與動態文字隔離、每條 help 與 parser 一致、權限及 console 篩選、無參數尾端 tab 空集合、缺檔復位而不覆寫、reload 失敗不切換，以及 copy／hover／click payload 保留。

平台複驗涵蓋舊版聊天、新版 Paper／Folia Dialog 和 console。真人驗收核對實際置中、字型、顏色與互動；HTML 模擬和靜態檢查不能代替。新 JAR 的證據另綁版本，不改寫 F205 既有驗收。

規格已通過，第一至三階段的實作與 Agent 檢查分別記錄於證據文件；第四階段已開始，2026-10-18 依真人回饋加入分層 help。真實網路／回呼不代替客戶端渲染與真人外觀，最新 Folia 的缺口另列。關服例外維持暫緩，沒有隔離測試。Human 更新依本輪授權進行，證據另記；沒有 push、PR 或發行。


## 2026-10-18 Help 分隔與固定導航

所有成功的 help 首頁、分類、分頁與詳細頁均使用同一行標題：灰色分隔線、語系 prefix、金色說明名稱／頁碼。
prefix 不再單獨佔一行、不重複；仍由 `presentation.prefix` 自訂，空值維持停用。
內容後加灰色結尾線，底部固定依序顯示上一頁、返回、下一頁。可用按鈕黃色、有唯讀 help 點擊與 hover；
停用按鈕深灰色，沒有 click 或 hover 事件，按下不做任何事。首頁三項均停用；首／末頁按實際可用性停用，
詳細頁只保留返回可用。Console 使用相同順序與狀態，可用項目另顯示導航命令，顏色仍遵守可讀 16 色。
模板使用 `runtime.help-frame-header`（`{prefix}`／`{title}`）、`help-frame-divider`、
`help-navigation-enabled`／`help-navigation-disabled`（`{text}`）與 `help-navigation-hover`；
沿用三項標籤設定。動態文字不重新解析樣式，模板不能自訂點擊目標。


2026-10-18 補充：首頁 `/ic help` 與 `/ic help admin` 都是唯讀導覽，點擊直接開啟；
`code`／`claim`／`status`／`stats` 等操作仍只填入聊天列，不自動執行。


## 2026-10-18 Help 框線長度與返回頁規則

所有新舊 help 頁共用框線版面：以可見文字的字型寬度估算補長較短框線，兼顧窄拉丁字、中文及粗體；
不靠空格位移標題。既有 messages 框線是最小長度，最多補 256 個線字元。
這是預設字型的近似，不保證資源包、Unicode 字型設定、GUI scale、窄聊天視窗下的像素對齊；
長標題可延長底線，無法避免客戶端換行時仍需真人確認。
返回目標由共同 help 樹與目前權限篩選後的分頁決定，回到包含該項目的上層頁；
例如全權限的 storage／reload／doctor 返回管理索引 2/2，而只見 doctor 的管理員返回 1/1。
分類頁、詳細頁與未來新增命令都套用相同規則，不為個別指令寫死頁碼；
這是可重建的上層導覽，不記錄跨命令的瀏覽器式歷史。


## 2026-10-18 Console 與逐欄報告規格

Console 不把多行文字一次塞入 sendMessage；每個邏輯行獨立送給伺服器，時間戳與 INFO 由核心產生，
不自行偽造。一般回覆與 doctor／inspect 每行加一次語系 prefix，維持各段顏色；
已成區塊的 help 仍只在標題出現 prefix，內容各行獨立輸出以保留真實 log 標頭。
玩家 doctor／inspect 使用一次標題 prefix 加逐欄內容，不在各行重複。
Console 首頁只列 help 入口，help 與 help admin 都開共用管理索引，標題改為「後台指令說明」。
不新增權限或第二份路由清單。玩家仍區分一般／管理說明。
Console 導航只列實際可輸入的上一頁／返回／下一頁命令，不顯示不可點擊的按鈕或停用項目，省略前導斜線。
玩家三項固定導航、停用無事件、返回所在父頁與框線共用規則保留。

面向使用者的繁中／英文分號句改為真正換行。doctor、inspect 與離線工具多表數量、匿名化影響範圍
改成一行一個欄位；玩家無法登記的多項原因逐項換行。離線工具不偽造伺服器 timestamp／INFO。
動態玩家輸入、原因、UUID、SQL／migration 檔案、程式語法與註解不作盲目分號替換。
核心與 storage 的本輪變更經去除字串後比對，僅訊息文字改變，沒有狀態、schema、備份或交易行為變更。
原 value.reason-separator 保留相容鍵；狀態畫面的多原因目前固定逐行顯示。

## 2026-10-29 健康檢查語系與語意配色

`/ic admin doctor` 的欄位與狀態值共用 messages 語系選擇，Console 使用設定預設語言；
玩家依 `use-client-locale` 決定是否使用客戶端語言。Paper、SQLite、MySQL、JSON、PlaceholderAPI、
Vault 等名稱保留。布林改成符合欄位的已啟用／未啟用、可用／不可用、支援／不支援，正值綠、反值紅；
整合未啟用不代表插件故障。

運作中與接受新工作為綠；啟動／停止中為黃、維護中為金、已停止／不接受新工作為灰。
獎勵數量 0 為灰；正數待執行為黃、執行中為青、結果待確認為金。未知值以灰色保留原始診斷值。
顏色只由可信語系模板提供，動態值不重新解析格式。Console 沿用黑底可讀的 legacy 16 色輸出且不額外粗體。
語系鍵使用 `admin.doctor-value.*`；玩家報告標題 prefix 一次，Console 每行一次並由核心提供 log 標頭。

新增可自訂語系鍵 runtime.help-console-home-header、runtime.help-topic.console、
runtime.help-console-navigation-title、admin.inspect-*-title 及 admin.inspect-* 報告模板。
既有檔案通常不自動覆寫；本輪獲准更新 Folia 時先備份再部署完整雙語文案，其他核心不部署。
