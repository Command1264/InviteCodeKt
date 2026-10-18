# 指令回饋與 messages 實作、測試計畫

日期：2026-10-18。使用者已授權沿用功能 Worktree 執行第三階段，明確暫不 push。[第一階段證據](../testing/evidence/2026-10-18-command-feedback-stage1.md) 記錄底層實作；[第二階段證據](../testing/evidence/2026-10-18-command-feedback-stage2.md) 記錄輸出整合與封裝；[第三階段證據](../testing/evidence/2026-10-18-command-feedback-stage3.md) 記錄八個選定核心及兩個網路玩家流程。Agent 可執行範圍已通過，保留最新 Folia 及渲染客戶端缺口；第四階段尚未開始。

## 基線與分支

- 已唯讀查詢遠端：`develop` 與 `origin/develop` 均為 `5041c701f824ba5934a0647f2db52a475f503bca`；主要 checkout `D:\Code\Java\Plugin\InviteCodeKt\repo` 乾淨。
- 已使用 branch：`codex/feat/command-feedback-messages`。
- 已使用 Worktree：`D:\Code\Java\Plugin\InviteCodeKt\worktrees\feat-command-feedback-messages`。
- 起點為開始實作時重新核對的最新 `develop`，未來 PR target 為 `develop`。
- 現有 `codex/release/1.0.0` HEAD `e8e2b1f` 的產品原始碼與 develop 相同，差異只有文件及版號；此功能不依賴未合併的 runtime 修改，不需要先合併 release。
- 第一階段已把明確列出的規格、雙語草案、help 核對表及本計畫複製到功能 Worktree；release 來源保留，不搬移、不 stash、不 cherry-pick 整條發行歷史。第二階段同步更新功能 Worktree 的參考文案，執行期權威為 plugin 的 `config/messages/`。
- release 的歷史驗收文件、其他 Worktree 及 F205 產物保持原樣。功能分支沿用 develop 版號，不夾帶 1.0.0 升版；功能合併後另行規劃發行候選重建及複驗。
- 此次已核准第一至三階段的本機實作、驗證與可回復的 task commit；GitHub Issue 建立、push、PR、merge、tag、發布皆需對應的明確授權，尚未執行。

## 範圍與方法

驗收依據為 [已通過的顯示規格](command-feedback-display-spec.md)、[help 核對表](command-feedback-help-reference.md) 與兩份語系草案。

1. 訊息模板／輸出處理：以少量共用處理統一 prefix、色彩與純值片段，將模板格式和動態值隔離。平台 API 留在 adapter；不改邀請、資格、獎勵交付與儲存契約。
2. 各發送路徑接入：命令、help、status／stats、管理查詢、互動聊天、Dialog、MESSAGE 獎勵。保留玩家左對齊與 Dialog 置中；不以空格補位置。
3. 雙語 messages、prefix 設定、缺鍵備援、啟動／reload 缺檔復位，保留自訂檔案與原本儲存切換限制。
4. Console 16 色、不額外套粗體、HEX 降色並避開過暗結果；沿用伺服器輸出能力，不插入自製 ANSI 控制或引入終端依賴。

不新增 production dependency、資料庫 schema migration、排行榜、每秒倒數或新的指令用途。關服終端例外維持暫緩，不做其隔離測試，也不修改 InstanceHarbor。

## 四個實作與驗收階段

### 第一階段：訊息與設定回歸

先寫會失敗的行為測試，再完成最小實作：

- prefix 同列／獨立行／空字串、不重複、不留尾端空格及多餘空行；純值、hover、按鈕不另加。
- 繁中／英文鍵與變數一致、現有自訂文字不被替換、語系選擇與缺鍵備援。
- 基本色與 HEX、玩家舊版降色、console 16 色與裝飾樣式去除、黑色／過暗輸入仍可讀。
- 動態文字含 `&a`、`&#000000`、大括號等不會被再次當作模板；複製與命令 payload 保持原值。
- 缺少內建檔案時補回；已有檔案逐位元組不變；無法讀寫／損壞檔明確失敗；reload 不切換到半套設定。

證據：測試結果與對應案例清單；不是僅對輸出常數逐字自我比對。

### 第二階段：輸出整合與封裝

- 每條 help 用法對照 20 個 parser 項目，測一般玩家、部分管理權限、完整管理員與 console；既有別名保留。
- 無參數指令及超出合法位置的 tab 一律空集合，不回落為玩家名稱。
- 測 code → claim 前置檢查 → 輸入／cancel → 確認／不同意／逾時 → 建立關係 → 資格通知／離線補發的必要整合路徑；不改其領域語意。
- 聊天與兩種原生 Dialog adapter 保留點碼、hover、按鈕與關閉／逾時語意。核對實際 API 的置中能力，不靠 HTML 樣例推定可行。
- 依 README，先核對 mise 現有選版與 JDK，再用 Wrapper 執行 `check shadowJar`；補做既有 local Java 8 相容性驗證，不安裝新 JDK。
- 檢查 JAR 的 plugin.yml、UTF-8、`config/` 資源與雙語 messages、Java 8 class 邊界；保存 commit、JAR SHA-256 及測試摘要。更新操作文件、設定說明與 CHANGELOG。

