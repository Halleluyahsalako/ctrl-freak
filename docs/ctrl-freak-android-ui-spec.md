# Ctrl+Freak Android — Jetpack Compose UI Spec

This is an implementation spec, not a design brief. Every value is concrete. Where a choice existed, it's been made. Implement exactly; deviating is a design decision this document already made for you.

The app is dark-only, single-user, dev-tool register. Match the browser extension's identity: mono for chrome/structure, sans for content, warm amber accent, hairline borders on near-black surfaces.

---

## 1. Design tokens

Define these once (a `Theme.kt` object) and reference by name everywhere. No raw hex in composables.

### Color

```kotlin
object CfColor {
    val Background     = Color(0xFF0E0F17) // page background
    val Surface        = Color(0xFF171925) // cards, panels, nav bar
    val SurfaceRaised  = Color(0xFF1E2130) // hover/pressed/active states
    val Inset          = Color(0xFF0A0B12) // input fields, wells, thumbnails
    val Ink            = Color(0xFFECEAF3) // primary text
    val InkMuted       = Color(0xFF9A9CB3) // secondary text
    val InkFaint       = Color(0xFF6B6D84) // tertiary text, labels, inactive icons
    val Rule           = Color(0xFF2A2C3D) // borders, dividers
    val Accent         = Color(0xFFE0A75E) // primary actions, brand, active state
    val Cyan           = Color(0xFF74C7D4) // links, secondary accent, image glyphs
    val Danger         = Color(0xFFEA6F67) // errors, destructive actions
}
```

`Accent` foreground pairs with `Background` as its text color (dark ink on amber), never white. On amber-filled buttons, label + icon are `Background`.

### Type

Two families. Bundle IBM Plex Mono and IBM Plex Sans as app fonts (don't rely on system fallback — Plex Mono in particular isn't guaranteed on Android).

```kotlin
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal),
                          Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))
val PlexSans = FontFamily(Font(R.font.ibm_plex_sans, FontWeight.Normal),
                          Font(R.font.ibm_plex_sans_medium, FontWeight.Medium))
```

| Role | Family | Size | Weight | Letter-spacing | Color | Used for |
|---|---|---|---|---|---|---|
| Wordmark | Mono | 18sp | Medium | 0 | Ink (+ Accent on "freak") | app title |
| Screen title | Mono | 15sp | Medium | 0 | Ink | top bar labels ("notes", "settings") |
| Label / eyebrow | Mono | 11sp | Normal | 0.06em | InkFaint | section labels, counts, all-lowercase |
| Meta | Mono | 11sp | Normal | 0 | InkFaint | device + time line |
| Nav item | Mono | 10sp | Normal | 0.04em | InkFaint / Accent | bottom nav labels |
| Chip | Mono | 12sp | Normal | 0 | InkMuted / Accent | category filter chips |
| Button | Mono | 13sp | Medium | 0 | Background | primary action labels |
| Body | Sans | 14sp | Normal | 0 | Ink | note content, clipboard previews |
| Body muted | Sans | 13sp | Normal | 0 | InkMuted | empty-state copy, secondary content |
| Editor body | Sans | 15sp | Normal | 0 | Ink | note editor text area |
| List title | Sans | 14sp | Normal | 0 | Ink | note titles |

Rule of thumb: if it's chrome, structure, a label, a count, or a timestamp → Mono. If a human wrote it as content → Sans. Line-height for body/editor text is 1.5×.

### Shape

```kotlin
object CfRadius {
    val Small  = 6.dp  // buttons, chips, thumbnails, snackbar
    val Card   = 10.dp // clipboard items, note rows
    val Large  = 14.dp // bottom sheets, dialogs, the sign-in logo well
}
```

All borders: `1.dp` solid `Rule`, except the amber-accented pinned/selected border which is `1.dp` solid `Accent`.

### Elevation & shadow

Not flat, not heavy. Raised surfaces get one soft shadow:

