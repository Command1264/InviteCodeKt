# Qualification Policy 設定

`qualifications.yml` 使用版本化格式。每份 Qualification Policy 由一個或多個 `any-of` 群組組成；任一群組成立即可通過。每個群組的 `all-of` condition 必須全部成立。

```yaml
config-version: 1

policies:
  new-player:
    any-of:
      - all-of:
          - id: initial-play-time
            type: PLAY_TIME
            at-least: 30m
          - id: initial-login-days
            type: LOGIN_DAYS
            at-least: 2
```

`id` 用於 Qualification Fact 與稽核紀錄，在同一份 policy 內必須唯一。省略時會依群組與 condition 位置產生 ID；正式設定建議明確填寫，避免重新排序後改變 ID。Policy 內容會正規化並產生 `sha256:` revision；名稱相同但內容不同的設定會留下不同 revision。

## 內建 condition

| `type` | 必要參數 | 判定方式 |
| --- | --- | --- |
| `PLAY_TIME` | `at-least` | 累積在線時間至少達到門檻。 |
| `ELAPSED_TIME` | `at-least` | 從 Claim 到評估時間至少經過門檻。 |
| `LOGIN_DAYS` | `at-least` | 不同登入日數至少達到整數門檻。 |
| `ADMIN_APPROVAL` | 無 | 管理員核准 fact 為 true。 |
| `PERMISSION` | `permission` | Invitee 具有指定 permission。 |
| `PLACEHOLDER` | `placeholder`、`operator`、`value` | 比較 Invitee 的 PlaceholderAPI 值。 |

時間接受 `ms`、`s`、`m`、`h`、`d` 簡寫或 ISO-8601 Duration，例如 `30m`、`1d`、`PT2H`。Placeholder operator 支援 `EQUALS`、`NOT_EQUALS`、`CONTAINS`、`GREATER_THAN`、`GREATER_THAN_OR_EQUAL`、`LESS_THAN` 與 `LESS_THAN_OR_EQUAL`；大小比較要求實際值與設定值都是數字。

Permission、Placeholder 或外部 provider 不存在時，condition 預設產生 `UNKNOWN`。可明確設定安全的退回行為：

```yaml
- id: optional-rank
  type: PLACEHOLDER
  placeholder: "%player_rank%"
  operator: EQUALS
  value: member
  on-unavailable: UNSATISFIED
```

`on-unavailable` 只接受 `UNKNOWN` 或 `UNSATISFIED`；兩者都不會讓群組通過。Provider 拋出例外或平台值無法取得時一律產生 `UNKNOWN`，避免 integration 故障誤發獎勵。

## 外部 provider

外部插件透過 `InviteCodeApi.registerQualificationConditionProvider` 註冊 `QualificationConditionProvider`。`type` 不分大小寫且同一時間只能有一個 provider；停用插件時必須關閉回傳的 `QualificationProviderRegistration`。外部插件可透過 `requestQualificationEvaluation` 要求重新評估指定 Invitation。

Provider 的 `evaluate` 必須快速、不可阻塞，並依傳入 context 與唯讀參數回傳 `SATISFIED`、`UNSATISFIED` 或 `UNKNOWN`。需要 Bukkit 主執行緒的資料應先由平台工作流程建立快照，再交給 qualification executor；provider 不得自行執行資料庫或網路 I/O。

## 保存與重新評估

Evaluator 會在 AND group 第一個非 `SATISFIED` 結果停止該群組，並在第一個成立的 OR group 後停止整份 policy。只有實際執行的 condition 會產生 Qualification Fact。

通過時，Qualified Invitation、policy ID、revision 與造成通過的 facts 會在同一個 storage transaction 保存。未通過時，本次已評估 facts 會以單一 transaction 保存。已經 Qualified 的 Invitation 不再呼叫 provider，也不會因一般 reevaluation 回到 Claimed 狀態。
