import { EMAIL_VERIFICATION_REQUIRED_CODE, LOGIN_EMAIL_PATTERN, LOGIN_ERROR_CODE_KEYS } from './login.constants';
import { loginErrorCode, loginErrorKey, pointerOffset } from './login.helpers';

describe('loginErrorKey', () => {
  it('maps FRANCHISE_INACTIVE to its translation key', () => {
    expect(loginErrorKey({ status: 403, error: { errorCode: 'FRANCHISE_INACTIVE', message: 'x' } })).toBe(
      'login.messages.franchiseInactive',
    );
    expect(LOGIN_ERROR_CODE_KEYS['FRANCHISE_INACTIVE']).toBe('login.messages.franchiseInactive');
  });

  it('answers null for an unknown, missing or malformed code', () => {
    expect(loginErrorKey({ error: { errorCode: 'SOMETHING_ELSE' } })).toBeNull();
    expect(loginErrorKey({ error: { message: 'Invalid username or password' } })).toBeNull();
    expect(loginErrorKey({ error: { errorCode: 42 } })).toBeNull();
    expect(loginErrorKey({ error: 'plain text' })).toBeNull();
    expect(loginErrorKey({ error: null })).toBeNull();
    expect(loginErrorKey(null)).toBeNull();
    expect(loginErrorKey(undefined)).toBeNull();
  });

  it('never maps an inherited object property', () => {
    expect(loginErrorKey({ error: { errorCode: 'toString' } })).toBeNull();
    expect(loginErrorKey({ error: { errorCode: '__proto__' } })).toBeNull();
  });
});

describe('pointerOffset', () => {
  it('maps the centre to 0 and the edges to -1 and 1', () => {
    expect(pointerOffset(500, 300, 1000, 600)).toEqual({ x: 0, y: 0 });
    expect(pointerOffset(0, 0, 1000, 600)).toEqual({ x: -1, y: -1 });
    expect(pointerOffset(1000, 600, 1000, 600)).toEqual({ x: 1, y: 1 });
  });

  it('clamps a pointer outside the area and answers 0 for an empty area', () => {
    expect(pointerOffset(-200, 900, 1000, 600)).toEqual({ x: -1, y: 1 });
    expect(pointerOffset(10, 10, 0, 0)).toEqual({ x: 0, y: 0 });
  });
});

describe('email verification on sign-in', () => {
  it('reads the error code UAM put on a refusal', () => {
    expect(loginErrorCode({ error: { errorCode: EMAIL_VERIFICATION_REQUIRED_CODE } })).toBe('EMAIL_VERIFICATION_REQUIRED');
    expect(loginErrorCode({ error: { message: 'no code' } })).toBe('');
    expect(loginErrorCode(null)).toBe('');
  });

  it('only emails a code to something shaped like an email address', () => {
    expect(LOGIN_EMAIL_PATTERN.test('admin@realboom.com')).toBe(true);
    expect(LOGIN_EMAIL_PATTERN.test('admin')).toBe(false);
    expect(LOGIN_EMAIL_PATTERN.test('9876543210')).toBe(false);
  });
});
