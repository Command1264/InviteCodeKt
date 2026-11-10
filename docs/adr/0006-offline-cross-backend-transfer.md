# 跨 backend 搬移採離線、驗證後發布

狀態：已接受（2026-09-28，Issue #26）

SQLite、JSON、MySQL 的 schema 升級只處理原 backend；變更 `storage.yml` 不會搬走既有玩家、Invitation、Reward 或稽核資料。為避免兩端分歧，跨 backend 搬移只透過獨立 CLI 執行，來源與目的地的所有 InviteCode 實例須先停止，不在插件啟動、reload 或遊戲指令中隱式搬移。

三種 backend 的六個不同方向都使用相同的持久化業務表白名單。匯出包含玩家、碼、邀請、資格事實、Reward Delivery 與 Action、狀態轉換、稽核、登入日期、網路 hash 及已匿名化舊碼的保留 hash；schema 與 migration ledger 由目的 backend 自行建立，不從來源複製。MySQL generated columns 由目的資料庫重建。匯入前須檢查來源版本、目的地完全空白及兩端防濫用密鑰識別碼相同；不提供自動重算網路 hash 或舊碼保留 hash 的密鑰輪替。來源始終保留，不因目的地驗證成功而刪除。

SQLite 目的地先在同資料夾建立暫存資料庫；JSON 目的地先建立暫存 SQLite 工作資料庫及暫存快照。全表資料、外鍵與關鍵唯一性檢查成功，並確認來源未變後，才以同目錄的硬連結建立正式檔；若目的檔已存在，建立會失敗而不覆寫，檔案系統不支援硬連結時安全失敗。MySQL 目的地必須是空白 database；搬移先建立 `invitecode_transfer_guard`，再初始化 schema、匯入及驗證。插件啟動與搬移會以同一把 database 級 advisory lock 序列化，偵測 guard 即拒絕啟用。MySQL DDL 無法交易回滾；失敗或中斷後目的 database 須保留供檢查，不能當成空白重試，須用備份或全新 database 重新開始。完成驗證才移除 guard。

CLI 要求操作者確認停寫，並在正式搬移前提供來源備份。SQLite／JSON 由工具建立並核對 SHA-256；MySQL 要求已建立的一致性 dump，工具只能確認檔案存在且非空，實際可還原性由操作者驗證。MySQL 還需專用帳號與 database，預檢拒絕同 database 的其他可見連線；其他帳號的不可見工作階段及檢查後新連入者仍須由維運流程停機保證。目的地完成後，由管理員單獨更新 `storage.yml`，確認只啟動指向目的地的一組實例。來源與備份保留作為回復基線；切回來源前先停止目的地寫入，評估切換後新增資料，不自動雙向同步。

三核心遊戲內驗收已於 2026-10-13 確認通過，Issue #12 已結案；這不新增跨 backend 人工切換或 dump 還原的驗證主張。搬移的隔離自動證據與限制見 [Issue #26 收尾文件](../testing/issue-26-closeout.md)。隔離資料庫測試不得連到正式資料庫。本段更新驗收狀態，不改變離線搬移決策。

來源匯出完成前也須通過唯讀完整性預檢；不可只依目的地外鍵在正式匯入時拒絕。SQLite 使用 `integrity_check` 與 `foreign_key_check`，包含曾停用 CHECK／外鍵後留下的違規資料；MySQL 依目前 schema 的外鍵 metadata 對業務表查找孤立紀錄，因重新啟用 `FOREIGN_KEY_CHECKS` 不會回頭驗證既有資料。SQL 識別符僅允許業務表與欄位白名單，不輸出玩家紀錄。匯出快照同時核對格式、欄位型別、nullability 及密鑰；預檢失敗不得建立備份或發布目的地。
