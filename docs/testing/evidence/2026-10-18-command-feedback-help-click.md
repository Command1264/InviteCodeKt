# 首頁 help 直接開啟：Folia 局部更新

候選 `656f416df8a7042e42286acef2d5e4f0b100be66`。已重現舊版首頁 help 只填入的回歸測試失敗，修正後
Java 11／8 `mise exec [java@local-8] -- .\gradlew.bat check shadowJar --console=plain` 通過。
293 passed／24 MySQL 條件測試 skipped；沒有新增依賴、schema 或管理操作。
完整 diff 已核對：首頁只有 HELP 改為唯讀導航，目標 `/ic help`；另四項仍只填入，權限不變。

只更新 Folia 26.1.2 port 30101，正常停止與 drained 退出紀錄確認後持管理鎖備份，再更新。
備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\help-click-20261018-081003-d6a0244b`；啟動 run `24d10a1a33fa4f3e9d241c57541678fb`，當次 Done／ACTIVE 與 console probes 通過。
資料庫各表及金鑰一致，部署期間世界 header／設定／權限保留，沒有 reset／unlink。
Spigot 1.8.8 與 Paper 26.3 均停止，未部署新候選、未啟動。
SHA-256 `4D6E9B8BB5E36D7CD2FE6337899008232F7E2E1E871275A867FDE2D52772D1EA`。

使用者回饋多層選單大致正常，屬局部外觀回饋，不是完整矩陣驗收。
下一步只需依 [最新 Folia 步驟](../command-feedback-human-acceptance.md) 點首頁 help 複驗。
新一輪玩家 click 尚待真人確認；沒有 push、PR 或 merge。

後續玩家導向管理操作只保存本機草案於 `D:/Code/Java/Plugin/InviteCodeKt/notes/admin-player-command-discovery-draft.md`。
待目前 help 分支完成後才評估新分支；沒有提前實作或建立 Issue。
