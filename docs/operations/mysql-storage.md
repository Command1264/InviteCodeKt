# MySQL 8.4 儲存設定與復原

InviteCode 的 MySQL backend 目前以 MySQL 8.4 LTS 為正式驗證目標。MariaDB 尚未列入相容性承諾。插件會在已存在、完全空白的 database 首次啟動時自行建立 schema；database 本身與專用帳號須先由管理員建立。本功能不搬移 SQLite 資料，也不會在 reload 時切換 backend。

## 準備 database 與設定

先在 MySQL 8.4 建立專用的空白 database 與帳號，並授予這個 database 所需的建表與讀寫權限。不要讓插件連到已有其他用途資料的 database。`plugins/InviteCode/storage.yml` 的預設檔案位於 JAR 的 `config/storage.yml`；將 `backend` 改為 `MYSQL` 並設定下列值：

```yaml
config-version: 1
backend: MYSQL

sqlite:
  file: invitecode.db

mysql:
  host: db.example.net
  port: 3306
  database: invitecode
  username: invitecode
  password: "${INVITECODE_DB_PASSWORD}"
  network-secret: "${INVITECODE_NETWORK_SECRET}"
  connection-timeout: 5s
  tls-mode: VERIFY_IDENTITY
```

`password` 可以像上例使用 `${ENV_VAR}`，也可以直接在 `storage.yml` 寫入密碼，例如 `password: "在此填入自己的資料庫密碼"`。環境變數不存在時會在連線前拒絕啟動；若密碼以 `${` 開頭，必須是完整的環境變數參照，以免拼錯後被當成明文密碼。直接寫入時請限制 `storage.yml` 的讀取權限，檢查備份與檔案分享範圍，且不要把真實密碼提交到 Git。預設的 JAR 設定仍採環境變數參照，不內附密碼。

`network-secret` 仍只接受 `${ENV_VAR}`。啟動 Minecraft 伺服器前，必須在其程序環境中設定該變數。`INVITECODE_NETWORK_SECRET` 是 32 bytes 隨機資料的 Base64 字串，同一個 MySQL database 的所有插件實例必須使用完全相同的值。PowerShell 7 可在伺服器啟動腳本中產生一次，再由安全的設定管理方式保存並分發：

```powershell
$env:INVITECODE_NETWORK_SECRET = [Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

上例每執行一次都會產生不同密鑰。正式使用時只產生一次，將其與 MySQL 備份一起安全保存；不得每次開機重新產生、寫入 Git、貼到 Issue 或輸出到 log。MySQL 只保存密鑰的 SHA-256 識別碼，不保存密鑰本體；新實例的密鑰不一致時會拒絕啟動。MySQL 模式不使用本機 `anti-abuse.key`。

`tls-mode: VERIFY_IDENTITY` 是預設值，要求 TLS 並驗證伺服器憑證與 host 名稱。憑證必須由 JVM 信任，必要時在 Minecraft 伺服器的 Java 啟動參數設定 trust store。`VERIFY_CA` 驗證憑證鏈但不驗證 host 名稱；`REQUIRED` 僅加密連線、不驗證伺服器身分，適用於受控的本機測試。設定變更後須重新啟動，不會熱切換連線池。`connection-timeout` 可設 250ms 至 60s；連線池耗盡或 MySQL 不可用時，當次操作會在有限時間內失敗。

## 初始化與復原

新 database 首次啟動時，插件會取得 MySQL advisory lock，寫入含 checksum 的 migration ledger，依序建立表並逐一檢查欄位、索引、外鍵與儲存引擎。MySQL DDL 無法像 SQLite 一樣完整交易回滾；若在建表途中中斷，ledger 會維持 `APPLYING`。下次啟動只在既有表通過結構檢查時建立剩餘的表；結構不同時進入維護模式，保留資料供人工檢查。完成後 ledger 為 `APPLIED`，後續啟動只檢查版本與結構。

既有 schema 的版本更新須先備份、暫停所有使用同一 database 的插件實例，再執行 `/invitecode admin storage migrate MYSQL <reason>`。`doctor` 僅讀取健康狀態，不執行 migration。只有資料庫已建立而 schema 為全新的情況才會在一般啟動時自動建表；選擇不同 backend 並不搬移既有資料。不要在兩個 backend 同時收集正式玩家資料後直接改回設定。

## 驗證

獨立測試使用隨機命名的 `invitecode_test_*` database，測試結束後只清理該次建立的 database；不會連線到未指定的服務。CI 的 MySQL 8.4 service 執行共用 storage contract、中斷續跑、資料結構遭修改的拒絕、兩個連線池競爭 Claim、密鑰不一致、連線池耗盡及連線失敗測試。本機要執行同一批測試時，先在隔離 MySQL 8.4 設定 `INVITECODE_TEST_MYSQL_HOST`、`INVITECODE_TEST_MYSQL_PORT`、`INVITECODE_TEST_MYSQL_USER` 與 `INVITECODE_TEST_MYSQL_PASSWORD`，再依 [`README.md`](../../README.md) 使用 mise 與 Gradle Wrapper 執行 `check`。
