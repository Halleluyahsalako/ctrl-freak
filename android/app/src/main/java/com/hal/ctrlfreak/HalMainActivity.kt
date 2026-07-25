package com.hal.ctrlfreak

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.google.firebase.auth.FirebaseAuth
import com.hal.ctrlfreak.auth.halAuth
import com.hal.ctrlfreak.auth.halGetDriveAccessToken
import com.hal.ctrlfreak.auth.halSignIn
import com.hal.ctrlfreak.clipboard.halClipboardHasImage
import com.hal.ctrlfreak.clipboard.halReadClipboardImageBytes
import com.hal.ctrlfreak.clipboard.halReadClipboardText
import com.hal.ctrlfreak.clipboard.halWriteClipboardText
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalClipKind
import com.hal.ctrlfreak.data.HalDevice
import com.hal.ctrlfreak.drive.halUploadFileToDrive
import com.hal.ctrlfreak.sync.halPushClip
import com.hal.ctrlfreak.sync.halSubscribeToClips
import kotlinx.coroutines.launch

class HalMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HalApp(activity = this, sharedIntent = intent)
                }
            }
        }
    }

    // Handles "Share to Ctrl+Freak" when the app is already running.
    // The first-launch case is handled via the intent passed into
    // setContent above.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun HalApp(activity: Activity, sharedIntent: Intent?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(halAuth.currentUser) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        halAuth.addAuthStateListener(listener)
        onDispose { halAuth.removeAuthStateListener(listener) }
    }

    // Incoming share-sheet content — see the SEND intent filters in
    // AndroidManifest.xml. Not yet handling ACTION_SEND for images landing
    // here while already signed out; that's a follow-up, not tonight's scope.
    LaunchedEffect(user, sharedIntent) {
        val uid = user?.uid ?: return@LaunchedEffect
        if (sharedIntent?.action != Intent.ACTION_SEND) return@LaunchedEffect

        try {
            val sharedText = sharedIntent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedImageUri = IntentCompat.getParcelableExtra(sharedIntent, Intent.EXTRA_STREAM, Uri::class.java)

            if (sharedImageUri != null) {
                val bytes = context.contentResolver.openInputStream(sharedImageUri)?.use { it.readBytes() }
                if (bytes != null) {
                    val token = halGetDriveAccessToken(activity)
                    val driveFileId = halUploadFileToDrive(
                        token, bytes, "image/png", "hal-share-${System.currentTimeMillis()}.png",
                    )
                    halPushClip(
                        uid,
                        HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID),
                    )
                }
            } else if (!sharedText.isNullOrEmpty()) {
                halPushClip(
                    uid,
                    HalClipItem(kind = HalClipKind.TEXT, text = sharedText, originDevice = HalDevice.ANDROID),
                )
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Ctrl+Freak", style = MaterialTheme.typography.headlineSmall)

        if (user == null) {
            Button(onClick = {
                scope.launch {
                    try {
                        halSignIn(context)
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }) { Text("Sign in with Google") }
        } else {
            val uid = user!!.uid
            val clips by halSubscribeToClips(uid).collectAsState(initial = emptyList())

            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            if (halClipboardHasImage(context)) {
                                val bytes = halReadClipboardImageBytes(context)
                                if (bytes != null) {
                                    val token = halGetDriveAccessToken(activity)
                                    val driveFileId = halUploadFileToDrive(
                                        token, bytes, "image/png", "hal-clip-${System.currentTimeMillis()}.png",
                                    )
                                    halPushClip(
                                        uid,
                                        HalClipItem(kind = HalClipKind.IMAGE, driveFileId = driveFileId, originDevice = HalDevice.ANDROID),
                                    )
                                }
                            } else {
                                val text = halReadClipboardText(context)
                                if (!text.isNullOrEmpty()) {
                                    halPushClip(
                                        uid,
                                        HalClipItem(kind = HalClipKind.TEXT, text = text, originDevice = HalDevice.ANDROID),
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Syncing…" else "Sync clipboard now") }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn {
                items(clips) { clip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                // Text paste-back only tonight — writing an
                                // image clip back to the system clipboard
                                // (ClipData.newUri with a content:// provider)
                                // is a follow-up, not done in this pass.
                                if (clip.kind != HalClipKind.IMAGE && clip.text != null) {
                                    halWriteClipboardText(context, clip.text)
                                }
                            },
                    ) {
                        Text(if (clip.kind == HalClipKind.IMAGE) "[image]" else clip.text ?: "")
                    }
                }
            }
        }
    }
}
