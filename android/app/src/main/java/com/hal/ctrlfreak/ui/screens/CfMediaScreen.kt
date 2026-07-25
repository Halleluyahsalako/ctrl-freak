package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
// attachment plus every clip that's ever been an image, newest first, each
// opening straight to its real Drive view page.

data class HalMediaItem(
    val driveFileId: String,
    val name: String,
    val size: Long,
    val isImage: Boolean,
    val sortAt: Long,
    val context: String,
)

private val HAL_MEDIA_IMAGE_EXT = Regex("(?i)\\.(png|jpe?g|gif|webp|bmp)$")

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
            )
        }
    }
    val fromClips = clips
        .filter { it.kind == HalClipKind.IMAGE && it.driveFileId != null }
        .map { clip ->
            HalMediaItem(
                driveFileId = clip.driveFileId!!,
                name = "Clipboard image",
                size = 0,
                isImage = true,
                sortAt = clip.createdAt,
                context = if (clip.originDevice == "android") "from phone" else "from browser",
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
) {
    Column(modifier = Modifier.fillMaxSize().background(CfColor.Background)) {
        Text(
            "media",
            style = CfType.ScreenTitle,
            modifier = Modifier.padding(horizontal = CfSpace.S14, vertical = CfSpace.S12),
        )

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
                items(items, key = { "${it.driveFileId}-${it.sortAt}" }) { item ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CfRadius.Card))
                            .background(CfColor.Surface)
                            .clickable { onOpenItem(item) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(CfColor.Inset),
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
                        }
                        Column(modifier = Modifier.padding(CfSpace.S8)) {
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
                    }
                }
            }
        }
    }
}
