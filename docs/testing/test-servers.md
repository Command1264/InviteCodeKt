# InviteCode 真實測試伺服器

InviteCode 使用專案外的 `D:\Code\Java\Plugin\InstanceHarbor` 共用伺服器核心快取與工具，但每個 profile、Worktree 與驗收版本都建立獨立實例。World、插件設定、SQLite、log 與其他可變狀態不跨實例共用。

訊息顯示矩陣的現行 C# 管理器操作、鎖定核心／JDK、Agent 隔離及檢查結果見 [2026-10-18 第三階段證據](evidence/2026-10-18-command-feedback-stage3.md)。下方 PowerShell runner 是歷史流程；本輪沒有直接沿用其啟停或舊訊息比對，也沒有把可攜式管理器的 C# 入口退回為舊產品腳本。渲染客戶端與真人外觀驗收仍分開處理。

第四階段的[兩帳號真人步驟](command-feedback-human-acceptance.md)已更新為可直接開始的版本；
2026-10-18 已依使用者授權備份、部署並啟動三台，完成 B 資格重設與 Folia 指定舊列 unlink。
世界、歷史與稽核保留；實際核心／JDK、候選 hash、當次 run 與備份見[部署證據](evidence/2026-10-18-command-feedback-stage4-deployment.md)。
Spigot 的資格測試不寫 LuckPerms JSON，A 須在 B 最後同意前回 Lobby。
部署前的[唯讀 preflight](evidence/2026-10-18-command-feedback-stage4-preflight.json)保留為歷史基線，不代表目前仍未部署。

## 測試矩陣

正式矩陣定義於 `test-servers/matrix.json`：

| Profile | 用途 | 發行 gate |
| --- | --- | --- |
| `spigot-1.8.8` | 最低支援邊界 | 是 |
| `spigot-1.12.2` | 舊版中間邊界 | 是 |
| `spigot-1.16.5` | Java 8 世代中間版本 | 是 |
| `spigot-latest` | 最新 Spigot | 是 |
| `paper-1.20.6` | 新版 Paper 代表版本 | 是 |
| `paper-1.21.8` | 原生 Dialog 能力邊界 | 是 |
| `paper-latest` | 最新穩定 Paper | 是 |
| `folia-latest` | 實驗性 smoke | 否 |

Folia 測試失敗會阻止宣稱 Folia 相容，但不會單獨阻止 Spigot／Paper 發行。

## 環境檢查

```powershell
pwsh -NoProfile -File .\scripts\test-servers\validate.ps1
pwsh -NoProfile -File .\scripts\test-servers\doctor.ps1
```

平常使用 `D:\Code\Java\Plugin\InstanceHarbor\repo`，也可設定 `PLUGIN_INSTANCEHARBOR_HOME` 或傳入 `-InstanceHarborRoot` 指向核對過的 repository。舊 `-TestLabRoot` 參數仍作為 alias；測試實例一律建立在 `instances/Agent/InviteCode/`，不使用 Human 或舊版實例。

## 建立測試實例

先建置受測 JAR：

```powershell
mise exec -- .\gradlew.bat clean check shadowJar
```

建立實例但不代替使用者接受 Minecraft EULA：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile paper-latest `
  -Artifact .\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar
```

使用者確認接受 EULA 後，以 `-AcceptEula -Replace` 重建同一實例：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile paper-latest `
  -Artifact .\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar `
  -AcceptEula `
  -Replace
```

啟動：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\start.ps1 -Profile paper-latest
```

啟動腳本依 profile 透過 mise 選擇 Java，不依賴系統 `PATH` 的 Java 優先順序。人工測試時應在可見的 PowerShell 7 視窗執行，並在 server console 輸入 `stop` 正常關閉。

## 每個 profile 的最低 smoke

已準備、記錄 EULA 接受狀態的隔離實例，可使用無玩家 smoke runner：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\smoke.ps1 `
  -Profile spigot-1.8.8 -Instance issue12-spigot-188
