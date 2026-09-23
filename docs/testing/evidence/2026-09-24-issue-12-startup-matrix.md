# Issue #12 首輪跨版本啟動矩陣

- 受測基線：`develop` merge commit `3d64431`；唯一插件 JAR SHA-256：`99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`。
- 本機建置：`mise exec -- .\gradlew.bat check shadowJar` 通過；本機未提供 MySQL service，相關整合測試依既有條件略過。PR #23 的獨立 MySQL CI 已通過。
- 每個 profile 使用 TestLab 的獨立 `issue12-*` 實例、預設 SQLite、對應 mise Java。首次啟動與一次重啟都經 `smoke.ps1` 從 Console 執行 `ic admin doctor`，取得 `Runtime=ACTIVE`、儲存版本、正常 `stop`，且兩次停機後的 SQLite DB 與 anti-abuse key SHA-256 各自一致。再以同一 runner 對七個實例驗證 Console `ic help`、`ic reload` 均回傳預期訊息。所有七個受測實例的嚴格 UTF-8 log 檢查通過，沒有 InviteCode linkage／event 錯誤；測試後沒有殘留的矩陣 port listener。

| Profile | 核心／build | Java | 啟動與重啟 |
| --- | --- | --- | --- |
| `spigot-1.8.8` | Spigot 1.8.8／local BuildTools | `local-8` | 通過 |
| `spigot-1.12.2` | Spigot 1.12.2／local BuildTools | `local-8` | 通過 |
| `spigot-1.16.5` | Spigot 1.16.5／local BuildTools | `local-8` | 通過 |
| `spigot-latest` | Spigot 26.2／local BuildTools | `local-25` | 通過 |
| `paper-1.20.6` | Paper 1.20.6 build 151 | `local-21` | 通過 |
| `paper-1.21.8` | Paper 1.21.8 build 60 | `local-21` | 通過 |
| `paper-latest` | Paper 26.2 build 128 | `local-25` | 通過 |
| `folia-latest` | Folia 26.1.2 stable build 8，僅於 TestLab 本機 task Worktree 修正 profile 後 | `local-25` | 不通過：Folia 在載入階段拒絕插件 |

另外以同一份 JAR 在 `paper-1.20.6` 的獨立 `issue12-json-paper-1206` 實例設為 `backend: JSON`，首次啟動與重啟均回報 `ACTIVE` 與 JSON 儲存版本 1；兩次正常停機後，`invitecode.json` 和 `anti-abuse.key` 的 SHA-256 各自一致。Console 的 `ic help` 與 `ic reload` 也通過。這是代表性 JSON 真實伺服器整合，並未測試 JSON 的玩家負載或跨 backend 搬移。

## 外部整合的有／無情境

`smoke.ps1` 已加入整合狀態預期值檢查；不傳 switch 時要求 doctor 回報 PlaceholderAPI 與 Vault Economy 均為 `false`。先前七個純淨實例符合此條件，另於 Spigot 1.8.8 以新檢查重跑一次通過。

| 情境 | 獨立實例與受測外掛 | 結果 |
| --- | --- | --- |
| PlaceholderAPI 存在 | `issue12-papi-paper-1206`，Paper 1.20.6、PlaceholderAPI 2.12.3；外掛 JAR SHA-256 `FDE03259F5AF6938F3C33EEB4D814000A1ADABF1D2304CE14970BE81F609A437` | 首次啟動與重啟均 `PlaceholderAPI=true`、`Vault Economy=false`；SQLite DB 與 key 雜湊不變 |
| Vault Economy 存在 | `issue12-vault-212-paper-1218`，Paper 1.21.8、Vault 1.7.3-b131、EssentialsX 2.21.2；兩個 JAR SHA-256 分別為 `A6B5ED97F43A5CF5BBAF00A7C8CD23C5AFC9BD003F849875AF8B36E6CF77D01D`、`0B759026F02F3C547C32886F3666DB3C1F94CFF73E141AE5648313FD0D0BDFC6` | 首次啟動與重啟均 `Vault Economy=true`、`PlaceholderAPI=false`；Vault log 顯示 Essentials Economy hooked；SQLite DB 與 key 雜湊不變 |

也在 Paper 26.2 與 1.21.8 試過 EssentialsX 2.22.0：doctor 可偵測 Vault Economy，但 EssentialsX 自行警告該伺服器版本不受支援，因此不把它當作乾淨的正向驗證。以上只確認外掛載入與 InviteCode 的服務偵測；尚未驗證實際 placeholder 展開、Vault 金額變化或發獎。

## 復原測試的現況

既有自動測試含 SQLite migration 失敗後的 rollback 與版本／資料保留、rollback 自身失敗時回報不確定狀態、Reward Action 在資料庫關閉後重開的 pending／interrupted recovery，以及 `UNCERTAIN` 只允許稽核後由管理員處理。核心測試也涵蓋外部 effect 執行後最終保存失敗時不自動重做。`mise exec -- .\gradlew.bat :invitecode-core:test :invitecode-storage-sql:test --rerun-tasks --no-daemon` 本輪通過，兩個 `test` task 均實際執行。這些是注入故障的程式層級證據，尚未在真實 Minecraft 伺服器上完成 Reward crash／restart 演練。MySQL 8.4 的儲存整合在 CI 的隔離 service 執行；本機沒有獨立 MySQL service，故本輪 MySQL 測試被略過，也沒有 MySQL 插件伺服器 smoke；不會使用現存的 MySQL 資料目錄代替隔離測試。

[PaperMC Folia 下載頁](https://papermc.io/downloads/folia)當時提供 26.1.2 stable build 8，將 26.2 標示為 experimental；TestLab `develop` 的 `folia-latest` 固定為 26.2 並只選 stable，因此初次無法建立實例。於 TestLab 獨立本機 `codex/fix/folia-stable-profile` Worktree 將 profile 指向 26.1.2 後，TestLab self-test 與 doctor 通過，實例可建立。Folia log 隨即顯示 `Could not load plugin 'InviteCode v0.1.0-SNAPSHOT' as it is not marked as supporting Folia!`；插件在載入前遭拒，尚未進入程式相容性測試。[PaperMC 的 Folia 開發文件](https://docs.papermc.io/paper/dev/folia-support/)也明確指出，加入支援標記仍不足以保證相容，須使用正確的 scheduler。目前程式仍呼叫傳統 `server.scheduler`，不能為通過 smoke 而直接在正式 JAR 宣告 Folia 支援，必須先驗證 scheduler 與事件處理是否符合 Folia 邊界。Folia 26.1.2 在這台主機上還讓 Console `/stop` 拋出伺服器內部 `NullPointerException`；runner 等待 30 秒後終止自己建立的程序樹，63620 port 已釋放。此項不與 InviteCode 載入失敗混為一談。

Spigot／Paper 26.2 的 log 有 Windows Perflib／OSHI／JNA 讀取系統計數器失敗及衍生的 system-report `NullPointerException`；堆疊未涉及 InviteCode，伺服器與插件仍可啟動、診斷並正常停機。這是測試主機／伺服器層級的已知雜訊，不能用它替代插件無錯誤的判定。

本輪對 Spigot／Paper 只涵蓋無玩家的啟動、SQLite／JSON 初始化、重啟一致性、Console help／reload／doctor 與外部服務偵測。Issue #12 尚缺玩家 critical path、各版本實際 Claim 與 Reward recovery、MySQL 在代表性伺服器的整合、Vault／PlaceholderAPI 實際功能、伺服器層級注入失敗／migration recovery、Folia 可載入後的實驗性 smoke 與最終人工觀感驗收；不得據此宣布 1.0 發行 gate 通過。
