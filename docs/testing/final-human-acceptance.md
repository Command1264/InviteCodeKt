# InviteCode 最終真人驗收

## 最新 develop 產物

最新 `fb215c0` 產品來源的同 JAR 證據及固定 Human 部署狀態見[本輪驗證紀錄](./evidence/2026-10-02-issue-12-current-artifact-retest.md)。本輪真人操作依[短版驗收清單](./current-artifact-human-acceptance.md)，保留既有世界、玩家與已通過的歷史結果，不因測服不存在而暫緩。下列 BA2D48 與 889DAD 產物區段是歷史證據，不能代替最終 D6AF 產物的當次驗收。

## 同產物自動複驗（先前產物）

2026-10-02 的整合候選自動結果見[同 JAR 複驗紀錄](./evidence/2026-10-02-issue-12-final-artifact-retest.md)。該輪僅使用新的 InstanceHarbor Agent 實例，沒有部署至下述固定 Human 測服。下述使用者已確認的真人結果維持有效，但受測產物不同；不可將自動封包檢查稱為整合 JAR 的真人畫面驗收。本輪只修正測試 runner 與文件，沒有改動產品 runtime，無須重做剛通過的完整三台 UX 流程。

歷史區段中的 `start.ps1`／TestLab 路徑僅供追溯，現在專案 runner 明確只操作 Agent 實例。固定 Human 測服的操作依 InstanceHarbor 現行管理入口與本節核對過的 `instances/Human/InviteCode` 路徑進行，不使用自動測試腳本。

## 2026-10-02 固定測服複驗回饋

