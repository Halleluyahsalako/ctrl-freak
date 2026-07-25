// Small, consistent, monochrome icon set (currentColor, stroke-based) —
// deliberately not emoji, which reads as consumer-app/mascot-y and clashes
// with the dev-tool identity the design specs call for.

interface HalIconProps {
  size?: number;
  class?: string;
}

export function HalIconPin({ size = 14, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <path d="M12 17v5" stroke-linecap="round" />
      <path d="M8 3h8l-1 6 3 3v2H6v-2l3-3-1-6Z" stroke-linejoin="round" />
    </svg>
  );
}

export function HalIconExternalLink({ size = 12, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <path d="M7 17 17 7M9 7h8v8" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  );
}

export function HalIconImage({ size = 16, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="9.5" r="1.5" />
      <path d="m21 15-5-5L5 20" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  );
}

export function HalIconDesktop({ size = 11, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <rect x="3" y="4" width="18" height="12" rx="1" />
      <path d="M8 20h8M12 16v4" stroke-linecap="round" />
    </svg>
  );
}

export function HalIconMobile({ size = 11, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <rect x="7" y="2" width="10" height="20" rx="2" />
      <path d="M11 18h2" stroke-linecap="round" />
    </svg>
  );
}

export function HalIconClipboardPlus({ size = 14, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <path d="M9 4h6v2H9z" fill="currentColor" stroke="none" />
      <path d="M12 10v6M9 13h6" stroke-linecap="round" />
    </svg>
  );
}

export function HalIconClipboardOff({ size = 20, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <path d="M9 4h6v2H9z" fill="currentColor" stroke="none" />
      <path d="M4 4l16 16" stroke-linecap="round" />
    </svg>
  );
}

export function HalIconCheck({ size = 14, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class={cls}>
      <path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  );
}

export function HalIconPaperclip({ size = 13, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <path
        d="M21 12.5 12.5 21a4.5 4.5 0 0 1-6.4-6.4L14.6 6a3 3 0 0 1 4.24 4.24l-8.49 8.49a1.5 1.5 0 0 1-2.12-2.12L16 8.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  );
}

export function HalIconAlert({ size = 14, class: cls }: HalIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class={cls}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v5M12 16h.01" stroke-linecap="round" />
    </svg>
  );
}