```kotlin
Modifier.shadow(
    elevation = 12.dp,
    shape = RoundedCornerShape(CfRadius.Card),
    ambientColor = Color.Black.copy(alpha = 0.35f),
    spotColor = Color.Black.copy(alpha = 0.35f)
)
```

Apply this only to genuinely floating things: the sync button, bottom sheets, dialogs, snackbar. List cards sit on the page with a hairline border and **no** shadow — the border defines them. Don't shadow every card or the screen turns muddy.

### Spacing scale

Use these; don't invent intermediate values.

`4 · 8 · 10 · 12 · 14 · 16 · 24 · 30` dp.

- Screen horizontal padding: `14.dp`
- Gap between list items: `9.dp`
- Card internal padding: `10.dp` vertical, `11.dp` horizontal
- Min tap target: `44.dp` (pin toggles, nav items, chips — pad to reach it even if the visual is smaller)

### Motion

- State transitions (hover→pressed, chip select, color shifts): `120ms`, `FastOutSlowInEasing`.
- Press feedback on buttons: translate down `1.dp` + brightness up (overlay `Ink` at `0.06f` alpha). Tactile, not bouncy — no scale spring, no overshoot.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE`; if reduced-motion, drop transitions to instant.

---

## 2. Component states

Every interactive element defines four states. Missing states are the current build's core failure.

### Primary button ("Sync clipboard now")

| State | Background | Label/icon | Extra |
|---|---|---|---|
| Default | Accent | Background | shadow (12dp) |
| Pressed | Accent + Ink@0.06 overlay | Background | translate-y +1dp |
| Disabled | Accent@0.4 | Background@0.6 | no shadow |
| Loading | Accent | Background | label → 14dp spinner (Background tint), non-interactive |

Full-width, height `44.dp`, radius `Small`, centered mono label with a leading `clipboard-plus`-style icon.

### List card (clipboard item / note row)

| State | Background | Border |
|---|---|---|
| Default | Surface | 1dp Rule |
| Pressed | SurfaceRaised | 1dp Rule |
| Pinned | Surface | 1dp Accent |
| Pinned + pressed | SurfaceRaised | 1dp Accent |

Pin state is the border color; press state is the fill. They compose — a pinned card still shifts fill on press while keeping its amber border.

### Pin toggle (icon button, in every card)

`44.dp` tap target, icon visually `15sp`.

| State | Icon color |
|---|---|
| Unpinned default | InkFaint |
| Unpinned pressed | InkMuted |
| Pinned | Accent |
| Pinned pressed | Accent, overlay Ink@0.06 |

Use a pin outline glyph for both states; color carries the meaning, not fill vs. outline. Cross-fade `120ms` on toggle.

### Category chip

Height `28.dp` (tap target padded to 44), radius `Small`, mono 12sp, horizontal padding `9.dp`.

| State | Background | Text | Border |
|---|---|---|---|
| Inactive | transparent | InkMuted | 1dp Rule |
| Inactive pressed | SurfaceRaised | InkMuted | 1dp Rule |
| Active | SurfaceRaised | Accent | 1dp Accent |

Single-select for the filter row. "all" is the default active chip.

### Text field (note title, note body, editor)

| State | Background | Border | Text |
|---|---|---|---|
| Default | Inset | 1dp Rule | Ink |
| Focused | Inset | 1dp Accent | Ink |
| Placeholder | Inset | 1dp Rule | InkFaint |
| Error | Inset | 1dp Danger | Ink; helper text Danger below |

Cursor color: Accent. Radius `Small` for single-line, `Card` for the multi-line body.

---

## 3. Screens

Navigation: two top-level destinations via a bottom nav bar. They must read as distinct — that's the single biggest gap in the current build. Bottom nav is `Surface`, `1dp Rule` top border, two items each `Mono 10sp` label under an icon, active = Accent icon+label, inactive = InkFaint.

### 3.1 Sign-in

Shown first if unauthenticated. Full-screen, centered column on `Background`.

- Wordmark: `ctrl+` in Ink, `freak` in Accent, Mono 18sp Medium.
- Tagline below, Sans 13sp InkMuted, two lines: **"Your clipboard and notes, on every device."**
- Google sign-in button: `Surface` fill, `1dp Rule` border, radius `Small`, height `44.dp`. Leading Google glyph in Cyan, then Mono 12sp Ink: **"Continue with Google"**. (This is not the amber primary button — sign-in is a neutral surface control, amber is reserved for the sync action.)
- On tap → loading state (button label → spinner). On failure → Danger helper text below button: **"Couldn't sign in. Try again."**

Vertical gaps: 16dp between wordmark, tagline, button. Column vertically centered.

### 3.2 Clipboard (default screen)

Top bar: wordmark left, a small sync-status meta right (Mono 11sp InkFaint, `desktop` icon + "synced" / "offline").

Below top bar, in order:
1. **Sync button** (primary, full-width, `14dp` horizontal margin, `12dp` top margin).
2. **Count label**, Mono 11sp InkFaint, `6dp` below button: **"50 recent · pinned first"** (number is live count; drop to "12 recent · pinned first" etc.).
3. **The feed** — a `LazyColumn`, pinned items first (any number, unaffected by the cap), then most-recent-first up to 50 total unpinned. `9dp` gaps, `14dp` horizontal padding.

Each clipboard card:
- Top row: preview text (Sans 14sp Ink, max 3 lines, ellipsize) on the left; pin toggle top-right aligned to first line.
- Image items: instead of text, a `34.dp` `Inset` thumbnail well (radius Small, 1dp Rule) with a Cyan photo glyph, followed by Sans 13sp InkMuted "[image]". If a real thumbnail is available, render it in the well; otherwise the glyph.
- Meta row, `7dp` below: Mono 11sp InkFaint — device icon (`desktop` / `mobile`) + origin + " · " + relative time ("2m ago", "1h ago", "yesterday").
- **Tap the card** (not the pin) → copy content back to phone clipboard → snackbar "Copied" (see §4).
- **Tap the pin** → toggle pinned, card animates to/from the pinned group.

Empty state (no items): centered column, `30dp` vertical padding.
- `44.dp` `Surface` well, radius Card, 1dp Rule, Accent `clipboard-off` glyph 20sp.
- Sans 14sp Ink: **"Nothing synced yet"**
- Sans 13sp InkMuted, 1–2 lines: **"Copy something, then tap Sync — it'll show up here and in your browser extension."**

### 3.3 Notes

Top bar: Mono 15sp "notes" left, Accent `plus` icon right (→ new note editor).

Below: **category filter row** — horizontally scrolling chips, `14dp` left padding, `6dp` gaps, `10dp` vertical padding. First chip "all" (active by default), then one chip per category (lowercase mono). Horizontal scroll, no wrap — this replaces the extension's sidebar, which doesn't fit a phone.

**Note list** — LazyColumn, pinned first then alphabetical or recent (pick recent-edited-first), `9dp` gaps, `14dp` horizontal padding. Each row:
- Left: `8.dp` category color dot + note title (Sans 14sp Ink).
- Right: pin toggle (same component/states as clipboard).
- Tap row → open editor.

Category dots use a fixed small palette drawn from the token set so they stay in-register — assign per category, e.g. Accent, Cyan, InkMuted, plus you may add `Color(0xFFB48EAD)` (muted mauve) and `Color(0xFF8FBCBB)` (muted teal) if more than three categories exist. Never introduce bright/pastel category colors.

Empty state (no notes): same layout as clipboard empty state.
- Accent `note-off`-style glyph.
- **"No notes yet"**
- **"Tap + to write your first note. Pick a category to keep things sorted."**

### 3.4 Note editor

Full screen (or large bottom sheet — full screen is cleaner for text entry). `Background`.

- Top bar: back chevron (InkMuted) left; "Save" text button (Mono 13sp Accent) right; between them nothing (title lives in the body). Delete lives in an overflow (`dots-vertical`, InkMuted) → confirm dialog.
- **Title field**: single-line text field, Sans 15sp, placeholder **"Title"** (InkFaint). No visible box border until focused — feels like a heading; on focus show the Accent underline/border.
- **Category picker**: a row directly under title — the same chips as the filter, single-select, but here they *set* the note's category. Label it with a Mono 11sp InkFaint eyebrow: **"category"**.
- **Body**: multi-line text field, Sans 15sp Ink, line-height 1.5, `Inset` background, radius Card, fills remaining height, placeholder **"Start typing…"** (InkFaint). Plain text only — no formatting toolbar (rich text is explicitly out of scope; design the plain-text version well rather than stubbing a toolbar).
- Pin toggle for the note lives in the top bar next to overflow, same pin component.

Delete confirm dialog: `Surface` fill, radius Large, shadow. Title Mono 15sp Ink "Delete note?"; body Sans 13sp InkMuted "This can't be undone."; buttons — "Cancel" (neutral, InkMuted text) and "Delete" (Danger text). Right-aligned.

---

## 4. Snackbars & toasts (confirmation experiences)

Custom snackbar, not the Material default (default breaks the identity). `Surface` fill, `1dp Rule` border, radius Small, shadow (12dp), Sans 13sp Ink, a leading glyph in the relevant accent. Slides up from above the bottom nav, `2.2s` auto-dismiss, `120ms` in/out.

| Trigger | Glyph / color | Copy |
|---|---|---|
| Tap clipboard item to copy back | `check`, Accent | **"Copied"** |
| Sync clipboard now → item added | `check`, Accent | **"Synced 1 item"** |
| Sync but clipboard empty | `alert-circle`, InkMuted | **"Clipboard's empty — nothing to sync"** |
| Note saved | `check`, Accent | **"Saved"** |
| Note deleted | `trash`, InkMuted | **"Note deleted"**, with "Undo" action (Mono 12sp Accent) if you support undo |
| Share-sheet landing (see §5) | `check`, Accent | **"Added to clipboard"** |
| Copy-back / sync failure | `alert-circle`, Danger | **"Couldn't sync — check your connection"** |

Copy stays consistent with the action that produced it (the button says "Sync", the toast says "Synced"). No exclamation marks, sentence case, no "successfully".

---

## 5. Share-sheet target

Register the app as a share target for `text/plain` and `image/*`. No dedicated screen.

When the user shares into Ctrl+Freak:
1. If unauthenticated → route to sign-in first, then complete the share after auth.
2. If authenticated → add the shared content to the clipboard history as a new item (origin = "phone", same as a manual sync), silently in the background.
3. Open the app **on the Clipboard screen** with the new item at the top of the feed (below pins), and fire the **"Added to clipboard"** snackbar (§4).
4. If the app was already open, just insert the item and fire the snackbar — no navigation jump if the user is mid-edit in Notes; instead show the snackbar with a "View" action (Mono 12sp Accent) that switches to Clipboard.

The new item should briefly (`600ms`) hold a `SurfaceRaised` fill before settling to `Surface`, so the user sees where it landed.

---

## 6. Quality floor (don't skip)

- Every interactive element has all four states wired (§2). The current build's "broken" feeling is almost entirely missing pressed/disabled states.
- Keyboard/focus: text fields show the Accent focus border; back gesture works in the editor with an unsaved-changes guard (dialog: "Discard changes?" / "Keep editing" / "Discard").
- Reduced motion honored (transitions → instant).
- Content descriptions on all icon-only buttons ("Pin item", "Sync clipboard", "New note", "Delete note").
- Status bar / nav bar: match `Background`, light icons (dark theme).
- No Material You dynamic color. Lock the palette above; opt out of `dynamicColorScheme`.
- Text scales with system font size but the mono chrome should cap its scale factor so labels don't wrap the nav bar.

---

## 7. What NOT to build (in scope for this pass)

- No light mode.
- No rich-text editor / formatting toolbar.
- No consumer-app illustrations, pastels, or mascot energy.
- No dynamic wallpaper-derived color.
- No single "current clipboard" slot — it's always a history feed.
