# 訊息顯示第三階段：真實核心矩陣

日期：2026-10-18。受測 runtime commit 為 `dfbc39c4a80cea91b22b88c5f89d16107e7d50a2`，JAR SHA-256 為 `E350DCA507D1FB953A2B785474708226BFBD682CBFE5D44E7E9BF0D0630C6C8B`，大小 19,890,059 bytes。後續本輪文件提交不改動 runtime／JAR。

使用者授權沿用 `codex/feat/command-feedback-messages` 與既有 Worktree 進行第三階段，明確暫不 push。本輪不部署 Human、不改管理器程式、不接人工 Velocity，也不處理已暫緩的 ConPTY 關服問題。

## 結果與限制

八個選定核心已通過兩輪啟停、Console help／doctor／reload、缺檔復位、自訂語系保留及空服重啟後 SQLite／key 一致性檢查。額外八份 audit 也通過實際 failed reload 後的 ACTIVE／同 generation 及退出核對。實際 provider、完整核心 hash 與資源狀態見[機器可讀摘要](2026-10-18-command-feedback-stage3.json)。這輪沒有修改產品程式，因此沒有重新建置不同的 JAR。

Spigot 1.8.8 完成真實網路連線的私密聊天、確認、資格及重啟後離線 MESSAGE 補發。Paper 1.21.8 完成原生輸入提交／取消、確認不同意／逾時／同意的實際回呼，以及重啟後的離線 MESSAGE 補發；兩者 help、code payload、重複 claim 前置拒絕亦通過。封包內容不等於客戶端畫面、置中、滑鼠 hover 或剪貼簿操作驗收。

**保留兩項缺口：**

- Folia 26.3 build 3 為 ALPHA，現行管理器只接受 STABLE。使用者已明確同意測最後找到的 STABLE Folia 26.1.2 build 8，保留最新 Folia 未驗證；該舊版本目前已停止上游支援。此結果不能宣稱 Folia 26.3 相容。
- `mct 0.20.1` 的 `client list` 為空；`client search --loader fabric --version 26.3` 及 `1.21.8` 無變體，26.2 可查到 Fabric 變體但本機未建立客戶端。本輪不安裝新依賴／客戶端，也沒有截圖、真實 GUI 或真人操作。Paper／Spigot 26.3 的 Dialog 開啟、點擊與外觀仍須後續驗收。

## 核心、JDK 與 port

| Matrix profile | 本輪鎖定核心 | JDK（mise alias） | Port |
| --- | --- | --- | --- |
| spigot-1.8.8 | Spigot 1.8.8，git-Spigot-3c60ece-741a1bd | local-8：1.8.0_501 | 51888 |
| spigot-1.12.2 | Spigot 1.12.2，git-Spigot-79a30d7-f4830a1 | local-8：1.8.0_501 | 51222 |
| spigot-1.16.5 | Spigot 1.16.5，3096a-Spigot-9fb885e-af1a232 | local-8：1.8.0_501 | 51650 |
| spigot-latest | Spigot 26.3，4663-Spigot-bed90fc-c4db4c3 | local-25：25.0.4.1 | 52620 |
| paper-1.20.6 | Paper 1.20.6 build 151 | local-21：21.0.12.1 | 62060 |
| paper-1.21.8 | Paper 1.21.8 build 60 | local-21：21.0.12.1 | 62180 |
| paper-latest | Paper 26.3 build 203 | local-25：25.0.4.1 | 62620 |
| folia-latest | Folia 26.1.2 build 8；經同意的 STABLE 替代範圍 | local-25：25.0.4.1 | 63620 |

