# Help 完整用法核對表

2026-10-15 唯讀核對，來源 HEAD `e8e2b1f`。產品命令解析器與 controller 是用法依據；下表是待實作 help 的呈現規格，不表示目前 help 已具備此排版。

`/ic` 是 `/invitecode` 的別名。表中 `<...>` 必填，`[...]` 選填；符號本身不輸入。Console 輸入時省略 `/`。下列每項仍需基本權限 `invitecode.command.basic`，並符合各列權限。

## 完整清單

| ID | 用法 | 用途 | 執行者 | 權限 |
| --- | --- | --- | --- | --- |
| help | `/ic help` | 查看指令說明；`/ic` 等價 | 玩家、console | `invitecode.command.basic` |
| code | `/ic code` | 查看或建立自己的邀請碼 | 玩家 | `invitecode.command.code` |
| claim | `/ic claim [code]` | 使用別人的邀請碼；省略 code 時依設定開啟輸入 | 玩家 | `invitecode.command.claim` |
| confirm | `/ic confirm` | 同意目前的邀請確認 | 玩家 | `invitecode.command.claim` |
| cancel | `/ic cancel` | 取消輸入或確認 | 玩家 | `invitecode.command.claim` |
| status | `/ic status` | 查看自己的邀請狀態 | 玩家 | `invitecode.command.status` |
| stats | `/ic stats` | 查看自己的邀請統計 | 玩家 | `invitecode.command.stats` |
| inspect-player | `/ic admin inspect player <player-reference>` | 查詢玩家紀錄 | 玩家、console | `invitecode.admin.inspect` |
| inspect-invitation | `/ic admin inspect invitation <invitation-uuid>` | 查詢邀請關係 | 玩家、console | `invitecode.admin.inspect` |
| inspect-reward | `/ic admin inspect reward <reward-action-uuid>` | 查詢獎勵動作 | 玩家、console | `invitecode.admin.inspect` |
| unlink | `/ic admin invitation unlink <invitation-uuid> <reason>` | 解除邀請關係 | 玩家、console | `invitecode.admin.invitation` |
| transfer | `/ic admin invitation transfer <invitation-uuid> <player-reference> <reason>` | 轉移邀請關係 | 玩家、console | `invitecode.admin.invitation` |
| force-qualify | `/ic admin invitation force-qualify <invitation-uuid> <qualification-profile-id> <reason>` | 強制達成指定資格 | 玩家、console | `invitecode.admin.qualification` |
| reset-eligibility | `/ic admin eligibility reset <player-reference> <reason>` | 重設登記資格 | 玩家、console | `invitecode.admin.eligibility` |
| retry-reward | `/ic admin reward retry <reward-action-uuid> <reason>` | 重試獎勵動作 | 玩家、console | `invitecode.admin.reward` |
| mark-delivered | `/ic admin reward mark-delivered <reward-action-uuid> <reason>` | 標記獎勵動作已交付 | 玩家、console | `invitecode.admin.reward` |
| rotate-code | `/ic admin code rotate <player-reference> <reason>` | 輪替玩家邀請碼 | 玩家、console | `invitecode.admin.code` |
| migrate-storage | `/ic admin storage migrate <SQLITE\|MYSQL> <reason>` | 更新目前儲存結構 | 玩家、console | `invitecode.admin.storage` |
| reload | `/ic reload` | 重新載入設定與訊息；`/ic admin reload` 等價 | 玩家、console | `invitecode.admin.reload` |
| doctor | `/ic admin doctor` | 檢查執行狀態與儲存健康 | 玩家、console | `invitecode.admin.doctor` |

Help 的顯示列依這些 ID 與固定路由產生，用途從雙語 `runtime.help-description` 取得；不讓文案編輯改變 parser 的路由與參數規則。基本與管理分區只在有對應列時顯示。

## 參數與容易混淆之處

- `code`：有效邀請碼，不是玩家名稱。
- `player-reference`：玩家 UUID 或已知名稱；同名有歧義時改用 UUID。
- `invitation-uuid`：邀請關係 ID，不能以玩家 UUID 代替。
- `reward-action-uuid`：獎勵動作 ID，不是 Reward Delivery ID。
- `qualification-profile-id`：資格設定的 ID，不是玩家名稱／UUID。
- `reason`：管理原因，必填，可包含多個單字；parser 將剩餘參數以空格合併。不要以文件中的尖括號或示例字串代替實際原因。
- `SQLITE|MYSQL`：二選一，且必須與目前設定的 backend 相同；不接受 JSON。此指令更新 schema，**不是**跨 backend 搬移工具。
- `root` 不是子命令；`/ic` 本身就會顯示 help。
- 玩家用法與管理用法的權限不變；此規格不授權變更服務狀態下哪些命令可用。

## 原始碼依據

- [InviteCodeCommandParser.kt](../../invitecode-platform-bukkit/src/main/kotlin/io/github/command1264/invitecode/platform/bukkit/InviteCodeCommandParser.kt)：20 個命令 ID、參數數量、選填 code、reason 合併及 reload 別名。
- [AdminWorkflowController.kt](../../invitecode-plugin/src/main/kotlin/io/github/command1264/invitecode/plugin/AdminWorkflowController.kt)：玩家 reference、Invitation／Reward Action UUID、qualification profile ID。
- [PlayerReferenceResolver.kt](../../invitecode-core/src/main/kotlin/io/github/command1264/invitecode/core/PlayerReferenceResolver.kt)：UUID／已知名稱解析。
- [InviteCodePlugin.kt](../../invitecode-plugin/src/main/kotlin/io/github/command1264/invitecode/plugin/InviteCodePlugin.kt)：runtime 權限與 storage migrate backend 限制。
- [plugin.yml](../../invitecode-plugin/src/main/resources/plugin.yml)：主命令、`ic` 別名與 permission 定義。

本輪未執行命令或 build；用法核對屬靜態證據。日後實作需以 parser 驅動檢查 help 的每條路由，避免維護兩份互相分歧的用法。
