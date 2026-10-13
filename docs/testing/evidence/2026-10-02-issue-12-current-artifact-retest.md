# Issue #12：最新 develop 產物驗證

## 受測版本與資料邊界

本輪從 `develop` 的 `fb215c09ee4bbce96050ef4aebf97a1905ed984a` 建立 `codex/test/issue-12-current-artifact`。PR #29 的產品來源 `418c8298e8deb6c7fdef66d6e0ff67f78d807a89` 與此 develop 的 tree 相同；本輪沿用已完成建置檢查的固定 JAR，不重新建置另一個產物。

```text
D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C
```

JAR：`D:\Code\Java\Plugin\InviteCodeKt\artifacts\merged-issue26-418c829\worktree\invitecode-plugin\build\issue26-evidence\InviteCode-418c829.jar`。原始 runtime log、runner 輸出與 manifest 保留在本 Worktree 的 `invitecode-plugin/build/current-artifact/`。

自動測試僅操作本輪新建的 `InstanceHarbor/instances/Agent/InviteCode/` 隔離實例，不接入人工 Velocity，不把 Human 世界或玩家資料當成 fixture。沿用 InstanceHarbor 的持續 EULA 授權、已編譯 BuildTools 核心與既有測試依賴。JDK 由 mise 的已安裝 local alias 選擇，未安裝新 Java 或新增 production dependency。

## 已有與本輪證據

