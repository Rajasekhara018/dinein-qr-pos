import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { SchemaField, SchemaStep } from '../onboarding-forms.models';
import { CURRENCY_PATTERN, CURRENCY_SYMBOL, EMAIL_PATTERN, PHONE_PATTERN } from './preview-dialog.constants';

export interface PreviewDialogData {
  typeLabel: string;
  steps: SchemaStep[];
}

const ERR = 'onboardingForms.previewDialog.errors.';

/**
 * Read-only, client-side mock of what the rider/driver app renders from this schema
 * (OnboardingFormScreen in Rider_CustomerApp/Rider_DriverApp) -- lets an admin walk through the
 * exact step/field sequence they're authoring here without saving a draft or publishing first.
 * No backend calls: values are held in a throwaway FormGroup per step and discarded on close.
 */
@Component({
  standalone: false,
  selector: 'app-onboarding-preview-dialog',
  templateUrl: './preview-dialog.html',
  styleUrl: './preview-dialog.scss',
})
export class PreviewDialog {
  readonly currencySymbol = CURRENCY_SYMBOL;

  stepIndex = 0;
  forms: FormGroup[];

  constructor(
    private fb: FormBuilder,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<PreviewDialog>,
    @Inject(MAT_DIALOG_DATA) public data: PreviewDialogData,
  ) {
    this.forms = this.data.steps.map((step) => this.buildStepForm(step));
  }

  private buildStepForm(step: SchemaStep): FormGroup {
    const group: Record<string, [boolean | string]> = {};
    for (const field of step.fields) {
      group[field.key] = [field.type === 'CHECKBOX' ? false : ''];
    }
    return this.fb.group(group);
  }

  get steps(): SchemaStep[] {
    return this.data.steps;
  }

  get currentStep(): SchemaStep | null {
    return this.steps[this.stepIndex] ?? null;
  }

  get currentForm(): FormGroup | null {
    return this.forms[this.stepIndex] ?? null;
  }

  get isFirstStep(): boolean {
    return this.stepIndex === 0;
  }

  get isLastStep(): boolean {
    return this.stepIndex === this.steps.length - 1;
  }

  /** Mirrors OnboardingFormScreen's own _validateStep (required) plus the backend
   *  OnboardingSubmissionService's format/range rules -- required fields (including an
   *  unchecked CHECKBOX) must be filled, and any present value must pass its field's own
   *  pattern/range before the mock "Next"/"Submit" proceeds. */
  get canAdvance(): boolean {
    const step = this.currentStep;
    const form = this.currentForm;
    if (!step || !form) return true;
    return step.fields.every((field) => this.fieldError(field, form.get(field.key)?.value) === null);
  }

  fieldError(field: SchemaField, value: unknown): string | null {
    const label = field.label;
    const isBlank =
      value === null || value === undefined || (typeof value === 'string' && value.trim() === '') ||
      (field.type === 'CHECKBOX' && value !== true);
    if (isBlank) {
      return field.required ? this.translate.instant(`${ERR}required`, { label }) : null;
    }

    const text = String(value);
    const fail = (key: string, params: Record<string, unknown> = {}) =>
      field.patternErrorMessage ?? this.translate.instant(`${ERR}${key}`, { label, ...params });

    switch (field.type) {
      case 'EMAIL': {
        const re = field.pattern ? new RegExp(field.pattern) : EMAIL_PATTERN;
        if (!re.test(text)) return fail('invalidEmail');
        break;
      }
      case 'PHONE': {
        const re = field.pattern ? new RegExp(field.pattern) : PHONE_PATTERN;
        if (!re.test(text)) return fail('invalidPhone');
        break;
      }
      case 'TEXT': {
        if (field.pattern && !new RegExp(field.pattern).test(text)) {
          return fail('patternMismatch');
        }
        break;
      }
      case 'NUMBER':
      case 'CURRENCY': {
        const numeric = Number(text);
        if (Number.isNaN(numeric) || (field.type === 'CURRENCY' && !CURRENCY_PATTERN.test(text))) {
          return fail(field.type === 'CURRENCY' ? 'invalidAmount' : 'invalidNumber');
        }
        if (field.minValue !== undefined && numeric < field.minValue) return fail('minValue', { value: field.minValue });
        if (field.maxValue !== undefined && numeric > field.maxValue) return fail('maxValue', { value: field.maxValue });
        break;
      }
      default:
        break;
    }

    if (field.type === 'TEXT' || field.type === 'PHONE' || field.type === 'EMAIL') {
      if (field.minLength !== undefined && text.length < field.minLength) {
        return fail('minLength', { value: field.minLength });
      }
      if (field.maxLength !== undefined && text.length > field.maxLength) {
        return fail('maxLength', { value: field.maxLength });
      }
    }

    return null;
  }

  fieldLabel(field: SchemaField): string {
    return field.required ? `${field.label} *` : field.label;
  }

  /** Error to display under a field -- only once the admin has typed something into it. */
  displayError(field: SchemaField): string | null {
    const value = this.currentForm?.get(field.key)?.value;
    return value ? this.fieldError(field, value) : null;
  }

  back(): void {
    if (this.stepIndex > 0) this.stepIndex -= 1;
  }

  next(): void {
    if (!this.canAdvance) return;
    if (this.isLastStep) {
      this.close();
      return;
    }
    this.stepIndex += 1;
  }

  close(): void {
    this.dialogRef.close();
  }
}
