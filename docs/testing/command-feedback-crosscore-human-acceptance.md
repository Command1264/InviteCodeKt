# Spigot 1.8.8／Paper 26.3 真人驗收流程

2026-10-30。候選 `5702e91` 已部署兩台並啟動，來源及同一 JAR hash 已核對；沒有更新核心或建立新世界。
[準備與備份證據](evidence/2026-10-30-folia-closeout-crosscore-preparation.json)。Folia 已還原原資格（30m＋2 登入日）與同網路 DENY，核對後正常停止；不用重測 Folia。

## 開始前先讀

資格代次是每次重開登記資格的批次；不是 SQLite schema 或插件版號。舊紀錄保留，本輪會另外核對新關係。

- 兩台各自 B 已核准重設為 generation 2，期限到 **2026-10-31 04:03（台灣時間）**；兩台資料獨立，各測一次，不共用登記結果。
- A＝Command1（已核對為 OP）；B＝Taiwan_PingLord（非 OP）。B 不用 code；A 每換核心都重取該台碼。
- 入口都是 localhost:25565，不直連後端。Spigot route 對應 10101／Java 8；Paper route 對應 20102／Java 25。
- 原生客戶端建議分別用 1.8.8／26.3。Proxy 雖有轉版插件，實際連線仍須真人確認，不將 JAR 存在當成可登入證據。
- **Spigot：A 必須最後同意前就離線，不可執行 lp permission set。Paper：先登記，A 離線後才設測試資格 true。**
- 同網路目前 ALLOW 是為同機兩帳號測試；qualification profile 也是暫設，測後由 Agent 依各台原備份還原，不手動清空或回灌 DB。
- 兩台各自保留 generation 0 的 QUALIFIED 與兩個 DELIVERED；這不是本輪新結果。Agent 將核對本輪 generation 2 的新關係，並保留全部歷史。
- 沒有 push／PR／merge／發布。此文件與入口 port 是準備證據，真人結果仍待回報。

## Spigot 1.8.8：完整一輪

### 1. 連線與帳號

兩個帳號都連 `localhost:25565`，再各自執行 `/server invitecode-spigot-1.8.8`。A＝**Command1**，B＝**Taiwan_PingLord**。一次只測一台，先 Spigot 再 Paper。

使用建議的原生客戶端 `1.8.8`；Proxy 雖有 ViaVersion／ViaBackwards／ViaRewind，實際登入及切服仍由這一步確認。若不成功先回報，**不要直接連後端 10101／20102**。

### 2. 先記基準與取這台的碼

A 執行 `/ic code`、`/ic status`、`/ic stats`；B 執行 `/ic status`、`/ic stats`。記下碼與統計。**每換一台都重新取碼，不用 Folia 或上一台的碼。B 全程不執行 `/ic code`。**

B 的「可使用邀請碼」應為是。看到舊「已達資格」紀錄是保留歷史；本輪代次 2 尚無新關係，並非清空舊資料。A `/ic claim` 應直接因已有自己的碼而拒絕，不開輸入介面。

### 3. 主控台與權限 Tab

在**目前這台**的 InstanceHarbor 原生後台輸入以下指令，不加 `/`：

```text
ic
ic help
ic help admin 2
ic admin doctor
ic code
```

後台首頁只有一個 help 入口；每個邏輯行有核心時間戳／INFO，doctor 與一般回覆每行有 prefix，16 色可讀。PAPI／Vault 未啟用不是故障。`ic code` 應說只能由玩家執行。doctor 顯示 `無原生介面（NONE）`。

A 能看管理 help，B 看不到管理入口或管理 Tab。A／B 的 `/ic status `、`/ic stats ` 與超出合法參數的位置按 Tab，不應補出玩家名稱；Console `ic help admin ` 有候選，完整路徑後無多餘補全。

### 4. Help、錯誤句與碼互動

A `/ic` 點 help 直接開；`/ic help admin 2` 進 reload／doctor／storage 詳細說明後返回應仍是第 2 頁。玩家上一頁／返回／下一頁固定顯示，停用為灰、點擊無動作；Console 用可輸入的導航命令。框線大致對齊，窄聊天視窗或自訂字型可能自然折行。

`/ic help not-a-topic` 的錯誤與指引同句紅色；A status 只列主要拒絕原因。碼的互動：點六碼本身應填入聊天列，再手動全選／複製；貼到本機記事本只有原碼。1.8.8 不要求原生剪貼簿動作，顏色為接近的 16 色。**不要按 Enter 把已填入聊天列的碼送到公開聊天。**

