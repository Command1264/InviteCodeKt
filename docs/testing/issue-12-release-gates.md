# Issue #12 發行門檻結論

## 結論與範圍

截至 2026-10-13，固定 D6AF 產物的跨版本自動證據與三核心人工驗收已完成。依下列證據，Issue #12 的技術驗證門檻可判定通過，支援範圍保留本文件列出的限制。本次是文件收尾候選；GitHub Issue #12 仍為 OPEN，尚須將收尾文件合併、在 Issue 留下驗收證據後結案。Roadmap #1 與正式 1.0 發行未完成。

- 產品來源：`418c8298e8deb6c7fdef66d6e0ff67f78d807a89`，已整合至 `fb215c09ee4bbce96050ef4aebf97a1905ed984a`。
- 受驗收的 runner／文件來源：`1405745c6fc049c7df2217304e8df801c728fb68`；產品 runtime 沿用上述來源，不因文件 commit 改稱另一個 JAR。
- 已合併：[PR #30](https://github.com/Command1264/InviteCodeKt/pull/30)，`develop` 合併 commit `1b46780ccc5bfbbe4ff63898999852f61e5fa218`。
- 固定 JAR：`D:\Code\Java\Plugin\InviteCodeKt\artifacts\merged-issue26-418c829\worktree\invitecode-plugin\build\issue26-evidence\InviteCode-418c829.jar`。
- SHA-256：`D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C`。

本結論只涵蓋記錄中的受測版本與產物，不宣稱未測版本、自動升級或未來最新核心相容。日後重建或改動產品 runtime 的 JAR 須重新綁定雜湊並依影響驗證；不能直接套用本次人工驗收。

## 逐條核對 Issue #12

| 驗收條件 | 判定 | 可追溯證據與邊界 |
| --- | --- | --- |
| 最舊、中間與新版 Spigot／Paper smoke | 通過 | 同 D6AF JAR 的 Spigot 1.8.8／1.12.2／1.16.5／26.2、Paper 1.20.6／1.21.8／26.2，共七個 Spigot／Paper profile 的 enable、doctor ACTIVE、help、reload 與正常停止。另有 Folia 26.1.2 實驗性 profile。 |
| 相同 JAR 於兩端執行 critical user path | 通過 | Spigot 1.8.8 與 Paper 26.2 的合成玩家 Claim、Confirm、Qualification、離線 Reward、Restart 與 Stats；2026-10-13 使用者確認三核心人工驗收通過。人工確認不補造逐步截圖或 log。 |
| SQLite 支援邊界，MySQL／JSON 代表性 integration | 通過 | SQLite 為八組矩陣預設 backend；JSON 完整邀請、離線補發及重啟。MySQL 8.4.11 實測初始化、DDL／checksum 拒絕、中斷 ledger 復原、共享 lock／guard 與資料保留；另有共用 storage contract 及跨 backend 搬移證據。 |
| Vault／PlaceholderAPI 存在與不存在 | 通過於已測環境 | 基本矩陣檢查兩者不存在；Paper 1.21.8 的 Vault 1.7.3 + EssentialsX 2.21.2 實際入帳與重啟，Paper 1.20.6 的 PlaceholderAPI 2.12.3 資格轉換及重啟。不能推定其他 provider 或 Folia 第三方已通過。 |
| Folia smoke 與實驗性標示 | 通過於實驗性範圍 | Folia 26.1.2 同產物啟動、玩家流程、Dialog 合成封包、重啟與使用者驗收。保留實驗性標示，不提升為全面正式支援。 |
| Reward／migration 重啟及注入失敗復原 | 通過於已測故障 | SQLite DDL 交易回滾、schema 9 明確遷移與資料／密鑰保留；MySQL APPLYING 恢復；外部效果後 JVM halt、UNCERTAIN 不自動重播，人工 mark-delivered／retry 持久化。未測 OS 斷電與資料庫服務中斷不包含在內。 |
| 相容性、安裝、設定與限制文件符合證據 | 本收尾候選已核對 | README、真人紀錄、證據索引與操作文件已同步；歷史待驗收段落標為歷史，不覆蓋舊證據。文件交付仍須 review／合併。 |

自動測試來源：[固定產物驗證紀錄](./evidence/2026-10-02-issue-12-current-artifact-retest.md)與[逐項 manifest](./evidence/2026-10-02-issue-12-current-artifact-manifest.json)。搬移證據另見 [Issue #26 複驗](./evidence/2026-10-02-issue-26-transfer-retest.md)。

## 人工確認、CI 與封存

使用者於 2026-10-13 明確確認 `1405745` 在 Folia 26.1.2、Paper 26.2、Spigot 1.8.8 完成人工驗收並授權合併。完整確認原文與保留的複驗流程見[短版真人紀錄](./current-artifact-human-acceptance.md)；先前確認畫面、同意／不同意、逾時及重啟通過的紀錄見[歷史真人紀錄](./final-human-acceptance.md)。

PR #30 的 CI 五項均成功：Linux／Windows 各自使用 Java 8 與 Java 11 建置檢查，以及隔離 MySQL integration。結果見 [CI run 38403715266](https://github.com/Command1264/InviteCodeKt/actions/runs/38403715266)。此為該 PR 的證據，本文件候選尚未執行遠端 CI，不把它說成新候選已通過 CI。

原始證據封存於 `D:\Code\Java\Plugin\InviteCodeKt\artifacts\merged-issue12-1405745\`；`archive-manifest.json` 記錄原來源、逐檔雜湊、三核心驗收及 PR。舊 Worktree 已安全移除，索引改指向封存檔案，原始 log／manifest bytes 保留。該封存排除可重建的 node_modules、依賴副本及隔離 MySQL 資料目錄；固定 JAR 與原 Human 備份另外保留。

## 明確保留的限制

- Folia 26.1.2 維持實驗性；本次最終產物的 Folia Vault／PlaceholderAPI 整合未驗證。Paper 結果與舊產物的 Folia provider 證據不替代本產物驗證。
- MySQL 的正式驗證系列是 8.4；MariaDB 未承諾相容。JSON 僅適合小型單實例與資料交換，不是多服共寫方案。
- 原生 Dialog 視覺與剪貼簿使用者體驗以使用者確認為據，合成封包不等同真人操作。舊版 Spigot 的複製操作是填入聊天列供手動選取。
- 外部效果無法判定時仍為 UNCERTAIN，只能經具稽核的管理處理決定重試或視為已交付，不承諾外部服務 exactly-once。
- 後續排版與美術改善已記錄為另行討論，非本次功能驗收失敗，也未在本輪新增設計需求。

## 接下來的交付順序

1. 審閱並授權本文件候選 push／建立目標為 `develop` 的 PR；不需重做已確認的三台人工測試。
2. 收尾文件通過檢查並取得指定 commit 的合併授權後整合；另取得 GitHub Issue 操作授權，在 #12 留下證據再關閉。#25／#26 的實作已合併但 Issue 尚開啟，亦須依各自驗收與授權核對結案，不直接批次關閉。
3. 依 [Roadmap #1「發行門檻」](https://github.com/Command1264/InviteCodeKt/issues/1)規劃 1.0 發行：確認版號、正式候選來源、JAR 雜湊、穩定目標 `main`、支援／已知限制與發行說明。版本調整若產生新 JAR，要補相稱的產物驗證。
4. `main` 整合、tag、GitHub Release 與 artifact 發布各依明確授權執行；本輪完成不代表已發布。

GitHub 狀態為 2026-10-13 核對時點，後續操作以實際 Issue／PR 狀態為準。本輪不啟動或修改測服，不操作 InstanceHarbor。
