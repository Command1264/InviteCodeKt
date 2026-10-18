# 訊息與 prefix：第四階段真人驗收

## 目前狀態：help 框線與固定導航已部署，待真人複驗

2026-10-18 原準備已依使用者授權完成三台部署、B 資格重設及 Folia 單筆舊關係解除；本輪只更新 help，不重做帳號操作。
三台當次 `Done`、InviteCode ACTIVE、help／doctor／玩家專用拒絕／reload、UTF-8 log、實際 JDK 與 port 均已核對。
世界原位保留；DB、密鑰與歷史未清空。原部署後因首頁資訊過量重建候選，三台已更新為下列新版；沒有 push。

- 程式來源：`fb303e23bb6acca4e6d97aa9686bc89475ff0166`。
- 已裝 JAR：`InviteCode-0.1.0-SNAPSHOT.jar`，19,914,359 bytes。
- SHA-256：`E8640904EA2963FB741C19A2037C9FD359DF85BAFF2B3F2F9A7EC1F591AE2EBA`。
- [本輪部署與當次啟動證據](evidence/2026-10-18-command-feedback-help-frame-deployment.json)；[原帳號準備與部署證據](evidence/2026-10-18-command-feedback-stage4-deployment.json)。

**現在直接從步驟 1 開始；不要再次 reset、unlink 或建立 B 的邀請碼。**
B 的本輪視窗最早在 **2026-10-19 14:38（臺灣時間）** 到期。若來不及，先回報，由 Agent 重新核對後安排。

## 本輪先驗收：框線、同列標題與固定導航

三台使用相同新候選，沒有再次 reset／unlink。每台經原本 `localhost:25565` 與同名 `/server` 路由進入。
A 使用管理員帳號 Command1，B 使用一般帳號 Taiwan_PingLord；B 不執行 `/ic code`。

1. A、B 輸入 `/ic`：prefix 與「常用指令」在同一條標題線，只出現一次；內容下方有結尾線。
   底部固定 `[上一頁] [返回] [下一頁]`，首頁三項皆深灰，點擊不反應、沒有操作 hover。
2. A 輸入 `/ic help admin`：第一頁上一頁深灰，返回／下一頁黃色。點下一頁到 2/2，
   下一頁改深灰，上一頁仍黃色可回 1/2。點停用項目不新增 help，也不填入聊天列。
3. A 輸入 `/ic help admin invitation transfer`：前後線清楚，三項導航完整，只有返回黃色可用；
   點操作用法仍只填入聊天列，**不要按 Enter 執行管理指令**。
4. B 輸入 `/ic help`，確認同樣框線與三項導航；`/ic help admin` 仍不得顯示管理用法。
   各自按既有 Tab 清單確認權限與空集合行為未回歸。
5. 原生主控台輸入 `ic`、`ic help admin`、`ic help admin 2`、`ic help admin invitation transfer`：
   prefix 同列、前後線與三項導航都顯示，可用項目帶導航命令。Console 仍使用可讀 16 色、不額外粗體；
   深灰在 console 可依可讀性降色，不要求與玩家畫面完全相同。
6. 逐台回覆「核心／客戶端版本；框線與 prefix、固定導航、停用無反應、分頁返回、console：通過／失敗」。
   失敗附指令、預期／實際及截圖；不需再次 reset。通過後續原先尚未完成的其他訊息／確認／雙語／重啟清單。

字型、視窗寬度可能造成換行；不是以空格固定像素對齊。真人未通過前不宣告外觀驗收完成。
本輪證據見 [部署紀錄](evidence/2026-10-18-command-feedback-help-frame-deployment.json)。

## 2026-10-18 分層 help 複驗：先完成這六項

本輪為真人回饋修正，沿用原分支／Worktree。只更新插件與雙語訊息，不再次 reset／unlink。
DB 各資料表在部署前後一致，金鑰、資格設定、權限與世界保留；三台當次啟動及 console 回覆已核對。
本輪不替使用者宣告真人通過。三台各做一次下列清單，之後繼續先前尚未完成的 12 步流程。

1. **連線。** A（Command1，管理員）與 B（Taiwan_PingLord，一般玩家）經 `localhost:25565`，
   各自 `/server invitecode-spigot-1.8.8`、`/server invitecode-paper-26.3` 或 `/server invitecode-folia-26.1.2`。
   每次只選一台；使用對應客戶端版本，記下版本。不要直接連後端。
