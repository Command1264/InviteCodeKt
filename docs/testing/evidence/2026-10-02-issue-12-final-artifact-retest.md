# Issue #12：同一整合 JAR 的隔離複驗

## 產物與範圍

本輪修正測試 runner 與文件，產品 runtime 沒有修改。沿用 `codex/test/final-release-acceptance`；預定目標為 `develop`，本輪沒有 push、PR、merge、tag 或發布。

受測產品是 `develop` 的 `10ce7b5a3472efb71baf3239c97e51df57ab5584` 與 UX 分支形成的整合 tree `ec68d7abe31dba52333a466ee0ed9e8526c2fd82`。所有伺服器情境均使用同一份 `InviteCode-0.1.0-SNAPSHOT.jar`，SHA-256：

```text
BA2D48E7EB774FAB464F41B8B468A142B1E559C4169B55DDA5EA42A89552C50C
```

JAR 保留於本 Worktree 的 `invitecode-plugin/build/review-integration/ec68d7a/`；原始 runner log 保留於其 `retest/`。可提交的[證據 manifest](./2026-10-02-issue-12-final-artifact-retest.json)記錄每次通過結果、實例路徑、實際核心 build、Java、port 與完整核心／插件雜湊；主 Agent 重新讀取並核對實體 JAR 雜湊，沒有只信任 metadata。

