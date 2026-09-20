# InviteCodeKt 1.0 Design

## Product boundary

InviteCodeKt creates one durable Invitation between an Inviter and an Invitee, qualifies that Invitation through configurable policies, and delivers auditable rewards. It is a new product line: legacy source, configuration, and `playerData.json` are reference material only and receive no compatibility or importer.

## Invitation lifecycle

- A player owns an automatically generated Invite Code but becomes an Inviter only after another player claims it.
- An Invitee has at most one Inviter. An Inviter may have many Invitees.
- A player may claim only while they have no Invitation, have not become an Inviter, and remain inside the Claim Window.
- First Seen At is when InviteCodeKt first observes the player. The default Claim Window is 24 hours.
- Claim requires a 30-second confirmation that shows the current Inviter name and code.
- A Claimed Invitation may become Qualified exactly once. Qualification is permanent.
- Ordinary players cannot undo an Invitation. Audited administration supports Unlink, Eligibility Reset, Transfer of unqualified Invitations, and Force Qualification.

## Invite Codes and input

- The default is a unique six-character hexadecimal Color Invite Code.
- Minecraft 1.16 and later display its actual RGB color; older clients receive the nearest legacy color.
- Optional Crockford Base32 and validated custom codes may be enabled later.
- Modern capable Paper and Spigot servers use native Dialog text input.
- Legacy servers use a one-shot private chat input with a 60-second timeout.
- `/invitecode claim <code>` remains available everywhere.

## Qualification

- A policy is an OR of groups whose conditions are ANDed.
- Built-in conditions cover tracked online time, elapsed time, distinct login days, Bukkit permissions, PlaceholderAPI comparisons, and administrator approval.
- Placeholder errors produce `UNKNOWN`, which cannot qualify an Invitation.
- Other plugins may register condition providers or request reevaluation. Only the explicit Admin API may force qualification.
- Events are emitted after transaction commit and cannot be cancelled.
- Qualified records preserve the stable policy ID and an immutable revision hash.

## Rewards

- Qualification Rewards may target Inviter, Invitee, or both. Milestone Rewards target Inviter counts.
- Actions include console commands, player commands, messages, and Vault Economy deposits.
- Player commands never gain temporary operator privileges.
- Offline-required actions remain Pending and are retried when the player joins.
- Command dispatch acceptance counts as delivery; downstream side effects cannot be inferred.
- Unsafe interruption during processing yields `UNCERTAIN`, never an automatic retry.
- Reward Delivery and action state transitions are auditable and idempotency-keyed.

## Storage and privacy

- SQLite and MySQL are production backends. MySQL must remain correct under concurrent plugin instances.
- JSON is for small installations, tests, and versioned interchange.
- Backend switching uses an explicit migration with preflight, write pause, verification, and cutover.
- Storage failures fail closed; the plugin never falls back to memory or another backend.
- IP anti-abuse stores a keyed hash for 30 days, not the raw address.
- Anonymization removes names, IP hashes, and display data while preserving historical consistency.
- Credentials and IP hashes never appear in logs.

## Configuration and operations

- Configuration is split into general, storage, qualifications, rewards, and locale files.
- Invalid reloads preserve the previous valid configuration.
- Storage and secret changes require restart.
- Configuration migration creates a reviewable output and never silently rewrites the user's file.
- `doctor` is read-only and reports storage, integration, configuration, and reward-delivery health.
- No metrics, telemetry, or automatic update download is included in 1.0.

## Platform and compatibility

- Kotlin with Gradle Kotlin DSL, packaged as one JAR.
- Java 8 bytecode is the legacy baseline. No NMS is allowed.
- Official support spans tested Spigot and Paper releases from 1.8.8 onward.
- Release tests cover Spigot 1.8.8, 1.12.2, 1.16.5; Paper 1.20.6, 1.21.8; and the latest Paper and Spigot at release time.
- Folia is experimental. Its failure blocks the compatibility claim but not a Paper/Spigot release.

## Initial non-goals

- Legacy data or configuration import
- Reward exchange GUI
- Native permission-grant reward action
- NMS integration
- Metrics or automatic updating
- Automatic storage migration during startup
