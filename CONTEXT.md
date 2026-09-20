# InviteCode 領域詞彙

InviteCode 管理 Minecraft 玩家之間的邀請關係，以及邀請從建立到符合獎勵資格的生命週期。

## 領域語言

**Inviter**:
至少已有一名玩家使用其 Invite Code 建立 Invitation 的玩家。一名 Inviter 可以有多名 Invitee。
_避免使用_：Code owner、referrer、inviter player

**Invitee**:
透過邀請碼與一名 Inviter 建立邀請關係的玩家。一名 Invitee 最多只能有一名 Inviter。
_避免使用_：Invited player、target player、code user

**Invite Code**:
由 Inviter 持有、供合資格玩家建立邀請關係的識別碼；它是建立關係的憑證，不是邀請關係本身。
_避免使用_：Code、referral code

**Color Invite Code**:
由六個十六進位字元組成的 Invite Code，其顯示顏色由邀請碼本身代表的 RGB 色彩決定。
_避免使用_：Hex code、special color code

**Claim**:
具備 Claim Eligibility 的玩家使用 Invite Code 建立 Invitation 的一次性行為。
_避免使用_：Use code、redeem code

**Claim Confirmation**:
Invitee 在看見 Inviter 與 Invitation 摘要後，明確同意完成 Claim 的確認。
_避免使用_：Code submit、second command

**Invitation**:
Inviter 與 Invitee 之間永久且唯一的邀請關係。
_避免使用_：Used code、referral record

**Claimed Invitation**:
Invitee 已使用 Invite Code 建立，但尚未達成獎勵資格的 Invitation。
_避免使用_：Pending code、used code

**Qualified Invitation**:
已達成設定資格條件、可依獎勵規則計算的 Invitation。
_避免使用_：Successful code、completed referral

**Claim Eligibility**:
玩家建立 Invitation 前必須具備的資格，包括尚未成為 Inviter、沒有既有 Invitation，且仍在 Claim Window 內。
_避免使用_：Can use code、available use count

**First Seen At**:
玩家首次加入此 InviteCode 資料範圍的時間點，也是 Claim Window 的起點。
_避免使用_：Registration time、first login

**Claim Window**:
玩家從 First Seen At 起可以使用 Invite Code 的期間。
_避免使用_：Registration period、code expiry

**Qualification Policy**:
判定 Claimed Invitation 是否成為 Qualified Invitation 的條件組合；同一方案內所有條件都須成立，多個方案則任一成立即可。
_避免使用_：Success requirements、completion config

**Qualification Condition**:
Qualification Policy 中一項可獨立判定的資格要求，例如遊玩時間、登入天數、權限或外部系統提供的值。
_避免使用_：Check、requirement plugin

**Qualification Revision**:
Qualification Policy 某份確切內容的不可變識別；同一 policy 名稱在內容變更後會產生新的 revision。
_避免使用_：Config version、current rules

**Qualification Reward**:
Invitation 首次成為 Qualified Invitation 時，依規則一次性給予 Inviter 或 Invitee 的獎勵。
_避免使用_：Instant reward、success reward

**Milestone Reward**:
Inviter 累積的 Qualified Invitation 數量首次達到指定門檻時給予的獎勵。
_避免使用_：Exchange reward、count reward

**Reward**:
由 Qualification 或 milestone 觸發、交付給指定玩家的一組 Reward Action。
_避免使用_：Prize、exchange item

**Reward Action**:
一份 Reward 中可獨立執行並記錄結果的獎勵動作。
_避免使用_：Reward command、prize step

**Pending Reward**:
已取得發放資格，但因收件玩家離線或暫時無法執行而等待後續交付的 Reward。
_避免使用_：Offline reward、retry reward

**Uncertain Reward Action**:
已開始執行但因非安全中斷而無法確認結果的 Reward Action；必須由管理員判定是否重試或視為已交付。
_避免使用_：Failed reward、pending reward

**Reward Delivery**:
一次 Qualification Reward 或 Milestone Reward 的交付紀錄，包含各 Reward Action 的結果，並用來避免重複發放。
_避免使用_：Reward log、command history

**Audit Record**:
對 Invitation、Invite Code、Qualification 或 Reward 的重要狀態變更所留下的可追溯紀錄。
_避免使用_：Debug log、history line

**Unlink**:
解除一筆 Invitation，但不自動恢復雙方 Claim Eligibility 的管理操作。
_避免使用_：Delete invitation、reset

**Eligibility Reset**:
由管理員明確恢復指定玩家 Claim Eligibility 的操作。
_避免使用_：Unlink、clear player

**Transfer**:
將尚未 qualified 的 Invitation 改連結至另一名 Inviter 的管理操作。
_避免使用_：Replace code、move player

**Force Qualification**:
由具權限的管理員略過 Qualification Policy，將 Claimed Invitation 轉為 Qualified Invitation 的操作。
_避免使用_：Manual success、approve reward

**Anonymized Player**:
已移除名稱、IP hash 與其他可選顯示資料，但保留歷史關係與防重複約束所需匿名識別的玩家主體。
_避免使用_：Deleted player、unknown player
