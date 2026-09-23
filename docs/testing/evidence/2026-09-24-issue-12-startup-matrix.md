# Issue #12 跨版本啟動矩陣與整合驗證

- 受測基線：`develop` merge commit `3d64431`；唯一插件 JAR SHA-256：`99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`。
- 本機建置：`mise exec -- .\gradlew.bat check shadowJar` 通過。首輪沒有本機 MySQL service；本輪另建立完全隔離的 MySQL 8.4.11 服務並重跑相關整合測試，結果見下文。
- 每個 profile 使用 TestLab 的獨立 `issue12-*` 實例、預設 SQLite、對應 mise Java。首次啟動與一次重啟都經 `smoke.ps1` 從 Console 執行 `ic admin doctor`，取得 `Runtime=ACTIVE`、儲存版本、正常 `stop`，且兩次停機後的 SQLite DB 與 anti-abuse key SHA-256 各自一致。再以同一 runner 對七個實例驗證 Console `ic help`、`ic reload` 均回傳預期訊息。所有七個受測實例的嚴格 UTF-8 log 檢查通過，沒有 InviteCode linkage／event 錯誤；測試後沒有殘留的矩陣 port listener。

| Profile | 核心／build | Java | 啟動與重啟 |
| --- | --- | --- | --- |
| `spigot-1.8.8` | Spigot 1.8.8／local BuildTools | `local-8` | 通過 |
| `spigot-1.12.2` | Spigot 1.12.2／local BuildTools | `local-8` | 通過 |
| `spigot-1.16.5` | Spigot 1.16.5／local BuildTools | `local-8` | 通過 |
| `spigot-latest` | Spigot 26.2／local BuildTools | `local-25` | 通過 |
| `paper-1.20.6` | Paper 1.20.6 build 151 | `local-21` | 通過 |
| `paper-1.21.8` | Paper 1.21.8 build 60 | `local-21` | 通過 |
| `paper-latest` | Paper 26.2 build 128 | `local-25` | 通過 |
| `folia-latest` | Folia 26.1.2 stable build 8，僅於 TestLab 本機 task Worktree 修正 profile 後 | `local-25` | 不通過：Folia 在載入階段拒絕插件 |

另外以同一份 JAR 在 `paper-1.20.6` 的獨立 `issue12-json-paper-1206` 實例設為 `backend: JSON`，首次啟動與重啟均回報 `ACTIVE` 與 JSON 儲存版本 1；兩次正常停機後，`invitecode.json` 和 `anti-abuse.key` 的 SHA-256 各自一致。Console 的 `ic help` 與 `ic reload` 也通過。這是代表性 JSON 真實伺服器整合，並未測試 JSON 的玩家負載或跨 backend 搬移。

## 外部整合的有／無情境

`smoke.ps1` 已加入整合狀態預期值檢查；不傳 switch 時要求 doctor 回報 PlaceholderAPI 與 Vault Economy 均為 `false`。先前七個純淨實例符合此條件，另於 Spigot 1.8.8 以新檢查重跑一次通過。

| 情境 | 獨立實例與受測外掛 | 結果 |
| --- | --- | --- |
| PlaceholderAPI 存在 | `issue12-papi-paper-1206`，Paper 1.20.6、PlaceholderAPI 2.12.3；外掛 JAR SHA-256 `FDE03259F5AF6938F3C33EEB4D814000A1ADABF1D2304CE14970BE81F609A437` | 首次啟動與重啟均 `PlaceholderAPI=true`、`Vault Economy=false`；SQLite DB 與 key 雜湊不變 |
| Vault Economy 存在 | `issue12-vault-212-paper-1218`，Paper 1.21.8、Vault 1.7.3-b131、EssentialsX 2.21.2；兩個 JAR SHA-256 分別為 `A6B5ED97F43A5CF5BBAF00A7C8CD23C5AFC9BD003F849875AF8B36E6CF77D01D`、`0B759026F02F3C547C32886F3666DB3C1F94CFF73E141AE5648313FD0D0BDFC6` | 首次啟動與重啟均 `Vault Economy=true`、`PlaceholderAPI=false`；Vault log 顯示 Essentials Economy hooked；SQLite DB 與 key 雜湊不變 |

