# InviteCodeKt

InviteCodeKt 是從零重寫的 Kotlin Minecraft 邀請與推薦獎勵插件。目標是以單一可安裝 JAR 支援 Spigot 與 Paper 1.8.8 到最新已測試版本，並透過隔離的 adapter 載入新版能力。

> [!IMPORTANT]
> 這個 repository 目前是 `0.1.0-SNAPSHOT`。平台無關的 Invitation 核心狀態機、Invite Code lifecycle 與 SQLite adapter 已實作；qualification、reward 與 Minecraft 平台工作流程尚未完成串接。

## 專案目標

- 透過 Invite Code 建立一筆永久的 Inviter 與 Invitee 關係。
- 將建立 Invitation 與達成獎勵資格分成不同階段。
- 支援可設定的 Qualification Policy 與可復原的 Reward Delivery。
- 支援 SQLite 與 MySQL；JSON 僅用於小型安裝環境與資料交換。
- 在不使用 NMS 的前提下維持 Java 8 執行環境相容性。
- 在有能力的新版伺服器使用原生 Dialog 輸入，其餘版本安全退回聊天或指令輸入。

標準領域詞彙記錄於 [`CONTEXT.md`](./CONTEXT.md)，已確認的產品與工程範圍記錄於 [`docs/spec/invitecode-1.0.md`](./docs/spec/invitecode-1.0.md)。

## 建置

需求：

- 已安裝 [mise](https://mise.jdx.dev/)
- 第一次解析 dependency 時需要網路連線

本機開發透過 `mise.toml` 使用已登錄的 `java@local-11`。先確認 mise 可解析工具與實際 JDK：

```powershell
mise where java
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
invitecode-plugin/build/libs/InviteCodeKt-0.1.0-SNAPSHOT.jar
```

所有本機 Java、Gradle 建置與測試指令都必須透過 `mise exec` 執行。Gradle Wrapper 仍由 repository 管理。建置流程會驗證目前 production class 使用 Java 8 bytecode。未來的新版 adapter 只有在 class 維持隔離，並驗證舊版 JVM 不會載入時，才可使用新版 bytecode。

## SQLite schema 與 thread 規則

`invitecode-storage-sql` 使用 `org.xerial:sqlite-jdbc:3.53.4.0`，driver 與主要平台的原生程式庫會封裝進單一插件 JAR。SQLite adapter 提供下列保證：

- schema 只會由 `SqliteSchemaMigrator` 依版本順序明確遷移；開啟 storage 不會暗中修改 schema。
- migration 在 transaction 內執行；失敗時回復原 schema version，並保留既有資料庫供檢查與重試。
- Claim 使用 SQLite write transaction，且資料庫以 unique constraint 保證一名 Invitee 最多只有一筆 Invitation。
- 所有 SQLite 入口都要求 `BlockingOperationGuard`；Minecraft adapter 必須用它拒絕 server main thread，並在專用 executor 呼叫同步 storage interface。
- storage 錯誤會拒絕當次操作，不會自動切換成 memory、JSON 或其他 backend。

目前 SQLite persistence 已有暫存資料庫 contract tests；插件啟動、設定載入與 executor 串接會在 Bukkit 工作流程 Issue 完成。

## 分支

- `main`：穩定發行基線與 GitHub 預設分支
- `develop`：日常整合分支
- task branch：使用獨立 Worktree，目標分支為 `develop`

## 目前狀態

實作工作記錄於本 repository 的 GitHub Issues。不規劃相容或匯入舊版 InviteCode 的資料與設定。
