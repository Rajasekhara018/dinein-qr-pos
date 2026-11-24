import { describe, expect, it } from 'vitest';
import { AdminFormField } from './admin-form.types';
import { buildPayload, errorDetail, fieldError, getByPath, initialFieldValue, invalidPhoneField, setByPath } from './admin-form.utils';

const f = (over: Partial<AdminFormField> & Pick<AdminFormField, 'key' | 'type'>): AdminFormField => ({ label: 'l', ...over });

describe('getByPath / setByPath', () => {
  it('reads nested values and returns undefined when any step is missing', () => {
    const obj = { a: { b: { c: 1 } }, n: null };
    expect(getByPath(obj, 'a.b.c')).toBe(1);
    expect(getByPath(obj, 'a.x.c')).toBeUndefined();
    expect(getByPath(obj, 'n.z')).toBeUndefined();
    expect(getByPath(undefined, 'a')).toBeUndefined();
  });

  it('writes along the path, creating or replacing intermediate objects', () => {
    const obj: Record<string, unknown> = { a: 5 };
    setByPath(obj, 'a.b.c', 1);
    setByPath(obj, 'top', 2);
    setByPath(obj, 'a.b.d', 3);
    expect(obj).toEqual({ a: { b: { c: 1, d: 3 } }, top: 2 });
  });
});

describe('initialFieldValue', () => {
  const entity = { name: 'X', tags: ['a', 'b'], ids: [1, 2], on: 1, userName: 'asha', full: { firstName: 'A', middleName: '', lastName: 'R' }, other: { k: 1 }, nothing: null };

  it('returns empty values when there is no entity', () => {
    expect(initialFieldValue(f({ key: 'name', type: 'text' }), null)).toBe('');
    expect(initialFieldValue(f({ key: 'tags', type: 'tags' }), null)).toBe('');
    expect(initialFieldValue(f({ key: 'ids', type: 'multiselect' }), null)).toEqual([]);
    expect(initialFieldValue(f({ key: 'on', type: 'checkbox' }), null)).toBe(false);
  });

  it('starts a create page with the default value, and an edit page with the entity value', () => {
    const active = f({ key: 'active', type: 'checkbox', defaultValue: true, readValue: (e) => e['active'] !== false });
    expect(initialFieldValue(active, null)).toBe(true);
    expect(initialFieldValue(active, { active: false })).toBe(false);
    expect(initialFieldValue(active, { active: null })).toBe(true);
    expect(initialFieldValue(f({ key: 'name', type: 'text', defaultValue: 'x' }), { name: 'Y' })).toBe('Y');
  });

  it('starts the application form active and warns that shared names are SUPER_ADMIN-only', async () => {
    const { APPLICATION_CRUD_CONFIG } = await import('./access-management-configs');
    const active = APPLICATION_CRUD_CONFIG.fields.find((field) => field.key === 'active')!;
    expect(active.type).toBe('checkbox');
    expect(initialFieldValue(active, null)).toBe(true);
    expect(initialFieldValue(active, { active: false })).toBe(false);
    ['name', 'description'].forEach((key) =>
      expect(APPLICATION_CRUD_CONFIG.fields.find((field) => field.key === key)?.hint).toBe('accessManagement.applications.sharedTextHint'));
  });

  it('does not offer the shared Owner Team field (the organization owner team lives on the application access page)', async () => {
    const { APPLICATION_CRUD_CONFIG } = await import('./access-management-configs');
    const keys = APPLICATION_CRUD_CONFIG.fields.map((field) => field.key);
    expect(keys).not.toContain('ownerTeam');
    expect(keys).toEqual(expect.arrayContaining(['code', 'name', 'supportEmail', 'active']));
  });

  it('reads text via readKey, readValue or the key itself', () => {
    expect(initialFieldValue(f({ key: 'name', type: 'text' }), entity)).toBe('X');
    expect(initialFieldValue(f({ key: 'username', readKey: 'userName', type: 'text' }), entity)).toBe('asha');
    expect(initialFieldValue(f({ key: 'name', type: 'text', readValue: () => 'computed' }), entity)).toBe('computed');
    expect(initialFieldValue(f({ key: 'nothing', type: 'text' }), entity)).toBe('');
  });

  it('joins tags, stringifies multiselect values and coerces checkboxes', () => {
    expect(initialFieldValue(f({ key: 'tags', type: 'tags' }), entity)).toBe('a, b');
    expect(initialFieldValue(f({ key: 'name', type: 'tags' }), entity)).toBe('');
    expect(initialFieldValue(f({ key: 'ids', type: 'entity-picker' }), entity)).toEqual(['1', '2']);
    expect(initialFieldValue(f({ key: 'name', type: 'multiselect' }), entity)).toEqual([]);
    expect(initialFieldValue(f({ key: 'on', type: 'checkbox' }), entity)).toBe(true);
  });

  it('flattens a name object into text and other objects into JSON', () => {
    expect(initialFieldValue(f({ key: 'full', type: 'text' }), entity)).toBe('A R');
    expect(initialFieldValue(f({ key: 'other', type: 'text' }), entity)).toBe('{"k":1}');
  });
});