```

Runner 核對實體 Agent 路徑、`workspace.json` schema 2／audience、profile、EULA、loopback／offline mode、未被監聽的 port、核心與插件 JAR SHA-256；等待插件啟用後從 Console 執行 `ic admin doctor`、`ic help` 與 `ic reload`，確認 `ACTIVE`、整合狀態、儲存版本、指令說明與重新載入成功，正常送出 `stop`，檢查嚴格 UTF-8 log，並輸出 JSON 結果。預設 backend 為 SQLite；對已明確設定 `backend: JSON` 的實例傳入 `-Backend JSON`，對 `backend: MYSQL` 的實例傳入 `-Backend MYSQL`。SQLite／JSON 會檢查本機儲存檔與 anti-abuse key；MySQL 要求測試程序環境有 `INVITECODE_DB_PASSWORD`、`INVITECODE_NETWORK_SECRET`，並確認沒有在插件資料夾建立本機 key。MySQL 的資料庫 schema／ledger 須另用該獨立服務的用戶端核對，Runner 對這兩個本機檔案雜湊輸出 `null`。它不會替使用者接受 EULA、建立資料庫、重建實例或模擬玩家。再次執行同一實例可比對重啟前後的資料與 key SHA-256；MySQL 改核對 ledger、表與稽核紀錄。完整發行 gate 仍須依下列清單補足 critical user path 與外部整合情境。

預設預期 PlaceholderAPI 與 Vault Economy 都不存在。已在獨立實例安裝 PlaceholderAPI 時加上 `-ExpectPlaceholderApi`；已安裝 Vault 與經濟提供者時加上 `-ExpectVaultEconomy`。Runner 會核對 Console doctor 的實際整合狀態，輸出對應布林值；這只驗證服務偵測，不代表實際 placeholder 展開或經濟入帳已通過。

1. 確認 InviteCode 啟用且沒有 linkage error、legacy material warning 或未處理 exception。
2. 確認所選 backend 首次啟動自動建立必要資料與 anti-abuse key（MySQL 使用外部密鑰），重新啟動後沿用既有資料。
3. 玩家與 Console 驗證 `/ic help`、`/ic reload`、Tab completion 與權限拒絕。
4. 玩家驗證 Invite Code 顯示、Claim、Confirmation、Status 與 Stats。
5. 驗證 `zh_TW`／`en_US`、UTF-8 與 client locale fallback。
6. 驗證離線 Reward 補發與 restart recovery；需要外部整合的 profile 再加入 Vault／PlaceholderAPI。

各次測試應記錄 `workspace.json`、受測 commit、JAR SHA-256、操作結果與 `logs/latest.log`。實際相容性證據尚未取得前，不得只因實例能建立就宣稱該版本受到支援。

## 無真人的合成玩家流程

`flow.cjs` 的伺服器啟動等待預設為 180 秒，正常停止等待為 30 秒；可在測試程序設定 `INVITECODE_SERVER_START_TIMEOUT_MS`／`INVITECODE_SERVER_STOP_TIMEOUT_MS`，兩者只接受 100–600000 的整數毫秒。首次啟動超時也會清理自己啟動的程序；正常 `stop` 失敗時驗證程序樹終止結果並回報失敗，不把強制清理算成流程通過。這些環境變數只影響測試 runner，不改變插件設定。

本機 Windows 已準備 InstanceHarbor 及固定 Node 依賴時，可執行 `node --test .\scripts\test-servers\synthetic-player\instance-boundary.test.cjs .\scripts\test-servers\synthetic-player\startup-cleanup.test.cjs`。後者透過 runner CLI 與假的外部 Java 程序驗證啟動期限、正常停止及停止失敗的清理，不啟動 Minecraft；只建立自己的唯一 Agent fixture，保留證據至 Worktree `invitecode-plugin/build/current-artifact/startup-cleanup-fixtures/` 後移除該精確葉目錄，不累積假測服。真實核心與玩家結果仍須另外驗證，見[最新產物紀錄](./evidence/2026-10-02-issue-12-current-artifact-retest.md)。

Issue #12 的測試專用 fixture 以 Mineflayer 建立兩名離線模式的本機合成玩家，透過正式玩家指令與 Console doctor 驗證 Invite Code、Claim Confirmation、Qualification、離線 Reward 跨重啟補發與 Stats。先用 `npm.cmd ci --ignore-scripts --prefix .\scripts\test-servers\synthetic-player` 安裝固定版本的測試依賴；這些套件不會封裝進插件 JAR。

使用同一份待驗證的插件 JAR，為每次執行建立全新的 `issue12-*` InstanceHarbor instance。例如：

```powershell
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
pwsh -NoProfile -File .\scripts\test-servers\prepare-synthetic-player.ps1 `
  -Profile spigot-1.8.8 -Instance issue12-example-spigot-188 `
  -Artifact $jar -AcceptEula
