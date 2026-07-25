# Ctrl+Freak Browser Extension — UI design brief

Self-contained brief for redesigning the browser extension's UI. Hand this whole document to a design-focused conversation — it doesn't assume prior context about this project. A sibling document (`docs/ctrl-freak-android-ui-spec.md`) already produced a detailed, implemented, approved design for the Android app from a brief like this one — read that file too if it's available to you; the extension needs to feel like the same product on a different surface, not a different app.

## What this app is

Ctrl+Freak is a personal clipboard-and-notes sync tool between a phone and a computer. It replaces "send it to yourself on WhatsApp." The browser extension is the desktop client — there's no separate desktop app; the browser is always open anyway, so the extension covers that role.

## Who it's for

One user: a developer, self-hosting this for personal use. Dark mode, coding/terminal-adjacent, mathematical aesthetic — precise, monospace-flavored, not playful or consumer-app-cute. The visual register of a well-designed dev tool (a terminal, a code editor, Linear, Raycast), not a consumer note app.

## Current state — be honest about this

A design pass already happened once: dark surface tokens, a border-radius scale, shadows, hover states. It's a real improvement over an earlier completely-unstyled version, but the user's own words after seeing it: **"the extension note interface still looks ugly... add some flair, border radius, something intentional."** So: this isn't a blank slate, but it also isn't good enough yet. A superficial pass over the same structure isn't what's wanted — a genuine rethink of layout, hierarchy, and the toolbar in particular is.

## Existing visual identity (shared with the Android app — the Android spec already built on this and the user approved it)

**Color tokens:**
- Background: `#0E0F17`
- Surface (cards/panels): `#171925`
- Surface raised (hover/active): `#1E2130`
- Inset (input fields, wells): `#0A0B12`
- Ink (primary text): `#ECEAF3`
- Ink muted (secondary text): `#9A9CB3`
- Ink faint (tertiary/labels): `#6B6D84`
- Rule (borders/dividers): `#2A2C3D`
- Accent (primary actions, brand): `#E0A75E` — warm amber/gold
- Cyan (links, secondary accent): `#74C7D4`
- Danger (errors): `#EA6F67`

**Type**: monospace for chrome/structure/labels (the wordmark, section headers, meta text, timestamps), a humanist sans-serif for content (note bodies, clipboard text). This pairing is deliberate and already proven on Android — keep it, refine the actual scale/weights if that's what a real pass calls for.

**Shape**: border-radius scale — small (buttons, chips) / card / large (bigger containers). Soft elevation shadows on floating things, hairline 1px borders using the rule color to define edges on things that sit flat on the page.

You're not locked to these exact hex values if there's a principled reason to adjust — but the palette identity (dark, amber accent, cyan secondary, mono+sans pairing) should carry through. Reinventing it from scratch would break consistency with the Android app that already shipped against it.

## The two surfaces (this is the part that's genuinely different from Android)

### 1. The popup (toolbar icon click)

Fixed width ~340–400px, variable height, no horizontal scroll ever possible. It **closes the instant it loses focus** — this is a hard browser constraint, not a design choice, and it means: no multi-step flows, no "are you sure" dialogs that require a second click on something outside the popup, everything actionable in one glance. It also gets opened and closed constantly (many times a day) — it needs to feel instant and light, not like loading a page.

Content: sign-in gate, then a **Clipboard history feed** — sync button, a list of recently synced items (text or image) each showing a preview, origin device, relative time, and a pin toggle. Click an item to copy it back. This is conceptually identical to the Android app's Clipboard tab (see the Android spec §3.2 if available to you) — same data, same interaction, compressed into a much smaller and more transient surface.

### 2. The notes page (opens in a full browser tab)

This is the desktop-scale surface — full browser window width/height, not a cramped popup. Currently: a left sidebar (category filter, note list, search) and a main panel with a rich-text editor (Tiptap: bold/italic/underline/headings/links/lists/text color/font size/text alignment) and file/image/PDF attachments (upload button, paste-to-attach, thumbnail list).

This is genuinely more feature-dense than the Android notes screen (which is deliberately plain-text-only for now) — the rich text toolbar in particular deserves real design attention: right now it's a flat row of small identical-looking mono buttons, which is exactly the kind of "not intentional" the user called out. Consider: logical grouping (text style / alignment+size / insert), clearer active-state indication for the current selection's formatting, and whether every control needs equal visual weight.

Also needs real treatment: the attachment list (currently a flat row of file chips), the category sidebar (currently a plain list), and the overall sidebar/editor proportion and spacing on a wide desktop viewport — this has much more room to work with than a phone screen or a 340px popup, and should use that room deliberately rather than just stretching the same narrow-context layout wider.

## Explicit constraints

- Both surfaces are dark-only. No light mode.
- The popup cannot use any interaction pattern that requires staying open across a page navigation or losing focus (no OAuth popups launched from inside it that would close it — that's already handled at the code level, just don't design around assuming it's fine).
- Real content only in whatever's produced — actual button labels, actual empty-state copy, not lorem ipsum or "[note title]" placeholders.
- Not a generic Material/Bootstrap look. Not a browser-extension-template look (small, cramped, afterthought). This should look like a tool someone chose to build carefully.

## Deliverable format

Same bar as the Android spec that already worked well: concrete color/spacing/type values, exact component states (default/hover/pressed/disabled/focused for every interactive element — the prior pass's biggest gap was missing states), and real copy for every piece of text. Specific enough that implementing it in HTML/CSS isn't itself a design decision.
