import { Component, Inject, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HQ_APP_DIALOG_DEFAULT_TEXTS, HQ_APP_DIALOG_TEXTS, HqAppDialogTexts } from '../hq-app-dialog.texts';
import { HqOption } from '../hq-option.model';

export type HqAppDialogFieldType = 'text' | 'number' | 'textarea' | 'select' | 'checkbox' | 'multiselect';

/** What a field holds: a string for text/number/textarea/select, `string[]` for multiselect, boolean for checkbox. */
export type HqAppDialogValue = string | string[] | boolean;

/** The values a form dialog resolves with, keyed by field key. */
export type HqAppDialogFormValues = Record<string, HqAppDialogValue>;

export interface HqAppDialogField {
  key: string;
  label: string;
  /** Initial value: string (text/number/textarea/select), string[] (multiselect) or boolean (checkbox). */
  value?: HqAppDialogValue;
  type?: HqAppDialogFieldType;
  /** The choices of a `select` / `multiselect` field; the chosen option value(s) are returned under the field's key. */
  options?: HqOption[];
  /** Text of the empty first option of a `select` field (the app passes its translated text). */
  placeholderOption?: string;
  /** For `multiselect`: at least one option must be chosen. Ignored for `checkbox`. */
  required?: boolean;
  placeholder?: string;
  /** Explanatory text under the control; `hint` is an alias kept for callers ported from the admin CRUD form. */
  helperText?: string;
  hint?: string;
  errorText?: string;
  min?: number;
  max?: number;
}

export interface HqAppDialogData {
  title: string;
  message?: string;
  icon?: string;
  variant?: 'primary' | 'danger' | 'warning';
  confirmText?: string;
  cancelText?: string;
  fields?: HqAppDialogField[];
  loading?: boolean;
  loadingText?: string;
}

/** A confirm / prompt / small-form dialog. Open it through HqAppDialogService. */
@Component({
  selector: 'hq-app-dialog',
  standalone: false,
  templateUrl: './hq-app-dialog.html',
  styleUrls: ['./hq-app-dialog.scss'],
})
export class HqAppDialog {
  private readonly texts: HqAppDialogTexts =
    inject(HQ_APP_DIALOG_TEXTS, { optional: true }) ?? HQ_APP_DIALOG_DEFAULT_TEXTS;
  values: HqAppDialogFormValues = {};

  constructor(
    private dialogRef: MatDialogRef<HqAppDialog>,
    @Inject(MAT_DIALOG_DATA) public data: HqAppDialogData,
  ) {
    (data.fields || []).forEach((field) => {
      this.values[field.key] = this.initialValue(field);
    });
  }

  get hasFields(): boolean {
    return Boolean(this.data.fields?.length);
  }

  get canSubmit(): boolean {
    if (this.data.loading) return false;
    return (this.data.fields || []).every((field) => !this.getFieldError(field));
  }

  get cancelLabel(): string {
    return this.data.cancelText || this.texts.cancel();
  }

  get confirmLabel(): string {
    return this.data.loading
      ? (this.data.loadingText || this.texts.working())
      : (this.data.confirmText || this.texts.confirm());
  }

  /** True for the field types rendered as a plain `<input>` (text / number). */
  isInputField(field: HqAppDialogField): boolean {
    const type = field.type ?? 'text';
    return type === 'text' || type === 'number';
  }

  fieldHint(field: HqAppDialogField): string {
    return field.helperText || field.hint || '';
  }

  getFieldError(field: HqAppDialogField): string {
    const raw = this.values[field.key];
    if (field.type === 'checkbox') return '';
    if (field.type === 'multiselect') {
      const chosen = Array.isArray(raw) ? raw : [];
      return field.required && chosen.length === 0 ? (field.errorText || this.texts.required(field.label)) : '';
    }
    const value = String(raw ?? '').trim();
    if (field.required && !value) {
      return field.errorText || this.texts.required(field.label);
    }
    if (value && field.type === 'number') {
      const numericValue = Number(value);
      if (!Number.isFinite(numericValue)) return this.texts.invalidNumber(field.label);
      if (field.min !== undefined && numericValue < field.min) return this.texts.min(field.label, field.min);
      if (field.max !== undefined && numericValue > field.max) return this.texts.max(field.label, field.max);
    }
    return '';
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.dialogRef.close(this.hasFields ? this.values : true);
  }

  /** Coerces the caller's `value` to the shape the field's control binds to, so a wrong type never leaks into the form. */
  private initialValue(field: HqAppDialogField): HqAppDialogValue {
    const value = field.value;
    if (field.type === 'checkbox') return value === true;
    if (field.type === 'multiselect') return Array.isArray(value) ? [...value] : [];
    if (Array.isArray(value)) return value.join(', ');
    if (typeof value === 'boolean') return value ? 'true' : 'false';
    return value ?? '';
  }
}
