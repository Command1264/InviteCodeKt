# 全新 Storage 自動初始化，既有資料只透過明確遷移流程變更

SQLite 與 MySQL 是 production backend；JSON 僅用於小型安裝環境與資料交換。選定 backend 尚未存在任何資料時，插件會在首次啟動自動建立該 backend 所需的 schema、metadata 與本機 secret，讓全新安裝可直接使用。自動初始化失敗時只清理由該次嘗試建立、尚未承載使用者資料的檔案，並進入可診斷的安全失敗狀態。

既有 backend 的 schema 升級、backend 切換與資料搬移不得在一般啟動或 reload 過程自動執行。這些操作必須使用明確的 preflight、migration、verification 與 cutover 流程；失敗時保留來源資料，避免在未察覺的情況下改寫資料或產生兩份各自寫入的資料。檔案或連線存在即視為既有 backend，不因內容異常而退回全新初始化。
