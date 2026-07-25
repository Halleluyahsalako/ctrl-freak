package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.ui.components.CfCategoryChip
import com.hal.ctrlfreak.ui.components.CfPinToggle
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §3.4

@Composable
fun CfNoteEditorScreen(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    categories: List<HalCategory>,
    categoryId: String?,
    onCategoryChange: (String?) -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    isExisting: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(CfColor.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14, vertical = CfSpace.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = CfColor.InkMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExisting) {
                    CfPinToggle(pinned = pinned, onToggle = onTogglePin)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = CfColor.InkMuted)
                        }
                        if (showMenu) {
                            Column(
                                modifier = Modifier
                                    .background(CfColor.Surface)
                                    .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
                                    .padding(CfSpace.S8),
                            ) {
                                Text(
                                    "Delete",
                                    style = CfType.Body.copy(color = CfColor.Danger),
                                    modifier = Modifier
                                        .clickable {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                        .padding(CfSpace.S8),
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onSave) {
                    Text("Save", style = CfType.Button.copy(color = CfColor.Accent))
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text("Title", style = CfType.Body.copy(color = CfColor.InkFaint)) },
            textStyle = CfType.Body,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CfColor.Background,
                unfocusedContainerColor = CfColor.Background,
                focusedIndicatorColor = CfColor.Accent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = CfColor.Accent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S14),
        )

        Text(
            "category",
            style = CfType.Label,
            modifier = Modifier.padding(start = CfSpace.S14, top = CfSpace.S10, bottom = CfSpace.S6),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CfSpace.S14),
            horizontalArrangement = Arrangement.spacedBy(CfSpace.S6),
        ) {
            item {
                CfCategoryChip(label = "none", active = categoryId == null, onClick = { onCategoryChange(null) })
            }
            items(categories, key = { it.id }) { cat ->
                CfCategoryChip(
                    label = cat.name.lowercase(),
                    active = categoryId == cat.id,
                    onClick = { onCategoryChange(cat.id) },
                )
            }
        }

        Spacer(Modifier.height(CfSpace.S12))

        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            placeholder = { Text("Start typing…", style = CfType.EditorBody.copy(color = CfColor.InkFaint)) },
            textStyle = CfType.EditorBody,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CfColor.Inset,
                unfocusedContainerColor = CfColor.Inset,
                focusedIndicatorColor = CfColor.Rule,
                unfocusedIndicatorColor = CfColor.Rule,
                cursorColor = CfColor.Accent,
            ),
            shape = RoundedCornerShape(CfRadius.Card),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = CfSpace.S14, vertical = CfSpace.S4),
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CfColor.Surface,
            shape = RoundedCornerShape(CfRadius.Large),
            title = { Text("Delete note?", style = CfType.ScreenTitle) },
            text = { Text("This can't be undone.", style = CfType.BodyMuted) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", style = CfType.Body.copy(color = CfColor.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", style = CfType.Body.copy(color = CfColor.InkMuted))
                }
            },
        )
    }
}
