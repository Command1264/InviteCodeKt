# Bukkit 操作與維護

本文件說明 InviteCode 在 Spigot／Paper 上的玩家流程、管理指令、儲存初始化、維護模式與人工驗收方式；Folia 26.1.2 已有固定產物自動證據與 2026-10-13 使用者驗收，仍維持實驗性，第三方整合限制見[發行門檻結論](../testing/issue-12-release-gates.md)。可選擇 `SQLITE`、`MYSQL` 或小型單實例環境使用的 `JSON`。JSON 與 MySQL 的設定及復原細節分別見 [`json-storage.md`](./json-storage.md) 與 [`mysql-storage.md`](./mysql-storage.md)。

## 首次啟動

插件的 Bukkit 名稱與資料夾均為 `InviteCode`。JAR 內的預設 YAML 集中在 `config/`，首次啟動時會輸出至 `plugins/InviteCode/`，不在資料夾中另外建立 `config/` 層級。設定選擇 SQLite 且資料庫檔案尚不存在時，第一次啟動會自動建立最新 schema 與 32-byte 的 `anti-abuse.key`，接著直接進入可用狀態，不要求管理員執行初始化指令。

`anti-abuse.key` 是 32 bytes 的隨機二進位 IP 防濫用 HMAC 密鑰，不是 ANSI、UTF-8 或其他文字編碼的文字檔；用文字編輯器開啟時顯示亂碼是正常現象，不得手動編輯。它必須像資料庫一樣備份並限制檔案讀取權限；不得提交 Git、貼到 Issue 或輸出至 log。後續啟動只讀取既有密鑰，不會自行取代。既有資料庫缺少密鑰、密鑰內容無效或 schema 版本不符時，插件會進入維護模式，不會把它當成全新安裝或自動升級。修復密鑰或執行明確 migration 後必須重啟；密鑰變更後，啟動時會清除由其他密鑰產生、已無法比較的舊雜湊。

`/invitecode admin storage migrate SQLITE <reason>` 保留給既有 SQLite schema 升級與人工復原。`doctor` 不會建立資料庫或執行 migration。

選擇 MySQL 時，管理員須先建立空白 database 並設定連線與共用防濫用密鑰的環境變數。插件首次啟動會建立 MySQL schema；既有 schema 不會自動升級。中斷於首次建表的 migration 只有在既有表結構通過檢查後才會續跑。明確遷移指令為 `/invitecode admin storage migrate MYSQL <reason>`。

維護模式只允許 `help`、`admin doctor` 與 `admin storage migrate`。設定錯誤須修正檔案並重新啟動；正常模式可用 `/invitecode reload` 驗證所有 YAML 後原子替換設定快照。`/invitecode admin reload` 保留為相容路徑。

## 玩家指令

### 訊息、prefix 與顯示設定

啟動及 `/ic reload` 載入設定前，都會從 JAR `config/` 白名單補回缺少的內建 YAML；已有檔案（包括損壞檔）不覆寫。復位不包含資料庫、`anti-abuse.key` 或任意自訂語系。寫入失敗或檔案無效會明確報錯；正常模式 reload 失敗仍保留上一份有效快照，storage 變更仍須遵守既有切換／重啟限制。

messages 查詢順序為玩家自訂語系、設定預設語系、對應內建語系、內建預設語系。只有使用內建備援時才回報缺鍵，同一快照的相同查詢語系／鍵不反覆警告；全部來源都缺鍵時仍顯示可辨識的 missing 訊息。

各語系使用 `presentation.prefix`（預設 `&7[&6InviteCode&7]&r`）。只有此鍵可為空字串，且必須為不帶首尾空白的單行文字。指令、help、status／stats、管理查詢及 MESSAGE 資格獎勵都經過共用輸出處理：一份完整回覆只加一次 prefix；純值、按鈕與 hover 不另加。一般回覆在同列自動補一個空格；聊天確認的 prefix 獨立一行，不留尾端空格。空 prefix 不產生空白行，Dialog 改用有意義的標題。

內建繁中／英文文案已套用。已有自訂 messages 不會自動改寫；要採用新文案，先備份自訂檔，再比對 JAR 的 `config/messages/` 調整，或在停服備份後移走指定語系檔，讓下次載入補回內建檔。不要移除資料庫或金鑰。缺鍵仍可使用內建備援。

