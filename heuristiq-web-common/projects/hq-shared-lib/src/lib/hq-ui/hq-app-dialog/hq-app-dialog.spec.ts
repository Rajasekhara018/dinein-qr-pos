import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { HQ_APP_DIALOG_DEFAULT_TEXTS, HQ_APP_DIALOG_TEXTS, HqAppDialogTexts } from '../hq-app-dialog.texts';
import { HqAppDialog, HqAppDialogData, HqAppDialogField } from './hq-app-dialog';

interface Setup {
  fixture: ComponentFixture<HqAppDialog>;
  component: HqAppDialog;
  close: ReturnType<typeof vi.fn>;
  el: HTMLElement;
}

async function setup(data: HqAppDialogData, texts?: HqAppDialogTexts): Promise<Setup> {
  const close = vi.fn();
  TestBed.configureTestingModule({
    imports: [CommonModule, FormsModule, MatCheckboxModule, MatSelectModule],
    declarations: [HqAppDialog],
    providers: [
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
      ...(texts ? [{ provide: HQ_APP_DIALOG_TEXTS, useValue: texts }] : []),
    ],
  });
  const fixture = TestBed.createComponent(HqAppDialog);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, close, el: fixture.nativeElement as HTMLElement };
}

function buttons(el: HTMLElement): { cancel: HTMLButtonElement; confirm: HTMLButtonElement } {
  return {
    cancel: el.querySelector('.btn-cancel') as HTMLButtonElement,
    confirm: el.querySelector('.btn-confirm') as HTMLButtonElement,
  };
}