### 5. 繁中／英文

A、B 將客戶端語言改 English (US)，查看 `/ic help`、`/ic status`、`/ic stats`；A 另看 `/ic admin doctor`。文字與該語系 prefix 正確、沒有缺鍵或色碼外洩，Console 仍用預設繁中。

改回繁中再看一次。若客戶端語系暫未同步，先重新登入／切入同一台並記錄；不需要修改 config 或 prefix。

### 6. 輸入：取消、逾時、格式錯誤

使用聊天輸入，**不是 Dialog**。B `/ic claim` 後在聊天輸入 `cancel`（不加斜線），應私密取消；A 看不到這段輸入。

**每次取消、逾時或格式錯誤後，都重新執行 `/ic claim` 才開始下一次輸入。**先取消一次，再重新開啟並等超過 60 秒，應收到紅色輸入逾時提示；重開後輸入 `!`，應是整句紅色格式錯誤。

可切英文重開一次看輸入文案／標籤與錯誤，再切回繁中；不用為英文重等一次 60 秒。逾時後請先重新 claim，不要直接把碼送到一般公開聊天。

### 7. 確認畫面：不同意

B 重開 claim，輸入 **A 在本台的碼**。確認文字靠左，prefix 獨立，邀請人／碼／問題／60 秒期限清楚；綠色同意、紅色不同意可點擊並有 hover。

按紅色「不同意」，不建立本輪邀請；B 仍可登記。prefix 不重複、不留尾端空格；期限文字最後不應有冒號。

### 8. 確認畫面：逾時

B 再開 claim、輸入 A 碼，進入**確認畫面之後**等超過 60 秒。這與輸入階段是兩個不同期限。聊天舊訊息不會被刪掉，但確認已失效，應有紅色提示；再點舊同意也不能登記。

再執行 `/ic confirm` 應拒絕，不能建立本輪邀請。若有異常先停下回報，不 reset、不連續送同意。

### 9. 最後同意與前置拒絕

**先讓 A 退出伺服器，B 才能按最後的綠色同意。**本台用 basic 權限測資格，可能在同意後立即達資格，不能等登記後才讓 A 離線。

A 已離線後，B 重開 claim、輸入 A 本台碼，在 60 秒內按同意。只建立一次，隨後應達資格。

B 再 `/ic claim` 應立即拒絕，不開輸入介面。**成功同意只做一次**，不要重設、不 unlink、不 retry。舊統計不是本輪新結果，後續由 Agent 核對唯一的 generation 2 新關係。

### 10. 資格與離線補發

**Spigot 不執行任何 `lp user ... permission set` 指令。**既有 LuckPerms 5.4.145 有 Gson JSON 寫入問題，本輪沒有修它。以既有 `invitecode.command.basic` 資格條件完成測試；若尚未達資格，等例行檢查或讓 B 重新入服，A 繼續離線。

若出現 LuckPerms JSON 保存錯誤，保存 log 回報，不自行升級、刪權限檔或套用 Paper 的指令。InviteCode 指令若拋出 exception、資格不符或資料結果異常，停在該步。

確認 B 達資格後，A 重新登入，再執行 `/server invitecode-spigot-1.8.8`。應收到離線獎勵；A 再退出／回服一次，不應重複補發。A stats 的已達資格數相對本台基準增加 1。**A 離線是退出遊戲伺服器，不是關掉後台伺服器。**

### 11. 成功 reload

所有確認流程結束、獎勵已收到後，在目前後台輸入 `ic reload`，應成功並繼續使用目前設定與語系；查看新回覆的 prefix、文字與色彩。

不用人手故意改壞設定。Agent 已在這兩台實測：錯誤布林設定遭拒、原快照保留、原檔逐位元組還原、成功 reload。缺檔復位也已有自動證據，不刪設定重測。

### 12. 正常重啟與資料保留

先記下 A 碼、A stats、B status，兩人退出。InstanceHarbor **只選目前這台 InviteCode 實例**，按停止，等「已停止」，再啟動並等 Done。不要按全部停止／全部重啟，也不要動 Velocity／Play。

兩人回到本台：碼、邀請、資格、統計都保留；不應再次補發。如果不方便操作重啟，可明確回報「重啟待 Agent」，其餘先完成；不要猜測通過。

### 13. 回報與關閉不用的測服

