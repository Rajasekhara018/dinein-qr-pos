import type { ApexOptions } from 'apexcharts';
import {
  HQ_CHART_CATEGORICAL_DARK, HQ_CHART_CATEGORICAL_LIGHT, HQ_CHART_MAX_SLOTS, HQ_CHART_SEQUENTIAL, hqCategoricalColor, hqChartIsDark,
  hqOrdinalColors,
} from './hq-chart.palette';
import { hqChartNodesToApex } from './hq-chart.transforms';

/**
 * Every chart is drawn by ApexCharts (ng-apexcharts) with one shared theme from {@link buildChartOptions}. Series shapes per type:
 *
 * | type                                   | `series`                                                     | `labels`               |
 * |----------------------------------------|--------------------------------------------------------------|------------------------|
 * | area, line, bar                        | `HqChartSeries[]`, `data: number[]` (per-series `type` mixes line/area/column on ONE y-axis) | x categories |
 * | donut, pie, radialBar, polarArea       | `number[]` (one value per label)                             | slice / ring names     |
 * | funnel                                 | `number[]` stage values, first stage first                   | stage names            |
 * | heatmap                                | `HqChartSeries[]`: one series per ROW, `data: (number\|null)[]` per column | column names |
 * | radar                                  | `HqChartSeries[]`, `data: number[]` per axis                | the radar's axes       |
 * | treemap                                | `HqChartSeries[]`, `data: { x: string; y: number }[]`       | —                      |
 * | scatter                                | `HqChartSeries[]`, `data: [x, y][]`                         | —                      |
 * | bubble                                 | `HqChartSeries[]`, `data: [x, y, size][]`                   | —                      |
 * | rangeBar (timeline)                    | `HqChartSeries[]`, `data: { x: string; y: [start, end] }[]` (epoch ms or numbers) | — |
 * | boxPlot                                | `HqChartSeries[]`, `data: { x: string; y: [min, q1, median, q3, max] }[]` | — |
 * | candlestick                            | `HqChartSeries[]`, `data: { x: string\|number; y: [open, high, low, close] }[]` | — |
 * | column, mixed                          | as bar (column = vertical bars; mixed = per-series `type` line / area / column, ONE y axis) | x categories |
 * | rangeArea                              | `HqChartSeries[]`, `data: { x: string\|number; y: [low, high] }[]` (a `type: 'line'` series may join) | — |
 * | dumbbell                               | `HqChartSeries[]`, `data: { x: string; y: [start, end] }[]`; `labels` = the two measures' names | — |
 * | gauge                                  | `number[]`, one value per ring (`min` / `max` set the scale, default 0..100) | ring names |
 * | pyramid                                | `number[]` stage values, base (largest) first — drawn as an upside-down funnel | stage names |
 * | histogram                              | `HqChartSeries[]`, `data: number[]` of RAW observations, binned by ApexCharts (`bins` / `binWidth`) | — |
 * | waterfall                              | `HqChartSeries[]`, `data: { x: string; y?: number; isSubtotal?: true; isTotal?: true }[]` (y = signed step) | — |
 * | streamgraph                            | `HqChartSeries[]`, `data: number[]` per layer                | x categories           |
 * | violin, raincloud                      | `HqChartSeries[]`, `data: { x: string; y: number[] }[]` (raw observations per group; density by ApexCharts) | — |
 * | beeswarm                               | `HqChartSeries[]`, `data: { x: string; y: number[] }[]` (a strip plot; points spread so they do not overlap) | — |
 * | waffle, unit, pictogram                | `number[]` counts (waffle: share of a 10×10 grid; unit / pictogram: one mark per `unitValue`) | category names |
 * | icicle, sunburst                       | ONE `HqChartSeries` whose `data` is `HqChartNode[]` (a hierarchy) | —             |
 *
 * unit, waffle, pictogram and raincloud are ApexCharts PREMIUM types: without a licence key (HQ_CHART_LICENSE) they draw with
 * the ApexCharts trial watermark. Every other type is free.
 *
 * There is never a second value axis: two measures of different scale are two charts.
 */
export type HqChartType =
  | 'area' | 'line' | 'bar' | 'column' | 'mixed' | 'donut' | 'pie' | 'radialBar' | 'gauge' | 'polarArea' | 'heatmap' | 'treemap'
  | 'radar' | 'scatter' | 'bubble' | 'rangeBar' | 'rangeArea' | 'dumbbell' | 'boxPlot' | 'candlestick' | 'funnel' | 'pyramid'
  | 'histogram' | 'waterfall' | 'streamgraph' | 'violin' | 'raincloud' | 'beeswarm' | 'waffle' | 'unit' | 'pictogram'
  | 'icicle' | 'sunburst';

/** Axis-based chart types (the gallery's "Axis" family). */
export const HQ_CHART_AXIS_TYPES: readonly HqChartType[] = [
  'line', 'area', 'bar', 'column', 'mixed', 'scatter', 'bubble', 'heatmap', 'candlestick', 'boxPlot', 'histogram', 'violin',
  'rangeBar', 'rangeArea', 'streamgraph', 'waterfall', 'dumbbell', 'treemap', 'raincloud', 'beeswarm',
];

/** Chart types without value axes (the gallery's "Non-axis" family). */
export const HQ_CHART_NON_AXIS_TYPES: readonly HqChartType[] = [
  'pie', 'donut', 'radialBar', 'gauge', 'radar', 'polarArea', 'funnel', 'pyramid', 'sunburst', 'icicle', 'unit', 'pictogram', 'waffle',
];

/** ApexCharts premium types: they draw with a trial watermark unless HQ_CHART_LICENSE holds a premium licence key. */
export const HQ_CHART_PREMIUM_TYPES: readonly HqChartType[] = ['unit', 'pictogram', 'waffle', 'raincloud'];

/** One node of a hierarchy (icicle, sunburst): a branch without a value of its own takes the sum of its children. */
export interface HqChartNode {
  name: string;
  value?: number;
  /** Categorical slot 1..8 of a top-level branch; its descendants are tints of that colour. */
  slot?: number;
  children?: HqChartNode[];
}

/** A threshold band along a gauge's arc. */
export interface HqChartBand {
  from: number;
  to: number;
  color: string;
  label?: string;
}

