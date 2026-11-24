import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, NgZone, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { HqStatusTone } from '../hq-ui/hq-status-badge/hq-status-badge.constants';
import { HqChart } from './hq-chart';
import { HqChartFormat, HqChartSpec, formatChartValue } from './hq-chart.options';
import { HQ_CHART_MAX_SLOTS, hqCategoricalColor, hqChartIsDark } from './hq-chart.palette';
import { HQ_CHART_TEXTS, hqChartText } from './hq-chart.texts';
import { hqWatchChartTheme } from './hq-chart.theme';

/** One row of a ranked bar list. */
export interface HqBarListItem {
  label: string;
  value: number;
  /** Categorical slot 1..8 of this entity (colour mode `categorical`). */
  slot?: number;
  /** Status of this row (colour mode `status`); its label always says it too. */
  tone?: HqStatusTone;
  /** Extra words for the tooltip. */
  hint?: string;
  /** Kept for screens that link rows; the chart itself does not navigate. */
  link?: string | readonly unknown[];
}

/** How a bar list colours its bars: one hue, one categorical slot per entity, or the status tones. */
export type HqBarListColorMode = 'single' | 'categorical' | 'status';

/** Height of one row and the chart's own padding, in px. */
const ROW_PX = 34;
const PADDING_PX = 36;

/**
 * A ranking: horizontal bars (drawn by ApexCharts, in the shared chart theme) with the label on the left and the value at the bar's
 * end in ink. Sorted, limited (the rest fold into "Other"), coloured by one hue, by entity, or by status.
 */
@Component({
  selector: 'hq-bar-list',
  standalone: true,
  imports: [HqChart],
  template: `<hq-chart [spec]="spec" [ariaLabel]="ariaLabel" [emptyText]="emptyText"></hq-chart>`,
  styles: [':host { display: block; min-width: 0; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqBarList implements OnInit, OnChanges, OnDestroy {
  private readonly texts = inject(HQ_CHART_TEXTS, { optional: true });
  private readonly zone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private stopWatching: () => void = () => undefined;

  @Input() items: readonly HqBarListItem[] = [];
  @Input() format: HqChartFormat = 'number';
  @Input() currency = 'INR';
  /** The value a full bar stands for when showing shares (defaults to the total of the rows). */
  @Input() max: number | null = null;
  @Input() colorMode: HqBarListColorMode = 'single';
  /** Adds each row's share ("· 42%") after its value. */
  @Input() showPercent = false;
  @Input() sort: 'none' | 'desc' = 'desc';
  /** Rows shown; the rest fold into one "Other" row. 0 shows them all (up to the colour limit in categorical mode). */
  @Input() limit = 0;
  @Input() ariaLabel = '';
  @Input() emptyText = '';

  spec: HqChartSpec = { type: 'bar', series: [] };

  ngOnInit(): void {
    this.stopWatching = hqWatchChartTheme(this.zone, () => {
      this.build();
      this.cdr.markForCheck();
    });
    this.build();
  }

  ngOnChanges(): void {
    this.build();
  }

  ngOnDestroy(): void {
    this.stopWatching();
  }

  private rows(): HqBarListItem[] {
    const rows = [...(this.items ?? [])].filter((r) => Number.isFinite(r.value));
    if (this.sort === 'desc') {
      rows.sort((a, b) => b.value - a.value);
    }
    const cap = this.colorMode === 'categorical' ? HQ_CHART_MAX_SLOTS : Number.MAX_SAFE_INTEGER;
    const limit = Math.min(this.limit > 0 ? this.limit : rows.length, cap);
    if (rows.length <= limit) {
      return rows;
    }
    const keep = rows.slice(0, limit - 1);
    const rest = rows.slice(limit - 1).reduce((sum, r) => sum + r.value, 0);
    return [...keep, { label: hqChartText(this.texts, 'other'), value: rest, slot: 0, tone: 'neutral' }];
  }

  private build(): void {
    const rows = this.rows();
    const dark = hqChartIsDark();
    const total = this.max ?? rows.reduce((sum, r) => sum + Math.max(0, r.value), 0);
    const colors = this.colorMode === 'status'
      ? rows.map((r) => readTone(r.tone ?? 'neutral', dark))
      : this.colorMode === 'categorical'
        ? rows.map((r, i) => hqCategoricalColor(r.slot === 0 ? 0 : r.slot ?? i + 1, dark))
        : rows.map(() => hqCategoricalColor(1, dark));
    this.spec = {
      type: 'bar',
      horizontal: true,
      distributed: true,
      series: [{ name: this.ariaLabel, data: rows.map((r) => r.value) }],
      labels: rows.map((r) => r.label),
      colors,
      format: this.format,
      currency: this.currency,
      dataLabels: true,
      showLegend: false,
      toolbar: false,
      height: rows.length * ROW_PX + PADDING_PX,
      stageNotes: this.showPercent && total > 0
        ? rows.map((r) => formatChartValue((r.value / total) * 100, 'percent'))
        : undefined,
    };
  }
}

/** The fill colour of a status tone from the theme's tone tokens (falls back to fixed status steps). */
function readTone(tone: HqStatusTone, dark: boolean): string {
  const fallback: Record<HqStatusTone, string> = { success: '#0ca30c', warning: '#fab219', danger: '#d03b3b', info: dark ? '#3987e5' : '#2a78d6', neutral: '#9aa1ab' };
  if (typeof document === 'undefined') {
    return fallback[tone];
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(`--hq-tone-${tone}-fg`).trim();
  return value || fallback[tone];
}
