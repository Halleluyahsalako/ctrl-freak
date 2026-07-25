import { render } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { onAuthStateChanged, type User } from "firebase/auth";
import { marked } from "marked";
import { halAuth } from "./hal-firebase";
import { halGetValidAccessToken } from "./hal-auth";
import { halUploadFileToDrive, halFetchDriveFileBlob } from "./hal-drive";
import {
  halCreateNote,
  halUpdateNote,
  halDeleteNote,
  halSubscribeToNotes,
  halCreateCategory,
  halDeleteCategory,
  halSubscribeToCategories,
} from "./hal-notes-sync";
import type { HalNote, HalCategory, HalAttachment } from "@shared/schema";

const HAL_CATEGORY_COLORS = ["#d99b47", "#6cbcc4", "#5fcf9a", "#e5645c", "#9a9cb3"];
const HAL_IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp)$/i;

function HalNotesApp() {
  const [halUser, setHalUser] = useState<User | null>(null);
  const [halNotes, setHalNotes] = useState<HalNote[]>([]);
  const [halCategories, setHalCategories] = useState<HalCategory[]>([]);
  const [halActiveCategoryId, setHalActiveCategoryId] = useState<string | "all">("all");
  const [halSelectedNoteId, setHalSelectedNoteId] = useState<string | null>(null);
  const [halTitle, setHalTitle] = useState("");
  const [halBody, setHalBody] = useState("");
  const [halNoteCategoryId, setHalNoteCategoryId] = useState<string>("");
  const [halAttachments, setHalAttachments] = useState<HalAttachment[]>([]);
  const [halNewCategoryName, setHalNewCategoryName] = useState("");
  const [halSearch, setHalSearch] = useState("");
  const [halError, setHalError] = useState<string | null>(null);
  const [halViewMode, setHalViewMode] = useState<"edit" | "preview">("edit");
  const [halUploading, setHalUploading] = useState(false);
  const [halThumbs, setHalThumbs] = useState<Record<string, string>>({});

  const halBodyRef = useRef<HTMLTextAreaElement>(null);
  const halFileInputRef = useRef<HTMLInputElement>(null);

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
    setHalAttachments(note?.attachments ?? []);
    setHalViewMode("edit");
  }

  async function halHandleSave() {
    if (!halUser || !halTitle.trim()) return;
    setHalError(null);
    const payload = {
      title: halTitle.trim(),
      body: halBody,
      categoryId: halNoteCategoryId || undefined,
      attachments: halAttachments,
    };
    try {
      if (halSelectedNoteId) {
        await halUpdateNote(halUser.uid, halSelectedNoteId, payload);
      } else {
        await halCreateNote(halUser.uid, payload);
        halSelectNote(null);
      }
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandleDelete(noteId: string) {
    if (!halUser) return;
    setHalError(null);
    try {
      await halDeleteNote(halUser.uid, noteId);
      if (halSelectedNoteId === noteId) halSelectNote(null);
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandleAddCategory() {
    if (!halUser || !halNewCategoryName.trim()) return;
    setHalError(null);
    const color =
      HAL_CATEGORY_COLORS[halCategories.length % HAL_CATEGORY_COLORS.length];
    try {
      await halCreateCategory(halUser.uid, { name: halNewCategoryName.trim(), color });
      setHalNewCategoryName("");
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandleDeleteCategory(categoryId: string) {
    if (!halUser) return;
    setHalError(null);
    try {
      await halDeleteCategory(halUser.uid, categoryId);
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  // ---- Attachments ----

  async function halUploadAttachment(blob: Blob, name: string) {
    setHalUploading(true);
    setHalError(null);
    try {
      const accessToken = await halGetValidAccessToken();
      const driveFileId = await halUploadFileToDrive(accessToken, blob, name);
      setHalAttachments((prev) => [...prev, { driveFileId, name }]);
      if (HAL_IMAGE_EXT.test(name)) {
        setHalThumbs((prev) => ({ ...prev, [driveFileId]: URL.createObjectURL(blob) }));
      }
    } catch (err) {
      setHalError((err as Error).message);
    } finally {
      setHalUploading(false);
    }
  }

  function halHandleFilePick(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) halUploadAttachment(file, file.name);
    (e.target as HTMLInputElement).value = "";
  }

  function halHandleBodyPaste(e: ClipboardEvent) {
    const items = e.clipboardData?.items;
    if (!items) return;
    for (const item of Array.from(items)) {
      if (item.type.startsWith("image/")) {
        e.preventDefault();
        const blob = item.getAsFile();
        if (blob) {
          const ext = item.type.split("/")[1] ?? "png";
          halUploadAttachment(blob, `pasted-${Date.now()}.${ext}`);
        }
        return;
      }
    }
    // otherwise let plain text paste through normally
  }

  function halRemoveAttachment(driveFileId: string) {
    setHalAttachments((prev) => prev.filter((a) => a.driveFileId !== driveFileId));
  }

  async function halDownloadAttachment(att: HalAttachment) {
    setHalError(null);
    try {
      const accessToken = await halGetValidAccessToken();
      const blob = await halFetchDriveFileBlob(accessToken, att.driveFileId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = att.name;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  // ---- Markdown toolbar ----

  function halInsertMarkdown(before: string, after: string) {
    const el = halBodyRef.current;
    if (!el) return;
    const start = el.selectionStart;
    const end = el.selectionEnd;
    const selected = halBody.slice(start, end);
    const next = halBody.slice(0, start) + before + selected + after + halBody.slice(end);
    setHalBody(next);
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = start + before.length;
      el.selectionEnd = start + before.length + selected.length;
    });
  }

  function halHandleBodyKeyDown(e: KeyboardEvent) {
    if (!(e.ctrlKey || e.metaKey)) return;
    if (e.key === "b") {
      e.preventDefault();
      halInsertMarkdown("**", "**");
    } else if (e.key === "i") {
      e.preventDefault();
      halInsertMarkdown("_", "_");
    } else if (e.key === "k") {
      e.preventDefault();
      halInsertMarkdown("[", "](url)");
    }
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
        {halError && <p class="hal-error">{halError}</p>}

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
              <button class="hal-link" onClick={() => halHandleDeleteCategory(cat.id)}>
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

        <div class="hal-toolbar">
          <button class="hal-toolbar-btn" title="Bold (Ctrl+B)" onClick={() => halInsertMarkdown("**", "**")}>
            B
          </button>
          <button class="hal-toolbar-btn" title="Italic (Ctrl+I)" onClick={() => halInsertMarkdown("_", "_")}>
            i
          </button>
          <button class="hal-toolbar-btn" title="Link (Ctrl+K)" onClick={() => halInsertMarkdown("[", "](url)")}>
            Lk
          </button>
          <button class="hal-toolbar-btn" title="Bulleted list" onClick={() => halInsertMarkdown("- ", "")}>
            •
          </button>
          <button
            class="hal-toolbar-btn"
            title="Attach a file"
            onClick={() => halFileInputRef.current?.click()}
          >
            +
          </button>
          <input
            ref={halFileInputRef}
            type="file"
            class="hal-file-input"
            onChange={halHandleFilePick}
          />
          <button
            class={`hal-toolbar-btn hal-toolbar-btn-right ${halViewMode === "preview" ? "hal-active" : ""}`}
            onClick={() => setHalViewMode(halViewMode === "edit" ? "preview" : "edit")}
          >
            {halViewMode === "edit" ? "Preview" : "Edit"}
          </button>
        </div>

        {halViewMode === "edit" ? (
          <textarea
            ref={halBodyRef}
            class="hal-input hal-note-body"
            placeholder="Write in markdown… paste an image directly to attach it"
            value={halBody}
            onInput={(e) => setHalBody((e.target as HTMLTextAreaElement).value)}
            onPaste={halHandleBodyPaste}
            onKeyDown={halHandleBodyKeyDown}
          />
        ) : (
          <div
            class="hal-note-preview"
            dangerouslySetInnerHTML={{ __html: marked.parse(halBody) as string }}
          />
        )}

        {halUploading && <p class="hal-uploading">Uploading attachment…</p>}

        {halAttachments.length > 0 && (
          <ul class="hal-attachment-list">
            {halAttachments.map((att) => (
              <li key={att.driveFileId} class="hal-attachment-item">
                {halThumbs[att.driveFileId] ? (
                  <img class="hal-attachment-thumb" src={halThumbs[att.driveFileId]} />
                ) : (
                  <span class="hal-attachment-icon">[file]</span>
                )}
                <span class="hal-attachment-name" onClick={() => halDownloadAttachment(att)}>
                  {att.name}
                </span>
                <button class="hal-link" onClick={() => halRemoveAttachment(att.driveFileId)}>
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

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
