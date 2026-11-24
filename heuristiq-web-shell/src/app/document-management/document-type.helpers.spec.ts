import { describe, expect, it } from 'vitest';
import {
  buildDocumentTypeSummary,
  buildRetentionSentence,
  emptyDocumentTypeForm,
  exceedsPlatformLimit,
  formFromResponse,
  formatFileSizeMb,
  hasErrors,
  localizedName,
  orderedFormats,
  saveErrorKey,
  toRequest,
  validateDocumentTypeForm,
} from './document-type.helpers';
import { DocumentTypeResponse, TranslateFn } from './document-type.models';

/** Fake translate: key plus its params, so tests do not depend on the JSON files. */
const t: TranslateFn = (key, params) => (params ? `${key}${JSON.stringify(params)}` : key);

describe('document-type helpers', () => {
  it('builds the sentence from formats, size, required and expiry', () => {
    const text = buildDocumentTypeSummary({ allowedFormats: ['JPG', 'PDF'], maxFileSizeMb: 5, required: true, expiryYears: 2 }, t);
    expect(text).toBe(
      'documentManagement.summary.accepts{"formats":"PDF, JPG","size":5} documentManagement.summary.required documentManagement.summary.expiresMany{"years":2}',
    );
  });

  it('uses the singular phrase for one year and "optional / no expiry" when unset', () => {
    expect(buildDocumentTypeSummary({ allowedFormats: ['PDF'], maxFileSizeMb: 1, required: false, expiryYears: 1 }, t))
      .toContain('documentManagement.summary.expiresOne');
    const open = buildDocumentTypeSummary({ allowedFormats: ['PDF'], maxFileSizeMb: 1, required: false, expiryYears: null }, t);
    expect(open).toContain('documentManagement.summary.optional');
    expect(open).toContain('documentManagement.summary.noExpiry');
  });

  it('says so when no format is chosen or the size is empty', () => {
    expect(buildDocumentTypeSummary({ allowedFormats: [], maxFileSizeMb: 5, required: false, expiryYears: null }, t))
      .toContain('documentManagement.summary.noFormats');
    expect(buildDocumentTypeSummary({ allowedFormats: ['PNG'], maxFileSizeMb: null, required: false, expiryYears: null }, t))
      .toContain('documentManagement.summary.acceptsAnySize{"formats":"PNG"}');
  });

  it('orders formats like the catalog and removes duplicates', () => {
    expect(orderedFormats(['XLSX', 'PDF', 'PDF', 'JPG'])).toEqual(['PDF', 'JPG', 'XLSX']);
  });

  it('maps the form to a request: trims, upper-cases the code, drops empty names but keeps English, blank flow becomes null', () => {
    const form = emptyDocumentTypeForm();
    form.code = ' aadhaar_card ';
    form.category = ' Identity ';
    form.names.en = ' Aadhaar card ';
    form.names.hi = 'आधार कार्ड';
    form.names.ta = '   ';
    form.allowedFormats = ['PNG', 'PDF'];
    form.maxFileSizeMb = 3;
    form.version = 4;
    const req = toRequest(form);
    expect(req.code).toBe('AADHAAR_CARD');
    expect(req.category).toBe('Identity');
    expect(req.flow).toBeNull();
    expect(req.names).toEqual({ en: 'Aadhaar card', hi: 'आधार कार्ड' });
    expect(req.allowedFormats).toEqual(['PDF', 'PNG']);
    expect(req.expiryYears).toBeNull();
    expect(req.retentionDays).toBeNull();
    expect(req.scanRequired).toBe(true);
    expect(req.version).toBe(4);
    form.retentionDays = 2555;
    form.scanRequired = false;
    const withRetention = toRequest(form);
    expect(withRetention.retentionDays).toBe(2555);
    expect(withRetention.scanRequired).toBe(false);
  });

  it('reads a response without the new fields as default retention and scan required', () => {
    const res: DocumentTypeResponse = {
      id: '2', code: 'VOTER', category: 'Identity', flow: null, names: { en: 'Voter ID' }, allowedFormats: ['PDF'],
      maxFileSizeMb: 2, required: false, expiryYears: null, active: true, version: 1,
    };
    const form = formFromResponse(res);
    expect(form.retentionDays).toBeNull();
    expect(form.scanRequired).toBe(true);
    expect(formFromResponse({ ...res, retentionDays: 90, scanRequired: false })).toMatchObject({ retentionDays: 90, scanRequired: false });
  });

  it('expresses retention in whole years, then whole months, else days', () => {
    expect(buildRetentionSentence(2555, t)).toBe('documentManagement.summary.keptYears{"count":7}');
    expect(buildRetentionSentence(365, t)).toBe('documentManagement.summary.keptOneYear');
    expect(buildRetentionSentence(90, t)).toBe('documentManagement.summary.keptMonths{"count":3}');
    expect(buildRetentionSentence(30, t)).toBe('documentManagement.summary.keptOneMonth');
    expect(buildRetentionSentence(45, t)).toBe('documentManagement.summary.keptDays{"count":45}');
    expect(buildRetentionSentence(1, t)).toBe('documentManagement.summary.keptOneDay');
  });

  it('mentions retention in the summary only when set', () => {
    const base = { allowedFormats: ['PDF' as const], maxFileSizeMb: 5, required: true, expiryYears: null };
    expect(buildDocumentTypeSummary(base, t)).not.toContain('kept');
    expect(buildDocumentTypeSummary({ ...base, retentionDays: 2555 }, t)).toMatch(/keptYears\{"count":7\}$/);
  });

  it('validates the retention range', () => {
    const form = emptyDocumentTypeForm();
    form.category = 'x';
    form.names.en = 'x';
    form.retentionDays = 36500;
    expect(validateDocumentTypeForm(form, true).retentionDays).toBeUndefined();
    form.retentionDays = 36501;
    expect(validateDocumentTypeForm(form, true).retentionDays).toBeDefined();
    form.retentionDays = 0;
    expect(validateDocumentTypeForm(form, true).retentionDays).toBeDefined();
    form.retentionDays = null;
    expect(validateDocumentTypeForm(form, true).retentionDays).toBeUndefined();
  });

  it('round-trips a response into the editor model', () => {
    const res: DocumentTypeResponse = {
      id: '1', code: 'PAN', category: 'Identity', flow: null, names: { en: 'PAN' }, allowedFormats: ['PDF'],
      maxFileSizeMb: 2, required: true, expiryYears: null, active: true, version: 7,
    };
    const form = formFromResponse(res);
    expect(form.flow).toBe('');
    expect(form.names.hi).toBe('');
    expect(form.version).toBe(7);
  });

  it('reads a type saved before verification existed as not verified, and keeps a saved method', () => {
    const res: DocumentTypeResponse = {
      id: '3', code: 'RC', category: 'driver-documents', flow: null, names: { en: 'RC' }, allowedFormats: ['PDF'],
      maxFileSizeMb: 5, required: true, expiryYears: 15, active: true, version: 1,
    };
    expect(formFromResponse(res)).toMatchObject({ verificationRequired: false, verificationMethod: 'NONE' });
    expect(formFromResponse({ ...res, verificationRequired: true, verificationMethod: 'VEHICLE_RC_VAHAN' }))
      .toMatchObject({ verificationRequired: true, verificationMethod: 'VEHICLE_RC_VAHAN' });
  });

  it('sends the verification method only when verification is required', () => {
    const form = emptyDocumentTypeForm();
    expect(form).toMatchObject({ verificationRequired: false, verificationMethod: 'NONE' });
    form.verificationMethod = 'PAN_VERIFICATION';
    expect(toRequest(form)).toMatchObject({ verificationRequired: false, verificationMethod: 'NONE' });
    form.verificationRequired = true;
    expect(toRequest(form)).toMatchObject({ verificationRequired: true, verificationMethod: 'PAN_VERIFICATION' });
  });

  it('stores one provider and rejects a provider for another method', () => {
    const form = emptyDocumentTypeForm();
    form.verificationRequired = true;
    form.verificationMethod = 'BANK_PENNY_DROP';
    form.verificationProvider = 'CASHFREE';
    expect(validateDocumentTypeForm(form, false).verificationProvider).toBeUndefined();
    expect(toRequest(form).verificationProvider).toBe('CASHFREE');
    expect(Array.isArray(toRequest(form).verificationProvider)).toBe(false);
    form.verificationProvider = 'IDFY';
    expect(validateDocumentTypeForm(form, false).verificationProvider).toBe('documentManagement.providers.invalid');
    form.verificationRequired = false;
    expect(toRequest(form).verificationProvider).toBeNull();
  });

  it('asks for a method when verification is required', () => {
    const form = emptyDocumentTypeForm();
    form.code = 'PAN_CARD';
    form.category = 'Identity';
    form.names.en = 'PAN card';
    form.verificationRequired = true;
    expect(validateDocumentTypeForm(form, false).verificationMethod).toBe('documentManagement.editor.errors.verificationMethod');
    form.verificationMethod = 'MANUAL_REVIEW';
    expect(validateDocumentTypeForm(form, false).verificationMethod).toBeUndefined();
  });

  it('validates a complete form without problems', () => {
    const form = emptyDocumentTypeForm();
    form.code = 'PAN_CARD';
    form.category = 'Identity';
    form.names.en = 'PAN card';
    expect(hasErrors(validateDocumentTypeForm(form, false))).toBe(false);
  });

  it('reports each broken field', () => {
    const form = emptyDocumentTypeForm();
    form.code = 'a b';
    form.allowedFormats = [];
    form.maxFileSizeMb = 51;
    form.expiryYears = 0;
    const errors = validateDocumentTypeForm(form, false);
    expect(Object.keys(errors).sort()).toEqual(['category', 'code', 'expiryYears', 'formats', 'maxFileSizeMb', 'nameEn']);
  });

  it('does not check the code when editing (it cannot change) and accepts the size and expiry bounds', () => {
    const form = emptyDocumentTypeForm();
    form.code = '';
    form.category = 'x';
    form.names.en = 'x';
    form.maxFileSizeMb = 50;
    form.expiryYears = 50;
    expect(hasErrors(validateDocumentTypeForm(form, true))).toBe(false);
    form.maxFileSizeMb = 0;
    expect(validateDocumentTypeForm(form, true).maxFileSizeMb).toBeDefined();
    form.maxFileSizeMb = 1.5;
    expect(validateDocumentTypeForm(form, true).maxFileSizeMb).toBeDefined();
  });

  it('picks the name in the current language, then English, then the code', () => {
    const names = { en: 'Passport', hi: 'पासपोर्ट' };
    expect(localizedName(names, 'hi-IN', 'PASSPORT')).toBe('पासपोर्ट');
    expect(localizedName(names, 'ta', 'PASSPORT')).toBe('Passport');
    expect(localizedName({}, 'en', 'PASSPORT')).toBe('PASSPORT');
    expect(localizedName(null, '', 'X')).toBe('X');
  });

  it('chooses the message for each failure status', () => {
    expect(saveErrorKey(409)).toBe('documentManagement.editor.messages.conflict');
    expect(saveErrorKey(503)).toBe('documentManagement.editor.messages.unavailable');
    expect(saveErrorKey(400)).toBe('documentManagement.editor.messages.invalid');
    expect(saveErrorKey(500)).toBe('documentManagement.editor.messages.saveFailed');
    expect(saveErrorKey(undefined)).toBe('documentManagement.editor.messages.saveFailed');
  });

  it('flags files above 50 MB and formats sizes', () => {
    expect(exceedsPlatformLimit(50 * 1024 * 1024)).toBe(false);
    expect(exceedsPlatformLimit(50 * 1024 * 1024 + 1)).toBe(true);
    expect(formatFileSizeMb(1.54 * 1024 * 1024)).toBe('1.5');
  });
});
