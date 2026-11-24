import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { HqChart } from './hq-chart';
import { HqChartFormat, HqChartSpec } from './hq-chart.options';

/** Height of one row and the chart's own padding (axis labels, legend), in px. */
const ROW_PX = 30;
const PADDING_PX = 96;

/**
 * A heatmap (drawn by ApexCharts, in the shared chart theme): rows by columns, each cell coloured from the validated sequential blue
 * ramp by its value (stepped buckets with a legend scale), empty cells (null) in a separate no-data colour, a tooltip per cell.
 * Pair it with hq-chart-card's table view so the values are also readable as text.
 */
@Component({
  selector: 'hq-heatmap',
  standalone: true,
  imports: [HqChart],
  template: `<hq-chart [spec]="spec" [ariaLabel]="ariaLabel" [emptyText]="emptyText"></hq-chart>`,
  styles: [':host { display: block; min-width: 0; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqHeatmap implements OnChanges {
  @Input() rows: readonly string[] = [];
  @Input() cols: readonly string[] = [];
  /** values[row][col]; null is an empty cell. */
  @Input() values: readonly (readonly (number | null)[])[] = [];
  @Input() format: HqChartFormat = 'number';
  @Input() currency = 'INR';
  /** Ends of the colour scale (default: the data's own minimum and maximum). */
  @Input() min: number | null = null;
  @Input() max: number | null = null;
  @Input() legend = true;
  @Input() ariaLabel = '';
  @Input() emptyText = '';
  /** Tooltip text of one cell (default: the formatted value). */
  @Input() cellLabel: ((row: string, col: string, value: number | null) => string) | null = null;

  spec: HqChartSpec = { type: 'heatmap', series: [] };

  ngOnChanges(): void {
    const rows = this.rows ?? [];
    this.spec = {
      type: 'heatmap',
      // ApexCharts draws the first series at the bottom; reversed, the first row reads at the top like a table.
      series: rows.map((name, r) => ({ name, data: [...(this.values?.[r] ?? [])] })).reverse(),
      labels: [...(this.cols ?? [])],
      format: this.format,
      currency: this.currency,
      min: this.min ?? undefined,
      max: this.max ?? undefined,
      showLegend: this.legend,
      cellLabel: this.cellLabel ?? undefined,
      height: rows.length * ROW_PX + PADDING_PX,
    };
  }
}
