package com.hal.ctrlfreak.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// Android 10+ restricts clipboard reads to the foreground app (or the
// default IME) for privacy — the same constraint spirit as the browser
// (ARCHITECTURE.md §3), different mechanism. This only works reliably
// while HalMainActivity is in the foreground, which is fine: it's called
// from a "Sync clipboard now" button tap, not a background listener.

fun halReadClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val item = clip.getItemAt(0)
    return item.text?.toString()
}

fun halClipboardHasImage(context: Context): Boolean {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val description = manager.primaryClipDescription ?: return false
    return description.hasMimeType("image/*")
}

fun halWriteClipboardText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Ctrl+Freak", text))
}

// Reads the current clipboard image's raw bytes via the content:// URI
// the OS clipboard holds it as — same idea as the extension's
// navigator.clipboard.read() returning a Blob, just Android's own path
// to get there.
fun halReadClipboardImageBytes(context: Context): ByteArray? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val uri = clip.getItemAt(0).uri ?: return null
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
}

