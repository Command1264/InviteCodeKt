# InviteCode

InviteCode is a Kotlin invitation and referral rewards plugin for Spigot and Paper, with experimental Folia support. `InviteCodeKt` is the repository name; the plugin and its data directory are named `InviteCode`.

[InviteCode 1.0.0](https://github.com/Command1264/InviteCodeKt/releases/tag/v1.0.0) was released on 2026-11-10. Download the plugin JAR, corresponding source archives, and checksums from the release assets. It builds one Java 8 compatible plugin JAR and keeps newer Minecraft APIs behind isolated adapters. SQLite and MySQL 8.4 are production storage backends; JSON is available for small single-instance installations. The [release record](docs/releases/1.0.0.md) lists the tested artifacts and versions. Folia remains experimental; full player acceptance of the final candidate on Spigot 26.3 is deferred. These results do not verify every newer server version.

The complete maintainer guide is in [繁體中文](docs/README.zh-TW.md) and is the primary maintained documentation. See also the [product specification](docs/spec/invitecode-1.0.md), [Bukkit operations](docs/operations/bukkit-workflow.md), [JSON setup](docs/operations/json-storage.md), [MySQL setup](docs/operations/mysql-storage.md), [offline anonymization](docs/operations/offline-anonymization.md), and [cross-backend transfer](docs/operations/cross-backend-transfer.md). These documents are maintained in Traditional Chinese.

## Build

Install [mise](https://mise.jdx.dev/) and use the repository's Gradle Wrapper. The project selects the locally registered `java@local-11` by default. Dependencies require network access on first resolution.

```powershell
mise ls --current java
mise ls java
mise exec -- java -version
mise exec -- .\gradlew.bat check shadowJar
```

To verify the Java 8 runtime, use the already registered local JDK:

```powershell
mise exec java@local-8 -- .\gradlew.bat clean check shadowJar
```

The installable artifact is `invitecode-plugin/build/libs/InviteCode-1.0.0.jar`. The build checks Java 8 class files and bundled driver entries. CI also runs MySQL 8.4 integration tests with an isolated service.

## Development branches

`develop` is the daily integration branch and the local main checkout. Feature work uses separate worktrees and targets `develop`. `main` remains the stable release baseline and GitHub default branch.