2. **首頁。** A、B 各輸入 `/ic`。B 應只見有權限的 `code`、`claim`、`status`、`stats`、`help`；
   A 額外一行 `/ic help admin`。不直接列管理用法、confirm 或 cancel。B 只閱讀首頁，**不要執行 `/ic code`**。
3. **分層與點擊。** A 依序 `/ic help admin`、`/ic help admin 2`、`/ic help admin invitation`、
   `/ic help admin invitation transfer`。分類每頁最多五項，有頁碼；詳細頁才顯示完整參數與範例。
   點分類／下一頁／返回應直接開 help；點真正的管理用法應只填入聊天列，**不要按 Enter 執行**。
   B 查 `/ic help admin` 應顯示一般錯誤，不洩漏管理用法。confirm／cancel 只在互動情境提示；明確查其 help 仍有效。
4. **Tab。** A 輸入 `/ic help admin ` 後按 Tab，應見分類與 `1`、`2`；
   `/ic help admin invitation ` 應見三種操作。B 的 `/ic ` 不見 admin／reload，`/ic help admin ` 沒有候選。
   A 的 `/ic admin invitation force-qualify EXAMPLE_INVITATION_UUID ` 應提示設定內的資格 ID，
   **只按 Tab，不送出這個假 ID 指令**。`/ic status `、`/ic stats `、`/ic help admin 2 `、
   `/ic help admin invitation transfer ` 及任意超長路徑的 Tab 都應為空，不冒出玩家名稱。
5. **Console 與錯誤。** 在各台原生主控台輸入 `ic`、`ic help admin`、`ic help admin 2`、
   `ic help admin invitation transfer`、`ic help typo`、`ic help admin 999`。console 只見適用項目，
   分頁列出可輸入的導航命令，錯誤清楚；16 色可讀、不額外粗體、沒有原始色碼。
   也請在原生主控台鍵入 `ic help admin ` 後按 Tab（不按 Enter），確認有分類／頁碼，
   `ic status ` 的 Tab 不回退玩家名稱。原生終端的實際 Tab 行為仍待這項真人驗收。
6. **回報。** 逐台回覆「核心／客戶端版本；首頁、權限、分頁點擊、玩家 Tab、console Tab、顏色：通過／失敗」。
   失敗附操作、預期／實際與截圖。通過後再繼續原清單未完成的確認、雙語、獎勵與重啟驗收。

已知限制：最新 Folia ALPHA 未驗證；本輪沿用 stable 26.1.2。Spigot 的 LuckPerms JSON 寫入問題仍保留，
本清單不修改權限。關服 JLine 問題維持暫緩；本輪正常停止三台皆有成功退出紀錄。
相關：[本輪部署證據](evidence/2026-10-18-command-feedback-help-deployment.json)。

## 選用三台，逐台完成

下表的核心、build 與 JDK 已依實際 JAR、當次啟動 log 及 Java 程序核對。

| 測服／同名 `/server` 路由 | 核心 | JDK alias | Port | 本輪用途 |
| --- | --- | --- | --- | --- |
| `invitecode-spigot-1.8.8` | Spigot `git-Spigot-3c60ece-741a1bd` | local-8 | 10101 | 舊版聊天與填入邀請碼 |
| `invitecode-paper-26.3` | Paper 26.3 build 202 | local-25 | 20102 | 新版 Dialog、複製、完整流程 |
| `invitecode-folia-26.1.2` | Folia 26.1.2 build 8 | local-25 | 30101 | 實驗性平台互動與排程 |
| `invitecode-paper-26.2` | Paper 26.2 build 134 | local-25 | 20101 | 保留；受影響 adapter 的 Agent 複驗尚待執行 |