Spigot 固定版本從 `D:\Code\Java\Plugin\BuildTools\Server\` 重用，沒有重新編譯或改寫 BuildTools。26.3 使用 `spigot-26.3-build4663.jar`；核對其 bundler 內嵌 JAR 的 `Implementation-Version`、版本與 refs，再與[官方 26.3 metadata](https://hub.spigotmc.org/versions/26.3.json)比對。`latest.json` 當時仍指向較舊的 4648，沒有將它直接當成最新 26.3 身分。

Paper／Folia 依[官方 Paper API](https://fill.papermc.io/v3/projects/paper)及[Folia API](https://fill.papermc.io/v3/projects/folia)核對版本與 build channel。三個既有 launcher 的 SHA-256 與官方一致；Paper 26.3 build 203 尚未在既有 cache，透過管理器下載到本輪隔離 cache 並核對官方 SHA-256。完整 hash 與 metadata 保存於同名 JSON 摘要。

Java 先透過 mise 核對實際版本及 alias 的 junction 目標，再把 `C:\Program Files\Java\` 下的實體 `java.exe` 登記到本輪專用 runtime registry；不把 junction 路徑交給管理器，也不改全域 mise／Java 設定。管理器依既有契約直接呼叫核對過的實體 JDK。

## 資料與生命週期邊界

使用 InstanceHarbor 現行 C# `Prepare`／`Start`／`Command`／`Stop`／`Status`，不使用舊 runner 的直接 Java 啟停。CLI 為既有 portable Basic：

```text
D:\Code\Java\Plugin\InstanceHarbor\worktrees\refactor-csharp-management-baseline\artifacts\portable-20261005-090638-9e19b49e\basic\InstanceHarbor.exe
SHA-256 D79B8F380C35982B2C457731B3DCDE084B30CA2578BB6C624FD36CE7642FBAAD
```

本輪專屬資料根為功能 Worktree `.run/command-feedback-stage3/data`，政策／profile 根為相鄰 `config/`。掃描僅允許 `Agent/InviteCode`；每台實例位於：

```text
<task-data-root>/instances/Agent/InviteCode/messages-dfbc39c-<matrix-profile>/
```

建立前確認八個 matrix port 都沒有 listener，且新目錄不存在；逐台檢查 schema 2／Agent metadata、JAR hash、loopback／offline mode、port 及 reparse point 邊界。不覆寫既有實例。既有設定曾重用 matrix port，故只將它們視為本輪未監聽的 Agent 測試 port，不宣稱取得 Human 槽位或路由。

現行管理器沒有已發布的共用 libraries adapter，因此採明確、有上限的隔離模式：重用既有 launcher／固定 LuckPerms 來源並核對副本 hash，各台 libraries、world、remap cache、plugins、SQLite、設定、log 與執行狀態獨立。新 Agent 依既有政策安裝 LuckPerms；不改 Human 或共用 manager settings。啟動模式為 Detached，不做 ConPTY 隔離測試。

開始時 C 約 56 GiB、D 約 51 GiB 可用；本輪峰值預算 8 GiB，要求至少 10 GiB 餘量，依序測試，必要的兩個不同 port 任務可並行。可重建 runtime 在退出與證據保存後回收；候選 JAR、Worktree、腳本、小型摘要與必要 log 保留，實際回收結果見 JSON。

最終核對共有 **34 個 accepted launch ticket 與 34 份相符退出 receipt**（包含首輪測試錯誤及補驗），均 child exit 0、outputDrained；所有本輪 Manager／Host／Java 程序已退出，八個 port 均無 listener。原始 log、receipt、metadata、Dialog JSON、腳本與本輪 config 保留於 `.run/command-feedback-stage3/`。

**資源回收尚未完成：**自動核准審查拒絕刪除本輪 `data/`，只提供 `blocked by policy`，沒有更具體理由。沒有改用其他方式刪除；目前保留 1,822,741,677 bytes（約 1.70 GiB）的可重建資料，沒有將其回報為已回收。程序停止／port 釋放已完成，磁碟清理與真人驗收仍分別列為未完成。

## 可重現檢查流程

每個 profile 使用同一個 JAR：

1. `Prepare` 記錄明確 profile／build、插件 hash、JDK、Agent 路徑及 port，manager `List` 核對八台可見。
2. 第一次啟動等待插件啟用及核心 Done，再查 doctor ACTIVE、外部整合關閉、預期 Dialog provider。Console help 有完整管理用法、只一份 prefix，沒有玩家專用用法或外洩格式碼。
3. 移開本輪自己的 zh_TW，reload 後與原內建檔逐位元組比對。正常停止，核對退出 receipt 與程序／port，保存空服 SQLite／key hash。
4. 把本輪 en_US prefix 改成 `[Matrix]` 並切英文，移開本輪 rewards.yml。重新啟動檢查缺檔復位、英文明確回饋與自訂語系不被覆寫。
5. 在本輪設定寫入無效 timeout；reload 拒絕且不覆寫壞檔。額外稽核實際查 doctor，確認仍為 ACTIVE、相同 configuration generation 及先前 `[Matrix]` 訊息；恢復原始位元組後 reload 成功。
6. 正常停止後，退出 receipt 與當次 run、host、Java PID／啟動時間綁定，確認 exit 0、outputDrained、程序退出及 port 釋放。空服兩輪 DB／key hash 一致，key 保持 32 bytes；不輸出 key 本文。

不把 CLI 的 accepted、exit code 或 listening 單獨當作通過；結果須由逐 profile 斷言、log、receipt 與最終機器可讀摘要共同核對。

## 玩家與 Dialog 傳輸測試

重用已存在的 `mineflayer 4.39.0`／`minecraft-protocol 1.68.0`／`prismarine-nbt`，不修改或安裝它們。測試暫時設定相同網路 `ALLOW`、三秒確認期限及登入一天達資格，限本輪 Agent 資料；測後復原設定。

- 邀請人 `IcMsgA` 查看 code，檢查 raw code click payload／hover；help 的七項玩家用法皆為 suggest，不立即執行。邀請人離線。
- 受邀人 `IcMsgB` 測 cancel／不同意／逾時／同意，再驗證 status、資格通知一次及已登記者再次 claim 先拒絕、不開 UI；旁觀合成帳號檢查聊天室未公開邀請碼。
- 重啟後邀請人登入，只收到一次資格 MESSAGE，stats 為一筆 qualified。Console 核對 pending 由 1 變 0。
- Paper 1.21.8 保存原生輸入／確認的實際 S2C Dialog JSON，核對 title、五個正文區塊、綠紅標籤及 raw code 顏色；回傳 custom action 的結果另見最終摘要。

此為真實核心上的網路／回呼證據，並非渲染客戶端；沒有把伺服器指令、合成 callback、封包或 HTML 當成點擊外觀驗收。

成功條件同時由網路回覆與 Console pending ledger 核對；第一階段玩家流程結束後 pending=1，正常停止再啟動，邀請人登入後 pending=0。被拒絕的首輪封包不計入成功結果；最後兩個完整流程均以正常 stop、相符 receipt、host／Java 退出及 port 釋放收尾。

## 首輪失敗與測試修正

- Paper／Folia 的 ANSI 會穿插 prefix 各色段，首輪原文比對失敗。保留原始 log 與 first-attempt JSON，只在文字斷言前正規化 ANSI；複驗成功。這不是修改產品以迎合測試。
- 1.21.8 首次 native cancel 被核心 decoder 拒絕。實際核心 `ServerboundCustomClickActionPacket` bytecode 顯示 optional tag 經 `lengthPrefixed(65536)` 包裝；既有測試套件 schema 缺少此長度且以 boolean option 描述。只在本輪封包 adapter 按受測 codec 編碼，保留依賴不變。首輪拒絕發生在插件事件之前，不能計為插件 callback 通過。
- 第二次確認字句比對以 JSON 原文搜尋，未考慮 `{seconds}` 已拆成有樣式的組件，改以組件的 text／extra 組合後核對五段正文；保留接收 JSON。這是測試結構修正，不改文字契約。
- 獨立審查補強 runner 的失敗退出碼、receipt／PID／port 綁定、設定復原及 failed reload 後 doctor。先前只看 reload 失敗回覆的證據不單獨作為保留快照的結論。

## 下一步

依[實作計畫第四階段](../../plans/command-feedback-implementation-plan.md#第四階段集中一次的真人外觀驗收)，Agent 可執行範圍完成後，建議交付綁定此 JAR 的集中真人清單，先核對既有固定 Spigot 1.8.8、Paper 26.3、Folia 26.1.2 的實際版本／路由，再經明確部署安排更新目標插件。前幾輪真人結果保持原產物綁定；新 prefix、色彩、置中、hover、剪貼簿與按鈕外觀須重新驗收。本輪沒有開始部署、真人操作或 remote write。
