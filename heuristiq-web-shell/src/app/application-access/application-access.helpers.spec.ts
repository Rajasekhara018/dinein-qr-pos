import { accessFailureKey, httpStatusOf } from './application-access.helpers';

describe('application access helpers', () => {
  it('chooses the sentence by HTTP status, from an error or from the status itself', () => {
    expect(accessFailureKey({ status: 403 })).toBe('applicationAccess.errors.forbidden');
    expect(accessFailureKey(409)).toBe('applicationAccess.errors.duplicate');
    expect(accessFailureKey({ status: 404 })).toBe('applicationAccess.errors.notFound');
    expect(accessFailureKey(400)).toBe('applicationAccess.errors.invalid');
    expect(accessFailureKey({ status: 500 })).toBe('applicationAccess.errors.failed');
    expect(accessFailureKey(null)).toBe('applicationAccess.errors.failed');
  });

  it('reads the status of a failed call and gives 0 for anything else', () => {
    expect(httpStatusOf({ status: 409 })).toBe(409);
    expect(httpStatusOf({ status: '409' })).toBe(0);
    expect(httpStatusOf(null)).toBe(0);
    expect(httpStatusOf('boom')).toBe(0);
  });
});
