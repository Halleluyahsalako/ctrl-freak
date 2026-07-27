import {
  collection,
  doc,
  addDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  getDocs,
  onSnapshot,
  query,
  orderBy,
  limit,
} from "firebase/firestore";
import { halDb } from "./hal-firebase";
import type { HalClipItem } from "@shared/schema";

const HAL_RECENT_LIMIT = 50;

function halClipItemsCollection(uid: string) {
  return collection(halDb, "users", uid, "hal_clipItems");
}

export async function halPushClip(
  uid: string,
  clip: Omit<HalClipItem, "id" | "createdAt">,
): Promise<void> {
  await addDoc(halClipItemsCollection(uid), {
    ...clip,
    createdAt: Date.now(),
  });
}

export async function halSetClipPinned(
  uid: string,
  clipId: string,
  pinned: boolean,
): Promise<void> {
  await updateDoc(doc(halDb, "users", uid, "hal_clipItems", clipId), { pinned });
}

export async function halDeleteClip(uid: string, clipId: string): Promise<void> {
  await deleteDoc(doc(halDb, "users", uid, "hal_clipItems", clipId));
}

export async function halDeleteClips(uid: string, clipIds: string[]): Promise<void> {
  await halBatchDelete(uid, "hal_clipItems", clipIds);
}

// Deletes every clip, not just the 50 the popup's listener keeps in memory.
export async function halClearAllClips(uid: string): Promise<void> {
  const snapshot = await getDocs(halClipItemsCollection(uid));
  await halBatchDelete(uid, "hal_clipItems", snapshot.docs.map((d) => d.id));
}

// Firestore caps a single batch at 500 writes.
const HAL_BATCH_CHUNK = 500;

async function halBatchDelete(uid: string, collectionName: string, ids: string[]): Promise<void> {
  for (let i = 0; i < ids.length; i += HAL_BATCH_CHUNK) {
    const batch = writeBatch(halDb);
    for (const id of ids.slice(i, i + HAL_BATCH_CHUNK)) {
      batch.delete(doc(halDb, "users", uid, collectionName, id));
    }
    await batch.commit();
  }
}

// Returns an unsubscribe function — call it when the popup closes.
export function halSubscribeToClips(
  uid: string,
  onChange: (clips: HalClipItem[]) => void,
): () => void {
  const halRecentClipsQuery = query(
    halClipItemsCollection(uid),
    orderBy("createdAt", "desc"),
    limit(HAL_RECENT_LIMIT),
  );

  return onSnapshot(halRecentClipsQuery, (snapshot) => {
    const clips = snapshot.docs.map(
      // id spread last, deliberately — a stray "id" field inside the
      // document body (Android's Firestore serializer used to write one,
      // see HalSchema.kt's @get:Exclude fix) must never be able to shadow
      // the real Firestore document id.
      (d) => ({ ...d.data(), id: d.id }) as HalClipItem,
    );
    onChange(clips);
  });
}
