import {
  collection,
  doc,
  addDoc,
  updateDoc,
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
      (d) => ({ id: d.id, ...d.data() }) as HalClipItem,
    );
    onChange(clips);
  });
}
