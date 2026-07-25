# Ctrl+Freak Browser Extension — UI Spec

This is an implementation spec, not a design brief. Every value is concrete. Where a choice existed, it's been made. Implement exactly; deviating is a design decision this document already made for you.

The extension is the desktop client for Ctrl+Freak — same product as the Android app (`docs/ctrl-freak-android-ui-spec.md`), different surfaces. It must feel like the same tool: mono for chrome/structure, sans for content, warm amber accent, cyan secondary, hairline borders on near-black surfaces. Where the extension and Android overlap (the clipboard feed), they should be recognizably the same screen.

The extension has two surfaces with genuinely different constraints:
- **The popup** — a transient ~360px panel that closes on blur. Clipboard history only. Must feel instant.
- **The notes page** — a full browser tab. Sidebar + rich-text editor + attachments. This is the feature-dense surface and where most of the design attention goes.

Dark-only. Single user. No light mode anywhere.

---

## 1. Design tokens

Define these once (`:root` in a shared stylesheet) and reference by name. No raw hex in component CSS.

### Color

```css
:root {
  --bg:            #0E0F17; /* page / popup background */
  --surface:       #171925; /* cards, panels, toolbar, nav */
  --surface-raised:#1E2130; /* hover / active / selected fill */
  --inset:         #0A0B12; /* input fields, wells, code, thumbnails */
  --ink:           #ECEAF3; /* primary text */
  --ink-muted:     #9A9CB3; /* secondary text */
  --ink-faint:     #6B6D84; /* tertiary text, labels, inactive icons */
  --rule:          #2A2C3D; /* borders, dividers */
  --accent:        #E0A75E; /* primary actions, brand, active state */
  --cyan:          #74C7D4; /* links, secondary accent, current-color swatch */
  --danger:        #EA6F67; /* errors, destructive, remove */
}
```

On amber-filled controls, label and icon are `--bg` (dark ink on amber), never white. `--accent` is never used as body text color; it's a fill, a border, or an icon/label on a control.

### Type