describe('fieldError', () => {
  it('has no error for an empty value', () => {
    expect(fieldError(f({ key: 'k', type: 'text', pattern: /^a$/ }), { k: '  ' })).toBeNull();
    expect(fieldError(f({ key: 'k', type: 'text', pattern: /^a$/ }), {})).toBeNull();
  });

  it('reports a pattern mismatch with the field message or the shared default', () => {
    expect(fieldError(f({ key: 'k', type: 'text', pattern: /^a$/, patternMessage: 'my.msg' }), { k: 'b' })).toBe('my.msg');
    expect(fieldError(f({ key: 'k', type: 'text', pattern: /^a$/ }), { k: 'b' })).toBe('shared.adminCrud.invalidValue');
    expect(fieldError(f({ key: 'k', type: 'text', pattern: /^a$/ }), { k: 'a' })).toBeNull();
  });

  it('reports a number below the minimum', () => {
    const field = f({ key: 'n', type: 'number', min: 5, minMessage: 'my.min' });
    expect(fieldError(field, { n: '3' })).toBe('my.min');
    expect(fieldError(f({ key: 'n', type: 'number', min: 5 }), { n: '3' })).toBe('shared.adminCrud.belowMinimum');
    expect(fieldError(field, { n: '5' })).toBeNull();
    expect(fieldError(f({ key: 'n', type: 'text', min: 5 }), { n: '3' })).toBeNull();
  });
});

describe('invalidPhoneField', () => {
  const fields = [f({ key: 'name', type: 'text' }), f({ key: 'mobile', type: 'phone' }), f({ key: 'alt', type: 'phone' })];
  it('returns the first phone field holding a malformed number', () => {
    expect(invalidPhoneField(fields, { mobile: '+919876543210', alt: '12' })?.key).toBe('alt');
    expect(invalidPhoneField(fields, { mobile: 'abc', alt: '12' })?.key).toBe('mobile');
  });
  it('ignores empty phone fields and non-phone fields', () => {
    expect(invalidPhoneField(fields, { name: 'not a phone', mobile: ' ', alt: '' })).toBeNull();
  });
});

describe('buildPayload', () => {
  it('splits tags, parses numbers, trims phones and nests dotted keys', () => {
    const payload = buildPayload(
      [f({ key: 'code', type: 'text' }), f({ key: 'tags', type: 'tags' }), f({ key: 'policy.min', type: 'number' }),
        f({ key: 'policy.max', type: 'number' }), f({ key: 'phone', type: 'phone' }), f({ key: 'on', type: 'checkbox' })],
      { code: 'X', tags: ' a, b ,, c', 'policy.min': ' 8 ', 'policy.max': '', phone: ' +91 98 ', on: true },
    );
    expect(payload).toEqual({ code: 'X', tags: ['a', 'b', 'c'], policy: { min: 8, max: null }, phone: '+91 98', on: true });
  });

  it('turns missing tags into an empty list', () => {
    expect(buildPayload([f({ key: 't', type: 'tags' })], {})).toEqual({ t: [] });
  });
});

describe('errorDetail', () => {
  it('returns the backend detail when there is one', () => {
    expect(errorDetail({ error: { detail: 'Nope' } })).toBe('Nope');
  });
  it('returns the envelope message, a plain text body, or the first field error', () => {
    expect(errorDetail({ status: 409, error: { success: false, message: 'Still in use' } })).toBe('Still in use');
    expect(errorDetail({ error: 'x' })).toBe('x');
    expect(errorDetail({ status: 400, error: { responseObject: [{ field: 'code', message: 'is required' }] } })).toBe('code: is required');
  });
  it('returns undefined for anything else', () => {
    for (const bad of [null, 'x', {}, { error: {} }, { error: { detail: '' } }, { error: { detail: 5 } }, { status: 500, error: { message: 'Boom' } }]) {
      expect(errorDetail(bad)).toBeUndefined();
    }
  });
});
