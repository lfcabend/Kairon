import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Bold, Heading2, Italic } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Markdown } from "tiptap-markdown";

import { Toggle } from "@/components/ui/toggle";
import type { JournalEntry } from "@/lib/api/types";

import { MoodPicker } from "./MoodPicker";

interface Props {
  entry: JournalEntry;
  autoFocus?: boolean;
  onSave: (patch: { title: string | null; content: string; mood: number | null }) => void;
}

// `tiptap-markdown` doesn't ship a module augmentation for `Editor.storage`,
// so its `markdown` entry isn't typed — this narrows the one shape we use.
function getMarkdown(editor: Editor): string {
  return (editor.storage as unknown as { markdown: { getMarkdown(): string } }).markdown.getMarkdown();
}

/** Tiptap WYSIWYG + 3-button toolbar + mood picker (D1). Save on blur, no autosave-while-typing. */
export function EntryEditor({ entry, autoFocus, onSave }: Props) {
  const [title, setTitle] = useState(entry.title ?? "");
  const [mood, setMood] = useState(entry.mood);

  // useEditor only builds the editor once; `onBlur` below must not close over
  // stale `title`/`mood`/`onSave`, so the actual commit logic is read from a
  // ref that's refreshed every render.
  const commitRef = useRef<() => void>(() => {});

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        blockquote: false,
        bulletList: false,
        code: false,
        codeBlock: false,
        hardBreak: false,
        horizontalRule: false,
        link: false,
        listItem: false,
        listKeymap: false,
        orderedList: false,
        strike: false,
        underline: false,
        heading: { levels: [2] },
      }),
      Markdown.configure({ html: false }),
    ],
    content: entry.content,
    autofocus: autoFocus ? "end" : false,
    // Tiptap v3 defaults to no re-render on transaction; the toolbar's
    // `isActive(...)` checks need one so Bold/Italic/Heading reflect state.
    shouldRerenderOnTransaction: true,
    editorProps: {
      attributes: {
        class:
          "min-h-[4rem] rounded-md border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring [&_h2]:text-base [&_h2]:font-semibold [&_p]:my-1",
      },
    },
    onBlur: () => commitRef.current(),
  });

  useEffect(() => setTitle(entry.title ?? ""), [entry.title]);
  useEffect(() => setMood(entry.mood), [entry.mood]);

  function commit() {
    if (!editor) return;
    onSave({
      title: title.trim() || null,
      content: getMarkdown(editor),
      mood,
    });
  }

  useEffect(() => {
    commitRef.current = commit;
  });

  function commitMood(next: number | null) {
    setMood(next);
    if (!editor) return;
    onSave({ title: title.trim() || null, content: getMarkdown(editor), mood: next });
  }

  return (
    <div className="space-y-2">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        onBlur={commit}
        placeholder="Untitled"
        aria-label="Entry title"
        className="w-full border-none bg-transparent text-sm font-semibold outline-none placeholder:text-muted-foreground"
      />

      <div className="flex items-center gap-1">
        <Toggle
          size="sm"
          aria-label="Bold"
          pressed={editor?.isActive("bold") ?? false}
          onPressedChange={() => editor?.chain().focus().toggleBold().run()}
        >
          <Bold className="h-3.5 w-3.5" />
        </Toggle>
        <Toggle
          size="sm"
          aria-label="Italic"
          pressed={editor?.isActive("italic") ?? false}
          onPressedChange={() => editor?.chain().focus().toggleItalic().run()}
        >
          <Italic className="h-3.5 w-3.5" />
        </Toggle>
        <Toggle
          size="sm"
          aria-label="Heading"
          pressed={editor?.isActive("heading", { level: 2 }) ?? false}
          onPressedChange={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}
        >
          <Heading2 className="h-3.5 w-3.5" />
        </Toggle>
      </div>

      <EditorContent editor={editor} />

      <MoodPicker value={mood} onChange={commitMood} />
    </div>
  );
}
