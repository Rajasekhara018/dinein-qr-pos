import {
  DocumentFormatKey,
  DocumentTypeFormErrors,
  DocumentTypeFormModel,
  DocumentTypeLanguage,
  DocumentTypeNames,
  DocumentTypeRequest,
  DocumentTypeResponse,
  TranslateFn,
} from './document-type.models';
import {
  BYTES_PER_MB,
  DEFAULT_MAX_FILE_SIZE_MB,
  RETENTION_DAYS_PER_MONTH,
  RETENTION_DAYS_PER_YEAR,
  DOCUMENT_FORMATS,
  DOCUMENT_TYPE_CODE_PATTERN,
  DOCUMENT_TYPE_LANGUAGES,
  DOCUMENT_TYPE_LIMITS,
  HTTP_STATUS,
  NO_VERIFICATION,
  VERIFICATION_PROVIDERS,
} from './document-types.constants';

/** A new, empty editor model. */
export function emptyDocumentTypeForm(): DocumentTypeFormModel {
  return {
    code: '',
    category: '',
    flow: '',
    names: emptyNames(),
    allowedFormats: ['PDF'],
    maxFileSizeMb: DEFAULT_MAX_FILE_SIZE_MB,
    required: false,
    expiryYears: null,
    retentionDays: null,
    scanRequired: true,
    verificationRequired: false,
    verificationMethod: NO_VERIFICATION,
    verificationProvider: null,
    active: true,
    version: 0,
  };
}

export function emptyNames(): DocumentTypeNames {
  return { en: '', hi: '', ta: '', te: '', kn: '', ml: '', ar: '' };
}

/** Editor model from an API response. */
export function formFromResponse(res: DocumentTypeResponse): DocumentTypeFormModel {
  return {
    code: res.code,
    category: res.category ?? '',
    flow: res.flow ?? '',
    names: { ...emptyNames(), ...(res.names ?? {}) },
    allowedFormats: [...(res.allowedFormats ?? [])],
    maxFileSizeMb: res.maxFileSizeMb ?? null,
    required: !!res.required,
    expiryYears: res.expiryYears ?? null,
    retentionDays: res.retentionDays ?? null,
    scanRequired: res.scanRequired ?? true,
    verificationRequired: !!res.verificationRequired,
    verificationMethod: res.verificationMethod ?? NO_VERIFICATION,
    verificationProvider: res.verificationProvider ?? null,
    active: !!res.active,
    version: res.version ?? 0,
  };
}

/** API request from the editor model: trims text, drops empty names (English is kept), orders formats as the catalog does. */
export function toRequest(form: DocumentTypeFormModel): DocumentTypeRequest {
  const names: Partial<DocumentTypeNames> = {};
  for (const lang of DOCUMENT_TYPE_LANGUAGES) {
    const value = (form.names[lang] ?? '').trim();
    if (value || lang === 'en') names[lang] = value;
  }
  const flow = form.flow.trim();
  return {
    code: form.code.trim().toUpperCase(),
    category: form.category.trim(),
    flow: flow ? flow : null,
    names,
    allowedFormats: orderedFormats(form.allowedFormats),
    maxFileSizeMb: Number(form.maxFileSizeMb),
    required: form.required,
    expiryYears: form.expiryYears === null || form.expiryYears === undefined ? null : Number(form.expiryYears),
    retentionDays: form.retentionDays === null || form.retentionDays === undefined ? null : Number(form.retentionDays),
    scanRequired: form.scanRequired,
    verificationRequired: form.verificationRequired,
    verificationMethod: form.verificationRequired ? form.verificationMethod : NO_VERIFICATION,
    verificationProvider: form.verificationRequired && form.verificationMethod !== 'MANUAL_REVIEW' && form.verificationMethod !== NO_VERIFICATION ? (form.verificationProvider || null) : null,
    active: form.active,
    version: form.version,
  };
}

/** Formats in catalog order, without duplicates. */
export function orderedFormats(selected: readonly DocumentFormatKey[]): DocumentFormatKey[] {
  const set = new Set(selected);
  return DOCUMENT_FORMATS.filter((f) => set.has(f.key)).map((f) => f.key);
}

function isWholeNumberInRange(value: number | null, min: number, max: number): boolean {
  return value !== null && value !== undefined && Number.isInteger(Number(value)) && Number(value) >= min && Number(value) <= max;
}

/** Problems in the form as translation keys; an empty object means the form can be sent. `isEdit` skips the (immutable) code check. */
export function validateDocumentTypeForm(form: DocumentTypeFormModel, isEdit: boolean): DocumentTypeFormErrors {
  const errors: DocumentTypeFormErrors = {};
  const limits = DOCUMENT_TYPE_LIMITS;
  if (!isEdit) {
    const code = form.code.trim().toUpperCase();
    if (code.length < limits.codeMin || code.length > limits.codeMax || !DOCUMENT_TYPE_CODE_PATTERN.test(code)) {
      errors.code = 'documentManagement.editor.errors.code';
    }
  }
  if (!form.category.trim()) errors.category = 'documentManagement.editor.errors.category';
  if (!(form.names.en ?? '').trim()) errors.nameEn = 'documentManagement.editor.errors.nameEn';
  if (!form.allowedFormats.length) errors.formats = 'documentManagement.editor.errors.formats';
  if (!isWholeNumberInRange(form.maxFileSizeMb, limits.maxFileSizeMbMin, limits.maxFileSizeMbMax)) {
    errors.maxFileSizeMb = 'documentManagement.editor.errors.maxFileSizeMb';
  }
  if (form.expiryYears !== null && form.expiryYears !== undefined
      && !isWholeNumberInRange(form.expiryYears, limits.expiryYearsMin, limits.expiryYearsMax)) {
    errors.expiryYears = 'documentManagement.editor.errors.expiryYears';
  }
  if (form.retentionDays !== null && form.retentionDays !== undefined
      && !isWholeNumberInRange(form.retentionDays, limits.retentionDaysMin, limits.retentionDaysMax)) {
    errors.retentionDays = 'documentManagement.editor.errors.retentionDays';
  }
  if (form.verificationRequired && (!form.verificationMethod || form.verificationMethod === NO_VERIFICATION)) {
    errors.verificationMethod = 'documentManagement.editor.errors.verificationMethod';
  }
  if (form.verificationRequired && form.verificationMethod !== 'MANUAL_REVIEW' && form.verificationMethod !== NO_VERIFICATION
      && (!form.verificationProvider || !VERIFICATION_PROVIDERS.some(provider => provider.key === form.verificationProvider && provider.methods.includes(form.verificationMethod)))) {
    errors.verificationProvider = 'documentManagement.providers.invalid';
  }
  return errors;
}

