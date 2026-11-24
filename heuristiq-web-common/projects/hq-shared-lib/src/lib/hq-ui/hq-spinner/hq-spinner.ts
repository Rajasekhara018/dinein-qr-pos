import { Component, Input, inject } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts } from '../hq-view-states/hq-ui-state.texts';

/** Spinner sizes. */
export type HqSpinnerSize = 'sm' | 'md' | 'lg';

/**
 * Small spinning ring in the theme's primary colour (inherits `color` when placed in coloured text via
 * `--hq-spinner-color`). `label` is read by screen readers only (default HQ_UI_STATE_TEXTS.loading()).
 *
 *   <hq-spinner size="sm"></hq-spinner>
 */
@Component({
  selector: 'hq-spinner',
  standalone: false,
  host: { role: 'status', class: 'hq-spinner-host' },
  template: `
    <span class="hq-spinner" [ngClass]="'hq-spinner--' + size" aria-hidden="true"></span>
    <span class="hq-spinner__label">{{ label || texts.loading() }}</span>
  `,
  styleUrl: './hq-spinner.scss',
})
export class HqSpinner {
  readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;
  @Input() size: HqSpinnerSize = 'md';
  /** Text for screen readers only; must be localised when set. */
  @Input() label = '';
}
