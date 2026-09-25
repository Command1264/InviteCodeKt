# InviteCode 最終真人驗收

此清單處理合成玩家無法判斷的畫面與互動。Issue #12 的自動證據見[測試矩陣](./evidence/2026-09-24-issue-12-startup-matrix.md)。使用者已於三個版本完成第一輪真人功能測試，回報主要流程大致正常，並提出下方 UX 修正；新版本仍待真人驗收，不宣稱 1.0 發行門檻完成，也不更新 `main` 或發布 JAR。

第一輪受測產品基線是 `develop` 合併 commit `57b0f5d`，插件 `InviteCode-0.1.0-SNAPSHOT.jar` SHA-256 為 `FF187679BD246A30BC76A573C16179363D3A79909C0595FF97F25A13065661BC`。以下三個 TestLab 實例已留下第一輪玩家資料，使用者回報均已關閉；它們是歷史驗收對象，不用於新 JAR 的 UX 複驗。測試設定固定 `zh_TW`、同 IP `ALLOW`、登入 1 天即符合資格，這些是**測試專用**設定，不代表正式環境建議值。

| 實例 | 版本與連線位址 | 主要人工驗收 |
| --- | --- | --- |
| `issue12-human-paper-262` | Paper 26.2，`127.0.0.1:62620` | 原生 Dialog、繁體中文、六碼 HEX 的 RGB、指令與獎勵 |
| `issue12-human-spigot-188` | Spigot 1.8.8，`127.0.0.1:51888` | 私密聊天退回、legacy 顏色、舊版指令 |
| `issue12-human-folia-2612` | Folia 26.1.2，`127.0.0.1:63620` | 實驗性原生 Dialog 與玩家互動；不得據此推論 Vault 相容 |

使用相符版本的 Minecraft Java 客戶端。請在**可見的 PowerShell 7** 視窗中，一次只啟動一個實例；本機已核對 `C:\Program Files\PowerShell\7\pwsh.exe` 為 PowerShell 7.6.6。執行下列其中一行，看到伺服器 `Done` 與 InviteCode 已啟用後再連線。結束時在該視窗的伺服器 Console 輸入 `stop`，確認程序關閉後才換下一個實例。

```powershell
& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile paper-latest -Instance issue12-human-paper-262
& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile spigot-1.8.8 -Instance issue12-human-spigot-188
& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile folia-latest -Instance issue12-human-folia-2612 -TestLabRoot 'D:\Code\Java\Plugin\TestLab\worktrees\fix-folia-stable-profile'
```

每個實例使用兩名新玩家 A、B；建議使用兩個 Minecraft 客戶端，讓 A 可直接檢查是否收到 B 的公開聊天。開始前在 Console 執行 `ic admin doctor`，預期 `Runtime=ACTIVE`，Paper／Folia 顯示 `Dialog=PAPER`，Spigot 1.8.8 顯示 `Dialog=NONE`。玩家分別確認 `/ic help`、Tab 補全與 `/ic status` 可讀，Console 的管理指令可用，普通玩家不能執行管理指令。

在 Paper 26.2 與 Folia 26.1.2，讓 A 用 `/ic code` 取得六碼 HEX；B 執行 `/ic claim`。預期出現可輸入邀請碼、可提交及可取消的原生 Dialog，繁體中文不亂碼。先按原生取消鍵及試一次 Esc，確認沒有 Invitation；再次開啟後輸入 A 的碼並按**原生提交按鈕**，預期顯示 A 與碼的確認摘要，`/ic status` 此時仍無 Invitation。執行 `/ic confirm` 後，預期 Invitation 建立並達資格。這一段的**按鈕點擊、畫面外觀、鍵盤輸入與取消行為**尚無自動化證據，須由真人判斷；合成測試只確認伺服器送出 Dialog 封包及 `/ic cancel` 不建立 Invitation。

