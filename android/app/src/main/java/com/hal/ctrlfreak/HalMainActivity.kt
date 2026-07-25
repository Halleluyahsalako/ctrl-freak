package com.hal.ctrlfreak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.google.firebase.auth.FirebaseAuth
import com.hal.ctrlfreak.auth.halAuth
import com.hal.ctrlfreak.auth.halGetDriveAccessToken
import com.hal.ctrlfreak.auth.halSignIn
import com.hal.ctrlfreak.clipboard.halClipboardHasImage
import com.hal.ctrlfreak.clipboard.halReadClipboardImageBytes
import com.hal.ctrlfreak.clipboard.halReadClipboardText
import com.hal.ctrlfreak.clipboard.halWriteClipboardText
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalClipKind
import com.hal.ctrlfreak.data.HalDevice
import com.hal.ctrlfreak.data.HalNote
import com.hal.ctrlfreak.drive.halUploadFileToDrive
import com.hal.ctrlfreak.sync.halCreateCategory
import com.hal.ctrlfreak.sync.halCreateNote
import com.hal.ctrlfreak.sync.halDeleteCategory
import com.hal.ctrlfreak.sync.halDeleteNote
import com.hal.ctrlfreak.sync.halPushClip
import com.hal.ctrlfreak.sync.halSetClipPinned
import com.hal.ctrlfreak.sync.halSubscribeToCategories
import com.hal.ctrlfreak.sync.halSubscribeToClips
import com.hal.ctrlfreak.sync.halSubscribeToNotes
import com.hal.ctrlfreak.sync.halUpdateNote
import com.hal.ctrlfreak.ui.components.CfSnackbar
import com.hal.ctrlfreak.ui.components.CfSnackbarKind
import com.hal.ctrlfreak.ui.screens.CfClipboardScreen
import com.hal.ctrlfreak.ui.screens.CfNoteEditorScreen
import com.hal.ctrlfreak.ui.screens.CfNotesScreen
import com.hal.ctrlfreak.ui.screens.CfSignInScreen
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private enum class HalTab { CLIPBOARD, NOTES }

class HalMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CfTheme {
                HalApp(activity = this, sharedIntent = intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun halIsOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun HalApp(activity: Activity, sharedIntent: Intent?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarKind by remember { mutableStateOf(CfSnackbarKind.Success) }

    var halUser by remember { mutableStateOf(halAuth.currentUser) }
    var signInBusy by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(HalTab.CLIPBOARD) }

    var clips by remember { mutableStateOf<List<HalClipItem>>(emptyList()) }
    var notes by remember { mutableStateOf<List<HalNote>>(emptyList()) }
    var categories by remember { mutableStateOf<List<HalCategory>>(emptyList()) }

    var syncing by remember { mutableStateOf(false) }
    var activeCategoryId by remember { mutableStateOf<String?>(null) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var isCreatingNote by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }
    var editCategoryId by remember { mutableStateOf<String?>(null) }
    var editPinned by remember { mutableStateOf(false) }

    var pendingShareIntent by remember { mutableStateOf(sharedIntent?.takeIf { it.action == Intent.ACTION_SEND }) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { halUser = it.currentUser }
        halAuth.addAuthStateListener(listener)
        onDispose { halAuth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(halUser) {
        val uid = halUser?.uid
        if (uid == null) {
            clips = emptyList(); notes = emptyList(); categories = emptyList()
            return@LaunchedEffect
        }
        launch { halSubscribeToClips(uid).collect { clips = it } }
        launch { halSubscribeToNotes(uid).collect { notes = it } }
        launch { halSubscribeToCategories(uid).collect { categories = it } }
    }

    suspend fun halShowSnackbar(message: String, kind: CfSnackbarKind, actionLabel: String? = null): Boolean {
        snackbarKind = kind
        val result = snackbarHostState.showSnackbar(message, actionLabel = actionLabel, duration = androidx.compose.material3.SnackbarDuration.Short)
        return result == SnackbarResult.ActionPerformed
    }

    // §5 — share-sheet target. Waits for auth if needed, adds silently,
    // routes to Clipboard unless the user is mid-edit in Notes (then just
    // a snackbar with a "View" action instead of yanking them away).
    LaunchedEffect(halUser, pendingShareIntent) {
        val uid = halUser?.uid ?: return@LaunchedEffect
        val intent = pendingShareIntent ?: return@LaunchedEffect
        try {
            val sharedImageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

            if (sharedImageUri != null) {
                val bytes = context.contentResolver.openInputStream(sharedImageUri)?.use { it.readBytes() }
                if (bytes != null) {
                    val token = halGetDriveAccessToken(activity)
                    val driveFileId = halUploadFileToDrive(token, bytes, "image/png", "hal-share-${System.currentTimeMillis()}.png")
                    halPushClip(uid, HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID))
                }
            } else if (!sharedText.isNullOrEmpty()) {
                halPushClip(uid, HalClipItem(kind = HalClipKind.TEXT, text = sharedText, originDevice = HalDevice.ANDROID))
            }
            pendingShareIntent = null

            if (editingNoteId == null && !isCreatingNote) {
                tab = HalTab.CLIPBOARD
                halShowSnackbar("Added to clipboard", CfSnackbarKind.Success)
            } else {
                val viewTapped = halShowSnackbar("Added to clipboard", CfSnackbarKind.Success, actionLabel = "View")
                if (viewTapped) {
                    editingNoteId = null; isCreatingNote = false; tab = HalTab.CLIPBOARD
                }
            }
        } catch (e: Exception) {
            pendingShareIntent = null
            halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
        }
    }

    fun halOpenNoteEditor(note: HalNote?) {
        editingNoteId = note?.id
        isCreatingNote = note == null
        editTitle = note?.title ?: ""
        editBody = note?.body ?: ""
        editCategoryId = note?.categoryId
        editPinned = note?.pinned ?: false
    }

    if (halUser == null) {
        CfSignInScreen(
            busy = signInBusy,
            error = signInError,
            onSignIn = {
                scope.launch {
                    signInBusy = true
                    signInError = null
                    try {
                        halSignIn(context)
                    } catch (e: Exception) {
                        signInError = "Couldn't sign in. Try again."
                    } finally {
                        signInBusy = false
                    }
                }
            },
        )
        return
    }

    val uid = halUser!!.uid

    Scaffold(
        containerColor = CfColor.Background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> CfSnackbar(data, snackbarKind) }
        },
        bottomBar = {
            if (editingNoteId == null && !isCreatingNote) {
                NavigationBar(containerColor = CfColor.Surface, modifier = Modifier.background(CfColor.Surface)) {
                    NavigationBarItem(
                        selected = tab == HalTab.CLIPBOARD,
                        onClick = { tab = HalTab.CLIPBOARD },
                        icon = {
                            Icon(
                                if (tab == HalTab.CLIPBOARD) Icons.Filled.ContentPaste else Icons.Outlined.ContentPaste,
                                contentDescription = "Clipboard",
                            )
                        },
                        label = { Text("clipboard") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CfColor.Accent,
                            selectedTextColor = CfColor.Accent,
                            unselectedIconColor = CfColor.InkFaint,
                            unselectedTextColor = CfColor.InkFaint,
                            indicatorColor = CfColor.Surface,
                        ),
                    )
                    NavigationBarItem(
                        selected = tab == HalTab.NOTES,
                        onClick = { tab = HalTab.NOTES },
                        icon = {
                            Icon(
                                if (tab == HalTab.NOTES) Icons.Filled.StickyNote2 else Icons.Outlined.StickyNote2,
                                contentDescription = "Notes",
                            )
                        },
                        label = { Text("notes") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CfColor.Accent,
                            selectedTextColor = CfColor.Accent,
                            unselectedIconColor = CfColor.InkFaint,
                            unselectedTextColor = CfColor.InkFaint,
                            indicatorColor = CfColor.Surface,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (editingNoteId != null || isCreatingNote) {
                CfNoteEditorScreen(
                    title = editTitle,
                    onTitleChange = { editTitle = it },
                    body = editBody,
                    onBodyChange = { editBody = it },
                    categories = categories,
                    categoryId = editCategoryId,
                    onCategoryChange = { editCategoryId = it },
                    pinned = editPinned,
                    onTogglePin = {
                        editPinned = !editPinned
                        editingNoteId?.let { id -> scope.launch { halUpdateNote(uid, id, HalNote(title = editTitle, body = editBody, categoryId = editCategoryId, pinned = editPinned)) } }
                    },
                    isExisting = editingNoteId != null,
                    onBack = { editingNoteId = null; isCreatingNote = false },
                    onSave = {
                        if (editTitle.isNotBlank()) {
                            scope.launch {
                                val note = HalNote(title = editTitle.trim(), body = editBody, categoryId = editCategoryId, pinned = editPinned)
                                val id = editingNoteId
                                try {
                                    if (id != null) halUpdateNote(uid, id, note) else halCreateNote(uid, note)
                                    editingNoteId = null; isCreatingNote = false
                                    halShowSnackbar("Saved", CfSnackbarKind.Success)
                                } catch (e: Exception) {
                                    halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                                }
                            }
                        }
                    },
                    onDelete = {
                        val id = editingNoteId ?: return@CfNoteEditorScreen
                        scope.launch {
                            halDeleteNote(uid, id)
                            editingNoteId = null; isCreatingNote = false
                            halShowSnackbar("Note deleted", CfSnackbarKind.Neutral)
                        }
                    },
                )
            } else when (tab) {
                HalTab.CLIPBOARD -> CfClipboardScreen(
                    clips = clips,
                    syncing = syncing,
                    isOnline = halIsOnline(context),
                    onSyncNow = {
                        scope.launch {
                            syncing = true
                            try {
                                if (halClipboardHasImage(context)) {
                                    val bytes = halReadClipboardImageBytes(context)
                                    if (bytes != null) {
                                        val token = halGetDriveAccessToken(activity)
                                        val driveFileId = halUploadFileToDrive(token, bytes, "image/png", "hal-clip-${System.currentTimeMillis()}.png")
                                        halPushClip(uid, HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID))
                                        halShowSnackbar("Synced 1 item", CfSnackbarKind.Success)
                                    } else {
                                        halShowSnackbar("Clipboard's empty — nothing to sync", CfSnackbarKind.Neutral)
                                    }
                                } else {
                                    val text = halReadClipboardText(context)
                                    if (!text.isNullOrEmpty()) {
                                        halPushClip(uid, HalClipItem(kind = HalClipKind.TEXT, text = text, originDevice = HalDevice.ANDROID))
                                        halShowSnackbar("Synced 1 item", CfSnackbarKind.Success)
                                    } else {
                                        halShowSnackbar("Clipboard's empty — nothing to sync", CfSnackbarKind.Neutral)
                                    }
                                }
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            } finally {
                                syncing = false
                            }
                        }
                    },
                    onCopyBack = { clip ->
                        scope.launch {
                            if (clip.text != null) {
                                halWriteClipboardText(context, clip.text)
                                halShowSnackbar("Copied", CfSnackbarKind.Success)
                            }
                        }
                    },
                    onTogglePin = { clip ->
                        scope.launch { halSetClipPinned(uid, clip.id, !clip.pinned) }
                    },
                )

                HalTab.NOTES -> CfNotesScreen(
                    notes = notes,
                    categories = categories,
                    activeCategoryId = activeCategoryId,
                    onSelectCategory = { activeCategoryId = it },
                    onNewNote = { halOpenNoteEditor(null) },
                    onOpenNote = { halOpenNoteEditor(it) },
                    onTogglePin = { note ->
                        scope.launch { halUpdateNote(uid, note.id, note.copy(pinned = !note.pinned)) }
                    },
                )
            }
        }
    }
}
