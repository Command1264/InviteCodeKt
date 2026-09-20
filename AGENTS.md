# InviteCodeKt Agent Guide

InviteCodeKt is a cross-version Minecraft invitation and referral rewards plugin. Before changing domain behavior, read `CONTEXT.md`, the relevant ADRs under `docs/adr/`, and the GitHub issue that authorizes the work.

Keep platform-independent behavior in `invitecode-core`. Keep modern Paper or Spigot API references in `invitecode-platform-modern`; do not introduce NMS. Preserve the documented storage, privacy, reward-delivery, and Java 8 compatibility boundaries unless an accepted ADR changes them.

## Agent skills

### Issue tracker

Issues and specifications are tracked in this repository's GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default five-role triage vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository with `CONTEXT.md` at the root and ADRs under `docs/adr/`. See `docs/agents/domain.md`.
