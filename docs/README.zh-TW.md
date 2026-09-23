# InviteCode

本文件為 InviteCode 的主要繁體中文維護者說明；精簡英文介紹請見 [`README.md`](../README.md)。

InviteCode 是從零重寫的 Kotlin Minecraft 邀請與推薦獎勵插件；`InviteCodeKt` 是 repository 名稱，`Kt` 只表示實作使用 Kotlin。單一可安裝 JAR 支援 Spigot 與 Paper 1.8.8 到最新已測試版本，並以隔離 adapter 實驗性支援 Folia 26.1.2。

> [!IMPORTANT]
> 這個 repository 目前是 `0.1.0-SNAPSHOT`。舊版相容的 Bukkit 指令、聊天輸入、SQLite lifecycle、Qualification、可復原 Reward Delivery 與新版原生 Dialog adapter 已完成串接；Folia 26.1.2 的隔離合成玩家 smoke 也已通過。Folia 上的第三方整合與原生 Dialog 畫面、以及最終玩家體驗仍未完成驗收。

## 專案目標

- 透過 Invite Code 建立一筆永久的 Inviter 與 Invitee 關係。
- 將建立 Invitation 與達成獎勵資格分成不同階段。
- 支援可設定的 Qualification Policy 與可復原的 Reward Delivery。
- 已實作 SQLite 與 MySQL 8.4；JSON 可供小型單實例伺服器、測試與資料交換使用。
- 在不使用 NMS 的前提下維持 Java 8 執行環境相容性。
- 在有能力的新版伺服器使用原生 Dialog 輸入，其餘版本安全退回聊天或指令輸入。

標準領域詞彙記錄於 [`CONTEXT.md`](../CONTEXT.md)，已確認的產品與工程範圍記錄於 [`invitecode-1.0.md`](./spec/invitecode-1.0.md)。

## 建置

需求：