async function typeInto(fixture: ComponentFixture<HqAppDialog>, control: HTMLInputElement | HTMLTextAreaElement, value: string): Promise<void> {
  control.value = value;
  control.dispatchEvent(new Event('input'));
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('HqAppDialog', () => {
  describe('confirm mode', () => {
    it('renders title, message and default English buttons, and closes with true on OK', async () => {
      const { el, close } = await setup({ title: 'Delete?', message: 'This cannot be undone' });
      expect(el.querySelector('h2')?.textContent).toContain('Delete?');
      expect(el.querySelector('.dialog-header p')?.textContent).toContain('This cannot be undone');
      expect(el.querySelector('.dialog-fields')).toBeNull();
      const { cancel, confirm } = buttons(el);
      expect(cancel.textContent?.trim()).toBe('Cancel');
      expect(confirm.textContent?.trim()).toBe('OK');
      expect(confirm.disabled).toBe(false);
      confirm.click();
      expect(close).toHaveBeenCalledWith(true);
    });

    it('closes with null on Cancel', async () => {
      const { el, close } = await setup({ title: 'Sure?' });
      buttons(el).cancel.click();
      expect(close).toHaveBeenCalledWith(null);
    });

    it('omits the message paragraph when no message is given', async () => {
      const { el } = await setup({ title: 'Sure?' });
      expect(el.querySelector('.dialog-header p')).toBeNull();
    });

    it('uses an info icon by default, a warning triangle for danger, and a custom icon when given', async () => {
      let { el } = await setup({ title: 'A' });
      expect(el.querySelector('.dialog-icon i')?.className).toContain('bi-info-circle');
      expect(el.querySelector('.app-dialog')?.classList.contains('danger')).toBe(false);

      TestBed.resetTestingModule();
      ({ el } = await setup({ title: 'B', variant: 'danger' }));
      expect(el.querySelector('.dialog-icon i')?.className).toContain('bi-exclamation-triangle');
      expect(el.querySelector('.app-dialog')?.classList.contains('danger')).toBe(true);

      TestBed.resetTestingModule();
      ({ el } = await setup({ title: 'C', variant: 'warning', icon: 'bi-trash' }));
      expect(el.querySelector('.dialog-icon i')?.className).toContain('bi-trash');
      expect(el.querySelector('.app-dialog')?.classList.contains('warning')).toBe(true);
    });

    it('uses caller confirm/cancel texts over the defaults', async () => {
      const { el } = await setup({ title: 'A', confirmText: 'Delete', cancelText: 'Keep' });
      expect(buttons(el).confirm.textContent?.trim()).toBe('Delete');
      expect(buttons(el).cancel.textContent?.trim()).toBe('Keep');
    });

    it('uses HQ_APP_DIALOG_TEXTS when provided', async () => {
      const texts: HqAppDialogTexts = {
        ...HQ_APP_DIALOG_DEFAULT_TEXTS,
        cancel: () => 'Annuler',
        confirm: () => 'Valider',
        working: () => 'En cours',
        required: (label) => `${label} requis`,
      };
      const { el } = await setup({ title: 'A', fields: [{ key: 'n', label: 'Nom', required: true }] }, texts);
      expect(buttons(el).cancel.textContent?.trim()).toBe('Annuler');
      expect(buttons(el).confirm.textContent?.trim()).toBe('Valider');
      expect(el.querySelector('.field-error')?.textContent).toBe('Nom requis');
    });
  });

  describe('loading state', () => {
    it('shows the spinner and working text, disables both buttons and ignores submit', async () => {
      const { el, component, close } = await setup({ title: 'A', loading: true });
      const { cancel, confirm } = buttons(el);
      expect(el.querySelector('.spinner-border')).not.toBeNull();
      expect(confirm.textContent?.trim()).toBe('Working...');
      expect(confirm.disabled).toBe(true);
      expect(cancel.disabled).toBe(true);
      expect(component.canSubmit).toBe(false);
      component.submit();
      expect(close).not.toHaveBeenCalled();
    });

    it('prefers the caller loading text', async () => {
      const { el } = await setup({ title: 'A', loading: true, loadingText: 'Saving' });
      expect(buttons(el).confirm.textContent?.trim()).toBe('Saving');
    });
  });

  describe('text / number / textarea fields', () => {
    it('collects text input and textarea values and closes with them', async () => {
      const { el, fixture, close } = await setup({
        title: 'Form',
        fields: [
          { key: 'name', label: 'Name', placeholder: 'Your name', helperText: 'Full name' },
          { key: 'notes', label: 'Notes', type: 'textarea', placeholder: 'Notes here', hint: 'Optional' },
        ],
      });
      const input = el.querySelector('input') as HTMLInputElement;
      const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
      expect(input.type).toBe('text');
      expect(input.placeholder).toBe('Your name');
      expect(textarea.placeholder).toBe('Notes here');
      const helpers = Array.from(el.querySelectorAll('.field-helper')).map((h) => h.textContent);
      expect(helpers).toEqual(['Full name', 'Optional']);

      await typeInto(fixture, input, 'Asha');
      await typeInto(fixture, textarea, 'hello');
      buttons(el).confirm.click();
      expect(close).toHaveBeenCalledWith({ name: 'Asha', notes: 'hello' });
    });

    it('marks a required field with *, shows its error, blocks submit, then allows it once filled', async () => {
      const { el, fixture, component, close } = await setup({
        title: 'Form',
        fields: [{ key: 'name', label: 'Name', required: true, helperText: 'hidden while invalid' }],
      });
      expect(el.querySelector('label em')?.textContent).toBe('*');
      expect(el.querySelector('label')?.classList.contains('invalid')).toBe(true);
      expect(el.querySelector('.field-error')?.textContent).toBe('Name is required');
      expect(el.querySelector('.field-helper')).toBeNull();
      expect(buttons(el).confirm.disabled).toBe(true);
      component.submit();
      expect(close).not.toHaveBeenCalled();

      await typeInto(fixture, el.querySelector('input') as HTMLInputElement, '   ');
      expect(el.querySelector('.field-error')?.textContent).toBe('Name is required');

      await typeInto(fixture, el.querySelector('input') as HTMLInputElement, 'Ravi');
      expect(el.querySelector('.field-error')).toBeNull();
      expect(el.querySelector('.field-helper')?.textContent).toBe('hidden while invalid');
      expect(buttons(el).confirm.disabled).toBe(false);
    });

    it('uses the field errorText for a missing required value', async () => {
      const { el } = await setup({ title: 'F', fields: [{ key: 'a', label: 'A', required: true, errorText: 'Fill A' }] });
      expect(el.querySelector('.field-error')?.textContent).toBe('Fill A');
    });

    it('validates number fields: not a number, below min, above max, in range', async () => {
      const field: HqAppDialogField = { key: 'qty', label: 'Qty', type: 'number', min: 1, max: 10 };
      const { el, fixture, component } = await setup({ title: 'F', fields: [field] });
      const input = el.querySelector('input') as HTMLInputElement;
      expect(input.type).toBe('number');
      expect(input.getAttribute('min')).toBe('1');
      expect(input.getAttribute('max')).toBe('10');
      expect(component.getFieldError(field)).toBe('');

      component.values['qty'] = 'abc';
      expect(component.getFieldError(field)).toBe('Qty must be a valid number');
      component.values['qty'] = '0';
      expect(component.getFieldError(field)).toBe('Qty must be at least 1');
      component.values['qty'] = '11';
      expect(component.getFieldError(field)).toBe('Qty must be 10 or less');
      component.values['qty'] = '5';
      expect(component.getFieldError(field)).toBe('');

      await typeInto(fixture, input, '42');
      expect(el.querySelector('.field-error')?.textContent).toBe('Qty must be 10 or less');
    });

    it('accepts any finite number when no min or max is set', async () => {
      const field: HqAppDialogField = { key: 'n', label: 'N', type: 'number' };
      const { component } = await setup({ title: 'F', fields: [field] });
      component.values['n'] = '-99999';
      expect(component.getFieldError(field)).toBe('');
    });

    it('uses custom min/max/invalidNumber texts from HQ_APP_DIALOG_TEXTS', async () => {
      const texts: HqAppDialogTexts = {
        ...HQ_APP_DIALOG_DEFAULT_TEXTS,
        invalidNumber: (l) => `bad ${l}`,
        min: (l, m) => `${l}>=${m}`,
        max: (l, m) => `${l}<=${m}`,
      };
      const field: HqAppDialogField = { key: 'n', label: 'N', type: 'number', min: 2, max: 3 };
      const { component } = await setup({ title: 'F', fields: [field] }, texts);
      component.values['n'] = 'x';
      expect(component.getFieldError(field)).toBe('bad N');
      component.values['n'] = '1';
      expect(component.getFieldError(field)).toBe('N>=2');
      component.values['n'] = '4';
      expect(component.getFieldError(field)).toBe('N<=3');
    });

    it('coerces initial values: arrays join, booleans become text, missing becomes empty', async () => {
      const { component } = await setup({
        title: 'F',
        fields: [
          { key: 'a', label: 'A', value: ['x', 'y'] },
          { key: 'b', label: 'B', value: true },
          { key: 'c', label: 'C', value: false },
          { key: 'd', label: 'D' },
          { key: 'e', label: 'E', value: 'kept' },
        ],
      });
      expect(component.values).toEqual({ a: 'x, y', b: 'true', c: 'false', d: '', e: 'kept' });
    });

    it('disables every control while loading', async () => {
      const { el, fixture } = await setup({
        title: 'F',
        loading: true,
        fields: [
          { key: 'a', label: 'A' },
          { key: 'b', label: 'B', type: 'textarea' },
          { key: 'c', label: 'C', type: 'select', options: [{ value: '1', label: 'One' }] },
        ],
      });
      await fixture.whenStable();
      fixture.detectChanges();
      expect((el.querySelector('input') as HTMLInputElement).disabled).toBe(true);
      expect((el.querySelector('textarea') as HTMLTextAreaElement).disabled).toBe(true);
      expect((el.querySelector('select') as HTMLSelectElement).disabled).toBe(true);
    });
  });

  describe('select field', () => {
    it('renders the placeholder option plus options and returns the chosen value', async () => {
      const { el, fixture, close } = await setup({
        title: 'F',
        fields: [{
          key: 'city', label: 'City', type: 'select', required: true, placeholderOption: 'Choose a city',
          options: [{ value: 'blr', label: 'Bengaluru' }, { value: 'hyd', label: 'Hyderabad' }],
        }],
      });
      const select = el.querySelector('select') as HTMLSelectElement;
      const options = Array.from(select.options).map((o) => o.textContent?.trim());
      expect(options).toEqual(['Choose a city', 'Bengaluru', 'Hyderabad']);
      expect(select.options[0].disabled).toBe(true);
      expect(buttons(el).confirm.disabled).toBe(true);

      select.value = select.options[2].value;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(buttons(el).confirm.disabled).toBe(false);
      buttons(el).confirm.click();
      expect(close).toHaveBeenCalledWith({ city: 'hyd' });
    });

    it('renders an empty placeholder option when none is given', async () => {
      const { el } = await setup({ title: 'F', fields: [{ key: 's', label: 'S', type: 'select', options: [] }] });
      expect((el.querySelector('select') as HTMLSelectElement).options[0].textContent).toBe('');
    });
  });

  describe('checkbox field', () => {
    it('renders a labelled checkbox with helper text, never errors, and returns a boolean', async () => {
      const field: HqAppDialogField = { key: 'active', label: 'Active', type: 'checkbox', required: true, hint: 'Can sign in' };
      const { el, fixture, component, close } = await setup({ title: 'F', fields: [field] });
      expect(el.querySelector('.dialog-field--checkbox mat-checkbox')?.textContent).toContain('Active');
      expect(el.querySelector('.dialog-field--checkbox .field-helper')?.textContent).toBe('Can sign in');
      expect(el.querySelector('label.dialog-field')).toBeNull();
      expect(component.values['active']).toBe(false);
      expect(component.getFieldError(field)).toBe('');

      (el.querySelector('mat-checkbox input') as HTMLInputElement).click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(component.values['active']).toBe(true);
      buttons(el).confirm.click();
      expect(close).toHaveBeenCalledWith({ active: true });
    });

    it('starts checked only for a true initial value and hides the helper when there is none', async () => {
      const { el, component } = await setup({
        title: 'F',
        fields: [
          { key: 'a', label: 'A', type: 'checkbox', value: true },
          { key: 'b', label: 'B', type: 'checkbox', value: 'true' },
        ],
      });
      expect(component.values).toEqual({ a: true, b: false });
      expect(el.querySelector('.dialog-field--checkbox .field-helper')).toBeNull();
    });
  });

  describe('multiselect field', () => {
    const options = [{ value: 'r1', label: 'Role 1' }, { value: 'r2', label: 'Role 2' }];

    it('requires at least one choice when required and returns the chosen array', async () => {
      const field: HqAppDialogField = { key: 'roles', label: 'Roles', type: 'multiselect', required: true, options, placeholder: 'Pick roles' };
      const { el, fixture, component, close } = await setup({ title: 'F', fields: [field] });
      expect(el.querySelector('mat-select.dialog-multiselect')).not.toBeNull();
      expect(el.querySelector('mat-select')?.textContent).toContain('Pick roles');
      expect(component.values['roles']).toEqual([]);
      expect(el.querySelector('.field-error')?.textContent).toBe('Roles is required');
      expect(buttons(el).confirm.disabled).toBe(true);

      component.values['roles'] = ['r2'];
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(el.querySelector('.field-error')).toBeNull();
      buttons(el).confirm.click();
      expect(close).toHaveBeenCalledWith({ roles: ['r2'] });
    });

    it('uses errorText, copies the initial array and treats a non-array value as empty', async () => {
      const initial = ['r1'];
      const fieldA: HqAppDialogField = { key: 'a', label: 'A', type: 'multiselect', options, value: initial };
      const fieldB: HqAppDialogField = { key: 'b', label: 'B', type: 'multiselect', options, value: 'r1', required: true, errorText: 'Pick one', placeholderOption: 'Choose' };
      const { el, component } = await setup({ title: 'F', fields: [fieldA, fieldB] });
      expect(component.values['a']).toEqual(['r1']);
      expect(component.values['a']).not.toBe(initial);
      expect(component.values['b']).toEqual([]);
      expect(component.getFieldError(fieldB)).toBe('Pick one');
      expect(el.querySelectorAll('mat-select')[1].textContent).toContain('Choose');

      component.values['b'] = 'not-an-array';
      expect(component.getFieldError(fieldB)).toBe('Pick one');
      expect(component.getFieldError(fieldA)).toBe('');
      component.values['a'] = [];
      expect(component.getFieldError(fieldA)).toBe('');
    });

    it('renders an empty placeholder when neither placeholder nor placeholderOption is set', async () => {
      const { el } = await setup({ title: 'F', fields: [{ key: 'a', label: 'A', type: 'multiselect', options }] });
      expect(el.querySelector('mat-select')?.textContent?.trim()).toBe('');
    });
  });

  describe('helpers', () => {
    it('isInputField is true for text (default) and number only', async () => {
      const { component } = await setup({ title: 'F' });
      expect(component.isInputField({ key: 'a', label: 'A' })).toBe(true);
      expect(component.isInputField({ key: 'a', label: 'A', type: 'number' })).toBe(true);
      expect(component.isInputField({ key: 'a', label: 'A', type: 'textarea' })).toBe(false);
      expect(component.isInputField({ key: 'a', label: 'A', type: 'select' })).toBe(false);
    });

    it('fieldHint prefers helperText, then hint, then empty', async () => {
      const { component } = await setup({ title: 'F' });
      expect(component.fieldHint({ key: 'a', label: 'A', helperText: 'h1', hint: 'h2' })).toBe('h1');
      expect(component.fieldHint({ key: 'a', label: 'A', hint: 'h2' })).toBe('h2');
      expect(component.fieldHint({ key: 'a', label: 'A' })).toBe('');
    });

    it('treats a field with no stored value as empty when validating', async () => {
      const { component } = await setup({ title: 'F' });
      expect(component.getFieldError({ key: 'unknown', label: 'U', required: true })).toBe('U is required');
      expect(component.getFieldError({ key: 'unknown', label: 'U' })).toBe('');
    });

    it('hasFields is false for an empty fields array, so submit closes with true', async () => {
      const { component, close } = await setup({ title: 'F', fields: [] });
      expect(component.hasFields).toBe(false);
      component.submit();
      expect(close).toHaveBeenCalledWith(true);
    });
  });
});
