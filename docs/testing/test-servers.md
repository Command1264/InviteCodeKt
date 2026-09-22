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

1. 確認 InviteCode 啟用且沒有 linkage error、legacy material warning 或未處理 exception。
2. 確認第一次啟動自動建立 SQLite 與 anti-abuse key，重新啟動後沿用既有資料。
3. 玩家與 Console 驗證 `/ic help`、`/ic reload`、Tab completion 與權限拒絕。
4. 玩家驗證 Invite Code 顯示、Claim、Confirmation、Status 與 Stats。
5. 驗證 `zh_TW`／`en_US`、UTF-8 與 client locale fallback。
6. 驗證離線 Reward 補發與 restart recovery；需要外部整合的 profile 再加入 Vault／PlaceholderAPI。

各次測試應記錄 `workspace.json`、受測 commit、JAR SHA-256、操作結果與 `logs/latest.log`。實際相容性證據尚未取得前，不得只因實例能建立就宣稱該版本受到支援。
