import { describe, expect, it } from 'vitest';
import { ruleInForce, ruleResolveQueryString } from './franchise-rules-in-force.helpers';
import { RuleResolution } from './franchise-rules-in-force.models';

const labels = { gst: (code: string) => `gst:${code}`, party: (code: string) => `party:${code}` };

function resolution(level: string, overrides: Partial<RuleResolution> = {}): RuleResolution {
  return {
    kind: 'COMMISSION_PLAN', date: '2026-11-16', scope: { level, planCode: level === 'PLAN' ? ' GOLD ' : null },
    rule: { id: 'r1', scope: { level }, lines: [{ party: 'OWNER', valueType: 'PERCENTAGE', basisPoints: 1500 }] }, ...overrides,
  };
}

describe('franchise rules in force helpers', () => {
  it('asks for one kind of rule of one application of one franchise', () => {
    expect(ruleResolveQueryString('REFERRAL_PLAN', 'f 1', 'APP')).toBe('?kind=REFERRAL_PLAN&franchiseId=f+1&vertical=APP');
  });

  it("names where the rule comes from: the franchise's own plan, a package's named plan (by code) or the organization", () => {
    expect(ruleInForce(resolution('FRANCHISE'), labels)?.sourceKey).toBe('accessManagement.franchises.rulesInForce.sources.franchise');
    expect(ruleInForce(resolution('PLAN'), labels)).toEqual({
      sourceKey: 'accessManagement.franchises.rulesInForce.sources.plan', sourceParams: { code: 'GOLD' }, summary: 'party:OWNER 15%',
    });
    expect(ruleInForce(resolution('CITY_VERTICAL'), labels)?.sourceKey).toBe('accessManagement.franchises.rulesInForce.sources.organization');
  });

  it('summarises a referral rate', () => {
    const referral = resolution('FRANCHISE', { kind: 'REFERRAL_PLAN', rule: { id: 'r2', scope: { level: 'FRANCHISE' }, rate: { valueType: 'PERCENTAGE', basisPoints: 250 } } });
    expect(ruleInForce(referral, labels)?.summary).toBe('2.5%');
  });

  it('is null when no rule is in force', () => {
    expect(ruleInForce(null, labels)).toBeNull();
    expect(ruleInForce(resolution('FRANCHISE', { rule: null }), labels)).toBeNull();
  });
});
