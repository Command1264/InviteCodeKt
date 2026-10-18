# Console 與逐欄報告：Agent 驗證及 Folia 部署

候選 `53893c475fdeaf631ca5b174493f33754660e0ad`，SHA-256 `6E7EB72D59896AF141FE17029FDAD5A0322EADA7F019929F5E78D11C20C8664E`，19,922,750 bytes。
沿用目前分支；僅 Folia 26.1.2 port 30101 更新。沒有 push、PR、merge 或發布。

## 變更與證據

Console 邏輯行分別送給核心，一般回覆每行 prefix，框線 help 頂端 prefix 一次；
原生時間戳／INFO 不由插件偽造。Console help 單一入口、後台標題與有效文字導航；玩家固定三按鈕保留。
doctor／inspect／離線工具及雙語分號句與密集欄位拆行，玩家報告只加一次標題 prefix。
核心與 storage 共16檔去除字串後比對一致，SQL resources 沒有改動，沒有 schema／交易／管理規則變更。

先用測試重現 console 一次送多行的失敗，再修正為通過。
Java 8／11 均以 mise exec 與 Gradle Wrapper 執行 check shadowJar，326 cases：302 passed、24 MySQL
條件測試 skipped、零失敗。JAR／bytecode、逐行prefix／顏色、玩家報告prefix一次、雙語唯一鍵／變數／
無分號及逐欄、console去重／導航檢查通過。獨立 review 的布林標籤／報告標題已修正，無未處理Critical／High。

## 部署與實際核心核對

Folia 原先已停止，確認無host／port釋放並持管理鎖新建備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\console-layout-20261018-104918-079ad994`，逐檔hash通過後
只換 JAR／完整雙語文案／workspace產物識別。世界、設定、權限、DB及key在部署期間不變。
啟動 run `f0d0a1687ee5457b94ae9e272e70837e`，當次 Done／ACTIVE，沒有新增錯誤。
root、help、第2頁、reload help、doctor、玩家與邀請查詢的每個非空輸出行皆有原生時間戳／INFO。
doctor13行、玩家查詢10行、邀請查詢9行；各報告行有prefix。無Reward Action紀錄，live reward查詢未執行。

啟動後DB比對僅網路 observed_at／expires_at 刷新，其餘資料列與備份一致；key一致。
沒有回灌舊DB、reset、unlink、資格／獎勵／邀請歷史變更。其他兩台仍停止、沒有更新。
離線匿名化／搬移只以隔離fixture測試，沒有在Human執行。

## 下一步

Agent驗證完成，待使用者依[最新Folia清單](../command-feedback-human-acceptance.md)驗收Console色彩／
導航及玩家報告prefix／換行／雙語。前版760c51a局部通過保留，不推定新報告外觀通過。
關服JLine仍暫緩；其他核心、完整互動流程與臨時設定還原等既有門檻未自動完成。
同一HTML同步更新，JS語法通過，不以文件示意或server log代替Minecraft渲染驗收。
