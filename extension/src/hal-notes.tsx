import { render } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { onAuthStateChanged, type User } from "firebase/auth";
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Link from "@tiptap/extension-link";
import TextAlign from "@tiptap/extension-text-align";
import TextStyle from "@tiptap/extension-text-style";
import Color from "@tiptap/extension-color";
import { Markdown } from "tiptap-markdown";
import { HalFontSize } from "./hal-font-size";
import { halAuth } from "./hal-firebase";
import { halGetValidAccessToken } from "./hal-auth";
import { halUploadFileToDrive, halFetchDriveFileBlob } from "./hal-drive";
import {
  halCreateNote,
  halUpdateNote,
  halDeleteNote,
  halSetNotePinned,
  halSubscribeToNotes,
  halCreateCategory,
  halDeleteCategory,
  halSubscribeToCategories,
} from "./hal-notes-sync";
import type { HalNote, HalCategory, HalAttachment } from "@shared/schema";

const HAL_CATEGORY_COLORS = ["#d99b47", "#6cbcc4", "#5fcf9a", "#e5645c", "#9a9cb3"];
const HAL_IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp)$/i;
const HAL_PREVIEWABLE_EXT = /\.(png|jpe?g|gif|webp|bmp|pdf)$/i;

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
  const [halUploading, setHalUploading] = useState(false);
  const [halThumbs, setHalThumbs] = useState<Record<string, string>>({});
  const [halShowShortcuts, setHalShowShortcuts] = useState(false);

  const halEditorContainerRef = useRef<HTMLDivElement>(null);
  const halEditorRef = useRef<Editor | null>(null);
  const halFileInputRef = useRef<HTMLInputElement>(null);
  // avoids a stale closure inside the Tiptap paste handler, which is only ever registered once
  const halUploadAttachmentRef = useRef<(blob: Blob, name: string) => void>(() => {});

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

  // Depends on halUser, not []: on first paint halUser is still null (auth
  // state hasn't resolved yet), so the container div below doesn't exist in
  // the DOM at all — an effect with [] would bail out once and never run
  // again, leaving the editor permanently uninitialized (looked like "the
  // text area isn't clickable" — there was no editor there to click).
  // Real-time WYSIWYG: typing bold shows bold immediately, and pasting rich
  // text (e.g. from Word) keeps its bold/italic/heading/list structure,
  // since ProseMirror parses the HTML Word puts on the clipboard.
  useEffect(() => {
    if (!halUser || !halEditorContainerRef.current || halEditorRef.current) return;
    const editor = new Editor({
      element: halEditorContainerRef.current,
      extensions: [
        StarterKit,
        Underline,
        Link.configure({ openOnClick: false }),
        TextStyle,
        HalFontSize,
        Color,
        TextAlign.configure({ types: ["heading", "paragraph"] }),
        Markdown.configure({ html: false }),
      ],
      content: "",
      onUpdate: ({ editor }) => {
        setHalBody((editor.storage as any).markdown.getMarkdown());
      },
      editorProps: {
        handlePaste: (_view, event) => {
          const items = event.clipboardData?.items;
          if (!items) return false;
          for (const item of Array.from(items)) {
            if (item.type.startsWith("image/")) {
              event.preventDefault();
              const blob = item.getAsFile();
              if (blob) {
                const ext = item.type.split("/")[1] ?? "png";
                halUploadAttachmentRef.current(blob, `pasted-${Date.now()}.${ext}`);
              }
              return true;
            }
          }
          return false; // let Tiptap handle text/HTML paste normally
        },
      },
    });
    halEditorRef.current = editor;
    return () => {
      editor.destroy();
      halEditorRef.current = null;
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
    halEditorRef.current?.commands.setContent(note?.body ?? "", false);
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
        await halCreateNote(halUser.uid, { ...payload, pinned: false });
        halSelectNote(null);
      }
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  async function halHandleToggleNotePin(e: Event, note: HalNote) {
    e.stopPropagation();
    if (!halUser) return;
    try {
      await halSetNotePinned(halUser.uid, note.id, !note.pinned);
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
  halUploadAttachmentRef.current = halUploadAttachment;

  function halHandleFilePick(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) halUploadAttachment(file, file.name);
    (e.target as HTMLInputElement).value = "";
  }

  function halRemoveAttachment(driveFileId: string) {
    setHalAttachments((prev) => prev.filter((a) => a.driveFileId !== driveFileId));
  }

  // Images and PDFs open in a new tab for the browser's own native preview.
  // Everything else downloads — there's no in-app viewer for arbitrary files.
  async function halOpenAttachment(att: HalAttachment) {
    setHalError(null);
    try {
      const accessToken = await halGetValidAccessToken();
      const blob = await halFetchDriveFileBlob(accessToken, att.driveFileId);
      const url = URL.createObjectURL(blob);
      if (HAL_PREVIEWABLE_EXT.test(att.name)) {
        window.open(url, "_blank");
      } else {
        const a = document.createElement("a");
        a.href = url;
        a.download = att.name;
        a.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setHalError((err as Error).message);
    }
  }

  // ---- Toolbar ----

  function halToolbar(action: () => void) {
    return () => {
      action();
      halEditorRef.current?.chain().focus();
    };
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
          {[...halVisibleNotes]
            .sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned))
            .map((note) => (
              <li
                key={note.id}
                class={`hal-note-item ${halSelectedNoteId === note.id ? "hal-active" : ""}`}
                onClick={() => halSelectNote(note)}
              >
                <button
                  class={`hal-pin ${note.pinned ? "hal-pinned" : ""}`}
                  onClick={(e) => halHandleToggleNotePin(e, note)}
                  title={note.pinned ? "Unpin" : "Pin"}
                >
                  *
                </button>
                <span class="hal-note-item-title">{note.title}</span>
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
          <button
            class="hal-toolbar-btn"
            title="Bold (Ctrl+B)"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().toggleBold().run())}
          >
            B
          </button>
          <button
            class="hal-toolbar-btn"
            title="Italic (Ctrl+I)"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().toggleItalic().run())}
          >
            i
          </button>
          <button
            class="hal-toolbar-btn"
            title="Underline (Ctrl+U)"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().toggleUnderline().run())}
          >
            U
          </button>
          <button
            class="hal-toolbar-btn"
            title="Heading"
            onClick={halToolbar(() =>
              halEditorRef.current?.chain().focus().toggleHeading({ level: 2 }).run(),
            )}
          >
            H
          </button>
          <button
            class="hal-toolbar-btn"
            title="Link"
            onClick={halToolbar(() => {
              const url = window.prompt("Link URL");
              if (url) halEditorRef.current?.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
            })}
          >
            Lk
          </button>
          <button
            class="hal-toolbar-btn"
            title="Bulleted list"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().toggleBulletList().run())}
          >
            •
          </button>
          <button
            class="hal-toolbar-btn"
            title="Numbered list"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().toggleOrderedList().run())}
          >
            1.
          </button>

          <span class="hal-toolbar-divider" />

          <button
            class="hal-toolbar-btn"
            title="Align left"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().setTextAlign("left").run())}
          >
            ⟸
          </button>
          <button
            class="hal-toolbar-btn"
            title="Align center"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().setTextAlign("center").run())}
          >
            ⟺
          </button>
          <button
            class="hal-toolbar-btn"
            title="Align right"
            onClick={halToolbar(() => halEditorRef.current?.chain().focus().setTextAlign("right").run())}
          >
            ⟹
          </button>

          <select
            class="hal-toolbar-select"
            title="Font size"
            onChange={(e) => {
              const size = (e.target as HTMLSelectElement).value;
              if (size === "default") {
                halEditorRef.current?.chain().focus().unsetFontSize().run();
              } else {
                halEditorRef.current?.chain().focus().setFontSize(size).run();
              }
            }}
          >
            <option value="default">Size</option>
            <option value="12px">Small</option>
            <option value="14px">Normal</option>
            <option value="18px">Large</option>
            <option value="24px">Huge</option>
          </select>

          <div class="hal-color-swatches">
            {["#eceaf3", "#e0a75e", "#74c7d4", "#7dd0a0", "#ea6f67", "#b58bde"].map((color) => (
              <button
                key={color}
                class="hal-color-swatch"
                style={{ background: color }}
                title={`Text color ${color}`}
                onClick={halToolbar(() => halEditorRef.current?.chain().focus().setColor(color).run())}
              />
            ))}
            <button
              class="hal-toolbar-btn"
              title="Reset color"
              onClick={halToolbar(() => halEditorRef.current?.chain().focus().unsetColor().run())}
            >
              ⦸
            </button>
          </div>

          <span class="hal-toolbar-divider" />

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
            class="hal-toolbar-btn hal-toolbar-btn-right"
            title="Keyboard shortcuts"
            onClick={() => setHalShowShortcuts((v) => !v)}
          >
            ?
          </button>
        </div>

        {halShowShortcuts && (
          <div class="hal-shortcuts">
            <strong>Editor:</strong> Ctrl+B bold · Ctrl+I italic · Ctrl+U underline · Ctrl+K link ·
            color/size/align/attach are toolbar-only, no shortcut
            <br />
            <strong>Notes:</strong> click <em>*</em> next to a note to pin it to the top
            <br />
            <strong>Extension:</strong> Ctrl+Shift+Y (Cmd+Shift+Y on Mac) opens the popup from
            anywhere · click a clip to copy it · click <em>*</em> to pin a clip
          </div>
        )}

        {halError && <p class="hal-error">{halError}</p>}

        <div ref={halEditorContainerRef} class="hal-note-body" />

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
                <span class="hal-attachment-name" onClick={() => halOpenAttachment(att)}>
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