最新候選已完成 Java 8 `check shadowJar`，55 suites／244 tests，零失敗、錯誤與跳過；搬移失敗及 guard 復原見[Issue #26 紀錄](./2026-10-02-issue-26-transfer-retest.md)。本輪不無故重做這些檢查，補齊此同一 JAR 的伺服器驗證。

本輪 SQLite 隔離 fixture 已通過：拒絕偷偷升級、維護模式、v7→v8 索引衝突造成 DDL 交易回滾、哨兵資料保留、正式遷移至 v9、重啟及原 anti-abuse key 保留。runner 的暫存副本只將隔離 port 斷言由 51888 改為 63811，其餘斷言與正式 runner 相同；沒有修改產品或正式 runner。

兩個獨立 Spigot 1.8.8 fixture 已通過外部效果後 JVM halt 83、重啟標記 UNCERTAIN、玩家登入不自動重播，以及管理員 `mark-delivered`／`retry` 後重啟持久化。外部效果分別維持一次／明確重試成為兩次。故障後測試客戶端 ECONNRESET 是預期現象；不推論未測的作業系統斷電或資料庫服務中斷。

同一 D6AF JAR 的本輪結果全部通過，逐項核心雜湊、JDK、port、斷言與原始 log 索引見[證據 manifest](./2026-10-02-issue-12-current-artifact-manifest.json)：

| 門檻 | 本輪結果 |
| --- | --- |
| 核心啟動矩陣 | Spigot 1.8.8／1.12.2／1.16.5／26.2、Paper 1.20.6／1.21.8／26.2、Folia 26.1.2，8 組 enable、doctor ACTIVE、help、reload 與正常停止全部通過 |
| 玩家完整流程 | Spigot CHAT、Paper／Folia Dialog 合成封包，包含 claim、confirm、資格、離線補發與重啟持久化 |
| Vault／PAPI | 真實 Vault + EssentialsX 的 0→37.5 及重啟；PlaceholderAPI 資格未達成→達成→重啟保留 |
| JSON | 完整邀請、離線獎勵與重啟流程 |
| MySQL 8.4.11 | 空庫初始化、DDL／checksum fail-closed、APPLYING 恢復、共享 lock／guard 拒絕及解除後恢復、哨兵保留 |
| SQLite／獎勵故障 | 交易回滾與正式復原；UNCERTAIN 不自動重播、mark-delivered／retry 持久化 |

首次 JSON 啟動逾時暴露 runner 未回收自有子程序。本輪以外部假 Java 程序的 CLI 測試先 RED 再 GREEN，修正啟動失敗及輸入 pipe 斷線的 bounded cleanup；7 個 Node 回歸測試全部通過，並以修正後 runner 再跑一次真實 Spigot 完整流程通過。假程序 fixture 已封存到 build 證據目錄，不算 Minecraft 相容性證據。初期路徑、fixture 設定及 Windows MySQL 長路徑失敗保留 log，僅最終有效 fixture 結果計為通過。最終有效流程的 Agent 測服及自有 MySQL 已正常停止，所有本輪測試 port 已釋放，臨時 credential 檔已移除。首次 JSON 洩漏的自有程序曾在核對 PID 後強制回收，該失敗不計為通過。

## 固定 Human 測服部署與資料保留

既有 Spigot 1.8.8／10101、Paper 26.2／20101、Folia 26.1.2／30101 三台足夠，不建立新 Human 實例。TUI 可辨識三者且部署前皆已停止；Velocity 已有對應唯一的 legacy forwarding 路由。設定相符與 port 監聽不是本輪真人切服證據，仍需使用者操作。

部署前實際插件都是 `889DAD100D56D18C425D7299FD759EAA3DD822C863CF55FF486F7D8F372EFE3C`，SQLite schema 8，各有 2 名玩家、1 筆 Invitation、0 筆 Reward Action。備份位於 `D:\Code\Java\Plugin\InviteCodeKt\artifacts\human-issue12-fb215c0\`，包含原 JAR、InviteCode 資料與設定、workspace、server.properties、spigot.yml，逐檔 SHA-256 已核對；世界檔案另記錄雜湊，沒有重建或覆寫。

三台現已部署同一 D6AF JAR，以正式 `ic admin storage migrate SQLITE issue12-current-artifact` 完成 schema 8→9，重啟後 doctor ACTIVE，正常停止。逐表比對確認所有既有業務資料與舊稽核列相同，各僅新增一筆 STORAGE_MIGRATED；integrity_check 與 foreign_key_check 通過，anti-abuse key 相同。替換後首次啟動前世界檔案雜湊皆相同；啟停後 Spigot 世界 uid.dat 相同，新版沒有 uid.dat，改核對其既有世界玩家檔案全部相同。正常啟停會改變世界時間等檔案，不宣稱啟停後所有世界 bytes 相同。此為部署所需的資料版本升級，不執行匿名化、不重設資格、不 unlink 邀請。當前同 IP 策略 `DENY` 保持不變；本輪短版真人步驟優先使用既有帳號狀態，不要求新成功 Claim。

## 真人與交付狀態

使用者先前已確認三台的確認畫面、同意／不同意、逾時與重啟全部通過，見[真人紀錄](../final-human-acceptance.md)。本輪不抹除那些結果，也不因測服不足而暫緩。最終產物部署、遷移、自動矩陣及資料比對已完成。TUI 再次發現三台且皆已停止，共用 lobby／Velocity 仍監聽。接下來依[短版真人步驟](../current-artifact-human-acceptance.md)驗證實際客戶端的六碼 hover／複製、繁體中文、資格預檢與重啟狀態。37 個相關 UI class bytes 與歷史受測 JAR 相同；另一個 RuntimeCommandExecutor 的 javap 方法反組譯相同，保留歷史確認畫面與按鈕驗收，但不冒稱本輪已有真人登入。

Folia 保持實驗性；Paper 的第三方結果不代表 Folia 第三方已驗證。未執行的真人畫面及按鈕不可用合成封包取代。

本輪未 push、建立 PR、merge、tag 或發布。依 [Issue #12 驗收條件](https://github.com/Command1264/InviteCodeKt/issues/12)與 [Roadmap #1 發行門檻](https://github.com/Command1264/InviteCodeKt/issues/1)，下一步是由使用者回覆固定 Human 短版真人驗收；驗收通過後才整理 Issue #12 的發行門檻結論，遠端交付與正式發行仍需明確授權。