實例根目錄：`D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\`。
沒有新增實例、更新核心或搬移世界。三台現在為 Managed／Listening／ConPTY，可在既有 InstanceHarbor GUI
按該台的原生後台按鈕開啟主控台。共用 Velocity 25565 與 Lobby 10001 的既有 run 保留。
Paper 26.2 不在本輪部署、reset 或 unlink 範圍，保持停止。

## 已完成的部署與備份

每台先持有管理鎖、確認停止及核心 hash，再備份並逐檔核對：舊 JAR、整個 InviteCode 設定／DB／key、
LuckPerms JSON 權限資料、metadata 與 ops。備份在各實例 `.invitecode-backups/command-feedback-20261018-*/`；
精確路徑見部署 JSON。本備份不包含完整世界，世界原位保留，部署期間 `level.dat` 未變。

部署三台的唯一候選 JAR、完整 zh_TW／en_US 文案及測試資格設定；storage／rewards 與 key 不變。
同網路暫用 ALLOW；測完還原 Spigot／Folia 原 DENY，Paper 26.3 原本就是 ALLOW。

Spigot 原先繼承全域 Java 25，已備份本台管理設定並改為 mise local-8，正常停服／退出後重啟驗證；
其他測服與全域 Java 設定未改。Paper／Folia 實際為 local-25。

**Spigot 的資格測試方式不同：**既有 LuckPerms 5.4.145 在寫 JSON 權限時發生 Gson 相容性錯誤。
這次新建而失敗的 B 權限檔已在停服後移入備份保留證據，原本的權限檔沒有覆寫。
本輪改用 `invitecode.command.basic` 資格條件，B 最後同意後立即達資格；
A 必須在 B 最後同意前回 Lobby。**Spigot 不執行本清單的 LuckPerms 權限寫入指令。**
Paper／Folia 仍用 `invitecode.test.qualified`，已設為 false 並核對持久化與實際檢查結果。

## 兩帳號與既有資料

- **A：Command1**，邀請人，原邀請碼保持不變。A 不 reset、不輪替邀請碼。
- **B：Taiwan_PingLord**，受邀人，三台都已由 Agent 重設為新的登記資格，generation 0 → 1。
  B 沒有自己的有效邀請碼，請不要執行 `/ic code`。

Spigot／Paper 26.3 的舊 QUALIFIED 保留；Folia 指定的那一筆舊 CLAIMED 已成為 UNLINKED，
原列與歷史仍在。三台分別新增 1／1／2 筆稽核，A、原邀請碼、舊 Reward 均未變。
現在沒有替真人建立新的 Invitation，也沒有新的待補發獎勵。SQLite 均為 schema 9，quick_check=ok。

Paper 26.3 的 B 原本有直接 `* = true` 與測試資格 true；為驗收一般玩家 help，已暫時移除該 wildcard 並設資格 false。
原權限已備份，驗收後還原。三台 B 都不是 OP。

## 簡單完整步驟（現在可以開始）

三台已啟動；一次測一台，做完切回 Lobby 再換下一台。後端主控台指令**不加 `/`**；玩家聊天指令加 `/`。
測試帳號、語言及客戶端版本記在回報中；不要提供密碼或 key。

### 1. 啟動與切服

在既有 InstanceHarbor GUI 依名稱選擇目標，開「原生後台」。三台目前已啟動，不必重複按啟動。
在目前後端主控台依序輸入：

```text
ic admin doctor
ic help
ic code
```

預期 doctor 正常；Paper／Folia 若具 Dialog 能力應回報 PAPER，Spigot 1.8.8 為 NONE。
help 有管理用法；console 的 `ic code` 明確說只能由玩家執行。
使用 16 色、不額外粗體，文字可讀、沒有原始 `&`／HEX 格式碼外洩。
本輪原生後台視覺色彩仍待人眼確認。

A、B 都連到 `localhost:25565`，然後各自輸入**目前這一台**：

```text
/server invitecode-spigot-1.8.8
/server invitecode-paper-26.3
/server invitecode-folia-26.1.2
```

Spigot 用 1.8.8 客戶端驗收舊版行為；Paper 用 26.3，Folia 用原生 26.1.2 或已核對的代理相容客戶端，
記錄實際版本。若無 Dialog 或切服失敗，先回報，不直接連離線模式後端。

### 2. 先測現有資格拒絕

A 輸入 `/ic claim`，預期因 Inviter 身分直接拒絕，不開輸入框。
A、B 各輸入 `/ic status`、`/ic stats` 並記下數量；B 目前應顯示可登記。
若 status 同時顯示舊 QUALIFIED／UNLINKED，那是保留的歷史；本輪可登記與後續新關係另外核對。
B 的「已有登記」前置拒絕要在步驟 8 成功後再測。

### 3. help、Tab 與邀請碼（改以上方六項複驗為準）

A、B 先依上方六項複驗首頁、分層說明及 Tab。首頁與詳細頁的操作用法只填入聊天列；分類／分頁直接開啟唯讀 help。
A 以 `/ic help admin` 查看其權限允許的分類；B 須先確認為一般玩家（沒有 OP／管理權限），只看到允許的項目。
在 `/ic status `、`/ic stats ` 和超出語法長度的位置按 Tab，應沒有線上玩家名稱補全。

A 輸入 `/ic code`。將滑鼠移到**六碼本身**，檢查 hover。
Paper／Folia 點擊後貼到本機記事本，應只有六碼；1.8.8 應填入聊天列，再手動選取複製，勿送到公開聊天。
檢查 HEX 顏色、繁體中文與 prefix：一般一份回覆只出現一次。

### 4. 確認 B 可開始，勿重做管理操作

B 輸入 `/ic status`，確認可登記；不要輸入 `/ic code`。
Agent 已完成 reset、Folia 精確 unlink、generation 與 audit 核對；這一步不需任何主控台修改。
若 B 已不符合資格，停止並回報，不自行反覆 reset。

### 5. 取消輸入與不公開

Spigot：B 執行 `/ic claim`，在聊天輸入 `cancel`。預期私密取消，A 看不到原始輸入。
Paper／Folia：B 執行 `/ic claim`，先按 Dialog「取消」；預期沒有登記關係變更。
輸入 Dialog 的標題／內容應清楚，置中、不靠空格補位置。
聊天路徑的 `cancel` 已由 Spigot 驗收；需要強制測新版聊天時，管理員暫改 input-mode 為 CHAT 並 reload，測完還原 AUTO。

### 6. 不同意

B 再執行 `/ic claim`，輸入 A 六碼（舊版聊天輸入；新版 Dialog 按繼續）。
聊天確認依序七區塊：prefix、確認標題、邀請人、邀請碼、問題、期限、按鈕。
聊天靠左；Dialog 內容置中。prefix 獨立且無尾端空格；期限末尾**沒有冒號**。
「同意」綠色、「不同意」紅色，hover 清楚，Dialog 底色沿用原生。
B 按「不同意」，再查 `/ic status`，預期仍可登記。

### 7. 逾時

B 重複輸入 A 碼進入確認後，等待超過 60 秒，再按同意或執行 `/ic confirm`。
預期拒絕過期操作、沒有建立登記；重新 `/ic claim` 仍可開始。不要把等待輸入逾時當成確認逾時。

### 8. 同意

**Spigot：A 先 `/server lobby`，B 才按最後的同意。**
Paper／Folia：A 此時可以留在原服，步驟 9 才回 Lobby。
B 重開輸入，填 A 碼並在期限內按綠色「同意」。預期成功只登記一次。
B `/ic status` 可看到新登記，再 `/ic claim` 應在輸入前拒絕。
Paper／Folia 新登記應先等待資格；Spigot 依本輪設定立即達成資格並通知 B，A 離線獎勵等待補發。
由 Agent 在回報後唯讀核對只有一筆新的 Invitation UUID，A／B 方向正確，
其 `invitee_eligibility_generation` 等於 B 本輪 generation；舊列與舊 Reward 不被覆寫。
玩家 status／stats 是外觀驗收，不能代替這項資料判定。

### 9. 資格通知與離線補發

**Spigot：不執行 lp 指令。**步驟 8 後 B 應已收到一次資格訊息；A 尚在 Lobby，直接做下方 A 回服檢查。

**Paper／Folia：**A 先 `/server lobby`，B 留在原服。目前後端主控台輸入：

```text
lp user Taiwan_PingLord permission set invitecode.test.qualified true
```

等待約 60 秒，或讓 B 回 Lobby 再切回本服，預期 B 收到一次資格訊息，A 的 MESSAGE 等待補發。
若先前已設過 true，不要再重複測本步；保留實際結果。

**三台共用：**A 回到正在測的那台，應收到一次資格訊息；A 再回 Lobby、再進來，不應重複。
A `/ic stats` 的 qualified 數量相對步驟 2 增加一。文字應清楚區分資格與獎勵交付。
不要執行 reward retry／mark-delivered。

### 10. 雙語與 reload

A、B 將客戶端語言切為 English (US)，再執行 help／status／stats，預期英文完整且 prefix 一致。
再切回繁體中文；若 locale 快取沒有即時更新，重新切入後端並記錄此行為。

由管理員先備份 `messages/zh_TW.yml`，將 `presentation.prefix` 暫改為 `[IC TEST]`，主控台 `ic reload`。
預期後續新回覆使用新 prefix，不重複；恢復檔案後 reload。
再把同一鍵暫改為數字 `123`（不是字串），執行 `ic reload`，預期明確報錯且舊有效 prefix 繼續使用。
立即恢復並 reload 成功；不要破壞 storage／DB／key。
缺檔復位已有 Agent 證據，真人不必刪檔重測。

### 11. 重啟與還原

記下 A 碼、A stats、B status；兩人切回 lobby，管理員正常 stop 並等待保存完成、退出 receipt／port 釋放。
重新啟動同一台，兩人切回，預期邀請碼、登記與設定保留，已送獎勵不再補發。
若遇到已記錄的 JLine 關服例外，保留 log；先核對儲存與退出，不在此輪展開隔離測試或直接重建。

測完先依步驟 12 回報，由 Agent 統一還原 B 原權限、原 qualifications 與 matching-ip-action，並核對正常關服。
不要手動回灌舊 DB，避免覆蓋本輪歷史與稽核；不要刪 key 或世界。共用代理／Lobby 保持原狀。

### 12. 回覆方式

每台提供：核心 build、兩帳號客戶端版本，以及下列項目的通過／失敗／未測：
切服、help／Tab、六碼 hover／複製、前置拒絕、取消輸入、不同意、確認逾時、同意、
七區塊與對齊、雙語、console 色彩、reload 失敗保留、資格／離線一次補發、重啟保留、設定還原。
失敗附畫面及時間即可；不要貼 key、IP 雜湊或完整 DB。

## Agent 唯讀資料核對方式

先依管理器身分選定本服的 `plugins/InviteCode/invitecode.db`，以 Python sqlite3
`connect(Path(database).as_uri() + "?mode=ro", uri=True)` 開啟，再執行 `PRAGMA query_only=ON`。
只做下列參數化 SELECT，結束後關閉 connection；不執行 migration、INSERT、UPDATE 或 DELETE，
不讀 key／網路 hash。帳號參數綁定 `Taiwan_PingLord`；結果保留於本機受限操作紀錄，公開證據只記錄角色與判定。

```sql
SELECT player_id, eligibility_generation
FROM invitecode_players WHERE last_known_name = ?;

