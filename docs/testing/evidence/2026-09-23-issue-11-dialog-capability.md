# Issue #11 Dialog 與 RGB 自動化驗證證據

- 受測 branch：`codex/feat/dialog-rich-text`
- 基準 commit：`3b56df0`
- JAR SHA-256：`37B85412975B6A1DEC40943141588D631EE8E7849873627DDCB61BA7D5DD539B`
- TestLab commit：`78fd831`
- 測試日期：2026-09-23
- 範圍：原生 Dialog adapter、RGB formatter、舊版降級與不需要真人操作的伺服器 smoke

## 自動化建置與測試

在 `local-8` 下執行 `clean check shadowJar`，Gradle 39 個 task 全部通過。驗證包含：

- Dialog capability 存在與缺少時的輸入模式選擇。
- Dialog 成功提交只消耗一次 callback。
- 空值與格式錯誤交由既有領域驗證拒絕。
- 取消、登出與 backend 開啟失敗不會提交 Invite Code。
- 六碼 `HEX_COLOR` 使用完全相同的 RGB 值；非顏色格式維持純文字。
- 產物保持 Java 8 bytecode 並通過既有 JAR 結構檢查。

## 真實伺服器 smoke

| Profile | Server | Java | Doctor capability | 結果 |
| --- | --- | --- | --- | --- |
| `spigot-1.8.8` | Spigot 1.8.8 | `local-8` | `Dialog=NONE`、`RGB=false` | 通過；舊版未載入新版 Dialog 型別 |
| `paper-1.21.8` | Paper 1.21.8 build 60 | `local-21` | `Dialog=PAPER`、`RGB=true` | 通過；原生能力邊界可綁定 |
| `spigot-latest` | Spigot 26.2 | `local-25` | `Dialog=SPIGOT`、`RGB=true` | 通過；Spigot adapter 可綁定 |
| `paper-latest` | Paper 26.2 build 128 | `local-25` | `Dialog=PAPER`、`RGB=true` | 通過；Paper adapter 可綁定 |

四個隔離實例均正常啟用與停用 InviteCode，沒有 InviteCode linkage error、legacy plugin warning 或未處理例外，測試後也沒有遺留 TestLab Java server process。Paper／Spigot 26.2 log 中另有 OSHI 讀取 Windows performance counter 的例外；它發生於插件載入前，並非 InviteCode 造成。

## 待最終真人驗收

本輪沒有連入實際玩家，因此不把 Dialog 畫面、按鈕、Escape 關閉、客戶端精確顏色或完整 Claim／Confirmation 流程標記為已通過。這些項目依專案測試策略集中到插件接近完成時，以同一個發行候選 JAR 做最終真人驗收。
