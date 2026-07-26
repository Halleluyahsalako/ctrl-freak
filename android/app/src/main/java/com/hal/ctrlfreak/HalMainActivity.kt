package com.hal.ctrlfreak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PermMedia
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.google.firebase.auth.FirebaseAuth
import com.hal.ctrlfreak.auth.halAuth
import com.hal.ctrlfreak.auth.halRequestDriveAuthorization
import com.hal.ctrlfreak.auth.halSignIn
import com.hal.ctrlfreak.clipboard.halClipboardHasImage
import com.hal.ctrlfreak.clipboard.halReadClipboardImageBytes
import com.hal.ctrlfreak.clipboard.halReadClipboardText
import com.hal.ctrlfreak.clipboard.halWriteClipboardText
import com.hal.ctrlfreak.data.HalAttachment
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalClipKind
import com.hal.ctrlfreak.data.HalDevice
import com.hal.ctrlfreak.data.HalNote
import com.hal.ctrlfreak.drive.halFetchDriveFileBytes
import com.hal.ctrlfreak.drive.halUploadFileToDrive
import com.hal.ctrlfreak.sync.halClearAllClips
import com.hal.ctrlfreak.sync.halCreateCategory
import com.hal.ctrlfreak.sync.halCreateNote
import com.hal.ctrlfreak.sync.halDeleteCategory
import com.hal.ctrlfreak.sync.halDeleteClips
import com.hal.ctrlfreak.sync.halDeleteNote
import com.hal.ctrlfreak.sync.halDeleteNotes
import com.hal.ctrlfreak.sync.halPushClip
import com.hal.ctrlfreak.sync.halSetClipPinned
import com.hal.ctrlfreak.sync.halSubscribeToCategories
import com.hal.ctrlfreak.sync.halSubscribeToClips
import com.hal.ctrlfreak.sync.halSubscribeToNotes
import com.hal.ctrlfreak.sync.halUpdateNote
import com.hal.ctrlfreak.ui.components.CfSnackbar
import com.hal.ctrlfreak.ui.components.CfSnackbarKind
import com.hal.ctrlfreak.ui.screens.CfClipboardScreen
import com.hal.ctrlfreak.ui.screens.CfMediaScreen
import com.hal.ctrlfreak.ui.screens.CfNoteEditorScreen
import com.hal.ctrlfreak.ui.screens.CfNotesScreen
import com.hal.ctrlfreak.ui.screens.CfSignInScreen
import com.hal.ctrlfreak.ui.screens.halBuildMediaItems
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private enum class HalTab { CLIPBOARD, NOTES, MEDIA }

