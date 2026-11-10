# 執行獎勵前先保存交付狀態

執行外部指令或 integration 前，必須先保存 Reward Delivery 與每一個 Reward Action。Action 正在處理時若發生無法安全判定結果的中斷，該 Action 進入 `UNCERTAIN`，必須由管理員決定是否重試或視為已交付。Bukkit command 與第三方經濟操作無法提供端到端的 exactly-once execution，因此不得自動重試這類不確定結果。