/** How a number is written on an axis, a tooltip or in the middle of a donut. */
export type HqChartFormat = 'number' | 'currency' | 'percent';

/** How the x axis reads its values (time series zoom only on `datetime`). */
export type HqChartXType = 'category' | 'datetime' | 'numeric';

/** One point of a series: a number (or null for a gap / empty heatmap cell), an [x, y] / [x, y, size] tuple, or an {x, y} object. */
export type HqChartDatum =
  | number
  | null
  | [number, number]
  | [number, number, number]
  | { x: string | number; y: number | null | number[]; fillColor?: string }
  | { x: string | number; y?: number | null; isSubtotal?: boolean; isTotal?: boolean }
  | HqChartNode;

/** One named series. */
export interface HqChartSeries {
  name: string;
  data: HqChartDatum[];
  /**
   * Categorical colour slot 1..8 that belongs to this entity. Give it whenever a filter can change which series are shown, so a
   * series keeps its colour (colour follows the entity, never its rank). Without it the series takes the slot of its position.
   */
  slot?: number;
  /** Mixed chart: draw this series as a line, area or column on the same (single) y axis. */
  type?: 'line' | 'area' | 'column' | 'bar';
}

/** A reference line across the plot (a target, a limit). */
export interface HqChartGoal {
  value: number;
  label?: string;
}

/** What one chart shows. See the table above for the series shape of each type. */
export interface HqChartSpec {
  type: HqChartType;
  series: number[] | HqChartSeries[];
  labels?: string[];
  height?: number;
  format?: HqChartFormat;
  /** ISO currency code for `format: 'currency'`. */
  currency?: string;
  stacked?: boolean;
  /** Bars lie down (a ranking) instead of standing up. */
  horizontal?: boolean;
  /** A donut's centre: the caption and the figure (for example "Total" and "128"). */
  centerLabel?: string;
  centerValue?: string;
  /** A bare line with no axes, grid, toolbar or tooltip chrome: the trend line inside a KPI card. */
  sparkline?: boolean;
  /** Per-label colour slots 1..8 (donut, pie, radial, polar area, distributed bars). */
  slots?: number[];
  /** A single bar series where each bar is its own entity with its own colour (by `slots`, else by position). */
  distributed?: boolean;
  /** Legend on or off. Default: shown for two or more series (or labels of a donut/pie), none for one series. */
  showLegend?: boolean;
  /** Value labels on the marks. Off by default; turn on only for a few marks (a short ranking), never a number on every point. */
  dataLabels?: boolean;
  /** Axis titles. */
  xTitle?: string;
  yTitle?: string;
  /** A reference line (target, limit). */
  goal?: HqChartGoal;
  /** How the x axis reads its values; `datetime` turns on zoom in the toolbar. */
  xType?: HqChartXType;
  /** The ApexCharts toolbar (download PNG / SVG / CSV, and zoom for time series). On by default; off for sparklines. */
  toolbar?: boolean;
  /** Heatmap: the value range of the colour scale (defaults to the data's own minimum and maximum). */
  min?: number;
  max?: number;
  /** Extra text after each label's value in its data label (funnel conversion, bar-list share); one per label. */
  stageNotes?: string[];
  /** Per-label fill colours that override the categorical slots (hq-bar-list's status mode). */
  colors?: string[];
  /** Heatmap: the tooltip text of one cell (defaults to the formatted value). */
  cellLabel?: (row: string, col: string, value: number | null) => string;
  /** Histogram: a fixed number of bins (default: ApexCharts' 'auto' rule), or a fixed bin width in value units (wins). */
  bins?: number;
  binWidth?: number;
  /** Unit / pictogram: one mark stands for this many items (default 1). */
  unitValue?: number;
  /** Pictogram: the glyph as SVG path data in a 24×24 box (default: a person). */
  glyph?: string;
  /** Gauge: threshold bands along the arc. */
  bands?: HqChartBand[];
  /** Gauge: a needle instead of a filled arc. */
  needle?: boolean;
}

/** The colours and font a chart takes from the page's theme. */
export interface HqChartPalette {
  /** Categorical slots 1..8 for the current mode. */
  colors: string[];
  text: string;
  muted: string;
  grid: string;
  dark: boolean;
  fontFamily: string;
  /** The chart surface (card background): the 2px gaps between marks and the ring round slices. */
  surface?: string;
  /** Label of the folded series past the eighth ("Other"). */
  otherLabel?: string;
  /** Label of an empty heatmap cell ("No data"). */
  noDataLabel?: string;
  /** ApexCharts licence key (premium types draw without the trial watermark). */
  license?: string | null;
}

export const HQ_CHART_DEFAULT_HEIGHT = 280;
export const HQ_CHART_SPARKLINE_HEIGHT = 48;

/** Most series a scatter or bubble chart colours apart (every pair of marks can be neighbours, so only three slots validate). */
export const HQ_CHART_SCATTER_MAX_SERIES = 3;

/** Buckets of a heatmap's colour scale (and its legend). */
export const HQ_CHART_HEATMAP_BUCKETS = 6;

/** Value standing in for an empty heatmap cell, drawn in the no-data colour. */
export const HQ_CHART_HEATMAP_EMPTY = -Number.MAX_SAFE_INTEGER;

const FALLBACK = { text: '#5a6a7e' };
/** Ink inside a categorical fill (treemap tiles): white, except near-black on the yellow slot (palette reference). */
const INSIDE_FILL_INK = '#ffffff';
const INSIDE_YELLOW_INK = '#0b0b0b';
const YELLOW_SLOT = 4;

