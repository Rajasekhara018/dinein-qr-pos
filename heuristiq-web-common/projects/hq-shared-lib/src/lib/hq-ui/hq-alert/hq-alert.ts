import { Component, EventEmitter, Input, Output, booleanAttribute, inject } from '@angular/core';
import { HqStatusTone } from '../hq-status-badge/hq-status-badge.constants';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqCloseText } from '../hq-view-states/hq-ui-state.texts';
import { HQ_ALERT_DEFAULT_TONE, HQ_ALERT_NO_ICON, HQ_ALERT_TONE_ICONS, HQ_ALERT_URGENT_TONES } from './hq-alert.constants';

/**
 * An inline message box coloured by the theme's tone tokens (follows every theme). The message is the projected
 * content; `title` is an optional bold first line. Danger and warning are announced at once (role="alert"), the
 * other tones politely (role="status").
 *
 *   <hq-alert tone="warning" [title]="'wallet.notReconciled' | translate">{{ 'wallet.notReconciledBody' | translate }}</hq-alert>
 *   <hq-alert tone="success" dismissible (dismissed)="saved = false">{{ 'common.saved' | translate }}</hq-alert>
 */
@Component({
  selector: 'hq-alert',
  standalone: false,
  host: {
    '[attr.role]': 'role',
    class: 'hq-alert',
    '[class.hq-alert--success]': "tone === 'success'",
    '[class.hq-alert--warning]': "tone === 'warning'",
    '[class.hq-alert--danger]': "tone === 'danger'",
    '[class.hq-alert--info]': "tone === 'info'",
    '[class.hq-alert--neutral]': "tone === 'neutral'",
    '[class.hq-alert--dense]': 'dense',
  },
  template: `
    @if (iconClass) {<i class="bi hq-alert__icon" [ngClass]="iconClass" aria-hidden="true"></i>}
    <div class="hq-alert__body">
      @if (title) {<div class="hq-alert__title">{{ title }}</div>}
      <div class="hq-alert__text"><ng-content></ng-content></div>
    </div>
    @if (dismissible) {
      <button type="button" class="hq-alert__close" [attr.aria-label]="closeText" [title]="closeText" (click)="dismissed.emit()">
        <i class="bi bi-x-lg" aria-hidden="true"></i>
      </button>
    }
  `,
  styleUrl: './hq-alert.scss',
})
export class HqAlert {
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;

  @Input() tone: HqStatusTone = HQ_ALERT_DEFAULT_TONE;
  /** Already-translated bold first line; optional. */
  @Input() title = '';
  /** bootstrap-icons class; empty uses the tone's icon, 'none' hides it. */
  @Input() icon = '';
  @Input({ transform: booleanAttribute }) dismissible = false;
  /** Tighter padding for use inside forms and tables. */
  @Input({ transform: booleanAttribute }) dense = false;
  /** The dismiss button was pressed; the screen hides the alert. */
  @Output() dismissed = new EventEmitter<void>();

  get role(): 'alert' | 'status' {
    return HQ_ALERT_URGENT_TONES.includes(this.tone) ? 'alert' : 'status';
  }

  get iconClass(): string {
    if (this.icon === HQ_ALERT_NO_ICON) return '';
    return this.icon || HQ_ALERT_TONE_ICONS[this.tone] || '';
  }

  get closeText(): string {
    return hqCloseText(this.texts);
  }
}
