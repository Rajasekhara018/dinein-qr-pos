import { apiErrorMessage } from './api-error.helpers';

const FALLBACK = 'Could not delete';

describe('apiErrorMessage', () => {
  it('shows the envelope message', () => {
    const err = { status: 409, error: { success: false, message: 'This application still has 1 roles and 2 users with access.' } };
    expect(apiErrorMessage(err, FALLBACK)).toBe('This application still has 1 roles and 2 users with access.');
  });

  it('falls back to detail, then the first field error, then responseObject.message', () => {
    expect(apiErrorMessage({ status: 400, error: { detail: 'Bad code' } }, FALLBACK)).toBe('Bad code');
    expect(apiErrorMessage({ status: 400, error: { message: 'Validation failed', responseObject: [{ field: 'code', message: 'must not be blank' }] } }, FALLBACK))
      .toBe('code: must not be blank');
    expect(apiErrorMessage({ status: 400, error: { errors: ['first', 'second'] } }, FALLBACK)).toBe('first');
    expect(apiErrorMessage({ status: 400, error: { responseObject: { message: 'Nested reason' } } }, FALLBACK)).toBe('Nested reason');
  });

  it('uses the fallback when there is nothing specific', () => {
    expect(apiErrorMessage(null, FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage({ status: 400, error: {} }, FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage({ status: 400, error: { message: 'Bad Request' } }, FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage({ status: 400, error: { errors: [{}] } }, FALLBACK)).toBe(FALLBACK);
  });

  it('never shows server internals for network failures and 5xx', () => {
    expect(apiErrorMessage({ status: 0, error: { message: 'boom' } }, FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage({ status: 500, error: { message: 'NullPointerException at X' } }, FALLBACK)).toBe(FALLBACK);
  });

  it('keeps a 403 server message and rejects html, stack traces, and caps length', () => {
    expect(apiErrorMessage({ status: 403, error: { message: 'You may not remove this' } }, FALLBACK)).toBe('You may not remove this');
    expect(apiErrorMessage({ status: 400, error: '<html><body>Gateway</body></html>' }, FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage({ status: 400, error: { message: 'x.Y at com.foo.Bar(Bar.java:12)' } }, FALLBACK)).toBe(FALLBACK);
    const long = apiErrorMessage({ status: 400, error: { message: 'a'.repeat(500) } }, FALLBACK);
    expect(long.length).toBe(300);
    expect(long.endsWith('…')).toBe(true);
    expect(apiErrorMessage({ status: 400, error: '  plain   text ' }, FALLBACK)).toBe('plain text');
  });
});
