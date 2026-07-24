# Contributing

Ctrl+Freak is a personal project, open sourced so others can self-host it. Contributions are welcome, but there's no formal process yet — this will grow as the project does.

## Before opening a PR

- Run against your own Firebase project (see `docs/SETUP.md`) — there's no shared test backend.
- Keep the "bring your own backend" model intact: no code should assume a shared Firebase project, and nothing should ever hardcode credentials.
- Small, focused PRs over large ones.

## Reporting bugs

Open an issue with what you did, what you expected, and what happened instead. Screenshots help for UI issues.

## Security issues

If you find a way to read or write another user's data, or a Firestore rules gap, please open an issue marked `security` rather than a public PR with the exploit — give it a chance to get fixed first.
