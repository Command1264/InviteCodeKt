# 訊息與 prefix：第四階段真人驗收

## 2026-10-30 輸入框複驗已準備：B generation 3，僅輸入與取消

使用者已核准這次 Folia B-only reset。正常停服／退出 0 與排空確認後，20 檔備份與 hash 通過；
backup DB quick_check=ok，世界保留原處，啟動前標頭一致。JAR／設定／權限未更換。
啟動後透過插件管理指令僅重設一次，B 起點更新、generation 2 → 3、只新增 1 筆稽核。
[備份與操作證據](evidence/2026-10-30-folia-input-dialog-reset-ready.json)。

目前候選 `5702e91`，Folia 已 Done／Listening、doctor 正常、DB quick_check=ok，入口及路由有效。
B 的登記期限至 **2026-10-31 03:25（台灣時間）**。generation 0 的 UNLINKED、generation 2 的 QUALIFIED、
兩筆 DELIVERED 均保留；generation 3 沒有新邀請，沒有任何新獎勵。A 的碼、設定、key 與權限保留。
測試資格權限維持 false；其他兩台維持停止。本輪未部署新產物，不 push／PR／merge。

### 現在只測以下輸入框，不按同意

1. B Taiwan_PingLord 連 `localhost:25565`，輸入 `/server invitecode-folia-26.1.2`。
   `/ic status` 的可使用邀請碼應為是；若仍顯示歷史已達資格，那是保留的舊邀請，不代表新代次已登記。
   B 不執行 `/ic code`。
2. B `/ic claim`：輸入標籤應只有「邀請碼」，不寫六位／6 位。
3. 停在輸入畫面，不輸入、不按按鈕，等超過 60 秒。應自動關閉並顯示整句紅色逾時提示。
4. B 再 `/ic claim`，輸入 `!` 並按繼續。應顯示「邀請碼格式不正確，請檢查後重新輸入。」整句紅色。
5. B 再開 claim，輸入 A 的原碼。到確認畫面後**只按紅色不同意／取消，絕對不要按綠色同意**。
   不需要重測資格或離線補發，不建立新邀請。A 只在需要時 `/ic code` 提供原碼。
6. 回覆 `Folia 5702e91：標籤／輸入逾時關閉／逾時提示／格式錯誤／取消：通過、失敗或未測`。
   由 Agent 唯讀核對 generation 3 仍無新邀請、舊資格與兩筆獎勵保留。

自動關閉成功後無法再點擊晚提交；排程稍晚才處理的提交／取消已由自動測試覆蓋，不要求真人製造延遲。
若 60 秒後未關閉、沒有提示、出現已登記或任何新獎勵，停在該步回報，別 reset／unlink／retry。
本組通過後仍待其餘臨時設定還原、其他核心驗收及整體交付；先前通過的項目不重做。
以下是歷史待核准提案，本次已由核准及操作證據取代。


## 2026-10-30 5702e91 三項局部真人複驗通過；輸入框待合資格帳號

使用者確認 status 主要原因、同句紅色錯誤、重啟後資料保留三項皆通過，不要求重做。
[真人回報與唯讀核對](evidence/2026-10-30-folia-ux-three-checks-human-acceptance.json)。
目前 Folia Listening，來源／JAR hash 仍符合 5702e91；A 有原碼，B generation 2 的 QUALIFIED 保留，
兩個 Reward Actions 仍 DELIVERED。本輪只有唯讀核對與文件更新。

下一個尚未完成的項目為新原生輸入框真人複驗；目前 A／B 均會在 claim 前置拒絕。
建議不增加第三帳號，只在先備份與重新核對後，針對 Folia B 執行新的單次 reset：

```text
ic admin eligibility reset Taiwan_PingLord claim-input-ui-20261030
```

這次尚未核准／執行。預期 B first_seen_at 更新、generation 2 → 3、新增一筆稽核；
generation 2 的 QUALIFIED、generation 0 的 UNLINKED、原本兩筆 DELIVERED、A／碼／世界／key／設定／權限全部保留。
本次只開啟 24 小時視窗測輸入框，不建立或同意新邀請，不重跑資格與獎勵。
若使用者改選既有合資格的第三帳號，可不用重設 B；目前不假設使用者持有第三帳號。

核准及準備完成後的最小清單（目前不要開始）：
1. B `/ic claim`，確認標籤是「邀請碼」，沒有固定位數。
2. 停在輸入框等待超過 60 秒，應自動關閉並顯示整句紅色逾時提示。
3. B 重開輸入框，輸入不合法字元，格式錯誤與重試提示同句紅色。
4. B 再輸入 A 的碼，進確認畫面後只選不同意／取消，不能同意；不建立新的邀請。
5. 回覆「標籤／輸入逾時自動關閉／逾時提示／格式錯誤／取消：通過、失敗或未測」。
   Agent 再唯讀核對 generation 3 沒有新關係，舊資格及兩筆獎勵都保留。

自動關閉正常後，已關閉視窗無法再點晚提交；排程延遲的晚提交／取消競態已由可控時鐘回歸覆蓋，
不要求真人製造伺服器延遲。完成後仍需其餘暫設還原與其他核心驗收。
本輪不啟停、不重設、不改權限或資料，不 push／PR／merge。以下為歷史安排。


## 2026-10-30 補發驗收通過；5702e91 已部署 Folia，待介面局部複驗

使用者確認資格、離線補發、再次登入不重複均沒有問題。
[真人驗收與唯讀核對](evidence/2026-10-30-folia-reward-human-acceptance.json)：generation 2 唯一邀請 QUALIFIED，
單一 delivery DELIVERED，A／B 各一個 MESSAGE action DELIVERED，舊 UNLINKED 保留。這組不要求重做。

依先前選定順序，已正常停服（退出 0、排空）、21 檔備份驗 hash、僅更新 Folia 至候選 `5702e91` 並啟動核對。
[部署證據](evidence/2026-10-30-claim-ux-folia-deployment.json)／[步驟摘要](evidence/2026-10-30-claim-ux-folia-deployment.md)。
重啟後邀請／獎勵逐表保留；config／key／世界保留，無新 reset／claim。doctor 與新錯誤句 smoke 通過。
B 測試權限已由 true 還原為原 false，只有這個 node 改變，既有 QUALIFIED／DELIVERED 不倒退。
其他兩台維持停止，沒有 push／PR／merge。

### 下一輪先做不改資料的介面核對

1. A Command1 連 localhost:25565，再 `/server invitecode-folia-26.1.2`。
2. A `/ic status`：無法登記原因只顯示「你已建立自己的邀請碼」，不另列期限已結束。
   不保證窄聊天視窗或自訂字型完全不折行，但程式不再插入第二原因行。
3. 玩家或主控台執行 `ic help not-a-topic`（玩家加 `/`）：錯誤與重試指引是同句紅色。
   切英文重做可一併檢查雙語，不需要改 config。
4. A `/ic stats`、B `/ic status` 確認重啟後本輪結果仍在，沒有再次補發。這是保留核對，不重做資格／claim。
5. 回覆 `Folia 5702e91：status 主要原因／單句紅色／資料保留：通過、失敗或未測`。

