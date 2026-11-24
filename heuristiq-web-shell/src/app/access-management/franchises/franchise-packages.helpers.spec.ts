import { describe, expect, it } from 'vitest';
import {
  emptyLineForm, emptyPackageForm, lineToRequest, packageFormError, packageQueryString, packageToForm, pickedPlanName, planField,
  toPackageRequest, withActive,
} from './franchise-packages.helpers';
import { PackageTemplate } from './franchise-packages.models';
import { applyTemplate, emptyTermsRow, openFranchiseError, rowFeeInvalid, rowLimitInvalid, termsExtrasError, toTerms } from './franchises.helpers';

const template: PackageTemplate = {
  id: 't1', name: 'Matrimony, agent 15%', applicationCode: 'MATRIMONY', description: null, active: true,
  feePlanCode: 'GOLD_FEE', feePlanName: 'Gold fee', commissionPlanCode: 'AGENT15', commissionPlanName: 'Agent 15%',
  referralPlanCode: null, referralPlanName: null, version: 3,
};

describe('franchise package helpers', () => {
  it('round-trips a template through the form and keeps the version and the picked plans, never a fee plan', () => {
    const request = toPackageRequest(packageToForm(template));
    expect(request).toEqual({
      name: 'Matrimony, agent 15%', applicationCode: 'MATRIMONY', placeType: null, feePlanCode: null,
      commissionPlanCode: 'AGENT15', referralPlanCode: null, active: true, version: 3,
    });
  });

  it('keeps the type of place a package is for, and sends none (null) for a package for any place', () => {
    const town = packageToForm({ ...template, placeType: 'TOWN' });
    expect(town.placeType).toBe('TOWN');
    expect(toPackageRequest(town)).toMatchObject({ placeType: 'TOWN' });
    expect(packageToForm(template).placeType).toBe('');
    expect(toPackageRequest({ ...town, placeType: '' })).toMatchObject({ placeType: null });
    expect(withActive({ ...template, placeType: 'CITY' }, false)).toMatchObject({ placeType: 'CITY', active: false });
  });

  it('sends the plan codes trimmed, a blank pick as null ("follow the rules that apply") and never a fee plan', () => {
    const form = { ...emptyPackageForm(), name: ' Gold ', applicationCode: 'RIDE', description: '  ', feePlanCode: ' F1 ', commissionPlanCode: '', referralPlanCode: '   ' };
    const request = toPackageRequest(form);
    expect(request).toMatchObject({ name: 'Gold', feePlanCode: null, commissionPlanCode: null, referralPlanCode: null });
    expect(request).not.toHaveProperty('description');
    expect(request).not.toHaveProperty('version');
    expect(request).not.toHaveProperty('fee');
    expect(request).not.toHaveProperty('lines');
  });

  it('reads a template that picked no plans as blank picks', () => {
    const bare: PackageTemplate = { id: 't2', name: 'Bare', applicationCode: 'RIDE', active: false, version: 0 };
    expect(packageToForm(bare)).toMatchObject({ feePlanCode: '', commissionPlanCode: '', referralPlanCode: '', description: '' });
  });

  it('switches a template on or off by sending the whole template, its plans included', () => {
    expect(withActive(template, false)).toMatchObject({ active: false, version: 3, name: template.name, feePlanCode: null, commissionPlanCode: 'AGENT15' });
  });

  it('names the plan a template picked, falls back to the code, and is blank without a pick', () => {
    expect(pickedPlanName(template, 'fee')).toBe('Gold fee');
    expect(pickedPlanName(template, 'commission')).toBe('Agent 15%');
    expect(pickedPlanName(template, 'referral')).toBe('');
    expect(pickedPlanName({ ...template, referralPlanCode: 'REF2', referralPlanName: ' ' }, 'referral')).toBe('REF2');
    expect([planField('fee'), planField('commission'), planField('referral')]).toEqual(['feePlanCode', 'commissionPlanCode', 'referralPlanCode']);
  });

  it('names the first thing wrong with a form, and needs no plan to be picked', () => {
    const form = emptyPackageForm();
    expect(packageFormError(form)).toContain('errors.name');
    form.name = 'A';
    expect(packageFormError(form)).toContain('errors.application');
    form.applicationCode = 'RIDE';
    expect(packageFormError(form)).toBeNull();
    expect(packageFormError({ ...form, description: 'x'.repeat(501) })).toContain('errors.description');
  });

  it('drops the min and max of a fixed line, which the service rejects, and keeps them on a percentage line', () => {
    const fixed = lineToRequest({ ...emptyLineForm(), party: 'PLATFORM', valueType: 'FIXED', fixedAmount: 120, minAmount: 5, maxAmount: 10 }, 'INR');
    expect(fixed).toEqual({ party: 'PLATFORM', valueType: 'FIXED', fixedAmount: { code: 'INR', value: 120 }, active: true });
    const percentage = lineToRequest({ ...emptyLineForm(), party: 'AGENT', valueType: 'PERCENTAGE', basisPoints: 1500, minAmount: 5, maxAmount: 10 }, 'INR');
    expect(percentage).toMatchObject({ basisPoints: 1500, minAmount: { code: 'INR', value: 5 }, maxAmount: { code: 'INR', value: 10 } });
  });

  it('builds the list query', () => {
    expect(packageQueryString({})).toBe('');
    expect(packageQueryString({ applicationCode: 'RIDE', active: false, q: ' x ', page: 0, size: 20 }))
      .toBe('?applicationCode=RIDE&active=false&q=x&page=0&size=20');
  });
});

