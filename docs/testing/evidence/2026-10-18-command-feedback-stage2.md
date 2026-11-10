# 指令回饋與 messages：第二階段證據

日期：2026-10-18。範圍為[實作計畫第二階段](../../plans/command-feedback-implementation-plan.md#第二階段輸出整合與封裝)。本機實作與 Agent 檢查完成；真實核心矩陣及集中真人驗收仍未執行。這不是新產物的使用者驗收或發布紀錄。

## 版本與授權邊界

- 基線 develop：`5041c701f824ba5934a0647f2db52a475f503bca`。
- 沿用 branch `codex/feat/command-feedback-messages`；第一階段 checkpoint `d6442a934436fd0c4186b6eb0c1ac958ccd980af`。
- Worktree：`D:\Code\Java\Plugin\InviteCodeKt\worktrees\feat-command-feedback-messages`。
- 本文件與第二階段程式碼一起提交；最終 checkpoint 用該提交識別，避免把前一階段 commit 誤認為本輪產物版本。
- 使用者已授權第二階段本機實作／測試，明確暫不 push。沒有 GitHub write、PR、merge、tag 或發布，也沒有部署 Human、啟動伺服器或修改 InstanceHarbor。
- `repo/` 保持 develop；既有 release Worktree、F205、歷史驗收、固定 Human 世界及資料不變。關服終端例外繼續暫緩，沒有隔離測試。

## 完成的輸出路徑

- 一般指令、reload／doctor、管理查詢、多行 status／stats、聊天、Dialog 及 MESSAGE 資格通知使用同一份已解析文字片段；一份完整回覆只加一次 prefix。logger 不額外加 prefix。
- help 對照全部 20 個 parser route ID，保留 `/ic`、`admin reload` 別名，依權限與玩家身分篩選；玩家用法只用 `SUGGEST_COMMAND` 填入。說明是獨立受信任模板，重設樣式不繼承用法列粗體。
- 聊天確認使用七個邏輯區塊，prefix 獨立無尾空白、期限不帶冒號，綠紅同意／不同意及 hover 不另加 prefix。空 prefix 不留空白行；Dialog 改用有意義的標題且不重複正文標題。
- code 點擊只套在真正 `{code}` 片段，不會誤套到恰好相同的文字。payload 保持原始邀請碼；舊版填入聊天列，新版使用可用的剪貼簿 action。邀請碼保留 HEX 顏色彩蛋。
- 完整繁中／英文資源套用，鍵與變數對等。已有自訂檔不覆寫，缺鍵、缺檔復位及失敗保留快照沿用第一階段測試與契約。
- 動態值不重新解析格式或變數，輸出邊界處理 section sign 與控制字元；不改命令 payload、領域狀態、儲存 schema 或外部獎勵冪等性。Console 使用可讀基本色、不額外套裝飾，不自行發送 ANSI。

## 測試方法與案例

輸出 adapter 的測試先觀察到舊實作缺少色彩／樣式邊界的兩項失敗；help 測試先觀察到舊 help 缺說明、分類及 click 的三項失敗。再完成最小實作。編譯錯誤、fixture 錯誤與後續新增的測試不算作行為 RED 證據。

新增／擴充案例涵蓋：

- `CommandHelpTest`：20 個路由的唯一性、parser metadata、root／reload 別名。
- `RuntimeCommandHelpTest`：一般玩家、部分管理權限、完整管理員、Console、僅共用 help 而沒有空分類、所有重複 usage placeholder 都只建議指令、自訂說明色彩與 reset。
- `RuntimeCommandOutputTest`：Console 的玩家專用指令拒絕有 prefix／色彩，doctor 仍可執行。
- `BukkitMessageOutputTest`：基本色、樣式隔離、Console 無粗體或控制字元、玩家動態值不重解析。
- `BundledMessageCatalogTest`：實際兩份 YAML 的完整鍵／變數對等，20 個 help 說明鍵存在，期限不帶冒號。
- `PlayerMessageLayoutsTest`：實際 loader 的雙語版面、七區塊、空 prefix、RGB、同字面邀請碼不誤綁 click、status 原因只用一份 prefix。
- `PlayerWorkflowPresentationIntegrationTest`：真實 controller、背景 executor 與暫存 SQLite，配合可控排程／Player proxy，走 code、私密聊天、確認一次、取消、逾時、status／stats、重複 claim 前置拒絕。
- `RewardMessagePresentationTest`：真實 Bukkit reward environment 發送一次有 prefix 的資格通知；離線不呼叫輸出，保留 `RECIPIENT_OFFLINE`。既有 reward state／offline recovery 測試繼續通過。
- `ReflectiveNativeDialogInputTest`：typed request 及 callback、關閉／失敗 fallback、原文值、樣式片段及 reset。Adventure 使用可控組件替身；Bungee 使用實際測試依賴反射方法，並修正 Boolean setter 必須查 boxed `Boolean` 的問題。
- 既有 tab、設定復位／reload、儲存、隱私、獎勵與 JAR 驗證繼續由完整 `check` 覆蓋。

這些組合測試沒有啟動 Minecraft，不能當成客戶端 E2E 或真人外觀驗收。

## 建置與產物

工具鏈由 mise 選用既有 `local-11`（11.0.32）與 `local-8`（1.8.0_501），沿用 Gradle Wrapper，沒有安裝 JDK 或新增 production dependency。兩次測試均在子程序明確清空 `INVITECODE_TEST_MYSQL_HOST` 與 `INVITECODE_TEST_MYSQL_PASSWORD`；不連既有 MySQL。

```powershell
mise exec -- .\gradlew.bat check shadowJar --rerun-tasks --console=plain --quiet
mise exec java@local-8 -- .\gradlew.bat check shadowJar --rerun-tasks --no-build-cache --console=plain
```

兩個 JDK 的完整 `check shadowJar` 均 exit 0，66 suites、304 項中 280 通過、24 跳過、0 failure／error。後續只新增一般玩家及完整管理員兩項 help 案例，於兩個 JDK 以 `:invitecode-plugin:test --no-build-cache` 重跑完整 plugin 測試，均 exit 0，help 六項全數通過。最終合併未變更模組及受影響 plugin 的報告為 66 suites、306 項中 **282 通過、24 跳過、0 failure／error**。MySQL 動態 factory 也可能在產生案例前跳過；報表數量不代表完成 MySQL 整合。

初次 Java 8 clean 檢查使用了 Gradle cache，MySQL skip 計數與 Java 11 不同，因此沒有採用該次計數或將其宣稱為實際執行證據。重新以 `--rerun-tasks --no-build-cache` 執行後，35 個 actionable tasks 全數執行，與 Java 11 的 skip 結果一致。這輪沒有連線 MySQL。

- 最終 JAR：`invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar`。
- SHA-256：`E350DCA507D1FB953A2B785474708226BFBD682CBFE5D44E7E9BF0D0630C6C8B`。
- 大小：19,890,059 bytes。
- `verifyPluginJar`／`verifyJava8Bytecode` 通過：plugin.yml 為 ASCII／有效 UTF-8、產品名 InviteCode、api-version 1.13，設定只放 JAR `config/` 路徑，必要驅動及資源存在，class major ≤ 52，Gson relocation／Vault 隔離邊界保留。兩語 YAML 另由實際 parser 測試核對。
- 完整 diff 主審及獨立唯讀輸出路徑審查未發現未解決的 Critical／High 問題；`git diff --check` 通過。僅有 Gradle 既有 deprecation 及測試 proxy 泛型 cast 等警告。
- 主要 HTML 閱讀入口沿用既有 messages 設計頁，更新第二階段狀態；Node 語法檢查、ID 唯一性、章節 anchor、本機來源連結及 JS 控制目標檢查通過。保留原右緣懸浮捲軸、鍵盤及拖曳程式；本輪沒有瀏覽器排版驗證，不把靜態檢查當作畫面證據。

## Dialog 置中的直接依據與限制

原生 Paper／Spigot adapter 使用 `plain_message` 訊息區塊，不加入補空格或每秒重畫。確認按鈕以文字綠／紅呈現，背景沿用原生客戶端外觀。

透過本機官方啟動器的 26.2 metadata 取得[官方 client artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)，以有上限的 HTTP Range／ZIP 讀取單一 `DialogBodyHandlers$PlainMessageHandler.class`，核對 entry 大小及 CRC32 `c0860c20`。class SHA-256 為 `669DC72E37506CB804C8EFDC7756075856696E70838ACC9FBC95229958B59F99`。

使用既有 `java@local-27` 的 `javap -c -p` 讀取該類別；`createControls` 在 bytecode offset 31 載入 true，offset 32 呼叫 `FocusableTextWidget.setCentered(boolean)`。這提供 26.2 原生訊息區塊置中的直接機制證據；沒有下載或驗證完整 client JAR SHA-1，也沒有啟動客戶端。單一 class 暫存隨本輪 clean 清除，不提交官方 class。

26.3、舊版 fallback、不同視窗寬度／字型的實際排版及兩個反射 adapter 的完整 runtime 能力，仍須第三階段核心矩陣與第四階段真人核對。HTML 示意、mock callback、純 JVM 測試與此單一類別證據均不代替這些驗收。

## 下一個里程碑

依實作計畫「第三階段：Agent 真實核心矩陣」，下一步是先唯讀核對八個 profile、現有核心／JDK／hash、未占用 port 與 Agent 資料隔離，再執行對應測試；目前只完成第二階段，尚未執行第三階段。矩陣完成後，才能交付綁定新 commit／JAR 的集中真人清單。未取得新授權前不啟動後續階段、不 push。
