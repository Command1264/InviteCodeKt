# 第四階段：三台固定測服部署與啟動

2026-10-18 使用者明確授權停服備份後部署三台、重設 B、解除 Folia 指定舊 CLAIMED，
保留歷史與稽核，再啟動核對；暫不 push。沿用 `codex/feat/command-feedback-messages` 與原 Worktree。
程式來源仍為 `dfbc39c`，沒有重建 JAR；部署來源 checkpoint `5af0cb5`。

## 完成結果

三台已裝唯一的 `InviteCode-0.1.0-SNAPSHOT.jar`，SHA-256：
`E350DCA507D1FB953A2B785474708226BFBD682CBFE5D44E7E9BF0D0630C6C8B`。
完整雙語 messages 已部署；storage、rewards、密鑰原值保留。
實際 build／JDK／當次 run／備份位置見[機器可讀證據](2026-10-18-command-feedback-stage4-deployment.json)。

| 固定測服 | 本輪實際核心 | 實際 JDK | Port | 啟動／doctor |
| --- | --- | --- | --- | --- |
| invitecode-spigot-1.8.8 | git-Spigot-3c60ece-741a1bd | 1.8.0_501 | 10101 | Done、ACTIVE、Dialog NONE |
| invitecode-paper-26.3 | Paper 26.3 build 202 | 25.0.4.1 | 20102 | Done、ACTIVE、Dialog PAPER |
| invitecode-folia-26.1.2 | Folia 26.1.2 build 8 | 25.0.4.1 | 30101 | Done、ACTIVE、Dialog PAPER |

三台目前 Managed／Listening／ConPTY，當次核心版本來自新啟動 log；Java 路徑另以子程序 PID 核對。
每台以當次 run 發送 help、doctor、console code 拒絕及有效 reload，均觀察到對應輸出。
中文 log 以 UTF-8 嚴格解碼，沒有 U+FFFD；實際原生主控台字色、粗細仍需人眼驗收。
畫面、hover、剪貼簿、點擊與真人切服沒有由 Agent 代為驗收。

## 備份與資料邊界

三台在 stopped、無 listener、無待執行核心更新、持有 `.testlab-manager.lock` 時備份。
Spigot／Paper／Folia 分別核對 19／18／19 個檔案，包括舊 JAR、完整 InviteCode 資料目錄
（DB、key、YAML、語系）、LuckPerms JSON／設定、metadata 與 ops。備份 DB 的 quick_check=ok、schema 9。
備份原始選定檔案合計 60,207,155 bytes；另保留停用原 JAR 與操作證據，不把它當成可立即刪除暫存。
備份含私有資料，不提交 Git。

沒有複製整個世界：世界原位保留，部署前後 level.dat hash 相同；正常啟停的世界儲存不宣稱逐位元組不變。
部署時 DB、key、storage、rewards、權限與 ops 保持原 hash，之後才執行已授權帳號操作。
三台資料庫沒有重建或 migration，密鑰逐位元組與停服備份相同。
舊 Paper 26.3 `InviteCode-1.0.0.jar` 移入備份，沒有與新 JAR 同時放在 plugins 載入。

## 帳號操作與稽核

先核對各服沒有玩家在線，A／B UUID 與舊 Invitation 方向唯一且一致，再執行管理指令。
三台 B 的 generation 0 → 1；A 紀錄、有效邀請碼、舊 Reward 與舊 Invitation 列均保留。
Spigot／Paper 的舊 QUALIFIED 不改；Folia 唯一舊 CLAIMED 經 inspect 核對後單獨 unlink，
再次 inspect 確認 UNLINKED 才 reset B。沒有直接寫入 SQLite。

只讀 DB 核對：三台新 audit 分別 +1／+1／+2；只有 Folia 指定列的 status 改成 UNLINKED，
沒有新建 Invitation 或 Reward、沒有待處理／結果不明獎勵。B 新登記視窗最早於臺灣時間
2026-10-19 14:38 到期。後續真人建立的新列及其 generation／獎勵恰一次仍待驗收後核對。

## 啟動時發現與處理

1. **Spigot 的有效 Java 選版與 metadata 不同。**原本實例設定為 inherit，繼承全域 mise local-25。
   已正常 stop、核對 exit 0／outputDrained、備份本台管理設定，再以 SettingsSet 的 revision 檢查
   將本台改為 mise local-8。全域 revision 不變，重新啟動後核對實際 java.exe 與版本。
2. **工具輸出編碼。**最初 Python 輸出沿用 Windows 編碼，工具讀取時看似亂碼；
   核對原始 log 是正確 UTF-8，後續工具使用 `python -X utf8`。沒有修改插件文案來補償轉讀問題。
3. **Spigot 既有 LuckPerms JSON 寫入失敗。**測試權限設定觸發 `NoSuchMethodError:
   com.google.gson.stream.JsonWriter.value(java.lang.Boolean)`，後續讀取指出 JSON 未完整寫入。
   這不是 InviteCode exception，也沒有修復第三方插件。當次新建的 B 權限檔原本不存在；
   正常停服後將失敗檔移入本台備份作證據，保留舊使用者權限不變，再重啟。
   此輪 Spigot 改用基本玩家權限 `invitecode.command.basic` 當資格條件，B 最後同意後立即達資格，
   A 須在最後同意前回 Lobby；不再要求 Spigot 寫 LuckPerms JSON。當次重啟 log 無 exception。
   舊 LuckPerms 的 JSON 寫入限制仍存在，不把繞開該測試依賴描述為修復完成。
4. Paper／Folia 的 B 測試資格已設 false 並核對落盤與 permission check=false。
   Paper 26.3 的 B 原有直接 wildcard true，為一般玩家 help 暫時移除，原檔已備份；驗收後需還原。
   一次等待英文成功字串因 LuckPerms 實際使用繁中而逾時；先核對落盤狀態後才繼續，沒有重送已完成的 unset。

上述測服設定調整與已授權準備直接相關；沒有新增依賴、改核心、修 InstanceHarbor 或更改共用設定。
JLine 關服問題仍保持先前暫緩，不做其隔離測試。

## 已核對與待完成

獨立 reviewer 已檢查備份／部署腳本的範圍、鎖、hash、DB/key/world 保留與唯一 JAR；
指出中斷時需查 journal 人工恢復，沒有自動 rollback，本輪沒有部署中斷。
完成後第二次獨立唯讀覆核實際 Status、port/PID、備份及 SQL：A／邀請碼／Reward 逐列保留，
B generation、單筆 Folia UNLINKED、audit 增量與臨時權限均相符，未發現 Critical／High。
此覆核沒有發送伺服器命令，也不取代真人驗收。
操作原始 log、run 與私有資料清單留在 Worktree `.run/command-feedback-stage4/`，必要備份留在固定實例內。

下一步依[現在可執行的兩帳號清單](../command-feedback-human-acceptance.md)完成三台集中真人驗收，
再核對新資料與還原臨時設定／權限、停止不再使用的後端。
Paper 26.2 Human 未變且未啟動，其隔離 adapter 自動複驗尚待執行；最新 Folia ALPHA 缺口仍保留。
Agent 部署／啟動檢查通過不等於真人驗收、合併或發布。本輪沒有 push、PR、merge、tag 或 release。
