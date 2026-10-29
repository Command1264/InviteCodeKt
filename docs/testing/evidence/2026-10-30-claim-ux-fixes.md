# 2026-10-30 InviteCode claim UI 修正證據

來源 `5702e91a261cf6587056eac8752c21f49a26ff33`，JAR SHA-256 `B0FB78588CA317650A83F7385441576DC5034612249318E865BB89FAB083EF72`。本機候選，尚未部署。原功能分支與 Worktree 保留，沒有 push／PR／merge。

Java 11／8 的 `check shadowJar`、Java 8 bytecode、JAR 檢查通過。338 項：314 通過、24 MySQL 條件跳過，0 失敗／錯誤。
先重現 5 項 targeted 失敗與 catalog 1 項失敗，再修正；RED XML 留在本輪 `.run/claim-ux-fixes-20261030`。
測試以可控時鐘與真實 SQLite workflow 驗證期限、關閉呼叫與訊息，不代表真人渲染結果。

根因：到期比較原先不包含相等，單次 tick 排程提早執行後不再檢查，逾時 consume 回傳 null 與沒有會話混淆。
修正：同一截止邊界、剩餘時間重排、原子讀取提交結果，過期就回覆且不進入登記；舊 String? wrapper 保留。
status 只顯示既有理由次序的第一項，不改 eligibility 判定。Dialog 標籤改邀請碼，不改實際碼長或格式。

使用者已回報 assigned tests／登記完成；唯讀核對 A→B 只有一筆 generation 2 的 CLAIMED，舊 UNLINKED 保留，獎勵 0。
輸入框逾時與文案的缺陷另外保留為新候選待複驗，不因登記完成將四個 UI 缺陷算通過。
使用者選定先完成既有關係的資格／離線補發，再停服更新。本輪沒有測服、設定、權限或 DB 變更。

## 尚待討論的相似呈現

`/ic claim` 不合資格的現有文案可能是：

> 目前無法使用邀請碼：你已建立自己的邀請碼、可登記期間已結束。

這裡仍合併所有理由，用頓號分隔；本輪未改。建議後續討論是否和 status 一樣只提示最主要原因。
儲存切換警告與正式離線報告保留：它們是操作流程／狀態資訊，不是同類錯誤＋重試指引。

## 雙語文案變更（17 鍵）

### zh_TW

- `runtime.usage`
  - 原：`&c指令用法不正確。&f請使用 /{label} help 查看說明。`
  - 新：`&c指令用法不正確，請使用 /{label} help 查看說明。`
- `runtime.help-page-error`
  - 原：`&c找不到此頁面或主題。&f請使用 /ic help 返回常用指令。`
  - 新：`&c找不到此頁面或主題，請使用 /ic help 返回常用指令。`
- `command.claim.chat-timeout`
  - 原：`&e邀請碼輸入已逾時。&f請重新執行 /invitecode claim。`
  - 新：`&c邀請碼輸入已逾時，請重新執行 /invitecode claim。`
- `command.claim.dialog-label`
  - 原：`六位邀請碼`
  - 新：`邀請碼`
- `command.claim.dialog-timeout`
  - 原：`&e邀請碼輸入已逾時。&f請重新執行 /invitecode claim。`
  - 新：`&c邀請碼輸入已逾時，請重新執行 /invitecode claim。`
- `command.claim.invalid-code`
  - 原：`&c邀請碼格式不正確。&f請檢查後重新輸入。`
  - 新：`&c邀請碼格式不正確，請檢查後重新輸入。`
- `command.claim.unavailable`
  - 原：`&c這組邀請碼不存在或已失效。&f請向邀請人確認目前的邀請碼。`
  - 新：`&c這組邀請碼不存在或已失效，請向邀請人確認目前的邀請碼。`
- `command.claim.confirmation-timeout`
  - 原：`&e邀請確認已逾時。&f請重新執行 /invitecode claim。`
  - 新：`&c邀請確認已逾時，請重新執行 /invitecode claim。`
- `command.claim.no-confirmation`
  - 原：`&e目前沒有可確認的邀請，或確認已逾時。&f請重新執行 /invitecode claim。`
  - 新：`&c目前沒有可確認的邀請，或確認已逾時，請重新執行 /invitecode claim。`
- `command.claim.matching-network-denied`
  - 原：`&c你與邀請人近期使用相同網路，依伺服器規則無法登記。&f如需協助，請聯絡管理員。`
  - 新：`&c你與邀請人近期使用相同網路，依伺服器規則無法登記，如需協助請聯絡管理員。`
- `command.error`
  - 原：`&c操作失敗。&f請聯絡管理員檢查伺服器紀錄。`
  - 新：`&c操作失敗，請聯絡管理員檢查伺服器紀錄。`
