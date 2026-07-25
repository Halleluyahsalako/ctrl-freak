// This file is the single source of truth for what a "clip", "note", and
// "category" look like — both the extension and the Android app read and
// write data in this shape, so a change made on one client always makes
// sense on the other. See ARCHITECTURE.md §2.
//
// Naming convention for this project: every type/class/function/variable
// carries a "hal" prefix (see ARCHITECTURE.md §1). Firestore collections
// follow the same rule: hal_clipItems, hal_notes, hal_categories.

export type HalTimestamp = number;

export type HalDevice = "android" | "browser";

export type HalClipKind = "text" | "image" | "file" | "code";

// a single synced clipboard entry — text or a Drive-backed image/file
export interface HalClipItem {
  id: string;
  kind: HalClipKind;
  text?: string;                // inline for text/code
  driveFileId?: string;         // set for image/file kinds
  categoryId?: string;
  pinned: boolean;
  createdAt: HalTimestamp;
  originDevice: HalDevice;
}

export interface HalAttachment {
  driveFileId: string;
  name: string;
}

// longer-form notes, separate from the transient clipboard feed
export interface HalNote {
  id: string;
  title: string;
  body: string;                 // markdown
  categoryId?: string;
  attachments: HalAttachment[];
  updatedAt: HalTimestamp;
}

export interface HalCategory {
  id: string;
  name: string;
  color: string;
}
