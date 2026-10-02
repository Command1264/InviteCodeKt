# Issue #12：整合審查與發行門檻核對

## 固定審查範圍

- 目標：`origin/develop`，`10ce7b5a3472efb71baf3239c97e51df57ab5584`。
- 來源：`codex/test/final-release-acceptance`，`5d8e0e7`。
- 共同基線：`57b0f5dbc8f1111d3a2a5e379bea7ff737ecd0c7`。
- 差異：`git diff 10ce7b5a...5d8e0e7`；24 個檔案，638 行新增、84 行刪除。
- `git merge-tree --write-tree` 成功，沒有衝突；暫存整合 tree 為 `ec68d7abe31dba52333a466ee0ed9e8526c2fd82`。
- 本輪只建立整合樹及其暫存來源快照，沒有將來源或目標分支合併，沒有 remote write。

## Standards 軸

獨立靜態審查未發現可由專案規則支持的 correctness／safety 問題，也沒有具體值得列出的 code smell。變更保留 storage、privacy、reward delivery 與 Java 8 相容性邊界。主要 runtime 差異可以與最新 develop 的匿名化功能無衝突整合；這不代替真實伺服器驗證。

## Spec 軸

**P1：最終整合候選 JAR 尚未取得同產物發行矩陣證據。** Issue #12 要求「完全相同的插件 JAR 可在版本範圍兩端啟動並執行 critical user path」。舊啟動矩陣使用 `99CE69BE0A38679EDD914AC608C0B0ACC85FE5BCD6A88D80B44FCEF3238BDADA`；本輪真人 UX 操作使用 `889DAD100D56D18C425D7299FD759EAA3DD822C863CF55FF486F7D8F372EFE3C` 加外部語系修正。這些證據不能直接標為最新 develop 加 UX 的整合 JAR 已通過。

其餘指定 UX 項目均有對應實作，未發現明顯 scope creep。這項發現是發行證據缺口，並非已證實 runtime 缺陷。Issue #12 仍保持未完成；不以本輪審查關閉它。

## 主 Agent 整合驗證

將整合 tree 匯出至獨立暫存目錄，透過既有 Gradle Wrapper 與已核對的 `mise java@local-8` 執行 `check shadowJar --no-daemon`。建置、`verifyJava8Bytecode` 與 `verifyPluginJar` 均通過。JUnit XML 共 51 個 suite、230 項測試、0 failures、0 errors、14 skipped；跳過項目均為需要隔離 MySQL 服務的測試，本輪沒有啟動該服務。

整合 JAR SHA-256：`BA2D48E7EB774FAB464F41B8B468A142B1E559C4169B55DDA5EA42A89552C50C`。保留於本 Worktree 的 `invitecode-plugin/build/review-integration/ec68d7a/InviteCode-0.1.0-SNAPSHOT.jar`。產物對應上述整合 tree，尚未部署至任何固定 Human 測服，亦沒有對它取得新的真人驗收。

## 發行條件核對

| Issue #12 條件 | 現有證據與本輪結論 |
| --- | --- |
| 舊版、中間版本及新版 smoke | 舊矩陣已通過；整合 JAR 尚須複驗。 |
| 同 JAR 兩端 critical user path | 舊 UX 產物有合成與真人證據；整合 JAR 尚須複驗。 |
| SQLite 邊界與 JSON／MySQL integration | 整合快照的 SQLite／JSON 本機測試通過；MySQL 本輪跳過。伺服器層級證據仍屬舊產物。 |
| Vault／PlaceholderAPI 有／無 | 舊產物有獨立偵測及效果證據；整合 JAR 尚須代表性複驗。 |
| Folia 實驗性 smoke | 舊產物有 region、聊天、Dialog 封包與真人結果；整合 JAR 尚須 smoke，Folia Vault 仍未驗證。 |
| Reward／migration 失敗復原 | 舊產物及本機測試有證據；舊 SQLite runner 不能直接驗證 schema 9。 |
| 文件與已知限制符合證據 | 真人通過項目已記錄；舊路徑與 runner 需更新，不宣稱完整 1.0 發行通過。 |

## 複驗工具的已知阻擋

1. `scripts/test-servers/synthetic-player/migration-recovery.cjs` 仍要求初始 schema 8，復原訊息也只接受版本 8；最新 develop 使用 schema 9。應保留 v7→v8 故障注入語義，同時正確建立／驗證 schema 9 的完整復原狀態。
2. 同一 runner 硬編碼 `TestLab/instances/InviteCode` 路徑，與目前 `InstanceHarbor/instances/Agent/InviteCode` 邊界不同；`common.ps1` 自動發現也仍搜尋 TestLab。必須明確使用 Agent 實例並保留路徑、metadata、全新資料、loopback、EULA 與雜湊核對，不能只刪除安全檢查。
3. 修正 runner 後，使用同一個整合 JAR 與新 Agent 實例複驗，不重建或覆寫 Human 測服；檢查 matrix port 是否未被使用。MySQL 必須使用專用隔離資料庫，不連到固定測服資料。

## 本機 PR 草稿

建議標題：`完成邀請碼互動與原生 Dialog 確認，補齊真人驗收紀錄`

目標分支：`develop`。在複驗缺口解決前保持 draft，不用 `Closes #12`。

建議說明：

> 邀請碼現在可直接點擊並顯示可翻譯的懸浮提示；Claim 在開啟輸入前檢查資格，聊天輸入支援 cancel。新版原生 Dialog 與舊版聊天提供同意／不同意確認、期限與紅綠文字，確認期限提示移除末尾冒號。三台固定 Human 測服的確認畫面、按鈕、逾時及重啟後資料保留已由使用者確認通過。
>
> 與 develop 的匿名化功能可無衝突整合。整合快照已通過 Java 8 check、shadowJar、bytecode 及 JAR 驗證；230 項測試沒有失敗，14 項隔離 MySQL 測試在本輪跳過。最終整合 JAR 的跨版本與外部整合複驗仍待補齊，Folia 第三方整合保留實驗性限制。Refs #12；詳見本審查紀錄。

## 建議下一步

依 Issue #12「驗收條件」，先修正 InstanceHarbor Agent 路徑與 SQLite schema 9 的測試 runner，再以固定整合 JAR 補跑兩端 critical path、migration／Reward recovery 及代表性 JSON／MySQL／第三方整合。前置條件是維持獨立測試資料、未使用的 port 與已驗證核心；本輪不需要使用者重做剛通過的三台 UX 操作。最後依影響決定是否還需要最終產物的真人快速複驗，並取得 push／PR 及指定版本 merge 授權。