也在 Paper 26.2 與 1.21.8 試過 EssentialsX 2.22.0：doctor 可偵測 Vault Economy，但 EssentialsX 自行警告該伺服器版本不受支援，因此不把它當作乾淨的正向驗證。上表只確認外掛載入與 InviteCode 的服務偵測；實際效果另見下節。

### 合成玩家功能整合

使用相同 InviteCode JAR 與前述已驗證的第三方 JAR，另外建立全新 SQLite TestLab 實例。測試專用 Vault probe 透過註冊的 Vault `Economy` 服務唯讀查詢餘額；測試專用 PlaceholderAPI expansion 回傳玩家專屬 `no`／`yes`，由 Console 指令切換。Probe JAR 不屬於產品產物。

| 情境 | 實例與版本 | 可觀察結果 |
| --- | --- | --- |
| Vault 實際存款 | `issue12-vault-flow4-1218`，Paper 1.21.8／Vault 1.7.3-b131／EssentialsX 2.21.2 | Invitee 餘額 `0 → 37.5`，Invitation 已達資格；重啟並再次登入後餘額仍為 `37.5`，Reward backlog `PENDING=1, PROCESSING=0, UNCERTAIN=0`；通過 |
| PlaceholderAPI 實際資格 | `issue12-papi-flow5-1206`，Paper 1.20.6／PlaceholderAPI 2.12.3 | `%icprobe_ready%` 為 `no` 時 Invitation 待資格；切為 `yes` 後重新登入變成已達資格並收到 Invitee Reward，重啟後仍已達資格；通過 |

Vault 的 `PENDING=1` 是離線 Inviter 的預設 `MESSAGE` Reward，並非存款卡住。Placeholder 的 `value` 須寫為 `"yes"`；首次試跑用未加引號的 `yes` 被 YAML 當作布林值，因而與 PlaceholderAPI 回傳文字不符，改成字串後通過。Paper 1.20.6 的 Mineflayer／protodef 仍出現既知非致命封包解析警告，伺服器端沒有對應的 InviteCode exception。兩個較早實例在玩家離線後立刻停機時出現 Paper 區塊生成執行緒錯誤，堆疊未涉及 InviteCode；測試器在首次停機前等待 10 秒後，新建的上述兩個實例沒有再出現此錯誤。這只能說明目前測試流程較穩定，不能斷定 Paper 錯誤的根因。此驗證覆蓋一個 Vault provider 與一個受控 Placeholder expansion，不代表所有第三方 provider 都相容。

## 復原測試的現況

既有自動測試含 SQLite migration 失敗後的 rollback 與版本／資料保留、rollback 自身失敗時回報不確定狀態、Reward Action 在資料庫關閉後重開的 pending／interrupted recovery，以及 `UNCERTAIN` 只允許稽核後由管理員處理。核心測試也涵蓋外部 effect 執行後最終保存失敗時不自動重做。`mise exec -- .\gradlew.bat :invitecode-core:test :invitecode-storage-sql:test --rerun-tasks --no-daemon` 通過，兩個 `test` task 均實際執行。這些是注入故障的程式層級證據，尚未在真實 Minecraft 伺服器上完成 Reward crash／restart 演練。

## MySQL 8.4 代表性伺服器整合

