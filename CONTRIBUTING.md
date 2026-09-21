# 貢獻方式

InviteCodeKt 目前透過有 GitHub issue 依據的 task branch 進行開發。

1. 閱讀 `CONTEXT.md`、`docs/adr/` 下的相關 ADR，以及本次實作對應的 issue。
2. 從目前的 `develop` HEAD 建立 branch，並使用獨立 Worktree。
3. 不要讓 `invitecode-core` 依賴 Bukkit 或任何 storage 實作。
4. 新版伺服器 API reference 只能放在 `invitecode-platform-modern`。
5. 為改變的行為新增有意義的回歸測試。
6. 送交 review 前執行 `mise exec -- .\gradlew.bat check shadowJar`；Java 8 相容性檢查使用 README 指定的 `java@local-8` override。

領域概念一律使用 `CONTEXT.md` 的標準詞彙。除非未來已接受的決策明確改變此項非目標，否則不得加入舊版遷移行為。
