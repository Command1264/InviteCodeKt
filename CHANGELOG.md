# 變更紀錄

本檔案記錄此專案所有值得注意的變更。

## 尚未發行

### 新增

- help 使用同列 prefix／標題分隔線與固定三項導航；可用項目黃色且可點擊，停用項目深灰且沒有互動事件，Console 同步呈現狀態。

- 統一指令、help、status／stats、聊天確認、原生 Dialog 與 MESSAGE 資格通知的 prefix、樣式及 HEX 解析；Console 使用可讀 16 色且不額外套裝飾，動態值不會被重新當成格式。
- 套用完整繁中／英文文案；help 改為五項常用首頁、權限篩選的分類／分頁與完整用法詳情；唯讀導覽可點擊，操作只填入指令，所有 help 路徑及適用參數支援權限分級 Tab；確認畫面的 prefix 獨立一行，保留邀請碼複製、綠紅按鈕與 hover。已有自訂語系不被覆寫。
- messages 缺鍵可使用 JAR 內建語系備援；設定載入前補回缺少的內建 YAML，不覆寫既有設定，寫入中斷不留下半份目標檔案。
- 初始 Kotlin 多模組專案骨架。
- 標準領域詞彙與已接受的 1.0 設計規格。
- Java 8 bytecode 驗證與初始 CI workflow。
- 最小 Bukkit 插件 metadata 與設定範例。
- SQLite persistence adapter、明確 schema migration、原子 Claim／Reward Delivery、可稽核且具復原流程的 Reward 狀態歷史，以及跨重啟 repository contract tests。
- Claim 防濫用使用獨立 HMAC-SHA-256 密鑰與 30 天到期網路雜湊，支援 `ALLOW`／`WARN`／`DENY`，不保存或記錄原始 IP 與雜湊。
- 可設定的 Qualification Policy、內建與外部 condition provider、短路求值、不可逆 qualification，以及 Qualification Fact 的 SQLite transaction。
- Qualification 與 Inviter milestone 獎勵規劃、穩定冪等鍵、先保存後執行的 Reward Action 狀態流程、離線補發邊界、失敗復原與 Vault Economy adapter。
- Bukkit runtime 狀態、版本化 YAML 與語系 snapshot、明確 SQLite migration／maintenance mode，以及不阻塞 server main thread 的單一背景 executor。
- 全新 SQLite 安裝會在首次啟動自動建立 schema 與防濫用密鑰；既有資料庫升級仍使用明確 migration。
- 玩家 Invite Code、Claim Confirmation、私密聊天輸入、status／stats、登入日 Qualification 與 Pending Reward 補發流程。
- 可稽核的 Invitation、Eligibility、Reward、Code Rotation 與 Storage 管理指令，以及唯讀 doctor 與原子 reload。
