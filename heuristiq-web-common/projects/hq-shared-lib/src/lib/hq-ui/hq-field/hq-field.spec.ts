import { ChangeDetectorRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqField } from './hq-field';
import { HQ_FIELD_TEXTS } from './hq-field.texts';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `
    <hq-field [formControl]="control" [type]="type" [label]="label" [required]="required" [error]="error" [hint]="hint"
               [maxLength]="maxLength" [showLabel]="showLabel" [icon]="icon" [readonly]="readonly" [min]="min" [max]="max" [invalid]="invalid"></hq-field>
  `,
})
class HostComponent {
  control = new FormControl<string | number | null>('');
  type: string = 'text';
  label = 'Username';
  required = false;
  error: string | null = null;
  hint = '';
  maxLength: number | null = null;
  showLabel = true;
  icon = '';
  readonly = false;
  min: string | null = null;
  max: string | null = null;
  invalid = false;
}

function setup(texts?: unknown) {
  TestBed.configureTestingModule({
    imports: [HostComponent],
    providers: texts ? [{ provide: HQ_FIELD_TEXTS, useValue: texts }] : [],
  });
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    host: fixture.componentInstance,
    el,
    input: () => el.querySelector('input.hq-field__control') as HTMLInputElement,
    textarea: () => el.querySelector('textarea.hq-field__control') as HTMLTextAreaElement,
    /** Zoneless change detection only refreshes views marked dirty, so state changed from the test is marked first. */
    update: () => {
      fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
      fixture.detectChanges();
    },
  };
}

