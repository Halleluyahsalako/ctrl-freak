package com.hal.ctrlfreak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.data.HalNote
import com.hal.ctrlfreak.sync.halCreateCategory
import com.hal.ctrlfreak.sync.halCreateNote
import com.hal.ctrlfreak.sync.halDeleteCategory
import com.hal.ctrlfreak.sync.halDeleteNote
import com.hal.ctrlfreak.sync.halSubscribeToCategories
import com.hal.ctrlfreak.sync.halSubscribeToNotes
import com.hal.ctrlfreak.sync.halUpdateNote
import kotlinx.coroutines.launch

// First Android pass: title/body/category CRUD only, plain text body.
// The extension's notes editor grew into a full Tiptap WYSIWYG editor
// with attachments (see extension/src/hal-notes.tsx) — matching that on
// Android needs either a WebView-hosted editor or a Compose rich-text
// library, neither of which was worth attempting blind this late.
// Tracked as a known gap, not a silent omission.

@Composable
fun HalNotesScreen(uid: String) {
    val scope = rememberCoroutineScope()
    val notes by halSubscribeToNotes(uid).collectAsState(initial = emptyList())
    val categories by halSubscribeToCategories(uid).collectAsState(initial = emptyList())

    var activeCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var noteCategoryId by remember { mutableStateOf<String?>(null) }
    var newCategoryName by remember { mutableStateOf("") }

    fun selectNote(note: HalNote?) {
        selectedNoteId = note?.id
        title = note?.title ?: ""
        body = note?.body ?: ""
        noteCategoryId = note?.categoryId
    }

    val visibleNotes = notes.filter { activeCategoryId == null || it.categoryId == activeCategoryId }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Notes", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        LazyRow {
            item {
                Text(
                    "All",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { activeCategoryId = null },
                )
            }
            items(categories) { cat: HalCategory ->
                Row(modifier = Modifier.padding(8.dp)) {
                    Text(cat.name, modifier = Modifier.clickable { activeCategoryId = cat.id })
                    Text(
                        " ×",
                        modifier = Modifier.clickable {
                            scope.launch { halDeleteCategory(uid, cat.id) }
                        },
                    )
                }
            }
        }

        Row {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("New category") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                if (newCategoryName.isNotBlank()) {
                    scope.launch {
                        halCreateCategory(uid, HalCategory(name = newCategoryName.trim(), color = "#d99b47"))
                        newCategoryName = ""
                    }
                }
            }) { Text("Add") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(visibleNotes) { note: HalNote ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectNote(note) }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(note.title)
                    Text(
                        "delete",
                        modifier = Modifier.clickable {
                            scope.launch {
                                halDeleteNote(uid, note.id)
                                if (selectedNoteId == note.id) selectNote(null)
                            }
                        },
                    )
                }
            }
        }

        Button(onClick = { selectNote(null) }) { Text("+ New note") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Body (markdown)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row {
            Text(
                "No category",
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { noteCategoryId = null },
            )
            categories.forEach { cat: HalCategory ->
                Text(
                    cat.name,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { noteCategoryId = cat.id },
                )
            }
        }

        Button(onClick = {
            if (title.isNotBlank()) {
                scope.launch {
                    val note = HalNote(title = title.trim(), body = body, categoryId = noteCategoryId)
                    val id = selectedNoteId
                    if (id != null) {
                        halUpdateNote(uid, id, note)
                    } else {
                        halCreateNote(uid, note)
                        selectNote(null)
                    }
                }
            }
        }) { Text(if (selectedNoteId != null) "Save changes" else "Create note") }
    }
}