回覆本台核心、實際客戶端版本及結果：`help／權限Tab／複製／雙語／輸入與確認逾時／不同意與同意／資格補發不重複／reload／重啟保留：通過、失敗或未測`。也可說「全項通過」，有未測就另列。失敗附指令、實際結果與截圖。

驗完本台，兩帳號離開後，可在 InstanceHarbor 正常停止本台，再換另一台。Agent 最後唯讀核對新關係／reward actions／audit，並依原備份還原各台暫設；不要回灌舊 DB，也不要手動改測試權限或政策來收尾。

## Paper 26.3 build 202：完整一輪

### 1. 連線與帳號

兩個帳號都連 `localhost:25565`，再各自執行 `/server invitecode-paper-26.3`。A＝**Command1**，B＝**Taiwan_PingLord**。一次只測一台，先 Spigot 再 Paper。

使用建議的原生客戶端 `26.3`；Proxy 雖有 ViaVersion／ViaBackwards／ViaRewind，實際登入及切服仍由這一步確認。若不成功先回報，**不要直接連後端 10101／20102**。

### 2. 先記基準與取這台的碼

A 執行 `/ic code`、`/ic status`、`/ic stats`；B 執行 `/ic status`、`/ic stats`。記下碼與統計。**每換一台都重新取碼，不用 Folia 或上一台的碼。B 全程不執行 `/ic code`。**

B 的「可使用邀請碼」應為是。看到舊「已達資格」紀錄是保留歷史；本輪代次 2 尚無新關係，並非清空舊資料。A `/ic claim` 應直接因已有自己的碼而拒絕，不開輸入介面。

### 3. 主控台與權限 Tab

在**目前這台**的 InstanceHarbor 原生後台輸入以下指令，不加 `/`：

```text
ic
ic help
ic help admin 2
ic admin doctor
ic code
```

後台首頁只有一個 help 入口；每個邏輯行有核心時間戳／INFO，doctor 與一般回覆每行有 prefix，16 色可讀。PAPI／Vault 未啟用不是故障。`ic code` 應說只能由玩家執行。doctor 顯示 `Paper 原生介面（PAPER）`。

A 能看管理 help，B 看不到管理入口或管理 Tab。A／B 的 `/ic status `、`/ic stats ` 與超出合法參數的位置按 Tab，不應補出玩家名稱；Console `ic help admin ` 有候選，完整路徑後無多餘補全。

### 4. Help、錯誤句與碼互動

A `/ic` 點 help 直接開；`/ic help admin 2` 進 reload／doctor／storage 詳細說明後返回應仍是第 2 頁。玩家上一頁／返回／下一頁固定顯示，停用為灰、點擊無動作；Console 用可輸入的導航命令。框線大致對齊，窄聊天視窗或自訂字型可能自然折行。

`/ic help not-a-topic` 的錯誤與指引同句紅色；A status 只列主要拒絕原因。碼的互動：將滑鼠移到六碼本身看 hover，點擊後貼到本機記事本，只有原碼、沒有 prefix 或額外文字；支援 RGB 色彩。

### 5. 繁中／英文

A、B 將客戶端語言改 English (US)，查看 `/ic help`、`/ic status`、`/ic stats`；A 另看 `/ic admin doctor`。文字與該語系 prefix 正確、沒有缺鍵或色碼外洩，Console 仍用預設繁中。

改回繁中再看一次。若客戶端語系暫未同步，先重新登入／切入同一台並記錄；不需要修改 config 或 prefix。

### 6. 輸入：取消、逾時、格式錯誤

使用 **AUTO 的原生 Dialog**。B `/ic claim`，標籤應只寫「邀請碼」；按取消應結束。

**每次取消、逾時或格式錯誤後，都重新執行 `/ic claim` 才開始下一次輸入。**先取消一次，再重新開啟並等超過 60 秒，應收到紅色輸入逾時提示；重開後輸入 `!`，應是整句紅色格式錯誤。

可切英文重開一次看輸入文案／標籤與錯誤，再切回繁中；不用為英文重等一次 60 秒。停在原生輸入框超過 60 秒應自動關閉並提示；關閉後無法再點晚提交，相關延遲競態已有回歸測試。本輪不強制切成 CHAT；聊天路徑在 Spigot 實機驗證，不宣稱 Paper 強制 CHAT 已真人通過。

### 7. 確認畫面：不同意

B 重開 claim，輸入 **A 在本台的碼**。原生 Dialog 置中，prefix 標題無尾端空格，邀請人／碼／問題／期限清楚，按鈕文字同意綠／不同意紅；按鈕背景沿原生樣式，不要求改底色。

