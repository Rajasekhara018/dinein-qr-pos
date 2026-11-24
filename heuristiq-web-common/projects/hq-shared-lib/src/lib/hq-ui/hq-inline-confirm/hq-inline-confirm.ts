import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts } from '../hq-view-states/hq-ui-state.texts';
import {
  HQ_INLINE_CONFIRM_MAX_REASON,
  HQ_INLINE_CONFIRM_REASON_ROWS,
  HqInlineConfirmVariant,
} from './hq-inline-confirm.constants';

let nextInlineConfirm = 0;

/**
 * Inline confirmation bar (instead of a confirm / confirm-with-reason modal): render it next to the action with
 * `*ngIf`, pass already-translated texts, and handle `confirmed` (emits the trimmed reason, '' without a reason
 * field) and `cancelled`. It is announced to assistive tech (aria-live) and moves focus to its first control
 * (the reason box, else the Cancel button). Escape cancels. Default button words come from HQ_UI_STATE_TEXTS.
 *
 *   <hq-inline-confirm *ngIf="confirmingDelete" variant="danger" [title]="…" withReason [reasonLabel]="…"
 *                       [busy]="deleting" (confirmed)="delete($event)" (cancelled)="confirmingDelete = false">
 *   </hq-inline-confirm>
 */
@Component({
  selector: 'hq-inline-confirm',
  standalone: false,
  templateUrl: './hq-inline-confirm.html',
  styleUrl: './hq-inline-confirm.scss',
})
export class HqInlineConfirm implements AfterViewInit {
  readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;
  readonly reasonRows = HQ_INLINE_CONFIRM_REASON_ROWS;

  @Input() title = '';
  @Input() message = '';
  @Input() variant: HqInlineConfirmVariant = 'warning';
  /** Defaults to HQ_UI_STATE_TEXTS.confirm(). */
  @Input() confirmLabel = '';
  /** Defaults to HQ_UI_STATE_TEXTS.cancel(). */
  @Input() cancelLabel = '';
  @Input() busy = false;
  /** Shows the reason textarea. */
  @Input() withReason = false;
  @Input() reasonRequired = true;
  @Input() reasonLabel = '';
  @Input() maxLength = HQ_INLINE_CONFIRM_MAX_REASON;
  /** Extra disable condition from the parent (e.g. nothing selected). */
  @Input() confirmDisabled = false;
  @Output() confirmed = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('firstControl', { read: ElementRef }) private firstControl?: ElementRef<HTMLElement>;

  reason = '';
  touched = false;
  readonly uid = `hq-ic-${++nextInlineConfirm}`;

  get reasonInvalid(): boolean {
    return this.withReason && this.reasonRequired && !this.reason.trim();
  }

  ngAfterViewInit(): void {
    // The reason box is a hq-field: focus the textarea inside it (the cancel button is focused as it is).
    setTimeout(() => {
      const host = this.firstControl?.nativeElement;
      (host?.querySelector<HTMLElement>('textarea, input') ?? host)?.focus();
    });
  }

  submit(): void {
    if (this.busy || this.confirmDisabled) return;
    if (this.reasonInvalid) {
      this.touched = true;
      return;
    }
    this.confirmed.emit(this.withReason ? this.reason.trim().slice(0, this.maxLength) : '');
  }

  cancel(): void {
    if (!this.busy) this.cancelled.emit();
  }
}