- `command.stopping`
  - 原：`&e插件正在停止，暫時無法操作。&f請稍後再試。`
  - 新：`&c插件正在停止，暫時無法操作，請稍後再試。`
- `admin.error`
  - 原：`&c管理操作失敗。&f請檢查伺服器紀錄。`
  - 新：`&c管理操作失敗，請檢查伺服器紀錄。`
- `admin.reload-failed`
  - 原：`&c重新載入失敗，仍使用上一份有效設定。&f請檢查伺服器紀錄並修正設定。`
  - 新：`&c重新載入失敗，仍使用上一份有效設定，請檢查伺服器紀錄並修正設定。`
- `admin.storage-config-unavailable`
  - 原：`&c設定尚未成功載入，無法判定儲存位置。&f請檢查伺服器紀錄。`
  - 新：`&c設定尚未成功載入，無法判定儲存位置，請檢查伺服器紀錄。`
- `admin.storage-failed`
  - 原：`&c儲存結構更新失敗。&f請檢查伺服器紀錄。`
  - 新：`&c儲存結構更新失敗，請檢查伺服器紀錄。`
- `admin.doctor-storage-failed`
  - 原：`&c儲存健康檢查失敗。&f請檢查伺服器紀錄。`
  - 新：`&c儲存健康檢查失敗，請檢查伺服器紀錄。`
### en_US

- `runtime.usage`
  - 原：`&cInvalid command usage. &fUse /{label} help for instructions.`
  - 新：`&cInvalid command usage, so use /{label} help for instructions.`
- `runtime.help-page-error`
  - 原：`&cThat page or topic was not found. &fUse /ic help to return to common commands.`
  - 新：`&cThat page or topic was not found, so use /ic help to return to common commands.`
- `command.claim.chat-timeout`
  - 原：`&eCode input has expired. &fRun /invitecode claim again.`
  - 新：`&cCode input expired, so run /invitecode claim again.`
- `command.claim.dialog-label`
  - 原：`Six-character Invite Code`
  - 新：`Invite code`
- `command.claim.dialog-timeout`
  - 原：`&eCode input has expired. &fRun /invitecode claim again.`
  - 新：`&cCode input expired, so run /invitecode claim again.`
- `command.claim.invalid-code`
  - 原：`&cInvalid Invite Code format. &fCheck the code and enter it again.`
  - 新：`&cThe Invite Code format is invalid, so check it and enter the code again.`
- `command.claim.unavailable`
  - 原：`&cThis Invite Code does not exist or is no longer active. &fAsk the inviter for their current code.`
  - 新：`&cThis Invite Code does not exist or is no longer active, so ask the inviter for their current code.`
- `command.claim.confirmation-timeout`
  - 原：`&eInvitation confirmation has expired. &fRun /invitecode claim again.`
  - 新：`&cInvitation confirmation expired, so run /invitecode claim again.`
- `command.claim.no-confirmation`
  - 原：`&eThere is no invitation to confirm, or it has expired. &fRun /invitecode claim again.`
  - 新：`&cThere is no invitation to confirm, or it has expired, so run /invitecode claim again.`
- `command.claim.matching-network-denied`
  - 原：`&cServer rules prevent this claim because you and the inviter recently used the same network. &fContact an administrator if you need help.`
  - 新：`&cServer rules prevent this claim because you and the inviter recently used the same network, so contact an administrator if you need help.`
- `command.error`
  - 原：`&cThe operation failed. &fContact an administrator to check the server log.`
  - 新：`&cThe operation failed, so contact an administrator to check the server log.`
- `command.stopping`
  - 原：`&eThe plugin is stopping and cannot accept operations. &fTry again later.`
  - 新：`&cThe plugin is stopping and cannot accept operations, so try again later.`
- `admin.error`
  - 原：`&cThe administrative operation failed. &fCheck the server log.`
  - 新：`&cThe administrative operation failed, so check the server log.`
- `admin.reload-failed`
  - 原：`&cReload failed. The previous configuration remains active. &fCheck the server log and fix the configuration.`
  - 新：`&cReload failed while the previous configuration remains active, so check the server log and fix the configuration.`
- `admin.storage-config-unavailable`
  - 原：`&cConfiguration is not loaded, so the storage location is unknown. &fCheck the server log.`
  - 新：`&cBecause configuration is not loaded, the storage location is unknown, so check the server log.`
- `admin.storage-failed`
  - 原：`&cStorage schema update failed. &fCheck the server log.`
  - 新：`&cStorage schema update failed, so check the server log.`
- `admin.doctor-storage-failed`
  - 原：`&cThe storage health check failed. &fCheck the server log.`
  - 新：`&cThe storage health check failed, so check the server log.`