沿用既有完整 check；MySQL 整合僅在已核對隔離資料與憑證邊界時執行，不觸及既有 DB。後續若授權 PR，沿用現行 Windows／Linux、Java 8／11 及隔離 MySQL CI，不在本輪觸發 remote。

### 第三階段：Agent 真實核心矩陣

沿用 `test-servers/matrix.json` 的八個 profile，不以三台真人測服代替發行矩陣：Spigot 1.8.8／1.12.2／1.16.5／latest、Paper 1.20.6／1.21.8／latest、Folia latest（實驗性）。

- 開始前以 InstanceHarbor 現行文件與實際資源核對版本、build、JDK、SHA、未被占用的 port，將 latest 鎖到本輪實際核心；不把舊的 latest 名稱當成版本證據。
- Agent 實例與 Human 分離，不接人工 Velocity；優先重用驗證過的不可變核心。若舊 runner 與現行管理器契約不符，先回報，不擴張修管理器。
- 每個 profile 做載入、console help／doctor／reload、色碼無外洩、缺檔復位與重啟保留檢查；用隔離資料，不重建既有世界。
- 對可支援的客戶端自動化先核對工具與版本，再補真實聊天／Dialog 路徑與截圖證據；不把合成 callback、console smoke 或缺少客戶端覆蓋說成真人通過。缺工具不擅自安裝依賴或刪減驗收。
- 失敗保存最小證據；停止本輪擁有的程序，保留待驗收 JAR 與必要資料，不清除固定 Human 世界。

本輪結果：八個選定核心 smoke 與 failed-reload／退出稽核通過，Spigot 1.8.8 聊天及 Paper 1.21.8 原生 Dialog 實際網路回呼／離線補發通過。使用者明確同意 Folia 以最後 STABLE 26.1.2 build 8 替代；26.3 ALPHA 未驗證。現有 mc-pilot 沒有已建立客戶端，也沒有 26.3／1.21.8 Fabric 變體，未安裝依賴；沒有渲染畫面、滑鼠或剪貼簿驗收，26.3 Dialog 玩家路徑仍未驗證。不以此宣稱所有遊戲內與真人門檻通過。

所有本輪程序與 port 已釋放；自動核准審查拒絕刪除可重建 `data/`，約 1.70 GiB 仍保留，磁碟清理狀態為 `retained-cleanup-blocked`。未將測試通過等同清理完成；路徑與證據見第三階段摘要。

### 第四階段：集中一次的真人外觀驗收

優先使用既有固定 Spigot 1.8.8、Paper 26.3、Folia 26.1.2 測服，準備時重新核對其存在、核心 build 與路由；現有 Paper 26.2 做受影響 adapter 的 Agent 複驗，若能力路徑不同再補真人項目。不為本計畫新建或啟動伺服器。

交付完整操作清單，綁定新 commit／JAR hash；玩家可集中完成：

1. 兩帳號分工：A 建立／查看邀請碼並測複製／hover；B 測 claim 的前置拒絕、聊天 cancel 或 Dialog 輸入。
2. 確認七區塊、玩家聊天靠左、Dialog 置中、無尾端空格／期限冒號、綠紅按鈕；同意／不同意／逾時各一次。
3. help 只填入不立即執行；不同權限可見列正確；status／stats 與資格通知用詞清楚。
4. 管理員操作 console help／doctor／reload，檢查 16 色、無裝飾粗體與暗色不可讀問題；玩家專用指令的拒絕正確。
5. 繁中／英文、有效 reload、失敗保留、正常重啟後設定與資料保留。離線獎勵補發不因顯示改動而重複。

三種平台盡量共用同一清單，不要求每個開發 checkpoint 反覆真人測試。新產物尚未經真人核對前，只回報「Agent 驗證完成，待使用者驗收」。

## 停止條件與後續

- 新增 dependency、實際格式不相容、改資料契約、或平台 API 無法達到已核准 Dialog 置中時，先說明具體限制及替代選項，不默默降低規格。
- 核心／客戶端不可用、環境邊界不明或已有程序占用時，只暫停相依測試，持續完成可獨立工作；清楚列出未驗證項目，不直接宣布通過。
- 既有 release Worktree 的相關草案以本任務具體檔案清單複製；其他 dirty／untracked 不納入，不廣泛 stage。
- 整體驗收後才請使用者核准該版本的 push／PR／merge；發行候選重建、回流 release／main、tag 及發布另行規劃。

第一至三階段沿用上述 task Worktree；實際檢查結果及限制見各階段證據。建議下一步依第四階段準備集中真人外觀驗收，先唯讀核對固定測服的核心／JAR／路由，再按明確部署安排更新目標產物及交付操作清單。這輪沒有部署或真人操作，也不需要先合併 release 或進行 remote write。