describe('open franchise row with a package template', () => {
  it('keeps the agent places, fee, currency and offer already in the row, and sends the template id', () => {
    const row = applyTemplate({ ...emptyTermsRow(), applicationCode: 'MATRIMONY', agentLimit: 35, fee: 500, currency: 'INR', planKey: 'p-in' }, template);
    expect(row).toMatchObject({ templateId: 't1', agentLimit: 35, fee: 500, currency: 'INR', planKey: 'p-in' });
    expect(toTerms(row)).toMatchObject({ templateId: 't1', agentLimit: 35 });
    expect(toTerms(row).commission).toBeUndefined();
  });

  it('keeps them whatever the package is for (a type of place, a fee plan or neither)', () => {
    const placed = { ...emptyTermsRow(), applicationCode: 'MATRIMONY', agentLimit: 35, fee: 500 };
    expect(applyTemplate(placed, { ...template, placeType: 'TOWN' })).toMatchObject({ agentLimit: 35, fee: 500 });
    expect(applyTemplate(placed, { ...template, feePlanCode: null })).toMatchObject({ agentLimit: 35, fee: 500 });
  });

  it('leaves blank terms blank, so the server fills them from the package fee plan', () => {
    const row = applyTemplate({ ...emptyTermsRow(), applicationCode: 'MATRIMONY' }, template);
    expect(toTerms(row)).not.toHaveProperty('agentLimit');
  });

  it('clears only the template when none is chosen', () => {
    const row = applyTemplate({ ...emptyTermsRow(), templateId: 't1', agentLimit: 4, fee: 9 }, undefined);
    expect(row).toMatchObject({ templateId: '', agentLimit: 4, fee: 9 });
  });

  it('lets a row from a package leave the fee blank, but not a row without one, nor a negative fee', () => {
    expect(rowFeeInvalid({ ...emptyTermsRow(), templateId: 't1', fee: null })).toBe(false);
    expect(rowFeeInvalid({ ...emptyTermsRow(), fee: null })).toBe(true);
    expect(rowFeeInvalid({ ...emptyTermsRow(), templateId: 't1', fee: -1 })).toBe(true);
    expect(rowFeeInvalid({ ...emptyTermsRow(), fee: 0 })).toBe(false);
    const owner = { ownerEmail: 'r@x.co', ownerName: 'Ravi', ownerPhone: '' };
    expect(openFranchiseError({ ...owner, rows: [{ ...emptyTermsRow(), applicationCode: 'MATRIMONY', agentLimit: 20, templateId: 't1' }] })).toBeNull();
    expect(openFranchiseError({ ...owner, rows: [{ ...emptyTermsRow(), applicationCode: 'MATRIMONY', agentLimit: 20 }] })).toContain('errors.terms');
  });

  it('lets a row from a package leave the agent places blank (its fee plan gives them), but not a row without one, nor 0', () => {
    expect(rowLimitInvalid({ ...emptyTermsRow(), templateId: 't1', agentLimit: null })).toBe(false);
    expect(rowLimitInvalid({ ...emptyTermsRow(), agentLimit: null })).toBe(true);
    expect(rowLimitInvalid({ ...emptyTermsRow(), templateId: 't1', agentLimit: 0 })).toBe(true);
    expect(rowLimitInvalid({ ...emptyTermsRow(), agentLimit: 1 })).toBe(false);
    const owner = { ownerEmail: 'r@x.co', ownerName: 'Ravi', ownerPhone: '' };
    expect(openFranchiseError({ ...owner, rows: [{ ...emptyTermsRow(), applicationCode: 'MATRIMONY', fee: 1, agentLimit: null, templateId: 't1' }] })).toBeNull();
    expect(openFranchiseError({ ...owner, rows: [{ ...emptyTermsRow(), applicationCode: 'MATRIMONY', fee: 1, agentLimit: null }] })).toContain('errors.terms');
  });

  it('sends explicit commission lines and referral only when switched on', () => {
    const row = { ...applyTemplate(emptyTermsRow(), template), customCommission: true, customReferral: true };
    row.commissionLines = [{ ...emptyLineForm(), party: 'AGENT', basisPoints: 1500 }, { ...emptyLineForm(), party: 'OWNER', basisPoints: 8500 }];
    expect(termsExtrasError(row)).toBeNull();
    const terms = toTerms(row);
    expect(terms.commission?.lines).toHaveLength(2);
    expect(terms.referral).toMatchObject({ valueType: 'PERCENTAGE', basisPoints: 0 });
    expect(termsExtrasError({ ...row, commissionLines: [] })).toContain('noLines');
  });
});
