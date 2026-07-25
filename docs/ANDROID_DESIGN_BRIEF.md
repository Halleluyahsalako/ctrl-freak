# Ctrl+Freak Android — UI design brief

This is a self-contained brief for designing the Android app's UI. Hand this whole document to a design-focused conversation — it doesn't assume any prior context about this project.

## What this app is

Ctrl+Freak is a personal clipboard-and-notes sync tool between a phone and a computer. It exists to replace "send it to yourself on WhatsApp," which the owner found unreliable and heavy. It already has a working browser extension (Chrome/Edge/Brave) with an established visual identity; this brief is for the Android app to match that identity, not invent a new one. **The current Android app has zero design applied — bare Material3 defaults, unstyled — and reads as broken/unfinished even though it functions.** The goal is a real, intentional design pass, not decoration on top of what exists.

## Who it's for

One user: a developer, self-hosting this for personal use. Dark mode, coding/terminal-adjacent, mathematical aesthetic was the original design brief for the whole project — precise, monospace-flavored, not playful or consumer-app-cute. Think: the visual register of a well-designed dev tool (a terminal, a code editor, Linear, Raycast) rather than a consumer note-taking app.

## Existing visual identity (from the browser extension — match this, don't reinvent)

**Color tokens** (dark theme, the only theme — this app doesn't need a light mode):
- Background: `#0e0f17` (near-black, slight blue cast)
- Surface (cards/panels): `#171925`
- Surface raised (hover/active state): `#1e2130`
- Inset (input fields, wells): `#0a0b12`
- Ink (primary text): `#eceaf3`
- Ink muted (secondary text): `#9a9cb3`
- Ink faint (tertiary/labels): `#6b6d84`
- Rule (borders/dividers): `#2a2c3d`
- Accent (primary actions, brand): `#e0a75e` — a warm amber/gold, NOT a cliché neon green or generic blue
- Cyan (links, secondary accent): `#74c7d4`
- Danger (errors): `#ea6f67`

**Type**: monospace (IBM Plex Mono or system equivalent) for the wordmark/headings/labels; a humanist sans-serif (IBM Plex Sans or system equivalent) for body text and note content. This pairing — mono for structure/chrome, sans for content — is deliberate and should carry over.

**Shape language**: consistent border-radius scale — 6px small elements (buttons, chips), 10px cards, 14px larger containers. Subtle elevation shadows on raised surfaces (not flat design, not heavy skeuomorphism — a soft `0 8px 24px rgba(0,0,0,0.35)` type shadow). Hairline 1px borders using the rule color, not just shadows, to define edges.

**Interaction feel**: hover/press states shift to the raised surface color with a quick (~120ms) transition. Buttons have a subtle brightness increase on hover and a 1px translate-down on press (tactile, not bouncy).

## What the app actually needs to do (functional requirements, not decoration)

Two top-level sections, navigated via a bottom nav bar or similar Android-idiomatic pattern — **these must read as clearly distinct**, which is the single biggest gap in the current build:

### 1. Clipboard (the primary, default screen)

This is a **history feed**, not a single-item display. The core loop: copy something on the computer → click "Sync" in the browser extension → it appears here. Copy something on the phone → tap "Sync" here → it appears in the extension. Each sync action adds ONE item to a running list (up to the last 50) — it does not replace a single "current clipboard" slot.

- A prominent "Sync clipboard now" action (this reads the phone's current clipboard and adds it to the list)
- A list of synced items, most recent first, pinned items pinned to the top. Each item shows: a preview (truncated text, or an "[image]" indicator with thumbnail if feasible), which device it came from, roughly when
- Tap an item to copy it back to the phone's clipboard (with a confirmation toast/snackbar — "Copied")
- A pin toggle per item (tap a pin icon to keep it at the top, unaffected by the 50-item cap ideally)
- Empty state: explain the sync gesture in one or two sentences, don't just show blank space
- This screen should make it obvious this is a *history*, not a single value — visual weight should go to the list, not just the sync button

### 2. Notes

Structured, longer-form content — explicitly separate from clipboard history, not a variant of it.

- A category filter (chips or a sidebar drawer — phone screens are narrow, so probably horizontal scrolling chips at the top rather than a permanent sidebar, which is the browser extension's pattern but doesn't fit a phone)
- A note list (title + category color dot), tap to open/edit
- An editor: title field, category picker, body. The body is plain text for now (rich text is a known future gap, explicitly out of scope for this design pass — design the plain-text version well rather than mocking up rich text that isn't being built yet)
- Pin toggle per note, same visual language as clipboard pinning
- Create/delete actions

### Also needed somewhere in the flow

- Sign-in screen (Google sign-in) — first thing shown if not authenticated
- Share-sheet target: when the user shares text or an image from another app ("Share to Ctrl+Freak"), it should land in the Clipboard history, same as a manual sync — this needs no dedicated screen, just confirm the target/toast experience is designed for when the app opens as a result of a share

## Explicit anti-goals

- Not a generic Material You dynamic-color app — the palette above is fixed and intentional, not device-wallpaper-derived
- Not a consumer note app aesthetic (no playful illustrations, no pastel colors, no rounded-mascot energy)
- Not light mode
- Don't design rich text editing UI — that's a separate, larger effort not in scope here

## Deliverable format

Whatever this produces should be specific enough to implement directly in Jetpack Compose: concrete spacing values, exact component states (default/hover/pressed/disabled), and real copy for every piece of text (button labels, empty states, section headers) — not lorem ipsum or "[note title]" placeholders. Screen-by-screen descriptions or mockups are both useful; what matters is that every visual decision is explicit enough that implementing it isn't itself a design decision.
