# Paper 26.3 真人驗收

日期：2026-10-15（Asia/Taipei）。結論：**InviteCode 玩家功能驗收通過；關服終端機例外另列待查，不視為已修復。**

## 版本與範圍

- 使用者明確回覆：「Paper 26.3 全部通過」，對應本對話先前提供的兩帳號、主控台與重啟驗收清單。
- Human 實例：`D:\Code\Java\Plugin\InstanceHarbor\instances\Human\InviteCode\invitecode-paper-26.3`；後端 `127.0.0.1:20102`，經 `localhost:25565` 與 `/server invitecode-paper-26.3` 進入。
- Paper 26.3 build 184，SHA-256：`E42FFC9929C6AEF74C78CD80B42494E0271C08561E238C17BD890662AFE8D28D`。
- InviteCode `1.0.0`，SHA-256：`F2055052DC9E8C0913AFE85C6B19E46850FC2F2FC2C7B5467DE60CA567203BCE`。本輪重新核對磁碟產物，相符；產品內容來源為 `11dcdc1319ccd075b69f9b1819fd1f484b8fee9c`，之後只補充文件。
- 本輪不建立、啟動、停止或重建測服，不修改世界、資料庫、密鑰、設定與 messages，不 push、合併或發布。

## 使用者確認通過的項目

| 項目 | 結果與判定來源 |
| --- | --- |
| 26.3 登入、切入測服與返回 Lobby | 通過，使用者對完整清單的確認 |
| A 取得邀請碼、色彩、懸浮提示及複製 | 通過，同上 |
| B 原生 Dialog 輸入與邀請人摘要 | 通過，同上 |
| 不同意不建立關係，超過 60 秒不能同意 | 通過，同上 |
| 同意建立關係，再次 claim 提前拒絕 | 通過，同上 |
| 首次重啟後邀請關係與未達資格狀態保留 | 通過，同上 |
| B 由 false 改成 true 的測試權限觸發資格 | 通過，同上 |
| A 離線的待發獎勵跨重啟保留，登入後補發 | 通過，同上 |
| 再次重啟後統計保留、訊息獎勵不重複補發 | 通過，同上 |

相同 IP 規則為本測服專用的 `ALLOW`；本次不再次驗收 `DENY`／`WARN`。資格用 `invitecode.test.qualified` 控制，預設獎勵為訊息。本次不是新的 Vault 入帳、MySQL、JSON、Spigot 26.3 或其他版本真人證據，也不自動接受沿用 D6AF 的歷史三核心驗收。

## 關服終端機例外：尚未確認根因

使用者回報：某次開啟 ConPTY，保持開啟並關服時出現 `IllegalStateException: Terminal has been closed`。堆疊位於 JLine／TerminalConsoleAppender／Paper 的 `Server console handler`，沒有 InviteCode frame；這只定位到終端機清理階段，不能證明是哪個元件先關閉終端機。

Agent 唯讀核對本實例的壓縮 log 與退出 receipt：

| 觀察 | 結果 |
| --- | --- |
| `logs/2026-10-15-4.log.gz`，20:21:15 | 先有 InviteCode 停用與世界 I/O 收尾，再出現相同例外 |
| `logs/2026-10-15-5.log.gz`，20:24:07 | 再次出現同型例外，同樣位於關服尾段 |
| 對應 run `154d1c35f28a4067a8bd0e69b9a60f8d`／`e7650ab4e05b41958351ffeb890201e4` | launch ticket 均為 `ConPty`；Java exit code 0、outputDrained true |
| 後續 run `8b91cb02fa3741f58b5b847e5326373a` | launch ticket 也是 `ConPty`，Java exit code 0、outputDrained true；沒有在這筆結果中找到同型例外，仍不構成受控對照 |

部分 receipt 為 `stopRequested=false`、`manualStopDetectionReliable=false` 或 `manualStopUncertain=true`，所以不把退出碼 0 解讀成已核對每次 stop 輸入來源。功能驗收仍依使用者回覆記錄；終端機例外作為獨立未解問題保留，不以「不影響功能」宣告忽略。

最小後續診斷應在全新的 Agent 實例固定核心／插件／JDK，比較管道模式、ConPTY 未開觀看端、觀看端開啟後正常 stop、先關觀看端再正常 stop。各次保存 Java／Host 結束先後、raw terminal 輸出與 receipt；不能拿 Human 世界反覆重現或直接換 JLine 套件。本輪僅唯讀調查，沒有執行這個重現計畫或修復終端機。

本機最小證據：`D:\Code\Java\Plugin\InviteCodeKt\artifacts\human-paper263-20261015\acceptance-20261015.json` 與 `terminal-shutdown-observations-20261015.json`。保留判定需要的例外與結束資訊，不複製玩家 IP／帳號至 Git。

## 下一步與仍待決定事項

2026-10-15 後續使用者決定：關服例外先保留紀錄，暫不修復，也不執行隔離測試。上述對照計畫僅作為日後可能的診斷方法，不是目前待執行工作；問題仍未解決。

後續指令回饋、messages 與 prefix 已在 PR #34 整合。PR #35 補上 Git 提交與 UTC 打包時間。新候選的驗證範圍見 [1.0.0 發行候選](../../releases/1.0.0.md)。

Roadmap #1「發行門檻」仍適用：Spigot 26.3 完整玩家流程、舊三核心證據沿用判定、產品授權與第三方 notice 仍待補齊。Paper 通過不代表整個 1.0.0 可發布，也不構成 InstanceHarbor 修正分支的合併授權。
