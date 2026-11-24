import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts } from './hq-ui-state.texts';

/** Reads the app's HQ_UI_STATE_TEXTS, falling back to the English defaults. */
function stateTexts(): HqUiStateTexts {
  return inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;
}

/**
 * "Nothing to show" block. `title` / `message` must already be localised when set; the default title comes from
 * HQ_UI_STATE_TEXTS.empty(). Projected content (e.g. a "Create" button) is shown below the text.
 *
 *   <hq-empty-state icon="bi-people" [title]="'users.none' | translate"><button hqButton>…</button></hq-empty-state>
 */
@Component({
  selector: 'hq-empty-state',
  standalone: false,
  template: `
    <div class="hq-state hq-state--empty">
      <i class="bi" [ngClass]="icon" aria-hidden="true"></i>
      <strong>{{ title || texts.empty() }}</strong>
      <span *ngIf="message" class="hq-state__body">{{ message }}</span>
      <ng-content></ng-content>
    </div>
  `,
  styleUrl: './hq-view-states.scss',
})
export class HqEmptyState {
  readonly texts = stateTexts();
  @Input() title = '';
  @Input() message = '';
  /** bootstrap-icons class of the icon tile. */
  @Input() icon = 'bi-inbox';
}

/**
 * Failed-load block. `message` should be the localised reason; empty shows HQ_UI_STATE_TEXTS.errorBody().
 * The Retry button is shown only when the usage listens to `(retry)`.
 *
 *   <hq-error-state [message]="errorText" (retry)="load()"></hq-error-state>
 */
@Component({
  selector: 'hq-error-state',
  standalone: false,
  template: `
    <div class="hq-state hq-state--error" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <strong>{{ title || texts.error() }}</strong>
      <span class="hq-state__body">{{ message || texts.errorBody() }}</span>
      <button *ngIf="retry.observed" hqButton="secondary" appearance="outline" size="sm" type="button" (click)="retry.emit()">
        {{ texts.retry() }}
      </button>
      <ng-content></ng-content>
    </div>
  `,
  styleUrl: './hq-view-states.scss',
})
export class HqErrorState {
  readonly texts = stateTexts();
  @Input() title = '';
  @Input() message = '';
  @Output() retry = new EventEmitter<void>();
}

/**
 * Centred spinner with a label (default HQ_UI_STATE_TEXTS.loading()); `label` must be localised when set.
 *
 *   <hq-loading-state *ngIf="isLoading"></hq-loading-state>
 */
@Component({
  selector: 'hq-loading-state',
  standalone: false,
  template: `
    <div class="hq-state hq-state--loading" role="status" aria-live="polite">
      <span class="hq-state__spinner" aria-hidden="true"></span>
      <span>{{ label || texts.loading() }}</span>
    </div>
  `,
  styleUrl: './hq-view-states.scss',
})
export class HqLoadingState {
  readonly texts = stateTexts();
  @Input() label = '';
}
