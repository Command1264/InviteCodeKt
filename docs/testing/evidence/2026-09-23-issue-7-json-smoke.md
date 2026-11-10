# Issue #7 JSON 儲存自動化 Smoke

- 日期：2026-09-23
- 受測 JAR SHA-256：`99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`
- 設定：`backend: JSON`，全新隔離 TestLab 實例；未安排真人玩家驗收

| Profile | 核心／Java | 首次啟動 | 停機後重啟 |
| --- | --- | --- | --- |
| `spigot-1.8.8` | Spigot 1.8.8／`local-8` | 自動建立 JSON 與 32-byte 密鑰，InviteCode `ACTIVE` | 直接讀回 JSON，Doctor 顯示 `JSON 儲存版本=1` |
| `spigot-latest` | Spigot 26.2／`local-25` | 自動建立 JSON 與 32-byte 密鑰，InviteCode `ACTIVE`；Doctor 顯示 Dialog=`SPIGOT` | 未執行，已由最低 Spigot 與最新 Paper 覆蓋重啟 |
| `paper-latest` | Paper 26.2 build 128／`local-25` | 自動建立 JSON 與 32-byte 密鑰，InviteCode `ACTIVE` | 直接讀回 JSON，Doctor 顯示 `JSON 儲存版本=1`、Dialog=`PAPER` |

最低 Spigot 與最新 Paper 的 JSON 與 `anti-abuse.key` SHA-256 在重啟前後各自保持不變；三個實例的 `logs/latest.log` 可用嚴格 UTF-8 解碼，沒有 InviteCode 維護模式、`NoSuchMethodError` 或找不到 JDBC driver。伺服器均以 `stop` 正常關閉，51888／52620／62620 port 已釋放。Java 8 JAR 檢查與相關 Gradle 測試通過。共用儲存契約由 SQLite、MySQL、JSON 三種 backend 使用；本機未提供 MySQL 測試服務，相關 integration tests 依既有設定略過。

測試過程先後查出兩個只有在實際伺服器 ClassLoader 才會出現的問題：JSON 快照直接經 `DriverManager` 無法取得封裝的 SQLite driver，以及舊版 Spigot 的 Gson 蓋過插件需要的版本。最終 JAR 已改用 SQL 模組既有的 driver 載入方式，並將插件用 Gson 搬至私有 package；上表與雜湊結果均為修正後同一份 JAR 的結果。

`spigot-latest` 的最後一輪 smoke 有一個從 `127.0.0.1` 進入的 `Command1` 玩家連線約四秒，並非本次安排的測試步驟。此連線使該實例產生玩家資料；沒有以其行為判定玩家流程通過，也不把該實例的 JSON 雜湊用於重啟一致性比較。

最新 Paper 與 Spigot 啟動時另有 OSHI 在 Windows Perflib 讀取英文 counter 名稱失敗的錯誤，堆疊位於伺服器的 OSHI／JNA，未涉及 InviteCode；伺服器與插件仍正常啟動。真人操作、其他 Minecraft 版本與高負載效能不在本次 smoke 範圍，仍待後續驗收。
