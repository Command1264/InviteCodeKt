# Issue #12：聊天室互動 UX 修正與回歸

使用者完成 Paper 26.2、Spigot 1.8.8、Folia 26.1.2 第一輪真人功能測試後，回報主要流程大致正常，但需要可複製的邀請碼、可點擊的確認／取消選項，以及聊天輸入時的 `cancel`。排版與美術列為後續討論。本輪沿用 `codex/test/final-release-acceptance`，沒有修改既有三個真人測試實例或其資料。

受測產品 JAR：`InviteCode-0.1.0-SNAPSHOT.jar`，SHA-256 `B586955C62FAE5F09F284D94203D674D84E90E8BF8F6E7B6230C496466689813`。測試以 mise 選擇 Java 11 建置，再以各實例指定的 Java 8 或 25 執行伺服器。

`ClaimInteractionSessionsTest` 與 `PlayerChatActionsTest` 先於未實作時呈紅燈，實作後通過；`mise exec -- .\gradlew.bat check shadowJar --console=plain` 通過，包含 Java 8 bytecode 與插件 JAR 結構檢查。互動選項以 Spigot 1.8.8 的 Bungee 聊天 API 編譯：舊版 `SUGGEST_COMMAND` 把原始碼填入聊天列；有 `COPY_TO_CLIPBOARD` 的新版 runtime 則選用直接複製。單元測試確認舊版動作及紅綠按鈕的 `RUN_COMMAND` 目標，真人剪貼簿與點擊仍需驗收。

| 隔離實例 | 合成玩家結果 |
| --- | --- |
| `issue12-ux-spigot-188` | `PASS`：複製入口文字、聊天 `CaNcEl` 私密取消後 Invitation 為 none、再次 Claim、確認選項文字、資格、重啟及離線 Reward |
| `issue12-ux-paper-262` | `PASS`：複製入口與確認選項文字、Claim、資格、重啟及離線 Reward；有先前已知的 Windows OSHI／Perflib 雜訊，未見 InviteCode exception |
| `issue12-ux-folia-2612` | `PASS`：複製入口文字、聊天 `CaNcEl` 私密取消後 Invitation 為 none、再次 Claim、確認選項文字、資格、重啟及離線 Reward |

三個合成實例使用新建 loopback／offline TestLab、同一 JAR 雜湊；runner 已停止啟動的伺服器。另建立三個空白的第二輪真人驗收實例 `issue12-ux-human-paper-262`、`issue12-ux-human-spigot-188`、`issue12-ux-human-folia-2612`，JAR 雜湊相同，尚未啟動。操作與待驗收項目見[最終真人驗收](../final-human-acceptance.md)。合成玩家只驗證元件文字已到達，沒有實際點擊剪貼簿或按鈕，故 Issue #12 的 UX 驗收仍未完成。
