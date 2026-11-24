import { FormBuilder } from '@angular/forms';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SchemaField } from '../onboarding-forms.models';
import { FieldDialog } from './field-dialog';

const docTypePage = { content: [
  { code: 'DRIVING_LICENCE', category: 'driver-documents', names: { en: 'Driving licence' } },
  { code: 'CUSTOMER_PHOTO', category: 'customer-documents', names: { en: 'Photo' } },
], page: 0, size: 100, totalElements: 2, totalPages: 1 };

function make(existing: SchemaField | null = null, takenKeys: string[] = [], types: Observable<unknown> = of(docTypePage)) {
  const backdrop$ = new Subject<void>();
  const keys$ = new Subject<{ key: string }>();
  const dialogRef = { close: vi.fn(), disableClose: false, backdropClick: () => backdrop$, keydownEvents: () => keys$ };
  const appDialog = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  const translate = { instant: vi.fn((k: string) => k), currentLang: 'en' };
  const documentTypes = { list: vi.fn(() => types) };
  const dialog = new FieldDialog(new FormBuilder(), dialogRef as never, appDialog as never, translate as never, documentTypes as never, { field: existing, takenKeys });
  dialog.ngOnInit();
  return { dialog, dialogRef, appDialog, backdrop$, keys$, documentTypes };
}
const fill = (d: FieldDialog, v: Record<string, unknown>) => d.form.patchValue(v);

