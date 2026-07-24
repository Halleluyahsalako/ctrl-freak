// Caches the Google OAuth access token (from hal-auth.ts) in extension
// storage so Drive uploads don't force a fresh sign-in on every use.
// Implicit-flow tokens ("response_type=token") carry no refresh token, so
// once this expires we either renew silently (if the browser still has an
// active Google session) or fall back to an interactive prompt.

interface HalStoredToken {
  accessToken: string;
  expiresAt: number; // epoch millis
}

const HAL_TOKEN_KEY = "hal_google_access_token";

export async function halStoreAccessToken(
  accessToken: string,
  expiresInSeconds: number,
): Promise<void> {
  const record: HalStoredToken = {
    accessToken,
    expiresAt: Date.now() + expiresInSeconds * 1000,
  };
  await chrome.storage.local.set({ [HAL_TOKEN_KEY]: record });
}

export async function halGetCachedAccessToken(): Promise<string | null> {
  const result = await chrome.storage.local.get(HAL_TOKEN_KEY);
  const record = result[HAL_TOKEN_KEY] as HalStoredToken | undefined;
  if (!record) return null;
  // 60s safety margin before actual expiry
  if (Date.now() > record.expiresAt - 60_000) return null;
  return record.accessToken;
}

export async function halClearAccessToken(): Promise<void> {
  await chrome.storage.local.remove(HAL_TOKEN_KEY);
}
