import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HqCommonTableColumn } from '../hq-ui/hq-common-table/hq-common-table';
import { HqDateRangePicker } from './hq-date-range';
import { HqDateRange, HqDateRangePreset } from './hq-date-range.helpers';
import { HQ_CHART_TEXTS, hqChartText } from './hq-chart.texts';

/** What a chart card shows: the chart, or the same data as a table. */
export type HqChartCardView = 'chart' | 'table';

/**
 * The frame of one dashboard chart: title, subtitle, header actions (`[actions]` slot), and the chart as content. It shows a
 * skeleton while loading, an error with retry, or an empty state instead of the chart. With `tableColumns` / `tableRows` a
 * Chart | Table toggle offers the same data as a table (the accessibility and print view, and the relief channel for chart colours
 * below 3:1 contrast). With `rangePresets` the card carries its own compact date-range picker in the header.
 */
@Component({
  selector: 'hq-chart-card',
  standalone: true,
  imports: [HqSharedLibModule, HqDateRangePicker],
  template: `
    <section class="card" [attr.aria-busy]="loading ? 'true' : null">
      <header class="card__head">
        <div class="card__titles">
          <h3 class="card__title">{{ title }}</h3>
          @if (subtitle) {
            <p class="card__subtitle">{{ subtitle }}</p>
          }
        </div>
        <div class="card__actions">
          @if (rangePresets?.length) {
            <hq-date-range size="sm" [presets]="rangePresets!" [value]="range" (valueChange)="rangeChange.emit($event)"></hq-date-range>
          }
          @if (hasTable) {
            <hq-tabs appearance="segmented" size="sm" [ariaLabel]="title" [value]="view" (valueChange)="view = $any($event)">
              <hq-tab value="chart" [label]="chartWord" icon="bi-bar-chart"></hq-tab>
              <hq-tab value="table" [label]="tableWord" icon="bi-table"></hq-tab>
            </hq-tabs>
          }
          <ng-content select="[actions]"></ng-content>
        </div>
      </header>
      <div class="card__body">
        @if (loading) {
          <hq-skeleton variant="rect" [height]="skeletonHeight"></hq-skeleton>
        } @else if (error) {
          @if (retry.observed) {
            <hq-error-state [message]="errorMessage" (retry)="retry.emit()"></hq-error-state>
          } @else {
            <hq-error-state [message]="errorMessage"></hq-error-state>
          }
        } @else if (empty) {
          <hq-empty-state icon="bi-bar-chart" [title]="emptyMessage"></hq-empty-state>
        } @else if (view === 'table' && hasTable) {
          <hq-common-table [columns]="tableColumns!" [rows]="tableRows!" [showActionsColumn]="false"></hq-common-table>
        } @else {
          <ng-content></ng-content>
        }
      </div>
    </section>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .card { height: 100%; padding: 1rem 1.15rem 1.1rem; background: var(--workspace-panel, #fff); border: 1px solid var(--workspace-border, rgba(0,0,0,.08));
      border-radius: 16px; box-shadow: var(--theme-soft-shadow, 0 6px 24px rgba(0,0,0,.05)); display: flex; flex-direction: column; }
    .card__head { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: .5rem .75rem; margin-block-end: .6rem; }
    .card__titles { min-width: 0; }
    .card__title { margin: 0; font-size: 1rem; font-weight: 700; color: var(--edu-ink, #1b2a4a); }
    .card__subtitle { margin: .15rem 0 0; font-size: .78rem; color: var(--edu-muted, #5a6a7e); }
    .card__actions { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; font-size: .8rem; font-weight: 600; }
    .card__actions ::ng-deep a { color: var(--hq-link, var(--hq-primary, #0B6E4F)); text-decoration: none; }
    .card__actions ::ng-deep a:hover { text-decoration: underline; }
    .card__body { flex: 1 1 auto; min-width: 0; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqChartCard<T extends object = Record<string, unknown>> {
  private readonly texts = inject(HQ_CHART_TEXTS, { optional: true });

  @Input({ required: true }) title = '';
  @Input() subtitle = '';
  @Input() loading = false;
  /** Shows the error state instead of the chart: true, or the message to show. */
  @Input() error: boolean | string | null = null;
  @Output() retry = new EventEmitter<void>();
  @Input() empty = false;
  @Input() emptyText = '';
  /** The same data as a table: with both set, a Chart | Table toggle appears. */
  @Input() tableColumns: HqCommonTableColumn<T>[] | null = null;
  @Input() tableRows: T[] | null = null;
  /** Presets of the card's own date-range picker; none hides it (the dashboard's filter row sets the period). */
  @Input() rangePresets: readonly HqDateRangePreset[] | null = null;
  @Input() range: HqDateRange | null = null;
  @Output() rangeChange = new EventEmitter<HqDateRange>();
  /** Height of the loading skeleton. */
  @Input() skeletonHeight = '240px';

  view: HqChartCardView = 'chart';

  get hasTable(): boolean {
    return !!this.tableColumns?.length && !!this.tableRows;
  }

  get errorMessage(): string {
    return typeof this.error === 'string' ? this.error : '';
  }

  get emptyMessage(): string {
    return this.emptyText || hqChartText(this.texts, 'noData');
  }

  get chartWord(): string {
    return hqChartText(this.texts, 'chart');
  }

  get tableWord(): string {
    return hqChartText(this.texts, 'table');
  }
}
