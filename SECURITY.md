# 安全政策

InviteCodeKt 尚未發布穩定版本。

疑似漏洞請私下回報 repository owner。請勿在公開 issue 中附上資料庫憑證、原始 IP 位址、IP hash、玩家資料匯出內容或可利用漏洞的細節。

與安全有關的專案規則包括：

- 不得記錄憑證與原始 IP 位址；
- storage 發生錯誤時，Claim、Qualification 與 Reward 必須採取拒絕操作的安全失敗模式；
- 外部輸入在進入 persistence 或 command execution 前必須經過驗證；
- 先前執行結果無法確認時，Reward retry 必須由明確操作觸發；
- 管理操作必須記錄執行者、原因與 Audit Record。
