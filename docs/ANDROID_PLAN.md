# Android implementation plan

**Status: feature-complete MVP, verified against the real Firebase project.** Sign-in, clipboard sync (text + image via Drive), share-sheet integration, and notes/categories CRUD all build and run. The one deliberate scope cut: notes are plain title/body/category, no rich text or attachments yet (the extension's editor has both — see below).

## What's actually verified

- **JDK**: Microsoft Build of OpenJDK 17.0.19 (`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`), installed via winget, `JAVA_HOME` persisted.
- **Android SDK**: command-line tools only (no Android Studio IDE), at `C:\Users\HP\AppData\Local\Android\Sdk`, `ANDROID_HOME` persisted. Installed: `platform-tools`, `platforms;android-34/36`, `build-tools;34.0.0/36.0.0`.
- **Gradle**: 9.6.1, standalone install at `C:\Users\HP\AppData\Local\Android\gradle\gradle-9.6.1`, plus the project's own `gradlew`/`gradle-wrapper.jar` committed for anyone else building this. On this machine, use the standalone `gradle` command, not `./gradlew` — the wrapper's own first-run Gradle download hit network timeouts here; the checked-in wrapper is still correct for a stabler connection or Android Studio.
- **AGP**: 9.3.0, **built-in Kotlin support** — real gotchas hit and fixed, documented in commit history:
  - Don't apply `org.jetbrains.kotlin.android` — AGP 9 errors if you do.
  - **Do** still apply `org.jetbrains.kotlin.plugin.compose` — needed separately even with built-in Kotlin.
  - `kotlinOptions { jvmTarget }` no longer resolves — use `compileOptions { sourceCompatibility / targetCompatibility }`.
- **Firebase**: real `google-services.json` registered in Firebase console (package `com.hal.ctrlfreak`, debug SHA-1 `6B:EA:C8:38:BD:19:9A:8D:8A:33:77:9C:D3:B0:68:D3:12:45:43:4D`), file lives at `android/app/google-services.json`, gitignored per the existing secrets policy — same as everyone else self-hosting this needs to do their own registration.
- **Network note**: Maven Central was timing out intermittently across the whole session. `android/gradle.properties` has extended HTTP timeouts (120s) and retry count (5) as a result.

## Current file layout

```
android/app/src/main/java/com/hal/ctrlfreak/
  HalMainActivity.kt   — sign-in gate, Clips/Notes tab switch, clip list + "sync now"
  data/HalSchema.kt    — HalClipItem/HalNote/HalCategory/HalAttachment
  auth/
    HalFirebase.kt     — Firebase.auth / Firebase.firestore accessors
    HalAuth.kt         — Credential Manager sign-in + separate Drive Authorization API
  sync/HalSync.kt      — Firestore CRUD + callbackFlow realtime listeners
  drive/HalDrive.kt    — OkHttp multipart upload + fetch
  clipboard/HalClipboard.kt — read/write text, read image bytes
  ui/HalNotesScreen.kt — categories + notes CRUD, plain text body
```

A real design note worth remembering: `HalSchema.kt`'s `kind`/`originDevice` fields are plain `String`s with a constants object, not Kotlin `enum class`. A real enum would have serialized to Firestore as `"TEXT"`/`"ANDROID"` (Kotlin's default `.name`), while the extension writes lowercase `"text"`/`"android"` — same collections, silently incompatible data neither side would have caught without cross-checking by hand. Keep this pattern for any future schema fields shared across both clients.

## What's left

1. **Rich text + attachments in notes** — the extension's editor is now a full Tiptap WYSIWYG with file/image/PDF attachments (paste-to-attach, native preview). Android's equivalent needs either a WebView hosting a small JS editor, or a Compose rich-text library/custom implementation — genuinely undesigned, not just unbuilt. Worth a dedicated planning pass, not a blind late-night attempt.
2. **Image paste-back to system clipboard** — clicking a synced image clip currently does nothing (noted in-code); text paste-back works. Needs `ClipData.newUri` backed by a `FileProvider` or similar.
3. **Share-sheet while signed out** — `HalMainActivity`'s share-intent handling only fires once `user` is non-null; sharing to the app before ever signing in silently drops the shared content instead of queuing it.
4. **Search** (notes) and **pinned clips** — both exist in the extension, not yet ported to Android.
5. **An actual on-device test** — everything here is compile-and-build verified, not run-on-a-phone-and-clicked-through verified. No emulator or physical device was used tonight.
