# 只能透過明確遷移流程切換 Storage

SQLite 與 MySQL 是 production backend；JSON 僅用於小型安裝環境與資料交換。變更 backend 設定時，不得在一般啟動或 reload 過程自動遷移資料。切換時必須執行明確的 preflight、migration、verification 與 cutover 流程；失敗時保留來源資料，避免在未察覺的情況下產生兩份各自寫入的資料。