受信任的模板支援 `&0`～`&f`、`&k`～`&o`、`&r` 及 `&#RRGGBB`；動態玩家名稱、原因、錯誤明細等以純值插入，不再次解析色碼或 `{變數}`。邀請碼的可點擊區域只涵蓋 `{code}` 本身，複製／舊版聊天填入的內容保持原始邀請碼。help 的操作用法點擊只填入聊天列；分類、分頁與返回可直接執行唯讀 help；用法、說明及玩家／管理分類按權限與發送者身分篩選。`runtime.help-row` 控制用法列與分隔文字，`runtime.help-description.*` 各自控制說明的樣式；說明不繼承外層用法列的粗體等格式。管理 inspect 的診斷欄位／識別符維持原文。

聊天確認依序顯示 prefix、標題、邀請人、邀請碼、問題、無冒號的期限提示、綠紅同意／不同意按鈕。聊天靠左，原生 Dialog 使用原生訊息區塊置中；不補空格、不做每秒刷新。Dialog 按鈕使用文字顏色，底色沿用客戶端原生外觀。資格已達成的通知不代表所有外部獎勵已送達，離線補發仍依原本 Reward 狀態處理。

Console 以標準黑底為基準篩選至少 4.5:1 對比的基本色；近黑／暗灰轉灰色，其餘暗色在可讀色中選最近值，移除額外粗體等裝飾。使用伺服器既有色碼輸出，不自行插入 ANSI 控制。這不保證自訂終端主題的外觀。第二階段的輸出及封裝測試與真實核心／客戶端驗收分開記錄，實際排版、色彩與點擊互動仍須後續矩陣及集中真人驗收。

詳細範圍見 [已核准顯示規格](../plans/command-feedback-display-spec.md) 與 [四階段計畫](../plans/command-feedback-implementation-plan.md)。

Console 可使用 `/invitecode`、`/invitecode help` 與管理指令。需要玩家身分與狀態的 `code`、`claim`、`confirm`、`cancel`、`status` 與 `stats` 會明確回覆只限玩家使用。

`/ic` 只顯示 `code`、`claim`、`status`、`stats`、`help`，有管理權限時增加 `/ic help admin` 入口。
`/ic help admin [page]` 每頁最多五個分類，使用 `/ic help admin invitation` 查看操作，再以
`/ic help admin invitation transfer` 查看完整參數與範例。不存在或未授權的頁面顯示一般錯誤。
`confirm`／`cancel` 保留情境提示與明確查詢 help，不列在首頁或一般說明。所有 help 子路徑及有效頁碼
提供權限篩選 Tab；資格 profile ID 在 force-qualify 的指定位置提示。UUID、Invite Code 與 reason 不猜值。

Tab 建議會依發送者身分、權限與當前參數位置篩選。已完成的指令、不明路徑、Invite Code、UUID、reason 與超出語法長度的位置會回傳空集合，不會退回顯示所有線上玩家。

| 指令 | 權限 | 行為 |
| --- | --- | --- |
| `/invitecode help` | `invitecode.command.basic` | 顯示基本操作。 |
| `/invitecode code` | `invitecode.command.code` | 建立或顯示自己的 Invite Code；建立後即成為 Inviter。 |
| `/invitecode claim <code>` | `invitecode.command.claim` | 驗證有效 Invite Code，顯示限時確認，不會直接建立 Invitation。 |
| `/invitecode claim` | `invitecode.command.claim` | `AUTO`／`DIALOG` 在具備能力的新版伺服器開啟原生 Dialog；無可用 adapter 或開啟失敗時退回私密聊天。`CHAT` 固定使用私密聊天，`COMMAND_ONLY` 顯示完整用法。 |
| `/invitecode confirm` | `invitecode.command.claim` | 在確認期限內原子建立 Invitation。 |
| `/invitecode cancel` | `invitecode.command.claim` | 取消目前輸入或確認。 |
| `/invitecode status` | `invitecode.command.status` | 顯示 Claim Eligibility、Invitation 狀態與待補發 Reward 數。 |
| `/invitecode stats` | `invitecode.command.stats` | 顯示等待資格與已達資格的邀請數。 |

