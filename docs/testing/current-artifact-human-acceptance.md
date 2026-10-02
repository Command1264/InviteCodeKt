# 最新產物短版真人驗收

本輪只補最終產物證據。先前三台確認畫面、同意／不同意、逾時與重啟通過的紀錄保留，不要求重新執行整套長流程。自動結果與部署狀態見[最新產物驗證紀錄](./evidence/2026-10-02-issue-12-current-artifact-retest.md)。三台部署與 migration、重啟及資料保留比對已完成，可執行下列步驟。

## 待驗收版本

- 產品來源：`418c8298e8deb6c7fdef66d6e0ff67f78d807a89`，已合入 develop `fb215c09ee4bbce96050ef4aebf97a1905ed984a`。
- 插件 SHA-256：`D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C`。
- 固定測服：`invitecode-spigot-1.8.8`、`invitecode-paper-26.2`、`invitecode-folia-26.1.2`，位於 `D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\`。
- 使用既有世界、帳號與邀請資料；不清空資料、不重設資格。三台保持同 IP `DENY`，本清單不需要新的成功 Claim。

## 每台依序操作

1. 從 Windows 開始功能表開啟已安裝的 **PowerShell 7**，在桌面視窗執行：

   ```powershell
   & 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'D:\Code\Java\Plugin\InstanceHarbor\repo\scripts\manage-human-servers.ps1'
   ```

   依名稱選擇一台 InviteCode 測服啟動；不要依照舊截圖的編號。共用 `lobby` 與 Velocity 也必須運行。用 `V` 加該台編號查看當次 `Done`、InviteCode 已啟用，再確認正確 port 監聽。只看到啟動請求不算完成。

2. 用 Minecraft 連到 `localhost:25565`，再輸入該台路由：

   ```text
   /server invitecode-spigot-1.8.8
   /server invitecode-paper-26.2
   /server invitecode-folia-26.1.2
   ```

   每次只執行目前那台的指令。預期切服成功，`/ic help` 正常，繁體中文沒有亂碼。Spigot 的舊版行為用 1.8.8 客戶端確認；Paper 用 26.2 客戶端；Folia 用代理已支援的客戶端版本。客戶端語言設為繁體中文。

3. **曾建立邀請碼的帳號**輸入 `/ic code`。將滑鼠放在六碼本身，預期出現複製提示。Paper／Folia 點擊後貼到本機文字編輯器，應只有六碼，沒有色碼或額外文字；Spigot 1.8.8 點擊後應把六碼填入聊天列，可手動選取複製，勿按 Enter 公開送出。顏色、懸浮文字與繁體中文應正常。

4. **已使用邀請碼的帳號**輸入 `/ic claim`。預期立即告知已使用／不符合資格，不開啟輸入 Dialog 或聊天輸入流程。曾建立邀請碼的帳號也可檢查，預期立即回覆 Inviter 不能 Claim。這些拒絕是正確結果，不需要關閉同 IP 檢查。

5. 輸入 `/ic status`、`/ic stats`，記錄邀請狀態與數量。切回 `/server lobby`，在 TUI 用 `S` 加該台編號正常停止，等待「已停止」後重新啟動；再切回該台、輸入上述兩個指令。預期原邀請碼與邀請關係保留，沒有 InviteCode exception。數量若因正常資格達成而變動，保留 log 並回報原因，不用自行重設資料。

6. 完成後切回 lobby，正常停止該台，再對下一台重複步驟 1–5。測試結束後三台 InviteCode 都關閉；共用入口若其他測試還需要使用，不必為本插件關閉。

## 回覆及限制

請分別回覆三台的「切服／指令、六碼提示與複製、資格預檢、繁體中文、重啟保留」是否通過；失敗時附實際畫面或文字及 `logs/latest.log`，保留原資料，不重建伺服器。不要提供帳號密碼或 anti-abuse key。

此短版不會重新建立 Invitation。原生 Dialog 的實際提交與同意／不同意沿用已確認的歷史結果；合成封包只算自動證據。若日後要以全新合資格帳號複驗完整流程，再另外界定測試帳號與可回復資料；不要為了開啟 Dialog 直接 unlink 或清空現有資料。

真人結果通過後，仍須完成 Issue #12 的門檻結論與文件交付；這不代表允許 push、PR、merge、tag 或正式發行。Folia 保持實驗性，第三方 provider 的支援範圍另列限制。
