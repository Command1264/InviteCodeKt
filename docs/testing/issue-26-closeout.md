# Issue #26 結案證據與交付狀態

核對日期：2026-10-14。文件基線：`develop` 的 `7b8c76611a1e86f9a96389877b63fdca81fdf155`。

跨 backend（資料儲存方式）搬移實作已合併，既有隔離自動證據支持 Issue #26 的約定範圍結案。Issue 目前仍開啟；本文件與下方留言是本機收尾候選，尚未發布至 GitHub。三核心真人驗收已完成，不再以等待 Issue #12 為由暫緩本項。

## 合併與 CI

| 項目 | 已核對結果 |
| --- | --- |
| 實作 PR | [PR #29](https://github.com/Command1264/InviteCodeKt/pull/29)，MERGED，目標 `develop` |
| 受測來源 | `418c8298e8deb6c7fdef66d6e0ff67f78d807a89` |
| 合併 commit | `fb215c09ee4bbce96050ef4aebf97a1905ed984a`，已包含在本文件基線 |
| CI | [run 37014114197](https://github.com/Command1264/InviteCodeKt/actions/runs/37014114197)：Linux Java 8／11、Windows Java 8／11、MySQL integration 全部 SUCCESS |
| 相關 Issue | #12、#25 已 CLOSED；#26 OPEN；正式 1.0 尚未發布 |

## 五項結案條件

以下依 [Issue #26](https://github.com/Command1264/InviteCodeKt/issues/26)逐項判定。測試細節、suite 名稱與限制以 [2026-10-02 複驗紀錄](./evidence/2026-10-02-issue-26-transfer-retest.md)為準。

| 條件 | 證據與判定 |
| --- | --- |
| 預檢拒絕不適用資料與目的地 | 版本／格式、密鑰、非空目的地、SQLite CHECK／外鍵與 MySQL 孤立紀錄檢查；可見 MySQL 併發連線拒絕。通過。停寫仍須操作者保證，不能偵測不可見帳號或之後的新寫入者。 |
| 唯讀來源與完整比對後發布 | 業務表白名單與全表比對，保留邀請、資格、獎勵／action、轉換、稽核、網路 hash 與舊碼保留 hash；schema／ledger 由目的地建立。通過。 |
| 失敗保留來源，目的地不可誤用 | SQLite／JSON staging 失敗不發布；MySQL trigger 注入 SQLState `45000` 後回滾，guard（未完成搬移的保護標記）持續阻擋啟動，來源／備份不變，全新 database 重試與重開成功。通過已測故障邊界；未涵蓋程序被強制終止或斷電。 |
| 隔離測試六個方向及恢復狀態 | SQLite↔JSON、SQLite↔MySQL、JSON↔MySQL，保留 PENDING／UNCERTAIN、冪等鍵與狀態轉換，涵蓋重開、可見併發及故障注入。通過；未使用正式玩家資料。 |
| 繁體中文維運、ADR、Java 8 單一 JAR | [維運流程](../operations/cross-backend-transfer.md)、[ADR 0006](../adr/0006-offline-cross-backend-transfer.md)與 Java 8 `check shadowJar` 成功紀錄。通過。 |

## 現存證據位置

封存根目錄：`D:\Code\Java\Plugin\InviteCodeKt\artifacts\merged-issue26-418c829\worktree\invitecode-plugin\build\`。

| 相對封存根目錄 | 用途 |
| --- | --- |
| `issue26-evidence/InviteCode-418c829.jar` | 固定受測產物 |
| `issue26-evidence/manifest.json` | 原 HEAD、工具鏈與結果索引；原 Worktree 路徑僅作歷史來源 |
| `issue26-evidence/xml/` | 55 組、244 項測試；零失敗、錯誤、跳過 |
| `issue26-mysql/full-check.log`、`issue26-mysql/final-check.log` | 原建置與隔離 MySQL 測試紀錄 |

JAR SHA-256：`D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C`。本次重新計算雜湊、加總 XML 與核對成功 log，未重新建置或啟動服務。原始 bytes 不改寫；本機封存不能當成其他電腦可直接取得的 GitHub 附件。

## 真人驗收與保留限制

[Issue #12 結論](./issue-12-release-gates.md)及其 [GitHub 驗收留言](https://github.com/Command1264/InviteCodeKt/issues/12#issuecomment-6149671122)記錄使用者確認 `1405745` 在 Spigot 1.8.8、Paper 26.2、Folia 26.1.2 通過；#12 已結案。[Issue #25 留言](https://github.com/Command1264/InviteCodeKt/issues/25#issuecomment-6151216884)記錄匿名化複驗與 PR #32 合併，#25 亦已結案。

上述真人驗收不證明跨儲存方式的實際停機、切換、dump 還原或程序崩潰。本項自動測試證明 transaction／staging 失敗及重開恢復，未測 kill CLI、作業系統斷電、mysqld 崩潰；MySQL dump fixture 僅驗證非空 gate，實際可還原性仍由管理員確認。這些限制須保留在結案證據，不以「完整崩潰測試通過」概括。

## 待發布 Issue 留言草稿

以下須在本收尾文件合併、取得 Issue 留言與關閉授權後發布，發布時補上文件 PR／合併 commit 並重新核對狀態。

> 跨 SQLite／JSON／MySQL 搬移實作 `418c8298e8deb6c7fdef66d6e0ff67f78d807a89` 已由 PR #29 合併至 `develop`（`fb215c09ee4bbce96050ef4aebf97a1905ed984a`）。CI run 37014114197 的 Linux／Windows Java 8、11 與 MySQL integration 五項全部通過。
>
> `docs/testing/issue-26-closeout.md` 逐項對應五項結案條件；`docs/testing/evidence/2026-10-02-issue-26-transfer-retest.md` 保存 suite 與限制。封存 JAR SHA-256 為 `D6AF246F62256E2BEF1FB441DC282B4ADE6001269776D8966C7652F5E264ED6C`，XML 合計 55 組、244 項，零失敗／錯誤／跳過。原始本機證據位於 `artifacts/merged-issue26-418c829/worktree/invitecode-plugin/build/`，2026-10-14 重新核對吻合，未重跑服務。
>
> 六種方向、完整資料比對、PENDING／UNCERTAIN、重開、可見併發拒絕及故障復原均有隔離自動證據。#12 的三核心真人驗收已完成；不把它視為跨 backend 人工切換證據。未測強制終止、斷電或實際 dump 還原，維運仍須停掉全部寫入者並確認備份可還原。依約定範圍完成結案，不代表已正式發布 1.0。

## 後續順序

先審閱本文件候選，授權後 push 並建立目標 `develop` 的文件 PR；CI 與指定版本驗收通過後，另依授權合併、在 #26 留下上述證據再關閉。這輪只完成本機文件準備。

#26 結案後，下一個規劃入口是 [Roadmap #1「發行門檻」](https://github.com/Command1264/InviteCodeKt/issues/1)：功能及驗收已有來源，先整理 1.0 發行候選、版號、JAR 雜湊、`main` 穩定基線及已知限制。版本調整產生新產物時補相稱驗證；整合 `main`、tag、Release 與產物發布仍需各自明確授權。
