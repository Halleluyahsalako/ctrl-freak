import {
  collection,
  doc,
  addDoc,
  updateDoc,
  deleteDoc,
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
): Promise<void> {
  await addDoc(halNotesCollection(uid), { ...note, updatedAt: Date.now() });
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

export function halSubscribeToNotes(
  uid: string,
  onChange: (notes: HalNote[]) => void,
): () => void {
  const halNotesQuery = query(halNotesCollection(uid), orderBy("updatedAt", "desc"));
  return onSnapshot(halNotesQuery, (snapshot) => {
    onChange(snapshot.docs.map((d) => ({ id: d.id, ...d.data() }) as HalNote));
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
    onChange(snapshot.docs.map((d) => ({ id: d.id, ...d.data() }) as HalCategory));
  });
}
