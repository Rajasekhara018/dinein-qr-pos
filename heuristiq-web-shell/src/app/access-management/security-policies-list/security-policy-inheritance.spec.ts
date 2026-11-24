import { buildPayload, initialFieldValue } from '../admin-form.utils';
import { SECURITY_POLICY_CRUD_CONFIG } from '../access-management-configs';

describe('security policy inheritance payloads', () => {
  const fields = SECURITY_POLICY_CRUD_CONFIG.fields;
  const mandatory = fields.find(field => field.key === 'mfaPolicy.mandatory')!;
  it('keeps inherit, yes and no distinct through edit and save', () => {
    for (const [stored, control] of [[null, ''], [true, 'true'], [false, 'false']] as const) {
      const value = initialFieldValue(mandatory, { mfaPolicy: { mandatory: stored } });
      expect(value).toBe(control);
      expect(buildPayload([mandatory], { [mandatory.key]: value })).toEqual({ mfaPolicy: { mandatory: stored } });
    }
  });
  it('inherits optional numbers and sends empty schedules as null', () => {
    const selected = fields.filter(field => ['passwordPolicy.minimumLength', 'effectiveFrom', 'effectiveUntil'].includes(field.key));
    expect(buildPayload(selected, { 'passwordPolicy.minimumLength': '', effectiveFrom: '', effectiveUntil: '' })).toEqual({
      passwordPolicy: { minimumLength: null }, effectiveFrom: null, effectiveUntil: null,
    });
  });
  it('preserves policy revision and converts explicit false to a boolean', () => {
    const selected = fields.filter(field => ['version', 'mfaPolicy.mandatory'].includes(field.key));
    expect(buildPayload(selected, { version: '7', 'mfaPolicy.mandatory': 'false' })).toEqual({ version: 7, mfaPolicy: { mandatory: false } });
  });
});
