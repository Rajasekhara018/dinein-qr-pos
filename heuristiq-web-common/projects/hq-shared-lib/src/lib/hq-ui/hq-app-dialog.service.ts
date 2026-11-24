import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { map, Observable } from 'rxjs';
import { HqAppDialog, HqAppDialogData, HqAppDialogField, HqAppDialogFormValues } from './hq-app-dialog/hq-app-dialog';

/** Opens HqAppDialog as a confirm (true/false), a single-value prompt, or a small form. */
@Injectable({ providedIn: 'root' })
export class HqAppDialogService {
  constructor(private dialog: MatDialog) {}

  confirm(data: HqAppDialogData): Observable<boolean> {
    return this.open(data).pipe(map((result) => result === true));
  }

  /** A one-field form; resolves the field's value as a string (a checkbox gives "true"/"false", a multiselect a comma-joined list). */
  prompt(data: Omit<HqAppDialogData, 'fields'> & { field: HqAppDialogField }): Observable<string | null> {
    return this.form<HqAppDialogFormValues>({ ...data, fields: [data.field] }).pipe(
      map((result) => result ? String(result[data.field.key] ?? '') : null),
    );
  }

  /**
   * Resolves the field values keyed by field key, or null when cancelled. Each value is a string
   * for text / number / textarea / select fields, a `string[]` for multiselect and a boolean for
   * checkbox. `R` defaults to `Record<string, string>` for the common all-text form; a form with
   * checkbox or multiselect fields names its shape, e.g. `form<{ roles: string[]; active: boolean }>(...)`.
   */
  form<R extends HqAppDialogFormValues = Record<string, string>>(data: HqAppDialogData): Observable<R | null> {
    return this.open(data).pipe(map((result) => result && result !== true ? result as R : null));
  }

  private open(data: HqAppDialogData): Observable<unknown> {
    return this.dialog.open(HqAppDialog, {
      width: '460px',
      maxWidth: '94vw',
      autoFocus: true,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data,
    }).afterClosed();
  }
}
