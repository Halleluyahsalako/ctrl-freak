# Android implementation plan

**Status: unverified spec, not tested code.** This machine has no Java or Android SDK installed, so unlike the extension (typecheck + build both verified), nothing here has been compiled. Treat this as a precise starting point to build from in Android Studio, not a drop-in working app — expect to fix version mismatches Android Studio's own wizard would have avoided.

## Why not hand-write the Gradle project too

Android Gradle/Compose/Kotlin version pairings are fragile and change often. Getting one number wrong (AGP vs. Kotlin vs. Compose compiler) produces a build that fails before a single line of app code runs, and there's no way to catch that without actually building. Android Studio's "Empty Activity (Compose)" wizard pins correct, current versions automatically — start there, then layer the plan below on top, rather than hand-typing `build.gradle.kts` from scratch.

## 1. Create the project

- Android Studio → New Project → **Empty Activity** (Compose)
- Package name: `com.hal.ctrlfreak` (see `ARCHITECTURE.md §1` naming convention)
- Minimum SDK: 26 (Android 8.0) — covers the vast majority of devices without complicating clipboard/Compose APIs
- Language: Kotlin

## 2. Add dependencies

Via the wizard's version catalog (`libs.versions.toml`) or directly — use whatever current stable versions Android Studio suggests when you add these, since anything pinned here today may already be stale:

- `com.google.firebase:firebase-bom` (Firestore + Auth)
- `com.google.firebase:firebase-firestore-ktx`
- `com.google.firebase:firebase-auth-ktx`
- `androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth` (Google sign-in — this replaced the old `GoogleSignIn` API; the Credential Manager APIs are the current recommended path)
- `com.google.android.gms:play-services-auth` (still needed alongside Credential Manager for the Google ID token flow)
- `com.squareup.okhttp3:okhttp` (Drive REST calls — no need for the full Drive SDK for three simple calls)
- Also add `google-services.json` (downloaded from Firebase console → Project settings → your Android app registration) to `android/app/`

## 3. File plan (mirrors the extension's module split 1:1)

| File | Mirrors | Purpose |
|---|---|---|
| `data/HalSchema.kt` | `shared/schema.ts` | `HalClipItem`, `HalNote`, `HalCategory` as Kotlin data classes — same fields, same `hal_` Firestore collection names |
| `auth/HalAuth.kt` | `hal-auth.ts` | Sign in via Credential Manager's `GetGoogleIdOption`, exchange the ID token for a `GoogleAuthProvider` Firebase credential. Separately request an OAuth access token with the `drive.file` scope for Drive calls (Credential Manager gives you an ID token, not an access token — these are two different things, easy to conflate) |
| `sync/HalSync.kt` | `hal-sync.ts` + `hal-notes-sync.ts` | Firestore CRUD + realtime listeners for `hal_clipItems`, `hal_notes`, `hal_categories` under `users/{uid}/...` — same paths, same `firestore.rules` already deployed, no backend changes needed |
| `drive/HalDrive.kt` | `hal-drive.ts` | OkHttp multipart upload to Drive, fetch-by-id for paste-back |
| `clipboard/HalClipboard.kt` | `hal-clipboard.ts` | `ClipboardManager.getPrimaryClip()` / `setPrimaryClip()` — see the constraint below |
| `ui/HalMainActivity.kt` | `hal-popup.tsx` | Compose screens: sign-in gate, clip list, "sync now" button |
| `ui/HalNotesScreen.kt` | `hal-notes.tsx` | Notes/categories CRUD screen |

## 4. The clipboard constraint is different, not absent

Android 10+ blocks background/non-focused apps from reading the clipboard for privacy — the same spirit as the browser constraint in `ARCHITECTURE.md §3`, different mechanism. `ClipboardManager.OnPrimaryClipChangedListener` only fires reliably while the app is foregrounded (or is the default IME, which this app has no reason to be).

Two ways to get content in, and both are worth building rather than fighting the OS for passive background capture:

1. **Manual sync button** — same pattern as the extension: open the app, tap "Sync clipboard now," it reads whatever's currently on the clipboard.
2. **Share-sheet integration** (already on the `ARCHITECTURE.md §5` backlog as "Next," not "Later" — worth pulling into this same pass) — add an `ACTION_SEND` intent filter to the manifest so any app's native "Share" menu lists Ctrl+Freak directly. This is arguably the *better* Android UX, not a fallback: sharing an image from the Photos app straight to Ctrl+Freak needs no clipboard round-trip at all.

## 5. What to bring back for review

Once this builds in Android Studio, the things most likely to need a second pass:
- The Credential Manager ID-token vs. OAuth-access-token distinction in `HalAuth.kt` (easy to get subtly wrong)
- Whatever version numbers Android Studio actually picked vs. what's written above
- Firestore security rules already deployed should need zero changes — same `users/{uid}/hal_*` shape as the extension
