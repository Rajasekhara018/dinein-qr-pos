import { HQ_DARK_THEME_IDS } from '../hq-theme/hq-theme.service';

/**
 * Chart colours, chosen by the job they do and validated with the dataviz palette validator (validate_palette.js), not by eye.
 *
 * Categorical (series identity): eight hues in a FIXED order, never cycled. Slot n always means the same colour, so a filter that
 * drops a series never repaints the others. More than eight series fold into "Other". Dark mode is its own set of steps.
 *   light, surface #ffffff: band PASS, chroma PASS, CVD worst adjacent ΔE 9.1, normal-vision worst adjacent ΔE 19.6, contrast WARN
 *     (aqua 2.82, yellow 2.17, magenta 2.69 below 3:1) -> relief rule: every chart ships visible labels or a table view.
 *   dark, surfaces #16213e / #181f28 / #241318: every check PASS (CVD 8.4, normal-vision 19.3, all >= 3:1).
 *
 * Sequential (heatmap magnitude): one blue hue, light -> dark (steps 100..700); continuous, so the near-zero end may recede towards
 *   the surface. Lightness is monotone (PASS); adjacent steps are close by design for a continuous ramp. Dark mode runs the same
 *   ramp reversed, so near-zero recedes into the dark surface and high values come forward.
 *
 * Ordinal (funnel stages): one blue hue, stepped.
 *   light 250 -> 650 (#86b6ef,#5598e7,#2a78d6,#1c5cab,#104281), surface #ffffff: monotone, adjacent ΔL >= 0.06, light end 2.11:1 PASS.
 *   dark 550 -> 150 (#1c5cab,#2a78d6,#5598e7,#86b6ef,#b7d3f6), surfaces #16213e 2.40:1, #181f28 2.50:1, #241318 2.68:1 PASS.
 *   (Step 600 failed on #16213e at 1.96:1, so the dark ramp starts at 550.)
 *
 * Status colours are the theme's --hq-tone-* tokens and always come with an icon and a label.
 */

/** Categorical slots 1..8 on a light surface. */
export const HQ_CHART_CATEGORICAL_LIGHT: readonly string[] = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'];

/** Categorical slots 1..8 on a dark surface (the same hues, stepped for dark). */
export const HQ_CHART_CATEGORICAL_DARK: readonly string[] = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'];

/** Most series a chart colours; the rest fold into "Other". */
export const HQ_CHART_MAX_SLOTS = 8;

/** Colour of the folded "Other" series: neutral, so it never reads as a ninth hue. */
export const HQ_CHART_OTHER_LIGHT = '#9aa1ab';
export const HQ_CHART_OTHER_DARK = '#6b7280';

/** Sequential blue ramp, steps 100..700 (13 steps, lightest first). */
export const HQ_CHART_SEQUENTIAL: readonly string[] = [
  '#cde2fb', '#b7d3f6', '#9ec5f4', '#86b6ef', '#6da7ec', '#5598e7', '#3987e5', '#2a78d6', '#256abf', '#1c5cab', '#184f95', '#104281', '#0d366b',
];

/** Ordinal ramp for discrete ordered marks (funnel stages), first stage first. */
export const HQ_CHART_ORDINAL_LIGHT: readonly string[] = ['#104281', '#1c5cab', '#2a78d6', '#5598e7', '#86b6ef'];
export const HQ_CHART_ORDINAL_DARK: readonly string[] = ['#b7d3f6', '#86b6ef', '#5598e7', '#2a78d6', '#1c5cab'];

/** Validated ordinal ranges inside the sequential ramp, for funnels with more stages than the five-step sets (indices into HQ_CHART_SEQUENTIAL). */
const ORDINAL_RANGE_LIGHT: readonly [number, number] = [12, 3]; // 700 .. 250
const ORDINAL_RANGE_DARK: readonly [number, number] = [1, 9]; // 150 .. 550

/** True when the page is in a dark theme (the theme service's mode attribute, or one of the dark theme ids). */
export function hqChartIsDark(root: HTMLElement | null = typeof document !== 'undefined' ? document.documentElement : null): boolean {
  if (!root) {
    return false;
  }
  if (root.getAttribute('data-theme-mode') === 'dark') {
    return true;
  }
  const theme = root.getAttribute('data-theme');
  return !!theme && (HQ_DARK_THEME_IDS as readonly string[]).includes(theme);
}

/** The colour of categorical slot 1..8; anything else is the neutral "Other" colour. */
export function hqCategoricalColor(slot: number, dark: boolean): string {
  const set = dark ? HQ_CHART_CATEGORICAL_DARK : HQ_CHART_CATEGORICAL_LIGHT;
  return slot >= 1 && slot <= HQ_CHART_MAX_SLOTS ? set[slot - 1] : dark ? HQ_CHART_OTHER_DARK : HQ_CHART_OTHER_LIGHT;
}

/** The sequential colour for a value's position 0..1 between the scale's minimum and maximum. */
export function hqSequentialColor(t: number, dark: boolean): string {
  const clamped = Math.min(1, Math.max(0, Number.isFinite(t) ? t : 0));
  const last = HQ_CHART_SEQUENTIAL.length - 1;
  const index = Math.round(clamped * last);
  return HQ_CHART_SEQUENTIAL[dark ? last - index : index];
}

/** Colours for `count` ordered stages, first stage strongest; up to five use the validated five-step sets. */
export function hqOrdinalColors(count: number, dark: boolean): string[] {
  if (count <= 0) {
    return [];
  }
  const preset = dark ? HQ_CHART_ORDINAL_DARK : HQ_CHART_ORDINAL_LIGHT;
  if (count <= preset.length) {
    // Spread across the validated set so two stages still differ clearly.
    return Array.from({ length: count }, (_, i) => preset[count === 1 ? 0 : Math.round((i * (preset.length - 1)) / (count - 1))]);
  }
  const [from, to] = dark ? ORDINAL_RANGE_DARK : ORDINAL_RANGE_LIGHT;
  return Array.from({ length: count }, (_, i) => HQ_CHART_SEQUENTIAL[Math.round(from + ((to - from) * i) / (count - 1))]);
}