聊天輸入會先取消公開聊天，再送入背景流程；原始訊息不會寫入 log。此時輸入 `cancel`（不分大小寫）也會私密取消，不會當作邀請碼解析。邀請碼訊息另外提供可點擊的複製入口：新版聊天 API 直接複製至剪貼簿，Spigot 1.8.8 則把原始碼填入聊天列，玩家可自行選取複製。確認摘要下方有綠色「同意」與紅色「不同意」，分別執行 `/invitecode confirm` 與 `/invitecode cancel`。Dialog 提交後沿用同一組格式、資格與限時確認流程；取消按鈕、`/invitecode cancel`、登出或逾時只會清除短期 Session，不會建立或修改 Invitation。輸入、確認、逾時、成功、取消、登出或停用插件後，短期 Session 都會失效。玩家一旦建立 Invite Code、已有 Invitation，或超過 Claim Window，就不能使用他人的 Invite Code。

玩家登入時，插件會以 SQLite 的 `anti-abuse.key` 或 MySQL 多實例共用的外部密鑰，對正規化網路位址執行 HMAC-SHA-256。資料庫只保存雜湊、密鑰識別碼與觀察／到期時間，絕不保存原始 IP。`anti-abuse.ip-hash-retention` 預設為 30 天；到期資料會在玩家登入時清除。邀請人與受邀人有尚未到期的相同雜湊時，`matching-ip-action` 的行為如下：

| 值 | 行為 |
| --- | --- |
| `ALLOW` | 不執行相符檢查，正常處理 Claim。 |
| `WARN` | 建立 Invitation，通知玩家並在 log 記錄雙方 UUID；不記錄 IP 或雜湊。 |
| `DENY` | 不建立 Invitation，通知玩家並在 log 記錄雙方 UUID；不記錄 IP 或雜湊。 |

`HEX_COLOR` 會產生六碼十六進位 Invite Code。支援 Bungee RGB API 的伺服器會以六碼所代表的精確 RGB 顯示；較舊版本使用最接近的 legacy color。`CROCKFORD_BASE32` 目前使用固定八碼。

原生 Dialog adapter 透過 runtime capability detection 隔離新版 API：Paper Dialog API 優先，Spigot Dialog API 次之，舊版不會載入這些型別。`/invitecode admin doctor` 會回報實際選用的 `Dialog=PAPER`、`Dialog=SPIGOT` 或 `Dialog=NONE`，以及 `RGB=true/false`。即使新版 API 簽章不相容或開啟 Dialog 失敗，插件也會保留 command 並退回聊天輸入。

## 管理指令

所有修改指令最後都要提供非空白 `reason`。玩家參照接受完整 UUID 或唯一的已知名稱；未知或重複名稱會拒絕操作。

```text
/invitecode admin inspect player <玩家>
/invitecode admin inspect invitation <Invitation UUID>
/invitecode admin inspect reward <Reward Action UUID>
/invitecode admin invitation unlink <Invitation UUID> <reason...>
/invitecode admin invitation transfer <Invitation UUID> <新 Inviter> <reason...>
/invitecode admin invitation force-qualify <Invitation UUID> <Policy ID> <reason...>
/invitecode admin eligibility reset <玩家> <reason...>
/invitecode admin reward retry <Reward Action UUID> <reason...>
/invitecode admin reward mark-delivered <Reward Action UUID> <reason...>
/invitecode admin code rotate <玩家> <reason...>
/invitecode admin storage migrate SQLITE <reason...>
/invitecode admin storage migrate MYSQL <reason...>
/invitecode reload
/invitecode admin doctor
```

`/invitecode admin reload` 仍可使用，但說明與 Tab 建議以較直接的 `/invitecode reload` 為主。

Invitation、Eligibility、Code Rotation 與 Storage Migration 會留下 actor、reason 與 Audit Record；Reward 的人工決定會留下 Reward transition。`retry` 與 `mark-delivered` 只接受 `UNCERTAIN` Action。Force Qualification 會在同一儲存 transaction 保存 Qualification、Audit Record 與完整 Reward plans。

## Qualification 與 Reward 復原

玩家登入、Claim 成功及每分鐘在線檢查會更新名稱、登入日與遊玩時間，然後重新評估仍為 `CLAIMED` 的 Invitation。第一次達成資格時，Qualified Invitation、Qualification Facts 與 Reward Delivery／Action 會原子保存。

玩家登入會補發自己的 `PENDING` Reward。玩家離線或 Vault Economy 不可用時維持 `PENDING`。插件啟動會先把上次中斷於 `PROCESSING` 的 Action 轉成 `UNCERTAIN`；這類外部結果不得自動重試，必須由管理員檢查後決定。

