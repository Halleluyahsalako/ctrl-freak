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
  halDeleteNotes,
  halSubscribeToNotes,
  halCreateCategory,
  halDeleteCategory,
  halSubscribeToCategories,
} from "./hal-notes-sync";
import { useHalToast, HalToast } from "./hal-toast";
import { HalIconPin, HalIconPaperclip, HalIconCheck } from "./hal-icons";
import type { HalNote, HalCategory, HalAttachment } from "@shared/schema";

// docs/ctrl-freak-extension-ui-spec.md §3

const HAL_CATEGORY_PALETTE = ["var(--accent)", "var(--cyan)", "var(--ink-muted)", "#B48EAD", "#8FBCBB"];
const HAL_IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp)$/i;
const HAL_PDF_EXT = /\.pdf$/i;
const HAL_PREVIEWABLE_EXT = /\.(png|jpe?g|gif|webp|bmp|pdf)$/i;
const HAL_FONT_SIZES: { label: string; value: string | null }[] = [
  { label: "Small", value: "12px" },
  { label: "Normal", value: null },
  { label: "Large", value: "18px" },
];
const HAL_SWATCHES = ["var(--ink)", "var(--accent)", "var(--cyan)", "var(--danger)", "var(--ink-muted)"];

function halCategoryColor(categories: HalCategory[], categoryId: string | undefined): string {
  const index = categories.findIndex((c) => c.id === categoryId);
  return index < 0 ? "var(--ink-faint)" : HAL_CATEGORY_PALETTE[index % HAL_CATEGORY_PALETTE.length];
}

function halFormatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function halRelativeTime(updatedAt: number): string {
  const minutes = Math.floor((Date.now() - updatedAt) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}h ago`;
  return `${Math.floor(minutes / (24 * 60))}d ago`;
}

function HalNotesApp() {
  const [halUser, setHalUser] = useState<User | null>(null);
  const [halNotes, setHalNotes] = useState<HalNote[]>([]);
  const [halCategories, setHalCategories] = useState<HalCategory[]>([]);
  const [halActiveCategoryId, setHalActiveCategoryId] = useState<string | "all">("all");
  const [halSearch, setHalSearch] = useState("");
  const [halNewCategoryName, setHalNewCategoryName] = useState("");

  const [halSelectedNoteId, setHalSelectedNoteId] = useState<string | null>(null);
  const [halHasSelection, setHalHasSelection] = useState(false);
  const [halTitle, setHalTitle] = useState("");
  const [halBody, setHalBody] = useState("");
  const [halNoteCategoryId, setHalNoteCategoryId] = useState<string>("");
  const [halAttachments, setHalAttachments] = useState<HalAttachment[]>([]);
  const [halNotePinned, setHalNotePinned] = useState(false);
  const [halUpdatedAt, setHalUpdatedAt] = useState<number>(Date.now());
  const [halThumbs, setHalThumbs] = useState<Record<string, string>>({});
  const [halUploadingNames, setHalUploadingNames] = useState<Set<string>>(new Set());
  const [halDragOver, setHalDragOver] = useState(false);

  const [halOpenMenu, setHalOpenMenu] = useState<
    "category" | "blockType" | "fontSize" | "color" | "overflow" | null
  >(null);
  const [halShowDeleteConfirm, setHalShowDeleteConfirm] = useState(false);
  const [halNoteSelectMode, setHalNoteSelectMode] = useState(false);
  const [halSelectedNoteIds, setHalSelectedNoteIds] = useState<Set<string>>(new Set());
  const [halShowBulkDeleteConfirm, setHalShowBulkDeleteConfirm] = useState(false);
  const [, halForceToolbarTick] = useState(0);

  const { toast, show: halToast } = useHalToast();

  const halEditorContainerRef = useRef<HTMLDivElement>(null);
  const halEditorRef = useRef<Editor | null>(null);
  const halFileInputRef = useRef<HTMLInputElement>(null);
  const halUploadAttachmentRef = useRef<(blob: Blob, name: string) => void>(() => {});

  useEffect(() => onAuthStateChanged(halAuth, setHalUser), []);

  useEffect(() => {
    if (!halOpenMenu) return;
    function halHandleOutsideClick(e: MouseEvent) {
      if (!(e.target as HTMLElement).closest(".hal-menu-anchor")) setHalOpenMenu(null);
    }
    document.addEventListener("mousedown", halHandleOutsideClick);
    return () => document.removeEventListener("mousedown", halHandleOutsideClick);
  }, [halOpenMenu]);

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
      onTransaction: () => halForceToolbarTick((n) => n + 1),
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
          return false;
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
  const halOrderedNotes = [...halVisibleNotes.filter((n) => n.pinned), ...halVisibleNotes.filter((n) => !n.pinned)];

  function halSelectNote(note: HalNote | null) {
    setHalSelectedNoteId(note?.id ?? null);
    setHalHasSelection(true);
    setHalTitle(note?.title ?? "");
    setHalBody(note?.body ?? "");
    setHalNoteCategoryId(note?.categoryId ?? "");
    setHalAttachments(note?.attachments ?? []);
    setHalNotePinned(note?.pinned ?? false);
    setHalUpdatedAt(note?.updatedAt ?? Date.now());
    setHalOpenMenu(null);
    halEditorRef.current?.commands.setContent(note?.body ?? "", false);
  }

  async function halHandleSave() {
    if (!halUser || !halTitle.trim()) return;
    const payload = {
      title: halTitle.trim(),
      body: halBody,
      categoryId: halNoteCategoryId || undefined,
      attachments: halAttachments,
      pinned: halNotePinned,
    };
    try {
      if (halSelectedNoteId) {
        await halUpdateNote(halUser.uid, halSelectedNoteId, payload);
      } else {
        await halCreateNote(halUser.uid, payload);
        halSelectNote(null);
      }
      halToast("Saved", "success");
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  async function halHandleDelete() {
    if (!halUser || !halSelectedNoteId) return;
    try {
      await halDeleteNote(halUser.uid, halSelectedNoteId);
      halSelectNote(null);
      setHalHasSelection(false);
      halToast("Note deleted", "neutral");
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    } finally {
      setHalShowDeleteConfirm(false);
    }
  }

  function halToggleNoteSelectMode() {
    setHalNoteSelectMode((on) => !on);
    setHalSelectedNoteIds(new Set());
  }

  function halToggleNoteSelected(noteId: string) {
    setHalSelectedNoteIds((prev) => {
      const next = new Set(prev);
      if (next.has(noteId)) next.delete(noteId);
      else next.add(noteId);
      return next;
    });
  }

  function halHandleRecentRowClick(note: HalNote) {
    if (halNoteSelectMode) halToggleNoteSelected(note.id);
    else halSelectNote(note);
  }

  async function halHandleBulkDelete() {
    if (!halUser) return;
    const ids = [...halSelectedNoteIds];
    try {
      await halDeleteNotes(halUser.uid, ids);
      if (halSelectedNoteId && ids.includes(halSelectedNoteId)) {
        halSelectNote(null);
        setHalHasSelection(false);
      }
      halToast(`Deleted ${ids.length} note${ids.length === 1 ? "" : "s"}`, "neutral");
      setHalNoteSelectMode(false);
      setHalSelectedNoteIds(new Set());
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    } finally {
      setHalShowBulkDeleteConfirm(false);
    }
  }

  async function halHandleDuplicate() {
    if (!halUser) return;
    try {
      await halCreateNote(halUser.uid, {
        title: `${halTitle} (copy)`,
        body: halBody,
        categoryId: halNoteCategoryId || undefined,
        attachments: halAttachments,
        pinned: false,
      });
      halToast("Saved", "success");
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    }
    setHalOpenMenu(null);
  }

  function halHandleExportMarkdown() {
    const blob = new Blob([`# ${halTitle}\n\n${halBody}`], { type: "text/markdown" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${halTitle.trim() || "note"}.md`;
    a.click();
    URL.revokeObjectURL(url);
    setHalOpenMenu(null);
  }

  async function halHandleAddCategory() {
    if (!halUser || !halNewCategoryName.trim()) return;
    const color = HAL_CATEGORY_PALETTE[halCategories.length % HAL_CATEGORY_PALETTE.length];
    try {
      await halCreateCategory(halUser.uid, { name: halNewCategoryName.trim(), color });
      setHalNewCategoryName("");
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  async function halHandleDeleteCategory(e: Event, categoryId: string) {
    e.stopPropagation();
    if (!halUser) return;
    try {
      await halDeleteCategory(halUser.uid, categoryId);
    } catch (err) {
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  // ---- Attachments ----

  async function halUploadAttachment(blob: Blob, name: string) {
    setHalUploadingNames((prev) => new Set(prev).add(name));
    try {
      const accessToken = await halGetValidAccessToken();
      const driveFileId = await halUploadFileToDrive(accessToken, blob, name);
      setHalAttachments((prev) => [...prev, { driveFileId, name, size: blob.size }]);
      if (HAL_IMAGE_EXT.test(name)) {
        setHalThumbs((prev) => ({ ...prev, [driveFileId]: URL.createObjectURL(blob) }));
      }
      halToast("Attached", "success");
    } catch (err) {
      console.error("hal:", err);
      halToast("Upload failed — try again", "error");
    } finally {
      setHalUploadingNames((prev) => {
        const next = new Set(prev);
        next.delete(name);
        return next;
      });
    }
  }
  halUploadAttachmentRef.current = halUploadAttachment;

  function halHandleFilePick(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) halUploadAttachment(file, file.name);
    (e.target as HTMLInputElement).value = "";
  }

  function halHandleDrop(e: DragEvent) {
    e.preventDefault();
    setHalDragOver(false);
    const file = e.dataTransfer?.files?.[0];
    if (file) halUploadAttachment(file, file.name);
  }

  function halRemoveAttachment(driveFileId: string) {
    setHalAttachments((prev) => prev.filter((a) => a.driveFileId !== driveFileId));
  }

  async function halOpenAttachment(att: HalAttachment) {
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
      console.error("hal:", err);
      halToast("Couldn't sync — check your connection", "error");
    }
  }

  // ---- Toolbar helpers ----

  function halRun(action: () => void) {
    return () => {
      action();
      halEditorRef.current?.chain().focus();
    };
  }

  const ed = halEditorRef.current;
  const halBlockLabel = ed?.isActive("heading", { level: 1 })
    ? "Heading 1"
    : ed?.isActive("heading", { level: 2 })
      ? "Heading 2"
      : ed?.isActive("heading", { level: 3 })
        ? "Heading 3"
        : ed?.isActive("blockquote")
          ? "Quote"
          : ed?.isActive("codeBlock")
            ? "Code block"
            : "Body";

  if (!halUser) {
    return (
      <main class="hal-empty-editor">
        <p class="hal-empty-title">Sign in from the extension popup first</p>
        <p class="hal-empty-body">Then reopen this page.</p>
      </main>
    );
  }

  return (
    <div class="hal-app">
      <div class="hal-topbar">
        <div class="hal-topbar-left">
          <span class="hal-wordmark">
            ctrl+<span class="hal-accent-part">freak</span>
          </span>
          <span class="hal-crumb">/ notes</span>
        </div>
        <button class="hal-btn-primary" onClick={() => halSelectNote(null)}>
          + New note
        </button>
      </div>

      <div class="hal-layout">
        <aside class="hal-sidebar">
          <div class="hal-search-wrap">
            <span class="hal-search-icon">⌕</span>
            <input
              class="hal-search-input"
              placeholder="Search notes"
              value={halSearch}
              onInput={(e) => setHalSearch((e.target as HTMLInputElement).value)}
            />
          </div>

          <div>
            <p class="hal-eyebrow">Categories</p>
            <div
              class={`hal-cat-row ${halActiveCategoryId === "all" ? "hal-selected" : ""}`}
              onClick={() => setHalActiveCategoryId("all")}
            >
              <span class="hal-dot" style={{ background: "var(--accent)" }} />
              <span class="hal-cat-name">All notes</span>
              <span class="hal-cat-count">{halNotes.length}</span>
            </div>
            {halCategories.map((cat, i) => (
              <div
                key={cat.id}
                class={`hal-cat-row ${halActiveCategoryId === cat.id ? "hal-selected" : ""}`}
                onClick={() => setHalActiveCategoryId(cat.id)}
              >
                <span class="hal-dot" style={{ background: HAL_CATEGORY_PALETTE[i % HAL_CATEGORY_PALETTE.length] }} />
                <span class="hal-cat-name">{cat.name}</span>
                <span class="hal-cat-count">{halNotes.filter((n) => n.categoryId === cat.id).length}</span>
                <button class="hal-cat-remove" onClick={(e) => halHandleDeleteCategory(e, cat.id)}>
                  ×
                </button>
              </div>
            ))}
            <div class="hal-add-category">
              <input
                placeholder="New category"
                value={halNewCategoryName}
                onInput={(e) => setHalNewCategoryName((e.target as HTMLInputElement).value)}
                onKeyDown={(e) => e.key === "Enter" && halHandleAddCategory()}
              />
              <button onClick={halHandleAddCategory}>Add</button>
            </div>
          </div>

          <div class="hal-recent-block">
            <div class="hal-eyebrow-row">
              <p class="hal-eyebrow">Recent</p>
              <button
                class="hal-text-btn"
                onClick={halToggleNoteSelectMode}
                disabled={halOrderedNotes.length === 0}
              >
                {halNoteSelectMode ? "Cancel" : "Select"}
              </button>
            </div>

            {halNoteSelectMode && (
              <div class="hal-select-bar">
                <span>{halSelectedNoteIds.size} selected</span>
                <button
                  class="hal-text-btn hal-text-btn-danger"
                  disabled={halSelectedNoteIds.size === 0}
                  onClick={() => setHalShowBulkDeleteConfirm(true)}
                >
                  Delete
                </button>
              </div>
            )}

            {halOrderedNotes.map((note) => (
              <div
                key={note.id}
                class={`hal-recent-row ${halSelectedNoteId === note.id && !halNoteSelectMode ? "hal-selected" : ""}`}
                onClick={() => halHandleRecentRowClick(note)}
              >
                {halNoteSelectMode && (
                  <span
                    class={`hal-checkbox ${halSelectedNoteIds.has(note.id) ? "hal-checked" : ""}`}
                    aria-label={halSelectedNoteIds.has(note.id) ? "Selected" : "Not selected"}
                  >
                    {halSelectedNoteIds.has(note.id) && <HalIconCheck size={9} />}
                  </span>
                )}
                <span class="hal-dot" style={{ background: halCategoryColor(halCategories, note.categoryId) }} />
                <span class="hal-recent-title">{note.title || "Untitled note"}</span>
                {note.pinned && <span style={{ color: "var(--accent)" }}><HalIconPin size={11} /></span>}
              </div>
            ))}
          </div>
        </aside>

        {halShowBulkDeleteConfirm && (
          <div class="hal-dialog-scrim" onClick={() => setHalShowBulkDeleteConfirm(false)}>
            <div class="hal-dialog" onClick={(e) => e.stopPropagation()}>
              <p class="hal-dialog-title">
                Delete {halSelectedNoteIds.size} note{halSelectedNoteIds.size === 1 ? "" : "s"}?
              </p>
              <p class="hal-dialog-body">This can't be undone.</p>
              <div class="hal-dialog-actions">
                <button
                  class="hal-dialog-btn hal-dialog-btn-cancel"
                  onClick={() => setHalShowBulkDeleteConfirm(false)}
                >
                  Cancel
                </button>
                <button class="hal-dialog-btn hal-dialog-btn-danger" onClick={halHandleBulkDelete}>
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}

        {!halHasSelection && (
          <div class="hal-empty-editor">
            {halNotes.length === 0 ? (
              <>
                <div class="hal-empty-well">✎</div>
                <p class="hal-empty-title">No notes yet</p>
                <p class="hal-empty-body">
                  Click New note to write your first. Pick a category to keep things sorted.
                </p>
              </>
            ) : (
              <>
                <div class="hal-empty-well">✎</div>
                <p class="hal-empty-title">Pick a note to start</p>
                <p class="hal-empty-body">Or click New note to write one.</p>
              </>
            )}
          </div>
        )}
        {
          // Always mounted (just hidden) rather than conditionally rendered —
          // the Tiptap editor attaches to hal-editor-body in a mount effect
          // keyed on [halUser], which runs once right after sign-in. If this
          // section were conditionally rendered on halHasSelection, that div
          // wouldn't exist in the DOM yet on first run, the effect would bail
          // out (element null), and never retry — leaving the editor
          // permanently unmounted, title/category still editable via plain
          // inputs but the rich-text body dead.
        }
        <section class="hal-editor-pane" style={halHasSelection ? undefined : { display: "none" }}>
            <input
              class="hal-title-input"
              placeholder="Untitled note"
              value={halTitle}
              onInput={(e) => setHalTitle((e.target as HTMLInputElement).value)}
            />

            <div class="hal-meta-row">
              <div class="hal-menu-anchor">
                <button
                  class="hal-chip hal-selected"
                  onClick={() => setHalOpenMenu(halOpenMenu === "category" ? null : "category")}
                  style={{ borderColor: "var(--rule)", color: "var(--ink-muted)" }}
                >
                  <span
                    class="hal-dot"
                    style={{ background: halCategoryColor(halCategories, halNoteCategoryId || undefined) }}
                  />
                  {halCategories.find((c) => c.id === halNoteCategoryId)?.name ?? "No category"}
                </button>
                {halOpenMenu === "category" && (
                  <div class="hal-menu">
                    <button class="hal-menu-item" onClick={() => { setHalNoteCategoryId(""); setHalOpenMenu(null); }}>
                      No category
                    </button>
                    {halCategories.map((cat) => (
                      <button
                        key={cat.id}
                        class="hal-menu-item"
                        onClick={() => { setHalNoteCategoryId(cat.id); setHalOpenMenu(null); }}
                      >
                        {cat.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <span class="hal-meta-time">edited {halRelativeTime(halUpdatedAt)}</span>
              <div class="hal-meta-spacer" />

              <button
                class={`hal-icon-btn ${halNotePinned ? "hal-active" : ""}`}
                aria-label="Pin note"
                onClick={() => setHalNotePinned((v) => !v)}
              >
                <HalIconPin />
              </button>
              <div class="hal-menu-anchor">
                <button
                  class="hal-icon-btn"
                  aria-label="More"
                  onClick={() => setHalOpenMenu(halOpenMenu === "overflow" ? null : "overflow")}
                >
                  ⋮
                </button>
                {halOpenMenu === "overflow" && (
                  <div class="hal-menu">
                    <button class="hal-menu-item" onClick={halHandleDuplicate}>
                      Duplicate
                    </button>
                    <button class="hal-menu-item" onClick={halHandleExportMarkdown}>
                      Export as .md
                    </button>
                    <button
                      class="hal-menu-item hal-danger-item"
                      onClick={() => { setHalOpenMenu(null); setHalShowDeleteConfirm(true); }}
                    >
                      Delete note
                    </button>
                  </div>
                )}
              </div>
            </div>

            <div class="hal-toolbar">
              <div class="hal-toolbar-group hal-menu-anchor">
                <button
                  class="hal-dropdown-btn"
                  onClick={() => setHalOpenMenu(halOpenMenu === "blockType" ? null : "blockType")}
                >
                  {halBlockLabel} ⌄
                </button>
                {halOpenMenu === "blockType" && (
                  <div class="hal-menu">
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().setParagraph().run(); setHalOpenMenu(null); })}>Body</button>
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().toggleHeading({ level: 1 }).run(); setHalOpenMenu(null); })}>Heading 1</button>
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().toggleHeading({ level: 2 }).run(); setHalOpenMenu(null); })}>Heading 2</button>
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().toggleHeading({ level: 3 }).run(); setHalOpenMenu(null); })}>Heading 3</button>
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().toggleBlockquote().run(); setHalOpenMenu(null); })}>Quote</button>
                    <button class="hal-menu-item" onClick={halRun(() => { ed?.chain().focus().toggleCodeBlock().run(); setHalOpenMenu(null); })}>Code block</button>
                  </div>
                )}
              </div>

              <div class="hal-toolbar-group" style={{ position: "relative" }}>
                <button
                  class={`hal-icon-btn ${ed?.isActive("bold") ? "hal-active" : ""}`}
                  aria-label="Bold"
                  title="Bold (Ctrl+B)"
                  onClick={halRun(() => ed?.chain().focus().toggleBold().run())}
                >
                  B
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive("italic") ? "hal-active" : ""}`}
                  aria-label="Italic"
                  title="Italic (Ctrl+I)"
                  onClick={halRun(() => ed?.chain().focus().toggleItalic().run())}
                >
                  i
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive("underline") ? "hal-active" : ""}`}
                  aria-label="Underline"
                  title="Underline (Ctrl+U)"
                  onClick={halRun(() => ed?.chain().focus().toggleUnderline().run())}
                >
                  U
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive("link") ? "hal-active" : ""}`}
                  aria-label="Link"
                  title="Link (Ctrl+K)"
                  onClick={halRun(() => {
                    const url = window.prompt("Link URL");
                    if (url) ed?.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
                  })}
                >
                  Lk
                </button>
                <div class="hal-menu-anchor">
                  <button
                    class="hal-icon-btn"
                    aria-label="Text color"
                    onClick={() => setHalOpenMenu(halOpenMenu === "color" ? null : "color")}
                  >
                    A
                    <span class="hal-color-swatch-indicator" />
                  </button>
                  {halOpenMenu === "color" && (
                    <div class="hal-swatch-popover">
                      {HAL_SWATCHES.map((c) => (
                        <button
                          key={c}
                          class="hal-swatch"
                          style={{ background: c }}
                          onClick={halRun(() => { ed?.chain().focus().setColor(c).run(); setHalOpenMenu(null); })}
                        />
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div class="hal-toolbar-group">
                <button
                  class={`hal-icon-btn ${ed?.isActive("bulletList") ? "hal-active" : ""}`}
                  aria-label="Bulleted list"
                  onClick={halRun(() => ed?.chain().focus().toggleBulletList().run())}
                >
                  •
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive("orderedList") ? "hal-active" : ""}`}
                  aria-label="Numbered list"
                  onClick={halRun(() => ed?.chain().focus().toggleOrderedList().run())}
                >
                  1.
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive({ textAlign: "left" }) ? "hal-active" : ""}`}
                  aria-label="Align left"
                  onClick={halRun(() => ed?.chain().focus().setTextAlign("left").run())}
                >
                  ⟸
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive({ textAlign: "center" }) ? "hal-active" : ""}`}
                  aria-label="Align center"
                  onClick={halRun(() => ed?.chain().focus().setTextAlign("center").run())}
                >
                  ⟺
                </button>
                <button
                  class={`hal-icon-btn ${ed?.isActive({ textAlign: "right" }) ? "hal-active" : ""}`}
                  aria-label="Align right"
                  onClick={halRun(() => ed?.chain().focus().setTextAlign("right").run())}
                >
                  ⟹
                </button>
                <div class="hal-menu-anchor">
                  <button
                    class="hal-dropdown-btn"
                    onClick={() => setHalOpenMenu(halOpenMenu === "fontSize" ? null : "fontSize")}
                  >
                    Size ⌄
                  </button>
                  {halOpenMenu === "fontSize" && (
                    <div class="hal-menu">
                      {HAL_FONT_SIZES.map((s) => (
                        <button
                          key={s.label}
                          class="hal-menu-item"
                          onClick={halRun(() => {
                            if (s.value) ed?.chain().focus().setFontSize(s.value).run();
                            else ed?.chain().focus().unsetFontSize().run();
                            setHalOpenMenu(null);
                          })}
                        >
                          {s.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div class="hal-toolbar-group">
                <button class="hal-attach-btn" onClick={() => halFileInputRef.current?.click()}>
                  <HalIconPaperclip /> Attach
                </button>
                <input ref={halFileInputRef} type="file" style={{ display: "none" }} onChange={halHandleFilePick} />
              </div>
            </div>

            <div
              class="hal-body-wrap"
              onDragOver={(e) => { e.preventDefault(); setHalDragOver(true); }}
              onDragLeave={() => setHalDragOver(false)}
              onDrop={halHandleDrop}
            >
              <div ref={halEditorContainerRef} class="hal-editor-body" />
              {halDragOver && <div class="hal-drop-hint">Drop to attach</div>}
            </div>

            {halAttachments.length > 0 && (
              <div class="hal-attachments-section">
                <p class="hal-eyebrow">Attachments · {halAttachments.length}</p>
                <ul class="hal-attachments-list">
                  {halAttachments.map((att) => (
                    <li key={att.driveFileId} class="hal-attachment-card">
                      <div
                        class={`hal-attachment-well ${HAL_PDF_EXT.test(att.name) ? "hal-pdf" : ""} ${HAL_IMAGE_EXT.test(att.name) ? "hal-image" : ""}`}
                      >
                        {halThumbs[att.driveFileId] ? (
                          <img src={halThumbs[att.driveFileId]} />
                        ) : HAL_PDF_EXT.test(att.name) ? (
                          "▤"
                        ) : HAL_IMAGE_EXT.test(att.name) ? (
                          "◱"
                        ) : (
                          "▯"
                        )}
                      </div>
                      <div class="hal-attachment-info">
                        <div class="hal-attachment-name">{att.name}</div>
                        <div class="hal-attachment-size">{halFormatSize(att.size)}</div>
                      </div>
                      <button class="hal-attachment-remove" onClick={() => halRemoveAttachment(att.driveFileId)}>
                        ×
                      </button>
                    </li>
                  ))}
                  {Array.from(halUploadingNames).map((name) => (
                    <li key={name} class="hal-attachment-card">
                      <div class="hal-attachment-well">▯</div>
                      <div class="hal-attachment-info">
                        <div class="hal-attachment-name">{name}</div>
                        <div class="hal-attachment-size hal-uploading-text">uploading…</div>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div style={{ padding: "0 20px 16px", display: "flex", gap: "8px" }}>
              <button class="hal-btn-primary" onClick={halHandleSave} disabled={!halTitle.trim()}>
                {halSelectedNoteId ? "Save changes" : "Create note"}
              </button>
            </div>
          </section>
      </div>

      {halShowDeleteConfirm && (
        <div class="hal-dialog-scrim" onClick={() => setHalShowDeleteConfirm(false)}>
          <div class="hal-dialog" onClick={(e) => e.stopPropagation()}>
            <p class="hal-dialog-title">Delete note?</p>
            <p class="hal-dialog-body">This can't be undone.</p>
            <div class="hal-dialog-actions">
              <button class="hal-dialog-btn hal-dialog-btn-cancel" onClick={() => setHalShowDeleteConfirm(false)}>
                Cancel
              </button>
              <button class="hal-dialog-btn hal-dialog-btn-danger" onClick={halHandleDelete}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      <HalToast toast={toast} position="notes" />
    </div>
  );
}

render(<HalNotesApp />, document.getElementById("hal-root")!);