/** Parses #rgb, #rrggbb or rgb()/rgba() into [r, g, b]; null for anything else (for example an unresolved color-mix). */
export function parseColor(value: string): [number, number, number] | null {
  const text = value.trim().toLowerCase();
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/.exec(text);
  if (hex) {
    const h = hex[1].length === 3 ? hex[1].split('').map((c) => c + c).join('') : hex[1];
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
  }
  const rgb = /^rgba?\(\s*(\d+)[\s,]+(\d+)[\s,]+(\d+)/.exec(text);
  return rgb ? [Number(rgb[1]), Number(rgb[2]), Number(rgb[3])] : null;
}

/** The colour lightened towards white by {@code amount} (0..1); the input is returned unchanged when it cannot be parsed. */
export function tint(color: string, amount: number): string {
  const rgb = parseColor(color);
  if (!rgb) {
    return color;
  }
  const [r, g, b] = rgb.map((c) => Math.round(c + (255 - c) * amount));
  return `#${[r, g, b].map((c) => c.toString(16).padStart(2, '0')).join('')}`;
}

function toHex(rgb: [number, number, number]): string {
  return `#${rgb.map((c) => c.toString(16).padStart(2, '0')).join('')}`;
}

function read(style: CSSStyleDeclaration, name: string, fallback: string): string {
  const value = style.getPropertyValue(name).trim();
  return value && parseColor(value) ? value : fallback;
}

/**
 * The chart palette of the page as it is now: the validated categorical slots for the current mode (light and dark are separate,
 * selected steps), ink and grid from the theme, and the card surface for the gaps between marks. Read again whenever the theme
 * or its mode changes.
 */
export function readChartPalette(root: HTMLElement = document.documentElement): HqChartPalette {
  const style = getComputedStyle(root);
  const dark = hqChartIsDark(root);
  const panel = parseColor(style.getPropertyValue('--workspace-panel').trim());
  return {
    colors: [...(dark ? HQ_CHART_CATEGORICAL_DARK : HQ_CHART_CATEGORICAL_LIGHT)],
    text: read(style, '--edu-ink', dark ? '#e2e8f0' : '#1b2a4a'),
    muted: read(style, '--edu-muted', FALLBACK.text),
    grid: dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.07)',
    dark,
    fontFamily: (root.ownerDocument?.body ? getComputedStyle(root.ownerDocument.body).fontFamily : style.fontFamily) || "'Outfit', 'Poppins', sans-serif",
    surface: panel ? toHex(panel) : dark ? '#16213e' : '#ffffff',
  };
}

/** 12500 is "12.5k", 1.2 crore is "1.2Cr" and so on, the way an axis wants it. */
export function compactNumber(value: number): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0';
  }
  const abs = Math.abs(value);
  if (abs >= 10_000_000) return `${(value / 10_000_000).toFixed(1)}Cr`;
  if (abs >= 100_000) return `${(value / 100_000).toFixed(1)}L`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
  return `${Math.round(value * 100) / 100}`;
}

/** The figure as text: an exact amount for a tooltip, or the short form for an axis. */
export function formatChartValue(value: number, format: HqChartFormat = 'number', currency = 'INR', compact = false): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '';
  }
  if (format === 'percent') {
    return `${Math.round(value * 10) / 10}%`;
  }
  if (format === 'currency') {
    try {
      return compact ? `${new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(0).replace(/[\d.,\s]/g, '')}${compactNumber(value)}`
        : new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value);
    } catch {
      return `${currency} ${compact ? compactNumber(value) : value}`;
    }
  }
  return compact ? compactNumber(value) : new Intl.NumberFormat('en-IN').format(value);
}

/** Types whose series is a plain list of numbers with one label each. */
const LABELLED_TYPES: readonly HqChartType[] = ['donut', 'pie', 'radialBar', 'gauge', 'polarArea', 'funnel', 'pyramid', 'waffle', 'unit', 'pictogram'];

/** The ApexCharts `chart.type` that draws one of our types. */
function apexTypeOf(type: HqChartType): NonNullable<NonNullable<ApexOptions['chart']>['type']> {
  switch (type) {
    case 'funnel': case 'pyramid': case 'column': case 'mixed': return 'bar';
    case 'beeswarm': return 'scatter';
    case 'pictogram': return 'unit';
    default: return type;
  }
}

/** A person, in a 24×24 box: the default pictogram glyph. */
export const HQ_CHART_PERSON_GLYPH = 'M12 11.5a4.25 4.25 0 1 0 0-8.5 4.25 4.25 0 0 0 0 8.5Zm-7.5 9.5a7.5 7.5 0 0 1 15 0Z';

/** True when there is nothing to draw (no series, or every value is zero / empty). */
export function chartIsEmpty(spec: HqChartSpec): boolean {
  if (!spec.series || spec.series.length === 0) {
    return true;
  }
  if (LABELLED_TYPES.includes(spec.type)) {
    return (spec.series as number[]).every((v) => !v);
  }
  if (spec.type === 'heatmap') {
    return (spec.series as HqChartSeries[]).every((s) => !s.data || s.data.every((v) => v === null || v === undefined));
  }
  return (spec.series as HqChartSeries[]).every((s) => !s.data || s.data.length === 0);
}

/** The colour slot of position `index` (0-based): the given slot, else the position. */
function slotAt(slots: readonly (number | undefined)[] | undefined, index: number): number {
  const given = slots?.[index];
  return given && given >= 1 && given <= HQ_CHART_MAX_SLOTS ? given : index + 1;
}

/** Labelled values with everything past the seventh folded into "Other", so there are at most eight colours. */
function foldLabelled(values: number[], labels: string[], slots: number[] | undefined, other: string):
  { values: number[]; labels: string[]; slots: number[] } {
  if (values.length <= HQ_CHART_MAX_SLOTS) {
    return { values, labels, slots: values.map((_, i) => slotAt(slots, i)) };
  }
  const keep = HQ_CHART_MAX_SLOTS - 1;
  const rest = values.slice(keep).reduce((sum, v) => sum + (v || 0), 0);
  return {
    values: [...values.slice(0, keep), rest],
    labels: [...labels.slice(0, keep), other],
    slots: [...values.slice(0, keep).map((_, i) => slotAt(slots, i)), 0],
  };
}

/** Numeric series with everything past the seventh summed into one "Other" series, so there are at most eight. */
function foldSeries(series: HqChartSeries[], other: string, max: number = HQ_CHART_MAX_SLOTS): HqChartSeries[] {
  if (series.length <= max) {
    return series;
  }
  const keep = max - 1;
  const rest = series.slice(keep);
  const numeric = rest.every((s) => s.data.every((d) => typeof d === 'number' || d === null));
  if (numeric) {
    const length = Math.max(...rest.map((s) => s.data.length));
    const data = Array.from({ length }, (_, i) => rest.reduce((sum, s) => sum + ((s.data[i] as number | null) || 0), 0));
    return [...series.slice(0, keep), { name: other, data, slot: 0 }];
  }
  // Points (scatter, bubble): the folded series simply holds every remaining point.
  return [...series.slice(0, keep), { name: other, data: rest.flatMap((s) => s.data), slot: 0 }];
}

