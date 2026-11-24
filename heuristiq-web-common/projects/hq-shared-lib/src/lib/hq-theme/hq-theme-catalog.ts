import { HqThemeId } from './hq-theme.service';

export interface HqThemeCatalogEntry {
  id: HqThemeId;
  name: string;
  mode: 'Light' | 'Dark';
  sidebar: string;
  surface: string;
  bar: string;
  cardBg: string;
  cardBorder: string;
  accent: string;
}

/** Swatches for a theme picker (both apps' Profile pages) -- one per [data-theme] in
 *  theme/styles.scss. */
export const HQ_THEME_CATALOG: HqThemeCatalogEntry[] = [
  { id: 'light', name: 'Classic Light', mode: 'Light', surface: '#f0f4ff', bar: '#ffffff', sidebar: '#0B6E4F', accent: '#D4A017', cardBg: 'rgba(255,255,255,0.9)', cardBorder: 'rgba(11, 110, 79,0.1)' },
  { id: 'dark', name: 'Midnight Dark', mode: 'Dark', surface: '#0c1428', bar: '#111c35', sidebar: '#001029', accent: '#D4A017', cardBg: 'rgba(22,33,62,0.96)', cardBorder: 'rgba(255,255,255,0.07)' },
  { id: 'ocean', name: 'Ocean Blue', mode: 'Light', surface: '#e0f2fe', bar: '#ffffff', sidebar: '#0369a1', accent: '#0284c7', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(3,105,161,0.1)' },
  { id: 'forest', name: 'Emerald Forest', mode: 'Light', surface: '#dcfce7', bar: '#ffffff', sidebar: '#166534', accent: '#16a34a', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(22,101,52,0.1)' },
  { id: 'royal', name: 'Royal Violet', mode: 'Light', surface: '#f5f3ff', bar: '#ffffff', sidebar: '#5b21b6', accent: '#7c3aed', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(109, 40, 217,0.1)' },
  { id: 'rose', name: 'Rose Quartz', mode: 'Light', surface: '#fff1f2', bar: '#ffffff', sidebar: '#9f1239', accent: '#e11d48', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(190, 18, 60,0.1)' },
  { id: 'sunset', name: 'Sunset Amber', mode: 'Light', surface: '#fff7ed', bar: '#ffffff', sidebar: '#9a3412', accent: '#f97316', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(194, 65, 12,0.1)' },
  { id: 'arctic', name: 'Arctic Teal', mode: 'Light', surface: '#f0fdfa', bar: '#ffffff', sidebar: '#0f766e', accent: '#14b8a6', cardBg: 'rgba(255,255,255,0.93)', cardBorder: 'rgba(15, 118, 110,0.1)' },
  { id: 'graphite', name: 'Graphite', mode: 'Dark', surface: '#0b0f14', bar: '#111a24', sidebar: '#111827', accent: '#22d3ee', cardBg: 'rgba(24, 31, 40,0.96)', cardBorder: 'rgba(255,255,255,0.07)' },
  { id: 'obsidian', name: 'Obsidian Gold', mode: 'Dark', surface: '#0a0a0a', bar: '#161616', sidebar: '#1a1a1a', accent: '#D4A017', cardBg: 'rgba(24, 24, 24,0.96)', cardBorder: 'rgba(255,255,255,0.07)' },
  { id: 'indigo', name: 'Indigo Night', mode: 'Dark', surface: '#0b1026', bar: '#141a3d', sidebar: '#1e1b4b', accent: '#818cf8', cardBg: 'rgba(26, 32, 72,0.96)', cardBorder: 'rgba(255,255,255,0.07)' },
  { id: 'pasupu', name: 'Pasupu Kumkuma', mode: 'Light', surface: '#FFFBF3', bar: '#ffffff', sidebar: '#A31621', accent: '#E8A33D', cardBg: 'rgba(255,255,255,0.96)', cardBorder: 'rgba(163,22,33,0.10)' },
  { id: 'pasupu-dark', name: 'Pasupu Kumkuma Dark', mode: 'Dark', surface: '#1A0E10', bar: '#241318', sidebar: '#7A0F18', accent: '#E8A33D', cardBg: 'rgba(36,19,24,0.96)', cardBorder: 'rgba(255,255,255,0.08)' },
];
