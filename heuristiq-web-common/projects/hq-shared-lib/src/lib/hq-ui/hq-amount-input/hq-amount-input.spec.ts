import { Component, LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HQ_AMOUNT_DEFAULT_CURRENCY } from './hq-amount-input.constants';
import { HqAmountInput } from './hq-amount-input';
import { HQ_AMOUNT_INPUT_TEXTS, HqAmountInputTexts } from './hq-amount-input.texts';
import { HqAmount } from './hq-amount.util';

registerLocaleData(localeEnIn);

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-amount-input [formControl]="ctrl" [isRequired]="required" [currencies]="currencies" [max]="max" label="Fee"></hq-amount-input>`,
})
class FormHost {
  ctrl = new FormControl<HqAmount | null>(null);
  required = false;
  currencies: string[] | null = null;
  max: number | null = null;
}

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-amount-input [formControl]="ctrl" valueMode="number" currency="USD" label="Fee"></hq-amount-input>`,
})
class NumberHost {
  ctrl = new FormControl<number | null>(null);
}

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-amount-input [formControl]="ctrl" valueMode="text" currency="INR" label="Fee"></hq-amount-input>`,
})
class TextHost {
  ctrl = new FormControl<string>('', { nonNullable: true });
}

describe('HqAmountInput', () => {
  let fixture: ComponentFixture<FormHost>;
  let host: FormHost;
  let el: HTMLElement;

  function setUp(providers: unknown[] = []): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [FormHost],
      providers: [{ provide: LOCALE_ID, useValue: 'en-IN' }, ...(providers as never[])],
    });
    fixture = TestBed.createComponent(FormHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  }

  /** The host's fields are changed directly, so it is marked for checking before change detection (zoneless). */
  function refresh(): void {
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  function box(): HTMLInputElement {
    return el.querySelector('[data-testid="amount-box"]') as HTMLInputElement;
  }

  function type(text: string): void {
    const input = box();
    input.dispatchEvent(new Event('focus'));
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function leave(): void {
    box().dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function component(): HqAmountInput {
    return fixture.debugElement.children[0].componentInstance as HqAmountInput;
  }

  beforeEach(() => setUp());

  it('emits the currency code and the value, cut to the currency\'s decimals while typing', () => {
    type('1500.456');

    expect(box().value).toBe('1500.45');
    expect(host.ctrl.value).toEqual({ code: 'INR', value: 1500.45 });
  });

  it('keeps what is being typed when the screen hands the same amount back on every keystroke (no "5.00" after the first digit)', () => {
    // As [ngModel] + (ngModelChange) screens do: each emitted amount comes back as a new object while the box has focus.
    const input = box();
    input.dispatchEvent(new Event('focus'));
    for (const typed of ['5', '50', '500', '5000', '5000.5']) {
      input.value = typed;
      input.dispatchEvent(new Event('input'));
      component().writeValue({ ...(host.ctrl.value as HqAmount) });
      fixture.detectChanges();
      expect(box().value).toBe(typed);
    }
    expect(host.ctrl.value).toEqual({ code: 'INR', value: 5000.5 });
  });

  it('still shows a different amount the screen sets while the box has focus', () => {
    type('5');
    component().writeValue({ code: 'INR', value: 75 });
    fixture.detectChanges();

    expect(box().value).toBe('75.00');
  });

  it('formats the amount with grouping and two decimals when the field is left, and shows the plain number again on focus', () => {
    type('100000');
    leave();
    expect(box().value).toBe('1,00,000.00');

    box().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(box().value).toBe('100000.00');
  });

  it('shows a value set from outside formatted, rounded to the currency\'s decimals, with its symbol', () => {
    host.ctrl.setValue({ code: 'usd', value: 1234.567 });
    fixture.detectChanges();

    expect(box().value).toBe('1,234.57');
    expect(component().value).toEqual({ code: 'USD', value: 1234.57 });
    expect(el.querySelector('[data-testid="amount-symbol"]')?.textContent?.trim()).toBe('$');
  });

  it('uses the currency\'s own decimal places: none for JPY, three for KWD', () => {
    host.ctrl.setValue({ code: 'JPY', value: 1500.6 });
    fixture.detectChanges();
    expect(box().value).toBe('1,501');

    host.ctrl.setValue({ code: 'KWD', value: 1.5 });
    fixture.detectChanges();
    expect(box().value).toBe('1.500');
  });

  it('starts on the app\'s currency when the value names none', () => {
    setUp([{ provide: HQ_AMOUNT_DEFAULT_CURRENCY, useValue: 'aed' }]);
    type('10');
    expect(host.ctrl.value).toEqual({ code: 'AED', value: 10 });
  });

  it('is invalid when required and empty, below zero or above the maximum, and says why once touched', () => {
    host.required = true;
    host.max = 100;
    refresh();
    host.ctrl.updateValueAndValidity();
    expect(host.ctrl.hasError('required')).toBe(true);

    type('250');
    leave();
    expect(host.ctrl.hasError('amountMax')).toBe(true);
    expect(el.querySelector('.amount-input__error')?.textContent).toContain('100.00');

    type('-5');
    expect(box().value).toBe('5');
    expect(host.ctrl.valid).toBe(true);
  });

  it('lets the person choose between currencies, keeping the amount rounded to the new currency', () => {
    host.currencies = ['INR', 'JPY'];
    refresh();
    type('99.75');
    const select = el.querySelector('[data-testid="amount-currency"]') as HTMLSelectElement;
    select.value = 'JPY';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(host.ctrl.value).toEqual({ code: 'JPY', value: 100 });
  });

  it('reads a pasted amount with grouping', () => {
    const input = box();
    const paste = new Event('paste', { cancelable: true }) as ClipboardEvent;
    Object.defineProperty(paste, 'clipboardData', { value: { getData: () => '1,25,000.5' } });
    input.dispatchEvent(paste);
    fixture.detectChanges();

    expect(paste.defaultPrevented).toBe(true);
    expect(host.ctrl.value).toEqual({ code: 'INR', value: 125000.5 });
  });

  it('reads and writes a bare number in number mode, taking the currency from the currency input', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [NumberHost], providers: [{ provide: LOCALE_ID, useValue: 'en-IN' }] });
    const f = TestBed.createComponent(NumberHost);
    f.componentInstance.ctrl.setValue(1500.456);
    f.detectChanges();
    const root = f.nativeElement as HTMLElement;
    const input = root.querySelector('[data-testid="amount-box"]') as HTMLInputElement;
    expect(input.value).toBe('1,500.46');
    expect(root.querySelector('[data-testid="amount-symbol"]')?.textContent?.trim()).toBe('$');

    input.dispatchEvent(new Event('focus'));
    input.value = '99.999';
    input.dispatchEvent(new Event('input'));
    expect(f.componentInstance.ctrl.value).toBe(99.99);
    input.value = '';
    input.dispatchEvent(new Event('input'));
    expect(f.componentInstance.ctrl.value).toBeNull();
    input.dispatchEvent(new Event('blur'));

    f.componentInstance.ctrl.setValue('42.5' as never);
    f.detectChanges();
    expect(input.value).toBe('42.50');
  });

  it('reads and writes decimal text in text mode, with the currency’s decimal places', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [TextHost], providers: [{ provide: LOCALE_ID, useValue: 'en-IN' }] });
    const f = TestBed.createComponent(TextHost);
    f.componentInstance.ctrl.setValue('250000');
    f.detectChanges();
    const input = (f.nativeElement as HTMLElement).querySelector('[data-testid="amount-box"]') as HTMLInputElement;
    expect(input.value).toBe('2,50,000.00');

    input.dispatchEvent(new Event('focus'));
    input.value = '12.5';
    input.dispatchEvent(new Event('input'));
    expect(f.componentInstance.ctrl.value).toBe('12.50');
    input.value = '';
    input.dispatchEvent(new Event('input'));
    expect(f.componentInstance.ctrl.value).toBe('');
  });

  it('follows the form\'s disabled state and uses the app\'s words when given', () => {
    host.ctrl.disable();
    fixture.detectChanges();
    expect(box().disabled).toBe(true);

    const texts: HqAmountInputTexts = {
      label: () => 'Montant', placeholder: () => '', required: (l) => `${l} requis`, invalid: () => 'invalide',
      min: (m) => `min ${m}`, max: (m) => `max ${m}`, currencyLabel: () => 'Devise',
    };
    setUp([{ provide: HQ_AMOUNT_INPUT_TEXTS, useValue: texts }]);
    host.required = true;
    refresh();
    leave();
    expect(el.querySelector('.amount-input__error')?.textContent).toContain('Fee requis');
  });
});

describe('HqAmountInput invalid flag', () => {
  it('shows the error state from the invalid input, without the person having touched the field', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fixture = TestBed.createComponent(HqAmountInput);
    fixture.componentRef.setInput('invalid', true);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.amount-input__field')?.classList.contains('has-error')).toBe(true);
  });
});
