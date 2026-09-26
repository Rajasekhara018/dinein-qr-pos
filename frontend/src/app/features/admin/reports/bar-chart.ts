import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export interface BarDatum {
  key: string;
  /** Short axis label. */
  label: string;
  value: number;
  /** Tooltip / accessible text, e.g. "27 Sep: ₹4,210 · 18 orders". */
  description: string;
}

/**
 * Minimal accessible bar chart (plain CSS, no chart library): one series, rounded bar tops anchored to the
 * baseline, hover/focus tooltip per bar, sparse axis labels, and a "Show table" alternative.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './bar-chart.html',
})
export class BarChart {
  readonly data = input.required<readonly BarDatum[]>();
  readonly label = input.required<string>();
  readonly valueHeader = input('Value');
  readonly formatValue = input<(value: number) => string>((v) => String(v));

  protected readonly showTable = signal(false);
  protected readonly active = signal<string | null>(null);

  protected readonly max = computed(() => Math.max(0, ...this.data().map((d) => d.value)));
  /** Show about 7 axis labels at most. */
  protected readonly labelEvery = computed(() => Math.max(1, Math.ceil(this.data().length / 7)));
  protected readonly summary = computed(() => {
    const data = this.data();
    if (!data.length) return `${this.label()}: no data`;
    const top = data.reduce((a, b) => (b.value > a.value ? b : a));
    return `${this.label()}, ${data.length} bars. Highest: ${top.description}.`;
  });

  protected height(value: number): number {
    const max = this.max();
    if (!max || value <= 0) return 0;
    return Math.max(2, (value / max) * 100);
  }
}
