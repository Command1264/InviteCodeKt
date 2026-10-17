# 指令回饋第一階段：訊息與設定底層驗證

日期：2026-10-18。範圍為 [已核准計畫的第一階段](../../plans/command-feedback-implementation-plan.md)。本文件記錄 Agent 驗證，不是遊戲內或真人驗收。

## 來源與範圍

- base：`develop`，`5041c701f824ba5934a0647f2db52a475f503bca`；建立前已透過 `git ls-remote` 核對。
- branch：`codex/feat/command-feedback-messages`。
- Worktree：`D:\Code\Java\Plugin\InviteCodeKt\worktrees\feat-command-feedback-messages`。
- 版本沿用 `0.1.0-SNAPSHOT`，沒有合併 release、修改版號、建立 Issue、push 或發布。
- 已完成訊息呈現底層、內建語系備援、單次變數替換、prefix 配置驗證、缺檔安全復位與設定快照回歸。沒有更改 InviteCodePlugin／PlayerWorkflowController／Dialog adapter 接線。
- 執行期語系只新增 `presentation.prefix`；完整新文案仍在 `docs/plans/*.draft.yml`。目前遊戲內輸出不會自動變成新設計。

## RED → GREEN 證據

1. 新增「動態值包含另一個 placeholder」案例，原本 `LocalizedMessages.resolve` 連續 replace 導致 AssertionFailedError；改為只掃描模板一次後通過。
2. 新增 prefix 回覆案例，初始呈現入口沒有 prefix／色碼處理時 AssertionFailedError；完成單次 prefix、獨立行、NONE 與空 prefix 後通過。
3. 內建備援與空 prefix 測試先分別出現 AssertionFailedError／IllegalArgumentException；完成保留自訂語系優先序的備援及 prefix 解析例外後通過。
4. HEX／裝飾／console 測試先失敗，完成 RGB 與基本色分流、console 去裝飾及暗色處理後通過。
5. loader 首次 load 自動補缺檔、partial catalog 備援，以及 installer 中斷寫入不留下半份目標檔先失敗；完成從 JAR 復位與暫存後發布後通過。

測試撰寫中曾修正 JUnit assertThrows import；這項編譯錯誤不計入行為 RED 證據。缺少內建資源原本就會明確失敗，該測試保留作回歸，不宣稱它是新增行為。

## 執行結果

| 檢查 | 結果 |
| --- | --- |
| local Java 11：`mise exec -- .\gradlew.bat check shadowJar --console=plain` | BUILD SUCCESSFUL |
| local Java 8：`mise exec java@local-8 -- .\gradlew.bat check shadowJar --console=plain` | BUILD SUCCESSFUL；有使用既有 Gradle cache，受影響訊息／設定測試實際重跑 |
| Java 8 bytecode 與插件 JAR 內容驗證 | 隨 check 通過 |
| 最終 XML 報告 | 58 suites，280 個已報告案例；0 failure、0 error、3 skipped，即 277 通過 |
| 本輪直接相關測試 | LocalizedMessages 5、MessagePresentation 6、RuntimeConfigurationAssembler 5、BukkitConfigurationLoader 6、BundledConfigurationInstaller 3、MessageConfigurationStageOne 2，共 27，皆通過 |
| `git diff --check` | 通過 |

獨立子代理唯讀審查已覆核實際共享 Worktree 的 formatter、fallback、loader／installer 與測試，沒有保留已確認的實質問題。曾提出動態值解析疑慮，重新讀取實際呼叫與解析順序後確認是誤讀並撤回；主 Agent 同時核對了完整 production diff 與測試結果。審查不代替 runtime／真人驗收。

工具鏈實際為 local-11 的 Java 11.0.32，以及 local-8 的 Java 1.8.0_501；沒有安裝或修改全域工具鏈。MySQL 測試的 host／password 僅在當次程序清空，以保證不連線未核對的 DB；MySQL 相關動態測試可能在產生案例前跳過，最終 XML 的 3 skipped **不是**所有未執行 MySQL 情境數量。此輪不能宣稱 MySQL 整合已驗證。

原始 XML／HTML 保留在各 module 的 `build/test-results/test/` 與 `build/reports/tests/test/`。後續重跑可能更新這些 build 報告；本文件保留本輪結果摘要。

## 重要回歸邊界

- 動態字串 `&#000000&l{status}` 保持字面文字，不會改色、加粗或展開其他變數；樣式片段與純文字分開保存，後續 adapter 不能重新解析這些動態值。
- 同列／獨立行 prefix 只加一次，空 prefix／只有樣式的 prefix 不產生空格或空行；prefix 的樣式不漏到正文。
- 舊版 HEX 使用既有近似基本色策略；console 保留成功／提醒／錯誤色彩、去除裝飾，近黑轉灰，其他過暗色在標準黑底可讀基本色中選最近值。
- 備援順序為自訂玩家語系→自訂預設語系→內建玩家語系→內建預設語系。每個快照的相同查詢語系／鍵只對內建備援警告一次。
- 已有設定、損壞設定、自訂語系、資料庫與 anti-abuse key 保留；寫入失敗不留下半份目標檔。新測試用 @TempDir 中的合成資料，不接觸既有資料或金鑰。
- 用真實 loader、MessagePresenter 與 AtomicRuntimeSnapshot 組合驗證：有效候選可安裝，損壞替代檔不改寫磁碟內容，也不取代前一份快照。

## 本機產物與限制

- JAR：`invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar`。
- SHA-256：`C3A1482EB94A45D264A26C1FA241997B17DBC44EE9B6A134B1C61606689A8837`。
- 大小：19,855,807 bytes。
- 不變更 F205／1.0.0 發行候選及其歷史驗收證據，不將此產物部署到 Human 測服。
- 沒有啟動 Minecraft／InstanceHarbor、沒有隔離關服測試、沒有 MySQL 連線、沒有遊戲內截圖或真人驗收。

第一階段完成後，下一步建議依計畫第二階段接入指令、聊天、help、status／stats、Dialog 與 MESSAGE 獎勵，並套用完整雙語文案。使用者此次只授權第一階段；第二階段尚未開始，需另行確認。推送與 PR 也仍未授權。
