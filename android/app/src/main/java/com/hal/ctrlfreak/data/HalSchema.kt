package com.hal.ctrlfreak.data

// Mirrors shared/schema.ts field-for-field — see ARCHITECTURE.md §2.
// Firestore collections: hal_clipItems, hal_notes, hal_categories, under
// /users/{uid}/... (see ../../../../../firestore.rules at the repo root).
//
// Not yet wired to Firestore — no Firebase dependency added yet, so this
// compiles standalone. That's the next increment, not done tonight.

enum class HalDevice { ANDROID, BROWSER }

enum class HalClipKind { TEXT, IMAGE, FILE, CODE }

data class HalClipItem(
    val id: String = "",
    val kind: HalClipKind = HalClipKind.TEXT,
    val text: String? = null,
    val driveFileId: String? = null,
    val categoryId: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val originDevice: HalDevice = HalDevice.ANDROID,
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
    val updatedAt: Long = 0,
)

data class HalCategory(
    val id: String = "",
    val name: String = "",
    val color: String = "",
)
