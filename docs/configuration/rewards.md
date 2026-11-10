# Reward Delivery 設定與復原

`rewards.yml` 使用版本化格式。Qualification Reward 會在 Invitation 首次成為 `QUALIFIED` 時建立；Milestone Reward 會在 Inviter 的 qualified Invitation 數量首次到達指定門檻時建立。

```yaml
config-version: 1

qualification-rewards:
  default:
    inviter:
      - type: MESSAGE
        message: invite.qualified.inviter
      - type: VAULT_ECONOMY
        amount: 100
    invitee:
      - type: CONSOLE_COMMAND
        command: give {invitee_name} diamond 1

milestone-rewards:
  five-qualified:
    at: 5
    inviter:
      - type: MESSAGE
        message: invite.milestone.five
```

每個 reward ID 都是持久化冪等鍵的一部分。Reward 已投入使用後，不應把同一 ID 改成另一種獎勵；需要改變內容時請使用新的 ID。

## Action 類型

| `type` | 必要欄位 | 執行方式 |
| --- | --- | --- |
| `MESSAGE` | `message` | 將 locale key 對應的訊息傳給在線收件玩家。 |
| `CONSOLE_COMMAND` | `command` | 由伺服器 Console 執行不含開頭 `/` 的指令。 |
| `PLAYER_COMMAND` | `command` | 由在線收件玩家以原有權限執行指令；不會暫時提高權限。 |
| `VAULT_ECONOMY` | `amount` | 透過 Vault 已註冊的 Economy provider 存入正數金額。 |

指令只接受下列 placeholder：

- `{recipient_uuid}`、`{recipient_name}`
- `{inviter_uuid}`、`{inviter_name}`
- `{invitee_uuid}`、`{invitee_name}`

名稱在執行當下無法取得時，使用該名稱的指令會進入 `FAILED`，且不會送出部分展開的指令。設定解析會拒絕未知 placeholder、換行、控制字元、開頭 `/` 及不成對的大括號。`MESSAGE` 的 `message` 是 locale key，不是直接顯示的文字。

Vault 是 soft dependency。沒有安裝 Vault、沒有 Economy provider，或 integration 暫時不可用時，`VAULT_ECONOMY` Action 會保留在 `PENDING`，待 integration 可用後再處理。`MESSAGE` 與 `PLAYER_COMMAND` 的收件玩家離線時也會保留在 `PENDING`，供玩家上線時補發。

## 持久化與失敗復原

每次 Reward Delivery 與完整 Action 清單會先以單一 storage transaction 寫入，再允許任何外部效果發生。Delivery、Action ID 與 idempotency key 都由 Invitation、reward ID、收件角色及 Action 位置穩定產生；相同事件重送只會讀回既有紀錄。

Action 執行流程如下：

1. 確認玩家在線或 integration 可用；尚不可執行時維持 `PENDING`。
2. 在 storage 將 Action 從 `PENDING` 寫成 `PROCESSING`。
3. 執行訊息、指令或 Vault 存款。
4. 依明確結果寫成 `DELIVERED` 或 `FAILED`。

如果玩家在步驟 1 與步驟 3 之間離線，平台 adapter 會在尚未執行外部效果時明確回報，Action 由 `PROCESSING` 安全回到 `PENDING`，等待下次登入。

如果外部呼叫拋出例外，或外部效果完成後無法保存最終狀態，結果可能已發生但無法證明。此時不會自動重送：仍是 `PROCESSING` 的紀錄會在重啟復原時轉成 `UNCERTAIN`。管理員必須根據外部系統紀錄選擇：

- `RETRY`：將 Action 恢復為 `PENDING`，接受外部效果可能重複的風險。
- `MARK_DELIVERED`：確認效果已發生，將 Action 視為 `DELIVERED`。

管理決定必須保存 actor 與 reason。指令 dispatcher 回傳已接受時，系統即視為 `DELIVERED`；插件無法驗證被呼叫指令後續所做的其他非同步工作。

## 執行緒邊界

`AsyncRewardDeliveryWorkflow` 會把同步 storage transition 排入專用 executor，將在線狀態、訊息、指令與 Vault 外部效果以 continuation 切回 server main thread，再回到 executor 保存結果。任何背景執行緒都不會同步等待 server main thread。

目前核心規劃、狀態處理、SQLite persistence、Bukkit／Vault adapter、`rewards.yml` 載入、玩家上線補發、啟動復原及管理指令均已串接。
