# Folia 健康檢查翻譯與配色候選

來源 `1a83b7328353f132dfb5da29b6cde15e3ade50d0`，JAR SHA-256 `5D7E7782E8BAEA222785DA8FA8B15416E777649517C5F63AD53101D48BE6733D`。

330 項：306 通過、24 MySQL 條件測試跳過，0 失敗／錯誤。Java 8／11 check shadowJar、bytecode、JAR 檢查通過。四項新增 doctor 回歸涵蓋繁英語系、值樣式、未知值 literal 與 Console 逐行 prefix。獨立 review 無阻擋問題。

Folia build 8、port 30101、JDK 25.0.4.1。已先核對停止、備份 21 個檔案，才替換唯一 JAR 與兩份語系。部署期間世界標頭、DB、key、設定與權限不變；啟動後資料庫逐表核對無列變化。其他兩台維持停止。

以下來自本次 ready run 的 latest.log；它驗證翻譯與逐行 log，不等於玩家色彩真人驗收。Console 顏色由 presenter 回歸驗證，實際終端外觀待人工。

```text
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 健康檢查
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 執行階段：運作中
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 設定世代：0
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 背景工作：接受新工作
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] PlaceholderAPI：未啟用
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] Vault 經濟服務：不可用
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 原生對話框：Paper
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] RGB 色彩：支援
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 儲存類型：SQLite
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 結構版本：9
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 待執行獎勵：0
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 執行中獎勵：0
[23:32:41] [Folia Region Scheduler Thread #0/INFO]: [InviteCode] 結果待確認：0
```

本次沒有重設玩家、修改邀請關係、schema、push、PR、merge 或發布。
