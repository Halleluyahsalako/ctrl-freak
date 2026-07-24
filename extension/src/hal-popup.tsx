import { render } from "preact";
import { useState } from "preact/hooks";
import type { HalClipItem } from "@shared/schema";

function HalPopup() {
  const [halClips] = useState<HalClipItem[]>([]);

  return (
    <main class="hal-popup">
      <h1 class="hal-title">Ctrl+Freak</h1>
      {halClips.length === 0 ? (
        <p class="hal-empty">Nothing synced yet — sign in to get started.</p>
      ) : (
        <ul class="hal-clip-list">
          {halClips.map((clip) => (
            <li key={clip.id} class="hal-clip-item">
              {clip.text}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

render(<HalPopup />, document.getElementById("hal-root")!);
