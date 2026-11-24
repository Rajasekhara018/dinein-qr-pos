import { describe, expect, it } from 'vitest';
import { passwordLengthOrderError, redirectUriError, redirectUrisError, splitUris } from './access-management.validators';
import { REDIRECT_URI_MAX_COUNT, REDIRECT_URI_MAX_LENGTH, RULE_ERROR_KEYS } from './access-management-rules.constants';

describe('splitUris', () => {
  it('splits on commas, trims and drops blanks', () => {
    expect(splitUris(' https://a.example/cb , ,https://b.example/cb ')).toEqual(['https://a.example/cb', 'https://b.example/cb']);
    expect(splitUris('')).toEqual([]);
    expect(splitUris(undefined)).toEqual([]);
  });
});

describe('redirectUriError', () => {
  it.each([
    'https://app.example.com/callback',
    'https://app.example.com:8443/cb?x=1',
    'http://localhost:4200/cb',
    'http://127.0.0.1/cb',
    'https://localhost/cb',
  ])('accepts %s', (uri) => {
    expect(redirectUriError(uri)).toBeNull();
  });

  it.each([
    'javascript:alert(1)',
    'data:text/html;base64,AAAA',
    'file:///etc/passwd',
    'ftp://example.com/cb',
    '/relative/path',
    'example.com/cb',
    'https://',
    'https://user@example.com/cb',
    'https://user:secret@example.com/cb',
    'https://example.com/cb#fragment',
    'https://example.com/cb#',
    'not a url',
  ])('rejects %s as not a valid absolute http(s) address', (uri) => {
    expect(redirectUriError(uri)).toBe(RULE_ERROR_KEYS.redirectUrisInvalid);
  });

  it('demands https except for localhost and 127.0.0.1', () => {
    expect(redirectUriError('http://app.example.com/cb')).toBe(RULE_ERROR_KEYS.redirectUrisHttps);
    expect(redirectUriError('http://localhost.example.com/cb')).toBe(RULE_ERROR_KEYS.redirectUrisHttps);
    expect(redirectUriError('http://10.0.0.1/cb')).toBe(RULE_ERROR_KEYS.redirectUrisHttps);
  });

  it('allows 255 characters and refuses 256', () => {
    const base = 'https://example.com/';
    expect(redirectUriError(base + 'a'.repeat(REDIRECT_URI_MAX_LENGTH - base.length))).toBeNull();
    expect(redirectUriError(base + 'a'.repeat(REDIRECT_URI_MAX_LENGTH - base.length + 1))).toBe(RULE_ERROR_KEYS.redirectUrisInvalid);
  });
});

describe('redirectUrisError', () => {
  const uris = (count: number) => Array.from({ length: count }, (_, i) => `https://app${i}.example.com/cb`).join(',');

  it('accepts none, one and the most allowed', () => {
    expect(redirectUrisError({ redirectUris: '' })).toBeNull();
    expect(redirectUrisError({ redirectUris: 'https://a.example.com/cb' })).toBeNull();
    expect(redirectUrisError({ redirectUris: uris(REDIRECT_URI_MAX_COUNT) })).toBeNull();
  });

  it('refuses one more than the most allowed', () => {
    expect(redirectUrisError({ redirectUris: uris(REDIRECT_URI_MAX_COUNT + 1) })).toBe(RULE_ERROR_KEYS.redirectUrisTooMany);
  });

  it('reports the first bad address in the list', () => {
    expect(redirectUrisError({ redirectUris: 'https://ok.example.com/cb, javascript:alert(1)' })).toBe(RULE_ERROR_KEYS.redirectUrisInvalid);
    expect(redirectUrisError({ redirectUris: 'https://ok.example.com/cb, http://bad.example.com/cb' })).toBe(RULE_ERROR_KEYS.redirectUrisHttps);
  });

  it('reads the field it is told to', () => {
    expect(redirectUrisError({ other: 'javascript:alert(1)' }, 'other')).toBe(RULE_ERROR_KEYS.redirectUrisInvalid);
  });
});

describe('passwordLengthOrderError', () => {
  const policy = (min: unknown, max: unknown) => passwordLengthOrderError({ 'passwordPolicy.minimumLength': min as string, 'passwordPolicy.maximumLength': max as string });

  it('refuses a maximum below the minimum (the QA case: minimum 20, maximum 8)', () => {
    expect(policy('20', '8')).toBe(RULE_ERROR_KEYS.passwordLengthOrder);
    expect(policy(20, 8)).toBe(RULE_ERROR_KEYS.passwordLengthOrder);
  });

  it('accepts an equal or larger maximum', () => {
    expect(policy('8', '8')).toBeNull();
    expect(policy('8', '128')).toBeNull();
  });

  it('says nothing while either is empty', () => {
    expect(policy('', '8')).toBeNull();
    expect(policy('20', '')).toBeNull();
    expect(policy(null, null)).toBeNull();
  });
});
