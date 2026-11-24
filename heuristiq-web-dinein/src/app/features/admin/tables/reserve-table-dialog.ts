import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ReserveTableRequest, TableResponse } from '../../../core/api/models';

export interface ReserveTableDialogData {
  table: TableResponse;
}

interface ReserveForm {
  until: FormControl<string>;
  note: FormControl<string>;
}

/** Books a table ahead of a group's arrival. Closes with the reservation, or undefined if cancelled. */
@Component({
  selector: 'app-reserve-table-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reserve-table-dialog.html',
})
export class ReserveTableDialog {
  protected readonly ref = inject<DialogRef<ReserveTableRequest | undefined, ReserveTableDialog>>(DialogRef);
  protected readonly data = inject<ReserveTableDialogData>(DIALOG_DATA);

  /** `datetime-local` needs no seconds/zone; an hour from now is a sane default. */
  private readonly defaultUntil = new Date(Date.now() + 60 * 60_000).toISOString().slice(0, 16);

  protected readonly form = new FormGroup<ReserveForm>({
    until: new FormControl(this.defaultUntil, { nonNullable: true, validators: [Validators.required] }),
    note: new FormControl(this.data.table.reservedNote ?? '', { nonNullable: true, validators: [Validators.maxLength(100)] }),
  });

  protected readonly error = signal('');

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const until = new Date(value.until);
    if (Number.isNaN(until.getTime()) || until.getTime() <= Date.now()) {
      this.error.set('Pick a time in the future.');
      return;
    }
    this.ref.close({ until: until.toISOString(), note: value.note.trim() || null });
  }
}
