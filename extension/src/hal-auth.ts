import { GoogleAuthProvider, signInWithCredential, signOut } from "firebase/auth";
import { halAuth } from "./hal-firebase";
import {
  halStoreAccessToken,
  halGetCachedAccessToken,
  halClearAccessToken,
} from "./hal-token-store";

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

function halBuildAuthUrl(clientId: string, interactive: boolean): string {
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: "token",
    redirect_uri: halRedirectUri(),
    scope: HAL_SCOPES.join(" "),
    prompt: interactive ? "consent" : "none",
  });
  return `${HAL_AUTH_URL}?${params.toString()}`;
}

function halClientId(): string {
  const clientId = import.meta.env.GOOGLE_OAUTH_CLIENT_ID;
  if (!clientId) {
    throw new Error("GOOGLE_OAUTH_CLIENT_ID is not set — check your .env");
  }
  return clientId;
}

// Parses the "#access_token=...&expires_in=..." fragment Google redirects
// back with, and caches the token so Drive calls don't force a re-prompt.
async function halHandleAuthRedirect(redirectUrl: string): Promise<string> {
  const fragment = new URL(redirectUrl).hash.slice(1);
  const params = new URLSearchParams(fragment);
  const accessToken = params.get("access_token");
  const expiresIn = Number(params.get("expires_in") ?? "3600");
  if (!accessToken) {
    throw new Error("No access_token in OAuth redirect");
  }
  await halStoreAccessToken(accessToken, expiresIn);
  return accessToken;
}

export async function halSignIn(): Promise<void> {
  const clientId = halClientId();
  const redirectUrl = await chrome.identity.launchWebAuthFlow({
    url: halBuildAuthUrl(clientId, true),
    interactive: true,
  });
  if (!redirectUrl) {
    throw new Error("Sign-in was cancelled or returned no redirect");
  }

  const accessToken = await halHandleAuthRedirect(redirectUrl);
  const credential = GoogleAuthProvider.credential(null, accessToken);
  await signInWithCredential(halAuth, credential);
}

// Returns a live Drive-scoped access token, renewing silently if the cached
// one expired. Throws if silent renewal fails (browser session cookie gone)
// — the caller should fall back to prompting the user to sign in again.
export async function halGetValidAccessToken(): Promise<string> {
  const cached = await halGetCachedAccessToken();
  if (cached) return cached;

  const clientId = halClientId();
  const redirectUrl = await chrome.identity.launchWebAuthFlow({
    url: halBuildAuthUrl(clientId, false),
    interactive: false,
  });
  if (!redirectUrl) {
    throw new Error("Silent token renewal failed — please sign in again");
  }
  return halHandleAuthRedirect(redirectUrl);
}

export async function halSignOut(): Promise<void> {
  await halClearAccessToken();
  await signOut(halAuth);
}
