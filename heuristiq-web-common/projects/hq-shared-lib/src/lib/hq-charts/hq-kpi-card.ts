import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { HqChart } from './hq-chart';
import { HqChartSpec } from './hq-chart.options';

/** Whether a rise in the figure is good news (sales) or bad news (failures); decides the colour of the trend badge. */
export type HqKpiTrendMeaning = 'up-is-good' | 'up-is-bad';

/**
 * A headline figure: a label, the figure itself, an optional change against before (with an arrow and a colour for good or bad)
 * and an optional small trend line behind it. Draws no link itself; wrap it in an anchor to make it one. Every word is an input, as
 * the library has no translation dependency.
 */
@Component({
  selector: 'hq-kpi-card',
  standalone: true,
  imports: [HqChart],
  templateUrl: './hq-kpi-card.html',
  styleUrl: './hq-kpi-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqKpiCard {
  @Input({ required: true }) label = '';
  /** The figure, already formatted ("1,248", "₹2.7L", "98%"). */
  @Input({ required: true }) value = '';
  /** A line of small print under the figure ("vs last week"). */
  @Input() caption = '';
  /** bootstrap-icons class of the round badge ("bi-people"); none draws no badge. */
  @Input() icon = '';
  /** The change since before, in percent; null or undefined shows no badge. */
  @Input() trend: number | null | undefined = null;
  @Input() trendMeaning: HqKpiTrendMeaning = 'up-is-good';
  /** Points of the small trend line behind the figure; fewer than two draw no line. */
  @Input() series: number[] | null = null;
  /** Draws the figure in a warning colour (a queue) or a danger colour (a failure). */
  @Input() tone: 'plain' | 'pending' | 'bad' = 'plain';

  get sparkline(): HqChartSpec | null {
    return this.series && this.series.length >= 2 ? { type: 'area', series: [{ name: this.label, data: this.series }], sparkline: true } : null;
  }

  get trendDirection(): 'up' | 'down' | 'flat' | null {
    if (this.trend === null || this.trend === undefined || Number.isNaN(this.trend)) {
      return null;
    }
    return this.trend > 0 ? 'up' : this.trend < 0 ? 'down' : 'flat';
  }

  /** The badge is good (green), bad (red) or neutral, from the direction and what a rise means for this figure. */
  get trendTone(): 'good' | 'bad' | 'neutral' {
    const direction = this.trendDirection;
    if (!direction || direction === 'flat') {
      return 'neutral';
    }
    const rise = direction === 'up';
    return rise === (this.trendMeaning === 'up-is-good') ? 'good' : 'bad';
  }

  get trendText(): string {
    return this.trend === null || this.trend === undefined ? '' : `${Math.abs(Math.round(this.trend * 10) / 10)}%`;
  }
}
