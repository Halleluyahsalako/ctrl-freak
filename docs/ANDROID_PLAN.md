# Android implementation plan

**Status: toolchain verified, minimal app builds and runs. Firebase/auth/sync/UI not yet added.**

Tonight this went further than "plan" — JDK 17, Android SDK, and Gradle got installed on this machine, and a real minimal Compose app (`android/`) was hand-written and successfully built with `gradle assembleDebug`, producing a real installable `app-debug.apk`. The sections below that used to be speculative are now a record of what actually worked, including the exact errors hit and fixed along the way — useful if a future Gradle/AGP update reintroduces similar issues.

## What's actually verified

- **JDK**: Microsoft Build of OpenJDK 17.0.19 (`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`), installed via winget, `JAVA_HOME` persisted.
- **Android SDK**: command-line tools only (no Android Studio IDE), at `C:\Users\HP\AppData\Local\Android\Sdk`, `ANDROID_HOME` persisted. Installed: `platform-tools`, `platforms;android-34`, `platforms;android-36`, `build-tools;34.0.0`, `build-tools;36.0.0`.
- **Gradle**: 9.6.1, standalone install at `C:\Users\HP\AppData\Local\Android\gradle\gradle-9.6.1` (portable zip, not on PATH by default — see below), plus the project's own `gradlew`/`gradle-wrapper.jar` committed for anyone else building this.
- **AGP**: 9.3.0 — this is recent enough that it has **built-in Kotlin support**, which changes the plugin setup from what most tutorials (and Android Studio's own migration doc, when fetched, gave an inconsistent example for) currently show:
  - Do **not** apply `org.jetbrains.kotlin.android` — AGP 9 errors if you do ("no longer required... since AGP 9.0").
  - **Do** still apply `org.jetbrains.kotlin.plugin.compose` — Compose specifically still needs its own compiler plugin even with built-in Kotlin; omitting it fails with "Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required."
  - `kotlinOptions { jvmTarget = ... }` no longer resolves (that block came from the plugin that's now removed) — JVM target comes from `compileOptions { sourceCompatibility / targetCompatibility }` instead.
- **Real build command**: `gradle assembleDebug` (using the standalone Gradle, not `./gradlew`, on this machine — the wrapper's own first-run download of Gradle itself hit network timeouts; the checked-in wrapper is still correct for anyone with a stabler connection or Android Studio, which manages this itself).
- **Network note**: Maven Central (`repo.maven.apache.org`) was timing out intermittently during dependency resolution tonight. `android/gradle.properties` has extended HTTP timeouts (120s) and retry count (5) as a result — if a fresh build times out again, that's the likely cause, not a config regression.

## Current file layout

```
android/
  settings.gradle.kts
  build.gradle.kts          — root: AGP + Compose compiler plugin versions
  gradle.properties         — JVM args, extended network timeouts
  local.properties          — sdk.dir (gitignored, machine-specific)
  gradlew / gradlew.bat / gradle/wrapper/
  app/
    build.gradle.kts        — namespace com.hal.ctrlfreak, minSdk 26 / compileSdk 36
    src/main/
      AndroidManifest.xml
      java/com/hal/ctrlfreak/
        HalMainActivity.kt  — bare Compose scaffold, just renders "Ctrl+Freak"
        data/HalSchema.kt   — HalClipItem/HalNote/HalCategory/HalAttachment,
                              mirrors shared/schema.ts field-for-field
      res/values/themes.xml
```

## What's left — in build order

Each of these should be added and re-verified with `gradle assembleDebug` one at a time, not all at once — that's exactly how tonight's plugin errors got caught quickly instead of compounding.

1. **Firebase**: add `com.google.firebase:firebase-bom`, `firebase-firestore-ktx`, `firebase-auth-ktx`. Needs `google-services.json` from Firebase console → Project settings → add an Android app with package `com.hal.ctrlfreak` (this also needs your debug keystore's SHA-1 fingerprint registered, same note as in `docs/SETUP.md`).
2. **Auth**: `androidx.credentials` + `credentials-play-services-auth` + `play-services-auth`, per `HalAuth.kt` plan below.
3. **Sync**: Firestore CRUD/listeners mirroring `hal-sync.ts`/`hal-notes-sync.ts` — same collections, same `firestore.rules`, zero backend changes needed.
4. **Drive**: OkHttp-based upload/fetch mirroring `hal-drive.ts`.
5. **Clipboard + UI**: manual "sync now" button (Android 10+ blocks background clipboard reads, same constraint spirit as the browser — see `ARCHITECTURE.md §3`) plus an `ACTION_SEND` share-sheet intent filter, which is arguably the *better* Android UX since it skips the clipboard round-trip entirely for sharing from other apps.
6. **Notes UI**: Compose screen mirroring `hal-notes.tsx`'s feature set (categories, search, attachments). The extension's editor got upgraded to a real Tiptap WYSIWYG editor tonight (see `ARCHITECTURE.md` commit history) — Android's equivalent would be a Compose rich-text approach (e.g. `androidx.compose.foundation.text` `BasicTextField` with `AnnotatedString`, or a library) once this is reached; not designed in detail yet.

## Auth design note (unchanged from the original plan, still accurate)

Credential Manager's `GetGoogleIdOption` gives you an **ID token**, not an OAuth **access token** — these are different things and easy to conflate. The ID token is enough to sign into Firebase Auth via `GoogleAuthProvider`. For Drive calls you separately need an access token with the `drive.file` scope, requested via the Google Identity/Authorization APIs, not Credential Manager. Get this distinction right before writing `HalAuth.kt` — it's the most likely spot for a subtle bug, same caution as the original plan flagged.
