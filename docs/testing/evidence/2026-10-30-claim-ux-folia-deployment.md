# Folia 四項 UI 候選部署與舊流程驗收

2026-10-30。舊候選 `1a83b73` 的資格／離線補發／再次登入不重複由使用者確認通過。
唯讀核對 generation 2 一筆 QUALIFIED、單一 Reward Delivery 完成、A／B 兩筆 MESSAGE 動作均 DELIVERED，舊 UNLINKED 保留。
這是舊候選流程通過，不代替新輸入框外觀驗收。

依使用者選定先測完再更新的順序，正常停止前次 run；退出碼 0、輸出排空、程序及 port 釋放確認。
21 個檔案在停止狀態備份與 hash 核對完成，世界保留原處且部署期間標頭不變。
備份位置：`D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\claim-ux-20261029-190401-01167f8f`。
僅更新 Folia JAR、兩語系及 workspace metadata。來源 `5702e91a261cf6587056eac8752c21f49a26ff33`，SHA-256 `B0FB78588CA317650A83F7385441576DC5034612249318E865BB89FAB083EF72`。
沒有重設 B、unlink、回灌 DB、刪 key、改核心或碰其他測服。

Folia Done／Listening；實際 JDK 25.0.4.1。doctor、更新後 help 錯誤句與無色碼外洩通過，無啟動 Exception／ERROR。
啟動後逐表核對邀請／獎勵與備份一致，DB quick_check=ok；config／key 保留。
之後依測試還原安排，僅透過 LuckPerms 將 B 的 invitecode.test.qualified true 改回原 false，持久化與 live check 通過；
其他權限語意不變。Qualified 及兩個 Delivered 仍保留。這項權限還原與部署期間的資料不變分開記錄。

Paper 26.3／Spigot 1.8.8 維持停止。Velocity 入口與 Folia 路由核對；設定與 port 不代替玩家登入驗收。
A／B 目前都不可再次 claim，新 Dialog 逾時／標籤真人複驗需後續合資格帳號安排，不自動重設 B。
matching-ip-action=ALLOW 與臨時 qualification profile 仍保留至後續驗收還原，不宣稱全部暫設已還原。
沒有新 build（重用已通過 Java 8／11 的固定候選）、push、PR、merge 或發布。
