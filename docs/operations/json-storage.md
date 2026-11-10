# JSON 儲存設定與復原

JSON backend 適合小型單一伺服器、測試與資料交換。將 `plugins/InviteCode/storage.yml` 的 `backend` 設為 `JSON`，可選擇在 `json.file` 指定 `plugins/InviteCode/` 下的相對 `.json` 路徑；未設定時預設為 `invitecode.json`。第一次啟動會自動建立 JSON 檔與二進位 `anti-abuse.key`，不需管理員執行初始化指令。

JSON 檔是唯一的持久化資料來源，內含 `format-version`、`schema-version` 與按資料表分組的記錄；執行時使用暫存 SQLite 工作檔沿用現有交易、唯一性及稽核約束。每次成功寫入都會重新輸出整份 JSON，以同資料夾暫存檔原子替換正式檔，並將替換前的快照保留為 `invitecode.json.previous`（實際名稱跟隨 `json.file`）。`.lock` sidecar 以作業系統檔案鎖阻止兩個程序同時開啟；程序異常結束後檔案可能留存，鎖會由作業系統釋放，不應僅因檔案存在而刪除它。

每次寫入的成本隨整體資料量成長，不能像 MySQL 一樣讓多個插件實例共用同一份資料，也不應用於高併發或大型伺服器。JSON 格式供檢視與交換，但手動更改記錄可能破壞領域約束。切換 backend 須使用[離線跨 backend 搬移工具](./cross-backend-transfer.md)，不會自動搬移資料。JSON 格式與內部 schema 版本不相符時會拒絕開啟；schema 8 升至 9 須使用[離線維護工具](./offline-anonymization.md)，沒有線上升級指令。

若 JSON 損毀或版本不支援，插件會進入維護模式，保留原檔，**不會**把它當成空白資料重新初始化。先停止伺服器並備份正式 JSON、`.previous` 及 `anti-abuse.key`。檢查錯誤訊息與版本；若上一份快照有效，可在伺服器停機時由管理員將 `.previous` 複製回正式檔，再啟動驗證。這會失去上一份快照之後的變更。不要重新產生 `anti-abuse.key`：它是 HMAC 密鑰，應與 JSON 同時備份。若寫入或原子替換失敗，當次程序會停止後續寫入；修正磁碟問題後重啟並檢查最後一份有效快照。
