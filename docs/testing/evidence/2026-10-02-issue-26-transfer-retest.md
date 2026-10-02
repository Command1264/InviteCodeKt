# Issue #26 整合、完整性預檢與復原驗證

日期：2026-10-02。分支：`codex/fix/issue-26-transfer-preflight`，目標 `develop`。

## 受測版本

在獨立 Worktree `D:\Code\Java\Plugin\InviteCodeKt\worktrees\fix-issue-26-transfer-preflight`，以 `develop` 的 `40e21b01edfc87275eac887b9f1f5ae147ae0542` 整合既有搬移候選 `8c57de5997e8c1b31f57ab883b5f46278500fcbb`，再修正來源完整性預檢及補足測試。主要 checkout 與舊候選 Worktree 保留，不在 `develop` 直接實作。

受測 JAR：`invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar`。

SHA-256：`D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C`。

## 執行與結果

mise 清單確認已有 `local-8`、`local-11`；本輪以 `mise exec java@local-8 -- java -version` 確認 Java `1.8.0_501`，所有建置與測試沿用該選版及 Wrapper，沒有安裝或修改工具鏈。

1. RED：新增 CLI 預覽拒絕孤立 SQLite 邀請碼的測試，原實作出現 `Expected StorageTransferException to be thrown, but nothing was thrown`。來源有錯誤資料仍被判定預檢成功。
2. GREEN：匯出改為先檢查來源完整性及快照格式，CLI 測試通過；SQLite 用唯讀 PRAGMA 檢查 CHECK／外鍵，MySQL 依白名單與 schema metadata 檢查外鍵關聯。
3. `mise exec java@local-8 -- .\gradlew.bat check shadowJar --console=plain` 通過；最後補強 trigger SQLState 與來源／備份位元組不變的斷言後，再次執行相同檢查通過。未變模組沿用 Gradle cache／up-to-date，受影響的測試重新執行。
4. 保留的 JUnit XML 合計 **55 suites、244 tests，0 failures、0 errors、0 skipped**。Java 8 bytecode、JAR 必要 entry／雙 JDBC driver／設定資源檢查通過，`git diff --check` 通過。

| 測試 suite | tests | 驗證內容 |
| --- | ---: | --- |
| OfflineStorageTransferMainTest | 3 | SQLite 孤立紀錄在 preview／apply 前拒絕，不建立目的地或備份且來源不變；SQLite→JSON→SQLite 保留 UNCERTAIN、冪等鍵、transition，重開不排入 pending |
| OfflineStorageTransferMysqlIntegrationTest | 2 | 另四種 MySQL 搬移方向保留 PENDING／UNCERTAIN；MySQL 重開讀回；可見併發來源連線拒絕；trigger 匯入失敗回滾、guard 阻擋重新啟動、保留來源／備份、全新 database 重試成功 |
| OfflineStorageTransferTest | 5 | 完整業務表搬移、非空目的地／密鑰拒絕、SQLite 故障回滾及既有 CHECK 違規拒絕 |
| MysqlIntegrationTest | 13 | 含重新啟用外鍵後的孤立來源仍拒絕及既有 MySQL schema／recovery 測試 |
| MysqlStorageContractTest | 2 | MySQL storage contract |
| MysqlRuntimeBootstrapTest | 2 | 同資料庫／密鑰重新開啟、startup 與 transfer lock／guard 的協調 |
| JsonOfflineTransferTest | 2 | staging 失敗不發布、重開讀回及目的檔競爭不覆寫 |

原始 XML 快照保留於本 Worktree `invitecode-plugin/build/issue26-evidence/xml/<module>/`；完整執行 log 位於 `invitecode-plugin/build/issue26-mysql/full-check.log` 與 `final-check.log`。此目錄屬本機驗證產物，不加入 Git。

## 隔離範圍與限制

MySQL 使用已存在的 `8.4.11` runtime，建立本 Worktree 全新的 `invitecode-plugin/build/issue26-mysql/data`，啟動前確認 port `63386` 無 listener，僅綁定 `127.0.0.1`。各測試使用 UUID 命名的專用 database，執行後只刪除自己建立的 database。未存取既有 MySQL 服務、玩家資料或 Human／Agent Minecraft 實例，也未接入 Velocity。

MySQL 故障注入在公開 `OfflineStorageTransfer.import` 的真實 transaction 邊界執行：目的地 trigger 發出 SQLState `45000`，確認回滾與持久 guard；關閉連線池後以 `MysqlRuntimeBootstrap` 重新開啟遭拒，使用新 database 匯入驗證後重開成功。這不是作業系統斷電、kill CLI 或重啟 mysqld 的測試；不宣稱涵蓋那些崩潰時序。

MySQL→檔案方向的 dump fixture 只驗證 CLI 要求非空備份的 gate，未證明 dump 可還原；實際維運仍須自行驗證一致性 dump。`--stopped` 仍是操作者確認，不能證明不可見的其他 MySQL 帳號或稍後連入的寫入者已停止。

測試結束後正常停止本輪隔離 MySQL，移除暫存憑證並確認 port 已釋放；保留資料目錄與 log 供核對。真人遊戲內驗收維持先前延後安排，本輪未要求追加真人測試。

## 交付

Agent 驗證完成後保留 task Worktree、JAR、XML 與分支供使用者審閱。Push、PR、merge 及 Issue 關閉尚未執行。下一步依 [Issue #26 驗收條件](https://github.com/Command1264/InviteCodeKt/issues/26)交付新 HEAD，授權後建立目標為 `develop` 的 PR；再依 [Roadmap #1 發行門檻](https://github.com/Command1264/InviteCodeKt/issues/1)核對 Issue #12 與最終發行證據。
