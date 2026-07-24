# ctrl+sync

A lightweight clipboard, note, and file sync layer between your phone and your computer — text, images, and files copied on one device show up on the other in under a second.

Built to replace "send it to yourself on WhatsApp," which crashes constantly and is not what WhatsApp is for.

## What it is

- **Android app** (Kotlin + Jetpack Compose) — native, small, low battery draw.
- **Browser extension** (Manifest V3, Chrome/Brave/Edge/Firefox) — no separate desktop app; the browser is always open anyway.
- **Your own Firebase project** for realtime sync of clipboard items and notes.
- **Your own Google Drive** for the actual files/images — nothing is stored on infrastructure anyone else runs.

Full design decisions and data model: [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Status

Phase 0 (foundations) in progress. See the roadmap in `ARCHITECTURE.md §4`.

## Setup

This project has no shared backend — every user runs their own Firebase project and connects their own Google account. Follow [`docs/SETUP.md`](docs/SETUP.md) to create your Firebase project, enable Google sign-in, and get your `.env` filled in.

## License

MIT — see [`LICENSE`](LICENSE).
