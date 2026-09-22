# Bukkit 操作與維護

本文件說明 InviteCode 在 Spigot／Paper 上的玩家流程、管理指令、SQLite 初始化、維護模式與人工驗收方式。Issue #10 使用 SQLite；選擇 `MYSQL` 或 `JSON` 會明確拒絕啟動，不會自動退回其他儲存方式。

## 首次啟動

插件的 Bukkit 名稱與資料夾均為 `InviteCode`。JAR 內的預設 YAML 集中在 `config/`，首次啟動時會輸出至 `plugins/InviteCode/`，不在資料夾中另外建立 `config/` 層級。設定選擇 SQLite 且資料庫檔案尚不存在時，第一次啟動會自動建立最新 schema 與 32-byte 的 `anti-abuse.key`，接著直接進入可用狀態，不要求管理員執行初始化指令。

`anti-abuse.key` 是 32 bytes 的隨機二進位 IP 防濫用 HMAC 密鑰，不是 ANSI、UTF-8 或其他文字編碼的文字檔；用文字編輯器開啟時顯示亂碼是正常現象，不得手動編輯。它必須像資料庫一樣備份並限制檔案讀取權限；不得提交 Git、貼到 Issue 或輸出至 log。後續啟動只讀取既有密鑰，不會自行取代。既有資料庫缺少密鑰、密鑰內容無效或 schema 版本不符時，插件會進入維護模式，不會把它當成全新安裝或自動升級。修復密鑰或執行明確 migration 後必須重啟；密鑰變更後，啟動時會清除由其他密鑰產生、已無法比較的舊雜湊。

`/invitecode admin storage migrate SQLITE <reason>` 保留給既有 SQLite schema 升級與人工復原。`doctor` 不會建立資料庫或執行 migration。

維護模式只允許 `help`、`admin doctor` 與 `admin storage migrate`。設定錯誤須修正檔案並重新啟動；正常模式可用 `/invitecode reload` 驗證所有 YAML 後原子替換設定快照。`/invitecode admin reload` 保留為相容路徑。

## 玩家指令

Console 可使用 `/invitecode`、`/invitecode help` 與管理指令。需要玩家身分與狀態的 `code`、`claim`、`confirm`、`cancel`、`status` 與 `stats` 會明確回覆只限玩家使用。

Tab 建議會依發送者身分、權限與當前參數位置篩選。已完成的指令、不明路徑、Invite Code、UUID、reason 與超出語法長度的位置會回傳空集合，不會退回顯示所有線上玩家。

| 指令 | 權限 | 行為 |
| --- | --- | --- |
| `/invitecode help` | `invitecode.command.basic` | 顯示基本操作。 |
| `/invitecode code` | `invitecode.command.code` | 建立或顯示自己的 Invite Code；建立後即成為 Inviter。 |
| `/invitecode claim <code>` | `invitecode.command.claim` | 驗證有效 Invite Code，顯示限時確認，不會直接建立 Invitation。 |
| `/invitecode claim` | `invitecode.command.claim` | `CHAT`／`AUTO` 時私密擷取下一則聊天；`COMMAND_ONLY` 顯示完整用法。 |
| `/invitecode confirm` | `invitecode.command.claim` | 在確認期限內原子建立 Invitation。 |
| `/invitecode cancel` | `invitecode.command.claim` | 取消目前輸入或確認。 |
| `/invitecode status` | `invitecode.command.status` | 顯示 Claim Eligibility、Invitation 狀態與待補發 Reward 數。 |
| `/invitecode stats` | `invitecode.command.stats` | 顯示等待資格與已達資格的邀請數。 |

聊天輸入會先取消公開聊天，再送入背景流程；原始訊息不會寫入 log。輸入、確認、逾時、成功、取消、登出或停用插件後，短期 Session 都會失效。玩家一旦建立 Invite Code、已有 Invitation，或超過 Claim Window，就不能使用他人的 Invite Code。

玩家登入時，插件會以 `anti-abuse.key` 對正規化網路位址執行 HMAC-SHA-256，SQLite 只保存雜湊、密鑰識別碼與觀察／到期時間，絕不保存原始 IP。`anti-abuse.ip-hash-retention` 預設為 30 天；到期資料會在玩家登入時清除。邀請人與受邀人有尚未到期的相同雜湊時，`matching-ip-action` 的行為如下：

