# Ctrl+Freak — architecture memo (v0.1)

A clipboard/note/file sync layer between phone and computer, replacing WhatsApp-to-self.
Two clients: **Android app** + **cross-browser extension**. No desktop app, nothing heavy running in the background.

## §1 Stack decisions

| Layer | Choice | Why |
|---|---|---|
| Android | Kotlin + Jetpack Compose | Native = smallest footprint, lowest battery draw. Compose makes the dark/mono custom UI cheap. |
| Desktop | None — extension covers it | Browser's always open; a second surface there costs nothing extra. |
| Extension | Manifest V3 + TypeScript + Preact | One codebase for Chrome/Brave/Edge (shared Chromium API). Firefox needs `webextension-polyfill`, not a rewrite. |
| Realtime sync | Firebase Firestore + Auth | Push-based sync (<1s), free tier is plenty for personal use, Google Sign-In doubles as Drive auth. |
| Bulk storage | Google Drive API | Files/images live in your own Drive — no storage bill, no separate account. Firestore just holds a pointer. |

**Naming convention:** every class, function, variable, CSS class/id, the Android package id, and Firestore collection/field name carries a `hal` prefix — `hal_variableName`, `hal-css-class`, `HalClipItem`, `com.hal.ctrlfreak`, `hal_clipItems`. Top-level folder scaffolding (`extension/`, `android/`, `shared/`, `docs/`) stays conventional so the project shape is still recognizable to any dev or tool. Firebase project: `halCtrlFreak`.

## §2 Data model

```ts
// a single synced clipboard entry — text or a Drive-backed image/file
interface HalClipItem {
  id: string
  kind: "text" | "image" | "file" | "code"
  text?: string                 // inline for text/code
  driveFileId?: string          // set for image/file kinds
  categoryId?: string
  pinned: boolean
  createdAt: Timestamp
  originDevice: "android" | "browser"
}

// longer-form notes, separate from the transient clipboard feed
interface HalNote {
  id: string
  title: string
  body: string                  // markdown
  categoryId?: string
  attachments: string[]         // driveFileIds
  updatedAt: Timestamp
}

interface HalCategory {
  id: string
  name: string
  color: string
}
```

Firestore collections: `hal_clipItems`, `hal_notes`, `hal_categories` (each nested under `/users/{uid}/...` — see `firestore.rules`; the `users` segment mirrors Firebase Auth's own concept rather than being part of our data model, so it's left unprefixed).

## §3 Sync flow

1. Copy on Device A — text goes straight to Firestore; images/files upload to Drive first
2. Firestore doc written: inline text, or a `driveFileId` pointer
3. Realtime listener on Device B fires instantly — no polling
4. Device B pastes text directly, or lazy-fetches the Drive file on open

## §4 MVP roadmap

- **Phase 0 — Foundations:** Firebase project, Google OAuth + Drive scopes, shared schema, `.env.example` + setup docs so credentials never live in the repo, MIT license + README. No UI yet.
- **Phase 1 — Clipboard sync core:** text + image sync working both directions, extension ⇄ Android. This is the WhatsApp replacement — don't move on until it's instant and reliable.
- **Phase 2 — Notes, categories, CRUD:** full create/edit/delete on notes and categories from either client, dark/mono UI applied throughout.
- **Phase 3 — Polish:** search across notes + clipboard history, pinned items, code-snippet syntax highlighting.

**Timing:** this memo is the full spend for this week. Start Phase 0 when you say "Start" (planned: Friday).

## §5 Feature backlog (post-MVP)

| Feature | Priority |
|---|---|
| Pinned clipboard history (browse last N, not just current) | Next |
| Code snippet syntax highlighting | Next |
| Android share-sheet integration ("Share to Ctrl+Freak") | Next |
| Global paste hotkey in the extension | Later |
| Calendar / quick tasks tied to categories (not a full calendar app) | Later |
| Offline queue — hold writes locally, flush on reconnect | Later |

## §6 Open source

Planned to be public, with a LinkedIn writeup — this shapes Phase 0, not something bolted on later.

- **License:** MIT — simplest, permissive, no friction for people who just want to self-host it.
- **No shared backend:** the app is "bring your own Firebase + Google account," not a hosted service. Each user creates their own Firebase project and grants their own Drive OAuth consent — nobody's clipboard data or API quota ever touches a project you run.
- **Nothing secret in the repo:** Firebase client config, OAuth client IDs, etc. go in a `.env.example` + setup docs, never committed with real values. (Firebase web client config is safe to expose by design — it's the Firestore security rules that actually gate access, so those need to be written carefully and tested before anyone else runs this.)
- **Repo hygiene at Phase 0:** README (what it is, setup steps), LICENSE, CONTRIBUTING.md, issue templates. Basic enough to not block Phase 1, but present from the first commit rather than retrofitted before the blog post.

## §7 Open questions

- **Encryption at rest** — is Firestore + Drive's default encryption enough, or do you want client-side encryption before anything leaves the device?
- **Device count** — designing for exactly two endpoints (phone + browser), or should the schema assume more from day one?
- **Drive quota** — any cap to enforce client-side before a big file eats into your existing Drive usage?

---
Status: draft, awaiting build window. Next: Phase 0 on your signal.
