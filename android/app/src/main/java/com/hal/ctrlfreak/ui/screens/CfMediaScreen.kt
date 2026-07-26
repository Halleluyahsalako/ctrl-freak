package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalClipKind
import com.hal.ctrlfreak.data.HalNote
import com.hal.ctrlfreak.ui.components.CfEmptyState
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// Same data shape as the extension's Media tab (hal-notes.tsx) — every note
// attachment plus every clip that's ever been an image or file, newest
// first, each opening straight to its real Drive view page.

data class HalMediaItem(
    val driveFileId: String,
    val name: String,
    val size: Long,
    val isImage: Boolean,
    val sortAt: Long,
    val context: String,
    val noteId: String? = null,
    val clipId: String? = null,
)

private val HAL_MEDIA_IMAGE_EXT = Regex("(?i)\\.(png|jpe?g|gif|webp|bmp)$")

fun halMediaItemKey(item: HalMediaItem): String = "${item.noteId ?: "clip"}-${item.clipId ?: ""}-${item.driveFileId}"

fun halBuildMediaItems(notes: List<HalNote>, clips: List<HalClipItem>): List<HalMediaItem> {
    val fromNotes = notes.flatMap { note ->
        note.attachments.map { att ->
            HalMediaItem(
                driveFileId = att.driveFileId,
                name = att.name,
                size = att.size,
                isImage = HAL_MEDIA_IMAGE_EXT.containsMatchIn(att.name),
                sortAt = note.updatedAt,
                context = note.title.ifBlank { "Untitled note" },
                noteId = note.id,
            )
        }
    }
    val fromClips = clips
        .filter { (it.kind == HalClipKind.IMAGE || it.kind == HalClipKind.FILE) && it.driveFileId != null }
        .map { clip ->
            HalMediaItem(
                driveFileId = clip.driveFileId!!,
                name = clip.text?.takeIf { it.isNotBlank() } ?: if (clip.kind == HalClipKind.IMAGE) "Clipboard image" else "Clipboard file",
                size = 0,
                isImage = clip.kind == HalClipKind.IMAGE,
                sortAt = clip.createdAt,
                context = if (clip.originDevice == "android") "from phone" else "from browser",
                clipId = clip.id,
            )
        }
    return (fromNotes + fromClips).sortedByDescending { it.sortAt }
}

private fun halFormatMediaSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

@Composable
fun CfMediaScreen(
    items: List<HalMediaItem>,
    thumbs: Map<String, ImageBitmap>,
    onOpenItem: (HalMediaItem) -> Unit,
    onDownloadItem: (HalMediaItem) -> Unit,
    onDeleteItems: (List<HalMediaItem>) -> Unit,
) {
    var selectMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    fun toggleSelectMode() {
        selectMode = !selectMode
        selectedKeys = emptySet()
    }

    Column(modifier = Modifier.fillMaxSize().background(CfColor.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14, vertical = CfSpace.S12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("media", style = CfType.ScreenTitle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectMode) {
                    TextButton(onClick = { toggleSelectMode() }) {
                        Text("Cancel", style = CfType.Label.copy(color = CfColor.InkMuted))
                    }
                } else {
                    TextButton(onClick = { toggleSelectMode() }, enabled = items.isNotEmpty()) {
                        Text("Select", style = CfType.Label.copy(color = CfColor.InkFaint))
                    }
                    TextButton(onClick = { confirmDeleteAll = true }, enabled = items.isNotEmpty()) {
                        Text("Delete all", style = CfType.Label.copy(color = CfColor.Danger))
                    }
                }
            }
        }

        if (selectMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14).padding(bottom = CfSpace.S9),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selectedKeys.size} selected", style = CfType.Label)
                TextButton(onClick = { confirmDeleteSelected = true }, enabled = selectedKeys.isNotEmpty()) {
                    Text("Delete", style = CfType.Label.copy(color = CfColor.Danger))
                }
            }
        }

        if (items.isEmpty()) {
            CfEmptyState(
                icon = Icons.Filled.PermMedia,
                title = "No media yet",
                body = "Images you sync from the clipboard and files you attach to notes show up here.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = CfSpace.S14, vertical = CfSpace.S4),
                horizontalArrangement = Arrangement.spacedBy(CfSpace.S9),
                verticalArrangement = Arrangement.spacedBy(CfSpace.S9),
            ) {
                items(items, key = { halMediaItemKey(it) }) { item ->
                    val key = halMediaItemKey(item)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CfRadius.Card))
                            .background(CfColor.Surface),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(CfColor.Inset)
                                .clickable {
                                    if (selectMode) {
                                        selectedKeys = if (selectedKeys.contains(key)) selectedKeys - key else selectedKeys + key
                                    } else {
                                        onOpenItem(item)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val thumb = thumbs[item.driveFileId]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (item.isImage) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = CfColor.Cyan)
                            } else {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = CfColor.InkMuted)
                            }
                            if (selectMode) {
                                Box(modifier = Modifier.align(Alignment.TopStart).padding(CfSpace.S6)) {
                                    CfCheckbox(checked = selectedKeys.contains(key))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(CfSpace.S6)
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(CfRadius.Small))
                                        .background(CfColor.Background.copy(alpha = 0.75f))
                                        .clickable { onDeleteItems(listOf(item)) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = CfColor.Ink, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                        Column(modifier = Modifier.padding(horizontal = CfSpace.S8, vertical = CfSpace.S6)) {
                            Text(
                                item.name,
                                style = CfType.Label.copy(color = CfColor.Ink),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (item.size > 0) "${item.context} · ${halFormatMediaSize(item.size)}" else item.context,
                                style = CfType.Meta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CfSpace.S8)
                                .padding(bottom = CfSpace.S8)
                                .height(28.dp)
                                .clip(RoundedCornerShape(CfRadius.Small))
                                .background(CfColor.Inset)
                                .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
                                .clickable { onDownloadItem(item) },
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = CfColor.InkMuted, modifier = Modifier.size(12.dp))
                            Text("Download", style = CfType.Meta.copy(color = CfColor.InkMuted))
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            containerColor = CfColor.Surface,
            shape = RoundedCornerShape(CfRadius.Large),
            title = { Text("Delete all ${items.size} media items?", style = CfType.ScreenTitle) },
            text = {
                Text(
                    "Clipboard images/files are deleted outright; note attachments are unlinked from their notes (the notes themselves aren't touched). This can't be undone.",
                    style = CfType.BodyMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; onDeleteItems(items) }) {
                    Text("Delete all", style = CfType.Body.copy(color = CfColor.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text("Cancel", style = CfType.Body.copy(color = CfColor.InkMuted))
                }
            },
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            containerColor = CfColor.Surface,
            shape = RoundedCornerShape(CfRadius.Large),
            title = { Text("Delete ${selectedKeys.size} item${if (selectedKeys.size == 1) "" else "s"}?", style = CfType.ScreenTitle) },
            text = { Text("This can't be undone.", style = CfType.BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = items.filter { selectedKeys.contains(halMediaItemKey(it)) }
                    confirmDeleteSelected = false
                    selectMode = false
                    selectedKeys = emptySet()
                    onDeleteItems(toDelete)
                }) {
                    Text("Delete", style = CfType.Body.copy(color = CfColor.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) {
                    Text("Cancel", style = CfType.Body.copy(color = CfColor.InkMuted))
                }
            },
        )
    }
}