$env:MISE_EXE = (Get-Command mise -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\flow.cjs `
  'D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\issue12-example-spigot-188'
```

`-AcceptEula` 只能在使用者已接受 Minecraft EULA 的測試環境使用。Fixture 支援 Spigot 1.8.8、Paper 1.20.6、1.21.8、`paper-latest` 設為 26.2 時的獨立測試客戶端，以及 `folia-latest` 設為 26.1.2 時的實驗性測試；同一測試埠的實例須依序執行。準備腳本把測試實例設定為 `en_US`、`COMMAND_ONLY`、同 IP `ALLOW`，並以登入 1 天即符合資格的政策觸發預設 `MESSAGE` Reward。這些都是隔離測試設定，產品預設不變。Runner 要求資料庫與 key 尚不存在，完成後正常關閉自己啟動的伺服器；再次測試須使用新實例名稱。輸出 `PASS` 只證明這些指令與訊息路徑，不涵蓋原生 Dialog 視覺、Vault 金額或中斷於外部效果期間的 `UNCERTAIN` 復原。

驗證聊天輸入與 `cancel` 時，可在**全新**實例首次啟動前把 `input-mode` 改成 `CHAT`，並設定 `INVITECODE_CHAT_INPUT=1` 執行同一 runner。它會先傳入大小寫混用的 `CaNcEl`，確認沒有建立 Invitation，再重新輸入邀請碼；同時確認複製入口與確認選項的文字已送達。元件的 ClickEvent 指令與舊版複製退回方式由單元測試檢查；剪貼簿和實際點擊效果仍須真人驗收。

Folia 26.1.2 的實驗性 fixture 使用目前 InstanceHarbor `repo` 的 `folia-latest` profile；建立後仍須核對 `workspace.json` 的實際版本。與其他版本一樣，先安裝 `scripts/test-servers/synthetic-player/` 的固定測試依賴，並為每次測試選全新 `issue12-*` 實例：

```powershell
$lab = 'D:\Code\Java\Plugin\InstanceHarbor\repo'
$name = 'issue12-folia-example'
pwsh -NoProfile -File .\scripts\test-servers\prepare-synthetic-player.ps1 `
  -Profile folia-latest -Instance $name `
  -Artifact .\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar `
  -InstanceHarborRoot $lab -AcceptEula
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\flow.cjs `
  "D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\$name"
```

跨 region 驗證須在**另一個全新實例首次啟動前**將其 `config/paper-global.yml` 的 `threaded-regions.threads` 設為 `2`，再設定 `$env:INVITECODE_FOLIA_MULTI_REGION = '1'` 執行同一 runner；它會把兩名合成玩家送往相距約兩萬格的區域，並行執行 `/ic status`。私密聊天驗證也使用新實例，將其 `plugins/InviteCode/config.yml` 設為 `input-mode: CHAT`，並設定 `$env:INVITECODE_FOLIA_CHAT = '1'`；原生 Dialog 畫面仍需後續人工驗收。完整實例、JAR 雜湊與結果見 [Issue #12 證據](./evidence/2026-09-24-issue-12-startup-matrix.md)。

Paper 26.2 的玩家測試使用[獨立維護的 Mineflayer 26.2](https://github.com/Complexity-ML/mineflayer-26.2)，它並非 [PrismarineJS 官方 Mineflayer](https://github.com/PrismarineJS/mineflayer/blob/master/docs/README.md)。它只存在於 `scripts/test-servers/synthetic-player-26-2/` 的測試依賴，JAR 不會封裝它；安裝時停用 npm scripts，並僅連線至 EULA 已接受的 loopback／offline InstanceHarbor。`package-lock.json` 固定下載內容。先安裝，再建立**全新**實例：

```powershell
npm.cmd ci --ignore-scripts --prefix .\scripts\test-servers\synthetic-player-26-2
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
$name = 'issue12-flow-paper-262-example'
pwsh -NoProfile -File .\scripts\test-servers\prepare-synthetic-player.ps1 `
  -Profile paper-latest -Instance $name -Artifact $jar -AcceptEula
$env:INVITECODE_SYNTHETIC_CLIENT_262 = (Resolve-Path `
  .\scripts\test-servers\synthetic-player-26-2\node_modules\mineflayer).Path
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\flow.cjs `
  "D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\$name"
```

Runner 會驗證 26.2 客戶端版本、InstanceHarbor metadata、EULA、loopback、離線模式與受測 JAR 的 SHA-256。這個獨立客戶端讓本機 26.2 玩家流程可測，但不代表官方 Mineflayer 已宣告支援，也不能代替最後的真人視覺與互動驗收。

### Reward 外部效果中斷

在**全新的** Spigot 1.8.8 隔離實例上，`-RewardInterruption` 會以本機 `mise java@local-8` 編譯測試專用 `InviteCodeFaultProbe.jar`，並把 Invitee 的 Reward 設為 `CONSOLE_COMMAND: icfault`。此指令先將效果進入標記同步寫入自己的資料夾，第一次呼叫隨即以 `Runtime.halt(83)` 強制終止測試 JVM；只有同一實例的管理員明確 `retry`，第二次呼叫才會正常返回。它絕不可放到正式伺服器。需要 Python 3 的標準 `sqlite3` 模組，僅用於找出測試 Action UUID；狀態斷言透過 Console `doctor` 與 `inspect reward`。

```powershell
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
$name = 'issue12-uncertain-example-188'
pwsh -NoProfile -File .\scripts\test-servers\prepare-synthetic-player.ps1 `
  -Profile spigot-1.8.8 -Instance $name -Artifact $jar -AcceptEula -RewardInterruption
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\uncertain.cjs `
  "D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\$name" mark-delivered
```

`mark-delivered` 與 `retry` 各使用不同的新實例，勿重用上例的 `$name`；`MISE_EXE` 指向當前電腦實際安裝的 mise 執行檔。Runner 會檢查 loopback、EULA、JAR SHA-256 與全新資料庫，並只停止自己啟動的伺服器。第一次故障造成測試客戶端 `ECONNRESET` 是預期現象。重啟後 `UNCERTAIN=1`、重新登入不重發；`mark-delivered` 後標記仍只有一次，`retry` 後才增加至兩次並轉為 `DELIVERED`。這是隔離演練，不代表能證明外部系統中的所有實際效果。

### SQLite 遷移失敗與復原

`migration-recovery.cjs` 使用全新的 Spigot 1.8.8／SQLite 隔離實例。它先讓插件正常初始化，再於停機時用 Python 3 標準 `sqlite3` 建立測試用 v7 schema 與哨兵玩家資料，並以同名索引阻擋 v8 遷移的第二個 DDL。Runner 檢查重啟不自動升級、管理指令失敗後交易回滾、哨兵資料與 `anti-abuse.key` 保留；清除測試衝突後，透過 Console 明確遷移到 v9（包含匿名化所需的邀請碼保留表），再重啟驗證。此 fixture 會直接修改**自己的隔離資料庫**，不得指向正式伺服器或既有實例。

```powershell
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
$name = 'issue12-migration-example-188'
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile spigot-1.8.8 -Instance $name -Artifact $jar -AcceptEula
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\migration-recovery.cjs `
  "D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\$name"
```

上例只適用於已接受 Minecraft EULA 的本機 InstanceHarbor。每次執行須換用尚不存在的新實例名稱，受測 JAR 的雜湊須與 `workspace.json` 相同。此測試驗證 SQLite 在伺服器層級的遷移回滾與復原，不涵蓋 MySQL DDL 中斷續建或正式資料搬移。

### MySQL 初始化中斷後續建

`mysql-migration-recovery.cjs` 在全新 Paper 1.20.6 InstanceHarbor 實例及專用 `invitecode_issue12_test_*` database 建立合成 `APPLYING` ledger、一張已提交的玩家表與哨兵資料。先把玩家表欄位改壞，驗證插件進入維護模式且未補建；修正欄位後再注入錯誤 checksum，驗證同樣拒絕；最後恢復 checksum，驗證啟動自動續建、ledger 轉為 `APPLIED`、玩家資料保留，且重啟不重複初始化。這是從已提交 DDL 邊界重建的**合成中斷狀態**，並非在正式建表過程中強制終止 JVM。

先啟動 InstanceHarbor 隔離的 MySQL 8.4.11 服務，使其只在 `127.0.0.1:63384` 接受連線；測試程序須有 `INVITECODE_TEST_MYSQL_ROOT_PASSWORD`、`INVITECODE_DB_PASSWORD`、`INVITECODE_NETWORK_SECRET`，均從 InstanceHarbor 使用者專屬加密暫存還原至目前程序環境，不寫入 repository 或命令列。測試器只接受 InstanceHarbor 中尚未啟動過的 `issue12-*` Paper 1.20.6 實例，驗證 EULA、loopback、JAR 雜湊與固定埠後，才建立新 database；拒絕覆寫既有 database。MySQL root 密碼不傳給 Paper 子程序。測試結束後關閉自己啟動的 Paper，再由測試操作者正常關閉隔離 MySQL 服務；保留測試 database 作為證據。

```powershell
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
$name = 'issue12-mysql-recover-example-1206'
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile paper-1.20.6 -Instance $name -Artifact $jar -AcceptEula
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
node .\scripts\test-servers\synthetic-player\mysql-migration-recovery.cjs `
  "D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\$name"
```

執行上例前須已接受 Minecraft EULA，且上述三個環境變數與隔離 MySQL 服務均已準備妥當。每次複驗使用新的實例名稱，以免重用有資料的 database。測試目前使用受控本機 `tls-mode: REQUIRED`，不證明正式預設 `VERIFY_IDENTITY` 的憑證驗證。

### Vault 與 PlaceholderAPI 的實際路徑

`prepare-integration-player.ps1` 以全新 `issue12-*` 實例測試已取得的外部插件 JAR。Vault 情境使用 Paper 1.21.8、Vault、EssentialsX 與測試專用唯讀餘額指令；Placeholder 情境使用 Paper 1.20.6、PlaceholderAPI 與測試專用 `%icprobe_ready%` 擴充。兩種 probe 都只在 loopback、離線模式及 `instances/Agent/InviteCode/issue12-*` 實體路徑內啟用，不會封裝進 InviteCode JAR。編譯 probe 需要既有 Spigot 1.8.8 `server.jar` 作為 Bukkit API classpath，並以 `mise java@local-21` 產生 Java 8 bytecode。

```powershell
$jar = (Resolve-Path '.\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar').Path
$api = 'D:\Code\Java\Plugin\BuildTools\Server\spigot-1.8.8.jar'
$env:MISE_EXE = (Get-Command mise.exe -CommandType Application | Select-Object -First 1).Source
pwsh -NoProfile -File .\scripts\test-servers\prepare-integration-player.ps1 `
  -Scenario Vault -Instance issue12-vault-example-1218 -Artifact $jar -SpigotApiJar $api `
  -VaultJar 'D:\Code\Java\Plugin\InstanceHarbor\instances\InviteCode\issue12-vault-212-paper-1218\plugins\Vault.jar' `
  -EconomyJar 'D:\Code\Java\Plugin\InstanceHarbor\instances\InviteCode\issue12-vault-212-paper-1218\plugins\EssentialsX-2.21.2.jar' -AcceptEula
node .\scripts\test-servers\synthetic-player\integration.cjs `
  'D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\issue12-vault-example-1218' Vault

pwsh -NoProfile -File .\scripts\test-servers\prepare-integration-player.ps1 `
  -Scenario Placeholder -Instance issue12-papi-example-1206 -Artifact $jar -SpigotApiJar $api `
  -PlaceholderApiJar 'D:\Code\Java\Plugin\InstanceHarbor\instances\InviteCode\issue12-papi-paper-1206\plugins\PlaceholderAPI.jar' -AcceptEula
node .\scripts\test-servers\synthetic-player\integration.cjs `
  'D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\issue12-papi-example-1206' Placeholder
```

只在已接受 Minecraft EULA 的測試環境使用 `-AcceptEula`。Vault 測試要求 Invitee 的 Vault 餘額從 0 變為 37.5，重啟並重新登入後仍為 37.5；Placeholder 測試要求切換前 Invitation 保持待資格、切換後重新登入才達資格並收到 Reward，重啟後維持已達資格。Paper 1.20.6 上 Mineflayer／protodef 可能輸出非致命封包解析警告，應與伺服器及 InviteCode log 分開判讀。再次執行須改用新實例名稱。

Placeholder fixture 另可傳入 `-PlaceholderProfile folia-latest` 與指向 Folia 26.1.2 的 `-InstanceHarborRoot`，再用相同 `integration.cjs ... Placeholder` 流程驗證。測試 probe 的玩家名稱快取與資格集合使用跨執行緒容器，並標記 `folia-supported: true`；Console 只切換測試值，實際 Placeholder 展開由 InviteCode 在玩家 entity scheduler 觸發。此結果僅涵蓋 PlaceholderAPI 2.12.3 與測試用 expansion。既有 Vault／EssentialsX 2.21.2 JAR 未宣告 Folia 支援，[EssentialsX 發行說明](https://github.com/EssentialsX/Essentials/releases)亦明確不支援 Folia，不能把 Paper 的存款測試推定為 Folia Vault 通過。

`flow.cjs` 在 Folia 26.1.2 或 Paper 26.2 的全新實例上可設 `INVITECODE_DIALOG_PROTOCOL=1`，並將實例的 `input-mode` 設為 `DIALOG`。Runner 驗證兩次輸入與一次確認的原生 `show_dialog` 封包；取消輸入後 Invitation 仍不存在，再以 command 完成 Claim／Reward 與重啟流程。合成客戶端無法證明畫面、原生按鈕點擊與輸入提交正確；這些步驟見[最終真人驗收](./final-human-acceptance.md)。

## 自動檢查與最終真人驗收

日常開發與各功能階段先由 Agent 執行不需要玩家判斷的檢查，包括 Gradle 測試、JAR 結構與 Java 8 bytecode、伺服器啟動與正常停機、log 錯誤掃描、UTF-8 解碼、SQLite／防濫用密鑰初始化，以及適用時的重啟持久化。這些結果須綁定受測 commit 與 JAR SHA-256。

需要真人觀察或實際玩家操作的項目集中到插件功能接近完成、準備發行候選版本時執行一次完整驗收，包括指令與 Tab completion、聊天及 Dialog 輸入、顏色與語系顯示、Claim／Confirmation／Status／Stats，以及實際獎勵體驗。延後的真人項目維持「待最終真人驗收」，不得因自動 smoke 通過而標記為已通過，也不阻擋仍在開發中的本機 checkpoint。

## 整合候選複驗

最新 develop 與 UX 分支整合後的同 JAR 複驗，見 [2026-10-02 複驗紀錄](./evidence/2026-10-02-issue-12-final-artifact-retest.md)。測試 runner 邊界可用 `pwsh -NoProfile -File scripts/test-servers/test-common.ps1` 與 `node --test scripts/test-servers/synthetic-player/instance-boundary.test.cjs` 驗證。
