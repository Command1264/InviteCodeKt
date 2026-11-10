# Help 框線與固定導航部署證據

2026-10-18。程式候選 `fb303e23bb6acca4e6d97aa9686bc89475ff0166`，分支 `codex/feat/command-feedback-messages`。
Agent 驗證完成，待使用者驗收；沒有 push、PR、合併或發布。

## 驗證

新增測試先重現未加框線／導航不固定的失敗，再修正為通過。
Java 8 與 11 的 `mise exec [java@local-8] -- .\gradlew.bat check shadowJar --console=plain` 通過；
Java 8 bytecode／JAR、雙語鍵與變數、空 prefix／同列標題、全部固定導航狀態及 click／hover 檢查通過。
317 cases：293 passed、24 MySQL 條件測試 skipped，零失敗。
獨立唯讀 review 發現的空 prefix 多餘空格已修復及複驗，其餘無阻擋。

## 產物與部署

`InviteCode-0.1.0-SNAPSHOT.jar`，19,914,359 bytes。
SHA-256：`E8640904EA2963FB741C19A2037C9FD359DF85BAFF2B3F2F9A7EC1F591AE2EBA`。

- `invitecode-spigot-1.8.8`，port 10101，run `1ac82869c64f4e8c88bff92231e931c1`；備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-spigot-1.8.8\.invitecode-backups\help-frame-20261018-075438-411ad62a`。
- `invitecode-paper-26.3`，port 20102，run `ac48e2bb512041f395f7dd5431eabbda`；備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-paper-26.3\.invitecode-backups\help-frame-20261018-075439-aea591bb`。
- `invitecode-folia-26.1.2`，port 30101，run `fe2826bf8c5e4cc58af81244b93897dd`；備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\help-frame-20261018-075439-3905d088`。

三台在使用者已停止後，重新確認 Stopped、無 host、port 釋放，持管理鎖建立全新備份及逐檔 hash 核對。
只更新 JAR、雙語 messages 及 workspace 產物識別；設定、權限、DB/key 與部署期間 world level.dat 保持不變。
世界原位保留，備份不含完整世界。沒有 reset、unlink、更新核心或變更路由。
當次 Done／ACTIVE、console 框線／同列 prefix／三項固定導航、分頁及詳細用法實際輸出均通過，沒有新增錯誤；
重啟後各 SQLite 表仍與本輪備份相同且 quick_check=ok。完整資料見
[JSON](2026-10-18-command-feedback-help-frame-deployment.json)。

下一步依 [真人六項清單](../command-feedback-human-acceptance.md) 複驗三台。
Minecraft 字型、換行、色彩、停用點擊與 native console Tab 仍待真人；最新 Folia ALPHA 與既有 Paper 26.2
adapter 複驗缺口保留。關服 JLine 問題不處理、不做隔離測試。
真人通過後仍須完成原本未完流程、還原暫時設定／權限、保存證據，再另取得 remote 操作授權。

文件沿用同一離線 HTML 入口。anchor、JS 語法與 DOM stub 的標題／三項停用首頁／首末頁／返回
互動檢查通過；不是瀏覽器排版或 Minecraft 渲染證據。候選與必要證據約 20 MB，保留待驗收；
清單為 `.run/command-feedback-help-frame/manifest.json`，沒有新增核心或完整測試環境。
實際 Java 程序核對：Spigot 使用本機 8.0_501，Paper／Folia 使用本機 25.0.4.1。
