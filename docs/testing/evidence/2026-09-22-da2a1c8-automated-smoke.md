# `da2a1c8` 自動化伺服器 Smoke 證據

- 受測 InviteCode commit：`da2a1c8`
- JAR SHA-256：`F1C61E0B32F7EF3188DD0EEC0CFA149377B6A012EA86E666A89DC657D6439AE3`
- TestLab commit：`78fd831`
- 測試日期：2026-09-22
- 範圍：不需要真人或玩家互動的啟動、儲存與停機 smoke

## 結果

| Profile | Minecraft | Java | 結果 |
| --- | --- | --- | --- |
| `spigot-1.8.8` | 1.8.8 | `local-8` | 通過 |
| `spigot-1.12.2` | 1.12.2 | `local-8` | 通過 |
| `spigot-1.16.5` | 1.16.5 | `local-8` | 通過 |
| `spigot-latest` | 26.2 | `local-25` | 通過 |
| `paper-1.20.6` | 1.20.6 | `local-21` | 通過 |
| `paper-1.21.8` | 1.21.8 | `local-21` | 通過 |
| `paper-latest` | 26.2 | `local-25` | 通過 |
| `folia-latest` | 26.2 | `local-25` | 未執行：PaperMC 沒有 stable build |

七個可用 profile 均符合下列結果：

- 伺服器到達 `Done`，InviteCode 回報啟用。
- `logs/latest.log` 通過嚴格 UTF-8 解碼。
- 沒有 InviteCode linkage error、未處理例外、SQLite migration 失敗、legacy plugin 警告或 `No key layers`。
- 首次啟動建立 `188416` bytes 的 SQLite DB 與 `32` bytes 的防濫用密鑰。
- 伺服器接受 `stop` 或等價的正常 console 中止，所有矩陣 port 在測試後均已釋放。

另以相同 JAR 在 `spigot-1.8.8` 與 `paper-latest` 完成一次停止後重啟。兩個 profile 的 DB 與防濫用密鑰 SHA-256 在重啟前後保持不變，且第二次啟動沒有再次執行首次初始化。

同一組最低／最新邊界也由 server console 自動執行 `/ic help`、`/ic reload`、`/ic admin doctor` 與 `/ic status`。Help 與 reload 正常回覆；doctor 回報 `Runtime=ACTIVE`、SQLite schema 8、Reward backlog 全為 0，且沒有 Vault／PlaceholderAPI；玩家專屬的 status 從 Console 明確回覆「此指令只能由玩家執行」。兩個 profile 均未產生 command exception 或缺少語系訊息。

## 尚未涵蓋

本次沒有把下列項目標記為通過，統一留待接近完成時的最終真人驗收：玩家與 Console 指令、Tab completion、聊天／Dialog 輸入、客戶端顏色與語系顯示、完整 Claim 流程及實際獎勵體驗。Folia 也尚未取得可用的 stable 測試核心。