按紅色「不同意」，不建立本輪邀請；B 仍可登記。prefix 不重複、不留尾端空格；期限文字最後不應有冒號。

### 8. 確認畫面：逾時

B 再開 claim、輸入 A 碼，進入**確認畫面之後**等超過 60 秒。這與輸入階段是兩個不同期限。原生確認畫面應關閉並有逾時提示。

再執行 `/ic confirm` 應拒絕，不能建立本輪邀請。若有異常先停下回報，不 reset、不連續送同意。

### 9. 最後同意與前置拒絕

A 此時可留在本台。B 重開 claim，輸入 A 本台碼，在 60 秒內按同意。只登記一次，應先等待資格；**若未觸發測試權限就立即達資格，先停下回報。**

B 再 `/ic claim` 應立即拒絕，不開輸入介面。**成功同意只做一次**，不要重設、不 unlink、不 retry。舊統計不是本輪新結果，後續由 Agent 核對唯一的 generation 2 新關係。

### 10. 資格與離線補發

先讓 **A 退出伺服器**，B 留在 Paper，再在 **Paper 主控台**輸入（不要在 Spigot 執行）：

```text
lp user Taiwan_PingLord permission set invitecode.test.qualified true
```

等待約 60 秒的資格檢查，或讓 B 重新加入同一台。B 應收到資格通知；**在 B 確認達資格以前，A 保持離線**。本台 test.qualified 目前是 false，這個指令只在此步設一次；Agent 驗收後還原。

確認 B 達資格後，A 重新登入，再執行 `/server invitecode-paper-26.3`。應收到離線獎勵；A 再退出／回服一次，不應重複補發。A stats 的已達資格數相對本台基準增加 1。**A 離線是退出遊戲伺服器，不是關掉後台伺服器。**

### 11. 成功 reload

所有確認流程結束、獎勵已收到後，在目前後台輸入 `ic reload`，應成功並繼續使用目前設定與語系；查看新回覆的 prefix、文字與色彩。

不用人手故意改壞設定。Agent 已在這兩台實測：錯誤布林設定遭拒、原快照保留、原檔逐位元組還原、成功 reload。缺檔復位也已有自動證據，不刪設定重測。

### 12. 正常重啟與資料保留

先記下 A 碼、A stats、B status，兩人退出。InstanceHarbor **只選目前這台 InviteCode 實例**，按停止，等「已停止」，再啟動並等 Done。不要按全部停止／全部重啟，也不要動 Velocity／Play。

兩人回到本台：碼、邀請、資格、統計都保留；不應再次補發。如果不方便操作重啟，可明確回報「重啟待 Agent」，其餘先完成；不要猜測通過。

### 13. 回報與關閉不用的測服

回覆本台核心、實際客戶端版本及結果：`help／權限Tab／複製／雙語／輸入與確認逾時／不同意與同意／資格補發不重複／reload／重啟保留：通過、失敗或未測`。也可說「全項通過」，有未測就另列。失敗附指令、實際結果與截圖。

驗完本台，兩帳號離開後，可在 InstanceHarbor 正常停止本台，再換另一台。Agent 最後唯讀核對新關係／reward actions／audit，並依原備份還原各台暫設；不要回灌舊 DB，也不要手動改測試權限或政策來收尾。

## 何時先停下回報

無法從入口登入／切服、B 一開始不可登記、出現不是預期的介面、輸入或確認未逾時、格式碼／亂碼外洩、提前達資格、邀請或獎勵重複，均停在該步回報，不自行 reset／unlink／reward retry／mark-delivered。期限過期也回報，不修改 DB。
若正常關服看到既有 `Terminal has been closed`，保留 log 回報，由 Agent 核對退出與儲存；不在本輪自行做隔離測試或重建伺服器。

## 回報範本

```text
Spigot 1.8.8（客戶端：____）：全項通過／部分通過
未測：____
問題：步驟____，預期____，實際____

Paper 26.3（客戶端：____）：全項通過／部分通過
未測：____
問題：步驟____，預期____，實際____
```

Agent 接手核對唯一新邀請與代次、歷史保留、Reward Action 的一次性交付與重啟後狀態，再依原備份還原暫設、關閉不用的測服，整理整體驗收與交付狀態。資料核對、真人通過、push、PR、merge 與發布分開，不以這份清單替代任何 gate。最新 Folia ALPHA／Paper 26.2 adapter 的既有缺口仍另列。
