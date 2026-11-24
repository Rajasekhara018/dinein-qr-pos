import { userTypeOf } from './users-list.helpers';

describe('userTypeOf', () => {
  it('uses the role named after a user type', () => {
    expect(userTypeOf({ roles: ['DRIVER'] })).toBe('DRIVER');
    expect(userTypeOf({ roles: ['customer'] })).toBe('CUSTOMER');
  });

  it('prefers the more privileged type when several match', () => {
    expect(userTypeOf({ roles: ['CUSTOMER', 'SUPER_ADMIN'] })).toBe('SUPER_ADMIN');
  });

  it('treats a user with only an organization role, or no roles, as staff', () => {
    expect(userTypeOf({ roles: ['Realboom Organization administrator'] })).toBe('OPS');
    expect(userTypeOf({})).toBe('OPS');
  });
});