背景 executor 執行 SQLite／MySQL 與 Qualification／Reward orchestration。Spigot／Paper 的 Bukkit 工作回到主執行緒；Folia 的全域工作走 global scheduler，玩家互動與資格取值走玩家 entity scheduler。Vault 的 Folia provider 相容性仍須個別驗證。停用時會停止接受新工作並有界等待，不會讓背景執行緒同步等待伺服器工作執行緒。

## 人工驗收

1. 在全新測試伺服器安裝 JAR，確認首次啟動自動建立 SQLite 與 `anti-abuse.key`，並直接進入可用狀態。
2. 重啟伺服器，確認既有 SQLite 與 `anti-abuse.key` 直接開啟，且 schema 不會再次變更。舊 schema 拒絕自動升級的行為由合成資料庫自動化測試覆蓋；目前沒有既有使用者資料，因此不列為人工驗收要求。
3. 讓玩家 A 建立 Invite Code；確認 A 不能再 Claim。玩家 B 分別以 command、聊天與支援版本的 Dialog 輸入，確認聊天未公開，關閉或取消 Dialog 不改變 Invitation，且成功提交後仍必須再執行 `confirm` 才建立 Invitation。
4. 驗證自邀、錯碼、輪替後舊碼、逾時、重複 Claim 與過期 Claim Window 均被拒絕。
5. 讓兩名測試玩家從同一網路登入，分別驗證 `DENY` 拒絕、`WARN` 允許並提示、`ALLOW` 直接允許；確認資料庫與 log 均沒有原始 IP，log 也沒有網路雜湊。
6. 達成 Qualification 條件，確認 Invitation、Facts 與 Reward plans 出現；離線收件者重新登入後收到 Pending Reward。
7. 在測試用 Reward 外部效果期間強制停止伺服器，重啟後確認 Action 為 `UNCERTAIN`，再驗證兩個人工處理指令與 transition history。
8. 修改一份 YAML 為無效值後執行 reload，確認舊 snapshot 仍有效；恢復後再 reload。修改 `storage.yml` 時確認提示重新啟動／明確 cutover，而不熱切換。
9. 以一般玩家、無權限管理員、Console 分別驗證權限與玩家專用限制；切換 `zh_TW`／`en_US`，確認語系 fallback、Dialog 文字與缺鍵紀錄；確認六碼 HEX Invite Code 在新版 client 呈現精確 RGB，在舊版呈現最接近的 legacy color。

跨 Minecraft 版本的真實伺服器矩陣由 Issue #12 執行。


### Help 框線與導航顯示

help 以同列 prefix／標題的分隔線起始、內容後加結尾線，固定顯示 `[上一頁] [返回] [下一頁]`。
黃色可用，深灰停用；停用項目沒有點擊或 hover，首頁三項皆停用。
Console 可用項目顯示其導航指令。`runtime.help-frame-*`／`help-navigation-*` 控制格式與色彩，
`presentation.prefix` 仍可自訂或設空。頁面導覽為唯讀，真正操作用法仍只填入聊天列。


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

新增可自訂語系鍵 runtime.help-console-home-header、runtime.help-topic.console、
runtime.help-console-navigation-title、admin.inspect-*-title 及 admin.inspect-* 報告模板。
既有檔案通常不自動覆寫；本輪獲准更新 Folia 時先備份再部署完整雙語文案，其他核心不部署。


## 2026-10-29 Doctor 語系值與色彩

健康檢查的一般欄位與已知狀態透過 `admin.doctor-value.*` 翻譯，沿用 config 的預設／客戶端語系政策。選配整合未啟用顯示紅色說明，不等於插件健康失敗；未知代碼中性灰保留原值。0 獎勵數量灰色，正數待執行黃／執行中青／結果待確認金。Console 使用黑底可讀的 legacy 色與每行 prefix，玩家標題 prefix 一次。僅 Folia 更新，驗收及資料保留證據見 `../testing/evidence/2026-10-29-doctor-localization.json`。

## 查詢 JAR 來源

/ic admin doctor 顯示 Git 提交前 12 碼、封裝時工作目錄是否有變更，以及 UTC 建置時間。完整提交存於 JAR 的 META-INF/MANIFEST.MF。建置時間是封裝任務執行時寫入的時間；重用既有產物時沿用原時間。Git 不可用時顯示未知，舊 JAR 缺少欄位時也顯示未知。工作目錄變更包含 Git 看得到的未追蹤檔案。
