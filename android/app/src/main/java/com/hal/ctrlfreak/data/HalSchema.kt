package com.hal.ctrlfreak.data

// Mirrors shared/schema.ts field-for-field — see ARCHITECTURE.md §2.
// Firestore collections: hal_clipItems, hal_notes, hal_categories, under
// /users/{uid}/... (see ../../../../../firestore.rules at the repo root).
//
// kind/originDevice are plain Strings, not Kotlin enums — the TS side
// defines these as string-literal unions ("text" | "image" | ...), and
// Firestore's default POJO mapping serializes a Kotlin enum as its
// `.name` (e.g. "TEXT", uppercase). Using a real enum here would write
// values the extension's `clip.kind === "text"` checks would never
// match — same collections, silently incompatible data. Constants below
// exist so call sites don't have to spell the strings out by hand.

object HalClipKind {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val FILE = "file"
    const val CODE = "code"
}

object HalDevice {
    const val ANDROID = "android"
    const val BROWSER = "browser"
}

data class HalClipItem(
    val id: String = "",
    val kind: String = HalClipKind.TEXT,
    val text: String? = null,
    val driveFileId: String? = null,
    val categoryId: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val originDevice: String = HalDevice.ANDROID,
)

data class HalAttachment(
    val driveFileId: String = "",
    val name: String = "",
)

data class HalNote(
    val id: String = "",
    val title: String = "",
    val body: String = "", // markdown
    val categoryId: String? = null,
    val attachments: List<HalAttachment> = emptyList(),
    val pinned: Boolean = false,
    val updatedAt: Long = 0,
)

data class HalCategory(
    val id: String = "",
    val name: String = "",
    val color: String = "",
)
