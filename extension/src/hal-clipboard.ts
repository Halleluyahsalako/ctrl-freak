// Reads/writes the OS clipboard. Must be called from a document with focus
// in response to a user gesture (a click or keypress) — the popup satisfies
// this; a bare service worker cannot call these at all (no DOM). See
// ARCHITECTURE.md §3 for why this can't run passively in the background.

export type HalClipboardRead =
  | { kind: "text"; text: string }
  | { kind: "image"; blob: Blob; mimeType: string };

export async function halReadClipboardText(): Promise<string> {
  return navigator.clipboard.readText();
}

export async function halWriteClipboardText(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
}

// Inspects whatever's actually on the clipboard right now and returns it as
// either text or an image blob — covers content copied from anywhere on the
// OS, not just web pages (e.g. an image copied from Windows' own tools).
export async function halReadClipboardSmart(): Promise<HalClipboardRead | null> {
  const items = await navigator.clipboard.read();
  for (const item of items) {
    const imageType = item.types.find((t) => t.startsWith("image/"));
    if (imageType) {
      const blob = await item.getType(imageType);
      return { kind: "image", blob, mimeType: imageType };
    }
  }
  const text = await navigator.clipboard.readText();
  return text ? { kind: "text", text } : null;
}

// Chrome's Clipboard API only reliably accepts "image/png" as a
// ClipboardItem key — writing a jpeg/webp/etc blob under its own mime type
// throws (caught upstream and misread as a network error, which it isn't).
// Anything not already png gets redrawn through a canvas first.
async function halToPngBlob(blob: Blob): Promise<Blob> {
  if (blob.type === "image/png") return blob;
  const bitmap = await createImageBitmap(blob);
  const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
  const ctx = canvas.getContext("2d");
  if (!ctx) return blob;
  ctx.drawImage(bitmap, 0, 0);
  return canvas.convertToBlob({ type: "image/png" });
}

export async function halWriteImageToClipboard(blob: Blob): Promise<void> {
  const pngBlob = await halToPngBlob(blob);
  await navigator.clipboard.write([new ClipboardItem({ "image/png": pngBlob })]);
}