| 值 | 行為 |
| --- | --- |
| `ALLOW` | 不執行相符檢查，正常處理 Claim。 |
| `WARN` | 建立 Invitation，通知玩家並在 log 記錄雙方 UUID；不記錄 IP 或雜湊。 |
| `DENY` | 不建立 Invitation，通知玩家並在 log 記錄雙方 UUID；不記錄 IP 或雜湊。 |

`HEX_COLOR` 會產生六碼十六進位 Invite Code，並以該 RGB 值最接近的舊版聊天色顯示。`CROCKFORD_BASE32` 目前使用固定八碼。新版原生 Dialog 屬於 Issue #11；本流程保留 command 與 chat fallback。

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
/invitecode reload
/invitecode admin doctor
```

`/invitecode admin reload` 仍可使用，但說明與 Tab 建議以較直接的 `/invitecode reload` 為主。

Invitation、Eligibility、Code Rotation 與 Storage Migration 會留下 actor、reason 與 Audit Record；Reward 的人工決定會留下 Reward transition。`retry` 與 `mark-delivered` 只接受 `UNCERTAIN` Action。Force Qualification 會在同一 SQLite transaction 保存 Qualification、Audit Record 與完整 Reward plans。

## Qualification 與 Reward 復原

玩家登入、Claim 成功及每分鐘在線檢查會更新名稱、登入日與遊玩時間，然後重新評估仍為 `CLAIMED` 的 Invitation。第一次達成資格時，Qualified Invitation、Qualification Facts 與 Reward Delivery／Action 會原子保存。

玩家登入會補發自己的 `PENDING` Reward。玩家離線或 Vault Economy 不可用時維持 `PENDING`。插件啟動會先把上次中斷於 `PROCESSING` 的 Action 轉成 `UNCERTAIN`；這類外部結果不得自動重試，必須由管理員檢查後決定。

背景 executor 執行 SQLite 與 Qualification／Reward orchestration；Bukkit、聊天、指令、訊息與 Vault 操作只在 server main thread 執行。停用時會停止接受新工作並有界等待，不會讓背景執行緒同步等待主執行緒。

## 人工驗收

1. 在全新測試伺服器安裝 JAR，確認首次啟動自動建立 SQLite 與 `anti-abuse.key`，並直接進入可用狀態。
2. 重啟伺服器，確認既有 SQLite 與 `anti-abuse.key` 直接開啟，且 schema 不會再次變更。舊 schema 拒絕自動升級的行為由合成資料庫自動化測試覆蓋；目前沒有既有使用者資料，因此不列為人工驗收要求。
3. 讓玩家 A 建立 Invite Code；確認 A 不能再 Claim。玩家 B 分別以 command 與聊天輸入，確認聊天未公開、必須再執行 `confirm` 才建立 Invitation。
4. 驗證自邀、錯碼、輪替後舊碼、逾時、重複 Claim 與過期 Claim Window 均被拒絕。
5. 讓兩名測試玩家從同一網路登入，分別驗證 `DENY` 拒絕、`WARN` 允許並提示、`ALLOW` 直接允許；確認資料庫與 log 均沒有原始 IP，log 也沒有網路雜湊。
6. 達成 Qualification 條件，確認 Invitation、Facts 與 Reward plans 出現；離線收件者重新登入後收到 Pending Reward。
7. 在測試用 Reward 外部效果期間強制停止伺服器，重啟後確認 Action 為 `UNCERTAIN`，再驗證兩個人工處理指令與 transition history。
8. 修改一份 YAML 為無效值後執行 reload，確認舊 snapshot 仍有效；恢復後再 reload。修改 `storage.yml` 時確認提示重新啟動／明確 cutover，而不熱切換。
9. 以一般玩家、無權限管理員、Console 分別驗證權限與玩家專用限制；切換 `zh_TW`／`en_US`，確認語系 fallback 與缺鍵紀錄。

跨 Minecraft 版本的真實伺服器矩陣由 Issue #12 執行。
