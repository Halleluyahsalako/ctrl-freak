import { Extension } from "@tiptap/core";
import "@tiptap/extension-text-style";

// Tiptap core has no font-size extension — this is the standard community
// pattern: extend the textStyle mark with a fontSize attribute serialized
// as inline CSS, since markdown itself has no concept of font size (this
// note property won't round-trip through the markdown storage format,
// same limitation any markdown-backed editor has).
declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    halFontSize: {
      setFontSize: (size: string) => ReturnType;
      unsetFontSize: () => ReturnType;
    };
  }
}

export const HalFontSize = Extension.create({
  name: "halFontSize",

  addOptions() {
    return { types: ["textStyle"] };
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          fontSize: {
            default: null,
            parseHTML: (element) => element.style.fontSize || null,
            renderHTML: (attributes) => {
              if (!attributes.fontSize) return {};
              return { style: `font-size: ${attributes.fontSize}` };
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      setFontSize:
        (size: string) =>
        ({ chain }) =>
          chain().setMark("textStyle", { fontSize: size }).run(),
      unsetFontSize:
        () =>
        ({ chain }) =>
          chain().setMark("textStyle", { fontSize: null }).run(),
    };
  },
});
