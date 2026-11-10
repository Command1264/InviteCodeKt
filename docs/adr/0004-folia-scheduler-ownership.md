# 依工作歸屬派送 Bukkit 與 Folia 任務

狀態：已採納（Folia 仍屬實驗性）

InviteCode 繼續提供單一 Java 8 相容 JAR。舊版 Spigot／Paper 使用 Bukkit scheduler；Folia 使用隔離於 `invitecode-platform-modern` 的反射 adapter，避免舊版 JVM 載入新版 scheduler API。只在偵測到 Folia 時選擇其 adapter，`plugin.yml` 才宣告 `folia-supported: true`。

背景儲存操作仍由單一 executor 執行，且先保存 Reward Action 的 `PROCESSING` 狀態。完成 callback 依歸屬派送：全域狀態、Console 指令與 Vault Economy 路徑進入 global scheduler；玩家 Statistic、permission、Placeholder、指令、訊息及私密輸入進入玩家 entity scheduler。玩家參照由登入／離線事件管理，排程器跟隨玩家跨 region；玩家在 callback 前離線時，線上動作保持 `PENDING`，已開始而結果不明的外部動作仍依 [ADR 0002](./0002-durable-reward-delivery.md) 進入 `UNCERTAIN`，不可自動重試。

此設計讓 Spigot 1.8.8、Paper 26.2 與 Folia 26.1.2 的同一份 JAR 得到實測覆蓋，並可在 Folia 兩個 region 排程執行緒上處理相距遙遠的玩家。它不保證任意第三方 Vault／Placeholder provider、未測 Folia 版本或原生 Dialog 視覺行為相容；這些項目需各自測試，不能由 `folia-supported` 欄位推論。測試範圍與剩餘限制記錄於 [Issue #12 證據](../testing/evidence/2026-09-24-issue-12-startup-matrix.md)。
