# 狀態保存成功後才在全域排程送出 Lifecycle Event

狀態：提議中（2026-11-24，待使用者審核）

InviteCode 1.1 以 Bukkit Event 對其他插件公開 Lifecycle Event。事件只在對應狀態變更的 storage transaction 成功 commit 後建立，不可取消，也不能修改 InviteCode 的狀態。事件由同一份 JAR 的全域排程送出：Spigot／Paper 使用主執行緒，Folia 使用 global region scheduler，以同步事件（`isAsynchronous() == false`）呼叫 listener。同一個 InviteCode 實例依 commit 順序送出事件。

這項決定補上 [1.0 規格](../spec/invitecode-1.0.md#qualification)已寫明、但 1.0.0 程式碼沒有實作的「Event 在 transaction commit 後送出，且不可取消」。

## 考慮過的選項

- **可取消的前置事件**：讓其他插件在 Claim 或 Qualification 前否決。沒有採用：否決條件已經由 Qualification Condition provider 提供，而且會讓外部插件介入 [ADR 0002](./0002-durable-reward-delivery.md) 的交付狀態流程。
- **從背景 storage executor 送出非同步事件**：不必多等一個 tick，但大多數 listener 會呼叫 Bukkit API（訊息、指令、記分板），在非同步事件中這樣做並不安全。沒有採用，避免把執行緒安全的負擔轉嫁給整合者。
- **只提供 `InviteCodeApi` listener**：不綁定 Bukkit，但 DiscordSRV、Skript 等常見整合方式都以 Bukkit Event 為主。沒有採用；平台中立的 `invitecode-api` 仍不依賴 Bukkit。

## 後果

- Lifecycle Event 是通知，不是可靠交付管道。插件停用、重載或伺服器當機時，已 commit 但尚未送出的事件可能遺失，也不會重送。需要完整紀錄的整合要以 `InviteCodeApi` 查詢狀態。
- Listener 拋出的例外由 Bukkit 記錄，不會回滾已保存的狀態，也不影響後續 listener 或 Reward Delivery。
- 事件只攜帶不可變快照與識別碼。Anonymized Player 只會以匿名識別出現；事件不包含 IP hash、原始輸入或憑證。
- Folia 的 global region 執行緒不可直接操作玩家 entity。Folia listener 需要依 [ADR 0004](./0004-folia-scheduler-ownership.md) 自行改派到玩家 scheduler；Folia 支援仍屬實驗性。
- 多個實例共用 MySQL 時，每個實例只送出自己 commit 的事件，不跨實例廣播。
