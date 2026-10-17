# 指令回饋與 messages 實作、測試計畫

日期：2026-10-18。整體顯示規格已通過；使用者已授權沿用功能 Worktree 完成第二階段，明確暫不 push。[第一階段證據](../testing/evidence/2026-10-18-command-feedback-stage1.md) 記錄底層實作；[第二階段證據](../testing/evidence/2026-10-18-command-feedback-stage2.md) 記錄輸出整合、封裝與檢查。第三、四階段仍未開始；以下保留完整計畫與 remote 邊界，不表示後續階段已授權。

## 基線與分支

- 已唯讀查詢遠端：`develop` 與 `origin/develop` 均為 `5041c701f824ba5934a0647f2db52a475f503bca`；主要 checkout `D:\Code\Java\Plugin\InviteCodeKt\repo` 乾淨。
- 已使用 branch：`codex/feat/command-feedback-messages`。
- 已使用 Worktree：`D:\Code\Java\Plugin\InviteCodeKt\worktrees\feat-command-feedback-messages`。
- 起點為開始實作時重新核對的最新 `develop`，未來 PR target 為 `develop`。
- 現有 `codex/release/1.0.0` HEAD `e8e2b1f` 的產品原始碼與 develop 相同，差異只有文件及版號；此功能不依賴未合併的 runtime 修改，不需要先合併 release。
- 第一階段已把明確列出的規格、雙語草案、help 核對表及本計畫複製到功能 Worktree；release 來源保留，不搬移、不 stash、不 cherry-pick 整條發行歷史。第二階段同步更新功能 Worktree 的參考文案，執行期權威為 plugin 的 `config/messages/`。
- release 的歷史驗收文件、其他 Worktree 及 F205 產物保持原樣。功能分支沿用 develop 版號，不夾帶 1.0.0 升版；功能合併後另行規劃發行候選重建及複驗。
- 此次已核准第一、二階段的本機實作、驗證與可回復的 task commit；GitHub Issue 建立、push、PR、merge、tag、發布皆需對應的明確授權，尚未執行。

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

第一、二階段沿用上述 task Worktree，完成訊息底層與輸出整合；實際檢查結果及限制見各階段證據。建議下一步是第三階段 Agent 真實核心矩陣，先核對八個 profile 的核心／JDK／資源及資料隔離，再執行已授權的測試；目前尚未取得第三階段執行授權。不需要先合併 release 或進行任何 remote write。
