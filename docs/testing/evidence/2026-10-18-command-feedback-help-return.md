# 框線與父頁返回修正：Folia 局部更新

候選 `760c51a8b3ae50e172cda8218f62aa5c8b4b9de7`；SHA-256 `D2669D283F5ACD0FB7AB1A9B45C398371E88EE9B0B1795A29DCCB429E1A3A012`。
Java 8／11 `mise exec [java@local-8] -- .\gradlew.bat check shadowJar --console=plain` 通過；
322 cases，298 passed、24 MySQL 條件測試 skipped，零失敗。
先以測試重現第 2 頁返回錯誤，再驗證權限過濾後的位置、所有分類返回包含自己的頁面、
runtime reload 按鈕與字寬近似補線、console 欄寬及補線上限。
獨立 review 發現管理索引返回未包含管理入口，已改回 `/ic` 並補測；其餘無阻擋。

Folia 在本輪部署前已停止（停止防護檢查沒有送出 Stop），重新核對 Stopped、無 host、port 釋放後，
持管理鎖建立新備份 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-folia-26.1.2\.invitecode-backups\help-return-20261018-083101-9be8492c`，更新並啟動 run `01820f515a9e4ce09d733a12215c03e3`。
當次 Done／ACTIVE、console help、reload／doctor／storage 的返回 2/2 目標均核對通過。
世界／設定／權限在部署期間保持不變，重啟後資料庫各表與 key 仍和備份一致。
沒有 reset／unlink、schema 變更、核心或路由更新。
Spigot／Paper 仍停止，未部署本輪候選。沒有 push、PR、merge 或發行。

框線為字寬近似，不保證自訂資源包、字型、GUI scale 或窄聊天視窗像素對齊；
單次最多補 256 個線字元。Console 使用終端欄寬，玩家使用預設字型估算。
下一步僅依 [最新 Folia 清單](../command-feedback-human-acceptance.md) 複驗框線與返回；
真人尚未通過，不啟動其他兩台或要求立即做詳細流程。
共用規則與回歸測試涵蓋未來新增指令，沒有逐項寫死頁碼。

既有單一 HTML 已同步；JS 語法與 DOM stub 的 reload 返回第 2 頁檢查通過，非 Minecraft 渲染證據。