新原生輸入框還需真人確認標籤為「邀請碼」、60 秒自動關閉及提示、晚提交／取消回覆。
目前 A 有自己的碼、B 已登記，兩者都會在 `/ic claim` 前置拒絕；不要用它們重跑輸入框，也不要自行 reset／unlink。
合資格測試帳號的準備另行安排；本次未因補發通過而擅自再重設 B。
新候選真人外觀尚未通過。其後還有暫設 qualification profile／同網路 ALLOW 還原與其他核心驗收；
本次 Agent 正常重啟及 DB 保留證據不替代新畫面人眼驗收。
以下為歷史未部署安排，以本節為準。


## 2026-10-30 登記核對通過；四項介面修正候選尚未部署

B 已回報「登記完成」。[真人回報與唯讀核對](evidence/2026-10-30-folia-claim-human-acceptance.json) 確認 A→B 只有一筆
目前 generation 2 的 CLAIMED，舊 UNLINKED 保留，沒有獎勵動作。這是登記資料核對通過；使用者指出的四項 UI 缺陷另行修正，不能當成新候選已通過。

修正候選 `5702e91` 已完成：status 單一主要原因、17 個雙語文案鍵（錯誤＋重試指引單句紅／邀請碼標籤不綁定位數）、
到期即關閉／提示、提早 tick 重排與逾時提交原子判定。Java 8／11 完整檢查通過，338 項中 314 通過、24 MySQL 條件跳過，0 失敗／錯誤。
[完整變更與測試證據](evidence/2026-10-30-claim-ux-fixes.json)／[文案前後與討論項](evidence/2026-10-30-claim-ux-fixes.md)。

使用者已選定：**先以 Folia 現有 `1a83b73` 完成資格／離線補發，再停服更新新候選。**
Folia 本輪未部署、啟停、改設定／權限或 DB；不重設 B，不重做 claim。新輸入框真人複驗留待合適帳號／後續安排。
目前下一步就是下方原步驟 6–7：

1. A Command1 先退出伺服器；B Taiwan_PingLord 留在 Folia。
2. Folia 主控台輸入（不加斜線）：

   ```text
   lp user Taiwan_PingLord permission set invitecode.test.qualified true
   ```

3. 等約 60 秒的資格檢查，或 B 退出／再切入 Folia。B 應收到資格通知。
4. A 重新登入並 `/server invitecode-folia-26.1.2`，檢查離線獎勵，再退出／加入一次，不重複。
5. 回覆「資格／離線補發／再次登入不重複：通過、失敗或未測」。由 Agent 唯讀核對交付，再處理還原及停服部署。

若已提前達資格或出現非預期獎勵，先回報，不自行 retry／mark-delivered／reset。
之後仍需新候選外觀、正常重啟保留、臨時設定／權限還原及其他核心驗收。沒有 push／PR／merge。
以下為既有登記準備與歷史紀錄。


## 2026-10-30 兩帳號流程已就緒：先做到「同意登記」再回報

使用者已明確核准備份及 B-only reset，並先停止測服。Agent 核對正常退出／排空、程序與 port 釋放，
在管理鎖內備份 20 個檔案並逐一驗 hash，backup DB quick_check=ok；世界在原處保留，啟動前標頭未變。
僅啟動 Folia，使用插件管理指令執行一次 B reset，沒有直接修改 SQL。
[備份、重設及 runtime 證據](evidence/2026-10-30-folia-b-reset-ready.json)。

B Taiwan_PingLord：generation 1 → 2，first_seen_at 更新，新增 1 筆 ELIGIBILITY_RESET 稽核；
新登記期限至 **2026-10-31 01:47（台灣時間）**。A、原碼、舊 UNLINKED 邀請、獎勵均保留，
其他資料表核對無額外變化；設定、key 與權限逐位元組保留。沒有 unlink、輪替碼或代玩家 claim。
Folia 已到 Done／Listening，doctor 正常，資格權限 false，沒有啟動錯誤；另外兩台 InviteCode 測服仍停止。
同網路 ALLOW 保留以支援同機兩帳號；待本輪結束還原原 DENY，不在流程中途關閉。

### 現在先做 1–5，完成後回覆「登記完成」

候選 `1a83b73`，只測 Folia 26.1.2。A=Command1（邀請人）；B=Taiwan_PingLord（受邀人）。
兩人先連 `localhost:25565`，各輸入 `/server invitecode-folia-26.1.2`。
入口與 Play fallback 有監聽，Folia 路由已核對；lobby 沒開，本輪離線步驟直接退出伺服器，不依賴 lobby。

1. **A：**`/ic code` 取得原邀請碼。**B：**`/ic status` 確認可登記；不要執行 `/ic code`。
2. **B：**`/ic claim` 後在原生輸入框按取消。再看 status，仍可登記；沒有新關係。
3. **B：**重開 `/ic claim`，輸入 A 的碼，進入確認畫面後按「不同意」。仍可登記。
4. **B：**再進確認畫面，等超過 60 秒才按同意，或用 `/ic confirm` 確認已逾時。
   預期過期拒絕、沒有登記。這 60 秒從進入確認畫面開始，不是等待輸入的畫面。
5. **B：**最後重開 claim、輸入 A 碼，在期限內同意。預期只成功一次，status 顯示等待資格。
   再 `/ic claim` 應立即拒絕、不開輸入框。回覆「登記完成」，先由 Agent 唯讀核對唯一新關係、generation 2 與舊資料。

步驟 2–5 看畫面：Dialog 置中、prefix 獨立且無尾端空格；同意綠／不同意紅，期限最後無冒號。
原生框正常時不強制切聊天模式；舊版 cancel 在 Spigot 另驗。
若 B 一開始不可登記、提前達資格、出現非預期獎勵或新錯誤，停在該步回報，不重設、不重送。
期限到期也先回報，不自行繞過期限。

### Agent 核對第 5 步後，再做 6–7

6. **A：**退出伺服器，確保在 Folia 離線。**B：**留在 Folia。
   **Folia 主控台（不加斜線）：**

   ```text
   lp user Taiwan_PingLord permission set invitecode.test.qualified true
   ```

   這是後續真人測試時由管理員執行的資格變更，目前 Agent 尚未設定 true。
   等資格檢查約 60 秒，或 B 退出再加入 Folia；B 應收到資格通知。
7. **A：**重新登入並切回 Folia，應補發離線獎勵；再退出／加入，不應重複補發。
   A `/ic stats` 應有本輪的新達資格邀請。回報後由 Agent 核對獎勵交付，最後將 B 測試權限還原原本 false。

回覆 `Folia 1a83b73：取消／不同意／逾時／同意／資格／離線補發／再次登入不重複：通過、失敗或未測`，
若失敗附指令、實際結果與截圖，不貼 key 或網路資料。
第 2／3 階段準備完成、第 3／3 階段真人流程待驗收；本組之後仍需正常重啟保留、臨時設定／權限還原及其他核心驗收。
沒有 push／PR／merge。下方為歷史未就緒提案，目前已由本節備份與操作證據取代。


## 2026-10-30 兩帳號準備：唯讀核對完成，B 期限已過

候選仍為 `1a83b73`，Folia 26.1.2 stable build 8 的核心 hash 與前次核對一致，port 30101、
已登記 Velocity 路由。當次管理器顯示 Listening／Managed；未啟停、未部署。
[唯讀 preflight](evidence/2026-10-30-folia-two-account-preflight.json) 是本次準備的狀態來源。

