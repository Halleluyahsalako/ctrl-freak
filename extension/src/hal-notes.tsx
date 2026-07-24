import { render } from "preact";
import { useEffect, useState } from "preact/hooks";
import { onAuthStateChanged, type User } from "firebase/auth";
import { halAuth } from "./hal-firebase";
import {
  halCreateNote,
  halUpdateNote,
  halDeleteNote,
  halSubscribeToNotes,
  halCreateCategory,
  halDeleteCategory,
  halSubscribeToCategories,
} from "./hal-notes-sync";
import type { HalNote, HalCategory } from "@shared/schema";

const HAL_CATEGORY_COLORS = ["#d99b47", "#6cbcc4", "#5fcf9a", "#e5645c", "#9a9cb3"];

function HalNotesApp() {
  const [halUser, setHalUser] = useState<User | null>(null);
  const [halNotes, setHalNotes] = useState<HalNote[]>([]);
  const [halCategories, setHalCategories] = useState<HalCategory[]>([]);
  const [halActiveCategoryId, setHalActiveCategoryId] = useState<string | "all">("all");
  const [halSelectedNoteId, setHalSelectedNoteId] = useState<string | null>(null);
  const [halTitle, setHalTitle] = useState("");
  const [halBody, setHalBody] = useState("");
  const [halNoteCategoryId, setHalNoteCategoryId] = useState<string>("");
  const [halNewCategoryName, setHalNewCategoryName] = useState("");
  const [halSearch, setHalSearch] = useState("");

  useEffect(() => onAuthStateChanged(halAuth, setHalUser), []);

  useEffect(() => {
    if (!halUser) {
      setHalNotes([]);
      setHalCategories([]);
      return;
    }
    const unsubNotes = halSubscribeToNotes(halUser.uid, setHalNotes);
    const unsubCategories = halSubscribeToCategories(halUser.uid, setHalCategories);
    return () => {
      unsubNotes();
      unsubCategories();
    };
  }, [halUser]);

  const halVisibleNotes = halNotes
    .filter((n) => halActiveCategoryId === "all" || n.categoryId === halActiveCategoryId)
    .filter((n) => {
      if (!halSearch.trim()) return true;
      const needle = halSearch.trim().toLowerCase();
      return n.title.toLowerCase().includes(needle) || n.body.toLowerCase().includes(needle);
    });

  function halSelectNote(note: HalNote | null) {
    setHalSelectedNoteId(note?.id ?? null);
    setHalTitle(note?.title ?? "");
    setHalBody(note?.body ?? "");
    setHalNoteCategoryId(note?.categoryId ?? "");
  }

  async function halHandleSave() {
    if (!halUser || !halTitle.trim()) return;
    const payload = {
      title: halTitle.trim(),
      body: halBody,
      categoryId: halNoteCategoryId || undefined,
      attachments: [],
    };
    if (halSelectedNoteId) {
      await halUpdateNote(halUser.uid, halSelectedNoteId, payload);
    } else {
      await halCreateNote(halUser.uid, payload);
      halSelectNote(null);
    }
  }

  async function halHandleDelete(noteId: string) {
    if (!halUser) return;
    await halDeleteNote(halUser.uid, noteId);
    if (halSelectedNoteId === noteId) halSelectNote(null);
  }

  async function halHandleAddCategory() {
    if (!halUser || !halNewCategoryName.trim()) return;
    const color =
      HAL_CATEGORY_COLORS[halCategories.length % HAL_CATEGORY_COLORS.length];
    await halCreateCategory(halUser.uid, { name: halNewCategoryName.trim(), color });
    setHalNewCategoryName("");
  }

  if (!halUser) {
    return (
      <main class="hal-notes-app">
        <p>Sign in from the extension popup first, then reopen this page.</p>
      </main>
    );
  }

  return (
    <main class="hal-notes-app">
      <aside class="hal-sidebar">
        <h1 class="hal-title">Ctrl+Freak — Notes</h1>

        <ul class="hal-category-list">
          <li
            class={`hal-category-item ${halActiveCategoryId === "all" ? "hal-active" : ""}`}
            onClick={() => setHalActiveCategoryId("all")}
          >
            All notes
          </li>
          {halCategories.map((cat) => (
            <li
              key={cat.id}
              class={`hal-category-item ${halActiveCategoryId === cat.id ? "hal-active" : ""}`}
            >
              <span onClick={() => setHalActiveCategoryId(cat.id)}>
                <span class="hal-dot" style={{ background: cat.color }} />
                {cat.name}
              </span>
              <button
                class="hal-link"
                onClick={() => halDeleteCategory(halUser.uid, cat.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>

        <div class="hal-new-category">
          <input
            class="hal-input"
            placeholder="New category"
            value={halNewCategoryName}
            onInput={(e) => setHalNewCategoryName((e.target as HTMLInputElement).value)}
          />
          <button class="hal-button hal-button-small" onClick={halHandleAddCategory}>
            Add
          </button>
        </div>

        <input
          class="hal-input"
          placeholder="Search notes…"
          value={halSearch}
          onInput={(e) => setHalSearch((e.target as HTMLInputElement).value)}
        />

        <ul class="hal-note-list">
          {halVisibleNotes.map((note) => (
            <li
              key={note.id}
              class={`hal-note-item ${halSelectedNoteId === note.id ? "hal-active" : ""}`}
              onClick={() => halSelectNote(note)}
            >
              {note.title}
            </li>
          ))}
        </ul>
        <button class="hal-button" onClick={() => halSelectNote(null)}>
          + New note
        </button>
      </aside>

      <section class="hal-editor">
        <input
          class="hal-input hal-note-title"
          placeholder="Title"
          value={halTitle}
          onInput={(e) => setHalTitle((e.target as HTMLInputElement).value)}
        />
        <select
          class="hal-input"
          value={halNoteCategoryId}
          onChange={(e) => setHalNoteCategoryId((e.target as HTMLSelectElement).value)}
        >
          <option value="">No category</option>
          {halCategories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name}
            </option>
          ))}
        </select>
        <textarea
          class="hal-input hal-note-body"
          placeholder="Write in markdown…"
          value={halBody}
          onInput={(e) => setHalBody((e.target as HTMLTextAreaElement).value)}
        />
        <div class="hal-editor-actions">
          <button class="hal-button" onClick={halHandleSave}>
            {halSelectedNoteId ? "Save changes" : "Create note"}
          </button>
          {halSelectedNoteId && (
            <button
              class="hal-button hal-button-danger"
              onClick={() => halHandleDelete(halSelectedNoteId)}
            >
              Delete
            </button>
          )}
        </div>
      </section>
    </main>
  );
}

render(<HalNotesApp />, document.getElementById("hal-root")!);