export function hasErrors(errors: DocumentTypeFormErrors): boolean {
  return Object.keys(errors).length > 0;
}

/** Input needed for the plain-language sentence. */
export interface SummaryInput {
  allowedFormats: readonly DocumentFormatKey[];
  maxFileSizeMb: number | null;
  required: boolean;
  expiryYears: number | null;
  /** Optional: only mentioned in the sentence when set. */
  retentionDays?: number | null;
}

/**
 * "Accepts PDF, JPG up to 5 MB. Required. Expires after 2 years." in the user's language.
 * Format codes (PDF, JPG ...) stay as they are; the phrases come from the translate function.
 */
export function buildDocumentTypeSummary(input: SummaryInput, t: TranslateFn): string {
  const parts: string[] = [buildFormatsAndSizeSentence(input, t)];
  parts.push(t(input.required ? 'documentManagement.summary.required' : 'documentManagement.summary.optional'));
  if (input.expiryYears === null || input.expiryYears === undefined) {
    parts.push(t('documentManagement.summary.noExpiry'));
  } else if (Number(input.expiryYears) === 1) {
    parts.push(t('documentManagement.summary.expiresOne'));
  } else {
    parts.push(t('documentManagement.summary.expiresMany', { years: Number(input.expiryYears) }));
  }
  if (input.retentionDays !== null && input.retentionDays !== undefined) {
    parts.push(buildRetentionSentence(Number(input.retentionDays), t));
  }
  return parts.join(' ');
}

/** "Kept 7 years.": whole years when exact (365 days each), else whole months (30 days each), else days. */
export function buildRetentionSentence(days: number, t: TranslateFn): string {
  if (days % RETENTION_DAYS_PER_YEAR === 0) {
    const years = days / RETENTION_DAYS_PER_YEAR;
    return years === 1 ? t('documentManagement.summary.keptOneYear') : t('documentManagement.summary.keptYears', { count: years });
  }
  if (days % RETENTION_DAYS_PER_MONTH === 0) {
    const months = days / RETENTION_DAYS_PER_MONTH;
    return months === 1 ? t('documentManagement.summary.keptOneMonth') : t('documentManagement.summary.keptMonths', { count: months });
  }
  return days === 1 ? t('documentManagement.summary.keptOneDay') : t('documentManagement.summary.keptDays', { count: days });
}

/** Just the "Accepts PDF, JPG up to 5 MB." part (used by the list's rule column). */
export function buildFormatsAndSizeSentence(input: Pick<SummaryInput, 'allowedFormats' | 'maxFileSizeMb'>, t: TranslateFn): string {
  const formats = orderedFormats(input.allowedFormats);
  if (!formats.length) return t('documentManagement.summary.noFormats');
  if (input.maxFileSizeMb !== null && input.maxFileSizeMb !== undefined && Number(input.maxFileSizeMb) > 0) {
    return t('documentManagement.summary.accepts', { formats: formats.join(', '), size: Number(input.maxFileSizeMb) });
  }
  return t('documentManagement.summary.acceptsAnySize', { formats: formats.join(', ') });
}

/** The name to show for a language, falling back to English, then to the code. */
export function localizedName(
  names: Partial<DocumentTypeNames> | null | undefined,
  lang: string,
  fallbackCode = '',
): string {
  const key = (lang || 'en').slice(0, 2).toLowerCase() as DocumentTypeLanguage;
  const own = names?.[key]?.trim();
  if (own) return own;
  const en = names?.en?.trim();
  return en || fallbackCode;
}

/** Translation key of the message to show after a failed save, by HTTP status. */
export function saveErrorKey(status: number | undefined): string {
  switch (status) {
    case HTTP_STATUS.badRequest:
      return 'documentManagement.editor.messages.invalid';
    case HTTP_STATUS.conflict:
      return 'documentManagement.editor.messages.conflict';
    case HTTP_STATUS.unavailable:
      return 'documentManagement.editor.messages.unavailable';
    default:
      return 'documentManagement.editor.messages.saveFailed';
  }
}

/** True when a file is larger than the biggest size the platform ever allows. */
export function exceedsPlatformLimit(sizeBytes: number): boolean {
  return sizeBytes > DOCUMENT_TYPE_LIMITS.maxFileSizeMbMax * BYTES_PER_MB;
}

/** File size in MB with at most one decimal, e.g. 1.5. */
export function formatFileSizeMb(sizeBytes: number): string {
  return (Math.round((sizeBytes / BYTES_PER_MB) * 10) / 10).toString();
}