/** Apex series objects: our extra fields (slot) removed, mixed-chart `type` kept ('column' is Apex's name for a bar series). */
function apexSeries(series: HqChartSeries[]): NonNullable<ApexOptions['series']> {
  return series.map((s) => ({ name: s.name, data: s.data as never, ...(s.type ? { type: s.type === 'bar' ? 'column' : s.type } : {}) }));
}

/** The ApexCharts toolbar: downloads always, zoom only on a time series. */
function toolbarFor(spec: HqChartSpec, zoomable: boolean): NonNullable<ApexOptions['chart']>['toolbar'] {
  const show = !spec.sparkline && (spec.toolbar ?? true);
  return {
    show,
    tools: { download: true, selection: false, zoom: zoomable, zoomin: zoomable, zoomout: zoomable, pan: false, reset: zoomable },
    export: { csv: { headerCategory: spec.xTitle ?? '', headerValue: spec.yTitle ?? '' } },
  };
}

/**
 * The ApexCharts options for a spec in the page's palette — the one theme every chart in every app shares. It follows the chart
 * marks spec: thin bars with a 4px rounded data end and 2px surface gaps between neighbouring fills, 2px lines, markers only on
 * hover (8px), a crosshair and shared tooltip on line/area and a per-mark tooltip elsewhere, a recessive dashed grid, one value
 * axis, a legend only for two or more series, value text in ink (never the series colour), the toolbar on by default, and no
 * animation when the user asked for reduced motion.
 */
