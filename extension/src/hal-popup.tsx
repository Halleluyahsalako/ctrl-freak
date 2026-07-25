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
import { useHalToast, HalToast } from "./hal-toast";
import {
  HalIconPin,
  HalIconExternalLink,
  HalIconImage,
  HalIconDesktop,
  HalIconMobile,
  HalIconClipboardPlus,
  HalIconClipboardOff,
} from "./hal-icons";
import type { HalClipItem } from "@shared/schema";

// docs/ctrl-freak-extension-ui-spec.md §2

const HAL_CODE_LIKE = /^\S+$/; // single unbroken token — a URL, path, var name

function halRelativeTime(createdAt: number): string {
  const minutes = Math.floor((Date.now() - createdAt) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / (24 * 60))}d`;
}

function HalPopup() {
  const [halUser, setHalUser] = useState<User | null>(null);
  const [halClips, setHalClips] = useState<HalClipItem[]>([]);
  const [halBusy, setHalBusy] = useState(false);
  const [halSignInError, setHalSignInError] = useState<string | null>(null);
  const { toast, show: halToast } = useHalToast();

  useEffect(() => onAuthStateChanged(halAuth, setHalUser), []);

  useEffect(() => {
    if (!halUser) {
      setHalClips([]);
      return;
    }
    return halSubscribeToClips(halUser.uid, setHalClips);
  }, [halUser]);

  async function halHandleSignIn() {
    setHalSignInError(null);
    try {
      await halSignIn();
    } catch {
      setHalSignInError("Couldn't sign in. Try again.");
    }
  }

  async function halHandleSyncNow() {
    if (!halUser) return;
    setHalBusy(true);
    try {
      const read = await halReadClipboardSmart();
      if (!read) {
        halToast("Clipboard's empty — nothing to sync", "neutral");
        return;
      }

      if (read.kind === "text") {
        await halPushClip(halUser.uid, {
          kind: "text",
          text: read.text,
          pinned: false,
          originDevice: "browser",
        });
      } else {
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
      }
      halToast("Synced 1 item", "success");
    } catch {
      halToast("Couldn't sync — check your connection", "error");
    } finally {
      setHalBusy(false);
    }
  }

  async function halHandleTogglePin(e: Event, clip: HalClipItem) {
    e.stopPropagation();
    if (!halUser) return;
    try {
      await halSetClipPinned(halUser.uid, clip.id, !clip.pinned);
    } catch {
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  async function halHandlePaste(clip: HalClipItem) {
    try {
      if (clip.kind === "image" && clip.driveFileId) {
        const accessToken = await halGetValidAccessToken();
        const blob = await halFetchDriveFileBlob(accessToken, clip.driveFileId);
        await halWriteImageToClipboard(blob);
      } else if (clip.text) {
        await halWriteClipboardText(clip.text);
      }
      halToast("Copied", "success");
    } catch {
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  if (!halUser) {
    return (
      <main class="hal-signin">
        <div class="hal-wordmark">
          ctrl+<span class="hal-accent-part">freak</span>
        </div>
        <p class="hal-tagline">Your clipboard and notes, on every device.</p>
        <button class="hal-google-btn" onClick={halHandleSignIn}>
          <span style={{ color: "var(--cyan)" }}>◍</span> Continue with Google
        </button>
        {halSignInError && <p class="hal-helper-error">{halSignInError}</p>}
      </main>
    );
  }

  const halPinned = halClips.filter((c) => c.pinned);
  const halRecent = halClips.filter((c) => !c.pinned);
  const halOrdered = [...halPinned, ...halRecent];

  return (
    <main>
      <div class="hal-topbar">
        <div class="hal-wordmark">
          ctrl+<span class="hal-accent-part">freak</span>
        </div>
        <div class="hal-status">
          <span class="hal-status-dot hal-online" />
          <span>synced</span>
          <span>·</span>
          <button
            class="hal-notes-link"
            onClick={() => chrome.tabs.create({ url: chrome.runtime.getURL("hal-notes.html") })}
          >
            <HalIconExternalLink /> notes
          </button>
        </div>
      </div>

      <div class="hal-popup-body">
        <button class="hal-sync-btn" onClick={halHandleSyncNow} disabled={halBusy}>
          {halBusy ? "Syncing…" : (
            <>
              <HalIconClipboardPlus /> Sync clipboard now
            </>
          )}
        </button>
        <p class="hal-count-label">{halClips.length} recent · pinned first</p>

        {halOrdered.length === 0 ? (
          <div class="hal-empty-state">
            <div class="hal-empty-well">
              <HalIconClipboardOff />
            </div>
            <p class="hal-empty-title">Nothing synced yet</p>
            <p class="hal-empty-body">
              Copy something, then click Sync — it'll show up here and on your phone.
            </p>
          </div>
        ) : (
          <ul class="hal-feed">
            {halOrdered.map((clip) => (
              <li
                key={clip.id}
                class={`hal-clip-card ${clip.pinned ? "hal-pinned-card" : ""}`}
                onClick={() => halHandlePaste(clip)}
                title="Click to copy"
              >
                <div class="hal-clip-top">
                  {clip.kind === "image" ? (
                    <div class="hal-clip-image-row">
                      <div class="hal-thumb-well">
                        <HalIconImage />
                      </div>
                      <span style={{ fontSize: "13px", color: "var(--ink-muted)" }}>[image]</span>
                    </div>
                  ) : (
                    <span
                      class={`hal-clip-text ${clip.text && HAL_CODE_LIKE.test(clip.text) ? "hal-code-like" : ""}`}
                    >
                      {clip.text}
                    </span>
                  )}
                  <button
                    class={`hal-pin-btn ${clip.pinned ? "hal-pinned" : ""}`}
                    onClick={(e) => halHandleTogglePin(e, clip)}
                    aria-label={clip.pinned ? "Unpin" : "Pin"}
                    title={clip.pinned ? "Unpin" : "Pin"}
                  >
                    <HalIconPin />
                  </button>
                </div>
                <div class="hal-clip-meta">
                  {clip.originDevice === "android" ? <HalIconMobile /> : <HalIconDesktop />}
                  <span>{clip.originDevice}</span>
                  <span>·</span>
                  <span>{halRelativeTime(clip.createdAt)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <HalToast toast={toast} position="popup" />
    </main>
  );
}

render(<HalPopup />, document.getElementById("hal-root")!);
