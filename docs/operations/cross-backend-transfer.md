# 離線跨 backend 資料搬移

本工具支援 SQLite、JSON、MySQL 之間六個不同方向。它搬移完整 InviteCode 持久化資料，驗證後才發布目的地，不改寫來源，也不修改 `storage.yml`。`--stopped` 是操作者對停機狀態的確認；CLI 無法證明所有使用其他 MySQL 帳號的寫入者都已停止。

## 執行順序

1. 停止**所有**使用來源與目的 backend 的 InviteCode 實例及其他維護程序。MySQL 需專用 database 與帳號；目的 database 必須完全空白。SQLite／JSON 目的檔必須不存在。確認沒有 SQLite `-wal`／`-shm` 留存；不要直接刪除仍可能含有資料的檔案。
2. 確認來源版本為 SQLite schema 9、JSON snapshot schema 9 或 MySQL schema 2。舊版本先依既有離線 schema 升級流程升級。事先準備**相同的 32-byte anti-abuse 密鑰**：SQLite／JSON 的 `--from-secret-file`、`--to-secret-file` 指向已存在的二進位密鑰檔；MySQL 的 Base64 密鑰從 `--mysql-secret-env` 指定的環境變數取得。不要重新產生密鑰；否則 IP hash 與已匿名化邀請碼的保留值會失效。
3. 備份原始設定及密鑰。若來源是 MySQL，另以一致性方式完成可還原的 database dump；`--backup` 指向該 dump。若來源是 SQLite／JSON，`--backup` 指向尚不存在的檔案，工具在正式搬移時複製並核對 SHA-256。MySQL dump 的內容與可還原性須由管理員自行確認，工具只檢查非空。
4. 先執行不加 `--apply` 的預檢，確認每張表的資料筆數、版本、目的地空白及密鑰一致；再加 `--apply --backup <路徑>` 正式執行。過程中不得重啟任何實例。CLI 回報成功後，檢查目的地資料、Reward 未完成狀態、舊碼保留值及稽核，再把 `plugins/InviteCode/storage.yml` 只改為目的 backend，啟動一組伺服器並做唯讀 `doctor` 檢查。

以下範例的 `$JAR` 應為已驗證的 InviteCode JAR；所有路徑、主機、database 及環境變數名稱均須換成實際值。MySQL 密碼與密鑰不得放在指令參數、log 或 Git。

### SQLite → JSON

```powershell
$entry = 'io.github.command1264.invitecode.plugin.OfflineStorageTransferMain'
$options = @('--from-backend','sqlite','--from-file',$SQLITE,'--from-secret-file',$SOURCE_KEY,
             '--to-backend','json','--to-file',$JSON,'--to-secret-file',$DEST_KEY,'--stopped')
mise exec -- java -cp $JAR $entry @options
mise exec -- java -cp $JAR $entry @options --apply --backup $SOURCE_BACKUP
```

反向或 SQLite／JSON 之間其他方向只需交換 `--from-backend`／`--to-backend` 與各自的檔案及密鑰路徑。

### 涉及 MySQL

MySQL database 必須事先建立但不含表格，並由專用帳號存取。TLS 預設 `VERIFY_IDENTITY`；隔離測試可明確改為 `--mysql-tls REQUIRED`。若 MySQL 是來源，`--backup` 必須是既有的非空 dump；若 MySQL 是目的地，來源的檔案備份由工具建立。

```powershell
$entry = 'io.github.command1264.invitecode.plugin.OfflineStorageTransferMain'
$mysql = @('--mysql-host',$HOSTNAME,'--mysql-port',$PORT,'--mysql-database',$DATABASE,
           '--mysql-username',$USERNAME,'--mysql-password-env','INVITECODE_MYSQL_PASSWORD',
           '--mysql-secret-env','INVITECODE_NETWORK_SECRET')
$options = @('--from-backend','json','--from-file',$JSON,'--from-secret-file',$SOURCE_KEY,
             '--to-backend','mysql','--stopped') + $mysql
mise exec -- java -cp $JAR $entry @options
mise exec -- java -cp $JAR $entry @options --apply --backup $SOURCE_BACKUP
```

MySQL → SQLite／JSON 時，將 MySQL 設為 `--from-backend mysql`，目的地提供 `--to-file` 與 `--to-secret-file`，並把 `--backup` 指向已建立的 MySQL dump。兩端若都在同一台伺服器，仍須停止所有指向來源 database 的插件實例。

## 中斷與回復

- 預檢失敗或目的地已存在：保留來源，不覆寫目的地。核對錯誤、密鑰與設定後，使用新的空白目的地重試。
- SQLite／JSON 匯入失敗：正式目的檔不會在匯入成功前發布。發布需要目的地所在檔案系統支援同目錄硬連結；不支援時安全失敗，請改用支援的檔案系統。暫存檔可能在程序崩潰後留存；先檢查後再以新目的路徑重試。來源與備份保留。
- MySQL 初始化或匯入中斷：目的 database 保留 `invitecode_transfer_guard`，插件會拒絕啟用。不要手工刪除 guard 來繞過檢查；先保留事故證據，使用全新空白 database 重試，或從經驗證的備份還原後再處理。MySQL DDL 不能靠交易回滾清除。
- 若切換設定後需要回復來源，先停止目的地所有寫入者；目的地啟用後新增的資料不會自動回流，須先人工核對差異。不得同時讓來源與目的地接受新 Claim 或獎勵交付。

真人遊戲內操作驗收依 Issue #12 延後。隔離測試與 dump 不得使用正式玩家資料。
