# Setup

ctrl+sync has no shared backend — you create your own Firebase project and connect your own Google account. Nobody else's usage ever touches your quota, and your clipboard data never touches infrastructure someone else runs.

## 1. Create a Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com/) and create a new project.
2. In **Build > Firestore Database**, create a database in production mode (the rules in `firestore.rules` lock it down — don't switch to test mode).
3. In **Build > Authentication > Sign-in method**, enable the **Google** provider.
4. In **Project settings > Your apps**, add a **Web app** and copy the config values — these go into `FIREBASE_*` in your `.env`.

## 2. Enable Google Drive access

The same Google Cloud project backs your Firebase project — you don't need a second project.

1. Go to the [Google Cloud console](https://console.cloud.google.com/), select the project matching your Firebase project.
2. Under **APIs & Services > Library**, enable the **Google Drive API**.
3. Under **APIs & Services > OAuth consent screen**, set it to **External** (unless you have a Workspace account) and add your own Google account as a test user.
4. Under **APIs & Services > Credentials**, create an **OAuth client ID**. Add `https://www.googleapis.com/auth/drive.file` as a scope — this restricts access to only the files ctrl+sync itself creates, not your whole Drive.
5. Copy the client ID into `GOOGLE_OAUTH_CLIENT_ID` in your `.env`.

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
