import { useCallback, useState } from "preact/hooks";

// docs/ctrl-freak-extension-ui-spec.md §5 — a custom toast, not a
// browser/Material default. Each page (popup, notes) owns its own toast
// state and positions the host differently — they never need to talk to
// each other.

export type HalToastKind = "success" | "error" | "neutral";

interface HalToastState {
  id: number;
  message: string;
  kind: HalToastKind;
}

export function useHalToast() {
  const [toast, setToast] = useState<HalToastState | null>(null);

  const show = useCallback((message: string, kind: HalToastKind = "success") => {
    const id = Date.now();
    setToast({ id, message, kind });
    setTimeout(() => {
      setToast((current) => (current?.id === id ? null : current));
    }, 2200);
  }, []);

  return { toast, show };
}

const HAL_TOAST_ICON: Record<HalToastKind, string> = {
  success: "✓",
  error: "!",
  neutral: "✓",
};

export function HalToast({ toast, position }: { toast: HalToastState | null; position: "popup" | "notes" }) {
  if (!toast) return null;
  return (
    <div class={`hal-toast hal-toast-${position} hal-toast-${toast.kind}`}>
      <span class="hal-toast-icon">{HAL_TOAST_ICON[toast.kind]}</span>
      <span>{toast.message}</span>
    </div>
  );
}
