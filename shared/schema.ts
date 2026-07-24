// This file is the single source of truth for what a "clip", "note", and
// "category" look like — both the extension and the Android app read and
// write data in this shape, so a change made on one client always makes
// sense on the other.
//
// Naming convention for this project: every type/class/function/variable
// carries a "hal" prefix (see ARCHITECTURE.md §1) — hence HalClipItem, not
// ClipItem. Firestore collections follow the same rule: hal_clipItems,
// hal_notes, hal_categories.
//
// YOUR TASK: fill in the three interfaces below using the spec in
// ARCHITECTURE.md §2 as the source of truth. Don't just copy it verbatim —
// read what each field is for, then write it out yourself.
//
// TypeScript quick notes, since this is new territory coming from JS:
// - `field: string` is required; `field?: string` is optional.
// - A union of string literals fixes the allowed values, e.g.
//   kind: "text" | "image" means kind can ONLY be one of those two strings.
// - `HalTimestamp` below is a placeholder — use `number` (epoch millis) for
//   now; we'll swap in Firestore's real Timestamp type once the SDK is
//   wired up in Phase 1.

export type HalTimestamp = number;

export interface HalClipItem {
  // TODO: id, kind, text?, driveFileId?, categoryId?, pinned, createdAt, originDevice
}

export interface HalNote {
  // TODO: id, title, body, categoryId?, attachments, updatedAt
}

export interface HalCategory {
  // TODO: id, name, color
}
