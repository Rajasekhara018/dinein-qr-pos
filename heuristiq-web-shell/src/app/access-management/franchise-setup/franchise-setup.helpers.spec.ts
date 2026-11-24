import { describe, expect, it } from 'vitest';
import { FranchiseOnboarding } from '../franchise-onboarding/franchise-onboarding.models';
import { FRANCHISE_SETUP_STEPS } from './franchise-setup.constants';
import { checklistDone, completedSteps, onboardingSectionOf } from './franchise-setup.helpers';

function onboarding(overrides: Partial<FranchiseOnboarding> = {}): FranchiseOnboarding {
  return {
    franchiseId: 'f1', code: 'RBM-1', onboardingStatus: 'ONBOARDING', activatedAt: null, activatedBy: null,
    checklist: { legalProfile: 'DONE', kyc: 'SUBMITTED', bankAccount: 'VERIFIED', agreement: 'MISSING' },
    canActivate: false, missing: [], kycDocuments: [], bankAccount: null, agreement: null, ...overrides,
  };
}

describe('franchise setup helpers', () => {
  it('counts only DONE and VERIFIED as done', () => {
    expect(['DONE', 'VERIFIED'].every(checklistDone)).toBe(true);
    expect(['SUBMITTED', 'REJECTED', 'MISSING', '', null, undefined].some(checklistDone)).toBe(false);
  });

  it('marks the steps the onboarding checklist reports as done, and activation once onboarded', () => {
    expect([...completedSteps(onboarding())].sort()).toEqual(['bank', 'details', 'legal']);
    expect(completedSteps(onboarding({ onboardingStatus: 'ONBOARDED' })).has('activation')).toBe(true);
  });

  it('knows only the details step without an onboarding read model', () => {
    expect([...completedSteps(null)]).toEqual(['details']);
  });

  it('maps the onboarding steps to their section and every other step to none', () => {
    expect(onboardingSectionOf('bank')).toBe('bank');
    expect(onboardingSectionOf('details')).toBeNull();
    expect(onboardingSectionOf(null)).toBeNull();
  });

  it('gives administrators the terms step and owners only legal details and onboarding, with the right privileges', () => {
    expect(FRANCHISE_SETUP_STEPS.franchise.map((step) => step.id)).toEqual(['details', 'legal', 'kyc', 'bank', 'agreement', 'activation']);
    expect(FRANCHISE_SETUP_STEPS.myFranchise.map((step) => step.id)).toEqual(['legal', 'kyc', 'bank', 'agreement', 'activation']);
    const owner = FRANCHISE_SETUP_STEPS.myFranchise.find((step) => step.id === 'activation')!;
    expect(owner.readPrivileges).toEqual(['FRANCHISE_MINE_ONBOARDING_READ']);
    expect(owner.writePrivileges).toEqual([]);
    expect(FRANCHISE_SETUP_STEPS.franchise.find((step) => step.id === 'activation')!.writePrivileges).toEqual(['FRANCHISE_ACTIVATION_UPDATE']);
  });
});
