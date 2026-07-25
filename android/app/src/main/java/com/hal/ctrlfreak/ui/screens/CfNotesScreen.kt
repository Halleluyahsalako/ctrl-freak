package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.data.HalNote
import com.hal.ctrlfreak.ui.components.CfEmptyState
import com.hal.ctrlfreak.ui.components.CfPinToggle
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §3.3

// Fixed palette per spec §3.3 — never introduce bright/pastel category colors.
private val HAL_CATEGORY_PALETTE = listOf(
    CfColor.Accent, CfColor.Cyan, CfColor.InkMuted,
    androidx.compose.ui.graphics.Color(0xFFB48EAD),
    androidx.compose.ui.graphics.Color(0xFF8FBCBB),
)

fun halCategoryColor(categories: List<HalCategory>, categoryId: String?): androidx.compose.ui.graphics.Color {
    val index = categories.indexOfFirst { it.id == categoryId }
    if (index < 0) return CfColor.InkFaint
    return HAL_CATEGORY_PALETTE[index % HAL_CATEGORY_PALETTE.size]
}

@Composable
fun CfNotesScreen(
    notes: List<HalNote>,
    categories: List<HalCategory>,
    activeCategoryId: String?,
    onSelectCategory: (String?) -> Unit,
    onNewNote: () -> Unit,
    onOpenNote: (HalNote) -> Unit,
    onTogglePin: (HalNote) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
) {
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun toggleSelectMode() {
        selectMode = !selectMode
        selectedIds = emptySet()
    }

    fun toggleSelected(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    Column(modifier = Modifier.fillMaxSize().background(CfColor.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14, vertical = CfSpace.S12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("notes", style = CfType.ScreenTitle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectMode) {
                    TextButton(onClick = { toggleSelectMode() }) {
                        Text("Cancel", style = CfType.Label.copy(color = CfColor.InkMuted))
                    }
                } else {
                    TextButton(onClick = { toggleSelectMode() }, enabled = notes.isNotEmpty()) {
                        Text("Select", style = CfType.Label.copy(color = CfColor.InkFaint))
                    }
                    IconButton(onClick = onNewNote) {
                        Icon(Icons.Filled.Add, contentDescription = "New note", tint = CfColor.Accent)
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

        LazyRow(
            contentPadding = PaddingValues(start = CfSpace.S14, end = CfSpace.S14),
            horizontalArrangement = Arrangement.spacedBy(CfSpace.S6),
        ) {
            item {
                com.hal.ctrlfreak.ui.components.CfCategoryChip(
                    label = "all",
                    active = activeCategoryId == null,
                    onClick = { onSelectCategory(null) },
                )
            }
            items(categories, key = { it.id }) { cat ->
                com.hal.ctrlfreak.ui.components.CfCategoryChip(
                    label = cat.name.lowercase(),
                    active = activeCategoryId == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = CfSpace.S10))

        val visible = notes.filter { activeCategoryId == null || it.categoryId == activeCategoryId }
        val ordered = visible.filter { it.pinned } + visible.filterNot { it.pinned }

        if (notes.isEmpty()) {
            CfEmptyState(
                icon = Icons.Filled.NoteAlt,
                title = "No notes yet",
                body = "Tap + to write your first note. Pick a category to keep things sorted.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = CfSpace.S14, vertical = CfSpace.S10),
                verticalArrangement = Arrangement.spacedBy(CfSpace.S9),
            ) {
                items(ordered, key = { it.id }) { note ->
                    CfNoteRow(
                        note = note,
                        color = halCategoryColor(categories, note.categoryId),
                        selectMode = selectMode,
                        selected = selectedIds.contains(note.id),
                        onClick = { if (selectMode) toggleSelected(note.id) else onOpenNote(note) },
                        onTogglePin = { onTogglePin(note) },
                    )
                }
            }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            containerColor = CfColor.Surface,
            shape = RoundedCornerShape(CfRadius.Large),
            title = { Text("Delete ${selectedIds.size} note${if (selectedIds.size == 1) "" else "s"}?", style = CfType.ScreenTitle) },
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
private fun CfNoteRow(
    note: HalNote,
    color: androidx.compose.ui.graphics.Color,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val borderColor = if (note.pinned) CfColor.Accent else CfColor.Rule

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfRadius.Card))
            .background(CfColor.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(CfRadius.Card))
            .clickable(onClick = onClick)
            .padding(vertical = CfSpace.S10, horizontal = CfSpace.S11),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (selectMode) {
                CfCheckbox(checked = selected, modifier = Modifier.padding(end = CfSpace.S9))
            }
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(CfSpace.S8))
            Text(
                note.title,
                style = CfType.ListTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selectMode) {
            CfPinToggle(pinned = note.pinned, onToggle = onTogglePin)
        }
    }
}
