package com.hal.ctrlfreak.sync

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import com.hal.ctrlfreak.auth.halDb
import com.hal.ctrlfreak.data.HalCategory
import com.hal.ctrlfreak.data.HalClipItem
import com.hal.ctrlfreak.data.HalNote
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Mirrors extension/src/hal-sync.ts + hal-notes-sync.ts — same collection
// names, same /users/{uid}/... paths, same firestore.rules. No backend
// changes needed for the Android client to talk to the same project.

private const val HAL_RECENT_CLIPS_LIMIT = 50L

private fun halClipItems(uid: String) = halDb.collection("users").document(uid).collection("hal_clipItems")
private fun halNotes(uid: String) = halDb.collection("users").document(uid).collection("hal_notes")
private fun halCategories(uid: String) = halDb.collection("users").document(uid).collection("hal_categories")

// ---- Clips ----

suspend fun halPushClip(uid: String, clip: HalClipItem) {
    val data = clip.copy(id = "", createdAt = System.currentTimeMillis())
    halClipItems(uid).add(data).await()
}

suspend fun halSetClipPinned(uid: String, clipId: String, pinned: Boolean) {
    halClipItems(uid).document(clipId).update("pinned", pinned).await()
}

suspend fun halDeleteClip(uid: String, clipId: String) {
    halClipItems(uid).document(clipId).delete().await()
}

suspend fun halDeleteClips(uid: String, clipIds: List<String>) {
    halBatchDelete(halClipItems(uid), clipIds)
}

// Deletes every clip, not just the 50 the live listener keeps in memory.
suspend fun halClearAllClips(uid: String) {
    val snapshot = halClipItems(uid).get().await()
    halBatchDelete(halClipItems(uid), snapshot.documents.map { it.id })
}

fun halSubscribeToClips(uid: String): Flow<List<HalClipItem>> = callbackFlow {
    val registration = halClipItems(uid)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(HAL_RECENT_CLIPS_LIMIT)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.documents.map { it.toObject(HalClipItem::class.java)!!.copy(id = it.id) })
        }
    awaitClose { registration.remove() }
}

// ---- Notes ----

suspend fun halCreateNote(uid: String, note: HalNote) {
    val data = note.copy(id = "", updatedAt = System.currentTimeMillis())
    halNotes(uid).add(data).await()
}

suspend fun halUpdateNote(uid: String, noteId: String, note: HalNote) {
    val data = note.copy(id = "", updatedAt = System.currentTimeMillis())
    halNotes(uid).document(noteId).set(data).await()
}

suspend fun halDeleteNote(uid: String, noteId: String) {
    halNotes(uid).document(noteId).delete().await()
}

suspend fun halDeleteNotes(uid: String, noteIds: List<String>) {
    halBatchDelete(halNotes(uid), noteIds)
}

fun halSubscribeToNotes(uid: String): Flow<List<HalNote>> = callbackFlow {
    val registration = halNotes(uid)
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.documents.map { it.toObject(HalNote::class.java)!!.copy(id = it.id) })
        }
    awaitClose { registration.remove() }
}

// ---- Categories ----

suspend fun halCreateCategory(uid: String, category: HalCategory) {
    halCategories(uid).add(category.copy(id = "")).await()
}

suspend fun halDeleteCategory(uid: String, categoryId: String) {
    halCategories(uid).document(categoryId).delete().await()
}

fun halSubscribeToCategories(uid: String): Flow<List<HalCategory>> = callbackFlow {
    val registration = halCategories(uid)
        .orderBy("name")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.documents.map { it.toObject(HalCategory::class.java)!!.copy(id = it.id) })
        }
    awaitClose { registration.remove() }
}

// ---- Shared helpers ----

// Firestore caps a single batch at 500 writes.
private const val HAL_BATCH_CHUNK = 500

private suspend fun halBatchDelete(collectionRef: CollectionReference, ids: List<String>) {
    for (chunk in ids.chunked(HAL_BATCH_CHUNK)) {
        val batch = halDb.batch()
        for (id in chunk) {
            batch.delete(collectionRef.document(id))
        }
        batch.commit().await()
    }
}
