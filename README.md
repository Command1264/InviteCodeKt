# InviteCodeKt

InviteCodeKt is a from-scratch Kotlin rewrite of a Minecraft invitation and referral rewards plugin. It targets a single installable JAR across Spigot and Paper 1.8.8 through the latest tested release, with modern capabilities loaded through isolated adapters.

> [!IMPORTANT]
> This repository is currently a `0.1.0-SNAPSHOT` scaffold. Invitation, qualification, storage, and reward workflows are deliberately not implemented yet.

## Project goals

- Establish one permanent Inviter-to-Invitee relationship through an Invite Code.
- Separate claiming an Invitation from qualifying it for rewards.
- Support configurable qualification policies and durable reward delivery.
- Support SQLite and MySQL, with JSON limited to small installations and interchange.
- Preserve Java 8 runtime compatibility without using NMS.
- Add native Dialog input on capable modern servers and safe chat/command fallbacks elsewhere.

The canonical domain language is in [`CONTEXT.md`](./CONTEXT.md). The accepted product and engineering scope is in [`docs/spec/invitecode-1.0.md`](./docs/spec/invitecode-1.0.md).

## Build

Requirements:

- JDK 8 or newer to run the checked-in Gradle wrapper; JDK 11 is recommended for development
- Network access for the first dependency resolution

```powershell
./gradlew check shadowJar
```

The installable scaffold JAR is written to:

```text
invitecode-plugin/build/libs/InviteCodeKt-0.1.0-SNAPSHOT.jar
```

The build verifies that current production classes use Java 8 bytecode. Future modern adapters may use newer bytecode only when their classes remain isolated and are verified not to load on legacy JVMs.

## Branches

- `main`: stable release baseline and GitHub default branch
- `develop`: day-to-day integration branch
- task branches: isolated worktrees targeting `develop`

## Status

Implementation work is tracked in this repository's GitHub Issues. No legacy InviteCode data or configuration compatibility is planned.
