# 分層 help 與權限 Tab：Agent 驗證及部署

2026-10-18。程式候選 `6de6dce250334ec6a1647cb91ac2b09918abc9e6`，沿用 `codex/feat/command-feedback-messages`。
狀態：Agent 驗證及三台部署完成，待使用者驗收；沒有 push、PR、合併或發布。

## 變更與驗證

首頁五項常用指令，有管理權限時加一個 help 入口。管理分類先依權限篩選，每頁五項；
完整 help 路徑提供參數及範例。只有 help 導覽執行 RUN_COMMAND，操作用法只 SUGGEST_COMMAND。
所有既有命令、help 路徑、頁碼與適用參數有權限分級 Tab；無候選時回傳空集合。
沒有變更 storage schema、獎勵、匿名化或跨 backend 行為。

RED 已重現 parser、Tab 及首頁過量；修正後 Java 11 `check shadowJar`、Java 8
`check shadowJar --rerun-tasks`、後續受影響 plugin 測試、JAR 及 Java 8 bytecode 檢查皆通過。
目前 XML 彙總：315 cases，291 passed，24 MySQL 依賴測試 skipped，零失敗；不把 skipped 當通過。
獨立唯讀 review 未發現阻擋問題；雙語鍵與變數一致，20 條詳細說明可達。

## 產物

`InviteCode-0.1.0-SNAPSHOT.jar`，19,912,806 bytes。
SHA-256：`29C46B948B43B3066768BC824853080D2DD09240E1DA2E6179B5210493B9E54A`。
候選保留於 `.run/command-feedback-help/candidate/`；完整結果與檢查命令見
[JSON](2026-10-18-command-feedback-help-deployment.json)。

## 三台部署

- `invitecode-spigot-1.8.8`，port 10101，run `21ec0d02c1014f9a99fcc49700b8563f`；正常停止、備份、更新後 Done／ACTIVE，console probes 通過。備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-spigot-1.8.8\.invitecode-backups\help-navigation-20261018-073208-0e22a4cf`。
- `invitecode-paper-26.3`，port 20102，run `f3ebc01950d34969be49f8d034df49c5`；正常停止、備份、更新後 Done／ACTIVE，console probes 通過。備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-paper-26.3\.invitecode-backups\help-navigation-20261018-073208-1efc6afd`。
- `invitecode-folia-26.1.2`，port 30101，run `5e98fb6b92554296b00c29fb39a876e7`；正常停止、備份、更新後 Done／ACTIVE，console probes 通過。備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\help-navigation-20261018-073208-a34c3166`。

每台在停止且持有管理鎖時建立新備份，逐檔核對後只更新 JAR、雙語 messages 及 workspace 產物識別。
資料庫各表在啟動前後一致、quick_check=ok，key 與設定不變；部署期間 world level.dat 不變。
世界原位保留，備份不含完整世界。沒有再重設 B 或解除邀請，沒有更新核心或修改共享 Velocity／Lobby。
三條原有唯一路由保留；實際客戶端新一輪切服與點擊／Tab／色彩仍待真人確認。
Paper 26.2 沒有修改，仍停止；最新 Folia ALPHA 及既有 Paper 26.2 adapter 複驗缺口未關閉。

下一步：依 [真人清單](../command-feedback-human-acceptance.md) 先完成六項 help 複驗，再續原本未完成的 12 步。
真人通過後仍需還原暫時測試設定／權限、保存證據，另取得 push／PR／merge 授權。

同一 HTML 閱讀入口已更新新版首頁樣例、玩家／console 切換、help 分層及本輪六項複驗。
靜態 anchor、JavaScript 語法、DOM stub 的層級／受眾及原 12 步控制檢查通過；
沒有以文件樣例代替實際瀏覽器或 Minecraft 渲染驗收。暫存清單記於 `.run/command-feedback-help/manifest.json`，
待驗收候選與必要證據保留；本輪額外保存量約 20 MB，沒有建立額外核心或完整 runtime。
