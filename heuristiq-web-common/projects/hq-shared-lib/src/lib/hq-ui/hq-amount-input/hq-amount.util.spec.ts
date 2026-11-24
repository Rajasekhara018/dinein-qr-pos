import {
  hqAmountCode,
  hqAmountOrNull,
  hqAmountValue,
  hqToAmount,
  hqCurrencyDecimals,
  hqCurrencySymbol,
  hqEditableAmount,
  hqFormatAmount,
  hqNormalizeCurrency,
  hqParseAmount,
  hqRoundAmount,
  hqSanitizeAmountTyping,
} from './hq-amount.util';

describe('hq amount helpers', () => {
  it('reads a currency code as three upper-case letters, or nothing', () => {
    expect(hqNormalizeCurrency(' inr ')).toBe('INR');
    expect(hqNormalizeCurrency('RUPEES')).toBe('');
    expect(hqNormalizeCurrency(null)).toBe('');
  });

  it('knows each currency\'s decimal places and falls back to two', () => {
    expect(hqCurrencyDecimals('INR')).toBe(2);
    expect(hqCurrencyDecimals('USD')).toBe(2);
    expect(hqCurrencyDecimals('JPY')).toBe(0);
    expect(hqCurrencyDecimals('KWD')).toBe(3);
    expect(hqCurrencyDecimals('')).toBe(2);
    expect(hqCurrencyDecimals('ZZZ')).toBe(2);
  });

  it('gives the currency symbol, or the code when there is none', () => {
    expect(hqCurrencySymbol('INR', 'en-IN')).toBe('₹');
    expect(hqCurrencySymbol('USD', 'en-US')).toBe('$');
    expect(hqCurrencySymbol('', 'en')).toBe('');
  });

  it('rounds half up in whole minor units', () => {
    expect(hqRoundAmount(1.005, 2)).toBe(1.01);
    expect(hqRoundAmount(2.5, 0)).toBe(3);
    expect(hqRoundAmount(-1.005, 2)).toBe(-1.01);
    expect(hqRoundAmount(10.12349, 3)).toBe(10.123);
  });

  it('reads typed or pasted text, with grouping and either decimal mark', () => {
    expect(hqParseAmount('1,00,000.50')).toBe(100000.5);
    expect(hqParseAmount('1.234,56')).toBe(1234.56);
    expect(hqParseAmount('1 234.5')).toBe(1234.5);
    expect(hqParseAmount('12,5')).toBe(12.5);
    expect(hqParseAmount('12,500')).toBe(12.5);
    expect(hqParseAmount('1,234,567')).toBe(1234567);
    expect(hqParseAmount('.5')).toBe(0.5);
    expect(hqParseAmount('-7')).toBe(-7);
    expect(hqParseAmount('')).toBeNull();
    expect(hqParseAmount('   ')).toBeNull();
    expect(hqParseAmount('12abc')).toBeNaN();
  });

  it('keeps only digits and one decimal point, cut to the currency\'s decimals while typing', () => {
    expect(hqSanitizeAmountTyping('12a3.4567', 2)).toBe('123.45');
    expect(hqSanitizeAmountTyping('1.2.3', 2)).toBe('1.23');
    expect(hqSanitizeAmountTyping('99.5', 0)).toBe('99');
    expect(hqSanitizeAmountTyping('10,75', 2)).toBe('10.75');
    expect(hqSanitizeAmountTyping('-5', 2)).toBe('5');
    expect(hqSanitizeAmountTyping('-5', 2, true)).toBe('-5');
  });

  it('formats with grouping and exactly the currency\'s decimal places', () => {
    expect(hqFormatAmount(100000, 'INR', 'en-IN')).toBe('1,00,000.00');
    expect(hqFormatAmount(1234.5, 'USD', 'en-US')).toBe('1,234.50');
    expect(hqFormatAmount(1234.5, 'JPY', 'en-US')).toBe('1,235');
    expect(hqFormatAmount(1.2, 'KWD', 'en-US')).toBe('1.200');
    expect(hqFormatAmount(5, 'INR', 'en-IN', 3)).toBe('5.000');
    expect(hqFormatAmount(null, 'INR', 'en-IN')).toBe('');
    expect(hqFormatAmount(Number.NaN, 'INR', 'en-IN')).toBe('');
  });

  it('shows the plain number to edit, with the currency\'s decimals', () => {
    expect(hqEditableAmount(1500, 2)).toBe('1500.00');
    expect(hqEditableAmount(1500.456, 2)).toBe('1500.46');
    expect(hqEditableAmount(null, 2)).toBe('');
  });

  it('reads an amount value from the object, a legacy number or legacy text', () => {
    expect(hqAmountValue({ code: 'INR', value: 150 })).toBe(150);
    expect(hqAmountValue(42.5)).toBe(42.5);
    expect(hqAmountValue('10.00')).toBe(10);
    expect(hqAmountValue({ code: 'INR', value: null })).toBeNull();
    expect(hqAmountValue('')).toBeNull();
    expect(hqAmountValue('abc')).toBeNull();
    expect(hqAmountValue(null)).toBeNull();
    expect(hqAmountValue(undefined)).toBeNull();
    expect(hqAmountValue(Number.NaN)).toBeNull();
  });

  it('takes the own currency of an amount, else the fallback', () => {
    expect(hqAmountCode({ code: 'usd', value: 1 }, 'INR')).toBe('USD');
    expect(hqAmountCode({ code: '', value: 1 }, 'inr')).toBe('INR');
    expect(hqAmountCode(5, 'AED')).toBe('AED');
    expect(hqAmountCode(5)).toBe('');
  });

  it('normalises any shape into { code, value } or null', () => {
    expect(hqToAmount({ code: 'INR', value: 150 })).toEqual({ code: 'INR', value: 150 });
    expect(hqToAmount('10.00', 'INR')).toEqual({ code: 'INR', value: 10 });
    expect(hqToAmount(7, 'jpy')).toEqual({ code: 'JPY', value: 7 });
    expect(hqToAmount({ code: 'INR', value: null }, 'INR')).toBeNull();
    expect(hqToAmount(null, 'INR')).toBeNull();
  });

  it('builds a request amount only when there is a value', () => {
    expect(hqAmountOrNull(99.5, 'inr')).toEqual({ code: 'INR', value: 99.5 });
    expect(hqAmountOrNull(null, 'INR')).toBeNull();
    expect(hqAmountOrNull(undefined, 'INR')).toBeNull();
    expect(hqAmountOrNull(Number.NaN, 'INR')).toBeNull();
  });
});
