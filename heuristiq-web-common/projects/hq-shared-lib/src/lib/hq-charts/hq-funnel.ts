import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { HqChart } from './hq-chart';
import { HqChartFormat, HqChartSpec, formatChartValue } from './hq-chart.options';
import { HQ_CHART_TEXTS, hqChartText } from './hq-chart.texts';

/** One stage of a funnel, first stage first. */
export interface HqFunnelStage {
  label: string;
  value: number;
}

/** Height of one stage and the chart's own padding, in px. */
const STAGE_PX = 46;
const PADDING_PX = 24;

/**
 * A funnel (drawn by ApexCharts as a centred bar funnel, in the shared chart theme): one ordinal blue step per stage, each stage's
 * value with its conversion from the previous stage and from the first.
 */
@Component({
  selector: 'hq-funnel',
  standalone: true,
  imports: [HqChart],
  template: `<hq-chart [spec]="spec" [ariaLabel]="ariaLabel" [emptyText]="emptyText"></hq-chart>`,
  styles: [':host { display: block; min-width: 0; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqFunnel implements OnChanges {
  private readonly texts = inject(HQ_CHART_TEXTS, { optional: true });

  @Input() stages: readonly HqFunnelStage[] = [];
  @Input() format: HqChartFormat = 'number';
  @Input() currency = 'INR';
  /** Shows the conversion from the previous stage and from the first after each value. */
  @Input() showConversion = true;
  @Input() ariaLabel = '';
  @Input() emptyText = '';

  spec: HqChartSpec = { type: 'funnel', series: [] };

  ngOnChanges(): void {
    const stages = (this.stages ?? []).filter((s) => Number.isFinite(s.value));
    const first = stages[0]?.value ?? 0;
    const notes = this.showConversion
      ? stages.map((stage, i) => {
        if (i === 0) {
          return '';
        }
        const previous = stages[i - 1].value;
        const fromPrevious = previous > 0 ? formatChartValue((stage.value / previous) * 100, 'percent') : '—';
        const ofFirst = first > 0 ? formatChartValue((stage.value / first) * 100, 'percent') : '—';
        return `${fromPrevious} ${hqChartText(this.texts, 'conversion')} · ${ofFirst} ${hqChartText(this.texts, 'ofFirst')}`;
      })
      : undefined;
    this.spec = {
      type: 'funnel',
      series: stages.map((s) => s.value),
      labels: stages.map((s) => s.label),
      format: this.format,
      currency: this.currency,
      stageNotes: notes,
      height: stages.length * STAGE_PX + PADDING_PX,
    };
  }
}
