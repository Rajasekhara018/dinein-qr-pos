import { FormBuilder } from '@angular/forms';
import { vi } from 'vitest';
import { SchemaField, SchemaStep } from '../onboarding-forms.models';
import { PreviewDialog } from './preview-dialog';

const field = (over: Partial<SchemaField> = {}): SchemaField => ({ key: 'f', label: 'Field', type: 'TEXT', required: false, ...over });
const ERR = 'onboardingForms.previewDialog.errors.';

function make(steps: SchemaStep[] = [{ key: 's1', title: 'One', fields: [field()] }, { key: 's2', title: 'Two', fields: [field({ key: 'g', type: 'CHECKBOX', required: true })] }]) {
  const dialogRef = { close: vi.fn() };
  const translate = { instant: vi.fn((key: string, params?: Record<string, unknown>) => key + (params ? JSON.stringify(params) : '')) };
  const dialog = new PreviewDialog(new FormBuilder(), translate as never, dialogRef as never, { typeLabel: 'Customer', steps });
  return { dialog, dialogRef };
}

describe('PreviewDialog', () => {
  describe('stepping', () => {
    it('starts on the first step and walks forward and back', () => {
      const { dialog } = make();
      expect([dialog.isFirstStep, dialog.isLastStep, dialog.currentStep?.key]).toEqual([true, false, 's1']);
      dialog.next();
      expect([dialog.isFirstStep, dialog.isLastStep, dialog.currentStep?.key]).toEqual([false, true, 's2']);
      dialog.back();
      dialog.back();
      expect(dialog.stepIndex).toBe(0);
    });

    it('does not advance while a required field of the step is empty, and closes after the last step', () => {
      const { dialog, dialogRef } = make();
      dialog.next();
      expect(dialog.canAdvance).toBe(false);
      dialog.next();
      expect(dialogRef.close).not.toHaveBeenCalled();
      dialog.currentForm!.get('g')!.setValue(true);
      expect(dialog.canAdvance).toBe(true);
      dialog.next();
      expect(dialogRef.close).toHaveBeenCalled();
    });

    it('handles a schema without steps', () => {
      const { dialog } = make([]);
      expect(dialog.currentStep).toBeNull();
      expect(dialog.currentForm).toBeNull();
      expect(dialog.canAdvance).toBe(true);
    });

    it('closes on request', () => {
      const { dialog, dialogRef } = make();
      dialog.close();
      expect(dialogRef.close).toHaveBeenCalled();
    });
  });

  describe('labels and displayed errors', () => {
    it('marks required fields with an asterisk', () => {
      const { dialog } = make();
      expect(dialog.fieldLabel(field({ required: true }))).toBe('Field *');
      expect(dialog.fieldLabel(field())).toBe('Field');
    });

    it('shows an error only once something has been typed', () => {
      const { dialog } = make([{ key: 's', title: 'S', fields: [field({ key: 'e', type: 'EMAIL', required: true })] }]);
      const f = dialog.steps[0].fields[0];
      expect(dialog.displayError(f)).toBeNull();
      dialog.currentForm!.get('e')!.setValue('nope');
      expect(dialog.displayError(f)).toContain(ERR + 'invalidEmail');
      dialog.currentForm!.get('e')!.setValue('a@b.co');
      expect(dialog.displayError(f)).toBeNull();
    });
  });

  describe('field rules', () => {
    const err = (f: SchemaField, v: unknown) => make().dialog.fieldError(f, v);

    it('requires required fields, including a ticked checkbox, and lets optional ones stay blank', () => {
      expect(err(field({ required: true }), '  ')).toContain(ERR + 'required');
      expect(err(field({ required: true }), null)).toContain(ERR + 'required');
      expect(err(field({ type: 'CHECKBOX', required: true }), false)).toContain(ERR + 'required');
      expect(err(field({ type: 'CHECKBOX', required: true }), true)).toBeNull();
      expect(err(field(), '')).toBeNull();
      expect(err(field({ type: 'CHECKBOX' }), false)).toBeNull();
    });

    it('checks email and phone formats, with a custom pattern overriding the default', () => {
      expect(err(field({ type: 'EMAIL' }), 'bad')).toContain(ERR + 'invalidEmail');
      expect(err(field({ type: 'EMAIL' }), 'a@b.co')).toBeNull();
      expect(err(field({ type: 'EMAIL', pattern: '@corp\\.com$' }), 'a@b.co')).toContain(ERR + 'invalidEmail');
      expect(err(field({ type: 'EMAIL', pattern: '@corp\\.com$' }), 'a@corp.com')).toBeNull();
      expect(err(field({ type: 'PHONE' }), '9876543210')).toContain(ERR + 'invalidPhone');
      expect(err(field({ type: 'PHONE' }), '+919876543210')).toBeNull();
      expect(err(field({ type: 'PHONE', pattern: '^0' }), '0123')).toBeNull();
    });

    it('applies a pattern to text only when one is set', () => {
      expect(err(field(), 'anything')).toBeNull();
      expect(err(field({ pattern: '^\\d+$' }), 'abc')).toContain(ERR + 'patternMismatch');
      expect(err(field({ pattern: '^\\d+$' }), '123')).toBeNull();
    });

    it('prefers the field\'s own error message over the generic one', () => {
      expect(err(field({ pattern: '^\\d+$', patternErrorMessage: 'Digits only' }), 'abc')).toBe('Digits only');
    });

    it('checks numbers, currency and their ranges', () => {
      expect(err(field({ type: 'NUMBER' }), 'x')).toContain(ERR + 'invalidNumber');
      expect(err(field({ type: 'NUMBER', minValue: 5 }), '3')).toContain(ERR + 'minValue');
      expect(err(field({ type: 'NUMBER', maxValue: 5 }), '9')).toContain(ERR + 'maxValue');
      expect(err(field({ type: 'NUMBER', minValue: 1, maxValue: 5 }), '3')).toBeNull();
      expect(err(field({ type: 'CURRENCY' }), '12.345')).toContain(ERR + 'invalidAmount');
      expect(err(field({ type: 'CURRENCY' }), '12.50')).toBeNull();
      expect(err(field({ type: 'CURRENCY', minValue: 10 }), '5')).toContain(ERR + 'minValue');
    });

    it('checks the length of text-like fields', () => {
      expect(err(field({ minLength: 3 }), 'ab')).toContain(ERR + 'minLength');
      expect(err(field({ maxLength: 3 }), 'abcd')).toContain(ERR + 'maxLength');
      expect(err(field({ minLength: 2, maxLength: 3 }), 'abc')).toBeNull();
      expect(err(field({ type: 'SELECT', minLength: 5 }), 'ab')).toBeNull(); // length rules do not apply
    });
  });
});
