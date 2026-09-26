/** Colour helpers used to apply a restaurant's brand colour while keeping WCAG AA contrast. */

export interface BrandPalette {
  /** The brand colour itself (backgrounds of primary buttons, chips…). */
  brand: string;
  /** Text/icon colour on top of `brand` (white or near-black, whichever contrasts better). */
  contrast: string;
  /** Brand colour darkened until it reaches 4.5:1 on white — for brand-coloured text/links. */
  ink: string;
}

const HEX = /^#?([0-9a-f]{6})$/i;

export function parseHex(hex: string): [number, number, number] | null {
  const match = HEX.exec(hex.trim());
  if (!match) return null;
  const n = parseInt(match[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function toHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map((c) => Math.round(c).toString(16).padStart(2, '0')).join('')}`;
}

export function relativeLuminance([r, g, b]: [number, number, number]): number {
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

export function contrastRatio(a: [number, number, number], b: [number, number, number]): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

const WHITE: [number, number, number] = [255, 255, 255];
const INK: [number, number, number] = [28, 25, 23];

/** Derives a readable palette from `#RRGGBB`; returns null for invalid input. */
export function brandPalette(hex: string | null | undefined): BrandPalette | null {
  if (!hex) return null;
  const rgb = parseHex(hex);
  if (!rgb) return null;
  const contrast = contrastRatio(rgb, WHITE) >= contrastRatio(rgb, INK) ? '#ffffff' : '#1c1917';
  let ink: [number, number, number] = rgb;
  for (let i = 0; i < 20 && contrastRatio(ink, WHITE) < 4.5; i++) {
    ink = [ink[0] * 0.9, ink[1] * 0.9, ink[2] * 0.9];
  }
  return { brand: toHex(rgb), contrast, ink: toHex(ink) };
}
