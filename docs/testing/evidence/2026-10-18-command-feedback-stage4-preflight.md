# 第四階段固定測服唯讀核對

日期：2026-10-18（Asia/Taipei）。程式仍為第二階段 `dfbc39c` 的 E350 JAR；
準備工作基於文件 checkpoint `85fb938`，沿用原功能 Worktree，沒有重新建置。

## 結果與界線

四台 Human 皆被現行 C# 管理器 List 發現；逐台 Status 為 Stopped、沒有 host／run，
10101／20101／20102／30101 沒有 listener。Velocity 與 Lobby 的既有 managed run 正常監聽，
未因本次工作停止或重啟。沒有部署、啟動、重設帳號、改設定或 remote write。

實際核心 SHA 與建立 metadata：Spigot／Folia 相符；Paper 26.2 的實際 JAR 是 build 134，
Paper 26.3 是 build 202。兩者以 `.testlab-core-backups/*/update.json` 中的 newHash
逐一對上實際 `server.jar`，不是僅按檔名猜版本。兩台舊 `versions/` 的 materialized JAR
仍分別為 build 133／184，不應當成更新後當次啟動證據；本輪沒有啟動。

四台都有唯一 loopback 路由、offline backend、Spigot Bungee forwarding；Paper／Folia
另核對 bungee-cord online-mode=true、Velocity modern forwarding=false。
代理 online-mode=true、legacy forwarding，玩家入口 25565。設定相符不代表本候選已真人切服。
實際核心、JAR、port、語系差異與資料狀態詳見[JSON](2026-10-18-command-feedback-stage4-preflight.json)。

## 現行管理器核對

最初使用第三階段的舊 CLI，管理器拒絕 executable identity；改用現行 Manager 同包 CLI 後，
仍因 config／host context 不符被拒絕。沒有停止衝突 Manager，也沒有修改設定繞過檢查。
唯讀檢視當前 Manager 命令列後，沿用其 config-root 與 host，List／Status 成功。

- CLI：`InstanceHarbor/worktrees/feat-core-update-workflow/artifacts/portable-core-group-query-20261014/gui/InstanceHarbor.exe`
- Data root：`D:\Code\Java\Plugin\InstanceHarbor`
- Config root：`InstanceHarbor/worktrees/refactor-csharp-management-baseline/artifacts/csharp-feedback-acceptance/config`
- Host：`InstanceHarbor/worktrees/feat-java-runtime-selector/artifacts/portable-20261007-133506-db6c6a36/gui/host/Host.exe`

這些是本輪查詢身分，不是要求使用者固定採用過時包；後續控制前仍須重新核對當前 Manager。
Java alias 的實體執行檔存在，但沒有本輪 JVM 啟動版本證據。

## 本機準備區

Worktree `.run/command-feedback-stage4/` 只包含讀取／準備腳本、manifest、候選 JAR、
從候選 JAR 取出的雙語 messages 與逐台暫時設定提案。JAR hash 與第三階段完全一致；
既有 messages 均與新文案不同，因此部署時需要明確備份與逐檔更新。
準備區不是 Human 的備份，沒有複製 DB、key 或世界。

四台 SQLite 以 URI `mode=ro`、query_only 檢查：schema 9、quick_check=ok。
只保存 aggregate／A-B 角色狀態，JSON 不包含原始 UUID、Invite Code、IP、網路 hash 或 key。
三台主測服的 B 都已登記；Folia 有舊 CLAIMED，Spigot／Paper 26.3 有舊 QUALIFIED。
完整重測需要先確認 B 資格重設；Folia 還需確認對精確舊 CLAIMED 的 unlink，避免歷史列同時達資格。
這些是待確認資料操作，本輪沒有執行。

## 獨立審查與修正

獨立唯讀 reviewer 檢查 core／SQL／workflow，指出 reset 只增加 eligibility_generation，
不移除歷史 Invitation；資格掃描仍重評所有 CLAIMED。清單已改為先保存權限並設 false、
B 暫留 Lobby、精確處理 Folia 舊 CLAIMED 後再 reset，核對 generation 與新 Invitation。
不能只用玩家 status／stats 代替資料證據，Paper 26.2 明確排除於本輪 Human 資料操作。

本輪只改測試文件與本機閱讀入口，不修改這些既有領域行為。未使用 Java／Gradle，
無需重跑已完成的程式測試；本輪檢查 JSON、連結、HTML IDs／JS 語法、候選 hash、部署來源與 diff。
靜態核對 29 個唯一 HTML ID、23 個本機／章節連結、12 步驟；另有 2 個既有外部來源連結，未重查網路。
Node 檢查內嵌 JS 語法，
以最小 DOM stub 驗證初始／下一項／最後一項／全部／路由切換，不把它當成瀏覽器渲染。
再次雜湊確認四台已裝 InviteCode JAR 與八份語系檔仍與 preflight 相同。
瀏覽器實際渲染、Minecraft 畫面、剪貼簿、滑鼠與新核心當次啟動仍未驗證。

## 下一步

依[第四階段完整清單](../command-feedback-human-acceptance.md)，先確認三台的備份部署與
精確帳號操作，再逐台啟動核對並集中真人驗收。Paper 26.2 adapter Agent 複驗尚待執行；
最新 Folia ALPHA 缺口保留。沒有 push、PR、merge 或 release。
