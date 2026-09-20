# 發布單一跨版本插件 JAR

InviteCodeKt 以一個可安裝 JAR 支援 Spigot 與 Paper 1.8.8 到最新已測試版本。平台無關與舊版程式碼以 Java 8 為目標且不使用 NMS；新版伺服器 API reference 放在隔離的 capability adapter，避免舊版 JVM 載入不相容的 class。這個決策讓安裝方式保持簡單，同時接受必須維護具代表性真實伺服器相容性矩陣的成本。
