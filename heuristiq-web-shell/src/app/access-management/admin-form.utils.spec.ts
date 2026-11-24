import { AdminFormField } from './admin-form.types';
import { vi } from 'vitest';
import { buildPayload, copyTextToClipboard, fieldError, initialFieldValue, invalidPhoneField } from './admin-form.utils';

describe('admin form phone fields', () => {
  const phoneField: AdminFormField = { key: 'phoneNumber', label: 'x', type: 'phone' };

  it('prefills an international number from {countryCode, number}', () => {
    expect(initialFieldValue(phoneField, { phoneNumber: { countryCode: '+91', number: '9876543210' } })).toBe('+919876543210');
  });

  it('prefills an old bare number unchanged so the input reads it in the default country', () => {
    expect(initialFieldValue(phoneField, { phoneNumber: { countryCode: null, number: '9876543210' } })).toBe('9876543210');
    expect(initialFieldValue(phoneField, { phoneNumber: '9876543210' })).toBe('9876543210');
  });

  it('accepts empty, bare Indian and international numbers, and rejects malformed ones', () => {
    expect(invalidPhoneField([phoneField], { phoneNumber: '' }, 'IN')).toBeNull();
    expect(invalidPhoneField([phoneField], { phoneNumber: '9876543210' }, 'IN')).toBeNull();
    expect(invalidPhoneField([phoneField], { phoneNumber: '+14155552671' }, 'IN')).toBeNull();
    expect(invalidPhoneField([phoneField], { phoneNumber: '12345' }, 'IN')).toBe(phoneField);
  });

  it('sends the trimmed control value', () => {
    expect(buildPayload([phoneField], { phoneNumber: ' +919876543210 ' })).toEqual({ phoneNumber: '+919876543210' });
  });
});

describe('admin form field errors', () => {
  const number: AdminFormField = { key: 'n', label: 'x', type: 'number', min: 1, max: 20, minMessage: 'too.low', maxMessage: 'too.high' };

  it('says nothing about an empty value', () => {
    expect(fieldError(number, { n: '' })).toBeNull();
    expect(fieldError(number, { n: '  ' })).toBeNull();
  });

  it('checks the minimum and the maximum of a number, boundaries included', () => {
    expect(fieldError(number, { n: '0' })).toBe('too.low');
    expect(fieldError(number, { n: '1' })).toBeNull();
    expect(fieldError(number, { n: '20' })).toBeNull();
    expect(fieldError(number, { n: '21' })).toBe('too.high');
    expect(fieldError(number, { n: '-1' })).toBe('too.low');
  });

  it('falls back to the shared messages when a field names none', () => {
    const bare: AdminFormField = { key: 'n', label: 'x', type: 'number', min: 1, max: 5 };
    expect(fieldError(bare, { n: '0' })).toBe('shared.adminCrud.belowMinimum');
    expect(fieldError(bare, { n: '6' })).toBe('shared.adminCrud.aboveMaximum');
  });

  it('refuses a number that is not a number', () => {
    expect(fieldError(number, { n: 'abc' })).toBe('shared.adminCrud.invalidValue');
  });

  it('runs a field rule with all the form values, only once the field has a value', () => {
    const ruled: AdminFormField = { key: 'a', label: 'x', type: 'text', rule: (values) => (values['b'] === 'bad' ? 'rule.failed' : null) };
    expect(fieldError(ruled, { a: '', b: 'bad' })).toBeNull();
    expect(fieldError(ruled, { a: 'x', b: 'ok' })).toBeNull();
    expect(fieldError(ruled, { a: 'x', b: 'bad' })).toBe('rule.failed');
  });

  it('checks the pattern before the rule', () => {
    const rule = vi.fn(() => 'rule.failed');
    const both: AdminFormField = { key: 'a', label: 'x', type: 'text', pattern: /^\d+$/, patternMessage: 'pattern.failed', rule };
    expect(fieldError(both, { a: 'x' })).toBe('pattern.failed');
    expect(rule).not.toHaveBeenCalled();
  });

  it('keeps a password out of the display flattening and sends it as typed', () => {
    const password: AdminFormField = { key: 'password', label: 'x', type: 'password' };
    expect(buildPayload([password], { password: 'Abcdef12' })).toEqual({ password: 'Abcdef12' });
  });
});

describe('copyTextToClipboard', () => {
  const stubClipboard = (value: unknown) => Object.defineProperty(navigator, 'clipboard', { value, configurable: true });
  afterEach(() => stubClipboard(undefined));

  it('uses the Clipboard API when it is there', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard({ writeText });

    expect(await copyTextToClipboard('ABC')).toBe(true);
    expect(writeText).toHaveBeenCalledWith('ABC');
  });

  it('falls back to the legacy copy command when the API is missing or refuses', async () => {
    const exec = vi.fn(() => true);
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });

    stubClipboard(undefined);
    expect(await copyTextToClipboard('ABC')).toBe(true);
    stubClipboard({ writeText: vi.fn().mockRejectedValue(new Error('denied')) });
    expect(await copyTextToClipboard('ABC')).toBe(true);
    expect(exec).toHaveBeenCalledWith('copy');
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('answers false when the legacy command fails or throws', async () => {
    stubClipboard(undefined);
    Object.defineProperty(document, 'execCommand', { value: vi.fn(() => false), configurable: true });
    expect(await copyTextToClipboard('ABC')).toBe(false);

    Object.defineProperty(document, 'execCommand', { value: vi.fn(() => { throw new Error('nope'); }), configurable: true });
    expect(await copyTextToClipboard('ABC')).toBe(false);
  });
});
