import { Component, Input } from '@angular/core';
import { HQ_STATUS_DEFAULT_TONE, HqStatusBadgeSize, HqStatusTone } from './hq-status-badge.constants';

/**
 * Small coloured pill for a status, coloured by the theme's tone tokens so it follows every theme.
 * `label` must already be the localised text (translate a status key map in the screen, falling back to the raw
 * code); when `label` is empty the projected content is shown instead.
 *
 *   <hq-status-badge tone="success" icon="bi-check-circle" [label]="'orders.status.PAID' | translate"></hq-status-badge>
 *   <hq-status-badge [tone]="toneOf(row.status)" size="sm">{{ row.status }}</hq-status-badge>
 */
@Component({
  selector: 'hq-status-badge',
  standalone: false,
  template: `
    <span class="hq-badge" [ngClass]="['hq-badge--' + tone, 'hq-badge--' + size]">
      <i *ngIf="icon" class="bi" [ngClass]="icon" aria-hidden="true"></i>
      @if (label) {{{ label }}} @else {<ng-content></ng-content>}
    </span>
  `,
  styleUrl: './hq-status-badge.scss',
})
export class HqStatusBadge {
  @Input() tone: HqStatusTone = HQ_STATUS_DEFAULT_TONE;
  /** Already-translated text; empty shows the projected content. */
  @Input() label = '';
  /** Optional bootstrap-icons class, e.g. `bi-check-circle`. */
  @Input() icon = '';
  @Input() size: HqStatusBadgeSize = 'md';
}