目前真人操作使用 InstanceHarbor 的 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\` 下三台固定測服：`invitecode-spigot-1.8.8`、`invitecode-paper-26.2`、`invitecode-folia-26.1.2`，各自保留原有世界與插件資料；下方舊 TestLab 路徑是歷史紀錄。

使用者在同一台電腦以兩個帳號操作時回報同網路 Claim 被拒絕；Folia 當次 log 亦有相符拒絕紀錄，因此同網路 `DENY` 拒絕項目記為真人通過，不推論其他網路策略或三台完整功能均通過。三台固定測服的 `config.yml` 已備份並暫時改成 `anti-abuse.matching-ip-action: ALLOW`，供單機雙帳號後續驗收；產品預設保持 `DENY`。完成測試後把此值改回 `DENY` 並執行 `/ic reload`。

確認期限文字移除中文及英文末尾冒號，包含聊天確認與原生 Dialog 的 prompt。既有測服的 `messages/zh_TW.yml`、`messages/en_US.yml` 先備份再同步修正；這些文字和網路策略可透過 `/ic reload` 套用，無須替換運行中的 JAR。此次受測為既有 JAR `889DAD100D56D18C425D7299FD759EAA3DD822C863CF55FF486F7D8F372EFE3C` 加上 `13d9c9c` 的外部語系修正，並非其他分支的新 JAR。

使用者於本輪確認 Folia 26.1.2、Paper 26.2、Spigot 1.8.8 的確認畫面、同意／不同意、逾時與重啟後資料保留「全部通過」。這些真人項目記為通過；期限排版由本輪確認畫面複驗涵蓋。同網路 `ALLOW` 測試完成後恢復為 `DENY`。此結果不替代其餘 Issue #12 發行門檻審查，也不代表 merge 或正式發行授權。下方尚待／暫緩敘述為歷史各輪交接狀態，以本節的已確認項目為準。

此清單處理合成玩家無法判斷的畫面與互動。Issue #12 的自動證據見[測試矩陣](./evidence/2026-09-24-issue-12-startup-matrix.md)。使用者已於三個版本完成第一輪真人功能測試，回報主要流程大致正常，並提出下方 UX 修正；新版本仍待真人驗收，不宣稱 1.0 發行門檻完成，也不更新 `main` 或發布 JAR。

目前依使用者決定，第三輪與後續真人驗收暫緩，待多插件、多伺服器的真人驗收管理方案就緒後集中執行。自動驗證的通過結果與真人驗收狀態分開記錄；這項暫緩不阻擋獨立的本機開發與自動檢查，也不表示 Issue #12 已完成驗收。下列實例與步驟保留作為當前版本的交接資料；正式開始真人驗收前，須先核對當時的分支 HEAD、JAR 雜湊、實例內容與新方案的啟動／路由方式，再決定是否沿用這些實例。

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

## 第三輪 UX 驗收（待真人操作）

本輪 JAR SHA-256 為 `889DAD100D56D18C425D7299FD759EAA3DD822C863CF55FF486F7D8F372EFE3C`。`/ic code` 的六碼本身可點擊並有可翻譯懸浮提示；新版直接複製、舊版填入聊天列。`/ic claim` 在開啟輸入流程前檢查資格，提交碼與最後確認仍複查。確認期限預設改為 60 秒，可由 `config.yml` 調整。舊版聊天確認使用四行摘要與第五行綠／紅選項；有 Dialog 能力的 Paper／Spigot 在輸入後開啟第二個原生確認 Dialog，其文字按鈕為綠／紅色，提示可翻譯，底色由 Minecraft 客戶端決定。畫面顯示設定期限，伺服器到期後關閉 Dialog，不逐秒刷新數字。

Paper 26.2、Spigot 26.2、Spigot 1.8.8、Folia 26.1.2 的隔離合成玩家流程已通過；除舊版 Spigot 外均收到了輸入與確認 Dialog 封包，四者完成 Claim、獎勵與重啟。這些證據不證明真人客戶端的按鈕點擊、剪貼簿或畫面顏色。測試實例均已由 runner 正常關閉。第三輪真人驗收須使用另外建立、雜湊相符的空白實例，不要沿用第二輪 JAR 或玩家資料。

| 第三輪真人實例 | 連線位址 | 可見 PowerShell 7 視窗中的啟動指令 |
| --- | --- | --- |
| `issue12-ux3-human-paper-262` | Paper 26.2，`127.0.0.1:62620` | `& 'D:\Code\Java\Plugin\InviteCodeKt\worktrees\test-final-release-acceptance\scripts\test-servers\start.ps1' -Profile paper-latest -Instance issue12-ux3-human-paper-262` |
| `issue12-ux3-human-spigot-188` | Spigot 1.8.8，`127.0.0.1:51888` | `& 'D:\Code\Java\Plugin\InviteCodeKt\worktrees\test-final-release-acceptance\scripts\test-servers\start.ps1' -Profile spigot-1.8.8 -Instance issue12-ux3-human-spigot-188` |
| `issue12-ux3-human-folia-2612` | Folia 26.1.2，`127.0.0.1:63620` | `& 'D:\Code\Java\Plugin\InviteCodeKt\worktrees\test-final-release-acceptance\scripts\test-servers\start.ps1' -Profile folia-latest -Instance issue12-ux3-human-folia-2612 -TestLabRoot 'D:\Code\Java\Plugin\TestLab\worktrees\fix-folia-stable-profile'` |

三者預設 `zh_TW` 且會跟隨玩家客戶端語系；驗收繁體中文時請將客戶端設為繁體中文。測試政策為同 IP `ALLOW`、登入 1 天達資格，且 `workspace.json` 的插件雜湊皆等於本節所列 JAR。請一次只開一台；看到 `Done` 與 InviteCode 已啟用後才連線，測完在該伺服器 Console 輸入 `stop`，確認結束後再啟動下一台。

真人依序只開一台：A 執行 `/ic code`，滑鼠移到**六碼本身**應出現提示，點擊後新版剪貼簿應只有六碼，舊版則填入聊天列。B 執行 `/ic claim`，原生輸入 Dialog 送出碼後應顯示邀請人、顏色正確的邀請碼、問題與期限、綠色「同意」和紅色「不同意」；Spigot 1.8.8 應顯示相同語意的五行聊天確認。先點「不同意」確認沒有 Invitation，再以新流程點「同意」確認建立。另用已用碼、已成為 Inviter 或逾期的玩家試 `/ic claim`，應立即顯示原因且不出現輸入框。預期沒有 InviteCode exception；回覆各版本的「複製、資格預檢、Dialog／聊天確認、按鈕、繁體中文」結果與實際畫面差異。真人通過後，才繼續 Issue #12 的發行門檻判定；Folia Vault provider 仍未驗證。
