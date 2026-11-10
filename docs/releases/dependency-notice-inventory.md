# 1.0.0 候選依賴授權資訊盤點

日期：2026-10-15。本輪只盤點實際封裝與現有 metadata，不選擇 InviteCode 的產品授權，也不修改封裝依賴。

根目錄尚未選定產品 LICENSE。候選 JAR 的根 `LICENSE` 以 `Licensing Information User Manual / MySQL Connector/J 26.7.0 Community` 開頭，來自隨附依賴，不能當作 InviteCode 自己的 LICENSE。

## 已核對的文字 entry

候選 `InviteCode-1.0.0.jar`（SHA-256 `F2055052DC9E8C0913AFE85C6B19E46850FC2F2FC2C7B5467DE60CA567203BCE`）含下列文字檔：

- `LICENSE`：MySQL Connector/J licensing information。
- `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE`。
- `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus`。

搜尋不把檔名含 `Notice` 的 Java class 當成 NOTICE 文件。未查得 Gson、Kotlin、HikariCP、SLF4J 各自具名稱的 LICENSE／NOTICE entry；這只描述候選 ZIP，不判定其散布要求。

## 後續確認

依 Gradle 實際 runtime dependencies 核對 Kotlin stdlib、Gson 2.14.0、sqlite-jdbc 3.53.4.0、MySQL Connector/J 26.7.0、HikariCP 4.0.3、SLF4J 1.7.36 與其傳遞依賴的授權及必要隨附資訊。compileOnly 的 Bukkit／Vault 與測試用依賴須和封裝內容分開。

本機快取中 MySQL Connector/J 26.7.0 的 POM 宣告 `The GNU General Public License, v2 with Universal FOSS Exception, v1.0`；後續產品授權決策須一併核對實際上游文字，不只依 private repository 狀態判定可以散布。

目前本機候選與測試可以繼續；對外提供 JAR、公開 repository 或正式發行前，產品授權與需要隨附的第三方資訊仍待確認。若補充封裝資訊導致 JAR 改變，須產生新的 SHA-256 與 entry 比對，不沿用本次固定雜湊。
