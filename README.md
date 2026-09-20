# InviteCodeKt

InviteCodeKt 是從零重寫的 Kotlin Minecraft 邀請與推薦獎勵插件。目標是以單一可安裝 JAR 支援 Spigot 與 Paper 1.8.8 到最新已測試版本，並透過隔離的 adapter 載入新版能力。

> [!IMPORTANT]
> 這個 repository 目前是 `0.1.0-SNAPSHOT` 專案骨架。Invitation、qualification、storage 與 reward 工作流程尚未實作。

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

- 使用 JDK 8 以上版本執行 repository 內的 Gradle Wrapper；開發環境建議使用 JDK 11
- 第一次解析 dependency 時需要網路連線

```powershell
./gradlew check shadowJar
```

可安裝的骨架 JAR 會輸出至：

```text
invitecode-plugin/build/libs/InviteCodeKt-0.1.0-SNAPSHOT.jar
```

建置流程會驗證目前 production class 使用 Java 8 bytecode。未來的新版 adapter 只有在 class 維持隔離，並驗證舊版 JVM 不會載入時，才可使用新版 bytecode。

## 分支

- `main`：穩定發行基線與 GitHub 預設分支
- `develop`：日常整合分支
- task branch：使用獨立 Worktree，目標分支為 `develop`

## 目前狀態

實作工作記錄於本 repository 的 GitHub Issues。不規劃相容或匯入舊版 InviteCode 的資料與設定。
