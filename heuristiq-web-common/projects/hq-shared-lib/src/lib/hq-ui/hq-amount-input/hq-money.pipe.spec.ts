import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { HQ_AMOUNT_DEFAULT_CURRENCY } from './hq-amount-input.constants';
import { hqAmount, hqMoneyText } from './hq-amount.util';
import { HqMoneyPipe } from './hq-money.pipe';

registerLocaleData(localeEnIn);

describe('HqMoneyPipe', () => {
  function pipe(providers: unknown[] = []): HqMoneyPipe {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: 'en-IN' }, HqMoneyPipe, ...(providers as never[])] });
    return TestBed.inject(HqMoneyPipe);
  }

  it('shows a { code, value } amount with its symbol and the currency\'s decimals', () => {
    const p = pipe();
    expect(p.transform({ code: 'INR', value: 100000 })).toBe('₹1,00,000.00');
    expect(p.transform({ code: 'USD', value: 1234.5 })).toBe('$1,234.50');
    expect(p.transform({ code: 'JPY', value: 1500.6 })).toBe('¥1,501');
  });

  it('shows a bare number in the given currency, or the app\'s, and reads decimal text', () => {
    expect(pipe().transform(250, 'INR')).toBe('₹250.00');
    expect(pipe([{ provide: HQ_AMOUNT_DEFAULT_CURRENCY, useValue: 'USD' }]).transform(250)).toBe('$250.00');
    expect(pipe().transform('10000.50', 'INR')).toBe('₹10,000.50');
    expect(pipe().transform({ code: '', value: 5 }, 'INR')).toBe('₹5.00');
  });

  it('is empty, or the placeholder, when there is no value', () => {
    const p = pipe();
    expect(p.transform(null)).toBe('');
    expect(p.transform({ code: 'INR', value: null }, null, '—')).toBe('—');
    expect(p.transform('', 'INR', '—')).toBe('—');
    expect(p.transform('abc', 'INR', '—')).toBe('—');
  });
});

describe('hq money helpers', () => {
  it('writes an amount with its symbol, or just the number for an unknown currency', () => {
    expect(hqMoneyText(1.2, 'KWD', 'en-US')).toContain('1.200');
    expect(hqMoneyText(5, '', 'en-US')).toBe('5.00');
    expect(hqMoneyText(null, 'INR', 'en-US')).toBe('');
  });

  it('builds a { code, value } amount', () => {
    expect(hqAmount(10, 'inr')).toEqual({ code: 'INR', value: 10 });
    expect(hqAmount(undefined, 'USD')).toEqual({ code: 'USD', value: null });
    expect(hqAmount(Number.NaN, 'USD')).toEqual({ code: 'USD', value: null });
  });
});
