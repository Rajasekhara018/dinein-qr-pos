import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

export interface ConfirmReasonOptions {
  label: string;
  placeholder?: string;
  required?: boolean;
  maxLength?: number;
}

export interface ConfirmDialogData {
  title: string;
  message: string;
  /** Extra bullet points (consequences). */
  details?: string[];
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'danger' | 'primary';
  /** Adds a reason textarea; its value is returned. */
  reason?: ConfirmReasonOptions;
}

export interface ConfirmDialogResult {
  reason: string;
}

/** Confirmation dialog / bottom sheet (use `ConfirmService.confirm`). Closes with a result only when confirmed. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ConfirmDialogResult, ConfirmDialog>>(DialogRef);

  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [
      ...(this.data.reason?.required ? [Validators.required] : []),
      Validators.maxLength(this.data.reason?.maxLength ?? 300),
    ],
  });

  protected confirm(): void {
    if (this.data.reason && this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.ref.close({ reason: this.reason.value.trim() });
  }
}