在 Spigot 1.8.8，B 執行 `/ic claim` 後，預期改為聊天輸入提示；輸入 A 的邀請碼不得被 A 或其他玩家當作公開聊天看到。確認摘要出現後仍須 `/ic confirm` 才建立 Invitation。比較兩端的邀請碼顏色：新版須符合該六碼 HEX 的 RGB，舊版則顯示最接近的 legacy 顏色。所有實例的 `/ic stats`、離線後再次登入的 Pending Reward 訊息、Console `ic admin doctor` 均應維持一致，且沒有 InviteCode exception。

若上述任一步驟失敗，請保留該實例的 `workspace.json`、`logs/latest.log` 與 `plugins/InviteCode/`，不要重建或清空；回覆「版本／實例、通過或失敗的步驟、實際看到的文字或畫面、相關 log」。不需提供帳號憑證。若全部通過，請分別回覆 Paper、Spigot、Folia 的結果，以及 Dialog 提交／取消、顏色與繁體中文是否正確。接著再判定 Issue #12 的發行門檻；Folia Vault 仍需使用**明確支援 Folia** 的 Vault 與經濟提供者另測，目前既有 EssentialsX 不能作為該測試對象。

## UX 修正的第二輪驗收

本輪 JAR SHA-256 為 `B586955C62FAE5F09F284D94203D674D84E90E8BF8F6E7B6230C496466689813`；自動證據見[UX 回歸紀錄](./evidence/2026-09-25-issue-12-ux-retest.md)。以下是另外建立的三個**空白、未啟動**實例；保留第一輪資料，請勿覆蓋原實例。三者仍用 `zh_TW`、同 IP `ALLOW` 與登入一天資格，依序使用對應版本客戶端、可見 PowerShell 7 視窗啟動，一次只開一台，測完在 Console 輸入 `stop`。

| 第二輪實例 | 連線位址 | 啟動指令 |
| --- | --- | --- |
| `issue12-ux-human-paper-262` | Paper 26.2，`127.0.0.1:62620` | `& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile paper-latest -Instance issue12-ux-human-paper-262` |
| `issue12-ux-human-spigot-188` | Spigot 1.8.8，`127.0.0.1:51888` | `& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile spigot-1.8.8 -Instance issue12-ux-human-spigot-188` |
| `issue12-ux-human-folia-2612` | Folia 26.1.2，`127.0.0.1:63620` | `& 'D:\Code\Java\Plugin\InviteCodeKt\repo\scripts\test-servers\start.ps1' -Profile folia-latest -Instance issue12-ux-human-folia-2612 -TestLabRoot 'D:\Code\Java\Plugin\TestLab\worktrees\fix-folia-stable-profile'` |

每台只須複驗新的 UX：A 輸入 `/ic code`，點擊下方複製入口；Paper／Folia 預期直接複製六碼原始碼，Spigot 1.8.8 預期填入聊天列供手動選取複製，且原有邀請碼 RGB／legacy 顏色仍正常。B 先輸入 `/ic claim`；在 Spigot 聊天模式輸入 `cancel`，預期私密取消、`/ic status` 仍無 Invitation，再重新輸入 A 的碼。Paper／Folia 以原生 Dialog 提交。確認摘要下方應有綠色「同意」、紅色「不同意」；先點「不同意」，確認沒有 Invitation；重新輸入碼後點「同意」，預期建立 Invitation。請特別檢查按鈕真的執行了對應指令、沒有公開聊天或亂碼。不同意後若需再試，請重新開啟 Claim；避免在同一 Invitee 已建立 Invitation 後重複測。

三台合成玩家流程、回歸測試與 JAR 檢查已通過；**真人剪貼簿、聊天按鈕點擊與外觀**仍待本輪驗收。排版與美術優化已記錄為後續討論，不納入本輪判定。請分別回覆三個實例的「複製／選取、紅綠按鈕、聊天 `cancel`、繁體中文」結果；若失敗，保留該實例的 log 和資料並描述實際畫面。通過後還須判定 Issue #12 的發行門檻；Folia Vault provider 仍未驗證。
