import { describe, expect, it } from 'vitest';
import { CLIENT_CRUD_CONFIG, SECURITY_POLICY_CRUD_CONFIG } from './access-management-configs';
import { USER_FORM_CONFIG } from './users-list/users-list.constants';
import { fieldError } from './admin-form.utils';
import { AdminFormConfig, AdminFormField } from './admin-form.types';
import { RULE_ERROR_KEYS } from './access-management-rules.constants';

const field = (config: AdminFormConfig, key: string): AdminFormField => {
  const found = config.fields.find((f) => f.key === key);
  if (!found) throw new Error(`no field ${key}`);
  return found;
};

const errorOf = (config: AdminFormConfig, key: string, values: Record<string, string | string[] | boolean>) => fieldError(field(config, key), values);

describe('API client form rules', () => {
  const clientId = (value: string) => errorOf(CLIENT_CRUD_CONFIG, 'clientId', { clientId: value });

  it.each(['abc', 'rider-app.v1', 'A1_b-2', `a${'b'.repeat(63)}`])('accepts the client id %s', (value) => {
    expect(clientId(value)).toBeNull();
  });

  it.each(['ab', 'qa client!', 'has space', '-lead', '.lead', 'a/b', `a${'b'.repeat(64)}`])('rejects the client id %s', (value) => {
    expect(clientId(value)).toBe(RULE_ERROR_KEYS.clientId);
  });

  it('requires a client id and limits what can be typed to 64', () => {
    expect(field(CLIENT_CRUD_CONFIG, 'clientId')).toMatchObject({ required: true, maxLength: 64 });
  });

  it('picks the organization and the application from lists instead of free text', () => {
    for (const key of ['tenantId', 'applicationId']) {
      expect(field(CLIENT_CRUD_CONFIG, key)).toMatchObject({ type: 'select' });
      expect(field(CLIENT_CRUD_CONFIG, key).loadOptions).toBeTypeOf('function');
    }
  });

  it('refuses the QA redirect URI javascript:alert(1) and a plain-http host', () => {
    expect(errorOf(CLIENT_CRUD_CONFIG, 'redirectUris', { redirectUris: 'javascript:alert(1)' })).toBe(RULE_ERROR_KEYS.redirectUrisInvalid);
    expect(errorOf(CLIENT_CRUD_CONFIG, 'redirectUris', { redirectUris: 'http://app.example.com/cb' })).toBe(RULE_ERROR_KEYS.redirectUrisHttps);
    expect(errorOf(CLIENT_CRUD_CONFIG, 'redirectUris', { redirectUris: 'https://app.example.com/cb, http://localhost:4200/cb' })).toBeNull();
  });

  it('fills in client credentials for an integration client and browser sign-in for a web client', () => {
    const typeOf = (value: string) => field(CLIENT_CRUD_CONFIG, 'clientType').options?.find((o) => o.value === value);
    expect(typeOf('INTEGRATION')?.fill).toEqual({ authenticationMethod: 'CLIENT_SECRET', grantTypes: ['CLIENT_CREDENTIALS'] });
    expect(typeOf('WEB')?.fill).toEqual({ authenticationMethod: 'CLIENT_SECRET', grantTypes: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] });
  });

  it('offers only the client secret method, which Keycloak is set up for, and defaults to it', () => {
    const method = field(CLIENT_CRUD_CONFIG, 'authenticationMethod');
    expect(method.defaultValue).toBe('CLIENT_SECRET');
    expect(method.options?.filter((o) => !o.disabled).map((o) => o.value)).toEqual(['CLIENT_SECRET']);
  });

  it('asks for a redirect URI only when the authorization code grant is ticked', () => {
    expect(errorOf(CLIENT_CRUD_CONFIG, 'grantTypes', { grantTypes: ['AUTHORIZATION_CODE'], redirectUris: '' })).toBe(RULE_ERROR_KEYS.redirectUrisRequired);
    expect(errorOf(CLIENT_CRUD_CONFIG, 'grantTypes', { grantTypes: ['AUTHORIZATION_CODE'], redirectUris: 'https://app.example.com/cb' })).toBeNull();
    expect(errorOf(CLIENT_CRUD_CONFIG, 'grantTypes', { grantTypes: ['CLIENT_CREDENTIALS'], redirectUris: '' })).toBeNull();
  });

  it('starts a new client active and shows a guide beside the form', () => {
    expect(field(CLIENT_CRUD_CONFIG, 'active').defaultValue).toBe(true);
    expect(CLIENT_CRUD_CONFIG.guide?.sections.length).toBeGreaterThan(0);
  });
});

