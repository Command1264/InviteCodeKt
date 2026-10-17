# 訊息與 prefix：第四階段真人驗收

## 目前狀態：準備完成，尚未部署或啟動

2026-10-18 沿用 `codex/feat/command-feedback-messages` Worktree。本輪只讀取固定測服，
在本機 `.run/command-feedback-stage4/` 準備 JAR、兩份語系及逐台設定提案。
沒有更動 Human 世界、資料庫、密鑰、設定、已裝 JAR、路由或帳號資格；沒有 push。

受測程式來源：`dfbc39c4a80cea91b22b88c5f89d16107e7d50a2`。
待部署 JAR：`invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar`，19,890,059 bytes。
SHA-256：`E350DCA507D1FB953A2B785474708226BFBD682CBFE5D44E7E9BF0D0630C6C8B`。
第一至三階段證據不代表這份 JAR 已通過真人外觀驗收。

## 選用三台，逐台完成

下表的 build 依實際 `server.jar` 雜湊與相符安裝紀錄核對；Paper 更新後尚未在本輪啟動，
不可拿舊 `versions/` 或 `latest.log` 當成新核心啟動證據。

| 測服／同名 `/server` 路由 | 核心 | JDK alias | Port | 本輪用途 |
| --- | --- | --- | --- | --- |
| `invitecode-spigot-1.8.8` | Spigot `git-Spigot-3c60ece-741a1bd` | local-8 | 10101 | 舊版聊天與填入邀請碼 |
| `invitecode-paper-26.3` | Paper 26.3 build 202 | local-25 | 20102 | 新版 Dialog、複製、完整流程 |
| `invitecode-folia-26.1.2` | Folia 26.1.2 build 8 | local-25 | 30101 | 實驗性平台互動與排程 |
| `invitecode-paper-26.2` | Paper 26.2 build 134 | local-25 | 20101 | 保留；受影響 adapter 的 Agent 複驗尚待執行 |

