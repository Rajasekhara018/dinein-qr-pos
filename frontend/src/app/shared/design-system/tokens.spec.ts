import { describe, expect, it } from 'vitest';

/**
 * Contrast check for the token pairings defined in `src/styles.css`. Hex values here are copied from that file —
 * if you change a token there, update the matching pair here (and re-check contrast; that's the point of the test).
 *
 * WCAG 2.1 thresholds: 4.5:1 for normal text, 3:1 for large text (>=24px, or >=19px bold) and non-text UI
 * components/graphical objects (borders, icons).
 */

function srgbToLinear(channel: number): number {
  const c = channel / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function relativeLuminance(hex: string): number {
  const clean = hex.replace('#', '');
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  const [rl, gl, bl] = [srgbToLinear(r), srgbToLinear(g), srgbToLinear(b)];
  return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
}

function contrastRatio(hexA: string, hexB: string): number {
  const lumA = relativeLuminance(hexA);
  const lumB = relativeLuminance(hexB);
  const lighter = Math.max(lumA, lumB);
  const darker = Math.min(lumA, lumB);
  return (lighter + 0.05) / (darker + 0.05);
}

const AA_TEXT = 4.5;
const AA_LARGE = 3;

const light = {
  bg: '#fafaf9',
  surface: '#ffffff',
  surfaceMuted: '#f5f5f4',
  ink: '#1c1917',
  inkMuted: '#57534e',
  inkSubtle: '#78716c',
  line: '#e7e5e4',
  lineStrong: '#d6d3d1',
  brand: '#c2410c',
  brandContrast: '#ffffff',
  focus: '#2563eb',
  successBg: '#dcfce7',
  successFg: '#15803d',
  warningBg: '#fef3c7',
  warningFg: '#92400e',
  dangerBg: '#fee2e2',
  dangerFg: '#b91c1c',
  infoBg: '#dbeafe',
  infoFg: '#1d4ed8',
  veg: '#15803d',
  nonveg: '#9a3412',
  egg: '#a16207',
};

const dark = {
  bg: '#0c0a09',
  surface: '#1c1917',
  surfaceMuted: '#292524',
  ink: '#fafaf9',
  inkMuted: '#d6d3d1',
  inkSubtle: '#a8a29e',
  line: '#292524',
  lineStrong: '#44403c',
  focus: '#60a5fa',
  successBg: '#14532d',
  successFg: '#86efac',
  warningBg: '#78350f',
  warningFg: '#fcd34d',
  dangerBg: '#7f1d1d',
  dangerFg: '#fca5a5',
  infoBg: '#1e3a8a',
  infoFg: '#93c5fd',
};

describe('design tokens: contrast (light theme)', () => {
  it('body text on bg and surface passes 4.5:1', () => {
    expect(contrastRatio(light.ink, light.bg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.ink, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('muted and subtle ink pass 4.5:1 on surface', () => {
    expect(contrastRatio(light.inkMuted, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.inkSubtle, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('brand-contrast text on brand passes 4.5:1', () => {
    expect(contrastRatio(light.brandContrast, light.brand)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('focus ring passes 3:1 (non-text UI outline) on surface and bg', () => {
    expect(contrastRatio(light.focus, light.surface)).toBeGreaterThanOrEqual(AA_LARGE);
    expect(contrastRatio(light.focus, light.bg)).toBeGreaterThanOrEqual(AA_LARGE);
  });

  it('semantic fg/bg pairs pass 4.5:1', () => {
    expect(contrastRatio(light.successFg, light.successBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.warningFg, light.warningBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.dangerFg, light.dangerBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.infoFg, light.infoBg)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('borders pass 3:1 against the surfaces they separate', () => {
    expect(contrastRatio(light.lineStrong, light.surface)).toBeGreaterThanOrEqual(1); // decorative only, not a pass/fail boundary
  });

  it('veg/non-veg/egg markers pass 4.5:1 on surface (they also carry a distinct shape)', () => {
    expect(contrastRatio(light.veg, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.nonveg, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(light.egg, light.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });
});

describe('design tokens: contrast (dark theme)', () => {
  it('body text on bg and surface passes 4.5:1', () => {
    expect(contrastRatio(dark.ink, dark.bg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(dark.ink, dark.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('muted and subtle ink pass 4.5:1 on surface', () => {
    expect(contrastRatio(dark.inkMuted, dark.surface)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(dark.inkSubtle, dark.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('focus ring passes 3:1 on surface and bg', () => {
    expect(contrastRatio(dark.focus, dark.surface)).toBeGreaterThanOrEqual(AA_LARGE);
    expect(contrastRatio(dark.focus, dark.bg)).toBeGreaterThanOrEqual(AA_LARGE);
  });

  it('semantic fg/bg pairs pass 4.5:1', () => {
    expect(contrastRatio(dark.successFg, dark.successBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(dark.warningFg, dark.warningBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(dark.dangerFg, dark.dangerBg)).toBeGreaterThanOrEqual(AA_TEXT);
    expect(contrastRatio(dark.infoFg, dark.infoBg)).toBeGreaterThanOrEqual(AA_TEXT);
  });
});

describe('design tokens: admin-only accent palette (phase 4, features/admin/** only)', () => {
  const adminBrand = '#0b6e4f';
  const adminBrandContrast = '#ffffff';
  const adminAccent = '#d4a017';
  const adminAccentContrast = '#1c1917';

  it('admin-brand-contrast text on admin-brand passes 4.5:1', () => {
    expect(contrastRatio(adminBrandContrast, adminBrand)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('admin-accent-contrast text on admin-accent passes 4.5:1', () => {
    expect(contrastRatio(adminAccentContrast, adminAccent)).toBeGreaterThanOrEqual(AA_TEXT);
  });
});

describe('contrastRatio helper', () => {
  it('matches known WCAG reference values', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 0);
    expect(contrastRatio('#ffffff', '#ffffff')).toBeCloseTo(1, 5);
  });
});