SELECT i.invitation_id, i.inviter_id, i.invitee_id,
       i.invitee_eligibility_generation, i.status, i.claimed_at
FROM invitecode_invitations i
JOIN invitecode_players p ON p.player_id = i.invitee_id
WHERE p.last_known_name = ?
ORDER BY i.claimed_at, i.invitation_id;
```

先確認查到唯一 B，再以同樣的第一個 SELECT、A 的名稱核對 inviter_id。
reset 前、後與同意後各讀一次；新列須對應當前 generation，歷史列仍在。
如果服務正在寫入而讀取失敗，保留錯誤並在安全時重讀，不更改 journal mode 或寫入 DB 來解鎖。

## 尚餘里程碑與來源

目前是「Agent 部署與啟動驗證完成，待使用者驗收」。下一步依上方清單集中完成三台真人外觀與互動；
若通過，Agent 核對新 Invitation generation、獎勵恰一次及還原臨時設定／權限、停止不再使用的後端，
再整理本任務的交付審查。沒有 push 授權，merge、release 與發布也另行處理。

Paper 26.2 的隔離 adapter 複驗尚待完成，不使用其 Human 世界作為可重建自動資料。
Folia 最新 26.3 ALPHA 缺口仍保留；26.1.2 是實驗性測試對象。

依據：[四階段計畫，第四階段](../plans/command-feedback-implementation-plan.md#第四階段集中一次的真人外觀驗收)、
[本輪部署證據](evidence/2026-10-18-command-feedback-stage4-deployment.json)、
[部署前唯讀證據](evidence/2026-10-18-command-feedback-stage4-preflight.json)。
