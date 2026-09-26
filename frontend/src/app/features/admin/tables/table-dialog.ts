import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { AdminTablesApi } from '../../../core/api/admin.api';
import { TableResponse } from '../../../core/api/models';
import { applyServerErrors, errorMessage, setServerError } from '../shared/form-errors';

export interface TableDialogData {
  table: TableResponse | null;
}

interface TableForm {
  label: FormControl<string>;
  active: FormControl<boolean>;
}

export const TABLE_LABEL_PATTERN = /^[A-Za-z0-9 _-]{1,20}$/;

/** Add or rename a table. */
@Component({
  selector: 'app-table-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './table-dialog.html',
})
export class TableDialog {
  private readonly api = inject(AdminTablesApi);
  protected readonly ref = inject<DialogRef<TableResponse, TableDialog>>(DialogRef);
  protected readonly data = inject<TableDialogData>(DIALOG_DATA);

  protected readonly form = new FormGroup<TableForm>({
    label: new FormControl(this.data.table?.label ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(TABLE_LABEL_PATTERN)],
    }),
    active: new FormControl(this.data.table?.active ?? true, { nonNullable: true }),
  });

  protected readonly saving = signal(false);
  protected readonly error = signal('');

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const body = { label: this.form.controls.label.value.trim(), active: this.form.controls.active.value };
    try {
      const existing = this.data.table;
      const saved = existing
        ? await firstValueFrom(this.api.update(existing.id, body))
        : await firstValueFrom(this.api.create(body));
      this.ref.close(saved);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE_LABEL') {
        setServerError(this.form.controls.label, error.message);
      } else if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const unmatched = applyServerErrors(this.form, error);
        if (unmatched.length) this.error.set(unmatched.join(' · '));
      } else {
        this.error.set(errorMessage(error, 'Could not save the table.'));
      }
    } finally {
      this.saving.set(false);
    }
  }
}