從 [Oracle 官方 MySQL 下載來源](https://dev.mysql.com/downloads/mysql/)取得 Windows ZIP，使用的 `mysql-8.4.11-winx64.zip` SHA-256 為 `A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C`。ZIP 解壓與資料目錄都在 TestLab 的 `artifacts/invitecode-issue12-mysql-8.4.11/`，與 `C:\Program Files\MySQL` 的既有資料完全分離。`mysqld --version` 為 8.4.11；服務只綁定 `127.0.0.1:63384`，使用隨機測試密碼、獨立 `invitecode_issue12` database 和專用 `invitecode_test` 帳號。測試密碼與 network secret 以目前 Windows 使用者的加密 CLIXML 暫存於該隔離 runtime，未寫入 repository、設定範本或 log。測試設定採 `tls-mode: REQUIRED`，因此未驗證正式預設的 `VERIFY_IDENTITY` 憑證身分檢查。

在此服務上強制重跑 `mise exec -- .\gradlew.bat :invitecode-storage-sql:test :invitecode-plugin:test --rerun-tasks --no-daemon`：storage SQL 51 個、plugin 17 個測試，皆無失敗或略過。當時測試所建的隨機 database 都已清理，除 MySQL 系統資料庫外只剩 `invitecode_issue12`；後續另保留兩個專用的中斷復原測試 database，見下文。

`issue12-mysql-paper-1206` 使用 Paper 1.20.6 build 151 與同一份受測插件 JAR，`storage.yml` 指向上述隔離服務。首次啟動回報 `Runtime=ACTIVE`、`MYSQL 儲存版本=1`、自動初始化；從 Console 執行 `ic admin doctor`、`ic help`、`ic reload` 均成功。正常停機後資料庫有 13 張表、migration ledger `version=1,state=APPLIED`、初始化稽核紀錄 1 筆。以相同密碼與 network secret 重啟後仍為 `ACTIVE`，上述表數、ledger 與稽核筆數維持不變；插件資料夾沒有產生本機 `anti-abuse.key`。兩次 Paper log 沒有 InviteCode error／exception。測試後以 `mysqladmin shutdown` 關閉本次 MySQL 服務，63384 與 Paper 的 62060 埠都已釋放。此項驗證首次建表與再開啟，尚未涵蓋真實玩家 Claim；合成中斷狀態的伺服器層級續建另見下節。

Runner 擴充後，再於 Paper 1.20.6 各重跑一次既有 SQLite 與 JSON 實例；`ACTIVE`、Console 指令、本機儲存檔與 key 檢查均通過，確認新增 MYSQL 分支沒有破壞原本兩種 backend 的 smoke 路徑。

## 合成玩家 Claim 與離線 Reward

測試專用官方 Mineflayer 4.39.0 fixture 使用固定 `package-lock.json`，`npm ci --ignore-scripts` 成功、當時 `npm audit` 回報 0 個已知漏洞，用於前三個版本。Paper 26.2 另用[獨立維護的 26.2 客戶端](https://github.com/Complexity-ML/mineflayer-26.2) `4.37.1+complexity.26.2.3`；其測試專用 lockfile 與官方版分開，`npm ci --ignore-scripts` 成功，`npm audit` 回報 6 個 moderate、0 個 high／critical，測試只連線 loopback／offline 伺服器。兩套測試都從正式玩家指令與 Console doctor 觀察結果，使用相同插件 JAR SHA-256 `99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`，每個版本各建立全新 SQLite TestLab 實例。測試設定限定英文指令訊息、`COMMAND_ONLY`、同 IP `ALLOW` 與 `LOGIN_DAYS at-least: 1`；本輪驗證的是預設 `MESSAGE` Reward，而非 Vault 發錢。

| Profile | 實例 | 合成玩家流程 |
| --- | --- | --- |
| Spigot 1.8.8 | `issue12-fixture-spigot-188` | 通過 |
| Paper 1.20.6 | `issue12-fixture-paper-1206` | 通過；Mineflayer／protodef 在登入時另有非致命封包解析警告 |
| Paper 1.21.8 | `issue12-fixture-paper-1218` | 通過 |
| Paper 26.2 build 129 | `issue12-flow-paper-262-1`、`issue12-flow-paper-262-2` | 兩個全新實例均通過；第二次使用 repository 固定的獨立客戶端 lockfile |

每次流程由 A 執行 `/ic code` 後離線，B 執行 `/ic claim <code>`、`/ic confirm`，收到 Invitation 已建立及 Qualification Reward 訊息。Console doctor 回報 `PENDING=1, PROCESSING=0, UNCERTAIN=0`；正常關機並重啟後仍為 `PENDING=1`。A 再登入收到離線補發訊息，doctor 變為 `PENDING=0`，`/ic stats` 顯示 `qualified=1`。以上驗證真實伺服器的玩家指令與訊息路徑；外部效果期間的非正常終止另見下節。Paper 1.20.6 的封包警告出自測試客戶端 `protodef`，伺服器端沒有 InviteCode error；該版本的其他客戶端封包相容性不能由此次流程推定。

## Reward 中斷與管理員決定

相同插件 JAR 在 Spigot 1.8.8、Java 8 的兩個全新隔離實例執行測試專用 `CONSOLE_COMMAND` Reward。`InviteCodeFaultProbe` 先 `fsync` 自己的效果進入標記，再以 exit code 83 強制中止 JVM；這是明確注入的預期故障，客戶端同時產生 `ECONNRESET`。重啟時 InviteCode log 均回報復原 1 筆 Action，Console doctor 為 `PENDING=1, PROCESSING=0, UNCERTAIN=1`，`admin inspect reward` 顯示 `status=UNCERTAIN`。Invitee 再登入後，標記仍只有 1 筆，證明此測試 Action 沒有被自動重做。

| 實例 | 管理員決定 | 結果 |
| --- | --- | --- |
| `issue12-uncertain-mark4-188` | `admin reward mark-delivered <UUID> isolated-test` | Action 轉為 `DELIVERED`，標記仍 1 筆，doctor 的 `UNCERTAIN=0`；再次重啟後狀態與標記不變，通過 |
| `issue12-uncertain-retry2-188` | `admin reward retry <UUID> isolated-test` | 收件者在線時，Action 再執行一次並轉為 `DELIVERED`，標記成 2 筆；再次重啟後狀態與標記不變，通過 |

UUID 由測試程式透過 Python 標準 `sqlite3` 唯讀查得，斷言仍透過公開管理指令。兩實例測完均正常停止重啟後的伺服器，測試用 JVM 中斷沒有影響正式資料。測試演練刻意讓外部效果與最後狀態保存之間斷開；不證明 Vault、其他插件或非同步命令效果可 exactly-once。

[Mineflayer 官方文件](https://github.com/PrismarineJS/mineflayer/blob/master/docs/README.md)目前列出的支援版本到 26.1；本地官方 Mineflayer 4.39.0 對 `26.2` 直接回報 `unsupported protocol version: 26.2`。本輪改用上述獨立客戶端後，兩個 Paper 26.2 build 129 實例都完成 Code、Claim、Confirm、Qualification、重啟後離線 Reward 補發與 Stats；Console doctor 的 `PENDING=1 → 0` 也符合預期。這是實際 26.2 伺服器玩家路徑證據，不代表上游 Mineflayer 已支援該版本，也不涵蓋真人客戶端的 Dialog 顯示或視覺體驗。

[PaperMC Folia 下載頁](https://papermc.io/downloads/folia)當時提供 26.1.2 stable build 8，將 26.2 標示為 experimental；TestLab `develop` 的 `folia-latest` 固定為 26.2 並只選 stable，因此初次無法建立實例。於 TestLab 獨立本機 `codex/fix/folia-stable-profile` Worktree 將 profile 指向 26.1.2 後，TestLab self-test 與 doctor 通過，實例可建立。Folia log 隨即顯示 `Could not load plugin 'InviteCode v0.1.0-SNAPSHOT' as it is not marked as supporting Folia!`；插件在載入前遭拒，尚未進入程式相容性測試。[PaperMC 的 Folia 開發文件](https://docs.papermc.io/paper/dev/folia-support/)也明確指出，加入支援標記仍不足以保證相容，須使用正確的 scheduler。目前程式仍呼叫傳統 `server.scheduler`，不能為通過 smoke 而直接在正式 JAR 宣告 Folia 支援，必須先驗證 scheduler 與事件處理是否符合 Folia 邊界。Folia 26.1.2 在這台主機上還讓 Console `/stop` 拋出伺服器內部 `NullPointerException`；runner 等待 30 秒後終止自己建立的程序樹，63620 port 已釋放。此項不與 InviteCode 載入失敗混為一談。

### Folia 程式路徑稽核（2026-09-24）

這是原始碼與先前 Folia 載入 log 的稽核，**不是**可載入後的 smoke。依 [PaperMC 的 scheduler 分工](https://docs.papermc.io/paper/dev/folia-support/#schedulers)，全域工作與玩家 entity 工作需要分別排程；不應把原本單一「server main thread」假設直接搬到 Folia。找到以下阻礙：

| 路徑 | 現況 | Folia 前置工作 |
| --- | --- | --- |
| `InviteCodePlugin` 啟動與每分鐘資格掃描 | `AsyncOperationExecutor` 的所有完成 callback 都交給 `server.scheduler.runTask`；`runTaskTimer` 在同一工作中遍歷玩家，讀取其遊玩時間、權限與 Placeholder 資料 | 將不涉及玩家的 callback 與逐玩家工作分開；玩家資料只在所屬 entity 排程取得，並處理離線／移動後失效 |
| `PlayerWorkflowController` 聊天、Dialog 與 timeout | `runTask`、`runTaskLater` 回到傳統主執行緒，再以 UUID 找玩家、操作 session 或發訊息 | 把玩家互動導回其 entity scheduler；timeout 與斷線競爭需重新驗證 |
| Reward delivery | `BukkitRewardExecutionEnvironment` 以 `Bukkit.isPrimaryThread()` 作為所有 Reward 的共同前提，且同一路徑執行 Console command、Player command、訊息與 Vault deposit | 先定義各 Action 的執行位置；Console 與玩家效果不可假定由同一個執行緒安全完成，保留既有 `PROCESSING`／`UNCERTAIN` 語意 |
| 管理指令及外部 API | 背景完成 callback 可能對玩家 sender 回訊，管理指令亦可依 UUID 直接讀取玩家；API 重評估讀取玩家 Statistic 與 qualification inputs | 依 sender／玩家歸屬派送，避免在全域 callback 讀寫遠端 region 的玩家 |

因此本輪不修改 `plugin.yml` 的 `folia-supported` 欄位，也不以測試用 JAR 強行宣告相容來冒充有效 smoke。若另行實作 Folia 支援，先設計 Java 8／Spigot 1.8 可載入的 scheduler 邊界與玩家離線時 callback 處理，再對 Console、玩家 Claim／Chat／Dialog、資格掃描、離線 Reward 與 Vault／Placeholder 整合建立跨 region 測試；最後才加入支援標記並在隔離 TestLab 重跑 Folia smoke。未完成前 Folia 保持**未支援、實驗性驗證未通過**，不阻擋已通過的 Spigot／Paper 路徑，但 Issue #12 的 Folia 項目仍未完成。

Spigot／Paper 26.2 的 log 有 Windows Perflib／OSHI／JNA 讀取系統計數器失敗及衍生的 system-report `NullPointerException`；堆疊未涉及 InviteCode，伺服器與插件仍可啟動、診斷並正常停機。這是測試主機／伺服器層級的已知雜訊，不能用它替代插件無錯誤的判定。

## SQLite 遷移故障與復原（伺服器層級）

新增 `migration-recovery.cjs`，在全新 `issue12-migration-rollback1-188` 實例使用 Spigot 1.8.8、`local-8` 與同一份產品 JAR（SHA-256 `99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`）。初始化到 SQLite v8 並正常停機後，測試程式只在此隔離資料庫建立合成 v7 fixture：保留舊資料、加入 `MigrationSentinel` 玩家，並於既有 `invitecode_players` 表先建立 `invitecode_network_hash_expiry` 索引。這是為了讓插件 v8 遷移先成功 `CREATE TABLE`，再於 `CREATE INDEX` 發生名稱衝突。fixture 由測試程式建造，**不是**舊版外掛實際產生的資料庫，也不代表曾有正式使用者需要遷移。

第一次重啟時，Console doctor 回報 `Runtime=MAINTENANCE`、schema 7 不符，未自動遷移。第二次重啟後，Console `ic admin storage migrate SQLITE injected-index-conflict` 回報遷移失敗，log 的根因是 `index invitecode_network_hash_expiry already exists`。停機後直接核對：版本仍為 7、v8 新表不存在、原索引與哨兵資料存在，`PRAGMA integrity_check=ok`，`anti-abuse.key` SHA-256 未變；證明這次 SQLite 交易中的前一步建表已回滾。預期的錯誤只出現在注入故障階段。

停機移除測試索引後，第三次啟動仍在維護模式；Console 明確遷移成功至 v8，doctor 轉為 `Runtime=ACTIVE`。第四次啟動直接進入 `ACTIVE`，版本 8、新表及正確索引存在，哨兵資料、密鑰與 integrity check 均保持正常。Runner 回報 `PASS`；加入 TestLab 路徑與固定埠檢查後，使用另一個全新 `issue12-migration-rollback2-188` 實例複驗也回報 `PASS`。所有伺服器均由 runner 正常關閉，51888 埠已釋放。此測試涵蓋 SQLite 的伺服器層級安全拒絕、失敗回滾與明確復原；尚未涵蓋在遷移交易進行中強制終止 JVM，亦未涵蓋 MySQL DDL 中斷續建。

## MySQL 初始化中斷狀態的安全續建

新增 `mysql-migration-recovery.cjs`，使用全新 `issue12-mysql-recover1-1206`、`issue12-mysql-recover2-1206` 實例，以及各自命名的新 MySQL database。Paper 1.20.6 build 151、`local-21`、MySQL 8.4.11 與同一份產品 JAR（SHA-256 `99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`）。測試器從受測 JAR 讀取 schema 與 checksum，在全新 database 建立 `APPLYING` ledger、已提交的 `invitecode_players` 表及 `MigrationSentinel` 玩家；它模擬首次建表剛完成一張表後中斷，不會觸碰既有 `invitecode_issue12` database。

先將玩家名稱欄位改為 `VARCHAR(32)`：插件啟動後進入 `MAINTENANCE`，只保留 ledger 加玩家表兩張表、`APPLYING` 狀態和哨兵資料。修正回 `VARCHAR(64)` 後把 checksum 改錯：再次啟動仍進維護模式，沒有補建任何表。恢復正確 checksum 後，下一次普通啟動自動核對既有表並補齊至 13 張表；Console doctor 回報 `ACTIVE`，ledger 轉為 `APPLIED`，原哨兵資料與唯一一次初始化稽核保留。再次重啟仍為 `ACTIVE`，表數、ledger、稽核及哨兵資料不變。插件資料夾沒有建立本機 SQLite DB 或 `anti-abuse.key`。兩個獨立實例的 runner 均回報 `PASS`；第二次測試前，測試器已改為從 Paper 子程序環境移除 MySQL root 密碼。預期的兩次維護模式錯誤只出現在注入故障階段。

這是**合成的已提交 DDL 中斷狀態**，不是在 MySQL 實際執行 DDL 時殺掉 JVM；它證明產品在該持久化邊界的安全拒絕、續建與重啟持久性，不證明任意時間點的 crash 都能復原。測試採本機 `tls-mode: REQUIRED`，仍未驗證 `VERIFY_IDENTITY`。兩次 Paper 及隔離 MySQL 服務均已正常關閉，62060／63384 埠已釋放；專用 database 保留作為測試證據。

目前 Spigot／Paper 的啟動、SQLite／JSON／MySQL 代表性初始化、重啟一致性、Console 指令、外部服務偵測，以及 Spigot 1.8.8、Paper 1.20.6／1.21.8／26.2 的合成玩家 Claim／離線 `MESSAGE` Reward 已有證據。Spigot 1.8.8 通過 Reward 中斷後的 `UNCERTAIN` 復原與兩種管理員決定；Paper 1.20.6／1.21.8 分別通過 Placeholder 資格與 Vault 實際存款；SQLite 與 MySQL 也完成上述伺服器層級遷移復原測試。Folia 的程式路徑稽核已完成，但可載入後的實驗性 smoke 仍缺，且最終人工觀感驗收尚未執行；不得據此宣布 1.0 發行 gate 通過。
