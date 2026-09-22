# InviteCodeKt Agent 指引

InviteCodeKt repository 開發的產品名稱是 InviteCode，它是跨 Minecraft 版本的邀請與推薦獎勵插件。修改領域行為前，先閱讀 `CONTEXT.md`、`docs/adr/` 下相關 ADR，以及授權該工作的 GitHub issue。

平台無關行為放在 `invitecode-core`。新版 Paper 或 Spigot API reference 放在 `invitecode-platform-modern`，不得引入 NMS。除非已接受的新 ADR 明確改變決策，否則必須保留既有的 storage、privacy、reward delivery 與 Java 8 相容性邊界。

## Agent Skills

### Issue tracker

Issue 與規格使用本 repository 的 GitHub Issues 管理。詳見 `docs/agents/issue-tracker.md`。

### Triage 標籤

使用預設的五角色 triage 詞彙。詳見 `docs/agents/triage-labels.md`。

### 領域文件

本 repository 只有一個 domain context，根目錄使用 `CONTEXT.md`，ADR 位於 `docs/adr/`。詳見 `docs/agents/domain.md`。

### Java 工具鏈

需要執行 Java、Gradle、建置或測試時，先閱讀 `README.md` 的「建置」章節。本專案由 `mise.toml` 管理 Java；本機指令一律透過 `mise exec` 執行，並繼續使用 repository 內的 Gradle Wrapper。