describe('security policy form rules', () => {
  const policy = (key: string, value: string, other: Record<string, string> = {}) =>
    errorOf(SECURITY_POLICY_CRUD_CONFIG, key, { [key]: value, ...other });

  it('keeps the minimum password length between 8 and 128', () => {
    expect(policy('passwordPolicy.minimumLength', '7')).toBe(RULE_ERROR_KEYS.passwordMinLength);
    expect(policy('passwordPolicy.minimumLength', '8')).toBeNull();
    expect(policy('passwordPolicy.minimumLength', '128')).toBeNull();
    expect(policy('passwordPolicy.minimumLength', '129')).toBe(RULE_ERROR_KEYS.passwordMinLength);
    expect(policy('passwordPolicy.minimumLength', '20')).toBeNull();
  });

  it('refuses a maximum password length below the minimum (the QA case 20 and 8)', () => {
    const both = { 'passwordPolicy.minimumLength': '20', 'passwordPolicy.maximumLength': '8' };
    expect(policy('passwordPolicy.maximumLength', '8', both)).toBe(RULE_ERROR_KEYS.passwordLengthOrder);
    expect(policy('passwordPolicy.maximumLength', '20', { ...both, 'passwordPolicy.maximumLength': '20' })).toBeNull();
    expect(policy('passwordPolicy.maximumLength', '-1', { 'passwordPolicy.maximumLength': '-1' })).toBe(RULE_ERROR_KEYS.notNegative);
  });

  it('keeps the allowed failed attempts between 1 and 20', () => {
    expect(policy('loginPolicy.maxFailedAttempts', '-1')).toBe(RULE_ERROR_KEYS.maxFailedAttempts);
    expect(policy('loginPolicy.maxFailedAttempts', '0')).toBe(RULE_ERROR_KEYS.maxFailedAttempts);
    expect(policy('loginPolicy.maxFailedAttempts', '1')).toBeNull();
    expect(policy('loginPolicy.maxFailedAttempts', '20')).toBeNull();
    expect(policy('loginPolicy.maxFailedAttempts', '21')).toBe(RULE_ERROR_KEYS.maxFailedAttempts);
  });

  it('needs an idle timeout of at least one minute', () => {
    expect(policy('sessionPolicy.idleTimeoutMinutes', '0')).toBe(RULE_ERROR_KEYS.idleTimeout);
    expect(policy('sessionPolicy.idleTimeoutMinutes', '1')).toBeNull();
    expect(policy('sessionPolicy.idleTimeoutMinutes', '30')).toBeNull();
  });

  it.each([
    'passwordPolicy.expiryDays',
    'loginPolicy.lockDurationMinutes',
    'loginPolicy.maxConcurrentSessions',
    'sessionPolicy.absoluteTimeoutMinutes',
  ])('does not accept a negative %s, and accepts zero', (key) => {
    expect(policy(key, '-1')).toBe(RULE_ERROR_KEYS.notNegative);
    expect(policy(key, '0')).toBeNull();
  });

  it('picks the organization and the application from lists instead of free text', () => {
    for (const key of ['tenantId', 'applicationId']) {
      expect(field(SECURITY_POLICY_CRUD_CONFIG, key)).toMatchObject({ type: 'select' });
      expect(field(SECURITY_POLICY_CRUD_CONFIG, key).loadOptions).toBeTypeOf('function');
    }
  });
});

describe('user form rules', () => {
  const email = (value: string) => errorOf(USER_FORM_CONFIG, 'email', { email: value });
  const password = (value: string) => errorOf(USER_FORM_CONFIG, 'password', { password: value });

  it('accepts a sane email of up to 254 characters and refuses the QA case bad@', () => {
    expect(email('person@example.com')).toBeNull();
    expect(email('bad@')).toBe(RULE_ERROR_KEYS.userEmail);
    expect(email('bad@x')).toBe(RULE_ERROR_KEYS.userEmail);
    expect(email('has space@example.com')).toBe(RULE_ERROR_KEYS.userEmail);
    expect(email('no-at-sign.example.com')).toBe(RULE_ERROR_KEYS.userEmail);
    expect(field(USER_FORM_CONFIG, 'email').maxLength).toBe(254);
  });

  it('needs a password of 8 to 128 characters with a letter and a digit', () => {
    expect(password('a')).toBe(RULE_ERROR_KEYS.userPassword);
    expect(password('abcdefg1')).toBeNull();
    expect(password('abcdefg')).toBe(RULE_ERROR_KEYS.userPassword);
    expect(password('abcdefgh')).toBe(RULE_ERROR_KEYS.userPassword);
    expect(password('12345678')).toBe(RULE_ERROR_KEYS.userPassword);
    expect(password(`a1${'x'.repeat(126)}`)).toBeNull();
    expect(password(`a1${'x'.repeat(127)}`)).toBe(RULE_ERROR_KEYS.userPassword);
  });

  it('hides the password as it is typed', () => {
    expect(field(USER_FORM_CONFIG, 'password').type).toBe('password');
  });

  it('leaves an empty password and email alone (both are optional on an edit)', () => {
    expect(password('')).toBeNull();
    expect(email('')).toBeNull();
  });
});