B Taiwan_PingLord 的 generation 為 1，登記期限已於台灣時間 2026-10-19 14:38 到期。
目前沒有有效邀請碼、目前代次邀請或獎勵動作；舊 generation 0 的 UNLINKED 邀請保留。
線上與持久化的 `invitecode.test.qualified` 均為 false，適合先登記再手動達資格。
同網路測試仍為 ALLOW；default zh_TW／use-client-locale true，確認與聊天輸入期限各 60 秒。
A Command1 的有效碼保留。沒有新邀請或獎勵由 Agent 建立。

### 尚待核准的精確操作

先以一致性 SQLite 備份與設定／key／權限快照保存現況，重新核對 B 的狀態及在線人數後，
僅對這台 Folia 執行一次：

```text
ic admin eligibility reset Taiwan_PingLord command-feedback-human-20261030
```

預期只更新 B 的登記起點至執行當下、generation 1 → 2，以及新增 1 筆稽核；
重新開啟原設定的 24 小時登記視窗。A、碼、舊邀請、獎勵、世界、設定與權限不變。
不需 unlink，不直接編輯 SQL、不回灌舊 DB；結果不明時先核對，不重送。
這個資料變更尚未取得本次精確授權，尚未執行；現在不要開始 claim。

### 重設完成並核對後使用的簡單清單（目前未就緒）

只測 Folia，不重做已通過的 help／語系／reload。A、B 經 localhost:25565，輸入
`/server invitecode-folia-26.1.2`；A 是 Command1，B 是 Taiwan_PingLord。

1. A `/ic code` 查看原碼；B `/ic status` 應可登記。B 不執行 `/ic code`。
2. B `/ic claim` 後取消輸入，應不建立關係。
3. B 重開 claim，輸入 A 的碼，在確認畫面按不同意，應仍可登記。
4. B 再次進入確認畫面，等超過 60 秒再同意，應拒絕過期操作，沒有登記。
5. B 最後重做並在期限內同意，應只建立一筆新關係，等待資格；再 claim 應立即拒絕。
   回報「登記完成」後先由 Agent 唯讀核對新關係／generation／舊資料保留。
6. A 先退出伺服器（不依賴 lobby 開服），B 留在 Folia。管理員在約定步驟用後台
   `lp user Taiwan_PingLord permission set invitecode.test.qualified true` 達成測試資格。
   這是後續測試權限操作，尚未由 Agent 執行；記錄並在最後還原目前 false。
7. 等待資格檢查，或讓 B 重新進入 Folia。B 應達資格；A 回服收到離線獎勵，再退出／回服不重複。
   由 Agent 唯讀核對每筆交付狀態，不能只靠聊天畫面推定一次性正確。

畫面預期：Dialog 置中、prefix 獨立且無尾端空格；同意綠／不同意紅，期限末尾無冒號。
AUTO 使用原生介面時不要求本輪重測舊版聊天 cancel；該路徑隨 Spigot 版本另驗。
已登記、視窗到期、原生介面失敗或出現非預期獎勵時停在該步回報，不連續重試或 reset。
回覆 `Folia 1a83b73：取消／不同意／逾時／同意／資格／離線補發／再次登入不重複：通過、失敗或未測`。
通過後仍待正常重啟保留、臨時設定還原與其他核心；沒有 push／PR／merge。
以下為歷史驗收安排，以本輪 preflight 的未就緒狀態為準。


## 2026-10-30 最新確認：雙語與 reload 真人驗收通過

使用者補充確認：reload 後回覆即生效，各語言 prefix 正確；`use-client-locale` 的 true／false 都測過並正確套用。
其後刻意改壞 config，reload 明確拒絕並保留上一份有效設定，沒有回落到預設 true。
結合先前已還原設定的回報，記錄雙語／有效 reload／失敗保留與還原這組真人驗收通過。
[完整回報](evidence/2026-10-30-folia-reload-human-acceptance.json) 取代前輪因資訊不足保留的未測判定。
使用者的錯誤案例是 config 語系設定，不宣稱測過數字 prefix；不要求為重做文件範例再破壞設定。

下一步為第四階段兩帳號流程的準備：先由 Agent 唯讀核對 Folia 候選、設定、B 的登記期限／角色／既有邀請及獎勵。
如果 B 仍符合條件，再交付 A／B 的簡單步驟，依序測取消、不同意、逾時、最後同意，再測資格與離線只補發一次。
如需額外重設資格或解除關係，先列精確變更與保留資料，不擅自套用舊的單次授權。
之後安排正常重啟保留、臨時設定還原及其他核心驗收；Folia 本輪通過不推定其他核心通過。
本輪只記錄文件，沒有核對或改動 live server、設定、DB、世界；沒有 push／PR／merge。
以下為歷史判定，以本次補充回報為準；已通過項目不重做。


## 2026-10-30 歷史補充：當時 reload 案例資訊不足（已由上方回報補齊）

使用者回報四項通過，並補充實際僅改動 `config.yml` 的 `use-client-locale`，其後已還原；
Console 跟隨預設繁中正常。據此記錄繁英顯示、Console 預設語系、語系設定切換及還原通過。
[完整回報與證據界線](evidence/2026-10-30-folia-locale-human-acceptance.json)。
當時僅已知 use-client-locale 改動與還原，尚未收到錯誤設定測試細節，故暫保留 reload 案例；此判定已由最新回報取代。

下一步建議由 Agent 核對並備份後，補以下一組設定驗證，避免使用者手動修改錯誤設定：
1. 暫改繁中 `presentation.prefix` 為 `[IC TEST]`，執行 `ic reload`，核對新回覆立即使用新值。
2. 暫改同鍵為數字 `123`，reload 應拒絕，新回覆繼續使用上一步有效值。
3. 無論驗證結果如何都還原原檔，再 reload 確認原 prefix 正常；保留結果與最小證據。
此組尚未執行；準備前需核對目標狀態與候選，不假設伺服器目前已啟動。
不改 DB、key、邀請關係或 B 資格，不要求重做已通過的語系與指令面板。
之後仍待兩帳號流程、重啟保留／臨時設定還原及其他核心驗收。
本輪僅更新本機文件，沒有設定、測服或 remote 操作。以下為歷史安排。


## 2026-10-30 最新真人回報：Folia 指令面板四項通過

使用者確認前輪列出的四項皆已測試，基本沒有問題。對應產品候選 `1a83b73`，
記錄為首頁 help／停用導航、A／B 權限與 Tab、status／stats／邀請碼 hover 與複製、Console Tab 通過。
[真人回報](evidence/2026-10-30-folia-command-panel-human-acceptance.json)。既有 doctor、報告排版與框線驗收保留，不重做。
本輪只更新證據與計畫；沒有啟停、部署、修改測服設定或資料，未重新確認目前執行狀態。

下一個前置條件已滿足的項目為第四階段「雙語與 messages reload」：

1. A、B 將客戶端改 English (US)，執行 `/ic help`、`/ic status`、`/ic stats`；A 另看 `/ic help admin` 與 `/ic admin doctor`。
   確認英文完整、prefix 一致、無缺鍵／色碼外洩，再改回繁中重做。
