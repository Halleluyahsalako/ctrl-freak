import { initializeApp } from "firebase/app";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

// Values come from the repo-root .env (see ../.env.example), exposed via
// Vite's envPrefix config in vite.config.ts.
const halFirebaseConfig = {
  apiKey: import.meta.env.FIREBASE_API_KEY,
  authDomain: import.meta.env.FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.FIREBASE_APP_ID,
};

export const halApp = initializeApp(halFirebaseConfig);
export const halAuth = getAuth(halApp);
export const halDb = getFirestore(halApp);
export const halGoogleProvider = new GoogleAuthProvider();
halGoogleProvider.addScope("https://www.googleapis.com/auth/drive.file");
