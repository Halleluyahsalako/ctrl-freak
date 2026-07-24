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

Fully wired: Google sign-in, clipboard sync (text + image via Drive) from the popup, and full notes/categories CRUD from the `hal-notes.html` page (open via the "notes" link in the popup). `tsc --noEmit` and `vite build` both pass. Not yet tested against a real Firebase project/live sign-in — that's the next step once `.env` is filled in.