describe('HqField', () => {
  it('draws the label with the required star and points the label at the control', () => {
    const t = setup();
    t.host.required = true;
    t.update();

    const label = t.el.querySelector('label.hq-field__label') as HTMLLabelElement;
    expect(label.textContent).toContain('Username');
    expect(label.querySelector('.hq-field__req')).not.toBeNull();
    expect(label.getAttribute('for')).toBe(t.input().id);
    expect(t.input().getAttribute('aria-required')).toBe('true');
  });

  it('draws only the control when the label is switched off', () => {
    const t = setup();
    t.host.showLabel = false;
    t.update();

    expect(t.el.querySelector('label')).toBeNull();
    expect(t.input()).not.toBeNull();
  });

  it('draws each type with the matching input type and keyboard', () => {
    const t = setup();
    for (const [type, inputType, mode] of [
      ['text', 'text', null],
      ['email', 'email', 'email'],
      ['tel', 'tel', 'tel'],
      ['number', 'number', 'decimal'],
    ] as const) {
      t.host.type = type;
      t.update();
      expect(t.input().getAttribute('type'), type).toBe(inputType);
      expect(t.input().getAttribute('inputmode'), type).toBe(mode);
    }
  });

  it('draws a textarea for the textarea type, and plain text for a type it does not know', () => {
    const t = setup();
    t.host.type = 'textarea';
    t.update();
    expect(t.textarea()).not.toBeNull();
    expect(t.el.querySelector('input.hq-field__control')).toBeNull();

    t.host.type = 'checkbox';
    t.update();
    expect(t.input().getAttribute('type')).toBe('text');
  });

  it('shows the form value, and passes what the user types back to the form', () => {
    const t = setup();
    t.host.control.setValue('asha');
    t.update();
    expect(t.input().value).toBe('asha');

    t.input().value = 'asha.rao';
    t.input().dispatchEvent(new Event('input'));

    expect(t.host.control.value).toBe('asha.rao');
  });

  it('gives a number field a number, and null for an empty one', () => {
    const t = setup();
    t.host.type = 'number';
    t.update();

    t.input().value = '42';
    t.input().dispatchEvent(new Event('input'));
    expect(t.host.control.value).toBe(42);

    t.input().value = '';
    t.input().dispatchEvent(new Event('input'));
    expect(t.host.control.value).toBeNull();
  });

  it('marks the form control touched when the field loses focus', () => {
    const t = setup();
    expect(t.host.control.touched).toBe(false);

    t.input().dispatchEvent(new Event('blur'));

    expect(t.host.control.touched).toBe(true);
  });

  it('disables the control when the form control is disabled', () => {
    const t = setup();
    t.host.control.disable();
    t.update();

    expect(t.input().disabled).toBe(true);
    expect(t.el.querySelector('.hq-field')?.classList.contains('is-disabled')).toBe(true);
  });

  it('shows an error as an alert, turns the control invalid and describes it for assistive technology', () => {
    const t = setup();
    t.host.error = 'Username is required.';
    t.host.hint = 'Letters and digits.';
    t.update();

    const error = t.el.querySelector('.hq-field__error') as HTMLElement;
    expect(error.textContent).toContain('Username is required.');
    expect(error.getAttribute('role')).toBe('alert');
    expect(t.input().getAttribute('aria-invalid')).toBe('true');
    expect(t.input().getAttribute('aria-describedby')).toBe(`${error.id} ${(t.el.querySelector('.hq-field__hint') as HTMLElement).id}`);
    expect(t.el.querySelector('.hq-field')?.classList.contains('has-error')).toBe(true);
  });

  it('shows no error, no invalid state and no description when there is nothing to say', () => {
    const t = setup();

    expect(t.el.querySelector('.hq-field__error')).toBeNull();
    expect(t.input().getAttribute('aria-invalid')).toBeNull();
    expect(t.input().getAttribute('aria-describedby')).toBeNull();
  });

  it('counts characters against the limit for a text field, and limits the input', () => {
    const t = setup();
    t.host.maxLength = 10;
    t.host.control.setValue('abc');
    t.update();

    expect((t.el.querySelector('.hq-field__counter') as HTMLElement).textContent).toBe('3 / 10');
    expect(t.input().getAttribute('maxlength')).toBe('10');
  });

  it('uses the words the app provides for the counter', () => {
    const t = setup({ showPassword: () => 'Show', hidePassword: () => 'Hide', counter: (n: number, max: number) => `${n} of ${max}` });
    t.host.maxLength = 8;
    t.host.control.setValue('ab');
    t.update();

    expect((t.el.querySelector('.hq-field__counter') as HTMLElement).textContent).toBe('2 of 8');
  });

  it('lets the user show and hide a password, with the button named for what it does', () => {
    const t = setup();
    t.host.type = 'password';
    t.update();
    const toggle = (): HTMLButtonElement => t.el.querySelector('.hq-field__toggle') as HTMLButtonElement;

    expect(t.input().getAttribute('type')).toBe('password');
    expect(toggle().getAttribute('aria-label')).toBe('Show password');
    expect(t.input().getAttribute('autocomplete')).toBeNull();

    toggle().click();
    t.update();

    expect(t.input().getAttribute('type')).toBe('text');
    expect(toggle().getAttribute('aria-label')).toBe('Hide password');
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
  });

  it('offers no show-password button on other types, or on a read-only or disabled password', () => {
    const t = setup();
    expect(t.el.querySelector('.hq-field__toggle')).toBeNull();

    t.host.type = 'password';
    t.host.readonly = true;
    t.update();
    expect(t.el.querySelector('.hq-field__toggle')).toBeNull();
  });

  it('draws an icon at the start when one is given', () => {
    const t = setup();
    t.host.icon = 'bi bi-search';
    t.update();

    expect(t.el.querySelector('.hq-field__icon')?.classList.contains('bi-search')).toBe(true);
  });

  it('shows a plain value given without a form control, for a read-only display', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('value', 'Pasupu Kumkuma');
    fixture.componentRef.setInput('readonly', true);
    fixture.componentRef.setInput('showLabel', false);
    fixture.detectChanges();

    const input = (fixture.nativeElement as HTMLElement).querySelector('input.hq-field__control') as HTMLInputElement;
    expect(input.value).toBe('Pasupu Kumkuma');
    expect(input.readOnly).toBe(true);
  });

  it('draws the common date picker, not the browser one, for the types date, time and datetime-local', () => {
    const t = setup();
    for (const [type, tag] of [['date', 'date'], ['time', 'time'], ['datetime-local', 'datetime']] as const) {
      t.host.type = type;
      t.update();
      expect(t.el.querySelector('input.hq-field__control'), type).toBeNull();
      expect(t.el.querySelector('hq-date-input input.hq-dt__control'), type).not.toBeNull();
      expect((t.el.querySelector('hq-date-input input.hq-dt__control') as HTMLInputElement).getAttribute('placeholder'), tag).toBeTruthy();
    }
  });

  it('passes a picked date back to the form as the same text a native date input gives', () => {
    const t = setup();
    t.host.type = 'date';
    t.update();
    const box = t.el.querySelector('hq-date-input input.hq-dt__control') as HTMLInputElement;

    box.value = '07-03-2026';
    box.dispatchEvent(new Event('input'));
    t.update();

    expect(t.host.control.value).toBe('2026-03-07');
  });

  it('marks the field invalid without a message when only the flag is set', () => {
    const t = setup();
    t.host.invalid = true;
    t.update();

    expect(t.el.querySelector('.hq-field')?.classList.contains('has-error')).toBe(true);
    expect(t.el.querySelector('.hq-field__error')).toBeNull();
    expect(t.input().getAttribute('aria-invalid')).toBe('true');
  });

  it('applies the limits of a number field the way a native number input does', () => {
    const t = setup();
    t.host.type = 'number';
    t.host.min = '1';
    t.host.max = '10';
    t.update();

    t.input().value = '0';
    t.input().dispatchEvent(new Event('input'));
    expect(t.host.control.errors?.['min']).toEqual({ min: 1, actual: 0 });

    t.input().value = '5';
    t.input().dispatchEvent(new Event('input'));
    expect(t.host.control.errors).toBeNull();

    t.input().value = '11';
    t.input().dispatchEvent(new Event('input'));
    expect(t.host.control.errors?.['max']).toEqual({ max: 10, actual: 11 });
  });

  it('applies no number limits to a field that is not a number field', () => {
    const t = setup();
    t.host.min = '5';
    t.update();

    t.input().value = '1';
    t.input().dispatchEvent(new Event('input'));

    expect(t.host.control.errors).toBeNull();
  });

  it('offers no show-password button when the screen has its own', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('type', 'password');
    fixture.componentRef.setInput('passwordToggle', false);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.hq-field__toggle')).toBeNull();
  });

  it('points the control at a datalist of suggestions', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('list', 'cities');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('input.hq-field__control')?.getAttribute('list')).toBe('cities');
  });

  it('adds the ids a screen gives to the description the field already has', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('hint', 'Letters only');
    fixture.componentRef.setInput('ariaDescribedBy', 'page-help');
    fixture.detectChanges();

    const describedBy = (fixture.nativeElement as HTMLElement).querySelector('input.hq-field__control')?.getAttribute('aria-describedby');
    expect(describedBy).toContain('page-help');
    expect(describedBy).toContain('-hint');
  });

  it('can be disabled as a plain input, without a form', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input.hq-field__control')?.disabled).toBe(true);
  });

  it('shows what is typed in the transform a screen asks for, for example upper case', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqField);
    fixture.componentRef.setInput('textTransform', 'uppercase');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input.hq-field__control')?.style.textTransform).toBe('uppercase');
  });
});