2. Agent 先核對候選與設定、備份繁中 messages，再暫改 `presentation.prefix` 為字串 `[IC TEST]`。
   Console `ic reload` 後，玩家及 Console 的新回覆應使用新 prefix，且不重複。
3. Agent 將同一 prefix 暫改為數字 `123`，Console `ic reload` 應明確拒絕；新回覆仍用上一步有效的 `[IC TEST]`，不可半套更新。
4. Agent 立即恢復原始檔案並執行 `ic reload`；確認成功且回復原 prefix。設定備份／暫改／還原由 Agent 準備，尚未執行。

回覆方式：`Folia 1a83b73：英文／繁中／reload 生效／錯誤設定保留／還原：通過、失敗或未測`。
不要求刪除檔案，缺檔復位已有 Agent 證據；不修改 storage、DB、key 或重設 B。
本組完成後才安排兩帳號確認／資格／離線補發／重啟，開始前重核 B 的資格與現有關係。
其他核心保持未驗收，待 Folia 完整流程通過後再部署同一候選並安排驗收。沒有 push／PR／merge。
以下為歷史紀錄；舊的「待驗收」以本節與對應證據為準。


## 2026-10-29 最新真人回報：doctor 顯示通過，測服已由使用者停止

使用者確認候選 `1a83b73` 本次更改可算通過，記錄為 Folia 健康檢查顯示驗收通過。
[原始回報與範圍](evidence/2026-10-29-doctor-human-acceptance.json)。未逐項確認英文語系、正數獎勵顏色，
不推定其他核心或完整第四階段通過。使用者另回報已停止伺服器；本輪不啟動、不部署、不改資料。

下一輪先只安排 Folia 的剩餘指令面板驗收（下方「剩餘驗收」第 1–4 項）：
首頁 help 點擊／停用按鈕、A／B 權限與 Tab、status／stats／邀請碼 hover 與複製、Console Tab。
已通過的 doctor 顯示、Console／玩家報告排版、框線及第 2 頁返回不要求重做。
B 僅測一般權限與 status／stats，不執行 code 或 claim；不修改登記資格與邀請歷史。
之後才安排 messages reload／雙語與兩帳號完整流程；涉及設定、重啟或 B 資格準備時另依既有授權核對，
不可憑舊準備紀錄假設仍可登記。Folia 完整通過後才安排其他核心。
以下為歷史候選清單，不代表目前仍待 doctor 顯示驗收或測服仍在執行。


## 2026-10-29 健康檢查候選：Agent 驗證完成，待局部真人驗收

來源 `1a83b73`。僅更新並啟動 Folia 26.1.2；Paper／Spigot 維持停止。
[Agent 證據](evidence/2026-10-29-doctor-localization.json) 保留 hash、run、備份及檢查結果。
既有世界與資料保留。前版 Console／玩家報告排版驗收仍有效；本次複驗新翻譯與配色。

1. 主控台輸入 `ic admin doctor`：每行有時間戳、INFO、prefix；一般欄位與狀態使用繁中，專有名詞保留。
2. A 帳號連線 `localhost:25565`，輸入 `/server invitecode-folia-26.1.2`，再輸入 `/ic admin doctor`。
   不需要 B 帳號，不執行 claim／reset／unlink。玩家標題 prefix 一次，後續一行一欄。
3. 檢查「運作中」「接受新工作」「支援」為綠；「未啟用」「不可用」為紅；三項獎勵數量 0 為灰。
   選配整合不可用不等於故障。健康檢查不應露出 ACTIVE／ACCEPTING 或布林原碼。
4. 將客戶端語言切英文後重跑，應顯示英文欄位及語意狀態；改回繁中後重跑確認。若設定關閉客戶端語系，應跟隨預設語言。
5. 回覆「Folia 1a83b73：Console 翻譯／玩家翻譯／布林色／零值／雙語：通過、失敗或未測」，失敗附截圖。

目前固定測服的三項獎勵數量皆為 0；正數待執行黃、執行中青、結果待確認金已由測試 fixture 驗證，
不為外觀製造獎勵或故障資料。畫面尚未真人通過；其他核心與完整兩帳號流程仍另行安排。
Velocity 現行路由指向 30101，入口有監聽；fallback Play 20401 有監聽、lobby 10001 未監聽，
未變更共用服務或路由。設定及 port 不代替客戶端切服驗收。

Git commit／編譯時間目前未嵌入 JAR；已記錄容器 notes/build-provenance-draft.md，待本分支完成後另行評估。
完成本次局部驗收後，依第四階段清單補尚未通過的 help／權限 Tab、雙語 reload 與兩帳號流程；開始 claim 前重核 B 資格。
沒有 push。以下為歷史紀錄，不代表目前仍在討論階段。




## 2026-10-29 歷史回報：Console／玩家報告排版通過，當時 doctor 翻譯配色待討論

使用者確認目前交付的 `53893c4` 在 Folia：Console 通過、玩家報告通過。
[驗收與後續要求](evidence/2026-10-29-folia-console-player-acceptance.json) 及
[使用者截圖](evidence/2026-10-29-folia-doctor-localization-feedback.png) 已保存。
本次通過範圍為排版，不推定英文語系、其他核心、完整互動／獎勵／重啟或整個分支通過。
不重做已通過排版。新增 doctor 的翻譯／配色要求另列：true 綠、false 紅；其他顏色先討論。
本輪只記錄與提案，不修改插件、建置、部署或啟停，沒有 push／merge。

建議下一步是確認 doctor 的狀態翻譯與配色語意，再沿目前分支實作及局部複驗。
語系建議沿 config 的既有選擇規則（use-client-locale 啟用時玩家用客戶端語系，否則預設語系；Console 用預設），
不趁此更改語系選擇或資料庫。詳細提案見同一 HTML 的 help-update；尚非定稿規格。


## 最新待驗收：Console 逐行與報告排版（只測 Folia）

候選 `53893c475fdeaf631ca5b174493f33754660e0ad`，SHA-256 `6E7EB72D59896AF141FE17029FDAD5A0322EADA7F019929F5E78D11C20C8664E`。只更新 Folia 26.1.2；另兩台保持停止。
前版 `760c51a` 的框線與返回第 2 頁通過紀錄保留，但不推定新版 Console／報告外觀已通過。

1. 打開 Folia 原生後台，輸入 `ic`：只有一個 help 入口，沒有重複 help admin。
2. 輸入 `ic help`、`ic help admin 2`、`ic help admin reload`：索引標示後台用途。
   每行都有核心產生的時間戳／INFO；頁尾只列可輸入的有效導航命令，不再模仿按鈕或顯示停用項目。
3. 輸入 `ic admin doctor`：標題與狀態逐欄，每行都有時間戳／INFO 及一次 prefix；
   backend、schema、各獎勵狀態不再擠在同一行。沒有分號或色碼外洩。
4. 輸入 `ic admin inspect player Command1`：逐欄顯示，名稱與 UUID、布林狀態容易辨認。
   不需查詢其他私人玩家；Invitation／Reward Action 的模板已有回歸檢查，可待後續方便取得 ID 再看外觀。
