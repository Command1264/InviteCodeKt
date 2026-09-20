# Contributing

InviteCodeKt is currently developed through issue-backed task branches.

1. Read `CONTEXT.md`, the relevant ADRs under `docs/adr/`, and the issue being implemented.
2. Branch from the current `develop` head and use an isolated worktree.
3. Keep `invitecode-core` free of Bukkit and storage implementation dependencies.
4. Keep modern server API references inside `invitecode-platform-modern`.
5. Add meaningful regression tests for changed behavior.
6. Run `./gradlew check shadowJar` before requesting review.

Use the canonical domain terms from `CONTEXT.md`. Do not add legacy migration behavior unless a future accepted decision explicitly changes that non-goal.
