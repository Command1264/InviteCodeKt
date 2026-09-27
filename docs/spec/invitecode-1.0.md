# InviteCode 1.0 設計規格

## 產品邊界

InviteCode 在一名 Inviter 與一名 Invitee 之間建立一筆持久的 Invitation，依可設定的 policy 判定該 Invitation 是否符合資格，並交付可稽核的獎勵。這是新的產品線：舊版原始碼、設定與 `playerData.json` 只作為參考，不提供相容層或 importer。

## Invitation 生命週期

- 每位玩家都擁有自動產生的 Invite Code，但只有另一名玩家 Claim 該邀請碼後，擁有者才成為 Inviter。
- 一名 Invitee 最多只能有一名 Inviter；一名 Inviter 可以有多名 Invitee。
- 玩家只有在沒有 Invitation、尚未成為 Inviter，且仍在 Claim Window 內時才可 Claim。
- First Seen At 是 InviteCode 首次觀察到該玩家的時間；Claim Window 預設為 24 小時。
- Claim 必須經過 30 秒內完成的確認，確認畫面顯示目前的 Inviter 名稱與邀請碼。
- Claimed Invitation 只能成為 Qualified Invitation 一次；Qualification 為永久狀態。
- 一般玩家不能撤銷 Invitation。可稽核的管理操作支援 Unlink、Eligibility Reset、未 qualified Invitation 的 Transfer，以及 Force Qualification。

## Invite Code 與輸入方式

- 預設使用唯一的六字元十六進位 Color Invite Code。
- Minecraft 1.16 以上版本顯示邀請碼實際代表的 RGB 顏色；舊版 client 使用最接近的 legacy color。
- 未來可選擇啟用 Crockford Base32 與經過驗證的 custom code。
- 有對應能力的新版 Paper 與 Spigot 伺服器使用原生 Dialog 文字輸入。
- 舊版伺服器使用一次性的私密聊天輸入，timeout 為 60 秒。
- 所有版本都保留 `/invitecode claim <code>`。

## Qualification

- 一份 policy 由多個 OR group 組成，每個 group 內的 condition 使用 AND。
- 內建 condition 包含累積在線時間、經過時間、不同登入日數、Bukkit permission、PlaceholderAPI 比較與管理員核准。
- Placeholder 發生錯誤時產生 `UNKNOWN`，不得讓 Invitation 因此 qualified。
- 其他插件可註冊 condition provider 或要求重新評估；只有明確的 Admin API 可以強制 qualification。
- Event 在 transaction commit 後送出，且不可取消。
- Qualified record 保留穩定的 policy ID 與不可變的 revision hash。

## Reward

- Qualification Reward 可發給 Inviter、Invitee 或雙方；Milestone Reward 依 Inviter 的 qualified invitation 數量觸發。
- Action 包含 console command、player command、message 與 Vault Economy deposit。
- Player command 不得暫時取得 operator 權限。
- 必須在線才能執行的 action，在玩家離線時維持 Pending，並於玩家登入後重試。
- Command dispatch 被接受即視為已交付，不推定下游 side effect 是否成功。
- 處理途中若發生無法安全判定結果的中斷，狀態設為 `UNCERTAIN`，不得自動重試。
- Reward Delivery 與 action state transition 必須可稽核，並以 idempotency key 防止重複發放。

## Storage 與隱私

- SQLite 與 MySQL 是 production backend；MySQL 必須在多個 plugin instance 同時操作時仍維持正確性。
- JSON 用於小型安裝環境、測試與有版本的資料交換。
- 全新 backend 在首次啟動時自動建立 schema、metadata 與所需 secret，不要求管理員先執行初始化指令。
- 切換 backend 時必須執行明確的 migration，包含 preflight、暫停寫入、verification 與 cutover。
- 已存在 backend 的 schema 升級不得在一般啟動時自動執行。
- Storage 發生錯誤時採拒絕操作的安全失敗模式；插件不得退回 memory 或其他 backend。
- IP 防濫用機制保存 keyed hash 30 天，不保存原始位址。
- Anonymization 只能在所有實例停止且完成備份後以離線工具執行；舊 Minecraft UUID 改為隨機歷史 ID，移除名稱、IP hash、登入日期及相關自由文字，保留歷史一致性。未處理獎勵會阻止操作；舊 Invite Code 以獨立用途的 keyed hash 保留防止重發，同一玩家再次加入視為新玩家。詳見 ADR 0005。
- Log 不得包含憑證與 IP hash。

## 設定與維運

- 設定拆分為 general、storage、qualifications、rewards 與 locale 檔案。
- Reload 的新設定無效時，繼續使用前一份有效設定。
- Storage 與 secret 變更後必須重新啟動。
- Configuration migration 產生可供 review 的輸出，不得在未告知的情況下改寫使用者檔案。
- `doctor` 是唯讀指令，用於回報 storage、integration、configuration 與 reward delivery 健康狀態。
- 1.0 不包含 metrics、telemetry 或自動下載更新。

## 平台與相容性

- 使用 Kotlin 與 Gradle Kotlin DSL，封裝為單一 JAR。
- 舊版基線為 Java 8 bytecode，不得使用 NMS。
- 官方支援範圍為 Spigot 與 Paper 1.8.8 起的已測試版本。
- 發行測試涵蓋 Spigot 1.8.8、1.12.2、1.16.5；Paper 1.20.6、1.21.8；以及發行當下最新的 Paper 與 Spigot。
- Folia 為實驗性支援。Folia 測試失敗會阻止宣稱 Folia 相容，但不阻止 Paper／Spigot 發行。

## 初期非目標

- 匯入舊版資料或設定
- Reward exchange GUI
- 原生 permission grant Reward Action
- NMS integration
- Metrics 或自動更新
- 啟動時自動升級既有 storage schema 或搬移 backend 資料
