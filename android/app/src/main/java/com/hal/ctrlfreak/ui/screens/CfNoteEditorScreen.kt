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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hal.ctrlfreak.data.HalAttachment
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.ui.components.CfCategoryChip
import com.hal.ctrlfreak.ui.components.CfPinToggle
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §3.4

// Body is stored as markdown (mirrors the extension's Tiptap-markdown
// output — same field, same format, round-trips between clients). Compose
// has no mature WYSIWYG rich-text editor to drop in, so instead of pulling
// in a heavy new dependency for that: a small toolbar inserts markdown
// syntax at the cursor/selection, and a VisualTransformation gives live
// bold/italic/heading styling over the raw markdown while editing — not a
// full editor, but real formatting, actually visible, actually round-trips.
private val HAL_BOLD_REGEX = Regex("\\*\\*[^*\\n]+\\*\\*")
private val HAL_ITALIC_REGEX = Regex("(?<!\\*)\\*[^*\\n]+\\*(?!\\*)")
private val HAL_HEADING_REGEX = Regex("(?m)^#{1,3} .*$")

private fun halMarkdownAnnotate(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (m in HAL_HEADING_REGEX.findAll(text)) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CfColor.Accent, fontSize = 17.sp), m.range.first, m.range.last + 1)
    }
    for (m in HAL_BOLD_REGEX.findAll(text)) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
    }
    for (m in HAL_ITALIC_REGEX.findAll(text)) {
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
    }
}

private class HalMarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(halMarkdownAnnotate(text.text), OffsetMapping.Identity)
}

private fun halWrapSelection(value: TextFieldValue, token: String): TextFieldValue {
    val sel = value.selection
    val text = value.text
    return if (sel.collapsed) {
        val newText = text.substring(0, sel.start) + token + token + text.substring(sel.start)
        TextFieldValue(newText, TextRange(sel.start + token.length))
    } else {
        val selected = text.substring(sel.start, sel.end)
        val newText = text.substring(0, sel.start) + token + selected + token + text.substring(sel.end)
        TextFieldValue(newText, TextRange(sel.start, sel.end + token.length * 2))
    }
}

private fun halInsertLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val sel = value.selection
    val searchFrom = (sel.start - 1).coerceAtLeast(0)
    val lineStart = text.lastIndexOf('\n', searchFrom).let { if (it == -1) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return TextFieldValue(newText, TextRange(sel.start + prefix.length, sel.end + prefix.length))
}

private fun halFormatAttachmentSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

@Composable
private fun CfToolbarButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CfRadius.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = CfSpace.S9, vertical = CfSpace.S6),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CfType.Label.copy(color = CfColor.InkMuted))
    }
}

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
    attachments: List<HalAttachment>,
    uploadingAttachmentNames: Set<String>,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    isExisting: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var fieldValue by remember { mutableStateOf(TextFieldValue(body)) }
    // Only resync from the body prop when it changed from outside our own
    // edits (switching to a different note) — not on every keystroke, which
    // would reset the cursor to the start of the field each time.
    LaunchedEffect(body) {
        if (body != fieldValue.text) fieldValue = TextFieldValue(body)
    }
    fun halUpdateField(next: TextFieldValue) {
        fieldValue = next
        onBodyChange(next.text)
    }

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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S11),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CfToolbarButton("B") { halUpdateField(halWrapSelection(fieldValue, "**")) }
            CfToolbarButton("i") { halUpdateField(halWrapSelection(fieldValue, "*")) }
            CfToolbarButton("H") { halUpdateField(halInsertLinePrefix(fieldValue, "# ")) }
            CfToolbarButton("•") { halUpdateField(halInsertLinePrefix(fieldValue, "- ")) }
            CfToolbarButton("1.") { halUpdateField(halInsertLinePrefix(fieldValue, "1. ")) }
            Spacer(Modifier.width(CfSpace.S6))
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach file", tint = CfColor.InkMuted, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { halUpdateField(it) },
            placeholder = { Text("Start typing…", style = CfType.EditorBody.copy(color = CfColor.InkFaint)) },
            textStyle = CfType.EditorBody,
            visualTransformation = HalMarkdownVisualTransformation(),
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

        if (attachments.isNotEmpty() || uploadingAttachmentNames.isNotEmpty()) {
            Text(
                "attachments · ${attachments.size}",
                style = CfType.Label,
                modifier = Modifier.padding(start = CfSpace.S14, top = CfSpace.S6, bottom = CfSpace.S6),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CfSpace.S14),
                horizontalArrangement = Arrangement.spacedBy(CfSpace.S8),
            ) {
                items(attachments, key = { it.driveFileId }) { att ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CfRadius.Small))
                            .background(CfColor.Surface)
                            .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
                            .padding(horizontal = CfSpace.S9, vertical = CfSpace.S6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                att.name,
                                style = CfType.Label.copy(color = CfColor.Ink),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(100.dp),
                            )
                            Text(halFormatAttachmentSize(att.size), style = CfType.Meta)
                        }
                        Spacer(Modifier.width(CfSpace.S6))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove attachment",
                            tint = CfColor.InkFaint,
                            modifier = Modifier.size(14.dp).clickable { onRemoveAttachment(att.driveFileId) },
                        )
                    }
                }
                items(uploadingAttachmentNames.toList()) { name ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CfRadius.Small))
                            .background(CfColor.Surface)
                            .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
                            .padding(horizontal = CfSpace.S9, vertical = CfSpace.S6),
                    ) {
                        Column {
                            Text(name, style = CfType.Label.copy(color = CfColor.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(100.dp))
                            Text("uploading…", style = CfType.Meta.copy(color = CfColor.Cyan))
                        }
                    }
                }
            }
            Spacer(Modifier.height(CfSpace.S6))
        }
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
