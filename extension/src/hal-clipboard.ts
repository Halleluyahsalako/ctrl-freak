// Reads/writes the OS clipboard. Must be called from a document with focus
// in response to a user gesture (a click or keypress) — the popup satisfies
// this; a bare service worker cannot call these at all (no DOM). See
// ARCHITECTURE.md §3 for why this can't run passively in the background.

export async function halReadClipboardText(): Promise<string> {
  return navigator.clipboard.readText();
}

export async function halWriteClipboardText(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
}