5. A 經 localhost:25565 與 `/server invitecode-folia-26.1.2`，輸入 `/ic admin doctor`、
   `/ic admin inspect player Command1`：每份報告只有頂端標題加一次 prefix，其餘逐欄。
   玩家 help 固定三按鈕、停用無反應、首頁 help 直接開啟與第 2 頁返回仍正常。
6. 若方便，切 English (US) 重做 help／doctor，確認雙語與 prefix；最後回覆
   `Folia 53893c4：每行時間戳、後台 help 去重、文字導航、doctor、inspect、玩家 prefix、雙語：通過／失敗／未測`。
   失敗附指令與截圖，不貼 DB、key 或原始網路資料。

只測顯示，不執行 reset／unlink／retry／migration，也不用現在重跑雙帳號登記。未測項目保持未測。
測完仍待完整訊息／互動驗收與臨時設定還原，沒有 push／PR／merge 授權。
離線匿名化／跨 backend 工具只有輸出格式調整，測試用隔離資料已驗證，不在 Human DB 執行。


## 已通過：Folia 框線與第 2 頁返回（局部真人驗收）

2026-10-18 使用者明確回報候選 `760c51a` 在 Folia 26.1.2：框線顯示可接受，
reload、doctor、storage 返回管理員第 2 頁均正常。這四項已通過，不要求重做。
客戶端版本／資源包未回報，不推定其他字型或核心也通過。完整證據：
[真人回報紀錄](evidence/2026-10-18-folia-help-partial-human-acceptance.json)。
這是局部驗收，沒有推定首頁點擊、其他 UI、整個第四階段或合併已通過。

## 剩餘驗收：先 Folia，再安排其他核心

目前仍只使用 Folia；本輪只更新文件，不啟停、部署、改設定或改資料。
以下沒有明確真人回報的項目保持待確認，不把 Agent 測試當作人眼驗收。

| 順序 | 項目 | 簡單操作與預期 | 執行者 |
| --- | --- | --- | --- |
| 1 | 首頁點擊與停用按鈕 | `/ic` 點 help 直接開說明；其他操作只填入。點深灰導航無反應，黃色可用 | A |
| 2 | 權限與 Tab | A 看管理分類；B 不見管理入口／管理 Tab。`/ic status `、`/ic stats ` 與多餘參數的 Tab 不冒玩家名 | A、B |
| 3 | status／stats 與邀請碼 | `/ic status`、`/ic stats` 的 prefix 一次、無亂碼。A `/ic code`，六碼 hover 清楚、複製只有原碼；B 不執行 code | A、B |
| 4 | Console 外觀與 Tab | `ic help admin`、`ic help admin 2`，文字及 16 色可讀；`ic help admin ` Tab 有候選，完成路徑 Tab 無玩家名 | Console |
| 5 | 雙語與 messages reload | 切英文／繁中，help／status／stats 完整。備份後暫改 prefix、reload 生效；錯誤型別 reload 失敗保留舊快照，恢復後 reload | A、B、管理員 |
| 6 | Claim 與確認畫面 | 先核對 B 仍可登記；輸入取消、不同意、60 秒逾時、最後同意；Dialog／聊天 prefix、綠紅按鈕與文字位置正確 | A、B |
| 7 | 資格與離線補發 | A 回 Lobby，B 達資格；A 回服只補發一次，再進不重複。由 Agent 核對新關係與獎勵，不只看畫面 | A、B、Agent |
| 8 | 重啟與還原 | 正常關服再開，碼／status／stats 保留、不重複補發；Agent 還原本輪臨時設定與權限，不回灌舊 DB | 管理員、Agent |

建議下一步只做第 1–4 項，先完成 Folia 指令面板與基本訊息；不用立即做雙帳號 claim 全流程。
第 1、3、4 項主要可先用 A 與主控台；第 2 項需要 B 的一般權限視角。
兩帳號流程前必須重新唯讀核對 B 的登記視窗、generation、目前邀請與資格設定；
不能因舊文件曾準備完成就直接假設今天仍可開始，不自動重設或 unlink。
第 5–8 項沿用下方 12 步完整清單；有先前操作成果則先核對，不盲目重做已完成的登記／補發。

Folia 尚未完整通過前，不自動啟動其他兩台。之後 Spigot 1.8.8／Paper 26.3
須先部署同一驗收候選，再驗證舊版聊天／新版 Dialog、權限 Tab、雙語與 console；
本輪 Folia 通過項目不能直接算作它們通過。Paper 26.2 adapter 的 Agent 複驗及最新 Folia ALPHA 缺口另列。
玩家名稱導向管理草案仍延後，關服 JLine 問題仍暫緩，不列為本輪要求真人重現的測項。

回報方式：`Folia 760c51a：首頁／停用按鈕／權限 Tab／status-stats／複製／console：通過、失敗或未測`。
失敗附指令、實際結果與截圖。全部相關驗收完成後才整理還原與交付；push／PR／merge 尚未授權。

## 前輪操作與部署紀錄（保留歷史，以下待驗收敘述以最上方狀態為準）

## 最新局部複驗：Folia 框線長度與返回頁

候選 `760c51a8b3ae50e172cda8218f62aa5c8b4b9de7`，JAR SHA-256 `D2669D283F5ACD0FB7AB1A9B45C398371E88EE9B0B1795A29DCCB429E1A3A012`。只更新 Folia 26.1.2，其他兩台保持停止。
本輪沒有更改 messages、資料庫結構、帳號資格或邀請歷史；框線補長與返回頁由共用 renderer／help 樹決定。

1. 經 `localhost:25565`，輸入 `/server invitecode-folia-26.1.2`，使用管理員帳號。
2. 輸入 `/ic`、`/ic help admin`、`/ic help admin 2`，比較標題線與底線右端，應比前版接近。
   預設字型只有近似估算；請記錄客戶端版本、字型／資源包及聊天視窗大小，必要時附截圖。
3. 在管理員第 2 頁點 reload 或 doctor 詳細說明，再按返回，應回 2/2，不能回 1/2。
4. 第 2 頁點 storage，再進 migrate 詳細說明，按返回先回 storage，再返回管理索引 2/2。
   **只瀏覽 help，不執行 migrate／reload 等操作。** 管理索引本身返回 `/ic` 首頁。
5. 首頁點 `/ic help` 應仍直接開啟；固定三項導航、停用無反應與權限規則保留。
6. 回覆「Folia 760c51a：框線大致對齊／仍有差距；reload／doctor 返回 2/2、storage 返回 2/2：通過／失敗」。
   失敗附指令與截圖。先不要求詳細流程；通過後再決定何時進行原本其餘驗收。

新增命令必須沿用這套共用框線及權限過濾後的父頁定位；已有全分類不變式與 runtime 回歸測試。
下方為前輪局部測試及歷史三台流程，保留參考，不代表要求立即啟動其他測服。

## 最新安排：只測 Folia 26.1.2 的首頁 help 點擊

候選 `656f416df8a7042e42286acef2d5e4f0b100be66`，JAR SHA-256 `4D6E9B8BB5E36D7CD2FE6337899008232F7E2E1E871275A867FDE2D52772D1EA`。
本輪只更新 Folia 26.1.2；Spigot 1.8.8／Paper 26.3 保持停止及前一版產物，不自動啟動。
使用者已回饋多層 help 顯示大致正常，這是局部回饋，不是三台完整真人驗收通過。

