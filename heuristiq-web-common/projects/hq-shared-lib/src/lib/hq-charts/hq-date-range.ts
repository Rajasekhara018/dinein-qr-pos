import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HqTimezoneService } from '../hq-timezone/hq-timezone.service';
import {
  HQ_DATE_RANGE_DEFAULT_PRESET, HQ_DATE_RANGE_DEFAULT_PRESETS, HqDateRange, HqDateRangePreset, hqDateRangeFor, hqRangeDay,
  hqSameDateRange, hqTodayInZone,
} from './hq-date-range.helpers';
import { HQ_CHART_TEXTS, HqChartTextKey, hqChartText } from './hq-chart.texts';

/**
 * The period of a dashboard: a segmented row of preset buttons (Last 7 days, Last month, Current month, Current year by default)
 * and, when `allowCustom` is on, a "Custom" option with From / To date fields (To is never before From). Presets are counted from
 * today in the user's timezone. With no `value` it starts on the current month and emits that once, so a screen can load straight
 * away.
 *
 * Dashboards place ONE range picker in the filter row above the charts and reload every chart on `(valueChange)`:
 *   <hq-filter-bar><hq-date-range [value]="range" (valueChange)="range = $event; reload()"></hq-date-range></hq-filter-bar>
 * A chart that needs its own period gets a compact one through hq-chart-card's `rangePresets`.
 */
@Component({
  selector: 'hq-date-range',
  standalone: true,
  imports: [FormsModule, HqSharedLibModule],
  templateUrl: './hq-date-range.html',
  styleUrl: './hq-date-range.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqDateRangePicker implements OnInit, OnChanges {
  private readonly texts = inject(HQ_CHART_TEXTS, { optional: true });
  private readonly timezone = inject(HqTimezoneService, { optional: true });
  private readonly cdr = inject(ChangeDetectorRef);

  @Input() value: HqDateRange | null = null;
  @Input() presets: readonly HqDateRangePreset[] = HQ_DATE_RANGE_DEFAULT_PRESETS;
  @Input() allowCustom = true;
  @Input() size: 'sm' | 'md' = 'md';
  @Input() ariaLabel = '';
  @Output() valueChange = new EventEmitter<HqDateRange>();

  /** The preset shown as chosen. */
  active: HqDateRangePreset = HQ_DATE_RANGE_DEFAULT_PRESET;
  customFrom = '';
  customTo = '';

  get options(): HqDateRangePreset[] {
    const list = this.presets.filter((p) => p !== 'custom');
    return this.allowCustom ? [...list, 'custom'] : list;
  }

  get label(): string {
    return this.ariaLabel || this.text('dateRange');
  }

  get customError(): string | null {
    const from = hqRangeDay(this.customFrom);
    const to = hqRangeDay(this.customTo);
    return from && to && to < from ? `${this.text('to')} ≥ ${this.text('from')}` : null;
  }

  ngOnInit(): void {
    if (!this.value) {
      const preset = this.options.includes(HQ_DATE_RANGE_DEFAULT_PRESET) ? HQ_DATE_RANGE_DEFAULT_PRESET : this.options[0] ?? HQ_DATE_RANGE_DEFAULT_PRESET;
      this.emit(hqDateRangeFor(preset, this.today()));
    }
  }

  ngOnChanges(): void {
    if (this.value) {
      this.active = this.value.preset;
      if (this.value.preset === 'custom') {
        this.customFrom = this.value.from;
        this.customTo = this.value.to;
      }
    }
  }

  text(key: HqChartTextKey): string {
    return hqChartText(this.texts, key);
  }

  presetLabel(preset: HqDateRangePreset): string {
    return this.text(preset);
  }

  choose(preset: unknown): void {
    const chosen = preset as HqDateRangePreset;
    this.active = chosen;
    if (chosen === 'custom') {
      // Custom starts from the period currently shown, so the fields are never empty.
      const start = this.value ?? hqDateRangeFor(HQ_DATE_RANGE_DEFAULT_PRESET, this.today());
      this.customFrom = this.customFrom || start.from;
      this.customTo = this.customTo || start.to;
      this.emitCustom();
      return;
    }
    this.emit(hqDateRangeFor(chosen, this.today()));
  }

  setFrom(value: unknown): void {
    this.customFrom = typeof value === 'string' ? value : '';
    this.emitCustom();
  }

  setTo(value: unknown): void {
    this.customTo = typeof value === 'string' ? value : '';
    this.emitCustom();
  }

  private emitCustom(): void {
    const from = hqRangeDay(this.customFrom);
    const to = hqRangeDay(this.customTo);
    if (!from || !to || to < from) {
      this.cdr.markForCheck();
      return;
    }
    this.emit({ preset: 'custom', from: this.customFrom, to: this.customTo });
  }

  private emit(range: HqDateRange): void {
    this.active = range.preset;
    if (hqSameDateRange(range, this.value)) {
      return;
    }
    this.value = range;
    this.valueChange.emit(range);
    this.cdr.markForCheck();
  }

  private today(): Date {
    return hqTodayInZone(this.timezone?.effectiveTimezone() ?? null);
  }
}
