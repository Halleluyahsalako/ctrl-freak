import { GoogleAuthProvider, signInWithCredential, signOut } from "firebase/auth";
import { halAuth } from "./hal-firebase";

// chrome.identity.getAuthToken requires a special "Chrome Extension" OAuth
// client type. launchWebAuthFlow works with the ordinary Web OAuth client we
// already set up in docs/SETUP.md, redirecting to a chromiumapp.org URL that
// Chrome intercepts — no extra Google Cloud console client needed beyond
// adding that redirect URI. See docs/SETUP.md for the exact URI to register.

const HAL_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const HAL_SCOPES = [
  "openid",
  "email",
  "https://www.googleapis.com/auth/drive.file",
];

function halRedirectUri(): string {
  return chrome.identity.getRedirectURL();
}

function halBuildAuthUrl(clientId: string): string {
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: "token",
    redirect_uri: halRedirectUri(),
    scope: HAL_SCOPES.join(" "),
    prompt: "consent",
  });
  return `${HAL_AUTH_URL}?${params.toString()}`;
}

export async function halSignIn(): Promise<void> {
  const clientId = import.meta.env.GOOGLE_OAUTH_CLIENT_ID;
  if (!clientId) {
    throw new Error("GOOGLE_OAUTH_CLIENT_ID is not set — check your .env");
  }

  const redirectUrl = await chrome.identity.launchWebAuthFlow({
    url: halBuildAuthUrl(clientId),
    interactive: true,
  });
  if (!redirectUrl) {
    throw new Error("Sign-in was cancelled or returned no redirect");
  }

  const fragment = new URL(redirectUrl).hash.slice(1);
  const accessToken = new URLSearchParams(fragment).get("access_token");
  if (!accessToken) {
    throw new Error("No access_token in OAuth redirect");
  }

  const credential = GoogleAuthProvider.credential(null, accessToken);
  await signInWithCredential(halAuth, credential);
}

export async function halSignOut(): Promise<void> {
  await signOut(halAuth);
}