2026-10-18 第四階段準備：固定四台均可被現行管理器發現且已停止，三台主測服選定
Spigot 1.8.8、Paper 26.3 build 202、Folia 26.1.2 build 8；Paper 26.2 實際為 build 134。
兩台 Paper 的 hash 已與核心更新紀錄對上，不能再採用建立 metadata 或舊啟動 log 的 build。
候選 JAR、雙語 messages 及逐台設定提案僅放在本機準備區，沒有部署、啟動、修改帳號或 push。
既有 B 均已登記，因此全流程重測須先確認資格重設及其資料影響；不是刪除 DB 或世界。
詳見[完整步驟與部署安排](../testing/command-feedback-human-acceptance.md)及
[機器可讀 preflight](../testing/evidence/2026-10-18-command-feedback-stage4-preflight.json)。
下一個可執行項目是停服備份後部署三台與核對當次啟動，再交付集中真人驗收；
Paper 26.2 受影響 adapter 的 Agent 複驗仍未完成，與最新 Folia ALPHA 缺口分別保留。

同日後續已取得明確部署／啟動／B reset／Folia 單筆 unlink 授權並完成，詳見
[部署與啟動證據](../testing/evidence/2026-10-18-command-feedback-stage4-deployment.md)。
三台目前可進行真人驗收；Spigot 的本台 Java 已核對為 local-8，Paper／Folia 為 local-25。
Spigot 既有 LuckPerms JSON 寫入限制以不寫該權限的資格測試安排避開，未修復第三方插件。
下一步是[集中真人清單](../testing/command-feedback-human-acceptance.md)，其後核對新資料、還原暫時設定與權限。
本機文件與測服操作不等於 remote 授權，仍未 push。


## 2026-10-18 真人回饋：分層 help 與權限 Tab

依使用者批准沿用目前分支修正首頁資訊過量；Agent 測試、三台停服備份／部署／啟動完成。
沒有重設 B、unlink 或改寫世界。新候選與結果見
[本輪證據](../testing/evidence/2026-10-18-command-feedback-help-deployment.md)，
下一個尚未完成且前置條件已滿足的項目是第四階段真人 help 複驗，接續同一份
[驗收清單](../testing/command-feedback-human-acceptance.md)。沒有 push 或發布。


## 2026-10-18 Help 框線與固定導航回饋

依已批准方案沿用分支加入同列 prefix／標題框線及固定三項導航；停用項目沒有互動事件。
新候選 `fb303e2` 已完成 Java 8／11 與相關回歸驗證，三台備份部署，沒有 push。
下一個可開始但尚未完成的項目仍為第四階段真人外觀複驗，先完成
[本輪六項清單](../testing/command-feedback-human-acceptance.md)，之後續未完成流程。


## 2026-10-18 僅 Folia 外觀階段

使用者要求先只開 Folia 26.1.2，暫不做詳細流程。已回饋多層選單大致正常；
修正首頁 `/ic help` 為直接唯讀導覽，與管理說明一致，其他操作仍只填入。
後續前置條件已滿足的下一步是 Folia 首頁 help 點擊及外觀局部複驗，不是啟動其他兩台。
玩家名稱／紀錄選取議題已保存至專案容器 notes 草案，延後到本分支完成後再開發；無 schema 變更。


## 2026-10-18 框線與父頁返回修正

本輪修正近似字寬框線補長與權限篩選後的父頁定位，新增命令沿用共用規則。
下一步仍僅是 Folia 的框線／第 2 頁返回局部驗收，見最新真人清單；其他兩台不啟動。
真人通過後再討論詳細測試，玩家導向管理草案仍延後，不混入目前分支。沒有 push。


## 2026-10-18 Folia 局部真人驗收通過

使用者確認 `760c51a` 在 Folia 26.1.2 的框線可接受，reload／doctor／storage 返回第 2 頁正常。
[真人證據](../testing/evidence/2026-10-18-folia-help-partial-human-acceptance.json) 記錄精確範圍；
不推定其他核心或完整第四階段通過，不重做這四項。
下一個前置條件已滿足的項目為 Folia 剩餘指令面板／基本訊息（首頁點擊、停用按鈕、權限 Tab、
status／stats、複製及 console），原因是可以沿現有候選繼續驗收且無需變更登記／獎勵資料。
之後再安排雙語 reload、兩帳號確認／補發／重啟；開始 claim 前需重新核對 B。
其他兩台仍延後，沒有自動啟動、部署、push 或合併。


## 2026-10-18 Console 與密集資訊排版

Console 的逐行 log 標頭、一般回覆每行 prefix、後台 help 去重／標題／文字導航已實作。
doctor／inspect／離線工具及中英文可見分號拆行，玩家報告只加一次標題 prefix。
Java 8／11 及 Agent 檢查完成，僅 Folia 部署；下一步為最新清單的 Console 與玩家報告外觀驗收。
前版局部通過保留，其他未測不推定通過；不啟動其他測服、不混入玩家紀錄導覽草案、不 push。
