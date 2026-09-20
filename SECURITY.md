# Security Policy

InviteCodeKt has not published a stable release yet.

Report suspected vulnerabilities privately to the repository owner. Do not include database credentials, raw IP addresses, IP hashes, player data exports, or exploit details in a public issue.

Security-sensitive project rules include:

- credentials and raw IP addresses must never be logged;
- storage failures fail closed for claims, qualification, and rewards;
- external input is validated before reaching persistence or command execution;
- reward retries are explicit when prior execution may be uncertain;
- administrative mutations require an actor, reason, and Audit Record.
