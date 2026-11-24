import { hqEmptyLegalProfile } from 'hq-shared-lib';
import { describe, expect, it } from 'vitest';
import { ORGANIZATION_SETUP_STEPS } from './organization-setup.constants';
import { legalProfileComplete, neighbourStep, resolveStep, stepStatus, visibleSteps } from './organization-setup.helpers';

const holding = (...granted: string[]) => (codes: readonly string[]): boolean => codes.some((c) => granted.includes(c));

describe('organization setup helpers', () => {
  it('lists the five steps in order, each with a read privilege from the catalogue', () => {
    expect(ORGANIZATION_SETUP_STEPS.map((s) => s.id)).toEqual(['details', 'legal', 'operating-model', 'applications', 'members']);
    expect(ORGANIZATION_SETUP_STEPS.map((s) => s.readPrivileges[0])).toEqual([
      'ORGANIZATION_READ', 'ORGANIZATION_LEGAL_PROFILE_READ', 'ORGANIZATION_OPERATING_MODEL_READ', 'ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_MEMBER_LIST',
    ]);
  });

  it('shows only the steps whose read privilege the person holds', () => {
    const steps = visibleSteps(ORGANIZATION_SETUP_STEPS, holding('ORGANIZATION_READ', 'ORGANIZATION_MEMBER_LIST'));
    expect(steps.map((s) => s.id)).toEqual(['details', 'members']);
    expect(visibleSteps(ORGANIZATION_SETUP_STEPS, holding())).toEqual([]);
  });

  it('opens the step the route names, else the first step that may be opened, else none', () => {
    const steps = visibleSteps(ORGANIZATION_SETUP_STEPS, holding('ORGANIZATION_LEGAL_PROFILE_READ', 'ORGANIZATION_MEMBER_LIST'));
    expect(resolveStep('members', steps)).toBe('members');
    expect(resolveStep('details', steps)).toBe('legal');
    expect(resolveStep('nonsense', steps)).toBe('legal');
    expect(resolveStep(null, steps)).toBe('legal');
    expect(resolveStep('legal', [])).toBeNull();
  });

  it('finds the step before and after among the visible ones, and none at the ends', () => {
    const steps = visibleSteps(ORGANIZATION_SETUP_STEPS, holding('ORGANIZATION_READ', 'ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_MEMBER_LIST'));
    expect(neighbourStep(steps, 'details', -1)).toBeNull();
    expect(neighbourStep(steps, 'details', 1)).toBe('applications');
    expect(neighbourStep(steps, 'applications', -1)).toBe('details');
    expect(neighbourStep(steps, 'members', 1)).toBeNull();
    expect(neighbourStep(steps, null, 1)).toBeNull();
    expect(neighbourStep(steps, 'legal', 1)).toBeNull();
  });

  it('gives unsaved edits priority, then done once opened and complete, otherwise to do', () => {
    expect(stepStatus({ unsaved: true, visited: true, complete: true })).toBe('unsaved');
    expect(stepStatus({ unsaved: false, visited: true, complete: true })).toBe('done');
    expect(stepStatus({ unsaved: false, visited: true, complete: false })).toBe('todo');
    expect(stepStatus({ unsaved: false, visited: false, complete: true })).toBe('todo');
  });

  it('counts a legal profile complete once it names the entity and has a registered address with street and city', () => {
    expect(legalProfileComplete(null)).toBe(false);
    expect(legalProfileComplete(hqEmptyLegalProfile())).toBe(false);
    const named = { ...hqEmptyLegalProfile(), legalName: 'Acme Pvt Ltd' };
    expect(legalProfileComplete(named)).toBe(false);
    const withAddress = { ...named, addresses: [{ kind: 'REGISTERED' as const, line1: '12 MG Road', city: 'Hyderabad', country: 'IN' }] };
    expect(legalProfileComplete(withAddress)).toBe(true);
    const operatingOnly = { ...named, addresses: [{ kind: 'OPERATING' as const, line1: '12 MG Road', city: 'Hyderabad', country: 'IN' }] };
    expect(legalProfileComplete(operatingOnly)).toBe(false);
  });
});
