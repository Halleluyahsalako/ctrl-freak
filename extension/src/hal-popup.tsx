import { render } from "preact";
import { useEffect, useState } from "preact/hooks";
import { onAuthStateChanged, type User } from "firebase/auth";
import { halAuth } from "./hal-firebase";
import { halSignIn, halSignOut } from "./hal-auth";
import { halReadClipboardText, halWriteClipboardText } from "./hal-clipboard";
import { halPushClip, halSubscribeToClips } from "./hal-sync";
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
      const text = await halReadClipboardText();
      if (text) {
        await halPushClip(halUser.uid, {
          kind: "text",
          text,
          pinned: false,
          originDevice: "browser",
        });
      }
    } catch (err) {
      setHalError((err as Error).message);
    } finally {
      setHalBusy(false);
    }
  }

  async function halHandlePaste(clip: HalClipItem) {
    if (clip.text) await halWriteClipboardText(clip.text);
  }

  if (!halUser) {
    return (
      <main class="hal-popup">
        <h1 class="hal-title">Ctrl+Freak</h1>
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
        <h1 class="hal-title">Ctrl+Freak</h1>
        <button class="hal-link" onClick={halSignOut}>
          sign out
        </button>
      </div>

      <button class="hal-button" onClick={halHandleSyncNow} disabled={halBusy}>
        {halBusy ? "Syncing…" : "Sync clipboard now"}
      </button>
      {halError && <p class="hal-error">{halError}</p>}

      {halClips.length === 0 ? (
        <p class="hal-empty">Nothing synced yet.</p>
      ) : (
        <ul class="hal-clip-list">
          {halClips.map((clip) => (
            <li
              key={clip.id}
              class="hal-clip-item"
              onClick={() => halHandlePaste(clip)}
              title="Click to copy"
            >
              {clip.text}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

render(<HalPopup />, document.getElementById("hal-root")!);