- 已安裝 [mise](https://mise.jdx.dev/)
- 第一次解析 dependency 時需要網路連線

本機開發透過 `mise.toml` 使用已登錄的 `java@local-11`。先確認 mise 可解析工具與實際 JDK：

```powershell
mise where java
mise ls --current java
mise ls java
mise exec -- java -version
mise exec -- .\gradlew.bat check shadowJar
```

驗證 Java 8 執行環境時，使用已登錄的 `java@local-8` 覆寫預設 JDK：

```powershell
mise exec java@local-8 -- .\gradlew.bat clean check shadowJar
```

`local-8` 與 `local-11` 由開發機的 mise 設定連結至既有 JDK。GitHub Actions 沒有這些本機 alias，因此 CI 透過 `MISE_JAVA_VERSION` 改用對應的 Temurin Java 8 與 Java 11。

可安裝的骨架 JAR 會輸出至：

```text
invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar
```

所有本機 Java、Gradle 建置與測試指令都必須透過 `mise exec` 執行。Gradle Wrapper 仍由 repository 管理。建置流程會驗證目前 production class 使用 Java 8 bytecode。未來的新版 adapter 只有在 class 維持隔離，並驗證舊版 JVM 不會載入時，才可使用新版 bytecode。

## SQLite schema 與 thread 規則

`invitecode-storage-sql` 使用 `org.xerial:sqlite-jdbc:3.53.4.0`，driver 與主要平台的原生程式庫會封裝進單一插件 JAR。SQLite adapter 提供下列保證：

- schema 只會由 `SqliteSchemaMigrator` 依版本順序明確遷移；開啟 storage 不會暗中修改 schema。
- migration 在 transaction 內執行；失敗時回復原 schema version，並保留既有資料庫供檢查與重試；rollback 本身失敗時會回報狀態不確定，且不再切回 auto-commit。
- Claim 使用 SQLite write transaction，且資料庫以 unique constraint 保證一名 Invitee 在每次 Claim Eligibility 世代最多只有一筆 Invitation；可稽核的 Eligibility Reset 會開始新世代並保留歷史資料。
- Reward Delivery 與其完整 Reward Action 集合只能從 `PENDING` 開始，並以單一 transaction 建立或更新；狀態不一致或任一 Action 寫入失敗時整批回復。Delivery 與 Action 的狀態轉換會在同一 transaction 追加不可覆寫的歷史，Pending Action 可依收件玩家於重新登入或重啟後找回。
- Reward Action 使用明確狀態轉換；重啟時會將中斷於 `PROCESSING` 的 Action 原子轉為 `UNCERTAIN`，之後只有記錄 actor 與 reason 的管理操作可決定重試或視為已交付。
- 初始 Invitation 只能以 `CLAIMED` 保存；一般 qualification 必須將 Qualified Invitation 與相符的 Qualification Facts 原子保存，管理員變更則使用可稽核轉換。Qualified Invitation 會同時保存穩定的 Qualification Policy ID 與 revision；Qualification Fact 為不可變稽核事實，只接受內容完全相同的冪等重送。
- 所有 SQLite 入口都要求 `BlockingOperationGuard`；Minecraft adapter 必須用它拒絕 server main thread，並在專用 executor 呼叫同步 storage interface。
- storage 錯誤會拒絕當次操作，不會自動切換成 memory、JSON 或其他 backend。

SQLite persistence 具備暫存資料庫 contract tests，插件只在專用 executor 執行 blocking I/O。全新安裝會自動建立 SQLite schema 與所需密鑰；既有資料庫的 schema 更新仍須使用明確的 `admin storage migrate`。完整操作方式請參閱 [`bukkit-workflow.md`](./operations/bukkit-workflow.md)。

MySQL 8.4 使用共用外部 HMAC 密鑰、連線池及具 checksum 與中斷復原檢查的 migration ledger。資料庫須先存在，全新的空白 database 會在插件啟動時自動建表；多個插件實例必須使用同一個密鑰。設定與復原方式請見 [`mysql-storage.md`](./operations/mysql-storage.md)。

JSON 以版本化、可閱讀的資料快照作為持久化來源，沿用 SQLite 的交易約束處理操作；每次寫入都要更新整份檔案，因此僅適合小型單實例環境。設定、備份與錯誤復原方式見 [`json-storage.md`](./operations/json-storage.md)。

Claim 防濫用只保存具期限的 HMAC-SHA-256 網路雜湊，不保存原始 IP；預設保留 30 天，並可設定相符時 `ALLOW`、`WARN` 或 `DENY`。SQLite 的 `anti-abuse.key` 由首次初始化建立，需隨 SQLite 備份且不得提交版本控制；MySQL 則使用所有實例共用的外部密鑰。

## Qualification Policy

`QualificationPolicy` 使用 OR group 包含 AND condition；每次解析都會依正規化內容產生不可變的 SHA-256 revision。`QualificationEvaluator` 對 condition 依序短路求值，provider 缺失或執行失敗時安全地產生 `UNKNOWN`／設定的 `UNSATISFIED`，不會因此讓 Invitation qualified。

內建 provider 涵蓋遊玩時間、Claim 後經過時間、不同登入日數、管理員核准、permission 與 Placeholder 比較。Permission 與 Placeholder 值由平台 adapter 注入，核心不直接依賴 Bukkit 或 PlaceholderAPI。Qualification 通過時，Qualified Invitation 與造成通過的 facts 透過 storage transaction 原子保存；一般 reevaluation 不會反轉既有 qualification。

設定格式、condition 參數與外部 provider 契約請參閱 [`qualifications.md`](./configuration/qualifications.md)。Bukkit workflow 會收集遊玩時間、不同登入日、Permission 與 PlaceholderAPI 值，並定期重新評估；外部插件可由 Bukkit ServicesManager 取得 `InviteCodeApi`。

## Reward Delivery

Qualification Reward 與 Inviter Milestone Reward 會先以穩定冪等鍵保存完整 Delivery／Action，再執行訊息、Console 指令、玩家指令或 Vault 經濟獎勵。玩家離線或 Vault integration 暫時不可用時維持 `PENDING`；外部結果無法判定時轉為 `UNCERTAIN`，只允許具稽核資訊的管理操作決定重試或視為已交付。

設定格式、placeholder、離線補發及失敗復原流程請參閱 [`rewards.md`](./configuration/rewards.md)。Bukkit workflow 已串接玩家登入補發、啟動復原、Vault Economy 與具 actor／reason 的人工處理指令。

## 分支

- `main`：穩定發行基線與 GitHub 預設分支
- `develop`：日常整合分支
- task branch：使用獨立 Worktree，目標分支為 `develop`

## 真實伺服器測試

專案的真實 Spigot／Paper／Folia 測試矩陣位於 [`test-servers/matrix.json`](../test-servers/matrix.json)，並使用專案外的共用 TestLab 建立隔離實例。操作方式與 EULA 邊界請參閱 [`test-servers.md`](./testing/test-servers.md)。

## 目前狀態

實作工作記錄於本 repository 的 GitHub Issues。不規劃相容或匯入舊版 InviteCode 的資料與設定。
