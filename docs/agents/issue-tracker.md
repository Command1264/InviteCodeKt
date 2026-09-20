# Issue tracker: GitHub

Issues and specifications for this repository live in GitHub Issues. Use the `gh` CLI and infer the repository from the configured Git remote.

## Conventions

- Create multi-line issues with `gh issue create --body-file <path>`.
- Read the issue body, comments, labels, assignees, and dependencies before acting.
- Apply and remove the labels defined in `docs/agents/triage-labels.md`.
- Close an issue only after its acceptance evidence is recorded.
- Use native sub-issues and issue dependencies when available.
- When native dependencies are unavailable, record `Blocked by: #...` in the issue body.

## Pull requests as a triage surface

**PRs as a request surface: no.**

## Skill routing

When a skill says "publish to the issue tracker", create a GitHub issue. When a skill says "fetch the relevant ticket", read that issue and its comments.