經 localhost:25565 輸入 `/server invitecode-folia-26.1.2`，再輸入 `/ic`：
點首頁 `/ic help` 應直接開啟玩家說明，與 `/ic help admin` 導覽一致；其他四個操作仍只填入聊天列。
請回覆「Folia 首頁 help 點擊通過／失敗」，失敗附操作與截圖。先看外觀，不要求立即重跑詳細流程。

管理指令的玩家名稱／紀錄選取改善另存本機草案，未實作、未改資料庫、未建分支或 Issue。
待目前 help 分支驗收及合併後再確認規格、從當時 develop 建立新分支，不提前混入本輪。
以下內容為前次三台清單及歷史部署紀錄，保留作後續詳細驗收參考。

## 目前狀態：help 框線與固定導航已部署，待真人複驗

2026-10-18 原準備已依使用者授權完成三台部署、B 資格重設及 Folia 單筆舊關係解除；本輪只更新 help，不重做帳號操作。
三台當次 `Done`、InviteCode ACTIVE、help／doctor／玩家專用拒絕／reload、UTF-8 log、實際 JDK 與 port 均已核對。
世界原位保留；DB、密鑰與歷史未清空。原部署後因首頁資訊過量重建候選，三台已更新為下列新版；沒有 push。

- 程式來源：`fb303e23bb6acca4e6d97aa9686bc89475ff0166`。
- 已裝 JAR：`InviteCode-0.1.0-SNAPSHOT.jar`，19,914,359 bytes。
- SHA-256：`E8640904EA2963FB741C19A2037C9FD359DF85BAFF2B3F2F9A7EC1F591AE2EBA`。
- [本輪部署與當次啟動證據](evidence/2026-10-18-command-feedback-help-frame-deployment.json)；[原帳號準備與部署證據](evidence/2026-10-18-command-feedback-stage4-deployment.json)。

**現在直接從步驟 1 開始；不要再次 reset、unlink 或建立 B 的邀請碼。**
B 的本輪視窗最早在 **2026-10-19 14:38（臺灣時間）** 到期。若來不及，先回報，由 Agent 重新核對後安排。

## 本輪先驗收：框線、同列標題與固定導航

三台使用相同新候選，沒有再次 reset／unlink。每台經原本 `localhost:25565` 與同名 `/server` 路由進入。
A 使用管理員帳號 Command1，B 使用一般帳號 Taiwan_PingLord；B 不執行 `/ic code`。

1. A、B 輸入 `/ic`：prefix 與「常用指令」在同一條標題線，只出現一次；內容下方有結尾線。
   底部固定 `[上一頁] [返回] [下一頁]`，首頁三項皆深灰，點擊不反應、沒有操作 hover。
2. A 輸入 `/ic help admin`：第一頁上一頁深灰，返回／下一頁黃色。點下一頁到 2/2，
   下一頁改深灰，上一頁仍黃色可回 1/2。點停用項目不新增 help，也不填入聊天列。
3. A 輸入 `/ic help admin invitation transfer`：前後線清楚，三項導航完整，只有返回黃色可用；
   點操作用法仍只填入聊天列，**不要按 Enter 執行管理指令**。
4. B 輸入 `/ic help`，確認同樣框線與三項導航；`/ic help admin` 仍不得顯示管理用法。
   各自按既有 Tab 清單確認權限與空集合行為未回歸。
5. 原生主控台輸入 `ic`、`ic help admin`、`ic help admin 2`、`ic help admin invitation transfer`：
   prefix 同列、前後線與三項導航都顯示，可用項目帶導航命令。Console 仍使用可讀 16 色、不額外粗體；
   深灰在 console 可依可讀性降色，不要求與玩家畫面完全相同。
6. 逐台回覆「核心／客戶端版本；框線與 prefix、固定導航、停用無反應、分頁返回、console：通過／失敗」。
   失敗附指令、預期／實際及截圖；不需再次 reset。通過後續原先尚未完成的其他訊息／確認／雙語／重啟清單。

字型、視窗寬度可能造成換行；不是以空格固定像素對齊。真人未通過前不宣告外觀驗收完成。
本輪證據見 [部署紀錄](evidence/2026-10-18-command-feedback-help-frame-deployment.json)。

## 2026-10-18 分層 help 複驗：先完成這六項

本輪為真人回饋修正，沿用原分支／Worktree。只更新插件與雙語訊息，不再次 reset／unlink。
DB 各資料表在部署前後一致，金鑰、資格設定、權限與世界保留；三台當次啟動及 console 回覆已核對。
本輪不替使用者宣告真人通過。三台各做一次下列清單，之後繼續先前尚未完成的 12 步流程。

1. **連線。** A（Command1，管理員）與 B（Taiwan_PingLord，一般玩家）經 `localhost:25565`，
   各自 `/server invitecode-spigot-1.8.8`、`/server invitecode-paper-26.3` 或 `/server invitecode-folia-26.1.2`。
   每次只選一台；使用對應客戶端版本，記下版本。不要直接連後端。
2. **首頁。** A、B 各輸入 `/ic`。B 應只見有權限的 `code`、`claim`、`status`、`stats`、`help`；
   A 額外一行 `/ic help admin`。不直接列管理用法、confirm 或 cancel。B 只閱讀首頁，**不要執行 `/ic code`**。
3. **分層與點擊。** A 依序 `/ic help admin`、`/ic help admin 2`、`/ic help admin invitation`、
   `/ic help admin invitation transfer`。分類每頁最多五項，有頁碼；詳細頁才顯示完整參數與範例。
   點分類／下一頁／返回應直接開 help；點真正的管理用法應只填入聊天列，**不要按 Enter 執行**。
   B 查 `/ic help admin` 應顯示一般錯誤，不洩漏管理用法。confirm／cancel 只在互動情境提示；明確查其 help 仍有效。
4. **Tab。** A 輸入 `/ic help admin ` 後按 Tab，應見分類與 `1`、`2`；
   `/ic help admin invitation ` 應見三種操作。B 的 `/ic ` 不見 admin／reload，`/ic help admin ` 沒有候選。
   A 的 `/ic admin invitation force-qualify EXAMPLE_INVITATION_UUID ` 應提示設定內的資格 ID，
   **只按 Tab，不送出這個假 ID 指令**。`/ic status `、`/ic stats `、`/ic help admin 2 `、
   `/ic help admin invitation transfer ` 及任意超長路徑的 Tab 都應為空，不冒出玩家名稱。
5. **Console 與錯誤。** 在各台原生主控台輸入 `ic`、`ic help admin`、`ic help admin 2`、
   `ic help admin invitation transfer`、`ic help typo`、`ic help admin 999`。console 只見適用項目，
   分頁列出可輸入的導航命令，錯誤清楚；16 色可讀、不額外粗體、沒有原始色碼。
   也請在原生主控台鍵入 `ic help admin ` 後按 Tab（不按 Enter），確認有分類／頁碼，
   `ic status ` 的 Tab 不回退玩家名稱。原生終端的實際 Tab 行為仍待這項真人驗收。
6. **回報。** 逐台回覆「核心／客戶端版本；首頁、權限、分頁點擊、玩家 Tab、console Tab、顏色：通過／失敗」。
   失敗附操作、預期／實際與截圖。通過後再繼續原清單未完成的確認、雙語、獎勵與重啟驗收。