Two families. Bundle IBM Plex Mono and IBM Plex Sans with the extension (don't rely on the browser/OS having Plex Mono).

```css
--font-mono: 'IBM Plex Mono', ui-monospace, monospace;
--font-sans: 'IBM Plex Sans', system-ui, sans-serif;
```

| Role | Family | Size | Weight | Letter-spacing | Color | Used for |
|---|---|---|---|---|---|---|
| Wordmark | Mono | 15px | 500 | 0 | ink (+ accent on "freak") | popup + page title |
| Page crumb | Mono | 12px | 400 | 0 | ink-faint | "/ notes" after wordmark |
| Label / eyebrow | Mono | 10px | 400 | 0.08em | ink-faint | section labels, uppercase |
| Meta | Mono | 11px | 400 | 0 | ink-faint | device + time, counts, sizes |
| Chip | Mono | 12px | 400 | 0 | ink-muted / accent | category chips, toolbar dropdowns |
| Button | Mono | 12px | 500 | 0 | bg | primary action labels |
| Sidebar item | Sans | 13px | 400 | 0 | ink / ink-muted | category + note list rows |
| Note title (list) | Sans | 13px | 400 | 0 | ink | note titles in sidebar |
| Editor title | Sans | 20px | 500 | 0 | ink | note title field |
| Body | Sans | 15px | 400 | 0 | ink | note editor content |
| Popup item text | Sans | 13px | 400 | 0 | ink | clipboard text previews |
| Code inline | Mono | 13px | 400 | 0 | cyan on inset | `code` spans in note body |

If it's chrome, structure, a label, count, timestamp, or file size → Mono. If a human wrote it as content → Sans. Body/editor line-height is 1.6.

### Shape

```css
--radius-sm:  6px;  /* buttons, chips, toolbar buttons, thumbnails, attachment cards */
--radius-card:10px; /* clipboard items, panels */
--radius-lg: 14px;  /* popup outer frame, dialogs */
```

Borders: `1px solid var(--rule)`, except amber-accented (pinned / selected / active) which is `1px solid var(--accent)`, and error which is `1px solid var(--danger)`.

### Elevation & shadow

Not flat, not heavy. One soft shadow for genuinely floating things only:

```css
--shadow-float: 0 8px 24px rgba(0,0,0,0.35);
```

Apply to: the popup frame itself, dropdown menus (block-type picker, overflow menu), and toasts. Everything that sits flat on the page — sidebar rows, list cards, attachment cards, the toolbar — gets a hairline border and no shadow. Shadowing flat elements muddies the surface.

### Spacing scale

Use these; don't invent intermediates. `4 · 6 · 8 · 9 · 11 · 12 · 16 · 20` px.

- Popup padding: `13px` horizontal.
- Notes page top bar: `11px` vertical, `16px` horizontal.
- Sidebar width: `210px`, padding `12px`.
- Editor content padding: `16px` vertical, `20px` horizontal.
- List/card gaps: `8px`.
- Min hit target: `28px` for dense toolbar/icon buttons (they're mouse-targeted, not touch — 28px is acceptable here, unlike Android's 44), `32px` for primary buttons.

### Motion

- State transitions (hover→pressed, chip/toolbar select, color shifts): `120ms ease` (`cubic-bezier(0.4,0,0.2,1)`).
- Button press: translate down `1px` + brightness up (overlay `--ink` at `0.06` alpha). Tactile, not bouncy — no scale bounce, no overshoot.
- Popup must feel instant: no entrance animation on open (it's opened dozens of times a day; an animation reads as lag). Content is simply present.
- Respect `prefers-reduced-motion`: transitions → instant.

---

## 2. The popup

Fixed width `360px`. Variable height, no horizontal scroll ever. **Closes the instant it loses focus** — this is a hard browser constraint. Design rules that follow from it:

- No multi-step flows, no confirm-then-click-outside dialogs, nothing that needs a second surface.
- No OAuth popup launched from inside (handled at code level — just don't design as if a second window is fine).
- Destructive actions (unpin is not destructive; there's no delete in the popup) resolve in one click.
- Everything actionable is visible in one glance — no scroll to reach the primary action.

Outer frame: `--bg`, `1px solid --rule`, `--radius-lg`, `--shadow-float`, overflow hidden.

### 2.1 Sign-in gate (popup, unauthenticated)

Centered column, `30px` vertical padding.
- Wordmark: `ctrl+` in ink, `freak` in accent, Mono 15px/500.
- Tagline: Sans 13px ink-muted, **"Your clipboard and notes, on every device."**
- Google button: `--surface` fill, `1px solid --rule`, `--radius-sm`, height `36px`, leading Google glyph in cyan, Mono 12px ink: **"Continue with Google"**.
- On failure: Danger helper below, **"Couldn't sign in. Try again."**

Sign-in is a neutral surface control, not the amber primary — amber is reserved for the sync action so it stays meaningful.

### 2.2 Clipboard feed (popup, authenticated — the default and only popup content)

This is conceptually identical to the Android Clipboard tab (`android spec §3.2`) — same data, same interactions, compressed.

Top bar (`11px 13px`, `1px --rule` bottom border):
- Wordmark left.
- Right: sync status + notes link, Mono 11px ink-faint — a cyan filled dot + "synced" (or ink-faint "offline"), then " · ", then an external-link glyph + "notes" that **opens the full notes tab** (the only navigation the popup offers).

Body (`11px 13px`):
1. **Sync button** — primary amber, full-width, height `38px`, `--radius-sm`, leading clipboard-plus glyph, Mono 12px: **"Sync clipboard now"**. Reads the current clipboard and adds one item to the feed.
2. **Count label** — Mono 10px ink-faint, `5px` below: **"12 recent · pinned first"** (live count).
3. **Feed** — pinned items first (any number), then most-recent-first up to 50 total. `8px` gaps.

Each item card (`--surface`, `1px --rule`, `--radius-card`, `9px 10px`):
- Text items: preview left (max 2 lines, ellipsize). Code-like content (single line, no spaces beyond tokens) renders Mono 12px; prose renders Sans 13px. Pin toggle top-right.
- Image items: a `30px` `--inset` thumbnail well (`--radius-sm`, `1px --rule`) with a cyan photo glyph (or the real thumbnail if available), then Sans 13px ink-muted "[image]".
- Meta row, `6px` below: Mono 11px ink-faint — device glyph (desktop/mobile) + origin + " · " + short relative time ("4m", "22m", "1h").
- **Click the card** (not the pin) → copy content back to the clipboard → toast "Copied" (§5).
- **Click the pin** → toggle pinned; item moves to/from the pinned group with a `120ms` fade.

Empty state: centered, `30px` padding — a `--surface` well with an accent clipboard-off glyph, Sans 13px ink **"Nothing synced yet"**, Sans 12px ink-muted **"Copy something, then click Sync — it'll show up here and on your phone."**

### 2.3 Popup component states

Primary button (sync):

| State | Background | Label/icon | Extra |
|---|---|---|---|
| Default | accent | bg | — |
| Hover | accent + ink@0.06 overlay | bg | — |
| Pressed | accent + ink@0.06 | bg | translate-y +1px |
| Disabled | accent@0.4 | bg@0.6 | not interactive |
| Loading | accent | bg | label → 14px spinner |

Item card:

| State | Background | Border |
|---|---|---|
| Default | surface | 1px rule |
| Hover | surface-raised | 1px rule |
| Pressed | surface-raised | 1px rule |
| Pinned | surface | 1px accent |
| Pinned hover | surface-raised | 1px accent |

Pin toggle: unpinned = ink-faint; unpinned hover = ink-muted; pinned = accent; pinned hover = accent + ink@0.06 overlay. Pin outline glyph in both states — color carries meaning.

---

## 3. The notes page (full browser tab)

Desktop-scale surface. Use the width deliberately — don't stretch a narrow layout. Three-part structure: top bar, a `210px` sidebar, and a flexible editor pane.

Page background `--bg`. Overall it fills the browser viewport; the sidebar scrolls independently of the editor if either overflows (this is the one place nested scroll is correct — a desktop two-pane layout).

### 3.1 Top bar

`11px 16px`, `1px --rule` bottom border.
- Left: wordmark `ctrl+freak` (accent on "freak") + Mono 12px ink-faint crumb **"/ notes"**.
- Right: primary amber button, height `32px`, `0 12px` padding, leading plus glyph, Mono 12px: **"New note"**.

### 3.2 Sidebar (`210px`, `1px --rule` right border, `12px` padding)

Three stacked blocks with `12px` gaps.

**Search** — `--inset` fill, `1px --rule`, `--radius-sm`, `7px 9px`. Leading search glyph (ink-faint), placeholder Mono 12px ink-faint **"Search notes"**. On focus: border → accent. Live-filters the note list.

**Categories** — eyebrow label Mono 10px ink-faint **"CATEGORIES"**, then rows (`6px 8px`, `--radius-sm`). Each row: an `8px` category color dot + name (Sans 13px) + right-aligned count (Mono 11px ink-faint).
- Selected category: `--surface-raised` fill, name in ink.
- Unselected: transparent fill, name in ink-muted.
- Hover: `--surface-raised` fill.
- First row is **"All notes"** with the accent dot, selected by default.
- Category dot palette (fixed, drawn from tokens to stay in-register across surfaces — same set as the Android spec): accent, cyan, ink-muted, plus `#B48EAD` (muted mauve) and `#8FBCBB` (muted teal) for additional categories. Never bright or pastel.

**Recent** — eyebrow **"RECENT"** (with a `1px --rule` top divider above it, `10px` padding-top), then compact note rows: a `6px` category dot + title (Sans 13px) + a pin glyph pushed right (accent if pinned, else omitted). Pinned notes float to the top of this list.
- Selected note (open in editor): `--surface` fill, `1px --rule`, ink title.
- Unselected: transparent, ink-muted title.
- Hover: `--surface-raised`.

### 3.3 Editor pane (flex-fills remaining width)

Top to bottom:

**Title field** — bare input, transparent background, no border, Sans 20px/500 ink, `16px 20px 8px` padding. Placeholder **"Untitled note"** (ink-faint). It reads as a heading, not a boxed input; no border even on focus (the cursor is enough).

**Meta row** (`0 20px 12px`): the note's category as a chip (see chip states §3.5) + Mono 11px ink-faint **"edited 8m ago"**, then pushed to the right: a pin toggle button and an overflow (dots) button, both `28px` toolbar buttons.
- Overflow menu (floating, `--surface`, `--shadow-float`, `--radius-sm`): **"Duplicate"**, **"Export as .md"**, and **"Delete note"** (danger text). Delete opens the confirm dialog (§3.6).

**Toolbar** — the piece the prior build got wrong. See §3.4 in full.

**Body** — the rich-text editor surface, `16px 20px`, flex-fills. Sans 15px ink, line-height 1.6. Inline `code` spans render Mono 13px cyan on an `--inset` pill (`1px 5px`, `--radius: 4px`). This is a real rich-text editor (Tiptap) — headings, bold/italic/underline, links, lists, text color, alignment, font size all render live here.

**Attachments** — see §3.7. Sits at the bottom of the editor pane with a `1px --rule` top divider.

### 3.4 The rich-text toolbar (design this properly — it's the headline fix)

The old build was a flat row of identical small mono buttons with no grouping and no active-state feedback. Replace with a **grouped toolbar**: logical clusters separated by `1px --rule` vertical dividers, unequal visual weight, and clear active-state indication for the current selection's formatting.

Toolbar container: `--surface`, `1px --rule` top and bottom borders, `7px 11px` padding, flex row, vertically centered.

Four groups, left to right, each a flex row of `3px`-gapped buttons with `9px` horizontal padding and a `1px --rule` right divider (last group no divider):

**Group 1 — block type.** A single dropdown button (not icons): Mono 12px label showing the current block ("Body", "Heading 1", "Heading 2", "Heading 3", "Quote", "Code block") + a chevron. Click opens a floating menu (`--surface`, `--shadow-float`) listing the options, each previewed at its actual size/weight. This replaces cramming heading buttons into the icon row.

**Group 2 — inline text.** Icon buttons (`28px`, `--radius-sm`, 16px glyph): bold, italic, underline, link, text-color. The text-color button carries a `2px` cyan underline swatch at its base showing the current color; clicking opens a small swatch popover (a restrained palette: ink, accent, cyan, danger, ink-muted — no full color wheel; this is a dev-note tool, not a design app).

**Group 3 — lists & alignment.** Icon buttons: bullet list, numbered list, and an alignment button that cycles/opens left/center/right. Font-size lives here too as a compact Mono stepper (`12px` value with up/down chevrons) if you keep font-size — but consider whether it earns its place; if the block-type dropdown covers the real needs, font-size can be cut. Recommended: keep it, as a dropdown with `S / M / L` rather than arbitrary px.

**Group 4 — insert.** A single labeled button (not a bare icon): paperclip glyph + Mono 12px **"Attach"**. Opens the file picker; also the drop/paste target (§3.7).

Not every control has equal weight: the two dropdowns (block type, attach) are labeled and wider; the inline-format icons are quiet until active; alignment and color are secondary. The eye lands on structure first.

### 3.5 Toolbar button states (applies to every toolbar/icon button)

| State | Background | Icon/text | Border |
|---|---|---|---|
| Default | transparent | ink-muted | 1px transparent |
| Hover | surface-raised | ink | 1px transparent |
| Pressed | surface-raised | ink | 1px transparent |
| **Active** (format applied to current selection) | surface-raised | **accent** | **1px accent** |
| Disabled | transparent | ink-faint@0.5 | 1px transparent |

Active state is the critical fix: when the cursor sits in bold text, the bold button shows the amber pressed-and-outlined state so the user can see current formatting at a glance. Multiple can be active at once (bold + italic + left-aligned). The block-type dropdown shows its active block as its label rather than an outline.

Chip (category, in editor meta and as filter):

| State | Background | Text | Border |
|---|---|---|---|
| Inactive | transparent | ink-muted | 1px rule |
| Hover | surface-raised | ink-muted | 1px rule |
| Active/selected | surface-raised | accent | 1px accent |

Chips carry a leading category color dot (`7px`).

Text field (title, search, link-entry):

| State | Background | Border |
|---|---|---|
| Default | inset (search) / transparent (title) | 1px rule / none |
| Focused | inset / transparent | 1px accent / none |
| Error | inset | 1px danger + danger helper below |

Cursor color accent throughout.

### 3.6 Delete confirm dialog

Note deletion needs confirmation — and unlike the popup, the notes tab can host a dialog safely.

Centered overlay (`rgba(0,0,0,0.45)` scrim). Dialog: `--surface`, `--radius-lg`, `--shadow-float`, `20px` padding, `~320px` wide.
- Title Mono 15px ink: **"Delete note?"**
- Body Sans 13px ink-muted: **"This can't be undone."**
- Right-aligned buttons: **"Cancel"** (neutral — transparent, `1px --rule`, ink-muted) and **"Delete"** (danger — transparent, `1px --danger`, danger text; hover fills `--danger` with bg text).

### 3.7 Attachments

Files, images, and PDFs attach to a note. Upload via the toolbar "Attach" button, drag-drop onto the body, or paste. Replace the old flat file-chip row with proper cards.

Attachment section (bottom of editor, `12px 20px 16px`, `1px --rule` top divider):
- Eyebrow Mono 10px ink-faint: **"ATTACHMENTS · 2"** (live count; hide the whole section when zero).
- Cards in a wrapping flex row, `9px` gaps. Each card (`--surface`, `1px --rule`, `--radius-sm`, `8px 11px 8px 8px`):
  - A `32px` `--inset` type-glyph well (`--radius-sm`, `1px --rule`): PDF → danger file-type-pdf glyph; image → cyan photo glyph (or real thumbnail); other → ink-muted file glyph.
  - Name (Sans 12px ink, ellipsize at ~140px) over size (Mono 10px ink-faint, "240 KB").
  - A remove `x` (ink-faint, 14px) at the right; hover → danger.

Attachment states:

| State | Background | Border |
|---|---|---|
| Default | surface | 1px rule |
| Hover | surface-raised | 1px rule |
| Uploading | surface | 1px rule + a `2px` accent progress bar along the bottom edge; size text → "uploading…" (Mono 10px accent) |
| Failed | surface | 1px danger; size text → "failed — retry" (Mono 10px danger, clickable) |

Drop target: while dragging a file over the editor, the body area shows a `1px dashed --accent` inset outline and a centered Mono 12px ink-muted hint **"Drop to attach"**.

### 3.8 Notes-page empty states

**No note selected** (sidebar has notes, editor pane empty): centered in the editor pane — an accent note glyph well, Sans 15px ink **"Pick a note to start"**, Sans 13px ink-muted **"Or click New note to write one."**

**No notes at all**: sidebar shows only "All notes (0)"; editor pane centered — accent note glyph, Sans 15px ink **"No notes yet"**, Sans 13px ink-muted **"Click New note to write your first. Pick a category to keep things sorted."**

---

## 4. Cross-surface consistency

The clipboard feed exists in both the popup and the Android app. They must stay recognizably identical: same card shape, same pin language (amber border + amber pin), same meta line (device glyph + origin + relative time), same "pinned first" ordering, same "Copied" confirmation. A user glancing between phone and browser should see one product.

Where they legitimately differ: the popup is denser (smaller cards, shorter time strings, mouse hover states instead of touch press) and transient (no bottom nav — the popup is clipboard-only; notes live in a separate tab reached via the "notes" link).

---

## 5. Toasts (confirmation experiences)

Custom toast, not a browser/Material default. `--surface` fill, `1px --rule`, `--radius-sm`, `--shadow-float`, Sans 13px ink, a leading glyph in the relevant accent. In the popup it slides down from just under the top bar; on the notes page it appears bottom-center. `2.2s` auto-dismiss, `120ms` in/out.

| Trigger | Glyph / color | Copy |
|---|---|---|
| Click clipboard item to copy back | check, accent | **"Copied"** |
| Sync clipboard now → item added | check, accent | **"Synced 1 item"** |
| Sync but clipboard empty | alert-circle, ink-muted | **"Clipboard's empty — nothing to sync"** |
| Note saved (autosave settle) | check, accent | **"Saved"** |
| Note deleted | trash, ink-muted | **"Note deleted"** + "Undo" action (Mono 12px accent) |
| Attachment added | check, accent | **"Attached"** |
| Attachment upload failed | alert-circle, danger | **"Upload failed — try again"** |
| Sync failure | alert-circle, danger | **"Couldn't sync — check your connection"** |

Copy stays consistent with the action that produced it (the button says "Sync", the toast says "Synced"). No exclamation marks, sentence case, no "successfully".

---

## 6. Quality floor (don't skip)

- Every interactive element has all its states wired (§2.3, §3.5, §3.7). Missing hover/pressed/active/disabled states were the prior build's biggest gap — the active toolbar state especially.
- Focus-visible: every button, chip, field, and toolbar control shows a visible focus ring (a `2px` accent outline, offset `2px`) for keyboard users.
- Keyboard: the toolbar is arrow-key navigable; the block-type and font-size dropdowns open with Enter/Space; the editor supports the standard shortcuts (Cmd/Ctrl-B/I/U, Cmd/Ctrl-K for link).
- The popup never triggers anything that survives blur; test that no menu, dialog, or picker is designed to outlive the popup.
- `prefers-reduced-motion` honored (transitions → instant, no popup entrance).
- Every icon-only button has an `aria-label` ("Pin item", "Bold", "Attach file", "Delete note", "Remove attachment").
- Popup: hard `360px` width, never horizontally scrolls, primary action always visible without scroll.
- No Material/Bootstrap component defaults; no extension-template cramped afterthought look.

---

## 7. What NOT to build (in scope for this pass)

- No light mode on either surface.
- No popup interaction that requires surviving focus loss.
- No full color-wheel in the text-color picker — the restrained token swatch set only.
- No consumer-app illustrations, pastels, or mascot energy.
- Don't reinvent the palette — the amber/cyan/mono+sans identity carries from Android; refine scale and weight, not the core tokens.
