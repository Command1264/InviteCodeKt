# InviteCode 1.1 設計規格

狀態：草案（2026-11-24）。使用者已在 2026-11-24 核准 1.1 的範圍與主要設計決定。本文經使用者審核後，才會建立 GitHub Roadmap Issue 與子 Issue，並開始實作。

## 版本目標

1.1 讓其他插件、記分板與玩家能看見並取用邀請資料，不改變 Claim、Qualification 與 Reward 的語意。範圍共五項：

1. [Lifecycle Event](#1-lifecycle-event)
2. [PlaceholderAPI 輸出](#3-placeholderapi-輸出)
3. [Inviter Leaderboard](#4-inviter-leaderboard)
4. [管理員以玩家名稱查詢紀錄](#5-管理員以玩家名稱查詢紀錄)
5. [Repeating Milestone Reward](#6-repeating-milestone-reward)

第 2 節的唯讀 API 是第 1、3、4 項的共用基礎，不另算一項。

## 不變的邊界

- 維持單一 Java 8 bytecode JAR，支援 Spigot／Paper 1.8.8 起的已測試版本，不使用 NMS。Folia 仍屬實驗性。
- [ADR 0002](../adr/0002-durable-reward-delivery.md) 的先保存再執行、`UNCERTAIN` 不自動重試，以及 [ADR 0003](../adr/0003-explicit-storage-migration.md) 的明確 schema migration 照常適用。
- 新查詢不在主執行緒存取 storage。Storage 錯誤時照 1.0 規則安全失敗，不退回 memory 或其他 backend。
- 新輸出不包含 IP hash、原始輸入、憑證或 Anonymized Player 被移除的資料。
- 1.0 的非目標（舊資料 importer、Reward exchange GUI、原生 permission grant、metrics、自動更新）在 1.1 仍不做。

## 1. Lifecycle Event

依 [ADR 0007](../adr/0007-post-commit-lifecycle-events.md)，在 storage transaction commit 成功後，由全域排程以同步 Bukkit Event 送出，不可取消。這補上 [1.0 規格](./invitecode-1.0.md#qualification)已寫明、但 1.0.0 程式碼沒有實作的事件承諾。

| 事件 | 送出時機 | 主要內容 |
| --- | --- | --- |
| `InvitationClaimedEvent` | Claim Confirmation 建立 Invitation 後 | Invitation 快照 |
| `InvitationQualifiedEvent` | Invitation 成為 Qualified Invitation 後，包含 Force Qualification | Invitation 快照、policy ID、Qualification Revision、是否為 Force Qualification |
| `InvitationUnlinkedEvent` | Unlink 保存後 | Invitation 快照、Audit Record ID |
| `InvitationTransferredEvent` | Transfer 保存後 | Invitation 快照、原 Inviter、新 Inviter、Audit Record ID |
| `RewardDeliveryStatusChangedEvent` | Reward Delivery 進入 `DELIVERED`、`FAILED` 或 `UNCERTAIN` 後 | Reward Delivery 快照、新狀態、收件玩家 |

- 事件類別放在新模組 `invitecode-api-bukkit`，以 `compileOnly` 依賴 Spigot API 1.8.8，並封裝進同一份 JAR。平台中立的 `invitecode-api` 不依賴 Bukkit。
- 事件只攜帶不可變快照與 UUID，不攜帶可變的 domain 物件。玩家名稱由整合者自行查詢。
- 事件是通知，不保證送達，也不重送。需要完整紀錄的整合要用第 2 節的 API 查詢。
- Eligibility Reset、Invite Code 更換、`PENDING`／`PROCESSING` 等中間狀態不送事件，有實際需求再評估。

**驗收**

- 每種事件都有測試證明：只在 commit 成功後送出；transaction 失敗時不送出；listener 拋出例外不影響已保存的狀態與後續流程。
- Spigot 1.8.8 與 Paper 1.21.8 實際載入一個測試 listener 插件，收到 Claim、Qualified 與 Reward 事件。Folia 26.1.2 做相同檢查，結果標示為實驗性。

## 2. 唯讀 API 補充

在 `InviteCodeApi` 新增兩個回傳 `CompletionStage` 的唯讀方法，快照型別放在 `invitecode-api`：

- `getInviterSummary(playerId)`：Invite Code、Inviter、目前 `CLAIMED` 與 `QUALIFIED` 數量、待交付 Reward 數。
- `getLeaderboard(page, pageSize)`：Inviter Leaderboard 的一頁快照與快照時間。

數量定義與 `/ic stats` 相同：`UNLINKED` 的 Invitation 不計入。既有 API 方法的簽章與語意不變。

## 3. PlaceholderAPI 輸出

偵測到 PlaceholderAPI 時，註冊 identifier `invitecode`。1.0 已有的「以 PlaceholderAPI 值作為 Qualification Condition」保持不變。

| Placeholder | 內容 |
| --- | --- |
| `%invitecode_code%` | 玩家的 Invite Code；還沒有時為空字串 |
| `%invitecode_code_colored%` | 帶顏色的 Invite Code；1.16 以上使用 RGB，舊版使用最接近的 legacy color，與指令顯示一致 |
| `%invitecode_inviter%` | 目前 Invitation 的 Inviter 名稱；沒有時為空字串 |
| `%invitecode_claimed%` | 等待資格的 Invitation 數 |
| `%invitecode_qualified%` | Qualified Invitation 數，與 Milestone Reward 的計數相同 |
| `%invitecode_pending_rewards%` | 待交付的 Reward Action 數 |
| `%invitecode_rank%` | 在 Inviter Leaderboard 的名次；沒有上榜時為空字串 |
| `%invitecode_top_<n>_name%`、`%invitecode_top_<n>_qualified%` | 第 n 名的名稱與數量，`n` 為 1–100 |

- PlaceholderAPI 可能在主執行緒呼叫。所有值都讀取記憶體中的快照，呼叫時不查詢 storage。
- 線上玩家的快照在登入時、相關 Lifecycle Event 之後，以及 `/ic reload` 後於背景更新。排名類 placeholder 讀取第 4 節的排行榜快照。
- 還沒有快照、離線玩家或 storage 異常時，回傳設定的 `placeholders.unknown-value`，預設為空字串，不回傳錯誤訊息。

**驗收**：單元測試涵蓋每個 placeholder 的值、沒有快照與 Anonymized Player 的情況；真人以記分板或 TAB 插件確認 Spigot 1.8.8 與 Paper 1.21.8 的顯示。

## 4. Inviter Leaderboard

新增 `/invitecode top [page]`，權限 `invitecode.command.top`，預設所有玩家可用；Console 也可以使用。

- 依目前 Qualified Invitation 數量由多到少排序。數量相同時，最後一筆 Qualified Invitation 較早達成的排前面；仍相同時依玩家 UUID 排序，讓名次固定。
- 不列出：數量為 0 的玩家、Anonymized Player、處於 Leaderboard Opt-out 的玩家。
- 每頁預設 10 名。畫面標示快照時間，例如「5 分鐘前更新」。
- 排行榜快照在背景計算，預設每 5 分鐘更新一次，`/ic reload` 後也會更新。查詢指令只讀快照。
- 設定 `leaderboard.enabled`（預設 `true`）、`leaderboard.page-size`、`leaderboard.refresh-interval`。關閉時 `/ic top` 回覆功能未啟用，排名 placeholder 為空字串。
- SQLite、MySQL 與 JSON 都要新增跨玩家的聚合查詢，並加入 repository contract test。只有證明現有索引不足時，才依 ADR 0003 提出 migration。

**Leaderboard Opt-out**：擁有 `invitecode.leaderboard.hidden`（預設 `false`）的玩家不出現在排行榜。Bukkit 無法查詢離線玩家的權限，保存方式見[待確認事項](#待確認事項)第 1 項。

**驗收**：三種 backend 的排序、同分、分頁、Opt-out 與匿名化測試結果一致；排行榜快照計算不阻塞主執行緒；真人確認聊天與 Console 的顯示。

## 5. 管理員以玩家名稱查詢紀錄

依 2026-10-18 的本機草案整理（存放於專案容器的 `notes/`，不在 repository 內），權限沿用 `invitecode.admin.inspect`：

```text
/invitecode admin inspect invitations <玩家> [page]
/invitecode admin inspect rewards <玩家> [page]
```

- `<玩家>` 沿用既有的 player reference：已知玩家名稱或 UUID。未知或同名歧義時明確拒絕，不猜測。
- `invitations` 在同一頁分成兩段：先列這名玩家作為 Invitee 的紀錄，再列作為 Inviter 的紀錄。每筆顯示對方名稱、狀態、資格世代與時間。歷史紀錄不會被隱藏成「沒有紀錄」。
- `rewards` 列出這名玩家作為收件人的 Reward Action，並標明所屬 Reward Delivery、獎勵 ID、種類與狀態。
- 每頁 8 筆。聊天中每筆紀錄可以點擊，把對應的 `inspect`／管理指令填入聊天列，不會直接執行。Console 在每筆紀錄下方印出含內部 UUID 的完整指令，複製就能執行，不使用限時編號。
- 既有以 UUID 操作的指令全部保留。執行任何管理操作時，照 1.0 的規則重新檢查權限、紀錄是否存在與目前狀態，並要求填寫原因。
- 不需要修改 schema。若現有查詢無法有效分頁，先新增查詢 API；只有證明索引不足時才依 ADR 0003 提出 migration。

**驗收**：自動測試涵蓋已知、未知、離線、改名、同名玩家，多筆歷史與資格世代，同一 Invitation 有多個 Reward Action，以及權限過濾與空白分頁。真人確認管理員只知道玩家名稱，就能在聊天與 Console 找到並選取紀錄。

## 6. Repeating Milestone Reward

Milestone Reward 新增可選欄位 `repeat-every`：

```yaml
milestone-rewards:
  every-five-after-ten:
    at: 10
    repeat-every: 5   # 在 10、15、20…筆時各觸發一次
    inviter:
      - type: MESSAGE
        message: invite.milestone.repeat
```

- 沒有 `repeat-every` 時，行為與 1.0 完全相同。`repeat-every` 必須是 1 以上的整數。
- 觸發條件與 1.0 相同：Invitation 成為 Qualified Invitation（包含 Force Qualification）後，Inviter 的數量剛好等於某個門檻時觸發。
- 沿用 1.0 的冪等鍵 `milestone:<Inviter>:<門檻>:<獎勵 ID>`，格式不變。因此 Unlink 讓數量下降、之後再次達到同一門檻時，不會重複發放。
- 修改 `at` 或 `repeat-every` 不會補發已經錯過的門檻，也不會重發已交付的門檻。

**驗收**：測試涵蓋多次觸發、Unlink 後再次達到同一門檻、Force Qualification、設定驗證錯誤時 reload 保留前一份有效設定。

## 7. 相容性與發行驗證

- Java 8 與 Java 11 完整建置、MySQL 8.4 整合測試、JAR 與 bytecode 檢查。
- 代表性 smoke：Spigot 1.8.8、Paper 1.21.8、Paper 26.2、Paper 26.3；Folia 26.1.2 標示為實驗性。
- Spigot 26.3 的完整真人驗收延後，等 InstanceHarbor 能建立 Spigot 26.3 實例後再補（使用者 2026-11-24 的決定）。發行文件不宣稱比實際測試更廣的相容性。

## 不在 1.1 的項目

**1.2 之後的候選**：玩家邀請面板（只能看、不能兌換）、邀請活動期（需要新詞彙與 ADR）、自選顏色邀請碼、每位 Inviter 的達成人數上限。1.1 發布後有實際使用或回報，才開始 1.2。

**備案（暫不排程）**：Discord 伺服器邀請追蹤、子網域邀請連結、遊戲內獎勵編輯器、多層推薦鏈、Web／REST API、自帶點數貨幣、內建 Discord webhook、metrics 與自動更新、Velocity／BungeeCord 網路模式。有實際需求或使用者回報時再重新評估；網路模式與 [ADR 0001](../adr/0001-single-cross-version-plugin-jar.md) 的單一 Java 8 JAR 衝突，評估時需要新的 ADR。

## 建議實作順序

每項各開一個子 Issue 與 task branch：

1. Lifecycle Event 與唯讀 API（其他項目依賴它）
2. PlaceholderAPI 輸出
3. Inviter Leaderboard
4. 管理員以玩家名稱查詢紀錄
5. Repeating Milestone Reward（不依賴其他項目，可以和 2–4 平行）

## 待確認事項

1. **Leaderboard Opt-out 的保存方式。** Bukkit 只能檢查線上玩家的權限。
   - A（建議）：玩家登入時記錄是否擁有 `invitecode.leaderboard.hidden` 並保存。這需要依 ADR 0003 為 SQLite、MySQL 與 JSON 做明確 schema migration，並同步更新跨 backend 搬移與匿名化。
   - B：改用設定檔 `leaderboard.hidden-players` 列出玩家 UUID，由管理員維護。不改 schema，但玩家不能自行設定，也和「用權限控制」的決定不同。
2. 第 1 節的五個事件名稱與範圍是否足夠。
