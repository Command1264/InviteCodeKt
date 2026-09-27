# 離線匿名化玩家資料

此維護工具只處理指定 Minecraft UUID。它把歷史關係改掛到隨機歷史 ID、刪除名稱、登入日期和網路雜湊、遮蔽相關自由文字，並以防濫用密鑰的獨立 HMAC 保留舊邀請碼，避免重新分配。匿名化後同一玩家再加入會建立全新資料。工具不從遊戲指令執行。

## 執行前

1. 停止**所有**連到相同 backend 的 InviteCode 實例，確認沒有仍在執行的伺服器或維護工具。`--stopped` 是操作者確認，工具無法證明其他 MySQL 寫入者已停止。
2. 保留原始資料、anti-abuse 密鑰與設定檔的可還原備份。MySQL 另須先完成一致性資料庫 dump，並把其路徑傳給 `--backup`；工具只檢查 dump 檔存在且非空，不會驗證 dump 可還原。SQLite／JSON 的 `--backup` 指向**尚不存在**的新檔案，工具會先複製並核對 SHA-256 才修改來源。
3. 確認目標 UUID 正確，先執行不帶 `--apply` 的預覽。任何相關 Reward Delivery 或 Action 尚為 `PENDING`、`PROCESSING`、`UNCERTAIN` 時會拒絕；先依獎勵復原流程處理。正式交易若在其他資料的自由文字中找到仍無法安全移除的原 UUID／名稱，也會回滾並提示所在資料表；不得為了通過檢查而直接清空未交付獎勵內容。
4. 以專案選定的 mise Java 執行已建置的 `InviteCode-0.1.0-SNAPSHOT.jar`。下列 `JAR`、路徑及 UUID 要替換成實際值；**不要**把密碼或密鑰寫在命令列。

### SQLite

現有 schema 8 必須先明確升級到 9；升級及匿名化各自保留一份不同名稱的備份：

```powershell
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --migrate-sqlite --backend sqlite --source $DB --backup $SCHEMA_BACKUP --stopped
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend sqlite --source $DB --secret $KEY --player $UUID
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend sqlite --source $DB --secret $KEY --player $UUID --apply --stopped --backup $DATA_BACKUP
```

初次建立的 schema 9 不需升級。若仍有 `-wal`／`-shm` 檔案，工具會停止備份與修改；先檢查是否有實例使用資料庫，完成 checkpoint 與完整備份，不要直接刪除檔案。

### JSON

現有 snapshot schema 8 必須先明確升級到 9；升級仍使用原子快照機制，另外留下 `.previous`：

```powershell
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --migrate-json --backend json --source $JSON --backup $SCHEMA_BACKUP --stopped
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend json --source $JSON --secret $KEY --player $UUID
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend json --source $JSON --secret $KEY --player $UUID --apply --stopped --backup $DATA_BACKUP
```

### MySQL

現有 schema 1 必須先升級到 2。`$MYSQL_BACKUP` 是已完成的非空 dump 檔；密碼與共用的 32-byte Base64 anti-abuse 密鑰分別從指定環境變數讀取。TLS 預設 `VERIFY_IDENTITY`，必要時可明確加 `--tls VERIFY_CA` 或 `--tls REQUIRED`。

```powershell
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --migrate-mysql --backend mysql --host $HOSTNAME --port $PORT --database $DATABASE --username $USERNAME --password-env INVITECODE_MYSQL_PASSWORD --secret-env INVITECODE_NETWORK_SECRET --backup $MYSQL_BACKUP --stopped
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend mysql --host $HOSTNAME --port $PORT --database $DATABASE --username $USERNAME --password-env INVITECODE_MYSQL_PASSWORD --secret-env INVITECODE_NETWORK_SECRET --player $UUID
mise exec -- java -cp $JAR io.github.command1264.invitecode.plugin.OfflineAnonymizeMain --backend mysql --host $HOSTNAME --port $PORT --database $DATABASE --username $USERNAME --password-env INVITECODE_MYSQL_PASSWORD --secret-env INVITECODE_NETWORK_SECRET --player $UUID --apply --stopped --backup $MYSQL_BACKUP
```

## 驗證與復原

工具顯示受影響的邀請、獎勵與舊碼數量。執行後確認：原 UUID 在 Player 查詢中不存在；歷史 ID 保留舊邀請關係；舊碼不能 Claim 或重新配發；資料可重新開啟；同 UUID 再加入會是新玩家。不要公開歷史 ID 與備份對照。交易失敗時 SQL 會回滾；若回滾狀態不明，停止所有寫入並檢查。JSON 快照失敗會停止當次寫入；檢查原檔與 `.previous` 後再處理。

匿名化不能刪除既有備份、log、終端機歷史、程序參數、第三方插件或玩家自行保存的資料。精確的歷史時間及關係仍可能被持有外部對照的人推測身分；備份與操作紀錄應依獨立保留政策保護與清除。工具不輸出新舊 ID 的對照。每次操作會新增固定理由的 `PLAYER_ANONYMIZED` 稽核事件，不在資料庫保留可能含身分的自由文字理由。