所有新實例都位於 `D:\Code\Java\Plugin\InstanceHarbor\instances\Agent\InviteCode\`，使用獨立資料與 loopback／offline mode。測試前確認 port 未被監聽，依既有 EULA 授權建立新實例；不覆寫或操作 Human 世界／插件資料，不接入人工 Velocity。既有 Legacy 實例僅提供已取得第三方 JAR 的唯讀來源。Spigot 核心沿用 BuildTools `Server` 的既有編譯產物，本輪未重新編譯核心。

## Runner 修正與審查

- PowerShell 改用 `InstanceHarbor.psm1` API 並明確傳入 `-Audience Agent`；新增 `-InstanceHarborRoot`，保留舊 `-TestLabRoot` alias 與環境變數 fallback。明確指定無效 root 時直接拒絕。
- Node runner 共用實體路徑檢查，要求 `workspace.json` schema 2、Agent audience、InviteCode project 與實例名稱相符；拒絕 Human、Legacy、路徑跳脫、Windows ADS 及根目錄 junction。保留原有 fresh-data、EULA、JAR 雜湊、profile、port、loopback／offline 檢查。
- SQLite 故障 fixture 維持 v7→v8 的索引衝突，完整復原至 v9 並核對保留邀請碼表；MySQL 維持 V1 `APPLYING` 故障 fixture，復原後核對 V1/V2 checksum、`APPLIED` 與 14 張表。
- 修正第三方／中斷測試對舊單行 confirmation 的假設，改核對新版邀請人、邀請碼與同意／不同意提示；MySQL CLI 多行結果正規化 Windows CRLF。Console smoke 同時接受繁體中文與英文訊息。
- 邊界驗證採 RED→GREEN：PowerShell 行為測試通過；Node 五項測試通過且沒有 skip，包含實例及 instances 根目錄 junction 逃逸。所有 PowerShell／CJS 語法及 `git diff --check` 通過。
- 獨立靜態審查發現 P2 根目錄 junction 缺口，補測與修正後再次確認解除；沒有未處理的 Critical／High 問題。

## 同產物結果

| 核心 profile | 實際 Minecraft／build | Java | SQLite 啟動、doctor、help、reload、stop |
| --- | --- | --- | --- |
| Spigot 1.8.8 | 1.8.8／local-buildtools | local-8 | 通過 |
| Spigot 1.12.2 | 1.12.2／local-buildtools | local-8 | 通過 |
| Spigot 1.16.5 | 1.16.5／local-buildtools | local-8 | 通過 |
| Spigot latest | 26.2／local-buildtools | local-25 | 通過 |
| Paper 1.20.6 | 1.20.6／151 | local-21 | 通過 |
| Paper 1.21.8 | 1.21.8／60 | local-21 | 通過 |
| Paper latest | 26.2／129 | local-25 | 通過 |
| Folia latest | 26.1.2／8 | local-25 | 通過，維持實驗性 |

其他通過情境：

- Spigot 1.8.8、Paper 26.2、Folia 26.1.2：邀請碼、Claim、Confirm、Qualification、重啟後 Pending Reward、Inviter 登入補發及 Stats；Folia 額外驗證兩個分離 region 的 Status。
- Spigot 1.8.8 私密聊天輸入及大小寫混合 `CaNcEl` 取消；Paper／Folia 原生輸入與確認 Dialog 封包、指令取消不建立 Invitation，後續獎勵與重啟流程。
- Reward 外部效果後故意 halt 83：重啟標為 UNCERTAIN、不自動重播；管理員 `mark-delivered` 保持一次效果，`retry` 明確產生第二次效果，處置經重啟保留。
- SQLite：啟動不偷偷升級舊 schema、維護模式、DDL 交易回滾、哨兵玩家保留、明確遷移到 v9、重啟持久化與原 key 保留。
- MySQL 8.4.11：錯誤表結構與 checksum 均拒絕初始化；修復後續建並保留哨兵玩家，兩個 ledger 版本與 14 張表經重啟保留，不建立本機 SQLite 或 key。
- Paper 1.21.8／Vault 1.7.3／EssentialsX 2.21.2：餘額由 0 成為 37.5，重啟後仍為 37.5。
- Paper 1.20.6／PlaceholderAPI 2.12.3：資格先不成立，測試 expansion 切換後重新登入達資格，Reward 與狀態經重啟保留。
- Paper 1.20.6／JSON：完整邀請及離線獎勵流程，重啟後 Console doctor／help／reload、JSON 與 key 存在。修正雙語 smoke 後，英文 JSON 與繁體中文 Spigot 均再次通過。

整合來源快照以已核對的 `mise java@local-8` 執行 Gradle Wrapper `check --rerun-tasks --no-daemon`，提供專用 loopback MySQL 測試服務：51 個 suite、230 項測試、0 failures、0 errors、0 skipped；Java 8 bytecode 與插件 JAR 檢查均通過。先前跳過的 MySQL 測試已補齊。此建置使用整合來源，沒有更換上方所有 runtime 測試使用的固定 JAR。

## 曾失敗的測試與限制

保留首輪失敗 log，不將其當作通過證據：Vault runner 等待舊分號串接 confirmation、MySQL CLI ledger 的 CRLF 比較、英文 JSON smoke 的中文限定 regex；修正後分別以全新 Vault／MySQL 實例或正常停機的同一 JSON 隔離實例通過複驗。最初含點號的名稱被 smoke 名稱限制拒絕，該未啟動實例沒有覆寫；合法 profile 名稱現可保留點號。

Paper 26.2 的 Windows OSHI／Perflib 計數器與 OSProcess 錯誤出自核心環境，Paper 1.20.6 合成客戶端也有 protodef 封包解析警告；另有 JVM Unsafe／第三方 deprecation 警告。上述 log 已保留；不當作 InviteCode 的 linkage 缺陷，也不隱藏。插件流程與持久化斷言均完成，沒有為本任務修改 Windows registry 或第三方依賴。

測試結束後正常停止各 runner 的伺服器與隔離 MySQL；矩陣八個 port 及 MySQL 63384／63385 皆沒有 listener。保留實例、測試資料與 log 作為證據，沒有清理固定 Human 伺服器。

同產物的自動矩陣與復原證據缺口已解除。Folia 第三方整合仍有限制，本輪未複驗 Folia Vault 或 PlaceholderAPI；Paper 的結果不代表 Folia 已通過。合成客戶端不能證明原生按鈕點擊、外觀、RGB 或剪貼簿操作；這些真人結果仍綁定先前受測 JAR，見[真人紀錄](../final-human-acceptance.md)。本輪沒有改動 runtime，不要求使用者重做已確認的完整三台 UX 流程；正式發行前仍須對齊最後安裝產物與人工證據，不能宣稱新整合 JAR 已真人驗收。

## 下一步與 Git 交付

依 [Issue #12 的驗收條件](https://github.com/Command1264/InviteCodeKt/issues/12)及 [Roadmap #1 的發行門檻](https://github.com/Command1264/InviteCodeKt/issues/1)，下一步是審閱本輪同產物證據、授權 push 並建立目標 `develop` 的 PR。這是已有 runtime／UX 實作的交付階段，沒有新增 dependency 或需要重做的設計前置。第一個動作是針對本輪提交版本確認遠端交付授權；PR 使用 `Refs #12`，保留真人產物差異與 Folia 限制，不自動關閉 Issue 或正式發行。合併須另取得目前 HEAD 與 `develop` 的明確確認。

Issue #26 的跨 backend 搬移已有另一個獨立 Worktree 候選 `8c57de5`，本輪未修改或驗收它；不因 roadmap 仍列為 open 而重新實作或擅自合併。
