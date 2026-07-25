package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalClipKind
import com.hal.ctrlfreak.data.HalDevice
import com.hal.ctrlfreak.ui.components.CfEmptyState
import com.hal.ctrlfreak.ui.components.CfPinToggle
import com.hal.ctrlfreak.ui.components.CfPrimaryButton
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §3.2

@Composable
fun CfClipboardScreen(
    clips: List<HalClipItem>,
    syncing: Boolean,
    isOnline: Boolean,
    onSyncNow: () -> Unit,
    onCopyBack: (HalClipItem) -> Unit,
    onTogglePin: (HalClipItem) -> Unit,
    onClearAll: () -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
) {
    val pinned = clips.filter { it.pinned }
    val recent = clips.filterNot { it.pinned }

    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun toggleSelectMode() {
        selectMode = !selectMode
        selectedIds = emptySet()
    }

    fun toggleSelected(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    Column(modifier = Modifier.fillMaxSize().background(CfColor.Background)) {
        // top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14, vertical = CfSpace.S12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text("ctrl+", style = CfType.Wordmark.copy(color = CfColor.Ink))
                Text("freak", style = CfType.Wordmark.copy(color = CfColor.Accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isOnline) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = CfColor.InkFaint,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isOnline) "synced" else "offline", style = CfType.Meta)
            }
        }

        CfPrimaryButton(
            text = if (syncing) "Syncing…" else "Sync clipboard now",
            onClick = onSyncNow,
            loading = syncing,
            modifier = Modifier.padding(horizontal = CfSpace.S14),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14, vertical = CfSpace.S6),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${clips.size} recent · pinned first", style = CfType.Label)
            Row(horizontalArrangement = Arrangement.spacedBy(CfSpace.S11)) {
                if (selectMode) {
                    TextButton(onClick = { toggleSelectMode() }) {
                        Text("Cancel", style = CfType.Label.copy(color = CfColor.InkMuted))
                    }
                } else {
                    TextButton(onClick = { toggleSelectMode() }, enabled = clips.isNotEmpty()) {
                        Text("Select", style = CfType.Label.copy(color = CfColor.InkFaint))
                    }
                    TextButton(onClick = { confirmClearAll = true }, enabled = clips.isNotEmpty()) {
                        Text("Clear all", style = CfType.Label.copy(color = CfColor.Danger))
                    }
                }
            }
        }

        if (selectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CfSpace.S14)
                    .padding(bottom = CfSpace.S9),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selectedIds.size} selected", style = CfType.Label)
                TextButton(onClick = { confirmDeleteSelected = true }, enabled = selectedIds.isNotEmpty()) {
                    Text("Delete", style = CfType.Label.copy(color = CfColor.Danger))
                }
            }
        }

        if (clips.isEmpty()) {
            CfEmptyState(
                icon = Icons.Filled.ContentPasteOff,
                title = "Nothing synced yet",
                body = "Copy something, then tap Sync — it'll show up here and in your browser extension.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = CfSpace.S14, vertical = CfSpace.S4,
                ),
                verticalArrangement = Arrangement.spacedBy(CfSpace.S9),
            ) {
                items(pinned + recent, key = { it.id }) { clip ->
                    CfClipCard(
                        clip = clip,
                        selectMode = selectMode,
                        selected = selectedIds.contains(clip.id),
                        onClick = { if (selectMode) toggleSelected(clip.id) else onCopyBack(clip) },
                        onTogglePin = { onTogglePin(clip) },
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = CfColor.Surface,
            shape = RoundedCornerShape(CfRadius.Large),
            title = { Text("Clear all clipboard items?", style = CfType.ScreenTitle) },
            text = { Text("This deletes all ${clips.size} synced items on every device. This can't be undone.", style = CfType.BodyMuted) },
            confirmButton = {
                TextButton(onClick = { confirmClearAll = false; onClearAll() }) {
                    Text("Clear all", style = CfType.Body.copy(color = CfColor.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
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
            title = { Text("Delete ${selectedIds.size} item${if (selectedIds.size == 1) "" else "s"}?", style = CfType.ScreenTitle) },
            text = { Text("This can't be undone.", style = CfType.BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelected = false
                    onDeleteSelected(selectedIds)
                    selectMode = false
                    selectedIds = emptySet()
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

@Composable
private fun CfClipCard(
    clip: HalClipItem,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (pressed) CfColor.SurfaceRaised else CfColor.Surface
    val border = if (clip.pinned) CfColor.Accent else CfColor.Rule

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfRadius.Card))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(CfRadius.Card))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = CfSpace.S10, horizontal = CfSpace.S11),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectMode) {
            CfCheckbox(checked = selected, modifier = Modifier.padding(top = 2.dp, end = CfSpace.S9))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                if (clip.kind == HalClipKind.IMAGE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(CfRadius.Small))
                                .background(CfColor.Inset)
                                .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = CfColor.Cyan, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(CfSpace.S8))
                        Text("[image]", style = CfType.BodyMuted)
                    }
                } else {
                    Text(
                        clip.text ?: "",
                        style = CfType.Body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!selectMode) {
                    CfPinToggle(pinned = clip.pinned, onToggle = onTogglePin)
                }
            }
            Spacer(Modifier.height(CfSpace.S7))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (clip.originDevice == HalDevice.ANDROID) Icons.Filled.PhoneAndroid else Icons.Filled.Computer,
                    contentDescription = null,
                    tint = CfColor.InkFaint,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${clip.originDevice} · ${halRelativeTime(clip.createdAt)}",
                    style = CfType.Meta,
                )
            }
        }
    }
}

@Composable
internal fun CfCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val fill = if (checked) CfColor.Accent else CfColor.Inset
    val border = if (checked) CfColor.Accent else CfColor.Rule
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = CfColor.Background, modifier = Modifier.size(11.dp))
        }
    }
}

private fun halRelativeTime(createdAt: Long): String {
    val diffMs = System.currentTimeMillis() - createdAt
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
