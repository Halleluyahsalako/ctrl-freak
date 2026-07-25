import { render } from "preact";
import { useEffect, useState } from "preact/hooks";
import { onAuthStateChanged, type User } from "firebase/auth";
import { halAuth } from "./hal-firebase";
import { halSignIn, halSignOut, halGetValidAccessToken } from "./hal-auth";
import {
  halReadClipboardSmart,
  halWriteClipboardText,
  halWriteImageToClipboard,
} from "./hal-clipboard";
import { halUploadFileToDrive, halFetchDriveFileBlob } from "./hal-drive";
import { halPushClip, halSubscribeToClips, halSetClipPinned } from "./hal-sync";
import type { HalClipItem } from "@shared/schema";

function HalPopup() {
  const [halUser, setHalUser] = useState<User | null>(null);
  const [halClips, setHalClips] = useState<HalClipItem[]>([]);
  const [halBusy, setHalBusy] = useState(false);
  const [halError, setHalError] = useState<string | null>(null);

  useEffect(() => onAuthStateChanged(halAuth, setHalUser), []);

  useEffect(() => {
    if (!halUser) {
      setHalClips([]);
      return;
    }
    return halSubscribeToClips(halUser.uid, setHalClips);
  }, [halUser]);

  async function halHandleSignIn() {
    setHalError(null);
    try {
      await halSignIn();
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandleSyncNow() {
    if (!halUser) return;
    setHalBusy(true);
    setHalError(null);
    try {
      const read = await halReadClipboardSmart();
      if (!read) return;

      if (read.kind === "text") {
        await halPushClip(halUser.uid, {
          kind: "text",
          text: read.text,
          pinned: false,
          originDevice: "browser",
        });
        return;
      }

      // image: upload to Drive first, then store the pointer
      const accessToken = await halGetValidAccessToken();
      const extension = read.mimeType.split("/")[1] ?? "png";
      const driveFileId = await halUploadFileToDrive(
        accessToken,
        read.blob,
        `hal-clip-${Date.now()}.${extension}`,
      );
      await halPushClip(halUser.uid, {
        kind: "image",
        driveFileId,
        pinned: false,
        originDevice: "browser",
      });
    } catch (err) {
      setHalError((err as Error).message);
    } finally {
      setHalBusy(false);
    }
  }

  async function halHandleTogglePin(e: Event, clip: HalClipItem) {
    e.stopPropagation();
    if (!halUser) return;
    try {
      await halSetClipPinned(halUser.uid, clip.id, !clip.pinned);
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandlePaste(clip: HalClipItem) {
    setHalError(null);
    try {
      if (clip.kind === "image" && clip.driveFileId) {
        const accessToken = await halGetValidAccessToken();
        const blob = await halFetchDriveFileBlob(accessToken, clip.driveFileId);
        await halWriteImageToClipboard(blob);
      } else if (clip.text) {
        await halWriteClipboardText(clip.text);
      }
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  if (!halUser) {
    return (
      <main class="hal-popup">
        <h1 class="hal-title">Ctrl+Freak</h1>
        <p class="hal-subtitle">clipboard + notes, synced to your phone</p>
        <button class="hal-button" onClick={halHandleSignIn}>
          Sign in with Google
        </button>
        {halError && <p class="hal-error">{halError}</p>}
      </main>
    );
  }

  return (
    <main class="hal-popup">
      <div class="hal-header">
        <div>
          <h1 class="hal-title">Ctrl+Freak</h1>
          <p class="hal-subtitle">{halUser.email}</p>
        </div>
        <div>
          <button
            class="hal-link"
            onClick={() => chrome.tabs.create({ url: chrome.runtime.getURL("hal-notes.html") })}
          >
            notes
          </button>
          <button class="hal-link" onClick={halSignOut}>
            sign out
          </button>
        </div>
      </div>

      <button class="hal-button" onClick={halHandleSyncNow} disabled={halBusy}>
        {halBusy ? "Syncing…" : "Sync clipboard now"}
      </button>
      {halError && <p class="hal-error">{halError}</p>}

      {halClips.length === 0 ? (
        <p class="hal-empty">Nothing synced yet.</p>
      ) : (
        <ul class="hal-clip-list">
          {[...halClips]
            .sort((a, b) => Number(b.pinned) - Number(a.pinned))
            .map((clip) => (
              <li
                key={clip.id}
                class="hal-clip-item"
                onClick={() => halHandlePaste(clip)}
                title="Click to copy"
              >
                <button
                  class={`hal-pin ${clip.pinned ? "hal-pinned" : ""}`}
                  onClick={(e) => halHandleTogglePin(e, clip)}
                  title={clip.pinned ? "Unpin" : "Pin"}
                >
                  *
                </button>
                <span class="hal-clip-text">
                  {clip.kind === "image" ? "[image]" : clip.text}
                </span>
              </li>
            ))}
        </ul>
      )}

      <p class="hal-footer">
        <kbd>Ctrl+Shift+Y</kbd> opens this popup · click a clip to copy it
      </p>
    </main>
  );
}

render(<HalPopup />, document.getElementById("hal-root")!);
