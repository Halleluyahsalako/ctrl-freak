# extension/

Manifest V3 browser extension (Chrome, Brave, Edge natively; Firefox via `webextension-polyfill`), TypeScript + Preact + Vite.

## Setup

```
npm install
npm run build
```

Then load it unpacked: `chrome://extensions` → enable Developer mode → **Load unpacked** → select the `extension/dist` folder (created by `npm run build`).

Requires the repo-root `.env` to be filled in first — see `../docs/SETUP.md`.

## Status

Scaffolded (manifest, popup, background service worker, Firebase init) but not yet wired to real sync logic — that's Phase 1 (see `../ARCHITECTURE.md §4`).