已知限制：最新 Folia ALPHA 未驗證；本輪沿用 stable 26.1.2。Spigot 的 LuckPerms JSON 寫入問題仍保留，
本清單不修改權限。關服 JLine 問題維持暫緩；本輪正常停止三台皆有成功退出紀錄。
相關：[本輪部署證據](evidence/2026-10-18-command-feedback-help-deployment.json)。

## 選用三台，逐台完成

下表的核心、build 與 JDK 已依實際 JAR、當次啟動 log 及 Java 程序核對。

| 測服／同名 `/server` 路由 | 核心 | JDK alias | Port | 本輪用途 |
| --- | --- | --- | --- | --- |
| `invitecode-spigot-1.8.8` | Spigot `git-Spigot-3c60ece-741a1bd` | local-8 | 10101 | 舊版聊天與填入邀請碼 |
| `invitecode-paper-26.3` | Paper 26.3 build 202 | local-25 | 20102 | 新版 Dialog、複製、完整流程 |
| `invitecode-folia-26.1.2` | Folia 26.1.2 build 8 | local-25 | 30101 | 實驗性平台互動與排程 |
| `invitecode-paper-26.2` | Paper 26.2 build 134 | local-25 | 20101 | 保留；受影響 adapter 的 Agent 複驗尚待執行 |

實例根目錄：`D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\`。
沒有新增實例、更新核心或搬移世界。三台現在為 Managed／Listening／ConPTY，可在既有 InstanceHarbor GUI
按該台的原生後台按鈕開啟主控台。共用 Velocity 25565 與 Lobby 10001 的既有 run 保留。
Paper 26.2 不在本輪部署、reset 或 unlink 範圍，保持停止。

## 已完成的部署與備份

每台先持有管理鎖、確認停止及核心 hash，再備份並逐檔核對：舊 JAR、整個 InviteCode 設定／DB／key、
LuckPerms JSON 權限資料、metadata 與 ops。備份在各實例 `.invitecode-backups/command-feedback-20261018-*/`；
精確路徑見部署 JSON。本備份不包含完整世界，世界原位保留，部署期間 `level.dat` 未變。

部署三台的唯一候選 JAR、完整 zh_TW／en_US 文案及測試資格設定；storage／rewards 與 key 不變。
同網路暫用 ALLOW；測完還原 Spigot／Folia 原 DENY，Paper 26.3 原本就是 ALLOW。

Spigot 原先繼承全域 Java 25，已備份本台管理設定並改為 mise local-8，正常停服／退出後重啟驗證；
其他測服與全域 Java 設定未改。Paper／Folia 實際為 local-25。

**Spigot 的資格測試方式不同：**既有 LuckPerms 5.4.145 在寫 JSON 權限時發生 Gson 相容性錯誤。
這次新建而失敗的 B 權限檔已在停服後移入備份保留證據，原本的權限檔沒有覆寫。
本輪改用 `invitecode.command.basic` 資格條件，B 最後同意後立即達資格；
A 必須在 B 最後同意前回 Lobby。**Spigot 不執行本清單的 LuckPerms 權限寫入指令。**
Paper／Folia 仍用 `invitecode.test.qualified`，已設為 false 並核對持久化與實際檢查結果。

## 兩帳號與既有資料

- **A：Command1**，邀請人，原邀請碼保持不變。A 不 reset、不輪替邀請碼。
- **B：Taiwan_PingLord**，受邀人，三台都已由 Agent 重設為新的登記資格，generation 0 → 1。
  B 沒有自己的有效邀請碼，請不要執行 `/ic code`。

Spigot／Paper 26.3 的舊 QUALIFIED 保留；Folia 指定的那一筆舊 CLAIMED 已成為 UNLINKED，
原列與歷史仍在。三台分別新增 1／1／2 筆稽核，A、原邀請碼、舊 Reward 均未變。
現在沒有替真人建立新的 Invitation，也沒有新的待補發獎勵。SQLite 均為 schema 9，quick_check=ok。

Paper 26.3 的 B 原本有直接 `* = true` 與測試資格 true；為驗收一般玩家 help，已暫時移除該 wildcard 並設資格 false。
原權限已備份，驗收後還原。三台 B 都不是 OP。

## 簡單完整步驟（現在可以開始）

三台已啟動；一次測一台，做完切回 Lobby 再換下一台。後端主控台指令**不加 `/`**；玩家聊天指令加 `/`。
測試帳號、語言及客戶端版本記在回報中；不要提供密碼或 key。

### 1. 啟動與切服

在既有 InstanceHarbor GUI 依名稱選擇目標，開「原生後台」。三台目前已啟動，不必重複按啟動。
在目前後端主控台依序輸入：

```text
ic admin doctor
ic help
ic code
```

預期 doctor 正常；Paper／Folia 若具 Dialog 能力應回報 PAPER，Spigot 1.8.8 為 NONE。
help 有管理用法；console 的 `ic code` 明確說只能由玩家執行。
使用 16 色、不額外粗體，文字可讀、沒有原始 `&`／HEX 格式碼外洩。
本輪原生後台視覺色彩仍待人眼確認。

A、B 都連到 `localhost:25565`，然後各自輸入**目前這一台**：

```text
/server invitecode-spigot-1.8.8
/server invitecode-paper-26.3
/server invitecode-folia-26.1.2
```

Spigot 用 1.8.8 客戶端驗收舊版行為；Paper 用 26.3，Folia 用原生 26.1.2 或已核對的代理相容客戶端，
記錄實際版本。若無 Dialog 或切服失敗，先回報，不直接連離線模式後端。

### 2. 先測現有資格拒絕

A 輸入 `/ic claim`，預期因 Inviter 身分直接拒絕，不開輸入框。
A、B 各輸入 `/ic status`、`/ic stats` 並記下數量；B 目前應顯示可登記。
若 status 同時顯示舊 QUALIFIED／UNLINKED，那是保留的歷史；本輪可登記與後續新關係另外核對。
B 的「已有登記」前置拒絕要在步驟 8 成功後再測。

### 3. help、Tab 與邀請碼（改以上方六項複驗為準）

A、B 先依上方六項複驗首頁、分層說明及 Tab。首頁與詳細頁的操作用法只填入聊天列；分類／分頁直接開啟唯讀 help。
A 以 `/ic help admin` 查看其權限允許的分類；B 須先確認為一般玩家（沒有 OP／管理權限），只看到允許的項目。
在 `/ic status `、`/ic stats ` 和超出語法長度的位置按 Tab，應沒有線上玩家名稱補全。

A 輸入 `/ic code`。將滑鼠移到**六碼本身**，檢查 hover。
Paper／Folia 點擊後貼到本機記事本，應只有六碼；1.8.8 應填入聊天列，再手動選取複製，勿送到公開聊天。
檢查 HEX 顏色、繁體中文與 prefix：一般一份回覆只出現一次。

### 4. 確認 B 可開始，勿重做管理操作

B 輸入 `/ic status`，確認可登記；不要輸入 `/ic code`。
Agent 已完成 reset、Folia 精確 unlink、generation 與 audit 核對；這一步不需任何主控台修改。
若 B 已不符合資格，停止並回報，不自行反覆 reset。

### 5. 取消輸入與不公開

Spigot：B 執行 `/ic claim`，在聊天輸入 `cancel`。預期私密取消，A 看不到原始輸入。
Paper／Folia：B 執行 `/ic claim`，先按 Dialog「取消」；預期沒有登記關係變更。
輸入 Dialog 的標題／內容應清楚，置中、不靠空格補位置。
聊天路徑的 `cancel` 已由 Spigot 驗收；需要強制測新版聊天時，管理員暫改 input-mode 為 CHAT 並 reload，測完還原 AUTO。

### 6. 不同意

B 再執行 `/ic claim`，輸入 A 六碼（舊版聊天輸入；新版 Dialog 按繼續）。
聊天確認依序七區塊：prefix、確認標題、邀請人、邀請碼、問題、期限、按鈕。
聊天靠左；Dialog 內容置中。prefix 獨立且無尾端空格；期限末尾**沒有冒號**。
「同意」綠色、「不同意」紅色，hover 清楚，Dialog 底色沿用原生。
B 按「不同意」，再查 `/ic status`，預期仍可登記。

### 7. 逾時

B 重複輸入 A 碼進入確認後，等待超過 60 秒，再按同意或執行 `/ic confirm`。
預期拒絕過期操作、沒有建立登記；重新 `/ic claim` 仍可開始。不要把等待輸入逾時當成確認逾時。

### 8. 同意

**Spigot：A 先 `/server lobby`，B 才按最後的同意。**
Paper／Folia：A 此時可以留在原服，步驟 9 才回 Lobby。
B 重開輸入，填 A 碼並在期限內按綠色「同意」。預期成功只登記一次。
B `/ic status` 可看到新登記，再 `/ic claim` 應在輸入前拒絕。
Paper／Folia 新登記應先等待資格；Spigot 依本輪設定立即達成資格並通知 B，A 離線獎勵等待補發。
由 Agent 在回報後唯讀核對只有一筆新的 Invitation UUID，A／B 方向正確，
其 `invitee_eligibility_generation` 等於 B 本輪 generation；舊列與舊 Reward 不被覆寫。
玩家 status／stats 是外觀驗收，不能代替這項資料判定。

### 9. 資格通知與離線補發

**Spigot：不執行 lp 指令。**步驟 8 後 B 應已收到一次資格訊息；A 尚在 Lobby，直接做下方 A 回服檢查。

**Paper／Folia：**A 先 `/server lobby`，B 留在原服。目前後端主控台輸入：

```text
lp user Taiwan_PingLord permission set invitecode.test.qualified true
```

等待約 60 秒，或讓 B 回 Lobby 再切回本服，預期 B 收到一次資格訊息，A 的 MESSAGE 等待補發。
若先前已設過 true，不要再重複測本步；保留實際結果。

**三台共用：**A 回到正在測的那台，應收到一次資格訊息；A 再回 Lobby、再進來，不應重複。
A `/ic stats` 的 qualified 數量相對步驟 2 增加一。文字應清楚區分資格與獎勵交付。
不要執行 reward retry／mark-delivered。

### 10. 雙語與 reload

A、B 將客戶端語言切為 English (US)，再執行 help／status／stats，預期英文完整且 prefix 一致。
再切回繁體中文；若 locale 快取沒有即時更新，重新切入後端並記錄此行為。

由管理員先備份 `messages/zh_TW.yml`，將 `presentation.prefix` 暫改為 `[IC TEST]`，主控台 `ic reload`。
預期後續新回覆使用新 prefix，不重複；恢復檔案後 reload。
再把同一鍵暫改為數字 `123`（不是字串），執行 `ic reload`，預期明確報錯且舊有效 prefix 繼續使用。
立即恢復並 reload 成功；不要破壞 storage／DB／key。
缺檔復位已有 Agent 證據，真人不必刪檔重測。

### 11. 重啟與還原

記下 A 碼、A stats、B status；兩人切回 lobby，管理員正常 stop 並等待保存完成、退出 receipt／port 釋放。
重新啟動同一台，兩人切回，預期邀請碼、登記與設定保留，已送獎勵不再補發。
若遇到已記錄的 JLine 關服例外，保留 log；先核對儲存與退出，不在此輪展開隔離測試或直接重建。

測完先依步驟 12 回報，由 Agent 統一還原 B 原權限、原 qualifications 與 matching-ip-action，並核對正常關服。
不要手動回灌舊 DB，避免覆蓋本輪歷史與稽核；不要刪 key 或世界。共用代理／Lobby 保持原狀。

### 12. 回覆方式

每台提供：核心 build、兩帳號客戶端版本，以及下列項目的通過／失敗／未測：
切服、help／Tab、六碼 hover／複製、前置拒絕、取消輸入、不同意、確認逾時、同意、
七區塊與對齊、雙語、console 色彩、reload 失敗保留、資格／離線一次補發、重啟保留、設定還原。
失敗附畫面及時間即可；不要貼 key、IP 雜湊或完整 DB。

## Agent 唯讀資料核對方式

先依管理器身分選定本服的 `plugins/InviteCode/invitecode.db`，以 Python sqlite3
`connect(Path(database).as_uri() + "?mode=ro", uri=True)` 開啟，再執行 `PRAGMA query_only=ON`。
只做下列參數化 SELECT，結束後關閉 connection；不執行 migration、INSERT、UPDATE 或 DELETE，
不讀 key／網路 hash。帳號參數綁定 `Taiwan_PingLord`；結果保留於本機受限操作紀錄，公開證據只記錄角色與判定。

```sql
SELECT player_id, eligibility_generation
FROM invitecode_players WHERE last_known_name = ?;