class HalMainActivity : ComponentActivity() {
    // A plain `intent` read only happens once, at setContent's first
    // composition. If the app is already running and receives a new share
    // via onNewIntent (standard launch mode reuses the top instance in that
    // case), Compose never observes the change — the share silently does
    // nothing, which is exactly what was reported ("clicked share, then
    // nothing"). Holding it as Compose state fixes that.
    private var halCurrentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        halCurrentIntent = intent
        setContent {
            CfTheme {
                HalApp(activity = this, sharedIntent = halCurrentIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        halCurrentIntent = intent
    }
}

private fun halQueryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return null
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
    var editAttachments by remember { mutableStateOf<List<HalAttachment>>(emptyList()) }
    var uploadingAttachmentNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mediaThumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var clipCaption by remember { mutableStateOf("") }
    var clipUploading by remember { mutableStateOf(false) }

    var pendingShareIntent by remember { mutableStateOf<Intent?>(null) }
    // Reacts to sharedIntent changing (including a share arriving while the
    // app is already open — see the halCurrentIntent fix in the Activity).
    LaunchedEffect(sharedIntent) {
        if (sharedIntent?.action == Intent.ACTION_SEND || sharedIntent?.action == Intent.ACTION_SEND_MULTIPLE) {
            pendingShareIntent = sharedIntent
        }
    }

    var driveConsentDeferred by remember {
        mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null)
    }
    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        driveConsentDeferred?.complete(result.resultCode == Activity.RESULT_OK)
        driveConsentDeferred = null
    }

    // Requests a Drive access token, launching the real consent screen when
    // silent authorization isn't enough (always true the first time). The
    // previous version threw instead of ever showing this prompt, so every
    // image sync/share failed silently — this is the actual fix, not just
    // a rewire.
    suspend fun halEnsureDriveAccessToken(): String {
        val first = halRequestDriveAuthorization(activity)
        first.accessToken?.let { return it }

        val pendingIntent = first.pendingIntent
            ?: throw IllegalStateException("Drive access wasn't granted")
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        driveConsentDeferred = deferred
        driveConsentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        val granted = deferred.await()
        if (!granted) throw IllegalStateException("Drive access wasn't granted")

        val retry = halRequestDriveAuthorization(activity)
        return retry.accessToken ?: throw IllegalStateException("Drive access wasn't granted")
    }

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

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val name = halQueryDisplayName(context, uri) ?: "attachment"
            scope.launch {
                uploadingAttachmentNames = uploadingAttachmentNames + name
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val token = halEnsureDriveAccessToken()
                        val driveFileId = halUploadFileToDrive(token, bytes, mimeType, name)
                        editAttachments = editAttachments + HalAttachment(driveFileId = driveFileId, name = name, size = bytes.size.toLong())
                    }
                } catch (e: Exception) {
                    halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                } finally {
                    uploadingAttachmentNames = uploadingAttachmentNames - name
                }
            }
        }
    }

    // Media tab thumbnails — fetched lazily (only while that tab is open,
    // only for images not already cached) so switching tabs never re-fetches
    // and browsing Clipboard/Notes never pays for a Drive round trip.
    LaunchedEffect(tab, notes, clips) {
        if (tab != HalTab.MEDIA) return@LaunchedEffect
        val items = halBuildMediaItems(notes, clips)
        val toFetch = items.filter { it.isImage && !mediaThumbs.containsKey(it.driveFileId) }
        if (toFetch.isEmpty()) return@LaunchedEffect
        try {
            val token = halEnsureDriveAccessToken()
            for (item in toFetch) {
                try {
                    val bytes = halFetchDriveFileBytes(token, item.driveFileId)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    mediaThumbs = mediaThumbs + (item.driveFileId to bitmap.asImageBitmap())
                } catch (e: Exception) {
                    // skip this one, keep loading the rest
                }
            }
        } catch (e: Exception) {
            halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
        }
    }

    // Direct clip upload — no note required (the "share media without
    // adding a note" request). Mirrors the extension popup's Upload button:
    // picks any file, uploads straight to Drive, pushes it as its own clip.
    val clipUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val clipUploadUid = halUser?.uid
        if (uri != null && clipUploadUid != null) {
            scope.launch {
                clipUploading = true
                try {
                    val name = halQueryDisplayName(context, uri) ?: "file"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val token = halEnsureDriveAccessToken()
                        val driveFileId = halUploadFileToDrive(token, bytes, mimeType, name)
                        val kind = if (mimeType.startsWith("image/")) HalClipKind.IMAGE else HalClipKind.FILE
                        halPushClip(
                            clipUploadUid,
                            HalClipItem(
                                kind = kind,
                                driveFileId = driveFileId,
                                text = clipCaption.trim().ifBlank { name },
                                originDevice = HalDevice.ANDROID,
                            ),
                        )
                        clipCaption = ""
                        halShowSnackbar("Uploaded", CfSnackbarKind.Success)
                    }
                } catch (e: Exception) {
                    halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                } finally {
                    clipUploading = false
                }
            }
        }
    }

    // §5 — share-sheet target. Waits for auth if needed, adds silently,
    // routes to Clipboard unless the user is mid-edit in Notes (then just
    // a snackbar with a "View" action instead of yanking them away).
    LaunchedEffect(halUser, pendingShareIntent) {
        val uid = halUser?.uid ?: return@LaunchedEffect
        val intent = pendingShareIntent ?: return@LaunchedEffect
        try {
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                val token = if (uris.isNotEmpty()) halEnsureDriveAccessToken() else null
                for ((index, uri) in uris.withIndex()) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                    val driveFileId = halUploadFileToDrive(token!!, bytes, "image/png", "hal-share-${System.currentTimeMillis()}-$index.png")
                    halPushClip(uid, HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID))
                }
            } else {
                val sharedImageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

                if (sharedImageUri != null) {
                    val bytes = context.contentResolver.openInputStream(sharedImageUri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val token = halEnsureDriveAccessToken()
                        val driveFileId = halUploadFileToDrive(token, bytes, "image/png", "hal-share-${System.currentTimeMillis()}.png")
                        halPushClip(uid, HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID))
                    }
                } else if (!sharedText.isNullOrEmpty()) {
                    halPushClip(uid, HalClipItem(kind = HalClipKind.TEXT, text = sharedText, originDevice = HalDevice.ANDROID))
                }
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
        editAttachments = note?.attachments ?: emptyList()
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

    // Real autosave — mirrors the extension (hal-notes.tsx): debounced
    // create-then-update instead of relying on the manual Save button, so a
    // draft never gets lost switching notes/tabs and attaching a file to a
    // brand-new note doesn't require creating it first. LaunchedEffect
    // restarting on any key change IS the debounce (and safely supersedes a
    // stale in-flight save) — no manual timer bookkeeping needed here the
    // way the JS side required.
    LaunchedEffect(editingNoteId, isCreatingNote, editTitle, editBody, editCategoryId, editPinned, editAttachments) {
        if (editingNoteId == null && !isCreatingNote) return@LaunchedEffect
        if (editTitle.isBlank() && editBody.isBlank() && editAttachments.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        val note = HalNote(
            title = editTitle.trim().ifBlank { "Untitled note" },
            body = editBody,
            categoryId = editCategoryId,
            pinned = editPinned,
            attachments = editAttachments,
        )
        try {
            val id = editingNoteId
            if (id != null) {
                halUpdateNote(uid, id, note)
            } else {
                editingNoteId = halCreateNote(uid, note)
            }
        } catch (e: Exception) {
            // Manual Save is still there and will surface the error via its
            // own snackbar if the connection is actually down.
        }
    }

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
                    NavigationBarItem(
                        selected = tab == HalTab.MEDIA,
                        onClick = { tab = HalTab.MEDIA },
                        icon = {
                            Icon(
                                if (tab == HalTab.MEDIA) Icons.Filled.PermMedia else Icons.Outlined.PermMedia,
                                contentDescription = "Media",
                            )
                        },
                        label = { Text("media") },
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
                        editingNoteId?.let { id -> scope.launch { halUpdateNote(uid, id, HalNote(title = editTitle, body = editBody, categoryId = editCategoryId, pinned = editPinned, attachments = editAttachments)) } }
                    },
                    attachments = editAttachments,
                    uploadingAttachmentNames = uploadingAttachmentNames,
                    onAttachClick = { attachmentPickerLauncher.launch("*/*") },
                    onRemoveAttachment = { driveFileId -> editAttachments = editAttachments.filterNot { it.driveFileId == driveFileId } },
                    isExisting = editingNoteId != null,
                    onBack = { editingNoteId = null; isCreatingNote = false },
                    onSave = {
                        if (editTitle.isNotBlank()) {
                            scope.launch {
                                val note = HalNote(title = editTitle.trim(), body = editBody, categoryId = editCategoryId, pinned = editPinned, attachments = editAttachments)
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
                                        val token = halEnsureDriveAccessToken()
                                        val driveFileId = halUploadFileToDrive(token, bytes, "image/png", "hal-clip-${System.currentTimeMillis()}.png")
                                        halPushClip(uid, HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, text = clipCaption.trim().ifBlank { null }, originDevice = HalDevice.ANDROID))
                                        clipCaption = ""
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
                            if (clip.driveFileId != null) {
                                // No OS-clipboard image write path on Android yet — opening the
                                // real Drive file is a working action instead of a silent no-op,
                                // which is what tapping an image/file clip did before this.
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/file/d/${clip.driveFileId}/view")))
                            } else if (clip.text != null) {
                                halWriteClipboardText(context, clip.text)
                                halShowSnackbar("Copied", CfSnackbarKind.Success)
                            }
                        }
                    },
                    onTogglePin = { clip ->
                        scope.launch { halSetClipPinned(uid, clip.id, !clip.pinned) }
                    },
                    onClearAll = {
                        scope.launch {
                            try {
                                halClearAllClips(uid)
                                halShowSnackbar("Clipboard cleared", CfSnackbarKind.Success)
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                    onDeleteSelected = { ids ->
                        scope.launch {
                            try {
                                halDeleteClips(uid, ids.toList())
                                halShowSnackbar("Deleted ${ids.size} item${if (ids.size == 1) "" else "s"}", CfSnackbarKind.Success)
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                    caption = clipCaption,
                    onCaptionChange = { clipCaption = it },
                    onUploadFile = { clipUploadLauncher.launch("*/*") },
                    uploading = clipUploading,
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
                    onDeleteSelected = { ids ->
                        scope.launch {
                            try {
                                halDeleteNotes(uid, ids.toList())
                                halShowSnackbar("Deleted ${ids.size} note${if (ids.size == 1) "" else "s"}", CfSnackbarKind.Neutral)
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                    onCreateCategory = { name ->
                        scope.launch {
                            try {
                                halCreateCategory(uid, HalCategory(name = name, color = "#E0A75E"))
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                    onDeleteCategory = { categoryId ->
                        scope.launch {
                            try {
                                halDeleteCategory(uid, categoryId)
                                if (activeCategoryId == categoryId) activeCategoryId = null
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                )

                HalTab.MEDIA -> CfMediaScreen(
                    items = halBuildMediaItems(notes, clips),
                    thumbs = mediaThumbs,
                    onOpenItem = { item ->
                        val url = "https://drive.google.com/file/d/${item.driveFileId}/view"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onDownloadItem = { item ->
                        scope.launch {
                            try {
                                val token = halEnsureDriveAccessToken()
                                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                val request = android.app.DownloadManager.Request(
                                    Uri.parse("https://www.googleapis.com/drive/v3/files/${item.driveFileId}?alt=media"),
                                )
                                    .addRequestHeader("Authorization", "Bearer $token")
                                    .setTitle(item.name)
                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, item.name)
                                downloadManager.enqueue(request)
                                halShowSnackbar("Downloading…", CfSnackbarKind.Success)
                            } catch (e: Exception) {
                                halShowSnackbar("Couldn't sync — check your connection", CfSnackbarKind.Error)
                            }
                        }
                    },
                )
            }
        }
    }
}
