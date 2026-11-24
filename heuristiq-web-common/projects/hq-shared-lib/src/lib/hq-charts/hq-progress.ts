import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { HqStatusTone } from '../hq-ui/hq-status-badge/hq-status-badge.constants';
import { HqChartFormat, formatChartValue } from './hq-chart.options';

/** Colour of a progress meter: the theme's primary, or a status tone. */
export type HqProgressTone = 'primary' | HqStatusTone;

/**
 * A single meter (usage, completion, quota): a thin track with a filled part and its value, as a real progressbar for screen
 * readers. Not a chart, so plain HTML; the fill follows the theme (primary or the --hq-tone-* status tokens).
 */
@Component({
  selector: 'hq-progress',
  standalone: true,
  template: `
    <div class="hq-progress" [class.hq-progress--sm]="size === 'sm'">
      @if (label || showValue) {
        <div class="hq-progress__head">
          @if (label) { <span class="hq-progress__label">{{ label }}</span> }
          @if (showValue) { <span class="hq-progress__value">{{ valueText }}</span> }
        </div>
      }
      <div class="hq-progress__track" role="progressbar" [attr.aria-valuenow]="value" [attr.aria-valuemin]="0" [attr.aria-valuemax]="max"
           [attr.aria-valuetext]="valueText" [attr.aria-label]="label || null">
        <span class="hq-progress__fill" [attr.data-tone]="tone" [style.inline-size.%]="percent"></span>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .hq-progress__head { display: flex; justify-content: space-between; gap: .5rem; margin-block-end: .3rem; font-size: .8rem; }
    .hq-progress__label { color: var(--edu-muted, #5a6a7e); }
    .hq-progress__value { color: var(--edu-ink, #1b2a4a); font-weight: 600; font-variant-numeric: tabular-nums; }
    .hq-progress__track { position: relative; block-size: 8px; border-radius: 999px; overflow: hidden;
      background: var(--theme-soft-surface, rgba(0, 0, 0, .06)); }
    .hq-progress--sm .hq-progress__track { block-size: 5px; }
    .hq-progress__fill { display: block; block-size: 100%; border-radius: 999px; background: var(--hq-primary, #0B6E4F);
      transition: inline-size .3s ease; }
    .hq-progress__fill[data-tone='success'] { background: var(--hq-tone-success-fg, #166534); }
    .hq-progress__fill[data-tone='warning'] { background: var(--hq-tone-warning-fg, #92400e); }
    .hq-progress__fill[data-tone='danger'] { background: var(--hq-tone-danger-fg, #991b1b); }
    .hq-progress__fill[data-tone='info'] { background: var(--hq-tone-info-fg, #1e40af); }
    .hq-progress__fill[data-tone='neutral'] { background: var(--hq-tone-neutral-fg, #374151); }
    @media (prefers-reduced-motion: reduce) { .hq-progress__fill { transition: none; } }
    :host-context([data-reduced-motion='true']) .hq-progress__fill { transition: none; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqProgress {
  @Input() value = 0;
  @Input() max = 100;
  @Input() label = '';
  @Input() tone: HqProgressTone = 'primary';
  @Input() showValue = true;
  /** How the value is written; 'percent' writes the share of `max`. */
  @Input() format: HqChartFormat = 'percent';
  @Input() currency = 'INR';
  @Input() size: 'sm' | 'md' = 'md';

  get percent(): number {
    const max = this.max > 0 ? this.max : 100;
    return Math.min(100, Math.max(0, ((Number(this.value) || 0) / max) * 100));
  }

  get valueText(): string {
    return this.format === 'percent'
      ? formatChartValue(this.percent, 'percent')
      : `${formatChartValue(this.value, this.format, this.currency)} / ${formatChartValue(this.max, this.format, this.currency)}`;
  }
}