export function buildChartOptions(spec: HqChartSpec, palette: HqChartPalette, reducedMotion: boolean): ApexOptions {
  // Aliases drawn by another branch: column / mixed are bar charts standing up; a pyramid is a funnel with its base at the bottom.
  if (spec.type === 'column' || spec.type === 'mixed') {
    return buildChartOptions({ ...spec, type: 'bar', horizontal: false }, palette, reducedMotion);
  }
  if (spec.type === 'pyramid') {
    const values = [...(spec.series as number[])].reverse();
    return buildChartOptions({
      ...spec, type: 'funnel', series: values, labels: [...(spec.labels ?? [])].reverse(),
      stageNotes: spec.stageNotes ? [...spec.stageNotes].reverse() : undefined,
    }, palette, reducedMotion);
  }
  const format = spec.format ?? 'number';
  const money = (value: number, compact = false): string => formatChartValue(value, format, spec.currency, compact);
  const sparkline = !!spec.sparkline;
  const surface = palette.surface ?? (palette.dark ? '#16213e' : '#ffffff');
  const other = palette.otherLabel ?? 'Other';
  const otherColor = hqCategoricalColor(0, palette.dark);
  const colorOf = (slot: number): string => (slot >= 1 && slot <= palette.colors.length ? palette.colors[slot - 1] : otherColor);
  const legendShown = (count: number): boolean => !sparkline && (spec.showLegend ?? count >= 2);
  const dateTime = spec.xType === 'datetime';
  const zoomable = dateTime && ['area', 'line', 'bar', 'scatter', 'bubble', 'candlestick', 'rangeBar', 'rangeArea', 'boxPlot'].includes(spec.type);
  const apexType = apexTypeOf(spec.type);

  const base: ApexOptions = {
    chart: {
      type: apexType,
      height: spec.height ?? (sparkline ? HQ_CHART_SPARKLINE_HEIGHT : HQ_CHART_DEFAULT_HEIGHT),
      fontFamily: palette.fontFamily,
      foreColor: palette.muted,
      background: 'transparent',
      toolbar: toolbarFor(spec, zoomable),
      zoom: { enabled: zoomable, type: 'x' },
      sparkline: { enabled: sparkline },
      stacked: !!spec.stacked,
      animations: { enabled: !reducedMotion, speed: 320 },
      ...(palette.license ? { license: palette.license } : {}),
    },
    theme: { mode: palette.dark ? 'dark' : 'light' },
    colors: palette.colors,
    dataLabels: {
      enabled: !!spec.dataLabels,
      formatter: (value: number) => money(Number(value), true),
      style: { colors: [palette.text], fontSize: '11px', fontWeight: 600 },
      background: { enabled: false },
      dropShadow: { enabled: false },
    },
    legend: { show: false, position: 'bottom', fontSize: '12px', markers: { size: 6 }, labels: { colors: palette.muted }, itemMargin: { horizontal: 8 } },
    tooltip: { theme: palette.dark ? 'dark' : 'light', y: { formatter: (value: number) => money(value) } },
    grid: { borderColor: palette.grid, strokeDashArray: 4, padding: { left: 8, right: 8 } },
    stroke: { curve: 'smooth', width: 2, lineCap: 'round' },
    states: { active: { filter: { type: 'none' } } },
  };
  const titleStyle = { color: palette.muted, fontSize: '11px', fontWeight: 500 };

  // ── Gauge: a half-circle radial bar on the spec's min..max scale ─────────────────────────────────────────────────────────────
  if (spec.type === 'gauge') {
    const folded = foldLabelled(spec.series as number[], spec.labels ?? [], spec.slots, other);
    return {
      ...base,
      series: folded.values, labels: folded.labels,
      colors: spec.colors?.length ? spec.colors : folded.slots.map(colorOf),
      legend: { ...base.legend, show: legendShown(folded.values.length) },
      stroke: { lineCap: 'round' },
      plotOptions: {
        radialBar: {
          startAngle: -90, endAngle: 90, min: spec.min ?? 0, max: spec.max ?? 100, shape: spec.needle ? 'needle' : 'arc',
          hollow: { size: '62%' }, track: { background: palette.grid, strokeWidth: '100%' },
          ...(spec.bands?.length ? { bands: spec.bands } : {}),
          ticks: { show: !!spec.bands?.length, labels: { show: !!spec.bands?.length, color: palette.muted, fontSize: '10px' } },
          dataLabels: {
            name: { fontSize: '12px', color: palette.muted, offsetY: 22 },
            value: { fontSize: '24px', fontWeight: 700, color: palette.text, offsetY: -14, formatter: (v: number) => money(v) },
          },
        },
      },
    } as ApexOptions;
  }

  // ── Unit, waffle, pictogram: one mark per unit of a count (ApexCharts premium types) ─────────────────────────────────────────
  if (spec.type === 'unit' || spec.type === 'waffle' || spec.type === 'pictogram') {
    const folded = foldLabelled(spec.series as number[], spec.labels ?? [], spec.slots, other);
    const unit: Record<string, unknown> = spec.type === 'waffle'
      ? { layout: 'grid', shape: 'square', borderRadius: 2, grid: { columns: 10, total: 100 } }
      : spec.type === 'pictogram'
        ? { layout: 'grouped', shape: 'pictogram', pictogram: { mark: { path: spec.glyph ?? HQ_CHART_PERSON_GLYPH, viewBox: '0 0 24 24' }, fallback: 'circle' }, unitValue: spec.unitValue ?? 1 }
        : { layout: 'grouped', shape: 'circle', unitValue: spec.unitValue ?? 1 };
    return {
      ...base,
      series: folded.values, labels: folded.labels,
      colors: spec.colors?.length ? spec.colors : folded.slots.map(colorOf),
      legend: { ...base.legend, show: legendShown(folded.values.length) },
      tooltip: { ...base.tooltip, fillSeriesColor: false },
      plotOptions: { unit } as ApexOptions['plotOptions'],
    };
  }

  // ── Hierarchies: icicle and sunburst; top-level branches take categorical slots, deeper levels are tints of them ───────────────
  if (spec.type === 'icicle' || spec.type === 'sunburst') {
    const source = (spec.series as HqChartSeries[])[0];
    const nodes = hqChartNodesToApex((source?.data ?? []) as HqChartNode[],
      (slot: number | undefined, i: number) => colorOf(slotAt(slot ? [slot] : undefined, slot ? 0 : Math.min(i, HQ_CHART_MAX_SLOTS - 1))));
    const labels = { show: true, style: { fontSize: '11px', fontWeight: 600, colors: [INSIDE_FILL_INK] } };
    const partition = spec.type === 'icicle'
      ? { icicle: { direction: spec.horizontal ? 'right' : 'down', spacing: 2, borderRadius: 4, tint: 0.28, dataLabels: { ...labels, minSizeToShow: 28 } } }
      : { sunburst: { innerSize: '18%', spacing: 2, borderRadius: 4, tint: 0.28, dataLabels: { ...labels, minAngleToShow: 12 } } };
    return {
      ...base,
      series: [{ name: source?.name ?? '', data: nodes }] as ApexOptions['series'],
      legend: { show: false },
      tooltip: { ...base.tooltip, fillSeriesColor: false },
      plotOptions: partition as ApexOptions['plotOptions'],
    };
  }

  // ── Labelled values: donut, pie, radial bar, polar area ────────────────────────────────────────────────────────────────────
  if (spec.type === 'donut' || spec.type === 'pie' || spec.type === 'radialBar' || spec.type === 'polarArea') {
    const folded = foldLabelled(spec.series as number[], spec.labels ?? [], spec.slots, other);
    const colors = spec.colors?.length ? spec.colors : folded.slots.map(colorOf);
    const legend = { ...base.legend, show: legendShown(folded.values.length) };
    const common: ApexOptions = { ...base, series: folded.values, labels: folded.labels, colors, legend, tooltip: { ...base.tooltip, fillSeriesColor: false } };
    if (spec.type === 'radialBar') {
      return {
        ...common, stroke: { lineCap: 'round' },
        plotOptions: {
          radialBar: {
            hollow: { size: '58%' }, track: { background: palette.grid },
            dataLabels: { name: { fontSize: '12px', color: palette.muted }, value: { fontSize: '24px', fontWeight: 700, color: palette.text, formatter: (v: number) => money(v) } },
          },
        },
      };
    }
    // A 2px ring in the surface colour separates neighbouring slices.
    const ring = { width: 2, colors: [surface] };
    if (spec.type === 'polarArea') {
      return { ...common, stroke: ring, fill: { opacity: 0.85 }, yaxis: { show: false }, plotOptions: { polarArea: { rings: { strokeColor: palette.grid }, spokes: { strokeWidth: 0 } } } };
    }
    if (spec.type === 'pie') {
      return { ...common, stroke: ring };
    }
    const total = folded.values.reduce((sum, v) => sum + (v || 0), 0);
    return {
      ...common, stroke: ring,
      plotOptions: {
        pie: {
          donut: {
            size: '72%',
            labels: {
              show: true,
              name: { show: true, fontSize: '12px', color: palette.muted, offsetY: 18 },
              value: { show: true, fontSize: '26px', fontWeight: 700, color: palette.text, offsetY: -14, formatter: (v: string) => money(Number(v)) },
              total: { show: true, showAlways: true, label: spec.centerLabel ?? '', fontSize: '12px', color: palette.muted,
                formatter: () => spec.centerValue ?? money(total) },
            },
          },
        },
      },
    };
  }

  // ── Funnel: horizontal bars centred on the axis, one ordinal step per stage ───────────────────────────────────────────────
  if (spec.type === 'funnel') {
    const values = spec.series as number[];
    const labels = spec.labels ?? [];
    const notes = spec.stageNotes ?? [];
    return {
      ...base,
      series: [{ name: spec.yTitle ?? '', data: values }],
      colors: hqOrdinalColors(values.length, palette.dark),
      xaxis: { categories: labels, labels: { show: false }, axisBorder: { show: false }, axisTicks: { show: false } },
      yaxis: { labels: { show: true, style: { colors: palette.text, fontSize: '12px' } } },
      grid: { show: false },
      legend: { show: false },
      stroke: { show: true, width: 2, colors: [surface] },
      plotOptions: { bar: { horizontal: true, distributed: true, isFunnel: true, borderRadius: 4, barHeight: '78%' } },
      dataLabels: {
        enabled: true,
        formatter: (value: string | number | number[], opts?: { dataPointIndex?: number }) => {
          const note = notes[opts?.dataPointIndex ?? -1];
          return note ? `${money(Number(value), true)} · ${note}` : money(Number(value), true);
        },
        // Ink on the fill: dark steps take white, light steps near-black, so the label always reads.
        style: { colors: hqOrdinalColors(values.length, palette.dark).map((c) => (isLight(c) ? INSIDE_YELLOW_INK : INSIDE_FILL_INK)), fontSize: '12px', fontWeight: 600 },
        dropShadow: { enabled: false },
      },
      tooltip: { ...base.tooltip, shared: false, intersect: true },
    };
  }

  // ── Heatmap: one sequential hue, stepped into buckets; empty cells in the no-data colour ──────────────────────────────────
  if (spec.type === 'heatmap') {
    const rows = spec.series as HqChartSeries[];
    const cols = spec.labels ?? [];
    const flat = rows.flatMap((r) => r.data).filter((v): v is number => typeof v === 'number');
    const min = spec.min ?? (flat.length ? Math.min(...flat) : 0);
    const max = spec.max ?? (flat.length ? Math.max(...flat) : 0);
    const hasEmpty = rows.some((r) => r.data.some((v) => v === null || v === undefined));
    const step = max > min ? (max - min) / HQ_CHART_HEATMAP_BUCKETS : 1;
    const ramp = palette.dark ? [...HQ_CHART_SEQUENTIAL].reverse() : [...HQ_CHART_SEQUENTIAL];
    const ranges = Array.from({ length: HQ_CHART_HEATMAP_BUCKETS }, (_, i) => {
      const from = min + step * i;
      const to = i === HQ_CHART_HEATMAP_BUCKETS - 1 ? max : min + step * (i + 1);
      // Spread the buckets across the ramp, skipping the faintest step so the lowest bucket still shows on the surface.
      const color = ramp[Math.round(1 + ((ramp.length - 2) * i) / (HQ_CHART_HEATMAP_BUCKETS - 1))];
      return { from, to, color, name: `${money(from, true)}–${money(to, true)}` };
    });
    if (hasEmpty) {
      ranges.unshift({ from: HQ_CHART_HEATMAP_EMPTY, to: HQ_CHART_HEATMAP_EMPTY, color: palette.dark ? '#2a2f3a' : '#eef0f3', name: palette.noDataLabel ?? 'No data' });
    }
    const series = rows.map((r) => ({
      name: r.name,
      data: cols.map((c, i) => ({ x: c, y: typeof r.data[i] === 'number' ? (r.data[i] as number) : HQ_CHART_HEATMAP_EMPTY })),
    }));
    return {
      ...base,
      series,
      colors: [ramp[Math.round(ramp.length / 2)]],
      // 2px surface gaps between cells.
      stroke: { show: true, width: 2, colors: [surface] },
      plotOptions: { heatmap: { radius: 2, enableShades: false, useFillColorAsStroke: false, colorScale: { ranges } } },
      dataLabels: { enabled: false },
      legend: { show: !sparkline && spec.showLegend !== false, position: 'bottom', fontSize: '12px', labels: { colors: palette.muted }, markers: { size: 6 } },
      xaxis: { type: 'category', labels: { style: { colors: palette.muted, fontSize: '11px' }, rotate: 0, hideOverlappingLabels: true },
        axisBorder: { show: false }, axisTicks: { show: false }, tooltip: { enabled: false }, title: { text: spec.xTitle, style: titleStyle } },
      yaxis: { labels: { style: { colors: palette.muted, fontSize: '11px' } }, title: { text: spec.yTitle, style: titleStyle } },
      grid: { show: false },
      tooltip: {
        ...base.tooltip,
        y: {
          formatter: (value: number, opts?: { seriesIndex?: number; dataPointIndex?: number }) => {
            const empty = value === HQ_CHART_HEATMAP_EMPTY;
            if (spec.cellLabel && opts && opts.seriesIndex !== undefined && opts.dataPointIndex !== undefined) {
              return spec.cellLabel(rows[opts.seriesIndex]?.name ?? '', cols[opts.dataPointIndex] ?? '', empty ? null : value);
            }
            return empty ? (palette.noDataLabel ?? 'No data') : money(value);
          },
        },
      },
    };
  }

  // ── Treemap: one categorical slot per tile, ink chosen for the fill ───────────────────────────────────────────────────────
  if (spec.type === 'treemap') {
    const series = foldSeries(spec.series as HqChartSeries[], other);
    const tiles = (series[0]?.data ?? []) as { x: string; y: number }[];
    const distributed = series.length === 1;
    const slots = distributed ? tiles.map((_, i) => slotAt(spec.slots, Math.min(i, HQ_CHART_MAX_SLOTS - 1))) : series.map((s, i) => (s.slot === 0 ? 0 : slotAt(series.map((x) => x.slot), i)));
    return {
      ...base,
      series: apexSeries(series),
      colors: spec.colors?.length ? spec.colors : slots.map(colorOf),
      legend: { ...base.legend, show: legendShown(distributed ? 0 : series.length) },
      stroke: { show: true, width: 2, colors: [surface] },
      plotOptions: { treemap: { distributed, enableShades: false, borderRadius: 4 } },
      dataLabels: {
        enabled: true,
        style: { colors: slots.map((s) => (s === YELLOW_SLOT ? INSIDE_YELLOW_INK : INSIDE_FILL_INK)), fontSize: '12px', fontWeight: 600 },
        dropShadow: { enabled: false },
      },
    };
  }

  // ── Axis charts: area, line, bar (+ mixed), radar, scatter, bubble, rangeBar, boxPlot, candlestick ────────────────────────
  const axisValue = (value: number | string): string => (typeof value === 'number' && Number.isFinite(value) ? money(value, true) : String(value ?? ''));
  const isBar = spec.type === 'bar';
  const horizontal = (isBar || spec.type === 'rangeBar' || spec.type === 'boxPlot' || spec.type === 'violin' || spec.type === 'raincloud') && !!spec.horizontal
    || spec.type === 'rangeBar' || spec.type === 'dumbbell';
  const scatterLike = spec.type === 'scatter' || spec.type === 'bubble';

  let series = spec.series as HqChartSeries[];
  let labels = spec.labels ?? [];
  let colors: string[];
  const distributed = isBar && !!spec.distributed && series.length === 1;
  if (distributed) {
    const folded = foldLabelled(series[0].data as number[], labels, spec.slots, other);
    series = [{ ...series[0], data: folded.values }];
    labels = folded.labels;
    colors = spec.colors?.length ? spec.colors : folded.slots.map(colorOf);
  } else {
    // Scatter and bubble: every pair of series can be neighbours, so only three slots stay distinguishable (validated --pairs all).
    series = foldSeries(series, other, scatterLike ? HQ_CHART_SCATTER_MAX_SERIES : HQ_CHART_MAX_SLOTS);
    colors = series.map((s, i) => colorOf(s.slot === 0 ? 0 : slotAt(series.map((x) => x.slot), i)));
  }

  const wholeNumbers = format === 'number' && !horizontal ? { decimalsInFloat: 0, forceNiceScale: true } : {};
  const xaxis: ApexOptions['xaxis'] = sparkline ? {} : {
    type: dateTime ? 'datetime' : spec.xType === 'numeric' || scatterLike ? 'numeric' : 'category',
    ...(labels.length && !dateTime && !scatterLike && spec.type !== 'rangeBar' ? { categories: labels } : {}),
    axisBorder: { color: palette.grid }, axisTicks: { color: palette.grid },
    // On a horizontal bar chart the figures run along the bottom, which ApexCharts calls the x axis.
    labels: {
      style: { colors: palette.muted, fontSize: '11px' }, rotate: 0, hideOverlappingLabels: true, datetimeUTC: false,
      ...((horizontal && !dateTime) || scatterLike ? { formatter: axisValue as (value: string) => string } : {}),
    },
    title: { text: spec.xTitle, style: titleStyle },
    crosshairs: { show: !isBar && !distributed, stroke: { color: palette.muted, width: 1, dashArray: 3 } },
    tooltip: { enabled: false },
  };
  // One value axis only, by design.
  const yaxis: ApexOptions['yaxis'] = sparkline ? undefined : {
    ...wholeNumbers,
    labels: { style: { colors: palette.muted, fontSize: '11px' }, ...(horizontal ? {} : { formatter: axisValue }) },
    title: { text: spec.yTitle, style: titleStyle },
  };

  const goalLine = spec.goal && !sparkline ? {
    borderColor: palette.muted, strokeDashArray: 4, borderWidth: 1,
    label: spec.goal.label ? {
      text: spec.goal.label, borderWidth: 0, position: horizontal ? 'top' : 'left', textAnchor: 'start',
      style: { color: palette.text, background: surface, fontSize: '11px', fontWeight: 600 },
    } : undefined,
  } : null;
  const annotations: ApexOptions['annotations'] = goalLine
    ? horizontal ? { xaxis: [{ x: spec.goal!.value, ...goalLine }] } : { yaxis: [{ y: spec.goal!.value, ...goalLine }] }
    : {};

  const common: ApexOptions = {
    ...base, series: apexSeries(series), xaxis, yaxis, colors, annotations,
    legend: { ...base.legend, show: !distributed && legendShown(series.length) },
  };
  const markers = { size: 0, strokeWidth: 2, strokeColors: surface, hover: { size: sparkline ? 0 : 8 } };
  const pointTooltip = { ...base.tooltip, shared: false, intersect: true };

  switch (spec.type) {
    case 'bar': {
      const mixed = series.some((s) => s.type && s.type !== 'bar' && s.type !== 'column');
      return {
        ...common,
        ...(mixed ? { chart: { ...common.chart, type: 'line' } } : {}),
        plotOptions: {
          bar: {
            horizontal, distributed, borderRadius: 4, borderRadiusApplication: 'end', borderRadiusWhenStacked: 'last',
            columnWidth: '42%', barHeight: '52%',
            // 'top' is the data end of a bar (its right end when horizontal), so a value label sits just past the bar.
            dataLabels: { position: 'top' },
          },
        },
        // 2px surface-coloured edge: the gap between neighbouring bars and stacked segments; lines in a mixed chart stay 2px wide.
        stroke: mixed ? { width: series.map((s) => (s.type === 'line' || s.type === 'area' ? 2 : 0)), curve: 'smooth' } : { show: true, width: 2, colors: [surface] },
        fill: { opacity: mixed ? series.map((s) => (s.type === 'area' ? 0.25 : 1)) : 1 },
        markers,
        tooltip: mixed ? { ...base.tooltip, shared: true, intersect: false } : pointTooltip,
        dataLabels: {
          ...base.dataLabels, offsetY: horizontal ? 0 : -18, offsetX: horizontal ? 24 : 0,
          // Ink, never the bar colour; an optional note per label (a share) follows the value.
          formatter: (value: string | number | number[], opts?: { dataPointIndex?: number }) => {
            const note = spec.stageNotes?.[opts?.dataPointIndex ?? -1];
            return note ? `${money(Number(value), true)} · ${note}` : money(Number(value), true);
          },
        },
      };
    }
    case 'area':
      return {
        ...common, markers, tooltip: { ...base.tooltip, shared: !sparkline, intersect: false, enabled: !sparkline },
        fill: { type: 'gradient', gradient: { shadeIntensity: 1, opacityFrom: 0.32, opacityTo: 0.02, stops: [0, 90, 100] } },
      };
    case 'line':
      return { ...common, markers, tooltip: { ...base.tooltip, shared: !sparkline, intersect: false, enabled: !sparkline }, fill: { opacity: 1 } };
    case 'radar':
      return {
        ...common,
        xaxis: { categories: labels, labels: { style: { colors: Array(labels.length).fill(palette.muted), fontSize: '11px' } } },
        yaxis: { show: false },
        stroke: { width: 2 }, fill: { opacity: 0.15 },
        markers: { size: 3, strokeWidth: 0, hover: { size: 8 } },
        plotOptions: { radar: { polygons: { strokeColors: palette.grid, connectorColors: palette.grid, fill: { colors: ['transparent'] } } } },
        tooltip: pointTooltip,
      };
    case 'scatter':
      return { ...common, markers: { size: 8, strokeWidth: 2, strokeColors: surface, hover: { sizeOffset: 2 } }, tooltip: pointTooltip, grid: { ...base.grid, xaxis: { lines: { show: true } } } };
    case 'bubble':
      return { ...common, fill: { opacity: 0.8 }, stroke: { width: 2, colors: [surface] }, tooltip: pointTooltip, dataLabels: { enabled: false } };
    case 'rangeBar':
      return {
        ...common,
        plotOptions: { bar: { horizontal: true, borderRadius: 4, barHeight: '52%', rangeBarGroupRows: true } },
        stroke: { show: true, width: 2, colors: [surface] },
        tooltip: pointTooltip,
      };
    case 'boxPlot': {
      // The two halves of a box are one entity: the series' own slot, stepped lighter for the lower half.
      const own = colors[0] ?? colorOf(1);
      return {
        ...common,
        plotOptions: { bar: { horizontal, columnWidth: '42%', barHeight: '52%' }, boxPlot: { colors: { upper: own, lower: tint(own, 0.45) } } },
        stroke: { width: 1, colors: [palette.text] },
        tooltip: pointTooltip,
      };
    }
    case 'candlestick':
      // Rise and fall are a polarity: the diverging blue <-> red pair.
      return {
        ...common,
        plotOptions: { candlestick: { colors: { upward: colorOf(1), downward: colorOf(8) }, wick: { useFillColor: true } } },
        tooltip: pointTooltip,
      };
    case 'rangeArea':
      // A band between a low and a high value; a joined `type: 'line'` series (the median, the target) stays a 2px line.
      return {
        ...common, markers,
        stroke: { curve: 'smooth', width: series.map((s) => (s.type === 'line' ? 2 : 0)) },
        fill: { opacity: series.map((s) => (s.type === 'line' ? 1 : 0.28)) },
        tooltip: { ...base.tooltip, shared: true, intersect: false },
      };
    case 'dumbbell': {
      // Two measures per row: the start in slot 1, the end in slot 2, joined by a thin connector; the legend names the measures.
      const ends = [colorOf(1), colorOf(2)];
      return {
        ...common,
        colors: ends,
        plotOptions: { bar: { horizontal: true, isDumbbell: true, dumbbellColors: [ends], barHeight: '3%' } } as ApexOptions['plotOptions'],
        markers: { size: 8, strokeWidth: 2, strokeColors: surface },
        legend: {
          ...base.legend, show: !sparkline && labels.length >= 2,
          customLegendItems: labels.slice(0, 2), markers: { fillColors: ends } as never,
        },
        tooltip: pointTooltip,
      };
    }
    case 'histogram':
      // ApexCharts bins the raw observations; neighbouring bins touch (a distribution), split by a 1px surface line.
      return {
        ...common,
        xaxis: { ...xaxis, type: 'numeric', categories: undefined, labels: { ...(xaxis as { labels?: object }).labels, formatter: axisValue as (value: string) => string } },
        plotOptions: {
          histogram: { bins: spec.bins ?? 'auto', ...(spec.binWidth ? { binWidth: spec.binWidth } : {}), overlap: true },
          bar: { borderRadius: 2, borderRadiusApplication: 'end' },
        } as ApexOptions['plotOptions'],
        stroke: { show: true, width: 1, colors: [surface] },
        tooltip: pointTooltip,
      };
    case 'waterfall':
      // Rise and fall are a polarity (blue <-> red, as on candlesticks); totals are neutral; the value of every step is labelled.
      return {
        ...common,
        legend: { show: false },
        plotOptions: {
          waterfall: {
            colors: { positive: colorOf(1), negative: colorOf(8), subtotal: otherColor, total: otherColor },
            connectors: { show: true, color: palette.grid, strokeWidth: 1, strokeDashArray: 3 },
          },
          bar: { borderRadius: 4, columnWidth: '52%', dataLabels: { position: 'top' } },
        } as ApexOptions['plotOptions'],
        stroke: { show: true, width: 2, colors: [surface] },
        dataLabels: { ...base.dataLabels, enabled: spec.dataLabels ?? true, offsetY: -18 },
        tooltip: pointTooltip,
      };
    case 'streamgraph':
      // The baseline wiggles, so the value axis carries no meaning: hidden; each band is named on itself and in the legend.
      return {
        ...common,
        yaxis: { show: false },
        stroke: { width: 1, colors: [surface] },
        fill: { opacity: 1 },
        plotOptions: { streamgraph: { offset: 'wiggle', order: 'inside-out', labels: { show: true } } } as ApexOptions['plotOptions'],
        tooltip: { ...base.tooltip, shared: true, intersect: false },
      };
    case 'violin':
    case 'raincloud':
      // ApexCharts estimates the density from the raw observations; a raincloud adds the box and the "rain" of observations.
      return {
        ...common,
        legend: { ...base.legend, show: legendShown(series.length) },
        plotOptions: {
          violin: {
            normalize: 'individual',
            box: { show: true, strokeWidth: 1 },
            points: { show: spec.type === 'raincloud', size: 3, opacity: 0.7, strokeColor: surface },
          },
          bar: { horizontal },
        } as ApexOptions['plotOptions'],
        stroke: { width: 1, colors: [palette.text] },
        fill: { opacity: 0.75 },
        tooltip: pointTooltip,
      };
    case 'beeswarm':
      // A strip plot: one band per group, the observations spread apart so none hides another.
      return {
        ...common,
        xaxis: { ...xaxis, type: 'category' },
        // One series: each group's band takes its own slot in order (the groups are the entities); several series keep theirs.
        colors: series.length === 1 ? palette.colors : colors,
        markers: { size: 5, strokeWidth: 1, strokeColors: surface, hover: { sizeOffset: 3 } },
        plotOptions: { scatter: { jitter: { enabled: true, x: 0.32, distributed: series.length === 1 } } } as ApexOptions['plotOptions'],
        tooltip: pointTooltip,
      };
    default:
      return common;
  }
}

/** True for a light fill (relative luminance above the mid-point), which takes near-black ink. */
function isLight(color: string): boolean {
  const rgb = parseColor(color);
  if (!rgb) {
    return false;
  }
  const [r, g, b] = rgb.map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.35;
}