describe('FieldDialog', () => {
  describe('opening', () => {
    it('starts a new TEXT field, invalid until key and label are given', () => {
      const { dialog, dialogRef } = make();
      expect(dialog.isEdit).toBe(false);
      expect(dialog.form.value.type).toBe('TEXT');
      expect(dialog.canSubmit).toBe(false);
      expect(dialogRef.disableClose).toBe(true);
      expect(dialog.hasChanges).toBe(false);
    });

    it('loads an existing field including its options', () => {
      const { dialog } = make({ key: 'city', label: 'City', type: 'SELECT', required: true, options: ['A', 'B'] });
      expect(dialog.isEdit).toBe(true);
      expect(dialog.optionsArray.value).toEqual(['A', 'B']);
      expect(dialog.isSelectType).toBe(true);
      expect(dialog.canSubmit).toBe(true);
    });

    it('detects the kind of field from the chosen type', () => {
      const { dialog } = make();
      for (const [type, flags] of [['NUMBER', [false, false, true, false]], ['CURRENCY', [false, false, true, false]], ['TEXT', [false, false, false, true]], ['EMAIL', [false, false, false, true]], ['DOCUMENT', [false, true, false, false]], ['SELECT', [true, false, false, false]]] as const) {
        fill(dialog, { type });
        expect([dialog.isSelectType, dialog.isDocumentType, dialog.isNumericType, dialog.isTextLikeType]).toEqual(flags);
      }
    });
  });

  describe('validation', () => {
    it('rejects keys that are not identifiers', () => {
      const { dialog } = make();
      fill(dialog, { key: '1abc', label: 'L' });
      expect(dialog.form.get('key')?.hasError('pattern')).toBe(true);
      dialog.form.get('key')?.markAsTouched();
      expect(dialog.hasError('key', 'pattern')).toBe(true);
      expect(dialog.hasError('label', 'required')).toBe(false);
      fill(dialog, { key: 'abc_1' });
      expect(dialog.canSubmit).toBe(true);
    });

    it('refuses a key another field of the form already uses', () => {
      const { dialog } = make(null, ['email', 'phone']);
      fill(dialog, { key: 'email', label: 'L' });
      expect(dialog.form.get('key')?.hasError('keyDuplicate')).toBe(true);
      expect(dialog.canSubmit).toBe(false);
      fill(dialog, { key: 'fullName' });
      expect(dialog.canSubmit).toBe(true);
    });

    it('lets a field keep its own key when it is edited', () => {
      const { dialog } = make({ key: 'email', label: 'Email', type: 'EMAIL', required: true }, ['phone']);
      expect(dialog.form.get('key')?.hasError('keyDuplicate')).toBe(false);
      expect(dialog.canSubmit).toBe(true);
    });

    it('refuses a pattern that is not a valid regular expression, and says so as the user types', () => {
      const { dialog } = make();
      fill(dialog, { key: 'k', label: 'L', pattern: '[' });
      expect(dialog.hasRuleError('patternInvalid')).toBe(true);
      expect(dialog.canSubmit).toBe(false);
      fill(dialog, { pattern: '^\\[$' });
      expect(dialog.hasRuleError('patternInvalid')).toBe(false);
      expect(dialog.canSubmit).toBe(true);
    });

    it('refuses a pattern longer than the limit', () => {
      const { dialog } = make();
      fill(dialog, { key: 'k', label: 'L', pattern: 'a'.repeat(dialog.maxPatternLength + 1) });
      expect(dialog.hasRuleError('patternTooLong')).toBe(true);
      expect(dialog.canSubmit).toBe(false);
    });

    it('refuses a minimum length above the maximum length, and a negative length', () => {
      const { dialog, dialogRef } = make();
      fill(dialog, { key: 'k', label: 'L', minLength: 10, maxLength: 2 });
      expect(dialog.hasRuleError('lengthRange')).toBe(true);
      dialog.submit();
      expect(dialogRef.close).not.toHaveBeenCalled();
      fill(dialog, { minLength: -1, maxLength: 5 });
      expect(dialog.hasRuleError('lengthNegative')).toBe(true);
      expect(dialog.canSubmit).toBe(false);
      fill(dialog, { minLength: 2, maxLength: 5 });
      expect(dialog.canSubmit).toBe(true);
    });

    it('refuses a minimum value above the maximum value on a number field only', () => {
      const { dialog } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'NUMBER', minValue: 9, maxValue: 1 });
      expect(dialog.hasRuleError('valueRange')).toBe(true);
      expect(dialog.canSubmit).toBe(false);
      fill(dialog, { type: 'TEXT' });
      expect(dialog.hasRuleError('valueRange')).toBe(false);
      expect(dialog.canSubmit).toBe(true);
    });

    it('needs at least one option for a select', () => {
      const { dialog } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'SELECT' });
      expect(dialog.canSubmit).toBe(false);
      dialog.submit();
      expect(dialog.errorMessage).toBe('onboardingForms.fieldDialog.optionsRequired');
      dialog.addOption();
      expect(dialog.canSubmit).toBe(false); // empty option is invalid
      dialog.optionsArray.at(0).setValue('Yes');
      expect(dialog.canSubmit).toBe(true);
      dialog.removeOption(0);
      expect(dialog.optionsArray.length).toBe(0);
    });
  });

  describe('submit', () => {
    it('does nothing while invalid', () => {
      const { dialog, dialogRef } = make();
      dialog.submit();
      expect(dialogRef.close).not.toHaveBeenCalled();
    });

    it('returns a simple field with only its basics', () => {
      const { dialog, dialogRef } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'DATE', required: true, pattern: 'ignored', minValue: 3 });
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith({ key: 'k', label: 'L', type: 'DATE', required: true });
    });

    it('keeps non-blank options only, for a select', () => {
      const { dialog, dialogRef } = make({ key: 'k', label: 'L', type: 'SELECT', required: false, options: ['A', ' ', 'B'] });
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith(expect.objectContaining({ options: ['A', 'B'] }));
    });

    it('keeps a trimmed document category for a document', () => {
      const { dialog, dialogRef } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'DOCUMENT', documentCategory: ' pan ' });
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith({ key: 'k', label: 'L', type: 'DOCUMENT', required: false, documentCategory: 'pan' });
    });

    it('adds pattern, message and length limits for text-like fields, skipping blanks', () => {
      const { dialog, dialogRef } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'TEXT', pattern: ' ^a ', patternErrorMessage: ' Starts with a ', minLength: 2, maxLength: '' });
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith({ key: 'k', label: 'L', type: 'TEXT', required: false, pattern: '^a', patternErrorMessage: 'Starts with a', minLength: 2 });
    });

    it('adds a numeric range for numbers, keeping a zero', () => {
      const { dialog, dialogRef } = make();
      fill(dialog, { key: 'k', label: 'L', type: 'NUMBER', minValue: 0, maxValue: '99' });
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith({ key: 'k', label: 'L', type: 'NUMBER', required: false, minValue: 0, maxValue: 99 });
    });
  });

  describe('cancelling', () => {
    it('closes at once when nothing changed', () => {
      const { dialog, dialogRef, appDialog } = make();
      dialog.cancel();
      expect(dialogRef.close).toHaveBeenCalledWith(null);
      expect(appDialog.confirm).not.toHaveBeenCalled();
    });

    it('asks before discarding edits, and closes only when confirmed', () => {
      const { dialog, dialogRef, appDialog } = make();
      fill(dialog, { label: 'Changed' });
      expect(dialog.hasChanges).toBe(true);
      appDialog.confirm.mockImplementation(() => of(false));
      dialog.cancel();
      expect(dialogRef.close).not.toHaveBeenCalled();
      appDialog.confirm.mockImplementation(() => of(true));
      dialog.cancel();
      expect(appDialog.confirm).toHaveBeenLastCalledWith(expect.objectContaining({ variant: 'warning' }));
      expect(dialogRef.close).toHaveBeenCalledWith(null);
    });

    it('does not stack discard prompts while one is open', () => {
      const { dialog, appDialog } = make();
      fill(dialog, { label: 'Changed' });
      const pending = new Subject<boolean>();
      appDialog.confirm.mockReturnValue(pending);
      dialog.cancel();
      dialog.cancel();
      expect(appDialog.confirm).toHaveBeenCalledTimes(1);
      pending.next(false);
      dialog.cancel();
      expect(appDialog.confirm).toHaveBeenCalledTimes(2);
    });

    it('routes the backdrop and Escape through the same discard prompt, ignoring other keys', () => {
      const { dialog, dialogRef, backdrop$, keys$ } = make();
      backdrop$.next();
      expect(dialogRef.close).toHaveBeenCalledTimes(1);
      keys$.next({ key: 'a' });
      expect(dialogRef.close).toHaveBeenCalledTimes(1);
      keys$.next({ key: 'Escape' });
      expect(dialogRef.close).toHaveBeenCalledTimes(2);
      dialog.ngOnDestroy();
      backdrop$.next();
      expect(dialogRef.close).toHaveBeenCalledTimes(2);
    });

    it('blocks the browser unload only with unsaved edits', () => {
      const { dialog } = make();
      const clean = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      dialog.beforeUnload(clean);
      expect(clean.preventDefault).not.toHaveBeenCalled();
      fill(dialog, { label: 'Changed' });
      const dirty = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      dialog.beforeUnload(dirty);
      expect(dirty.preventDefault).toHaveBeenCalled();
    });
  });

  describe('document type', () => {
    it('offers the active document types, sorted by name with their code', () => {
      const { dialog, documentTypes } = make();
      expect(documentTypes.list).toHaveBeenCalledWith(expect.objectContaining({ active: 'true', size: 100 }));
      expect(dialog.documentTypes.map((t) => t.code)).toEqual(['DRIVING_LICENCE', 'CUSTOMER_PHOTO']);
      expect(dialog.documentTypes[0].label).toBe('Driving licence (DRIVING_LICENCE)');
    });

    it("sets the category from the chosen type and locks it, then frees it when the type is cleared", () => {
      const { dialog } = make();
      fill(dialog, { type: 'DOCUMENT', documentCategory: 'old', documentType: 'DRIVING_LICENCE' });
      expect(dialog.form.get('documentCategory')!.value).toBe('driver-documents');
      expect(dialog.form.get('documentCategory')!.disabled).toBe(true);
      expect(dialog.categoryFromType).toBe(true);
      fill(dialog, { documentType: '' });
      expect(dialog.form.get('documentCategory')!.enabled).toBe(true);
      expect(dialog.categoryFromType).toBe(false);
    });

    it("saves the document type with the type's category, and keeps the type of a field being edited", () => {
      const { dialog, dialogRef } = make({ key: 'dl', label: 'DL', type: 'DOCUMENT', required: true, documentCategory: 'driver-documents', documentType: 'DRIVING_LICENCE' });
      expect(dialog.form.get('documentType')!.value).toBe('DRIVING_LICENCE');
      dialog.submit();
      expect(dialogRef.close).toHaveBeenCalledWith({ key: 'dl', label: 'DL', type: 'DOCUMENT', required: true, documentCategory: 'driver-documents', documentType: 'DRIVING_LICENCE' });
    });

    it('leaves the category to be typed when the types cannot be read', () => {
      const { dialog } = make(null, [], throwError(() => ({ status: 403 })));
      expect(dialog.documentTypesFailed).toBe(true);
      expect(dialog.documentTypes).toEqual([]);
      expect(dialog.form.get('documentCategory')!.enabled).toBe(true);
    });
  });
});
