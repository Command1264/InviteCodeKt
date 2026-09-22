# InviteCode

InviteCode is a Kotlin invitation and referral rewards plugin for Spigot and Paper. `InviteCodeKt` is the repository name; the plugin and its data directory are named `InviteCode`.

The project is currently `0.1.0-SNAPSHOT`. It builds one Java 8 compatible plugin JAR and keeps newer Minecraft APIs behind isolated adapters. SQLite and MySQL 8.4 are implemented storage backends. The final player experience and cross-version server matrix still require release-stage acceptance.

The complete maintainer guide is in [繁體中文](docs/README.zh-TW.md) and is the primary maintained documentation. See also the [product specification](docs/spec/invitecode-1.0.md), [Bukkit operations](docs/operations/bukkit-workflow.md), and [MySQL setup](docs/operations/mysql-storage.md). These documents are maintained in Traditional Chinese.

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

The installable artifact is `invitecode-plugin/build/libs/InviteCode-0.1.0-SNAPSHOT.jar`. The build checks Java 8 class files and bundled driver entries. CI also runs MySQL 8.4 integration tests with an isolated service.

## Development branches

`develop` is the daily integration branch and the local main checkout. Feature work uses separate worktrees and targets `develop`. `main` remains the stable release baseline and GitHub default branch.