實例根目錄：`D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\`。
不新建實例、不更新核心、不搬移世界。四台均被現行管理器發現、`Status=Stopped`、後端 port 未監聽。
Java alias 指向的實體 `java.exe` 均存在；本輪沒有啟動 JVM，故不宣稱啟動時版本驗證完成。
四條唯一路由、loopback、legacy forwarding 設定相符；仍須以本候選真人切服確認。
共用 Velocity 25565 與 Lobby 10001 已在既有 managed ConPTY run 運行，保持原狀。

## 部署前，由 Agent 完成的工作

這是待執行安排，**不是部署成功紀錄**。

1. 逐台重新取得管理器 `Status`、run、port 與獨佔鎖；確認停止且沒有待執行核心更新。
   任一身分、來源 hash 或狀態改變，停止該台部署並重新核對。不要關閉共用代理／Lobby。
2. 在目標實例旁的獨立備份目錄保留：唯一的舊 InviteCode JAR、整個 `plugins/InviteCode/`、
   `workspace.json`。資料庫、WAL／SHM（若存在）與密鑰必須成套備份；不將它們提交 Git 或貼出內容。
   只在停服並持有管理鎖時複製，核對檔案清單、大小、雜湊與可還原性。
3. 比對本輪 preflight 與實際 JAR，部署原封不動的 E350 候選。先將舊 JAR 移入備份、保持 plugins
   內只有一份 InviteCode；Paper 26.3 原本叫 `InviteCode-1.0.0.jar`，不能留下它與新 JAR 同時載入。
   `workspace.json` 只更新插件檔名／hash／來源／更新時間，不改建立時核心紀錄、世界或首次設定狀態。
4. 備份後，將候選 JAR 的 `config/messages/zh_TW.yml`、`en_US.yml` 部署至既有
   `plugins/InviteCode/messages/`，保留其舊檔。只換 JAR 不會覆寫舊文案。
   `storage.yml`、資料庫、密鑰與 rewards 保持原樣。
5. 為同電腦兩帳號，將三台測試期間的 `matching-ip-action` 設為 `ALLOW`；原設定為 Spigot/Folia
   `DENY`、Paper 26.3 `ALLOW`。驗收後分別還原原值；不要把 Paper 原值誤報成 DENY。
   原有同 IP 拒絕證據不代表本輪新候選已重新驗證防濫用。
6. 若要重測資格通知及離線補發，以提案中的 `PERMISSION invitecode.test.qualified` 暫時取代
   `qualifications.yml`（policy ID 保持 `new-player`）。保存原檔及 B 原權限，避免被原本 30 分鐘／兩天
   規則阻擋或提早達成；測完還原。不要改動獎勵種類。
7. 保留部署前後檔案清單與 hash。啟動由下一個明確安排執行；看到當次核心版本、`Done`、InviteCode
   ACTIVE 及預定 port 才交給玩家。JAR 存在、`accepted` 或舊 log 均不足以宣告已就緒。

本機候選與提案位於 Worktree `.run/command-feedback-stage4/candidate/`、`proposed/<測服>/`。
它們不是既有資料備份，也尚未套用。預估候選及文字約 20 MB，已確認 D 槽有 47 GiB 以上空間。

## 兩帳號與既有資料

- **A：Command1**，邀請人，三台都有自己的有效邀請碼。A 不重設資格、不輪替邀請碼。
- **B：Taiwan_PingLord**，受邀人，三台都有舊登記，沒有自己的有效邀請碼。
  Spigot／Paper 26.3 的舊登記為 QUALIFIED；Folia 的舊登記為 CLAIMED。

舊資料均為 SQLite schema 9，`quick_check=ok`，本輪沒有 migration；舊獎勵沒有 PENDING／PROCESSING／UNCERTAIN。
要讓同一個 B 重跑成功 Claim，必須先明確同意對這三台的 B 執行一次**登記資格重設**。
本輪沒有執行；也不以刪 DB、換 key 或匿名化來繞過。
操作會影響 B 的當前登記資格並留下稽核；不能把「保留歷史」說成「帳號狀態完全不變」。
Folia 還有一筆舊 CLAIMED：目前資格掃描會評估所有 CLAIMED 歷史，重設資格不會停止舊列的評估。
因此也須確認先將 Folia 該筆精確 Invitation **unlink**，保留歷史列與稽核；否則重新授予測試權限時，
可能連舊列一起達成資格並發獎。Spigot／Paper 26.3 的舊 QUALIFIED 保留，不 unlink。
Paper 26.2 不在本輪 Human 部署、reset 或 unlink 範圍。
若不接受此改動，可以先驗收 help、code、status／stats、資格預檢、雙語及 console；新的確認互動仍未驗收。

## 簡單完整步驟（部署並完成前置後才開始）

每次只開目前那台，做完正常關閉，再換下一台。後端主控台指令**不加 `/`**；玩家聊天指令加 `/`。
測試帳號、語言及客戶端版本記在回報中；不要提供密碼或 key。

### 1. 啟動與切服

在既有 InstanceHarbor GUI 依名稱啟動目標，開原生後台；不要使用舊 PowerShell 管理入口。
由 Agent／管理員核對本候選 hash、當次核心版本、`Done`、InviteCode 啟用、無例外，主控台輸入：

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

A、B 各輸入 `/ic status`、`/ic stats`、`/ic claim`。
預期 A 因 Inviter 身分直接拒絕；B 因已有登記直接拒絕，不開輸入框。
記下原本邀請碼與統計，做為重啟比較基準。

### 3. help、Tab 與邀請碼

A、B 各輸入 `/ic help`。點擊一條用法，應只填入聊天列、不自動執行。
A 可看到其權限允許的管理用法；B 須先確認為一般玩家（沒有 OP／管理權限），只看到允許的項目。
在 `/ic status `、`/ic stats ` 和超出語法長度的位置按 Tab，應沒有線上玩家名稱補全。

A 輸入 `/ic code`。將滑鼠移到**六碼本身**，檢查 hover。
Paper／Folia 點擊後貼到本機記事本，應只有六碼；1.8.8 應填入聊天列，再手動選取複製，勿送到公開聊天。
檢查 HEX 顏色、繁體中文與 prefix：一般一份回覆只出現一次。

### 4. 由管理員準備 B 的本輪資格

僅於同意上述資格變更、備份及暫時設定已核對後操作。B 先留在 lobby，不在後端。
先保存 B 的 `invitecode.test.qualified` 原值／來源，確認 B 沒有 OP 或 wildcard，然後主控台執行：

```text
lp user Taiwan_PingLord permission set invitecode.test.qualified false
lp user Taiwan_PingLord permission check invitecode.test.qualified
```

預期檢查為 false；若不是，先回報並處理精確權限，不改所有玩家或群組。
若目前是 Folia，由 Agent 唯讀找出舊 CLAIMED 的唯一 Invitation UUID，核對 A／B 方向與狀態，
再在本服執行下列指令（`<舊Invitation-UUID>` 必須換成剛核對的值，不能直接貼入）：

```text
ic admin inspect invitation <舊Invitation-UUID>
```

先閱讀結果，核對 UUID、A／B 方向與狀態仍為 CLAIMED；任何不符都停止。
確認後**單獨**執行：

```text
ic admin invitation unlink <舊Invitation-UUID> command-feedback-human-old-claimed
```

再次執行 inspect，確認該列為 UNLINKED、歷史與 audit 保留，才繼續 reset。
Spigot／Paper 26.3 跳過此 unlink。
若舊列數量、狀態與本輪 preflight 不符，先停止並重新核對，不能以廣泛 unlink 代替。
接著在**目前後端**執行：

```text
ic admin inspect player Taiwan_PingLord
ic admin eligibility reset Taiwan_PingLord command-feedback-human-acceptance
ic admin inspect player Taiwan_PingLord
```

由 Agent 唯讀核對 B 的 `eligibility_generation` 恰好增加 1（本輪原值 0，預期 1），
歷史 Invitation／Reward 仍保留；不重設 A。B 切入後 `/ic status` 應顯示可登記；
狀態畫面可能同時列出歷史 Invitation，不能只靠該文字證明本輪新關係。
若仍不可登記，停止並回報，不反覆 reset。
B 必須在重新開放的 24 小時內完成後續步驟，且**不要執行 `/ic code`**。

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

B 重開輸入，填 A 碼並在期限內按綠色「同意」。預期成功只登記一次。
B `/ic status`、A `/ic stats` 可看到相符狀態。B 再 `/ic claim` 應在輸入前拒絕。
資格提示與「獎勵送達」要能區分；這一步尚不應因測試權限提前達成資格。
由 Agent 唯讀核對只有一筆新的 Invitation UUID，A／B 方向正確、狀態 CLAIMED，
其 `invitee_eligibility_generation` 等於 B 本輪 generation；舊列與舊 Reward 不被覆寫。
玩家 status／stats 是外觀驗收，不能代替這項資料判定。

### 9. 資格通知與離線補發

步驟 4 已確認 B 的測試資格權限為 false。同意後，A 切回 `/server lobby`，讓 A 離開該後端；B 留在原服。
主控台將 B 設為 true：

```text
lp user Taiwan_PingLord permission set invitecode.test.qualified true
```

等待最多一個週期（約 60 秒）或 B 重新加入該服。預期 B 收到一次資格訊息，A 的 MESSAGE 留待補發。
A 回到該服應收到一次訊息；再次離開再回來不重複。A `/ic stats` 的 qualified 數量相對步驟 8 增加一。
不要用 reward retry 或 mark-delivered 來製造此測試。

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

測完還原 B 原權限、原 qualifications 與 matching-ip-action，再 reload／必要時正常重啟核對。
只還原測試設定，不回灌舊 DB 覆蓋本輪稽核。若要撤回本輪帳號資料變更，須另核對停服備份與精確還原範圍。
最後正常停止該 InviteCode 後端，保留世界與資料；代理／Lobby 若其他測試在用就保持運行。

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

本文件完成的是第四階段的**準備**。下一步先確認三台部署與 B 資格重設的資料影響，
以及 Folia 單筆舊 CLAIMED 的 unlink，再依停服備份流程部署；核對當次啟動後進行上述一次集中真人驗收。
Paper 26.2 的受影響 adapter Agent 複驗另列未完成，不占用 Human 世界做自動化。
Folia 最新 ALPHA 缺口保留，26.1.2 仍為實驗性。
驗收後才整理本任務的 push／PR；merge、release 與正式發布另行取得授權。

依據：[四階段計畫，第四階段](../plans/command-feedback-implementation-plan.md#第四階段集中一次的真人外觀驗收)、
[本輪唯讀證據](evidence/2026-10-18-command-feedback-stage4-preflight.json)、
[第三階段結果](evidence/2026-10-18-command-feedback-stage3.md)。
