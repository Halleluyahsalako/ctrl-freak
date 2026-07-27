import {
  collection,
  doc,
  addDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  onSnapshot,
  query,
  orderBy,
} from "firebase/firestore";
import { halDb } from "./hal-firebase";
import type { HalNote, HalCategory } from "@shared/schema";

function halNotesCollection(uid: string) {
  return collection(halDb, "users", uid, "hal_notes");
}

function halCategoriesCollection(uid: string) {
  return collection(halDb, "users", uid, "hal_categories");
}

// ---- Notes ----

export async function halCreateNote(
  uid: string,
  note: Omit<HalNote, "id" | "updatedAt">,
): Promise<string> {
  const docRef = await addDoc(halNotesCollection(uid), { ...note, updatedAt: Date.now() });
  return docRef.id;
}

export async function halUpdateNote(
  uid: string,
  noteId: string,
  patch: Partial<Omit<HalNote, "id">>,
): Promise<void> {
  await updateDoc(doc(halDb, "users", uid, "hal_notes", noteId), {
    ...patch,
    updatedAt: Date.now(),
  });
}

export async function halDeleteNote(uid: string, noteId: string): Promise<void> {
  await deleteDoc(doc(halDb, "users", uid, "hal_notes", noteId));
}

// Firestore caps a single batch at 500 writes.
const HAL_BATCH_CHUNK = 500;

export async function halDeleteNotes(uid: string, noteIds: string[]): Promise<void> {
  for (let i = 0; i < noteIds.length; i += HAL_BATCH_CHUNK) {
    const batch = writeBatch(halDb);
    for (const id of noteIds.slice(i, i + HAL_BATCH_CHUNK)) {
      batch.delete(doc(halDb, "users", uid, "hal_notes", id));
    }
    await batch.commit();
  }
}

export async function halSetNotePinned(
  uid: string,
  noteId: string,
  pinned: boolean,
): Promise<void> {
  await updateDoc(doc(halDb, "users", uid, "hal_notes", noteId), { pinned });
}

export function halSubscribeToNotes(
  uid: string,
  onChange: (notes: HalNote[]) => void,
): () => void {
  const halNotesQuery = query(halNotesCollection(uid), orderBy("updatedAt", "desc"));
  return onSnapshot(halNotesQuery, (snapshot) => {
    // id spread last — see hal-sync.ts's identical fix. A note ever
    // touched by the (now-fixed) Android write path can still have a
    // stray "id": "" stored inside its own document body; this must never
    // be allowed to shadow the real Firestore document id.
    onChange(snapshot.docs.map((d) => ({ ...d.data(), id: d.id }) as HalNote));
  });
}

// ---- Categories ----

export async function halCreateCategory(
  uid: string,
  category: Omit<HalCategory, "id">,
): Promise<void> {
  await addDoc(halCategoriesCollection(uid), category);
}

export async function halDeleteCategory(uid: string, categoryId: string): Promise<void> {
  await deleteDoc(doc(halDb, "users", uid, "hal_categories", categoryId));
}

export function halSubscribeToCategories(
  uid: string,
  onChange: (categories: HalCategory[]) => void,
): () => void {
  const halCategoriesQuery = query(halCategoriesCollection(uid), orderBy("name"));
  return onSnapshot(halCategoriesQuery, (snapshot) => {
    onChange(snapshot.docs.map((d) => ({ ...d.data(), id: d.id }) as HalCategory));
  });
}
