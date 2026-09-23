# InviteCode 真實測試伺服器

InviteCode 使用專案外的 `D:\Code\Java\Plugin\TestLab` 共用伺服器核心快取與工具，但每個 profile、Worktree 與驗收版本都建立獨立實例。World、插件設定、SQLite、log 與其他可變狀態不跨實例共用。

## 測試矩陣

正式矩陣定義於 `test-servers/matrix.json`：

| Profile | 用途 | 發行 gate |
| --- | --- | --- |
| `spigot-1.8.8` | 最低支援邊界 | 是 |
| `spigot-1.12.2` | 舊版中間邊界 | 是 |
| `spigot-1.16.5` | Java 8 世代中間版本 | 是 |
| `spigot-latest` | 最新 Spigot | 是 |
| `paper-1.20.6` | 新版 Paper 代表版本 | 是 |
| `paper-1.21.8` | 原生 Dialog 能力邊界 | 是 |
| `paper-latest` | 最新穩定 Paper | 是 |
| `folia-latest` | 實驗性 smoke | 否 |

Folia 測試失敗會阻止宣稱 Folia 相容，但不會單獨阻止 Spigot／Paper 發行。

## 環境檢查

```powershell
pwsh -NoProfile -File .\scripts\test-servers\validate.ps1
pwsh -NoProfile -File .\scripts\test-servers\doctor.ps1
```

若 TestLab foundation 尚未合併，可用 `-TestLabRoot` 指向其 task Worktree。平常也可設定 `PLUGIN_TEST_LAB_HOME`。

## 建立測試實例

先建置受測 JAR：

```powershell
mise exec -- .\gradlew.bat clean check shadowJar
```

建立實例但不代替使用者接受 Minecraft EULA：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile paper-latest `
  -Artifact .\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar
```

使用者確認接受 EULA 後，以 `-AcceptEula -Replace` 重建同一實例：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\prepare.ps1 `
  -Profile paper-latest `
  -Artifact .\invitecode-plugin\build\libs\InviteCode-0.1.0-SNAPSHOT.jar `
  -AcceptEula `
  -Replace
```

啟動：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\start.ps1 -Profile paper-latest
```

啟動腳本依 profile 透過 mise 選擇 Java，不依賴系統 `PATH` 的 Java 優先順序。人工測試時應在可見的 PowerShell 7 視窗執行，並在 server console 輸入 `stop` 正常關閉。

## 每個 profile 的最低 smoke

已準備、記錄 EULA 接受狀態的隔離實例，可使用無玩家 smoke runner：

```powershell
pwsh -NoProfile -File .\scripts\test-servers\smoke.ps1 `
  -Profile spigot-1.8.8 -Instance issue12-spigot-188
```

Runner 核對 `workspace.json` 的 profile、EULA、核心與插件 JAR SHA-256；等待插件啟用後從 Console 執行 `ic admin doctor`、`ic help` 與 `ic reload`，確認 `ACTIVE`、整合狀態、儲存版本、指令說明與重新載入成功，正常送出 `stop`，檢查嚴格 UTF-8 log，並輸出 JSON 結果。預設 backend 為 SQLite；對已明確設定 `backend: JSON` 的實例傳入 `-Backend JSON`，對 `backend: MYSQL` 的實例傳入 `-Backend MYSQL`。SQLite／JSON 會檢查本機儲存檔與 anti-abuse key；MySQL 要求測試程序環境有 `INVITECODE_DB_PASSWORD`、`INVITECODE_NETWORK_SECRET`，並確認沒有在插件資料夾建立本機 key。MySQL 的資料庫 schema／ledger 須另用該獨立服務的用戶端核對，Runner 對這兩個本機檔案雜湊輸出 `null`。它不會替使用者接受 EULA、建立資料庫、重建實例或模擬玩家。再次執行同一實例可比對重啟前後的資料與 key SHA-256；MySQL 改核對 ledger、表與稽核紀錄。完整發行 gate 仍須依下列清單補足 critical user path 與外部整合情境。

預設預期 PlaceholderAPI 與 Vault Economy 都不存在。已在獨立實例安裝 PlaceholderAPI 時加上 `-ExpectPlaceholderApi`；已安裝 Vault 與經濟提供者時加上 `-ExpectVaultEconomy`。Runner 會核對 Console doctor 的實際整合狀態，輸出對應布林值；這只驗證服務偵測，不代表實際 placeholder 展開或經濟入帳已通過。

1. 確認 InviteCode 啟用且沒有 linkage error、legacy material warning 或未處理 exception。
2. 確認所選 backend 首次啟動自動建立必要資料與 anti-abuse key（MySQL 使用外部密鑰），重新啟動後沿用既有資料。
3. 玩家與 Console 驗證 `/ic help`、`/ic reload`、Tab completion 與權限拒絕。
4. 玩家驗證 Invite Code 顯示、Claim、Confirmation、Status 與 Stats。
5. 驗證 `zh_TW`／`en_US`、UTF-8 與 client locale fallback。
6. 驗證離線 Reward 補發與 restart recovery；需要外部整合的 profile 再加入 Vault／PlaceholderAPI。

各次測試應記錄 `workspace.json`、受測 commit、JAR SHA-256、操作結果與 `logs/latest.log`。實際相容性證據尚未取得前，不得只因實例能建立就宣稱該版本受到支援。

## 自動檢查與最終真人驗收

日常開發與各功能階段先由 Agent 執行不需要玩家判斷的檢查，包括 Gradle 測試、JAR 結構與 Java 8 bytecode、伺服器啟動與正常停機、log 錯誤掃描、UTF-8 解碼、SQLite／防濫用密鑰初始化，以及適用時的重啟持久化。這些結果須綁定受測 commit 與 JAR SHA-256。

需要真人觀察或實際玩家操作的項目集中到插件功能接近完成、準備發行候選版本時執行一次完整驗收，包括指令與 Tab completion、聊天及 Dialog 輸入、顏色與語系顯示、Claim／Confirmation／Status／Stats，以及實際獎勵體驗。延後的真人項目維持「待最終真人驗收」，不得因自動 smoke 通過而標記為已通過，也不阻擋仍在開發中的本機 checkpoint。
