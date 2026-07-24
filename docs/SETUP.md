# Setup

Ctrl+Freak has no shared backend — you create your own Firebase project and connect your own Google account. Nobody else's usage ever touches your quota, and your clipboard data never touches infrastructure someone else runs.

## 1. Create a Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com/) and create a new project. Project name: `halCtrlFreak`.
2. In **Build > Firestore Database**, create a database in production mode (the rules in `firestore.rules` lock it down — don't switch to test mode).
3. In **Build > Authentication > Sign-in method**, enable the **Google** provider.
4. In **Project settings > Your apps**, add a **Web app** and copy the config values — these go into `FIREBASE_*` in your `.env`.

## 2. Enable Google Drive access

The same Google Cloud project backs your Firebase project — you don't need a second project.

Google's console UI here is called "Google Auth Platform" — the sidebar has Overview, Branding, Audience, Clients, Data Access, Verification Center, Settings. These steps map onto that:

1. Go to the [Google Cloud console](https://console.cloud.google.com/), select the project matching your Firebase project.
2. Under **APIs & Services > Library**, enable the **Google Drive API**.
3. Under **Google Auth Platform > Audience**, set user type to **External** (unless you have a Workspace account — you likely don't), stay in **Testing** status (not Production — Production skips the test-user list but shows an "unverified app" warning on every sign-in instead), and add your own Gmail address under **Test users**.
4. Under **Google Auth Platform > Data Access**, click **Add or remove scopes** and add `https://www.googleapis.com/auth/drive.file` (search "drive.file" — it'll show as part of the Google Drive API). This is a separate step from creating the client below; skipping it causes Drive uploads to fail with `ACCESS_TOKEN_SCOPE_INSUFFICIENT` even though the extension requests the scope correctly.
5. Under **Google Auth Platform > Clients**, create an **OAuth client ID** of type **Web application** (not "Chrome Extension" — the extension signs in via `chrome.identity.launchWebAuthFlow`, which uses the standard web OAuth flow).
6. Copy the client ID into `GOOGLE_OAUTH_CLIENT_ID` in your `.env`.
7. Load the unpacked extension once (see `extension/README.md`), then copy its ID from `chrome://extensions`. Back in the OAuth client's settings, add `https://<extension-id>.chromiumapp.org/` as an **Authorized redirect URI** — without this, sign-in from the extension will fail with a redirect_uri_mismatch error.

## 3. Deploy the Firestore security rules

The rules in `firestore.rules` are what actually gate access (your Firebase config values are safe to expose — they're not secrets). Deploy them via the Firebase console's Firestore **Rules** tab, or with the Firebase CLI once it's installed:

```
firebase deploy --only firestore:rules
```

## 4. Fill in your `.env`

```
cp .env.example .env
```

Then fill in the values gathered above. `.env` is already git-ignored.

---

Once this is done, the extension and Android app both authenticate with the same Google account and read/write the same Firestore project.