SELECT i.invitation_id, i.inviter_id, i.invitee_id,
       i.invitee_eligibility_generation, i.status, i.claimed_at
FROM invitecode_invitations i
JOIN invitecode_players p ON p.player_id = i.invitee_id
WHERE p.last_known_name = ?
ORDER BY i.claimed_at, i.invitation_id;
```

先確認查到唯一 B，再以同樣的第一個 SELECT、A 的名稱核對 inviter_id。
reset 前、後與同意後各讀一次；新列須對應當前 generation，歷史列仍在。
如果服務正在寫入而讀取失敗，保留錯誤並在安全時重讀，不更改 journal mode 或寫入 DB 來解鎖。

## 尚餘里程碑與來源

目前是「Agent 部署與啟動驗證完成，待使用者驗收」。下一步依上方清單集中完成三台真人外觀與互動；
若通過，Agent 核對新 Invitation generation、獎勵恰一次及還原臨時設定／權限、停止不再使用的後端，
再整理本任務的交付審查。沒有 push 授權，merge、release 與發布也另行處理。

Paper 26.2 的隔離 adapter 複驗尚待完成，不使用其 Human 世界作為可重建自動資料。
Folia 最新 26.3 ALPHA 缺口仍保留；26.1.2 是實驗性測試對象。

依據：[四階段計畫，第四階段](../plans/command-feedback-implementation-plan.md#第四階段集中一次的真人外觀驗收)、
[本輪部署證據](evidence/2026-10-18-command-feedback-stage4-deployment.json)、
[部署前唯讀證據](evidence/2026-10-18-command-feedback-stage4-preflight.json)。
