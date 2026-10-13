# Issue #25 匿名化補充驗證

日期：2026-10-14。基線：`develop` 的 `befef792b20fd572b98e00569ca28a442b3e7320`。
分支：`codex/test/issue-25-anonymization-evidence`。本次只修改測試與驗證文件；插件 production 程式碼、資料版本與匿名化設計均未變更。

## 補齊的驗收證據

| 邊界 | 已驗證內容 |
| --- | --- |
| SQLite | 匿名化後關閉／重開仍保留歷史邀請、Qualification Fact 與時間，舊 UUID／名稱／邀請碼不留在有效匯出；PENDING／PROCESSING／UNCERTAIN 拒絕且完整快照不變；真正寫入途中的 SQL trigger 失敗回滾；兩個獨立連線競爭同一玩家，僅一個操作成功。既有舊碼保留、重新加入、Invitee 與文字遮蔽測試保留。 |
| MySQL 8.4.11 | 完整合成歷史包含邀請、Qualification Fact、已完成 Reward／transition、稽核、登入日與網路雜湊。新建連線池重開驗證匿名關聯、狀態與時間、舊碼占用、新 UUID 世代與有效匯出隱私；三種未完成獎勵狀態拒絕且快照不變；兩個獨立連線池並行維護；SQLState 45000 trigger 故障完整回滾並允許明確重試。 |
| JSON | 完整邀請／資格／Reward／transition／稽核／網路資料在 close／reopen 後保留匿名關聯、狀態、時間與約束；有效 JSON 不含原 UUID、名稱及網路雜湊；舊碼不能 Claim 或重新啟用；同 UUID 可作為新玩家。三種未完成獎勵狀態拒絕且快照不變；第二個 open 被檔案鎖拒絕；備份發布失敗後，重開仍是完整舊資料。 |
| 離線 CLI execute 邊界 | 唯讀 preview 不改來源；缺 player／stopped／backup、無效 UUID、不存在目標、無效來源、錯長度密鑰、另一把有效長度密鑰與既有備份均拒絕。拒絕原因與來源 SHA-256、備份是否建立分開斷言，避免提前 parser 拒絕造成假通過。成功只改指定玩家，保留另一玩家；擷取正常及錯誤訊息，確認不輸出秘密、資料中名稱或新舊 ID 對照。操作者已提供的目標 UUID 可以在拒絕訊息出現。 |

SQLite／MySQL 的失敗斷言核對實際注入錯誤／SQLState，不把任意 exception 當作故障注入通過。併發測試在兩個連線／連線池就緒後才同時放行，使用有界 Future 等待並結束 executor。JSON 的檔案鎖與 SQL 的維護交易／鎖各自驗證，不宣稱三者具有相同的線上多寫入者模型。

## 執行與結果

工具鏈依 mise 核對 `java@local-8`：Java `1.8.0_501`；沿用 Gradle Wrapper，未安裝 JDK 或新增 dependency。

```powershell
mise exec java@local-8 -- .\gradlew.bat check shadowJar --no-daemon --console=plain
```

最後完整檢查通過；JUnit XML 彙整 **56 suites、265 tests、0 failures、0 errors、0 skipped**。Java 8 bytecode 與插件 JAR 必要 entry 檢查通過。部分未變模組沿用 Gradle up-to-date 的有效結果；修改過的測試重新執行，不能把所有案例說成同一輪重新跑過。

| 本次專用 suite | tests | failures／errors／skipped |
| --- | ---: | --- |
| OfflinePlayerAnonymizerTest | 11 | 0／0／0 |
| MysqlAnonymizationEvidenceTest | 6 | 0／0／0 |
| JsonOfflineAnonymizationTest | 5 | 0／0／0 |
| OfflineAnonymizeMainTest | 8 | 0／0／0 |

JSON 的三種未完成 Reward 狀態在一個 testcase 中依序核對。資料 fixture、編譯、錯誤訊息斷言及 MySQL 初始化的初期失敗保留 log，不計為插件故障的 RED 證據，也不計為通過。最初完整檢查的三個 plugin MySQL 案例因空測試密碼而跳過，改用本次專屬臨時帳號密碼後重跑，最終 XML 已無跳過。沒有為製造失敗而修改 production。

原始命令 log、最終 XML 副本與結果 manifest 保留於本 Worktree 的 `invitecode-plugin/build/issue25-evidence/`；隔離服務的初始化、正常停止與初期失敗 log 位於 `invitecode-plugin/build/issue25-mysql/`。該證據目錄不加入 Git，合併清理前須封存仍需追溯的檔案並同步索引。

## 產物與隔離邊界

本次重建 JAR SHA-256：`B82C7D3B3263AC1153C8048DA2E6342FF423C90EDADFED8F5228FA80331848F4`。
與 Issue #12 的 D6AF 固定 JAR 比較，所有解壓後 entry 內容相同，沒有新增、刪除或變更 entry；外層 ZIP bytes／SHA-256 不同，不能把它們說成同一個檔案。本次未替換或部署至 Human 測服，不將重建 JAR 標記為新一次真人驗收。

MySQL 使用既有唯讀 8.4.11 binary，所有可寫資料放在本任務新建的 `invitecode-plugin/build/issue25-mysql/data`，只綁定 `127.0.0.1:63387`。測試以 UUID 名稱建立、清理專屬 `invitecode_test_*` database；未連到既有資料庫、Minecraft 或 InstanceHarbor 管理服務。初始化的 loopback 帳號設定錯誤曾在正常 shutdown 被拒後回收核對過的自有程序；此失敗另記錄，不計為成功流程。有效測試結束後以 mysqladmin 正常停止（exit 0），已確認自有程序與 port 釋放、移除臨時憑證並回收本次可重建資料目錄；保留 log、XML、JAR 與 resource manifest。刪除前資料目錄邏輯大小約 189 MiB，不將此數字當成磁碟實際釋放量。

## 保留的限制與交付

- 匿名化只處理有效 backend 資料，不清除既有備份、JSON `.previous`、伺服器 log、第三方插件或外部對照；依 [ADR 0005](../../adr/0005-offline-player-anonymization.md) 與[維運文件](../../operations/offline-anonymization.md)管理。
- 所有寫入者仍須事先停止；`--stopped` 是操作者確認。並行維護測試不代表可以在遊戲伺服器持續寫入時安全匿名化。
- 本次故障是在 SQL transaction／JSON 備份發布邊界注入，沒有測 OS 斷電、kill CLI 或 mysqld 崩潰。CLI 測試呼叫 execute，不驗證 JVM 子程序 exit code。
- 新登入語義以 storage 的重新建立玩家及舊關係隔離驗證；不新增真人登入證據。既有 Issue #12 三核心人工確認保留。
- 本次沒有 remote write。Issue #25 結案仍須指定版本交付／合併後，在 Issue 留下本證據與 CI 結果並取得結案授權；Issue #26 不在本次修改範圍。

下一步依 [Issue #25 驗收條件](https://github.com/Command1264/InviteCodeKt/issues/25)交付本機候選、授權後建立目標 `develop` 的 PR。之後再依 [Issue #26](https://github.com/Command1264/InviteCodeKt/issues/26)整理已具備的搬移證據與文件現況，銜接 [Roadmap #1「發行門檻」](https://github.com/Command1264/InviteCodeKt/issues/1)。不因本次自動測試通過而直接關閉 Issue 或正式發布。
